#!/usr/bin/env node
import { readFile, writeFile } from "node:fs/promises";
import path from "node:path";

const USER_AGENT =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";
const DEFAULT_OUT = "tmp/domeggook-urls.txt";
const DEFAULT_JSON = "tmp/domeggook-search-candidates.json";

function usage() {
  console.log(`Usage:
  node scripts/collect-domeggook-search-urls.mjs "https://domeggook.com/main/item/itemList.php?sfc=ttl&sf=ttl&sw=..."
  node scripts/collect-domeggook-search-urls.mjs --file tmp/domeggook-search-pages.txt

Options:
  --out tmp/domeggook-urls.txt
  --json tmp/domeggook-search-candidates.json
  --limit 50
  --delay-ms 1000`);
}

function argValue(argv, name, fallback = "") {
  const index = argv.indexOf(name);
  return index === -1 ? fallback : argv[index + 1] || "";
}

function parseArgs(argv) {
  if (argv.includes("--help") || argv.includes("-h")) return { help: true };
  return {
    file: argValue(argv, "--file"),
    out: argValue(argv, "--out", DEFAULT_OUT),
    json: argValue(argv, "--json", DEFAULT_JSON),
    limit: Number(argValue(argv, "--limit", "50")),
    delayMs: Number(argValue(argv, "--delay-ms", "1000")),
    urls: argv.filter((arg) => !arg.startsWith("--") && !argv[argv.indexOf(arg) - 1]?.startsWith("--")),
  };
}

async function readUrls(args) {
  if (args.file) {
    return (await readFile(args.file, "utf8"))
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => line && !line.startsWith("#"));
  }
  if (args.urls.length) return args.urls;
  throw new Error("검색 결과 URL 또는 --file 이 필요합니다.");
}

async function fetchHtml(url) {
  const response = await fetch(url, {
    headers: {
      "user-agent": USER_AGENT,
      accept: "text/html,application/xhtml+xml",
      "accept-language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
    },
  });
  if (!response.ok) throw new Error(`검색 결과 접근 실패: ${response.status} ${url}`);
  const bytes = await response.arrayBuffer();
  const charset = response.headers.get("content-type")?.match(/charset=([^;]+)/i)?.[1] || "euc-kr";
  return new TextDecoder(charset).decode(bytes);
}

function htmlDecode(value = "") {
  return value
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&#x([0-9a-f]+);/gi, (_, hex) => String.fromCodePoint(Number.parseInt(hex, 16)))
    .replace(/&#(\d+);/g, (_, num) => String.fromCodePoint(Number.parseInt(num, 10)));
}

function cleanText(value = "") {
  return htmlDecode(value)
    .replace(/<br\s*\/?>/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function parseCandidates(sourceUrl, html) {
  const candidates = [];
  const seen = new Set();
  const blocks = [...html.matchAll(/<ol class="lItemList">([\s\S]*?)<\/ol>/gi)].map((match) => match[1]);

  for (const block of blocks) {
    for (const match of block.matchAll(/<a\s+href="\/(\d{6,})(?:\?[^"]*)?"[^>]*class="title[^"]*"[^>]*>([\s\S]*?)<\/a>/gi)) {
      const itemNo = match[1];
      if (seen.has(itemNo)) continue;
      seen.add(itemNo);
      candidates.push({
        itemNo,
        sourceUrl,
        productUrl: `https://mobile.domeggook.com/${itemNo}`,
        title: cleanText(match[2]),
      });
    }
  }

  return candidates;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) return usage();
  if (!Number.isFinite(args.limit) || args.limit <= 0) throw new Error("--limit must be a positive number");

  const urls = await readUrls(args);
  const all = [];
  const seen = new Set();

  for (let index = 0; index < urls.length; index += 1) {
    const html = await fetchHtml(urls[index]);
    for (const candidate of parseCandidates(urls[index], html)) {
      if (seen.has(candidate.itemNo)) continue;
      seen.add(candidate.itemNo);
      all.push(candidate);
      if (all.length >= args.limit) break;
    }
    if (all.length >= args.limit) break;
    if (index < urls.length - 1) await sleep(args.delayMs);
  }

  await writeFile(args.out, `${all.map((candidate) => candidate.productUrl).join("\n")}\n`);
  await writeFile(args.json, `${JSON.stringify({ items: all }, null, 2)}\n`);
  console.log(`${all.length}개 후보 저장: ${args.out}`);
  console.log(`검수용 JSON: ${args.json}`);
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
