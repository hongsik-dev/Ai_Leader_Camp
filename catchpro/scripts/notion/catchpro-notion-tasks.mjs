#!/usr/bin/env node

const notionVersion = "2022-06-28";
const apiBase = "https://api.notion.com/v1";

const token = process.env.NOTION_TOKEN || process.env.NOTION_SECRET;
const databaseId = process.env.NOTION_DATABASE_ID;

function usage() {
  console.log(`Usage:
  node scripts/notion/catchpro-notion-tasks.mjs add --title "작업명" [--status 예정] [--priority P2] [--type Android] [--version 공통] [--memo "..."] [--checked false] [--sort-order 10] [--status-order 2]
  node scripts/notion/catchpro-notion-tasks.mjs update --title "작업명" [--status 진행중] [--github-pr URL] [--blog-url URL] [--memo "..."] [--checked true] [--sort-order 10] [--status-order 1]
  node scripts/notion/catchpro-notion-tasks.mjs append --title "작업명" [--heading "제목"] [--paragraph "내용"] [--bullets "항목1|항목2"]
  node scripts/notion/catchpro-notion-tasks.mjs done --title "작업명" [--github-pr URL] [--blog-url URL] [--memo "..."] [--checked true]
  node scripts/notion/catchpro-notion-tasks.mjs list [--status 진행중]

Required env:
  NOTION_TOKEN or NOTION_SECRET
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

function requireEnv() {
  if (!token) {
    throw new Error("NOTION_TOKEN 또는 NOTION_SECRET 환경변수가 필요합니다.");
  }
  if (!databaseId) {
    throw new Error("NOTION_DATABASE_ID 환경변수가 필요합니다.");
  }
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

function selectProperty(name) {
  return name ? { select: { name } } : undefined;
}

function dateProperty(value) {
  return value ? { date: { start: value } } : undefined;
}

function checkboxProperty(value) {
  if (value === undefined) return undefined;
  if (typeof value === "boolean") return { checkbox: value };
  return { checkbox: ["1", "true", "yes", "y", "on", "완료"].includes(String(value).toLowerCase()) };
}

function numberProperty(value) {
  if (value === undefined || value === "") return undefined;
  const number = Number(value);
  if (!Number.isFinite(number)) {
    throw new Error(`숫자 값이 필요합니다: ${value}`);
  }
  return { number };
}

function urlProperty(value) {
  return value ? { url: value } : undefined;
}

function richTextProperty(value) {
  return value ? { rich_text: [{ text: { content: value } }] } : undefined;
}

function richText(content) {
  return [{ text: { content } }];
}

function headingBlock(content) {
  return { object: "block", type: "heading_2", heading_2: { rich_text: richText(content) } };
}

function paragraphBlock(content) {
  return { object: "block", type: "paragraph", paragraph: { rich_text: richText(content) } };
}

function bulletBlock(content) {
  return { object: "block", type: "bulleted_list_item", bulleted_list_item: { rich_text: richText(content) } };
}

function compactProperties(properties) {
  return Object.fromEntries(Object.entries(properties).filter(([, value]) => value !== undefined));
}

function buildCreateProperties(args) {
  const today = new Date().toISOString().slice(0, 10);
  return compactProperties({
    이름: { title: [{ text: { content: args.title } }] },
    상태: selectProperty(args.status || "예정"),
    우선순위: selectProperty(args.priority || "P2"),
    구분: selectProperty(args.type || "운영"),
    버전: selectProperty(args.version || "공통"),
    작업일: dateProperty(args.date || today),
    "GitHub PR": urlProperty(args["github-pr"]),
    "블로그 URL": urlProperty(args["blog-url"]),
    메모: richTextProperty(args.memo),
    체크: checkboxProperty(args.checked),
    정렬순서: numberProperty(args["sort-order"] || args.sortOrder),
    상태순서: numberProperty(args["status-order"] || args.statusOrder),
  });
}

function buildUpdateProperties(args) {
  const properties = compactProperties({
    상태: selectProperty(args.status),
    우선순위: selectProperty(args.priority),
    구분: selectProperty(args.type),
    버전: selectProperty(args.version),
    작업일: dateProperty(args.date),
    완료일: dateProperty(args.doneDate || args["done-date"]),
    "GitHub PR": urlProperty(args["github-pr"]),
    "블로그 URL": urlProperty(args["blog-url"]),
    메모: richTextProperty(args.memo),
    체크: checkboxProperty(args.checked),
    정렬순서: numberProperty(args["sort-order"] || args.sortOrder),
    상태순서: numberProperty(args["status-order"] || args.statusOrder),
  });
  return properties;
}

async function createTask(args) {
  if (!args.title) throw new Error("--title 값이 필요합니다.");
  const result = await notionRequest("/pages", {
    method: "POST",
    body: {
      parent: { database_id: databaseId },
      properties: buildCreateProperties(args),
    },
  });
  return {
    id: result.id,
    url: result.url,
    title: args.title,
  };
}

async function findTaskByTitle(title) {
  const result = await notionRequest(`/databases/${databaseId}/query`, {
    method: "POST",
    body: {
      filter: {
        property: "이름",
        title: {
          equals: title,
        },
      },
      page_size: 1,
    },
  });
  return result.results[0] || null;
}

async function updateTask(args) {
  const pageId = args.id || (await findTaskByTitle(args.title))?.id;
  if (!pageId) {
    throw new Error("--id 또는 존재하는 --title 값이 필요합니다.");
  }
  const properties = buildUpdateProperties(args);
  if (Object.keys(properties).length === 0) {
    throw new Error("업데이트할 속성이 없습니다.");
  }
  const result = await notionRequest(`/pages/${pageId}`, {
    method: "PATCH",
    body: { properties },
  });
  return {
    id: result.id,
    url: result.url,
  };
}

async function appendTaskDetails(args) {
  const pageId = args.id || (await findTaskByTitle(args.title))?.id;
  if (!pageId) {
    throw new Error("--id 또는 존재하는 --title 값이 필요합니다.");
  }

  const children = [];
  if (args.heading) children.push(headingBlock(args.heading));
  if (args.paragraph) children.push(paragraphBlock(args.paragraph));
  if (args.bullets) {
    String(args.bullets)
      .split("|")
      .map((item) => item.trim())
      .filter(Boolean)
      .forEach((item) => children.push(bulletBlock(item)));
  }

  if (children.length === 0) {
    throw new Error("--heading, --paragraph, --bullets 중 하나가 필요합니다.");
  }

  const result = await notionRequest(`/blocks/${pageId}/children`, {
    method: "PATCH",
    body: { children },
  });
  return {
    id: pageId,
    appended: result.results.length,
  };
}

async function completeTask(args) {
  const today = new Date().toISOString().slice(0, 10);
  return updateTask({
    ...args,
    status: "완료",
    checked: args.checked ?? true,
    doneDate: args.doneDate || args["done-date"] || today,
  });
}

function plainTitle(page) {
  const title = page.properties?.이름?.title || [];
  return title.map((item) => item.plain_text || "").join("");
}

function plainSelect(page, property) {
  return page.properties?.[property]?.select?.name || "";
}

async function listTasks(args) {
  const body = {
    sorts: [{ property: "작업일", direction: "descending" }],
    page_size: Number(args.limit || 20),
  };
  if (args.status) {
    body.filter = {
      property: "상태",
      select: { equals: args.status },
    };
  }
  const result = await notionRequest(`/databases/${databaseId}/query`, {
    method: "POST",
    body,
  });
  return result.results.map((page) => ({
    id: page.id,
    title: plainTitle(page),
    status: plainSelect(page, "상태"),
    priority: plainSelect(page, "우선순위"),
    type: plainSelect(page, "구분"),
    version: plainSelect(page, "버전"),
    checked: page.properties?.체크?.checkbox || false,
    url: page.url,
  }));
}

async function main() {
  const [command, ...rest] = process.argv.slice(2);
  if (!command || command === "help" || command === "--help") {
    usage();
    return;
  }

  requireEnv();
  const args = parseArgs(rest);

  let result;
  if (command === "add") {
    result = await createTask(args);
  } else if (command === "update") {
    result = await updateTask(args);
  } else if (command === "append") {
    result = await appendTaskDetails(args);
  } else if (command === "done") {
    result = await completeTask(args);
  } else if (command === "list") {
    result = await listTasks(args);
  } else {
    throw new Error(`지원하지 않는 명령입니다: ${command}`);
  }

  console.log(JSON.stringify(result, null, 2));
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
