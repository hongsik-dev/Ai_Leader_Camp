const http = require("http");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const WebSocket = require("ws");
let createRedisClient = null;

try {
  ({ createClient: createRedisClient } = require("redis"));
} catch {
  createRedisClient = null;
}

const PORT = process.env.PORT || 3001;
const ROOM_TTL_MS = 12 * 60 * 60 * 1000;
const NAVER_CLIENT_ID = process.env.NAVER_MAP_CLIENT_ID || "";
const NAVER_CLIENT_SECRET = process.env.NAVER_MAP_CLIENT_SECRET || "";
const REDIS_URL = process.env.REDIS_URL || "redis://127.0.0.1:6379";
const REDIS_ENABLED = process.env.REDIS_ENABLED !== "0";
const NAVER_GEOCODE_URL = "https://maps.apigw.ntruss.com/map-geocode/v2/geocode";
const NAVER_DIRECTIONS_URL = "https://maps.apigw.ntruss.com/map-direction/v1/driving";
const GEOCODE_CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000;
const DIRECTIONS_CACHE_TTL_MS = 15 * 60 * 1000;
const GEOCODE_NEGATIVE_CACHE_TTL_MS = 5 * 60 * 1000;
const DIRECTIONS_NEGATIVE_CACHE_TTL_MS = 2 * 60 * 1000;
const RATE_LIMIT_WINDOW_MS = 60 * 1000;
const RATE_LIMIT_MAX = 180;
const LICENSE_STORE_PATH = process.env.CATCHPRO_LICENSE_STORE || path.join(__dirname, "licenses.json");
const LICENSE_ADMIN_TOKEN = process.env.CATCHPRO_LICENSE_ADMIN_TOKEN || "";
const LICENSE_PRODUCT_EDITION = "catchpro-pro";
const LICENSE_APP_EDITIONS = new Set([LICENSE_PRODUCT_EDITION, "insung-pro", "navi-pro"]);
const DEFAULT_LICENSE_MAX_DEVICES = 2;
const CATCHPRO_APPLY_URL = process.env.CATCHPRO_APPLY_URL || "https://hongsik.blog/catchpro-pro-apply/";
const rooms = new Map();
const responseCache = new Map();
const rateLimits = new Map();
let redisClient = null;
let redisReady = false;
let redisLastErrorLogAt = 0;
let licenseStoreMutationQueue = Promise.resolve();

startRedis();

function startRedis() {
  if (!REDIS_ENABLED) {
    console.log("REDIS_CACHE disabled");
    return;
  }
  if (!createRedisClient) {
    console.warn("REDIS_CACHE unavailable module=redis");
    return;
  }

  redisClient = createRedisClient({ url: REDIS_URL });
  redisClient.on("error", (error) => {
    redisReady = false;
    logRedisError(error);
  });
  redisClient.on("ready", () => {
    redisReady = true;
    console.log("REDIS_CACHE ready");
  });
  redisClient.on("end", () => {
    redisReady = false;
  });
  redisClient.connect().catch(logRedisError);
}

function logRedisError(error) {
  const now = Date.now();
  if (now - redisLastErrorLogAt < 60_000) return;
  redisLastErrorLogAt = now;
  console.warn(`REDIS_CACHE unavailable reason=${error?.code || error?.message || "unknown"}`);
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host || "localhost"}`);
    if (url.pathname === "/health") {
      sendJson(res, 200, { ok: true, service: "catchpro-sync" });
      return;
    }
    if (await handleLicenseApi(req, res, url)) {
      return;
    }
    if (await handleKakaoChatbot(req, res, url)) {
      return;
    }
    if (await handleNaverProxy(req, res, url)) {
      return;
    }
    res.writeHead(404);
    res.end();
  } catch (error) {
    console.error("HTTP request failed", error);
    sendJson(res, 500, { ok: false, error: "internal server error" });
  }
});

const wss = new WebSocket.Server({ server, path: "/catchpro-sync" });

function sendJson(res, status, payload) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "X-Robots-Tag": "noindex, nofollow, noarchive",
    "X-Content-Type-Options": "nosniff",
  });
  res.end(JSON.stringify(payload));
}

function sendNaverResponse(res, result, cacheHit) {
  res.writeHead(result.status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": cacheHit ? "private, max-age=60" : "no-store",
    "X-CatchPro-Cache": cacheHit ? "hit" : "miss",
    "X-Robots-Tag": "noindex, nofollow, noarchive",
    "X-Content-Type-Options": "nosniff",
  });
  res.end(result.body);
}

function clientIp(req) {
  const forwarded = String(req.headers["x-forwarded-for"] || "")
    .split(",")[0]
    .trim();
  return forwarded || req.socket.remoteAddress || "unknown";
}

function rateLimitAllows(ip) {
  const now = Date.now();
  const current = rateLimits.get(ip);
  if (!current || current.resetAt <= now) {
    rateLimits.set(ip, { count: 1, resetAt: now + RATE_LIMIT_WINDOW_MS });
    return true;
  }
  current.count += 1;
  return current.count <= RATE_LIMIT_MAX;
}

async function cached(cacheKey) {
  const entry = responseCache.get(cacheKey);
  if (entry) {
    if (entry.expiresAt > Date.now()) {
      return entry.result;
    }
    responseCache.delete(cacheKey);
  }

  if (!redisReady || !redisClient) return null;
  try {
    const value = await redisClient.get(cacheKey);
    if (!value) return null;
    const result = JSON.parse(value);
    responseCache.set(cacheKey, {
      result,
      expiresAt: Date.now() + Math.min(60_000, GEOCODE_CACHE_TTL_MS),
    });
    return result;
  } catch (error) {
    logRedisError(error);
    return null;
  }
}

async function cacheResponse(cacheKey, successTtlMs, failureTtlMs, result) {
  const ttlMs = result.status >= 200 && result.status < 300 ? successTtlMs : failureTtlMs;
  if (ttlMs <= 0) return;

  responseCache.set(cacheKey, { result, expiresAt: Date.now() + ttlMs });
  if (!redisReady || !redisClient) return;
  try {
    await redisClient.setEx(cacheKey, Math.max(1, Math.ceil(ttlMs / 1000)), JSON.stringify(result));
  } catch (error) {
    logRedisError(error);
  }
}

function validCoordinatePair(value) {
  return /^-?\d{1,3}(?:\.\d+)?,-?\d{1,2}(?:\.\d+)?$/.test(String(value || ""));
}

function sha256(value) {
  return crypto.createHash("sha256").update(String(value)).digest("hex").slice(0, 16);
}

function requestSource(url) {
  return String(url.searchParams.get("source") || "unknown")
    .trim()
    .replace(/[^\w.-]/g, "_")
    .slice(0, 40) || "unknown";
}

function normalizedCoordinatePair(value) {
  const [longitude, latitude] = String(value || "")
    .split(",")
    .map((part) => Number(part));
  return `${longitude.toFixed(5)},${latitude.toFixed(5)}`;
}

function logNaverProxy(kind, fields) {
  const parts = Object.entries(fields)
    .filter(([, value]) => value !== undefined && value !== null && value !== "")
    .map(([key, value]) => `${key}=${String(value).replace(/\s+/g, "_")}`);
  console.log(`${kind} ${parts.join(" ")}`);
}

async function handleNaverProxy(req, res, url) {
  if (!url.pathname.startsWith("/api/naver/")) return false;
  const startedAt = Date.now();
  const source = requestSource(url);

  if (req.method !== "GET") {
    sendJson(res, 405, { ok: false, error: "method not allowed" });
    return true;
  }
  if (!NAVER_CLIENT_ID || !NAVER_CLIENT_SECRET) {
    sendJson(res, 503, { ok: false, error: "naver proxy is not configured" });
    return true;
  }

  const ip = clientIp(req);
  if (!rateLimitAllows(ip)) {
    console.warn(`NAVER_PROXY_RATE_LIMIT ip=${ip} path=${url.pathname}`);
    sendJson(res, 429, { ok: false, error: "rate limited" });
    return true;
  }

  if (url.pathname === "/api/naver/geocode") {
    const query = String(url.searchParams.get("query") || "").trim();
    if (query.length < 2 || query.length > 200) {
      sendJson(res, 400, { ok: false, error: "query must be 2-200 characters" });
      return true;
    }

    const naverUrl = new URL(NAVER_GEOCODE_URL);
    naverUrl.searchParams.set("query", query);
    for (const optional of ["coordinate", "filter", "language"]) {
      const value = url.searchParams.get(optional);
      if (value) naverUrl.searchParams.set(optional, value);
    }

    const queryHash = sha256(query);
    const cacheKey = `naver:geocode:v1:${sha256(naverUrl.searchParams.toString())}`;
    const hit = await cached(cacheKey);
    if (hit) {
      logNaverProxy("NAVER_PROXY_GEOCODE", {
        cache: "hit",
        source,
        queryHash,
        status: hit.status,
        durationMs: Date.now() - startedAt,
      });
      sendNaverResponse(res, hit, true);
      return true;
    }

    logNaverProxy("NAVER_PROXY_GEOCODE", {
      cache: "miss",
      source,
      queryHash,
    });
    const result = await callNaver(naverUrl);
    await cacheResponse(cacheKey, GEOCODE_CACHE_TTL_MS, GEOCODE_NEGATIVE_CACHE_TTL_MS, result);
    logNaverProxy("NAVER_PROXY_GEOCODE_RESULT", {
      cache: "miss",
      source,
      queryHash,
      status: result.status,
      durationMs: Date.now() - startedAt,
    });
    sendNaverResponse(res, result, false);
    return true;
  }

  if (url.pathname === "/api/naver/directions") {
    const start = url.searchParams.get("start");
    const goal = url.searchParams.get("goal");
    if (!validCoordinatePair(start) || !validCoordinatePair(goal)) {
      sendJson(res, 400, { ok: false, error: "start and goal must be lon,lat" });
      return true;
    }
    const normalizedStart = normalizedCoordinatePair(start);
    const normalizedGoal = normalizedCoordinatePair(goal);
    const option = url.searchParams.get("option") || "trafast";
    const routeHash = sha256(`${normalizedStart}|${normalizedGoal}|${option}`);

    const naverUrl = new URL(NAVER_DIRECTIONS_URL);
    naverUrl.searchParams.set("start", normalizedStart);
    naverUrl.searchParams.set("goal", normalizedGoal);
    naverUrl.searchParams.set("option", option);

    const cacheKey = `naver:directions:v1:${routeHash}`;
    const hit = await cached(cacheKey);
    if (hit) {
      logNaverProxy("NAVER_PROXY_DIRECTIONS", {
        cache: "hit",
        source,
        routeHash,
        status: hit.status,
        durationMs: Date.now() - startedAt,
      });
      sendNaverResponse(res, hit, true);
      return true;
    }

    logNaverProxy("NAVER_PROXY_DIRECTIONS", {
      cache: "miss",
      source,
      routeHash,
    });
    const result = await callNaver(naverUrl);
    await cacheResponse(cacheKey, DIRECTIONS_CACHE_TTL_MS, DIRECTIONS_NEGATIVE_CACHE_TTL_MS, result);
    logNaverProxy("NAVER_PROXY_DIRECTIONS_RESULT", {
      cache: "miss",
      source,
      routeHash,
      status: result.status,
      durationMs: Date.now() - startedAt,
    });
    sendNaverResponse(res, result, false);
    return true;
  }

  sendJson(res, 404, { ok: false, error: "unknown naver proxy path" });
  return true;
}

async function callNaver(url) {
  const response = await fetch(url, {
    headers: {
      "X-NCP-APIGW-API-KEY-ID": NAVER_CLIENT_ID,
      "X-NCP-APIGW-API-KEY": NAVER_CLIENT_SECRET,
      Accept: "application/json",
    },
  });
  return {
    status: response.status,
    body: await response.text(),
  };
}

async function handleKakaoChatbot(req, res, url) {
  if (url.pathname !== "/api/kakao/chatbot") return false;

  if (req.method === "GET") {
    sendJson(res, 200, { ok: true, service: "catchpro-kakao-chatbot" });
    return true;
  }
  if (req.method !== "POST") {
    sendKakaoResponse(res, "지원하지 않는 요청 방식입니다. 아래 메뉴에서 다시 선택해 주세요.");
    return true;
  }

  const payload = await readJsonBody(req, 64 * 1024);
  const utterance = extractKakaoUtterance(payload);
  const answer = catchproFaqAnswer(utterance);
  console.log(`KAKAO_CHATBOT_REPLY intent=${answer.intent} queryHash=${sha256(utterance || "empty")}`);
  sendKakaoResponse(res, answer.text, answer.quickReplies);
  return true;
}

function extractKakaoUtterance(payload) {
  if (!payload || typeof payload !== "object") return "";
  return String(
    payload.userRequest?.utterance ||
    payload.action?.params?.utterance ||
    payload.action?.detailParams?.utterance?.value ||
    ""
  ).trim().slice(0, 200);
}

function catchproFaqAnswer(rawText) {
  const text = normalizeKorean(rawText);
  if (!text) {
    return faq("home", [
      "CatchPro 상담 채널입니다.",
      "",
      "CatchPro Pro 신청, 설치, 요금, 주소/네비, 오류 문의를 안내합니다.",
      "아래 항목을 선택하거나 궁금한 내용을 그대로 입력해 주세요.",
    ].join("\n"));
  }

  if (hasAny(text, ["신청", "가입", "체험", "사용하고", "도입", "써보고", "시작"])) {
    return faq("apply", [
      "CatchPro Pro는 신청 후 사용 환경을 확인하고 30일 무료체험으로 시작합니다.",
      "",
      "신청 페이지:",
      CATCHPRO_APPLY_URL,
      "",
      "체험 후 계속 사용할 경우 월 9,900원 구독으로 전환합니다.",
      "구독에는 인성 CatchPro Pro와 CatchPro Navi Pro가 함께 포함됩니다.",
      "",
      "남겨 주실 내용:",
      "1. 이름 또는 연락 가능한 닉네임",
      "2. 연락처",
      "3. 안드로이드 기종",
      "4. 인성앱 사용 여부",
      "5. 주 운행 지역 또는 문의 내용",
    ].join("\n"));
  }

  if (hasAny(text, ["설치", "apk", "다운", "파일", "업데이트", "다운로드", "버전"])) {
    return faq("install", [
      "설치는 안드로이드 APK 방식으로 진행합니다.",
      "",
      "기본 순서:",
      "1. 설치 파일 전달",
      "2. 알 수 없는 앱 설치 허용",
      "3. CatchPro 실행",
      "4. 접근성/위치/알림 권한 설정",
      "5. 실제 화면에서 동작 확인",
      "",
      "기종과 안드로이드 버전을 알려 주시면 맞는 설치 방법으로 안내합니다.",
    ].join("\n"));
  }

  if (hasAny(text, ["요금", "가격", "결제", "구독", "비용", "얼마", "유료", "무료"])) {
    return faq("price", [
      "CatchPro는 Free/Pro 구성으로 운영합니다.",
      "",
      "Free: 지도/네비 중심의 기본 기능",
      "Pro: 인성 CatchPro Pro와 CatchPro Navi Pro를 함께 사용하는 통합 구독",
      "",
      "Pro는 신청 승인 후 30일 무료체험을 제공하고, 이후 계속 사용할 경우 월 9,900원으로 이용합니다.",
    ].join("\n"));
  }

  if (hasAny(text, ["차이", "인성", "navi", "네비", "지도", "역할", "두개", "두대"])) {
    return faq("difference", [
      "CatchPro Pro는 하나의 구독이고, 앱은 역할에 따라 나눠 씁니다.",
      "",
      "인성 CatchPro: 인성앱을 사용하는 폰에서 오더조건 확인과 오더확정을 보조합니다.",
      "CatchPro Navi: 지도, 방문순서, 네이버/TMAP 네비 연결을 담당합니다.",
      "",
      "두 폰을 나눠 쓰면 오더확정 폰은 가볍게 유지하고, 네비폰은 지도 중심으로 사용할 수 있습니다.",
    ].join("\n"));
  }

  if (hasAny(text, ["자동확정", "오더확정", "확정", "잡아", "잡히", "배차", "콜"])) {
    return faq("confirm", [
      "CatchPro Pro에 포함된 인성 CatchPro Pro는 오더조건 확인 후 오더확정을 빠르게 보조하는 방향으로 운영합니다.",
      "",
      "단, 통신 상태, 인성앱 화면 상태, 다른 기사와의 경쟁 상황에 따라 확정 결과는 달라질 수 있습니다.",
      "배정 실패가 반복되면 발생 시간과 화면 캡처를 남겨 주세요. 로그 기준으로 확인합니다.",
    ].join("\n"));
  }

  if (hasAny(text, ["주소", "동기화", "aws", "방코드", "방 코드", "티맵", "tmap", "네이버지도", "네이버 지도"])) {
    return faq("route_sync", [
      "주소/네비 기능은 CatchPro Navi 기준으로 운영하는 것을 권장합니다.",
      "",
      "주요 기능:",
      "1. 주소 1~6 관리",
      "2. 현재 위치 기준 방문순서 확인",
      "3. 네이버 지도 표시",
      "4. TMAP 또는 네이버 네비 연결",
      "5. Pro 구독 시 인성 CatchPro와 AWS 주소 동기화",
      "",
      "주소가 동기화되지 않으면 두 폰의 방 코드와 인터넷 연결 상태를 먼저 확인해 주세요.",
    ].join("\n"));
  }

  if (hasAny(text, ["아이폰", "ios", "갤럭시", "안드로이드", "기종", "권한", "접근성", "위치권한", "알림권한"])) {
    return faq("device", [
      "현재는 안드로이드 폰 기준으로 지원합니다.",
      "",
      "인성 CatchPro는 접근성 권한이 필요하고, CatchPro Navi는 위치 권한이 필요합니다.",
      "아이폰은 현재 지원하지 않습니다.",
      "",
      "권한 설정 화면에서 막히면 기종명과 현재 화면 캡처를 보내 주세요.",
    ].join("\n"));
  }

  if (hasAny(text, ["오류", "안돼", "안되", "실패", "로그", "튕", "배정", "안됨", "멈춤", "느림"])) {
    return faq("trouble", [
      "오류 확인은 로그 기준으로 진행합니다.",
      "",
      "아래 내용을 남겨 주세요:",
      "1. 발생 시간",
      "2. 사용 앱: 인성 CatchPro 또는 CatchPro Navi",
      "3. 증상",
      "4. 화면 캡처 또는 짧은 설명",
      "5. 어떤 버튼을 눌렀는지",
      "",
      "예: 오전 11시 40분, 인성 CatchPro, 오더상세 진입 후 오더확정 실패",
    ].join("\n"));
  }

  if (hasAny(text, ["해지", "환불", "취소", "중지", "탈퇴"])) {
    return faq("cancel", [
      "해지/환불 문의는 상담원이 확인합니다.",
      "",
      "신청자명, 연락처, 결제일 또는 체험 시작일을 남겨 주세요.",
      "확인 후 순서대로 안내하겠습니다.",
    ].join("\n"));
  }

  if (hasAny(text, ["상담", "사람", "문의", "연결", "담당", "직접", "전화"])) {
    return faq("human", [
      "상담원이 확인할 수 있도록 아래 정보를 남겨 주세요.",
      "",
      "이름 / 연락처 / 사용 폰 기종 / 사용 목적 / 문의 내용",
      "",
      "운행 중이면 바로 답변이 늦을 수 있습니다. 남겨 주신 내용은 순서대로 확인합니다.",
    ].join("\n"));
  }

  return faq("fallback", [
    "문의 내용을 확인했습니다.",
    "",
    "아래 메뉴 중 가까운 항목을 선택하거나, 사용 폰 기종과 문의 내용을 함께 남겨 주세요.",
  ].join("\n"));
}

function faq(intent, text) {
  return { intent, text, quickReplies: kakaoQuickReplies() };
}

function sendKakaoResponse(res, text, quickReplies = kakaoQuickReplies()) {
  sendJson(res, 200, {
    version: "2.0",
    template: {
      outputs: [
        {
          simpleText: {
            text: String(text || "").slice(0, 900),
          },
        },
      ],
      quickReplies,
    },
  });
}

function kakaoQuickReplies() {
  return [
    kakaoQuickReply("Pro 신청"),
    kakaoQuickReply("설치 안내"),
    kakaoQuickReply("요금 안내"),
    kakaoQuickReply("지도/네비 기능"),
    kakaoQuickReply("주소 동기화"),
    kakaoQuickReply("오류 문의"),
    kakaoQuickReply("상담원 연결"),
  ];
}

function kakaoQuickReply(label) {
  return {
    label,
    action: "message",
    messageText: label,
  };
}

function normalizeKorean(value) {
  return String(value || "")
    .trim()
    .toLowerCase()
    .replace(/\s+/g, "");
}

function hasAny(text, words) {
  return words.some((word) => text.includes(String(word).toLowerCase().replace(/\s+/g, "")));
}

async function handleLicenseApi(req, res, url) {
  if (!url.pathname.startsWith("/api/license/")) return false;

  if (url.pathname === "/api/license/app-version") {
    if (req.method !== "GET") {
      sendJson(res, 405, { ok: false, error: "method not allowed" });
      return true;
    }
    const store = await readLicenseStore();
    sendJson(res, 200, {
      ok: true,
      versions: store.appVersions || defaultLicenseStore().appVersions,
    });
    return true;
  }

  if (url.pathname === "/api/license/check") {
    if (req.method !== "POST") {
      sendJson(res, 405, { ok: false, error: "method not allowed" });
      return true;
    }
    const payload = await readJsonBody(req, 32 * 1024);
    if (!payload) {
      sendJson(res, 400, { ok: false, error: "invalid json body" });
      return true;
    }
    const result = await checkLicense(payload);
    sendJson(res, 200, result);
    return true;
  }

  if (url.pathname === "/api/license/register-device") {
    if (req.method !== "POST") {
      sendJson(res, 405, { ok: false, error: "method not allowed" });
      return true;
    }
    if (!licenseAdminAllowed(req)) {
      sendJson(res, 403, { ok: false, error: "admin token required" });
      return true;
    }
    const payload = await readJsonBody(req, 32 * 1024);
    if (!payload) {
      sendJson(res, 400, { ok: false, error: "invalid json body" });
      return true;
    }
    const result = await registerLicenseDevice(payload);
    sendJson(res, result.ok ? 200 : 400, result);
    return true;
  }

  if (url.pathname === "/api/license/list") {
    if (req.method !== "GET") {
      sendJson(res, 405, { ok: false, error: "method not allowed" });
      return true;
    }
    if (!licenseAdminAllowed(req)) {
      sendJson(res, 403, { ok: false, error: "admin token required" });
      return true;
    }
    const store = await readLicenseStore();
    sendJson(res, 200, {
      ok: true,
      licenses: listLicenses(store),
    });
    return true;
  }

  if (url.pathname === "/api/license/upsert") {
    if (req.method !== "POST") {
      sendJson(res, 405, { ok: false, error: "method not allowed" });
      return true;
    }
    if (!licenseAdminAllowed(req)) {
      sendJson(res, 403, { ok: false, error: "admin token required" });
      return true;
    }
    const payload = await readJsonBody(req, 32 * 1024);
    if (!payload) {
      sendJson(res, 400, { ok: false, error: "invalid json body" });
      return true;
    }
    const result = await upsertLicense(payload);
    sendJson(res, result.ok ? 200 : 400, result);
    return true;
  }

  sendJson(res, 404, { ok: false, error: "unknown license path" });
  return true;
}

function licenseAdminAllowed(req) {
  if (!LICENSE_ADMIN_TOKEN) return false;
  return String(req.headers["x-catchpro-admin-token"] || "") === LICENSE_ADMIN_TOKEN;
}

async function readJsonBody(req, maxBytes) {
  let size = 0;
  const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > maxBytes) return null;
    chunks.push(chunk);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}");
  } catch {
    return null;
  }
}

function defaultLicenseStore() {
  return {
    appVersions: {
      "catchpro-pro": { minimumVersionCode: 0, latestVersionName: "" },
      "insung-pro": { minimumVersionCode: 0, latestVersionName: "" },
      "navi-pro": { minimumVersionCode: 0, latestVersionName: "" },
    },
    licenses: [],
  };
}

async function readLicenseStore() {
  try {
    const raw = await fs.promises.readFile(LICENSE_STORE_PATH, "utf8");
    const parsed = JSON.parse(raw);
    return {
      ...defaultLicenseStore(),
      ...parsed,
      licenses: Array.isArray(parsed.licenses) ? parsed.licenses : [],
    };
  } catch (error) {
    if (error && error.code !== "ENOENT") {
      console.warn(`LICENSE_STORE_READ_FAILED reason=${error.code || error.message || "unknown"}`);
    }
    return defaultLicenseStore();
  }
}

async function writeLicenseStore(store) {
  const tmpPath = `${LICENSE_STORE_PATH}.tmp`;
  await fs.promises.writeFile(tmpPath, `${JSON.stringify(store, null, 2)}\n`, "utf8");
  await fs.promises.rename(tmpPath, LICENSE_STORE_PATH);
}

async function withLicenseStoreLock(task) {
  const run = licenseStoreMutationQueue.then(task, task);
  licenseStoreMutationQueue = run.catch(() => {});
  return run;
}

async function checkLicense(payload) {
  return withLicenseStoreLock(async () => {
  const store = await readLicenseStore();
  const now = Date.now();
  const requestEdition = normalizeLicenseAppEdition(payload.edition);
  const productEdition = normalizeLicenseProductEdition(requestEdition);
  const email = sanitizeEmail(payload.email);
  const phone = normalizePhone(payload.phone);
  const deviceId = sanitizeDeviceId(payload.deviceId);
  const packageName = sanitizeLicenseText(payload.packageName);
  const versionCode = Number(payload.versionCode) || 0;
  const license = findLicense(store, { edition: requestEdition, email, phone });
  const requestHash = sha256(`${productEdition}|${email}|${phone}|${deviceId}`);

  if (!requestEdition) {
    return {
      ok: false,
      licenseStatus: "invalid_edition",
      message: "지원하지 않는 라이선스 버전입니다.",
    };
  }

  if (!deviceId) {
    console.log(`LICENSE_CHECK status=missing_device requestHash=${requestHash} edition=${requestEdition}`);
    return {
      ok: false,
      licenseStatus: "missing_device",
      message: "기기 인증 정보가 없습니다. 앱을 다시 실행한 뒤 라이선스를 확인해 주세요.",
    };
  }

  if (!license) {
    console.log(`LICENSE_CHECK status=missing requestHash=${requestHash} edition=${requestEdition}`);
    return {
      ok: false,
      licenseStatus: "missing",
      message: "등록된 라이선스를 찾지 못했습니다.",
    };
  }

  const normalizedStatus = sanitizeLicenseText(license.status).toLowerCase() || "inactive";
  const expiresAt = sanitizeLicenseText(license.expiresAt || license.expires_at);
  const expiresAtMillis = expiresAt ? Date.parse(expiresAt) : NaN;
  const expired = Number.isFinite(expiresAtMillis) && expiresAtMillis <= now;
  const registeredDeviceIds = licenseDeviceList(license);
  const maxDevices = licenseMaxDevices(license);
  const deviceRegistered = deviceId && registeredDeviceIds.includes(deviceId);
  const deviceCanBind = Boolean(
    deviceId &&
    !deviceRegistered &&
    license.allowDeviceBind !== false &&
    registeredDeviceIds.length < maxDevices,
  );
  const deviceLimitReached = Boolean(deviceId && !deviceRegistered && !deviceCanBind);
  const active = ["active", "trial"].includes(normalizedStatus) &&
    !expired &&
    (deviceRegistered || deviceCanBind);
  const versionPolicy = (store.appVersions || {})[requestEdition] || (store.appVersions || {})[productEdition] || {};
  const minimumVersionCode = Number(versionPolicy.minimumVersionCode) || 0;
  const updateRequired = minimumVersionCode > 0 && versionCode > 0 && versionCode < minimumVersionCode;

  if (deviceCanBind && active) {
    addLicenseDeviceId(license, requestEdition, deviceId);
    license.updatedAt = new Date().toISOString();
    await writeLicenseStore(store);
  }

  const licenseStatus = expired ? "expired" : (deviceLimitReached ? "device_limit" : normalizedStatus);
  console.log(`LICENSE_CHECK status=${licenseStatus} active=${active && !updateRequired} requestHash=${requestHash} edition=${requestEdition}`);
  return {
    ok: active && !updateRequired,
    licenseStatus: updateRequired ? "update_required" : licenseStatus,
    expiresAt,
    deviceId: deviceRegistered || (deviceCanBind && active) ? deviceId : "",
    deviceCount: deviceCanBind && active ? registeredDeviceIds.length + 1 : registeredDeviceIds.length,
    maxDevices,
    message: updateRequired
      ? "앱 업데이트가 필요합니다."
      : licenseMessage(licenseStatus, active),
    latestVersionName: versionPolicy.latestVersionName || "",
    packageName,
  };
  });
}

async function registerLicenseDevice(payload) {
  return withLicenseStoreLock(async () => {
  const store = await readLicenseStore();
  const edition = normalizeLicenseAppEdition(payload.edition);
  const email = sanitizeEmail(payload.email);
  const phone = normalizePhone(payload.phone);
  const deviceId = sanitizeDeviceId(payload.deviceId);
  if (!edition || (!email && !phone) || !deviceId) {
    return { ok: false, error: "edition, email or phone, and deviceId are required" };
  }
  const license = findLicense(store, { edition, email, phone });
  if (!license) return { ok: false, error: "license not found" };
  const result = addLicenseDeviceId(license, edition, deviceId);
  if (!result.ok) {
    return { ok: false, error: result.error, maxDevices: result.maxDevices, deviceCount: result.deviceCount };
  }
  license.status = license.status || "active";
  license.updatedAt = new Date().toISOString();
  await writeLicenseStore(store);
  return {
    ok: true,
    licenseStatus: license.status,
    deviceId,
    deviceCount: licenseDeviceList(license).length,
    maxDevices: licenseMaxDevices(license),
  };
  });
}

function listLicenses(store) {
  const now = Date.now();
  return store.licenses.map((license) => {
    const expiresAt = sanitizeLicenseText(license.expiresAt || license.expires_at);
    const expiresAtMillis = expiresAt ? Date.parse(expiresAt) : NaN;
    const daysRemaining = Number.isFinite(expiresAtMillis)
      ? Math.ceil((expiresAtMillis - now) / (24 * 60 * 60 * 1000))
      : null;
    const deviceIds = licenseDeviceIds(license);
    const devices = licenseDeviceList(license);
    const maxDevices = licenseMaxDevices(license);
    const deviceSuffixes = devices.map((deviceId) => deviceId.slice(-6));
    return {
      id: sanitizeLicenseText(license.id),
      name: sanitizeLicenseText(license.name),
      email: sanitizeEmail(license.email),
      phone: normalizePhone(license.phone),
      edition: normalizeLicenseProductEdition(license.edition) || LICENSE_PRODUCT_EDITION,
      status: sanitizeLicenseText(license.status).toLowerCase() || "inactive",
      startedAt: sanitizeLicenseText(license.startedAt || license.started_at),
      expiresAt,
      daysRemaining,
      deviceBound: deviceSuffixes.length > 0,
      deviceCount: devices.length,
      maxDevices,
      deviceIdSuffix: deviceSuffixes.join(", "),
      devices,
      deviceSlots: deviceIds,
      allowDeviceBind: license.allowDeviceBind !== false,
      memo: sanitizeLicenseText(license.memo),
      updatedAt: sanitizeLicenseText(license.updatedAt || license.updated_at),
    };
  }).sort((a, b) => {
    const aExpires = Date.parse(a.expiresAt || "") || Number.MAX_SAFE_INTEGER;
    const bExpires = Date.parse(b.expiresAt || "") || Number.MAX_SAFE_INTEGER;
    return aExpires - bExpires;
  });
}

async function upsertLicense(payload) {
  return withLicenseStoreLock(async () => {
  const store = await readLicenseStore();
  const requestEdition = normalizeLicenseAppEdition(payload.edition || LICENSE_PRODUCT_EDITION);
  const edition = normalizeLicenseProductEdition(requestEdition);
  const email = sanitizeEmail(payload.email);
  const phone = normalizePhone(payload.phone);
  const name = sanitizeLicenseText(payload.name);
  const status = sanitizeLicenseText(payload.status).toLowerCase() || "trial";
  const memo = sanitizeLicenseText(payload.memo);
  const resetDevice = Boolean(payload.resetDevice);
  const allowDeviceBind = payload.allowDeviceBind !== false;
  const maxDevices = sanitizeMaxDevices(payload.maxDevices || payload.max_devices);
  const extendDays = Math.max(0, Math.min(366, Number(payload.extendDays) || 0));
  const requestedExpiresAt = sanitizeLicenseText(payload.expiresAt || payload.expires_at);

  if (edition !== LICENSE_PRODUCT_EDITION) {
    return { ok: false, error: "edition must be catchpro-pro" };
  }
  if (!email && !phone) {
    return { ok: false, error: "email or phone is required" };
  }
  if (!["trial", "active", "past_due", "expired", "blocked", "device_change_pending"].includes(status)) {
    return { ok: false, error: "unsupported status" };
  }

  let license = findLicense(store, { edition: requestEdition, email, phone });
  const now = new Date();
  const created = !license;
  if (!license) {
    license = {
      id: sanitizeLicenseText(payload.id) || makeLicenseId(edition),
      startedAt: now.toISOString(),
      maxDevices,
      devices: [],
      deviceIds: {},
    };
    store.licenses.push(license);
  }

  license.name = name || license.name || "";
  license.email = email || sanitizeEmail(license.email);
  license.phone = phone || normalizePhone(license.phone);
  license.edition = edition;
  license.status = status;
  license.allowDeviceBind = allowDeviceBind;
  license.maxDevices = maxDevices;
  license.memo = memo || license.memo || "";
  license.updatedAt = now.toISOString();

  if (resetDevice) {
    clearLicenseDevices(license);
  }

  if (requestedExpiresAt) {
    const parsed = Date.parse(requestedExpiresAt);
    if (!Number.isFinite(parsed)) {
      return { ok: false, error: "expiresAt is invalid" };
    }
    license.expiresAt = new Date(parsed).toISOString();
  } else if (extendDays > 0) {
    const currentExpiresAt = Date.parse(sanitizeLicenseText(license.expiresAt || license.expires_at));
    const base = Number.isFinite(currentExpiresAt) && currentExpiresAt > now.getTime()
      ? currentExpiresAt
      : now.getTime();
    license.expiresAt = new Date(base + extendDays * 24 * 60 * 60 * 1000).toISOString();
  } else if (!license.expiresAt && ["trial", "active"].includes(status)) {
    license.expiresAt = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000).toISOString();
  }

  await writeLicenseStore(store);
  const requestHash = sha256(`${edition}|${email}|${phone}`);
  console.log(`LICENSE_UPSERT created=${created} status=${license.status} requestHash=${requestHash} edition=${edition}`);
  return {
    ok: true,
    created,
    license: listLicenses({ licenses: [license] })[0],
  };
  });
}

function makeLicenseId(edition) {
  const stamp = new Date().toISOString().slice(0, 10).replace(/-/g, "");
  const random = crypto.randomBytes(3).toString("hex");
  return `${edition}-${stamp}-${random}`;
}

function findLicense(store, request) {
  const licenses = store.licenses.filter((license) => {
    const licenseEdition = sanitizeLicenseText(license.edition).toLowerCase();
    if (!licenseEditionMatches(licenseEdition, request.edition)) return false;
    const licenseEmail = sanitizeEmail(license.email);
    const licensePhone = normalizePhone(license.phone);
    return (request.email && licenseEmail === request.email) ||
      (request.phone && licensePhone === request.phone);
  });
  return licenses.find((license) => sanitizeLicenseText(license.edition).toLowerCase() === LICENSE_PRODUCT_EDITION) ||
    licenses[0];
}

function normalizeLicenseAppEdition(value) {
  const edition = sanitizeLicenseText(value).toLowerCase();
  return LICENSE_APP_EDITIONS.has(edition) ? edition : "";
}

function normalizeLicenseProductEdition(value) {
  return normalizeLicenseAppEdition(value) ? LICENSE_PRODUCT_EDITION : "";
}

function licenseEditionMatches(licenseEdition, requestEdition) {
  const licenseProduct = normalizeLicenseProductEdition(licenseEdition);
  const requestProduct = normalizeLicenseProductEdition(requestEdition);
  return Boolean(licenseProduct && requestProduct && licenseProduct === requestProduct);
}

function licenseDeviceIds(license) {
  const slots = {};
  if (license.deviceIds && typeof license.deviceIds === "object") {
    for (const edition of ["insung-pro", "navi-pro"]) {
      const deviceId = sanitizeDeviceId(license.deviceIds[edition]);
      if (deviceId) slots[edition] = deviceId;
    }
  }
  const legacyDeviceId = sanitizeDeviceId(license.deviceId || license.device_id);
  const legacyEdition = normalizeLicenseAppEdition(license.edition);
  if (legacyDeviceId && legacyEdition && legacyEdition !== LICENSE_PRODUCT_EDITION && !slots[legacyEdition]) {
    slots[legacyEdition] = legacyDeviceId;
  }
  return slots;
}

function licenseDeviceId(license, edition) {
  const deviceIds = licenseDeviceIds(license);
  return sanitizeDeviceId(deviceIds[edition]);
}

function licenseDeviceList(license) {
  const devices = [];
  const addDevice = (value) => {
    const deviceId = sanitizeDeviceId(
      value && typeof value === "object" ? value.deviceId || value.id : value,
    );
    if (deviceId && !devices.includes(deviceId)) devices.push(deviceId);
  };

  if (Array.isArray(license.devices)) {
    license.devices.forEach(addDevice);
  }
  Object.values(licenseDeviceIds(license)).forEach(addDevice);
  addDevice(license.deviceId || license.device_id);
  return devices;
}

function sanitizeMaxDevices(value) {
  const maxDevices = Number(value) || DEFAULT_LICENSE_MAX_DEVICES;
  return Math.max(1, Math.min(5, Math.floor(maxDevices)));
}

function licenseMaxDevices(license) {
  return sanitizeMaxDevices(license.maxDevices || license.max_devices);
}

function addLicenseDeviceId(license, edition, deviceId) {
  const normalizedEdition = normalizeLicenseAppEdition(edition);
  if (!normalizedEdition || normalizedEdition === LICENSE_PRODUCT_EDITION) {
    return { ok: false, error: "invalid edition" };
  }
  const normalizedDeviceId = sanitizeDeviceId(deviceId);
  if (!normalizedDeviceId) {
    return { ok: false, error: "deviceId is required" };
  }

  const devices = licenseDeviceList(license);
  const maxDevices = licenseMaxDevices(license);
  if (!devices.includes(normalizedDeviceId)) {
    if (devices.length >= maxDevices) {
      return {
        ok: false,
        error: "device limit reached",
        deviceCount: devices.length,
        maxDevices,
      };
    }
    devices.push(normalizedDeviceId);
  }

  license.devices = devices;
  license.deviceIds = licenseDeviceIds(license);
  if (!license.deviceIds[normalizedEdition]) {
    license.deviceIds[normalizedEdition] = normalizedDeviceId;
  }
  delete license.deviceId;
  delete license.device_id;
  return { ok: true, deviceCount: devices.length, maxDevices };
}

function clearLicenseDevices(license) {
  license.devices = [];
  license.deviceIds = {};
  delete license.deviceId;
  delete license.device_id;
}

function licenseMessage(status, active) {
  if (active) return "구독 사용 가능";
  if (status === "expired") return "구독 기간이 만료되었습니다.";
  if (status === "blocked") return "라이선스가 차단되었습니다.";
  if (status === "missing_device") return "기기 인증 정보가 없습니다.";
  if (status === "device_limit") return "등록 가능한 기기 수를 초과했습니다. 관리자에게 기기 초기화를 요청해 주세요.";
  if (status === "device_mismatch") return "등록된 기기와 현재 기기가 다릅니다.";
  return "라이선스가 활성 상태가 아닙니다.";
}

function sanitizeLicenseText(value) {
  return String(value || "").trim().slice(0, 120);
}

function sanitizeEmail(value) {
  return String(value || "").trim().toLowerCase().slice(0, 120);
}

function normalizePhone(value) {
  return String(value || "").replace(/\D/g, "").slice(0, 20);
}

function sanitizeDeviceId(value) {
  return String(value || "").replace(/[^\w.-]/g, "").slice(0, 120);
}

function roomOf(code) {
  let room = rooms.get(code);
  if (!room) {
    room = { clients: new Set(), last: null, expiresAt: Date.now() + ROOM_TTL_MS };
    rooms.set(code, room);
  }
  room.expiresAt = Date.now() + ROOM_TTL_MS;
  return room;
}

function send(ws, msg) {
  if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(msg));
}

function broadcast(room, msg, except) {
  for (const client of room.clients) {
    if (client !== except) send(client, msg);
  }
}

wss.on("connection", (ws) => {
  ws.roomCode = null;

  ws.on("message", (raw) => {
    let msg;
    try { msg = JSON.parse(raw.toString()); } catch { return; }

    if (msg.type === "join") {
      const code = String(msg.roomCode || "").replace(/\D/g, "").slice(0, 6);
      if (code.length !== 6) {
        send(ws, { type: "error", message: "roomCode must be 6 digits" });
        return;
      }
      ws.roomCode = code;
      const room = roomOf(code);
      room.clients.add(ws);
      send(ws, { type: "joined", roomCode: code, serverTime: Date.now() });
      if (room.last) send(ws, { type: "snapshot", payload: room.last });
      return;
    }

    if (msg.type === "update" && ws.roomCode) {
      const room = roomOf(ws.roomCode);
      const payload = {
        addresses: Array.isArray(msg.addresses) ? msg.addresses.slice(0, 6) : [],
        activeDriveDestination: msg.activeDriveDestination || "",
        updatedAt: Date.now(),
      };
      room.last = payload;
      send(ws, { type: "ack", updatedAt: payload.updatedAt });
      broadcast(room, { type: "snapshot", payload }, ws);
    }
  });

  ws.on("close", () => {
    if (!ws.roomCode) return;
    const room = rooms.get(ws.roomCode);
    if (room) room.clients.delete(ws);
  });
});

setInterval(() => {
  const now = Date.now();
  for (const [code, room] of rooms) {
    if (room.clients.size === 0 && room.expiresAt < now) rooms.delete(code);
  }
  for (const [key, entry] of responseCache) {
    if (entry.expiresAt < now) responseCache.delete(key);
  }
  for (const [ip, entry] of rateLimits) {
    if (entry.resetAt < now) rateLimits.delete(ip);
  }
}, 60_000);

server.listen(PORT, "127.0.0.1", () => {
  console.log(`CatchPro sync server listening on 127.0.0.1:${PORT}`);
});

async function shutdown() {
  try {
    if (redisClient) await redisClient.quit();
  } catch {
    // Ignore shutdown cleanup errors.
  }
  process.exit(0);
}

process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);
