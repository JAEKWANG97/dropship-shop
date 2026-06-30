#!/usr/bin/env node
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";

const USER_AGENT =
  "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
const DEFAULT_OUT_DIR = "tmp/domeggook-products";

function usage() {
  console.log(`Usage:
  node scripts/collect-domeggook-product.mjs https://mobile.domeggook.com/8667274
  node scripts/collect-domeggook-product.mjs --file tmp/domeggook-urls.txt

Output:
  tmp/domeggook-products/{itemNo}/product.json
  tmp/domeggook-products/{itemNo}/product.csv
  tmp/domeggook-products/{itemNo}/images/*`);
}

function parseArgs(argv) {
  if (argv.includes("--help") || argv.includes("-h")) return { help: true };

  const fileIndex = argv.indexOf("--file");
  if (fileIndex !== -1) {
    const file = argv[fileIndex + 1];
    if (!file) throw new Error("--file path is required");
    return { file };
  }

  const urls = argv.filter((arg) => !arg.startsWith("--"));
  if (urls.length === 0) throw new Error("상품 URL 또는 --file 이 필요합니다.");
  return { urls };
}

async function readUrls(args) {
  if (args.urls) return args.urls;

  const content = await readFile(args.file, "utf8");
  return content
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#"));
}

async function fetchText(url) {
  const response = await fetch(url, {
    headers: {
      "user-agent": USER_AGENT,
      accept: "text/html,application/xhtml+xml",
      "accept-language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
    },
  });
  if (!response.ok) throw new Error(`페이지 접근 실패: ${response.status} ${response.statusText}`);
  return response.text();
}

async function fetchBuffer(url) {
  const response = await fetch(url, {
    headers: {
      "user-agent": USER_AGENT,
      accept: "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
      referer: "https://mobile.domeggook.com/",
    },
  });
  if (!response.ok) throw new Error(`이미지 다운로드 실패: ${response.status} ${url}`);
  return {
    bytes: Buffer.from(await response.arrayBuffer()),
    contentType: response.headers.get("content-type") || "",
  };
}

function htmlDecode(value) {
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
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<[^>]+>/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function firstMatch(html, regex, field) {
  const match = html.match(regex);
  if (!match) throw new Error(`${field} 파싱 실패`);
  return cleanText(match[1]);
}

function optionalMatch(html, regex) {
  const match = html.match(regex);
  return match ? cleanText(match[1]) : "";
}

function parseBasicInfo(html, label) {
  const rows = [...html.matchAll(/<tr>\s*<th>([\s\S]*?)<\/th>\s*<td>([\s\S]*?)<\/td>\s*<\/tr>/gi)];
  const row = rows.find(([, th]) => cleanText(th) === label);
  return row ? cleanText(row[2]) : "";
}

function decodeJsString(value) {
  return JSON.parse(`"${value.replace(/\n/g, "\\n")}"`);
}

function parseDetailImages(html) {
  const detailBlock = html.match(/<div id="lItemContent">([\s\S]*?)<div id="lItemRecommBorder"/i)?.[1] || "";
  const parsed = detailBlock.match(/parseFromString\("((?:\\.|[^"\\])*)",\s*'text\/html'\)/i);
  if (!parsed) return [];

  const detailHtml = decodeJsString(parsed[1]);
  return [
    ...new Set(
      [...detailHtml.matchAll(/<img\b[^>]*(?:src|data-src)=["']([^"']+)["']/gi)].map(([, src]) =>
        htmlDecode(src).trim(),
      ),
    ),
  ];
}

function parseProduct(url, html) {
  const itemNo = url.match(/\/(\d+)(?:[/?#]|$)/)?.[1] || parseBasicInfo(html, "상품번호");
  if (!itemNo) throw new Error("상품번호 파싱 실패");

  const thumbnailImageUrl = htmlDecode(
    html.match(/<img class="lItemImg"[^>]+src="([^"]+)"/i)?.[1] || "",
  );
  if (!thumbnailImageUrl) throw new Error("대표 이미지 파싱 실패");

  const imageUsage = parseBasicInfo(html, "이미지사용");
  if (!imageUsage) throw new Error("이미지사용 파싱 실패");

  return {
    sourceUrl: url,
    itemNo,
    title: firstMatch(html, /<div class="lItemTitle[^"]*">([\s\S]*?)<\/div>/i, "상품명"),
    priceText: firstMatch(html, /<div class="lPrice">\s*([\s\S]*?)\s*<\/div>/i, "가격"),
    minOrderQuantityText: optionalMatch(html, /<div class="lMinQty">\s*<span>\s*([\s\S]*?)\s*<\/span>/i),
    sellerName: firstMatch(html, /<div class="lSeller">\s*([\s\S]*?)\s*<\/div>/i, "판매자"),
    origin: parseBasicInfo(html, "원산지"),
    manufacturer: parseBasicInfo(html, "제조사"),
    imageUsage,
    thumbnailImageUrl,
    detailImageUrls: parseDetailImages(html),
  };
}

function imageExt(url, contentType) {
  const ext = path.extname(new URL(url).pathname).replace(".", "").toLowerCase();
  if (["jpg", "jpeg", "png", "webp", "gif"].includes(ext)) return ext;
  if (contentType.includes("webp")) return "webp";
  if (contentType.includes("png")) return "png";
  if (contentType.includes("gif")) return "gif";
  return "jpg";
}

async function downloadImage(url, fileBase) {
  const { bytes, contentType } = await fetchBuffer(url);
  const filePath = `${fileBase}.${imageExt(url, contentType)}`;
  await writeFile(filePath, bytes);
  return filePath;
}

function csvEscape(value) {
  return `"${String(value ?? "").replace(/"/g, '""')}"`;
}

async function writeOutputs(product, dir) {
  const csvFields = [
    "itemNo",
    "title",
    "priceText",
    "minOrderQuantityText",
    "sellerName",
    "origin",
    "manufacturer",
    "imageUsage",
    "sourceUrl",
    "thumbnailImagePath",
    "detailImagePaths",
  ];
  const csv = [
    csvFields.join(","),
    csvFields.map((field) => csvEscape(Array.isArray(product[field]) ? product[field].join(" ") : product[field])).join(","),
  ].join("\n");

  await writeFile(path.join(dir, "product.json"), `${JSON.stringify(product, null, 2)}\n`);
  await writeFile(path.join(dir, "product.csv"), `${csv}\n`);
}

async function collect(url) {
  const html = await fetchText(url);
  const product = parseProduct(url, html);
  const dir = path.join(DEFAULT_OUT_DIR, product.itemNo);
  const imageDir = path.join(dir, "images");
  await mkdir(imageDir, { recursive: true });

  if (!product.imageUsage.includes("허용")) {
    throw new Error(`이미지사용 미허용 또는 확인 필요: ${product.imageUsage}`);
  }

  product.thumbnailImagePath = await downloadImage(product.thumbnailImageUrl, path.join(imageDir, "thumb"));
  product.detailImagePaths = [];

  for (let index = 0; index < product.detailImageUrls.length; index += 1) {
    const number = String(index + 1).padStart(2, "0");
    const imagePath = await downloadImage(product.detailImageUrls[index], path.join(imageDir, `detail-${number}`));
    product.detailImagePaths.push(imagePath);
  }

  await writeOutputs(product, dir);
  return product;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    usage();
    return;
  }

  const urls = await readUrls(args);
  for (const url of urls) {
    const product = await collect(url);
    console.log(`${product.itemNo} 수집 완료: ${product.title}`);
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
