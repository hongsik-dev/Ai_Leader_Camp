#!/usr/bin/env node

const notionVersion = "2022-06-28";
const apiBase = "https://api.notion.com/v1";

const token = process.env.NOTION_TOKEN || process.env.NOTION_SECRET;
const databaseId = process.env.NOTION_DATABASE_ID;

const operationDefaults = {
  license: { label: "라이선스", type: "라이선스", priority: "P0", version: "공통" },
  chatbot: { label: "카카오 챗봇", type: "상담", priority: "P1", version: "공통" },
  wordpress: { label: "WordPress 신청", type: "WordPress", priority: "P1", version: "공통" },
  blog: { label: "블로그", type: "블로그", priority: "P2", version: "공통" },
  server: { label: "서버", type: "AWS", priority: "P0", version: "공통" },
  release: { label: "배포", type: "배포", priority: "P0", version: "공통" },
};

function usage() {
  console.log(`Usage:
  node scripts/notion/catchpro-ops-log.mjs license --action trial --customer "홍길동" [--contact "010..."] [--device "..."] [--memo "..."] [--dry-run]
  node scripts/notion/catchpro-ops-log.mjs chatbot --action faq-update [--memo "..."] [--dry-run]
  node scripts/notion/catchpro-ops-log.mjs wordpress --action application-received [--customer "..."] [--application-id "..."] [--memo "..."] [--dry-run]
  node scripts/notion/catchpro-ops-log.mjs blog --action published --title "글 제목" [--url "..."] [--dry-run]
  node scripts/notion/catchpro-ops-log.mjs server --action deploy --memo "내용" [--dry-run]
  node scripts/notion/catchpro-ops-log.mjs release --action apk-built --version "navi-pro" [--github-pr "..."] [--dry-run]

Required env for live write:
  NOTION_TOKEN or NOTION_SECRET
  NOTION_DATABASE_ID

Privacy:
  연락처, 이메일, 기기값은 Notion에 저장하기 전에 자동 마스킹됩니다.`);
}

function parseArgs(argv) {
  const result = { _: [] };
  for (let i = 0; i < argv.length; i += 1) {
    const item = argv[i];
    if (!item.startsWith("--")) {
      result._.push(item);
      continue;
    }
    const [rawKey, rawValue] = item.slice(2).split("=", 2);
    const key = rawKey.trim();
    if (rawValue !== undefined) {
      result[key] = rawValue;
      continue;
    }
    const next = argv[i + 1];
    if (next && !next.startsWith("--")) {
      result[key] = next;
      i += 1;
    } else {
      result[key] = true;
    }
  }
  return result;
}

function requireEnv() {
  if (!token) throw new Error("NOTION_TOKEN 또는 NOTION_SECRET 환경변수가 필요합니다.");
  if (!databaseId) throw new Error("NOTION_DATABASE_ID 환경변수가 필요합니다.");
}

function isTruthy(value) {
  return ["1", "true", "yes", "y", "on"].includes(String(value).toLowerCase());
}

function today() {
  return new Date().toISOString().slice(0, 10);
}

function nowStamp() {
  const now = new Date();
  const yyyy = now.getFullYear();
  const mm = String(now.getMonth() + 1).padStart(2, "0");
  const dd = String(now.getDate()).padStart(2, "0");
  const hh = String(now.getHours()).padStart(2, "0");
  const mi = String(now.getMinutes()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}`;
}

function normalize(value) {
  return String(value || "").trim();
}

function maskCustomer(value) {
  const text = normalize(value);
  if (!text) return "";
  if (text.length <= 2) return `${text[0] || ""}*`;
  return `${text[0]}${"*".repeat(Math.min(3, text.length - 2))}${text[text.length - 1]}`;
}

function maskContact(value) {
  const text = normalize(value);
  if (!text) return "";
  if (text.includes("@")) {
    const [name, domain] = text.split("@", 2);
    const safeName = name.length <= 2 ? `${name[0] || ""}*` : `${name.slice(0, 2)}***`;
    return `${safeName}@${domain || "***"}`;
  }
  const digits = text.replace(/\D/g, "");
  if (digits.length >= 8) return `${digits.slice(0, 3)}-****-${digits.slice(-4)}`;
  if (text.length > 4) return `${text.slice(0, 2)}***${text.slice(-2)}`;
  return "***";
}

function maskDevice(value) {
  const text = normalize(value);
  if (!text) return "";
  if (text.length <= 6) return "***";
  return `${text.slice(0, 4)}...${text.slice(-4)}`;
}

function safeMemo(value) {
  return normalize(value)
    .replace(/\b\d{2,3}[-.\s]?\d{3,4}[-.\s]?\d{4}\b/g, "[연락처]")
    .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, "[이메일]")
    .replace(/ntn_[A-Za-z0-9]+/g, "[notion-token]")
    .replace(/(?:secret|token|key|password)\s*[:=]\s*\S+/gi, "$1=[hidden]")
    .slice(0, 1500);
}

function richText(content) {
  return [{ text: { content } }];
}

function titleProperty(value) {
  return { title: [{ text: { content: value } }] };
}

function selectProperty(value) {
  return value ? { select: { name: value } } : undefined;
}

function dateProperty(value) {
  return value ? { date: { start: value } } : undefined;
}

function urlProperty(value) {
  return value ? { url: value } : undefined;
}

function richTextProperty(value) {
  return value ? { rich_text: [{ text: { content: value } }] } : undefined;
}

function checkboxProperty(value) {
  return { checkbox: Boolean(value) };
}

function numberProperty(value) {
  if (value === undefined || value === "") return undefined;
  const number = Number(value);
  if (!Number.isFinite(number)) throw new Error(`숫자 값이 필요합니다: ${value}`);
  return { number };
}

function compactProperties(properties) {
  return Object.fromEntries(Object.entries(properties).filter(([, value]) => value !== undefined));
}

function bulletBlock(content) {
  return { object: "block", type: "bulleted_list_item", bulleted_list_item: { rich_text: richText(content) } };
}

function headingBlock(content) {
  return { object: "block", type: "heading_2", heading_2: { rich_text: richText(content) } };
}

function paragraphBlock(content) {
  return { object: "block", type: "paragraph", paragraph: { rich_text: richText(content) } };
}

async function notionRequest(path, { method = "GET", body } = {}) {
  const response = await fetch(`${apiBase}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      "Notion-Version": notionVersion,
      "Content-Type": "application/json",
    },
    body: body ? JSON.stringify(body) : undefined,
  });

  const text = await response.text();
  const json = text ? JSON.parse(text) : {};
  if (!response.ok) {
    const message = json.message || response.statusText;
    throw new Error(`Notion API ${response.status}: ${message}`);
  }
  return json;
}

function buildOperation(command, args) {
  const defaults = operationDefaults[command];
  if (!defaults) {
    throw new Error(`지원하지 않는 운영 로그 종류입니다: ${command}`);
  }

  const action = normalize(args.action || "record");
  const customer = maskCustomer(args.customer);
  const titleParts = ["운영 로그", defaults.label, action];
  if (customer) titleParts.push(customer);
  titleParts.push(nowStamp());

  const memo = safeMemo(args.memo);
  const details = [
    `종류: ${defaults.label}`,
    `작업: ${action}`,
    `기록 시각: ${nowStamp()}`,
  ];
  if (customer) details.push(`고객: ${customer}`);
  if (args.contact) details.push(`연락처: ${maskContact(args.contact)}`);
  if (args.device) details.push(`기기: ${maskDevice(args.device)}`);
  if (args["application-id"]) details.push(`신청 ID: ${safeMemo(args["application-id"])}`);
  if (args.title) details.push(`제목: ${safeMemo(args.title)}`);
  if (args.url) details.push(`URL: ${normalize(args.url)}`);
  if (memo) details.push(`메모: ${memo}`);

  return {
    title: args["task-title"] || titleParts.join(" - "),
    properties: compactProperties({
      이름: titleProperty(args["task-title"] || titleParts.join(" - ")),
      상태: selectProperty(args.status || "완료"),
      우선순위: selectProperty(args.priority || defaults.priority),
      구분: selectProperty(args.type || defaults.type),
      버전: selectProperty(args.version || defaults.version),
      작업일: dateProperty(args.date || today()),
      완료일: dateProperty(args["done-date"] || args.doneDate || today()),
      "GitHub PR": urlProperty(args["github-pr"]),
      "블로그 URL": urlProperty(command === "blog" ? args.url : args["blog-url"]),
      메모: richTextProperty(memo || `${defaults.label} ${action}`),
      체크: checkboxProperty(args.checked === undefined ? true : isTruthy(args.checked)),
      정렬순서: numberProperty(args["sort-order"] || args.sortOrder),
      상태순서: numberProperty(args["status-order"] || args.statusOrder),
    }),
    children: [
      headingBlock("운영 처리 기록"),
      ...details.map(bulletBlock),
      paragraphBlock("민감정보는 저장 전에 마스킹했습니다. 원본 연락처와 기기값은 WordPress 신청/라이선스 서버 원본에서만 확인합니다."),
    ],
  };
}

async function createOperationPage(operation) {
  const result = await notionRequest("/pages", {
    method: "POST",
    body: {
      parent: { database_id: databaseId },
      properties: operation.properties,
      children: operation.children,
    },
  });
  return {
    id: result.id,
    url: result.url,
    title: operation.title,
  };
}

async function main() {
  const [command, ...rest] = process.argv.slice(2);
  if (!command || command === "help" || command === "--help") {
    usage();
    return;
  }

  const args = parseArgs(rest);
  const operation = buildOperation(command, args);

  if (args["dry-run"] || args.dryRun) {
    console.log(JSON.stringify({ dryRun: true, ...operation }, null, 2));
    return;
  }

  requireEnv();
  const result = await createOperationPage(operation);
  console.log(JSON.stringify(result, null, 2));
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
