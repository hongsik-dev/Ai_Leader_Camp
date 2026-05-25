#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

const defaultApiBase = "https://hongsik.blog";
const apiBase = (process.env.CATCHPRO_LICENSE_API_BASE || process.env.CATCHPRO_API_BASE || defaultApiBase).replace(/\/+$/, "");
const adminToken = process.env.CATCHPRO_LICENSE_ADMIN_TOKEN || "";
const defaultEdition = "catchpro-pro";

function usage() {
  console.log(`Usage:
  node scripts/license/catchpro-license-admin.mjs list [--within-days 7] [--status active]
  node scripts/license/catchpro-license-admin.mjs trial --name "고객명" (--phone "010..." | --email "a@b.com") [--days 30] [--memo "..."] [--notion] [--dry-run]
  node scripts/license/catchpro-license-admin.mjs extend (--phone "010..." | --email "a@b.com") [--days 30] [--memo "..."] [--notion] [--dry-run]
  node scripts/license/catchpro-license-admin.mjs reset-device (--phone "010..." | --email "a@b.com") [--notion] [--dry-run]
  node scripts/license/catchpro-license-admin.mjs expire (--phone "010..." | --email "a@b.com") [--notion] [--dry-run]
  node scripts/license/catchpro-license-admin.mjs block (--phone "010..." | --email "a@b.com") [--notion] [--dry-run]
  node scripts/license/catchpro-license-admin.mjs check --edition insung-pro --device "device-id" (--phone "010..." | --email "a@b.com")
  node scripts/license/catchpro-license-admin.mjs register-device --edition navi-pro --device "device-id" (--phone "010..." | --email "a@b.com") [--notion] [--dry-run]

Required env for admin write:
  CATCHPRO_LICENSE_ADMIN_TOKEN

Optional env:
  CATCHPRO_LICENSE_API_BASE=https://hongsik.blog
  NOTION_TOKEN
  NOTION_DATABASE_ID`);
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

function boolArg(value) {
  return value === true || ["1", "true", "yes", "y", "on"].includes(String(value).toLowerCase());
}

function normalize(value) {
  return String(value || "").trim();
}

function normalizePhone(value) {
  return normalize(value).replace(/\D/g, "");
}

function sanitizeEmail(value) {
  return normalize(value).toLowerCase();
}

function contactPayload(args) {
  const phone = normalizePhone(args.phone);
  const email = sanitizeEmail(args.email);
  if (!phone && !email) {
    throw new Error("--phone 또는 --email 값이 필요합니다.");
  }
  return { phone, email };
}

function maskName(value) {
  const text = normalize(value);
  if (!text) return "";
  if (text.length <= 2) return `${text[0] || ""}*`;
  return `${text[0]}${"*".repeat(Math.min(3, text.length - 2))}${text[text.length - 1]}`;
}

function maskContact({ phone, email }) {
  if (phone) {
    if (phone.length >= 8) return `${phone.slice(0, 3)}-****-${phone.slice(-4)}`;
    return "***";
  }
  if (email) {
    const [name, domain] = email.split("@", 2);
    const safeName = name.length <= 2 ? `${name[0] || ""}*` : `${name.slice(0, 2)}***`;
    return `${safeName}@${domain || "***"}`;
  }
  return "";
}

function maskDevice(value) {
  const text = normalize(value);
  if (!text) return "";
  if (text.length <= 6) return "***";
  return `${text.slice(0, 4)}...${text.slice(-4)}`;
}

function requireAdminToken() {
  if (!adminToken) {
    throw new Error("CATCHPRO_LICENSE_ADMIN_TOKEN 환경변수가 필요합니다.");
  }
}

async function requestJson(pathname, { method = "GET", body, admin = true } = {}) {
  if (admin) requireAdminToken();
  const response = await fetch(`${apiBase}${pathname}`, {
    method,
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json; charset=utf-8",
      ...(admin ? { "X-CatchPro-Admin-Token": adminToken } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  const json = text ? JSON.parse(text) : {};
  if (!response.ok || json.ok === false) {
    throw new Error(json.error || json.message || `HTTP ${response.status}`);
  }
  return json;
}

function buildMutation(command, args) {
  const contact = contactPayload(args);
  const days = Math.max(1, Math.min(366, Number(args.days || 30)));
  const memo = normalize(args.memo);
  const base = {
    ...contact,
    edition: defaultEdition,
    allowDeviceBind: true,
    maxDevices: Number(args["max-devices"] || args.maxDevices || 2),
    memo,
  };

  if (command === "trial") {
    return {
      action: "trial",
      payload: {
        ...base,
        name: normalize(args.name),
        status: "trial",
        extendDays: days,
        memo: memo || `${days}일 무료체험`,
      },
    };
  }
  if (command === "extend") {
    return {
      action: "extend-1m",
      payload: {
        ...base,
        name: normalize(args.name),
        status: "active",
        extendDays: days,
        memo: memo || `${days}일 연장`,
      },
    };
  }
  if (command === "reset-device") {
    return {
      action: "device-reset",
      payload: { ...base, status: normalize(args.status) || "active", resetDevice: true, memo: memo || "기기 초기화" },
    };
  }
  if (command === "expire") {
    return {
      action: "expire",
      payload: { ...base, status: "expired", expiresAt: new Date().toISOString(), memo: memo || "만료 처리" },
    };
  }
  if (command === "block") {
    return {
      action: "block",
      payload: { ...base, status: "blocked", memo: memo || "차단 처리" },
    };
  }

  throw new Error(`지원하지 않는 변경 명령입니다: ${command}`);
}

function dryPlan(command, args, extra = {}) {
  const contact = args.phone || args.email ? contactPayload(args) : {};
  return {
    dryRun: true,
    command,
    apiBase,
    customer: maskName(args.name),
    contact: maskContact(contact),
    device: maskDevice(args.device),
    ...extra,
  };
}

function summarizeLicense(license) {
  return {
    name: maskName(license.name),
    contact: maskContact({ phone: license.phone, email: license.email }),
    status: license.status,
    edition: license.edition,
    expiresAt: license.expiresAt,
    daysRemaining: license.daysRemaining,
    devices: `${license.deviceCount || 0}/${license.maxDevices || 2}`,
    deviceSuffix: license.deviceIdSuffix || "",
    memo: license.memo || "",
  };
}

async function listLicenses(args) {
  const result = await requestJson("/api/license/list");
  let licenses = Array.isArray(result.licenses) ? result.licenses : [];
  if (args.status) {
    licenses = licenses.filter((license) => String(license.status || "") === String(args.status));
  }
  if (args["within-days"] || args.withinDays) {
    const days = Number(args["within-days"] || args.withinDays);
    licenses = licenses.filter((license) => {
      const remaining = Number(license.daysRemaining);
      return Number.isFinite(remaining) && remaining <= days;
    });
  }
  return licenses.map(summarizeLicense);
}

async function mutateLicense(command, args) {
  const mutation = buildMutation(command, args);
  if (boolArg(args["dry-run"]) || boolArg(args.dryRun)) {
    return dryPlan(command, args, {
      action: mutation.action,
      status: mutation.payload.status,
      extendDays: mutation.payload.extendDays,
      resetDevice: mutation.payload.resetDevice || false,
      maxDevices: mutation.payload.maxDevices,
    });
  }

  const result = await requestJson("/api/license/upsert", {
    method: "POST",
    body: mutation.payload,
  });
  if (boolArg(args.notion)) {
    writeNotionLog("license", {
      action: mutation.action,
      customer: mutation.payload.name,
      contact: mutation.payload.phone || mutation.payload.email,
      device: args.device || "",
      memo: mutation.payload.memo,
    });
  }
  return {
    ok: true,
    action: mutation.action,
    created: result.created,
    license: summarizeLicense(result.license || {}),
  };
}

async function checkLicense(args) {
  const contact = contactPayload(args);
  const body = {
    ...contact,
    edition: normalize(args.edition || "insung-pro"),
    deviceId: normalize(args.device),
    packageName: normalize(args.package || ""),
    versionCode: Number(args["version-code"] || args.versionCode || 0),
  };
  if (!body.deviceId) throw new Error("--device 값이 필요합니다.");
  if (boolArg(args["dry-run"]) || boolArg(args.dryRun)) {
    return dryPlan("check", args, { edition: body.edition });
  }
  return requestJson("/api/license/check", { method: "POST", body, admin: false });
}

async function registerDevice(args) {
  const contact = contactPayload(args);
  const body = {
    ...contact,
    edition: normalize(args.edition || "navi-pro"),
    deviceId: normalize(args.device),
  };
  if (!body.deviceId) throw new Error("--device 값이 필요합니다.");
  if (boolArg(args["dry-run"]) || boolArg(args.dryRun)) {
    return dryPlan("register-device", args, { edition: body.edition });
  }
  const result = await requestJson("/api/license/register-device", {
    method: "POST",
    body,
  });
  if (boolArg(args.notion)) {
    writeNotionLog("license", {
      action: "register-device",
      customer: args.name || "",
      contact: body.phone || body.email,
      device: body.deviceId,
      memo: `기기 등록 ${body.edition}`,
    });
  }
  return {
    ok: true,
    edition: body.edition,
    licenseStatus: result.licenseStatus,
    device: maskDevice(result.deviceId || body.deviceId),
    deviceCount: result.deviceCount,
    maxDevices: result.maxDevices,
  };
}

function writeNotionLog(kind, fields) {
  const scriptDir = path.dirname(fileURLToPath(import.meta.url));
  const opsScript = path.resolve(scriptDir, "..", "notion", "catchpro-ops-log.mjs");
  const args = [opsScript, kind];
  for (const [key, value] of Object.entries(fields)) {
    if (value === undefined || value === null || value === "") continue;
    args.push(`--${key}`, String(value));
  }
  const result = spawnSync(process.execPath, args, {
    stdio: "pipe",
    encoding: "utf8",
    env: process.env,
  });
  if (result.status !== 0) {
    console.error(result.stderr.trim() || "Notion 운영 로그 기록 실패");
    return;
  }
  const stdout = result.stdout.trim();
  if (stdout) console.error(`Notion logged: ${stdout}`);
}

async function main() {
  const [command, ...rest] = process.argv.slice(2);
  if (!command || command === "help" || command === "--help") {
    usage();
    return;
  }
  const args = parseArgs(rest);

  let result;
  if (command === "list") result = await listLicenses(args);
  else if (["trial", "extend", "reset-device", "expire", "block"].includes(command)) result = await mutateLicense(command, args);
  else if (command === "check") result = await checkLicense(args);
  else if (command === "register-device") result = await registerDevice(args);
  else throw new Error(`지원하지 않는 명령입니다: ${command}`);

  console.log(JSON.stringify(result, null, 2));
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
