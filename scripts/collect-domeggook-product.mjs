#!/usr/bin/env node
import { mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import path from "node:path";
import {
  CATEGORY_KEYWORD_OVERRIDES,
  CUSTOMER_EXPOSURE_KEYWORDS,
  CATEGORY_NON_COMPLETE_PRODUCT_KEYWORDS,
  NON_COMPLETE_PRODUCT_KEYWORDS,
  NON_SAFETY_KEYWORDS,
  readCategoryDefinitions,
  resolveReviewCategory,
  scoreCategory,
  stopCustomOptions,
} from "./review-domeggook-products.mjs";

const USER_AGENT =
  "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
const DEFAULT_OUT_DIR = "tmp/domeggook-products";
const CATEGORY_FILE = "apps/web/src/lib/categories.ts";
const COVERAGE_OUT_DIR = "tmp/domeggook-category-coverage";
const OPEN_API_COVERAGE_OUT_DIR = "tmp/domeggook-open-api-coverage";
const SOURCE_DISCOVERY_OUT_DIR = "tmp/domeggook-source-discovery";
const DEFAULT_REFERENCE_FILE = "docs/domeggook-reference-items.txt";
const OPTION_BACKFILL_REPORT = "tmp/domeggook-option-backfill-report.json";
const SELLER_SCORE_BACKFILL_REPORT = "tmp/domeggook-seller-score-backfill-report.json";
const OPEN_API_REFRESH_REPORT = "tmp/domeggook-open-api-refresh-report.json";
const OPEN_API_COLLECTOR_VERSION = 10;
const OPEN_API_DAILY_LIMIT = 5000;
const OPEN_API_RANKING_SORT = "rd";

function usage() {
  console.log(`Usage:
  node scripts/collect-domeggook-product.mjs https://mobile.domeggook.com/8667274
  node scripts/collect-domeggook-product.mjs --file tmp/domeggook-urls.txt
  node scripts/collect-domeggook-product.mjs --backfill-options --limit 5
  node scripts/collect-domeggook-product.mjs --backfill-seller-score --limit 5
  node scripts/collect-domeggook-product.mjs --coverage-scan --target-per-category 5
  node scripts/collect-domeggook-product.mjs --coverage-scan --target-per-category 1 --max-categories 3
  node scripts/collect-domeggook-product.mjs --open-api-coverage --target-per-category 30
  node scripts/collect-domeggook-product.mjs --open-api-coverage --category PPE_SAFETY_HELMET --target-per-category 2
  node scripts/collect-domeggook-product.mjs --open-api-coverage --target-per-category 10 --ranking-count 60 --fresh
  node scripts/collect-domeggook-product.mjs --source-category-discovery --target-per-category 30
  node scripts/collect-domeggook-product.mjs --open-api-refresh
  node scripts/collect-domeggook-product.mjs --self-check

Output:
  tmp/domeggook-products/{itemNo}/product.json
  tmp/domeggook-products/{itemNo}/product.csv
  tmp/domeggook-products/{itemNo}/images/*
  tmp/domeggook-option-backfill-report.json
  tmp/domeggook-seller-score-backfill-report.json
  tmp/domeggook-category-coverage/*
  tmp/domeggook-open-api-coverage/*
  tmp/domeggook-source-discovery/*`);
}

function parseArgs(argv) {
  if (argv.includes("--help") || argv.includes("-h")) return { help: true };
  if (argv.includes("--self-check")) return { selfCheck: true };

  if (argv.includes("--open-api-coverage")) {
    return {
      openApiCoverage: true,
      category: stringArg(argv, "--category"),
      targetPerCategory: numberArg(argv, "--target-per-category", 30),
      rankingCount: numberArg(argv, "--ranking-count", numberArg(argv, "--sales-count", 60)),
      maxCategories: numberArg(argv, "--max-categories", 0),
      delayMs: Math.max(1000, numberArg(argv, "--delay-ms", 1000)),
      fresh: argv.includes("--fresh"),
    };
  }

  if (argv.includes("--source-category-discovery")) {
    return {
      sourceCategoryDiscovery: true,
      file: stringArg(argv, "--file", DEFAULT_REFERENCE_FILE),
      targetPerCategory: numberArg(argv, "--target-per-category", 30),
      rankingCount: numberArg(argv, "--ranking-count", 60),
      delayMs: Math.max(1000, numberArg(argv, "--delay-ms", 1000)),
    };
  }

  if (argv.includes("--open-api-refresh")) {
    return {
      openApiRefresh: true,
      limit: numberArg(argv, "--limit", 0),
      delayMs: Math.max(1000, numberArg(argv, "--delay-ms", 1000)),
    };
  }

  if (argv.includes("--backfill-options")) {
    return {
      backfillOptions: true,
      limit: numberArg(argv, "--limit", 0),
      delayMs: numberArg(argv, "--delay-ms", 250),
    };
  }

  if (argv.includes("--backfill-seller-score")) {
    return {
      backfillSellerScore: true,
      limit: numberArg(argv, "--limit", 0),
      delayMs: Math.max(1000, numberArg(argv, "--delay-ms", 1000)),
      fresh: argv.includes("--fresh"),
    };
  }

  if (argv.includes("--coverage-scan")) {
    return {
      coverageScan: true,
      targetPerCategory: numberArg(argv, "--target-per-category", 5),
      maxCategories: numberArg(argv, "--max-categories", 0),
      limit: numberArg(argv, "--limit", 0),
      delayMs: numberArg(argv, "--delay-ms", 350),
    };
  }

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

function numberArg(argv, name, fallback) {
  const index = argv.indexOf(name);
  if (index === -1) return fallback;
  const value = Number(argv[index + 1]);
  return Number.isFinite(value) ? value : fallback;
}

function stringArg(argv, name, fallback = "") {
  const index = argv.indexOf(name);
  return index === -1 ? fallback : argv[index + 1] || fallback;
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
  return String(value || "")
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

function parseTitle(html) {
  const titleFromDiv = cleanText(html.match(/<div class="lItemTitle[^"]*">([\s\S]*?)<\/div>/i)?.[1] || "");
  if (titleFromDiv) return titleFromDiv;
  const titleFromHead = optionalMatch(html, /<title>([\s\S]*?)<\/title>/i)
    .replace(/\s*\|\s*돈버는 쇼핑,\s*도매꾹\s*$/i, "")
    .trim();
  if (titleFromHead) return titleFromHead;
  throw new Error("상품명 파싱 실패");
}

function parseAssignedJson(html, variableName) {
  const regex = new RegExp(`\\b${variableName}\\s*=\\s*([\\[{][\\s\\S]*?);\\s*(?:\\/\\/[^\\n]*)?`, "m");
  const match = html.match(regex);
  if (!match) return null;
  return JSON.parse(match[1].trim());
}

function numberOrNull(value) {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number(String(value).replace(/,/g, ""));
  return Number.isFinite(parsed) ? parsed : null;
}

function parseOptions(html) {
  const optSet = parseAssignedJson(html, "optSet") || [];
  const optData = parseAssignedJson(html, "optData");
  if (!optData || Object.keys(optData).length === 0) return [defaultOption()];

  const optSoldOut = parseAssignedJson(html, "optSoldOut") || {};
  const orderedCodes = [
    ...new Set(
      optSet
        .flatMap((set) => (Array.isArray(set.changeKey) ? set.changeKey : []))
        .map(String)
        .map((code) => (optData[code] ? code : code.padStart(2, "0"))),
    ),
  ].filter((code) => optData[code]);
  const codes = orderedCodes.length > 0 ? orderedCodes : Object.keys(optData);
  return codes.map((code, index) => {
    const raw = optData[code];
    const option = raw || {};
    const sourceAdditionalPrice = numberOrNull(option.addPrice ?? option.domPrice) ?? 0;
    const sourceStockQuantity = numberOrNull(option.qty);
    const soldOutFlag = numberOrNull(optSoldOut[code]);
    const enabled = String(option.enable ?? "1") !== "0";
    const hidden = String(option.hid ?? "0") === "2";
    const inStock = sourceStockQuantity === null || sourceStockQuantity > 0;
    const notSoldOut = soldOutFlag === null || soldOutFlag > 0;
    return {
      sourceOptionCode: code,
      name: cleanText(option.name || option.title || code),
      sourceAdditionalPrice,
      sourceStockQuantity,
      status: enabled && !hidden && inStock && notSoldOut ? "ACTIVE" : "SOLD_OUT",
      sortOrder: index,
    };
  });
}

function defaultOption() {
  return {
    sourceOptionCode: "00",
    name: "기본",
    sourceAdditionalPrice: 0,
    sourceStockQuantity: null,
    status: "ACTIVE",
    sortOrder: 0,
  };
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
    title: parseTitle(html),
    priceText: firstMatch(html, /<div class="lPrice">\s*([\s\S]*?)\s*<\/div>/i, "가격"),
    minOrderQuantityText: optionalMatch(html, /<div class="lMinQty">\s*<span>\s*([\s\S]*?)\s*<\/span>/i),
    sellerName: firstMatch(html, /<div class="lSeller">\s*([\s\S]*?)\s*<\/div>/i, "판매자"),
    origin: parseBasicInfo(html, "원산지"),
    manufacturer: parseBasicInfo(html, "제조사"),
    imageUsage,
    thumbnailImageUrl,
    detailImageUrls: parseDetailImages(html),
    options: parseOptions(html),
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

function optionSummary(product) {
  return (product.options || [])
    .map((option) => {
      const price = Number(option.sourceAdditionalPrice || 0);
      const priceText = price === 0 ? "" : ` ${price > 0 ? "+" : ""}${price}원`;
      return `${option.name}${priceText} [${option.status}]`;
    })
    .join(" | ");
}

async function writeOutputs(product, dir) {
  const csvFields = [
    "itemNo",
    "title",
    "priceText",
    "minimumResalePrice",
    "minOrderQuantityText",
    "minimumOrderQuantity",
    "orderQuantityStep",
    "businessOrderAvailable",
    "sellerName",
    "sellerReviewCount",
    "sellerSatisfaction",
    "origin",
    "model",
    "manufacturer",
    "productSize",
    "productWeight",
    "deliveryMethod",
    "deliveryWaiting",
    "imageUsage",
    "optionCount",
    "options",
    "sourceUrl",
    "thumbnailImagePath",
    "detailImagePaths",
  ];
  const csvValues = {
    ...product,
    optionCount: product.options?.length || 0,
    options: optionSummary(product),
  };
  const csv = [
    csvFields.join(","),
    csvFields.map((field) => csvEscape(Array.isArray(csvValues[field]) ? csvValues[field].join(" ") : csvValues[field])).join(","),
  ].join("\n");

  await writeFile(path.join(dir, "product.json"), `${JSON.stringify(product, null, 2)}\n`);
  await writeFile(path.join(dir, "product.csv"), `${csv}\n`);
}

async function readExistingProduct(itemNo) {
  const filePath = path.join(DEFAULT_OUT_DIR, itemNo, "product.json");
  if (!existsSync(filePath)) return null;
  return JSON.parse(await readFile(filePath, "utf8"));
}

async function collect(url) {
  const html = await fetchText(url);
  const product = parseProduct(url, html);
  const existing = await readExistingProduct(product.itemNo);
  const dir = path.join(DEFAULT_OUT_DIR, product.itemNo);
  const imageDir = path.join(dir, "images");
  await mkdir(imageDir, { recursive: true });

  if (!product.imageUsage.includes("허용")) {
    throw new Error(`이미지사용 미허용 또는 확인 필요: ${product.imageUsage}`);
  }

  product.thumbnailImagePath = existing?.thumbnailImagePath
    || await downloadImage(product.thumbnailImageUrl, path.join(imageDir, "thumb"));
  product.detailImagePaths = existing?.detailImagePaths || [];

  if (product.detailImagePaths.length === 0) {
    for (let index = 0; index < product.detailImageUrls.length; index += 1) {
      const number = String(index + 1).padStart(2, "0");
      const imagePath = await downloadImage(product.detailImageUrls[index], path.join(imageDir, `detail-${number}`));
      product.detailImagePaths.push(imagePath);
    }
  }

  await writeOutputs(product, dir);
  return product;
}

async function backfillOptions(args) {
  const entries = await readdir(DEFAULT_OUT_DIR, { withFileTypes: true });
  const dirs = entries
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .filter((itemNo) => existsSync(path.join(DEFAULT_OUT_DIR, itemNo, "product.json")))
    .sort();
  const targets = args.limit > 0 ? dirs.slice(0, args.limit) : dirs;
  const report = { attempted: 0, succeeded: 0, failed: [] };

  for (const itemNo of targets) {
    report.attempted += 1;
    const filePath = path.join(DEFAULT_OUT_DIR, itemNo, "product.json");
    try {
      const product = JSON.parse(await readFile(filePath, "utf8"));
      if (!product.sourceUrl) throw new Error("sourceUrl missing");
      const html = await fetchText(product.sourceUrl);
      product.options = parseOptions(html);
      await writeOutputs(product, path.join(DEFAULT_OUT_DIR, itemNo));
      report.succeeded += 1;
      console.log(`${itemNo} 옵션 backfill 완료: ${product.options.length}개`);
    } catch (error) {
      report.failed.push({ itemNo, reason: error.message });
      console.error(`${itemNo} 옵션 backfill 실패: ${error.message}`);
    }
    await sleep(args.delayMs);
  }

  await writeFile(OPTION_BACKFILL_REPORT, `${JSON.stringify(report, null, 2)}\n`);
  console.log(`${OPTION_BACKFILL_REPORT} 저장 완료`);
}

async function coverageScan(args) {
  const categories = await readCategories();
  const selectedCategories = args.maxCategories > 0 ? categories.slice(0, args.maxCategories) : categories;
  const missing = selectedCategories.filter((category) => keywordsForCategory(category).length === 0);
  if (missing.length > 0) {
    throw new Error(`검색 키워드가 없는 카테고리: ${missing.map((category) => category.code).join(", ")}`);
  }

  await mkdir(COVERAGE_OUT_DIR, { recursive: true });
  const summary = [];

  for (const category of selectedCategories) {
    const categoryReport = {
      categoryCode: category.code,
      categoryLabel: category.label,
      keywords: keywordsForCategory(category),
      candidates: [],
      succeeded: [],
      failed: [],
      shortfall: false,
    };

    for (const keyword of categoryReport.keywords) {
      const urls = await searchUrls(keyword);
      categoryReport.candidates.push({ keyword, count: urls.length, urls });
      for (const url of urls) {
        if (categoryReport.succeeded.length >= args.targetPerCategory) break;
        if (args.limit > 0 && categoryReport.succeeded.length + categoryReport.failed.length >= args.limit) break;
        try {
          const product = await collect(url);
          categoryReport.succeeded.push({ itemNo: product.itemNo, title: product.title, sourceUrl: product.sourceUrl });
          console.log(`${category.code} 수집 성공: ${product.itemNo} ${product.title}`);
        } catch (error) {
          categoryReport.failed.push({ url, reason: error.message });
        }
        await sleep(args.delayMs);
      }
      if (categoryReport.succeeded.length >= args.targetPerCategory) break;
    }

    categoryReport.shortfall = categoryReport.succeeded.length < args.targetPerCategory;
    summary.push({
      categoryCode: category.code,
      categoryLabel: category.label,
      succeeded: categoryReport.succeeded.length,
      failed: categoryReport.failed.length,
      shortfall: categoryReport.shortfall,
    });
    await writeFile(
      path.join(COVERAGE_OUT_DIR, `${category.code}.json`),
      `${JSON.stringify(categoryReport, null, 2)}\n`,
    );
  }

  await writeFile(path.join(COVERAGE_OUT_DIR, "summary.json"), `${JSON.stringify(summary, null, 2)}\n`);
  console.log(`${COVERAGE_OUT_DIR}/summary.json 저장 완료`);
}

async function readCategories() {
  const content = await readFile(CATEGORY_FILE, "utf8");
  return [...content.matchAll(/\["([^"]+)",\s*"([^"]*)",\s*"([A-Z0-9_]+)",\s*"([^"]+)"\]/g)]
    .map((match) => ({
      group: match[1],
      subgroup: match[2],
      code: match[3],
      label: match[4],
    }));
}

function keywordsForCategory(category) {
  return [
    cleanKeyword(category.label),
    ...(CATEGORY_KEYWORD_OVERRIDES[category.code] || []),
  ].filter(Boolean);
}

function cleanKeyword(value) {
  return String(value || "")
    .replace(/[()]/g, " ")
    .replace(/[①②③④⑤⑥]/g, " ")
    .replace(/[·/]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

async function searchUrls(keyword) {
  const form = new FormData();
  form.append("kw", keyword);
  form.append("pg", "1");
  form.append("sz", "50");
  form.append("ext", "true");
  form.append("isItemList", "true");
  const response = await fetch("https://mobile.domeggook.com/api/v1/getItemList", {
    method: "POST",
    headers: {
      "user-agent": USER_AGENT,
      accept: "application/json",
      "accept-language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
    },
    body: form,
  });
  if (!response.ok) throw new Error(`검색 API 접근 실패: ${response.status} ${response.statusText}`);
  const result = await response.json();
  const itemNos = (result.data?.list || [])
    .map((item) => item.no || String(item.url || "").match(/\/(\d{6,})/)?.[1])
    .filter(Boolean)
    .map(String);
  return [...new Set(itemNos)].map((itemNo) => `https://mobile.domeggook.com/${itemNo}`);
}

const OBVIOUS_ACCESSORY_KEYWORDS = [
  "악세사리",
  "액세서리",
  "교체용",
  "리필",
  "부속품",
  "부품",
  "내피",
  "턱끈",
  "햇빛가리개",
  "햇빛 가리개",
  "땀받이",
  "헤드랜턴",
  "헬멧랜턴",
  "보관 케이스",
  "전용 케이스",
  "거치대",
  "스티커",
  "보호필름",
];

const CATEGORY_ACCESSORY_KEYWORDS = {
  PPE_SAFETY_HELMET: [
    "차양",
    "썬캡",
    "그늘이",
    "햇빛가리개",
    "귀덮개",
    "귀마개덮개",
    "방한패딩",
    "턱끈",
    "내피",
    "땀받이",
    "쿨링패드",
    "쿨링 패드",
    "아이스젤",
    "헬멧쿨러",
    "활선접근경보기",
    "클립",
    "걸이대",
    "보관대",
    "보관함",
    "안전모외피",
    "안전모덮개",
    "헬멧커버",
    "이름표",
    "식별표",
    "아이스팩",
    "쿨패드",
    "안면보호구",
    "보안면",
    "쉴드",
    "부착식",
    "귀보호대",
    "식별띠",
    "그늘막",
    "가이드 LED",
  ],
  PPE_SAFETY_SHOES: ["깔창", "인솔", "신발끈", "덧신"],
  PPE_RESPIRATOR: ["필터", "정화통", "흡수관", "면체부품"],
  PPE_SAFETY_GLASSES: ["보호필름", "교체렌즈", "보안경렌즈", "안경줄"],
  SAFETY_FENCE: [
    "하부베이스",
    "연결클램프",
    "파이프클램프",
    "휀스부속",
    "펜스부속",
    "랙보호",
    "모서리보호",
  ],
};

const OPEN_API_REQUIRED_TITLE_KEYWORDS = {
  PPE_SAFETY_HELMET: ["안전모", "산업용헬멧", "건설헬멧", "현장헬멧", "작업용헬멧"],
  PPE_SAFETY_SHOES: ["안전화", "산업용작업화", "건설작업화"],
  PPE_FALL_ARREST_HARNESS: ["안전대", "추락방지하네스", "전체식하네스", "전신하네스"],
  PPE_SAFETY_BELT: ["산업안전벨트", "작업용안전벨트", "추락방지안전벨트", "주상안전벨트", "안전그네"],
  PPE_SAFETY_GLASSES: ["보안경", "보호안경", "산업용고글", "작업용고글"],
  PPE_EAR_PROTECTION: [
    "산업용귀마개",
    "작업용귀마개",
    "산업용귀덮개",
    "청력보호",
    "공장용귀마개",
    "현장용귀마개",
  ],
  WORK_PLATFORM: ["작업발판", "작업대", "우마"],
  OPENING_COVER: ["개구부덮개", "개구부안전덮개", "맨홀커버"],
  SAFETY_BLOCK: ["안전블록", "안전블럭"],
  GAS_DETECTOR: ["가스감지기", "가스검지기", "누출감지기", "복합가스측정기", "휴대용가스측정기"],
  LIGHT_METER: ["조도계", "조명조도", "광량측정", "LUX", "LX"],
  VIBRATION_METER: ["디지털진동측정기", "휴대용진동측정기", "산업용진동측정기", "진동계", "진동분석기"],
  EYEWASH_STATION: ["비상세안기", "응급세안기", "산업용세안기", "눈세척기", "아이워시"],
  ACCESS_CONTROL_FACILITY: ["출입통제시스템", "출입통제게이트", "출입차단기", "턴스타일", "출입리더기", "출입단말기"],
  WORKER_LOCATION_TRACKING: ["작업자위치추적", "UWB위치추적", "BLE위치추적", "RFID위치추적", "위치추적태그"],
  SMART_WATCH: ["낙상감지", "SOS스마트워치", "산업용스마트워치", "작업자스마트워치"],
};

const OPEN_API_DISALLOWED_TITLE_KEYWORDS = {
  PPE_SAFETY_HELMET: [
    "오토바이",
    "바이크",
    "자전거",
    "라이딩",
    "스키",
    "스노우보드",
    "도난방지",
    "자물쇠",
    "꾸미기",
    "인형",
    "이너캡",
    "헤어밴드",
  ],
  PPE_SAFETY_BELT: [
    "자동차",
    "차량",
    "카시트",
    "유아",
    "아기",
    "고정클립",
    "스토퍼",
    "안전벨트커버",
    "안전벨트락",
  ],
  PPE_SAFETY_GLASSES: ["수영", "물안경", "오토바이", "바이크", "스키", "스노우보드"],
  PPE_EAR_PROTECTION: ["이어폰", "헤드폰", "블루투스", "수능", "집중력", "수면", "코골이"],
  FALL_PREVENTION_GUARDRAIL: [
    "나무",
    "목재",
    "핸드레일",
    "브라켓",
    "부속",
    "복도",
    "계단",
    "벽손잡이",
    "디자인난간",
  ],
  FALL_PREVENTION_NET: ["화물차", "트럭", "자동차", "차량", "적재함", "개구부덮개", "배수로"],
  FALLING_OBJECT_NET: ["화물차", "트럭", "자동차", "차량", "적재함"],
  WORK_PLATFORM: ["의류매장", "가정용", "화물차", "크레인", "유압", "계단사다리", "발판사다리"],
  OPENING_COVER: ["타공분진", "집진", "먼지방지", "전동공구"],
  SAFE_PASSAGE: ["웨빙띠", "폴리스라인", "접근금지", "차단테이프"],
  LIFELINE: ["안전블록", "안전블럭"],
  TRAFFIC_CONE: ["축구", "운동", "게임", "어린이", "운동회", "스포츠"],
  GAS_DETECTOR: ["잔량", "가스통", "저장탱크", "게이지", "탐지제", "점검제", "스프레이"],
  OXYGEN_METER: ["PH", "산가", "측정종이", "페이퍼", "당도", "전자파"],
  LIGHT_METER: [
    "토양",
    "PH",
    "토질",
    "수분측정",
    "표면조도",
    "윤곽",
    "내경",
    "3차원",
    "버니어",
    "캘리퍼스",
    "링게이지",
    "당도",
    "알코올",
  ],
  VIBRATION_METER: ["다이얼게이지", "다이알게이지", "측정봉", "게이지팁", "평탄도", "공구부속"],
  AED: ["보관함", "월케비넷", "벽걸이함", "캐비닛"],
  EYEWASH_STATION: ["클렌징", "피부", "화장품", "버블", "거품", "진동세안"],
  HEAT_COLD_PREVENTION_SUPPLIES: ["이불", "침구", "매트리스", "바디필로우", "베개", "쿠션"],
  VENTILATION_EQUIPMENT: ["그릴", "배기구커버", "환기구덮개", "환풍구커버"],
  SMART_CCTV_GENERAL_SPECIAL: ["표지판", "안내판", "안내문", "알림판", "사인"],
  SMART_CCTV_SOLAR_MOBILE: ["가짜", "모형", "더미"],
  WORKER_LOCATION_TRACKING: ["RFID차단", "지갑", "여권", "카드지갑", "파우치"],
  WORKER_ACCESS_CONTROL: ["리본", "카트리지", "소모품"],
  HEAVY_EQUIPMENT_COLLISION_PREVENTION: ["고양이", "반려", "문고정", "도어"],
  HEAVY_EQUIPMENT_REAR_DETECTOR: ["승용차", "주차도우미"],
  HEAVY_EQUIPMENT_PINCH_PREVENTION: ["장갑", "손가락"],
  IOT_TEMPERATURE_HUMIDITY_METER: ["아날로그"],
  ACCESS_CONTROL_FACILITY: ["표지판", "사인", "테이프", "안전띠", "출입금지띠"],
};

const OPEN_API_REQUIRED_CONTEXT_KEYWORDS = {
  PPE_EAR_PROTECTION: ["산업", "작업", "현장", "공장", "청력보호", "3M"],
  FALL_PREVENTION_GUARDRAIL: [
    "건설현장",
    "공사장",
    "추락방지",
    "단부",
    "철골",
    "갱폼",
    "가설",
    "슬라브",
    "시스템비계",
  ],
  WORK_PLATFORM: ["산업안전", "건설", "현장", "말비계", "우마", "고소작업", "비계"],
  SAFE_PASSAGE: ["보행", "작업통로", "통로발판", "가설통로", "공사장통로"],
  SAFETY_BLOCK: ["추락", "고소", "구명줄", "생명줄"],
  TRAFFIC_CONE: ["도로", "공사", "주차", "차량", "안전", "차단", "교통"],
  WARNING_SIGN: [
    "산업안전",
    "공사",
    "현장",
    "위험",
    "추락",
    "감전",
    "화재",
    "출입금지",
    "보호구",
    "고압",
    "낙하",
  ],
  SIGNAL_BATON: ["교통", "주차", "안전", "유도", "신호수"],
  BARRIER_TAPE: ["위험", "안전", "통제", "공사", "출입금지"],
  HEAT_COLD_PREVENTION_SUPPLIES: ["산업", "작업", "현장", "온열질환", "폭염", "혹한", "안전", "근로자"],
  EYEWASH_STATION: ["비상", "응급", "산업", "실험실", "화학", "작업장", "안전"],
  VENTILATION_EQUIPMENT: ["산업", "작업장", "공장", "유해", "송풍", "배풍"],
  SMART_CCTV_GENERAL_SPECIAL: ["카메라", "CCTV세트", "감시시스템", "녹화기", "NVR", "DVR"],
  WORKER_ACCESS_CONTROL: ["단말기", "리더기", "출입통제시스템", "출입관리시스템", "게이트", "도어락"],
  HEAVY_EQUIPMENT_COLLISION_PREVENTION: ["중장비", "지게차", "굴착기", "크레인", "건설기계"],
  HEAVY_EQUIPMENT_REAR_DETECTOR: ["중장비", "지게차", "굴착기", "트럭", "화물차", "건설기계"],
  HEAVY_EQUIPMENT_PINCH_PREVENTION: ["중장비", "지게차", "굴착기", "크레인", "센서", "경보", "시스템"],
  IOT_TEMPERATURE_HUMIDITY_METER: ["IoT", "와이파이", "WiFi", "원격", "클라우드", "스마트센서"],
};

async function readOpenApiKey() {
  if (process.env.DOMEGGOOK_OPEN_API_KEY) return process.env.DOMEGGOOK_OPEN_API_KEY;
  if (!existsSync(".env")) throw new Error("DOMEGGOOK_OPEN_API_KEY가 필요합니다.");
  const env = await readFile(".env", "utf8");
  const value = env.match(/^(?:export\s+)?DOMEGGOOK_OPEN_API_KEY\s*=\s*(.+)$/m)?.[1]
    ?.trim()
    .replace(/^(['"])(.*)\1$/, "$2");
  if (!value) throw new Error("DOMEGGOOK_OPEN_API_KEY가 필요합니다.");
  return value;
}

function koreaDate() {
  return new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Seoul" });
}

async function recordOpenApiCall(mode) {
  const date = koreaDate();
  const file = path.join("tmp", `domeggook-api-usage-${date}.json`);
  const usage = existsSync(file)
    ? JSON.parse(await readFile(file, "utf8"))
    : { date, calls: 0, byMode: {} };
  if (usage.calls >= OPEN_API_DAILY_LIMIT) {
    throw new Error(`Open API 자체 일일 한도 ${OPEN_API_DAILY_LIMIT}회를 초과했습니다.`);
  }
  usage.calls += 1;
  usage.byMode[mode] = (usage.byMode[mode] || 0) + 1;
  usage.updatedAt = new Date().toISOString();
  await mkdir("tmp", { recursive: true });
  await writeFile(file, `${JSON.stringify(usage, null, 2)}\n`);
}

async function openApiRequest(apiKey, params) {
  const url = new URL("https://domeggook.com/ssl/api/");
  const query = {
    aid: apiKey,
    market: "supply",
    om: "json",
    ...params,
  };
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== "") url.searchParams.set(key, String(value));
  }
  await recordOpenApiCall(params.mode);
  const response = await fetch(url, {
    headers: {
      "user-agent": USER_AGENT,
      accept: "application/json",
    },
  });
  if (response.status === 429) {
    throw new Error("Open API 호출 제한(429)이 발생해 수집을 중단합니다.");
  }
  if (!response.ok) throw new Error(`Open API 접근 실패: ${response.status} ${response.statusText}`);
  const result = await response.json();
  const error = result.domeggook?.error || result.error || result.errors;
  if (error) {
    throw new Error(`Open API 오류: ${error.dcode || error.code || ""} ${error.dmessage || error.message || error.msg || JSON.stringify(error)}`.trim());
  }
  if (!result.domeggook) throw new Error("Open API 오류: 응답 본문이 없습니다.");
  return result.domeggook;
}

function arrayValue(value) {
  if (!value) return [];
  return Array.isArray(value) ? value : [value];
}

function openApiListParams(keyword, size) {
  return {
    ver: "4.1",
    mode: "getItemList",
    kw: keyword,
    so: OPEN_API_RANKING_SORT,
    sz: size,
    pg: 1,
    mxq: 10,
  };
}

function openApiSourceCategoryListParams(categoryCode, size) {
  return {
    ver: "4.1",
    mode: "getItemList",
    ca: categoryCode,
    so: OPEN_API_RANKING_SORT,
    sz: size,
    pg: 1,
    mxq: 10,
  };
}

async function openApiList(apiKey, keyword, size) {
  const result = await openApiRequest(apiKey, openApiListParams(keyword, size));
  return {
    total: Number(result.header?.numberOfItems || 0),
    items: arrayValue(result.list?.item),
  };
}

async function openApiSourceCategoryList(apiKey, categoryCode, size) {
  const result = await openApiRequest(apiKey, openApiSourceCategoryListParams(categoryCode, size));
  return {
    total: Number(result.header?.numberOfItems || 0),
    items: arrayValue(result.list?.item),
  };
}

async function openApiDetail(apiKey, itemNo) {
  return openApiRequest(apiKey, {
    ver: "4.6",
    mode: "getItemView",
    no: itemNo,
  });
}

function openApiSellerScore(detail) {
  return {
    sellerRank: numberOrNull(detail.seller?.rank),
    sellerGood: booleanValue(detail.seller?.good),
    sellerReviewCount: numberOrNull(detail.seller?.score?.cnt) ?? 0,
    sellerSatisfaction: numberOrNull(String(detail.seller?.score?.avg || "").replace("%", "")),
  };
}

async function backfillSellerScore(args) {
  const apiKey = await readOpenApiKey();
  const entries = (await readdir(DEFAULT_OUT_DIR, { withFileTypes: true }))
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .filter((itemNo) => existsSync(path.join(DEFAULT_OUT_DIR, itemNo, "product.json")))
    .sort();
  const allTargets = [];
  let skipped = 0;
  for (const itemNo of entries) {
    const filePath = path.join(DEFAULT_OUT_DIR, itemNo, "product.json");
    const product = JSON.parse(await readFile(filePath, "utf8"));
    if (!args.fresh && Object.hasOwn(product, "sellerReviewCount")) {
      skipped += 1;
      continue;
    }
    allTargets.push({ itemNo, product });
  }
  const targets = args.limit > 0 ? allTargets.slice(0, args.limit) : allTargets;
  const report = { attempted: 0, succeeded: 0, skipped, unavailable: [], failed: [] };

  for (const { itemNo, product } of targets) {
    report.attempted += 1;
    try {
      const detail = await openApiDetail(apiKey, itemNo);
      Object.assign(product, openApiSellerScore(detail), {
        sellerScoreUpdatedAt: new Date().toISOString(),
      });
      await writeOutputs(product, path.join(DEFAULT_OUT_DIR, itemNo));
      report.succeeded += 1;
      console.log(
        `${itemNo} 판매자 후기 backfill 완료: ${product.sellerReviewCount}건, 만족도 ${product.sellerSatisfaction ?? "-"}%`,
      );
    } catch (error) {
      if (String(error.message).includes("ITEM_ERROR")) {
        Object.assign(product, {
          sourceStatus: "NOT_FOUND",
          sellerReviewCount: 0,
          sellerSatisfaction: null,
          sellerScoreUpdatedAt: new Date().toISOString(),
        });
        await writeOutputs(product, path.join(DEFAULT_OUT_DIR, itemNo));
        report.unavailable.push({ itemNo, reason: error.message });
        console.log(`${itemNo} 판매 종료 상품 자동 제외`);
        await writeFile(SELLER_SCORE_BACKFILL_REPORT, `${JSON.stringify(report, null, 2)}\n`);
        await sleep(args.delayMs);
        continue;
      }
      report.failed.push({ itemNo, reason: error.message });
      if (String(error.message).includes("429")) {
        await writeFile(SELLER_SCORE_BACKFILL_REPORT, `${JSON.stringify(report, null, 2)}\n`);
        throw error;
      }
      console.error(`${itemNo} 판매자 후기 backfill 실패: ${error.message}`);
    }
    await writeFile(SELLER_SCORE_BACKFILL_REPORT, `${JSON.stringify(report, null, 2)}\n`);
    await sleep(args.delayMs);
  }

  await writeFile(SELLER_SCORE_BACKFILL_REPORT, `${JSON.stringify(report, null, 2)}\n`);
  console.log(`${SELLER_SCORE_BACKFILL_REPORT} 저장 완료`);
}

function booleanValue(value) {
  return value === true || value === 1 || ["true", "1", "y"].includes(String(value).toLowerCase());
}

function normalizedText(value) {
  return cleanText(value).toLowerCase().replace(/\s+/g, "");
}

function titleHasAny(title, keywords) {
  const normalized = normalizedText(title);
  return keywords.some((keyword) => normalized.includes(normalizedText(keyword)));
}

function commonListCandidateIssue(item) {
  const title = String(item.title || "");
  if (!item.no) return "ITEM_NO_MISSING";
  if (numberOrNull(item.price) <= 0) return "PRICE_MISSING";
  const minimumOrderQuantity = numberOrNull(item.unitQty);
  if (!minimumOrderQuantity || minimumOrderQuantity < 1) return "MIN_ORDER_QUANTITY_MISSING";
  if (minimumOrderQuantity > 10) return "MIN_ORDER_QUANTITY_GT_10";
  if (booleanValue(item.adultOnly)) return "ADULT_ONLY";
  if (booleanValue(item.deli?.fromOversea)) return "OVERSEAS_DIRECT";
  if (item.market?.supply !== undefined && !booleanValue(item.market.supply)) return "SUPPLY_MARKET_INACTIVE";
  if (titleHasAny(title, CUSTOMER_EXPOSURE_KEYWORDS)) return "CUSTOMER_EXPOSURE_KEYWORD";
  if (titleHasAny(title, NON_SAFETY_KEYWORDS)) return "NON_SAFETY_KEYWORD";
  return "";
}

function listCandidateIssue(item, category) {
  const title = String(item.title || "");
  const commonIssue = commonListCandidateIssue(item);
  if (commonIssue) return commonIssue;
  const categoryKeywords = OPEN_API_REQUIRED_TITLE_KEYWORDS[category.code] || [
    cleanKeyword(category.label),
    ...(CATEGORY_KEYWORD_OVERRIDES[category.code] || []),
  ];
  if (titleHasAny(title, OPEN_API_DISALLOWED_TITLE_KEYWORDS[category.code] || [])) {
    return "CATEGORY_MISMATCH";
  }
  if (!titleHasAny(title, categoryKeywords)) return "CATEGORY_MISMATCH";
  if (
    OPEN_API_REQUIRED_CONTEXT_KEYWORDS[category.code]
    && !titleHasAny(title, OPEN_API_REQUIRED_CONTEXT_KEYWORDS[category.code])
  ) {
    return "CATEGORY_MISMATCH";
  }
  if (
    category.code !== "OPENING_COVER"
    && titleHasAny(title, [...OBVIOUS_ACCESSORY_KEYWORDS, ...NON_COMPLETE_PRODUCT_KEYWORDS])
  ) {
    return "ACCESSORY_SUSPECT";
  }
  if (titleHasAny(title, [
    ...(CATEGORY_ACCESSORY_KEYWORDS[category.code] || []),
    ...(CATEGORY_NON_COMPLETE_PRODUCT_KEYWORDS[category.code] || []),
  ])) return "ACCESSORY_SUSPECT";
  return "";
}

function parseOpenApiOptions(value) {
  if (!value) return [defaultOption()];
  const parsed = typeof value === "string" ? JSON.parse(value) : value;
  const entries = Object.entries(parsed.data || {});
  if (entries.length === 0) return [defaultOption()];
  return entries.map(([code, option], index) => {
    const stock = numberOrNull(option.qty);
    const enabled = option.sup === undefined ? String(option.dom ?? "1") !== "0" : String(option.sup) !== "0";
    const visible = String(option.hid ?? "0") === "0";
    return {
      sourceOptionCode: code,
      name: cleanText(option.name || option.title || code),
      sourceAdditionalPrice: numberOrNull(option.supPrice ?? option.domPrice) ?? 0,
      sourceStockQuantity: stock,
      status: enabled && visible && (stock === null || stock > 0) ? "ACTIVE" : "SOLD_OUT",
      sortOrder: index,
    };
  });
}

function openApiDetailImageUrls(detail) {
  const html = [
    detail.desc?.contents?.item,
    detail.desc?.contents?.deli,
  ].filter(Boolean).join("\n");
  const urls = [...html.matchAll(/<img\b[^>]*(?:src|data-src)=["']([^"']+)["']/gi)]
    .map(([, src]) => htmlDecode(src).trim())
    .filter((src) => src && !src.startsWith("data:"))
    .map((src) => {
      try {
        return new URL(src, "https://domeggook.com").href;
      } catch {
        return "";
      }
    })
    .filter(Boolean);
  return [...new Set(urls)];
}

function openApiShipping(detail) {
  const deli = detail.deli || {};
  const selected = deli.supply || deli.dome || {};
  const pay = String(selected.pay || deli.pay || "");
  const type = String(selected.type || "");
  const table = String(selected.tbl || "");
  let fee = numberOrNull(selected.fee);
  if (pay.includes("무료")) fee = 0;
  if (fee === null && table) fee = numberOrNull(table.match(/\+(\d[\d,]*)/)?.[1]);
  const conditional = /수량|차등|비례|금액비노출|착불|구매자선택/.test(`${type} ${pay}`);
  return {
    known: fee !== null,
    fee,
    conditional,
    text: [pay, type, table || (fee !== null ? `${fee}원` : "")].filter(Boolean).join(" / "),
  };
}

function openApiCategoryPath(detail) {
  const parents = arrayValue(detail.category?.parents?.elem).map((item) => item.name).filter(Boolean);
  const current = detail.category?.current?.name;
  return [...parents, current].filter(Boolean).join(" > ");
}

function openApiProduct(detail, candidate, category, keyword, sorts, scoringCategories, discovery = false) {
  const itemNo = String(detail.basis?.no || candidate.no || "");
  const title = cleanText(detail.basis?.title || candidate.title);
  const sourcePrice = numberOrNull(detail.price?.supply ?? candidate.price);
  const minimumOrderQuantity = numberOrNull(detail.qty?.supplyUnit ?? candidate.unitQty);
  const orderQuantityStep = minimumOrderQuantity;
  const sourceWholesaleMinimumOrderQuantity = numberOrNull(detail.qty?.domeMoq);
  const sourceMaximumOrderQuantity = numberOrNull(detail.qty?.supplyLoq);
  const minimumResalePrice = numberOrNull(detail.price?.resale?.minimum ?? detail.price?.resale?.minumum);
  const businessOrderAvailable = booleanValue(detail.channel?.supply);
  const detailImageUrls = openApiDetailImageUrls(detail);
  const options = parseOpenApiOptions(detail.selectOpt);
  const shipping = openApiShipping(detail);
  const sellerScore = openApiSellerScore(detail);
  const hardReasons = [];
  const reviewReasons = [];

  if (detail.basis?.status !== "판매중") hardReasons.push("NOT_ON_SALE");
  if (!sourcePrice || sourcePrice <= 0) hardReasons.push("PRICE_MISSING");
  if (!businessOrderAvailable) hardReasons.push("BUSINESS_ORDER_NOT_AVAILABLE");
  if (!minimumOrderQuantity || minimumOrderQuantity < 1) hardReasons.push("MIN_ORDER_QUANTITY_MISSING");
  if (minimumOrderQuantity > 10) hardReasons.push("MIN_ORDER_QUANTITY_GT_10");
  if (!booleanValue(detail.desc?.license?.usable)) hardReasons.push("IMAGE_USAGE_NOT_ALLOWED");
  if (detailImageUrls.length === 0) hardReasons.push("DETAIL_IMAGE_MISSING");
  if (!options.some((option) => option.status === "ACTIVE")) hardReasons.push("NO_ACTIVE_OPTIONS");
  if (booleanValue(detail.deli?.fromOversea)) hardReasons.push("OVERSEAS_DIRECT");
  if (!shipping.known) reviewReasons.push("SHIPPING_FEE_MISSING");
  if (shipping.conditional) hardReasons.push("SHIPPING_FEE_CONDITIONAL");
  if (!String(detail.detail?.country || "").trim() || !String(detail.detail?.manufacturer || "").trim()) {
    reviewReasons.push("ORIGIN_OR_MANUFACTURER_MISSING");
  }
  if (options.length > 20) reviewReasons.push("OPTION_COUNT_GT_20");
  const categoryScore = scoreCategory({ title, options }, scoringCategories);
  if (!discovery && (
    categoryScore.code !== category.code
    || categoryScore.secondScore >= categoryScore.score - 4
  )) {
    reviewReasons.push("CATEGORY_AMBIGUOUS");
  }
  hardReasons.push(...reviewReasons);
  reviewReasons.length = 0;

  return {
    product: {
      collectorVersion: OPEN_API_COLLECTOR_VERSION,
      sourceUrl: `https://mobile.domeggook.com/${itemNo}`,
      itemNo,
      title,
      priceText: String(sourcePrice || ""),
      minimumResalePrice,
      minOrderQuantityText: `${minimumOrderQuantity || 0}개`,
      minimumOrderQuantity,
      orderQuantityStep,
      sourceWholesaleMinimumOrderQuantity,
      sourceMaximumOrderQuantity,
      sellerName: cleanText(detail.seller?.nick || detail.seller?.id || detail.seller?.company?.name),
      ...sellerScore,
      sellerScoreUpdatedAt: new Date().toISOString(),
      origin: detail.detail?.country ?? "",
      model: detail.detail?.model ?? "",
      manufacturer: detail.detail?.manufacturer ?? "",
      productSize: detail.detail?.size ?? "",
      productWeight: detail.detail?.weight ?? "",
      deliveryMethod: detail.deli?.method ?? "",
      deliveryWaiting: detail.deli?.wating ?? "",
      imageUsage: "허용 (Open API)",
      thumbnailImageUrl: detail.thumb?.original || detail.thumb?.large || candidate.thumb || "",
      detailImageUrls,
      options,
      shippingFee: shipping.fee,
      shippingFeeText: shipping.text,
      shippingConditional: shipping.conditional,
      collectionCategoryCode: category.code,
      collectionCategoryLabel: category.label,
      collectionKeyword: keyword,
      collectionSorts: sorts,
      lowestPriceVerified: booleanValue(candidate.lwp),
      businessOrderAvailable,
      collectionDecision: "IMPORT_CANDIDATE",
      collectionReviewReasons: [],
      sourceStatus: detail.basis?.status || "",
      sourceCategoryCode: detail.category?.current?.code || "",
      sourceCategoryPath: openApiCategoryPath(detail),
      collectionCategoryScore: categoryScore,
      safetyCert: arrayValue(detail.detail?.safetyCert),
      productInfoDuty: detail.detail?.infoDuty || null,
      sourceDeliveryInfo: detail.deli || null,
      sourceSellerInfo: detail.seller || null,
      sourceReturnInfo: detail.return || null,
      sourceDeliveryReturnContents: detail.desc?.contents?.deli ?? null,
      sourceTermsInfo: detail.dialog?.msg ?? null,
      collectedAt: new Date().toISOString(),
    },
    hardReasons,
    reviewReasons,
  };
}

function referenceItemNo(value) {
  return String(value || "").match(/(?:domeggook\.com\/|^)(\d{5,})(?:\D|$)/)?.[1] || "";
}

function candidateFromDetail(detail) {
  return {
    no: String(detail.basis?.no || ""),
    title: detail.basis?.title || "",
    price: detail.price?.supply,
    unitQty: detail.qty?.supplyUnit,
    adultOnly: detail.basis?.adultOnly,
    deli: { fromOversea: detail.deli?.fromOversea },
    market: { supply: detail.channel?.supply },
  };
}

async function sourceCategoryDiscovery(args) {
  const apiKey = await readOpenApiKey();
  const references = (await readFile(args.file, "utf8"))
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#"))
    .map(referenceItemNo)
    .filter(Boolean);
  if (references.length === 0) throw new Error(`참조 상품이 없습니다: ${args.file}`);

  const scoringCategories = readCategoryDefinitions();
  const details = new Map();
  const sourceCategories = new Map();
  for (const itemNo of [...new Set(references)]) {
    const detail = await openApiDetail(apiKey, itemNo);
    details.set(itemNo, detail);
    const code = String(detail.category?.current?.code || "");
    if (!code) throw new Error(`참조 상품 원본 카테고리 누락: ${itemNo}`);
    const category = sourceCategories.get(code) || {
      code,
      path: openApiCategoryPath(detail),
      references: [],
    };
    category.references.push(itemNo);
    sourceCategories.set(code, category);
    await sleep(args.delayMs);
  }

  await mkdir(SOURCE_DISCOVERY_OUT_DIR, { recursive: true });
  const reports = [];
  for (const sourceCategory of sourceCategories.values()) {
    const reportPath = path.join(SOURCE_DISCOVERY_OUT_DIR, `${sourceCategory.code}.json`);
    const report = {
      collectorVersion: OPEN_API_COLLECTOR_VERSION,
      generatedAt: new Date().toISOString(),
      sourceCategoryCode: sourceCategory.code,
      sourceCategoryPath: sourceCategory.path,
      references: sourceCategory.references,
      target: args.targetPerCategory,
      discovered: [],
      excluded: [],
    };
    const result = await openApiSourceCategoryList(apiKey, sourceCategory.code, args.rankingCount);
    const candidates = new Map();
    for (const itemNo of sourceCategory.references) {
      candidates.set(itemNo, { ...candidateFromDetail(details.get(itemNo)), reference: true });
    }
    for (const item of result.items) {
      const itemNo = String(item.no || "");
      if (itemNo && !candidates.has(itemNo)) candidates.set(itemNo, item);
    }
    await sleep(args.delayMs);

    for (const candidate of candidates.values()) {
      if (report.discovered.length >= args.targetPerCategory && !candidate.reference) break;
      const itemNo = String(candidate.no || "");
      const listIssue = commonListCandidateIssue(candidate);
      if (listIssue) {
        report.excluded.push({ itemNo, title: candidate.title, reference: Boolean(candidate.reference), reasons: [listIssue], stage: "list" });
        continue;
      }
      try {
        const detail = details.get(itemNo) || await openApiDetail(apiKey, itemNo);
        const categoryScore = scoreCategory({
          title: detail.basis?.title || candidate.title,
          options: parseOpenApiOptions(detail.selectOpt),
        }, scoringCategories);
        const category = scoringCategories.find((item) => item.code === categoryScore.code) || { code: "", label: "" };
        const parsed = openApiProduct(
          detail,
          candidate,
          category,
          `source-category:${sourceCategory.code}`,
          [OPEN_API_RANKING_SORT],
          scoringCategories,
          true,
        );
        const existing = await readExistingProduct(itemNo);
        const collectionCategory = category.code ? category : {
          code: existing?.collectionCategoryCode || "",
          label: existing?.collectionCategoryLabel || "",
        };
        const reasons = [...new Set([
          ...parsed.hardReasons,
          ...(collectionCategory.code ? [] : ["CATEGORY_UNMAPPED"]),
        ])];
        if (reasons.includes("IMAGE_USAGE_NOT_ALLOWED") || reasons.includes("DETAIL_IMAGE_MISSING")) {
          report.excluded.push({ itemNo, title: parsed.product.title, reference: Boolean(candidate.reference), reasons, stage: "detail" });
        } else {
          parsed.product.collectionCategoryCode = collectionCategory.code;
          parsed.product.collectionCategoryLabel = collectionCategory.label;
          Object.assign(parsed.product, {
            collectionDecision: reasons.length ? "REVIEW_CANDIDATE" : "IMPORT_CANDIDATE",
            collectionReviewReasons: reasons,
            discoveryReference: Boolean(candidate.reference),
          });
          await saveOpenApiProduct(parsed.product);
          report.discovered.push({
            itemNo,
            title: parsed.product.title,
            reference: Boolean(candidate.reference),
            decision: parsed.product.collectionDecision,
            reasons,
            collectionCategoryCode: collectionCategory.code,
          });
          console.log(`${sourceCategory.code}: ${parsed.product.collectionDecision} ${itemNo} ${parsed.product.title}`);
        }
      } catch (error) {
        if (/일일 한도|호출 제한\(429\)/.test(String(error.message))) throw error;
        report.excluded.push({ itemNo, title: candidate.title, reference: Boolean(candidate.reference), reasons: [error.message], stage: "collect" });
      }
      await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`);
      await sleep(args.delayMs);
    }
    Object.assign(report, {
      sourceTotal: result.total,
      requested: args.rankingCount,
      actual: report.discovered.length,
      shortfall: report.discovered.length < args.targetPerCategory,
      pass: true,
      completed: true,
    });
    await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`);
    reports.push(report);
  }

  const summary = {
    collectorVersion: OPEN_API_COLLECTOR_VERSION,
    generatedAt: new Date().toISOString(),
    referenceCount: references.length,
    sourceCategoryCount: reports.length,
    discoveredProducts: reports.reduce((sum, report) => sum + report.discovered.length, 0),
    reviewCandidates: reports.reduce((sum, report) => sum + report.discovered.filter((item) => item.decision === "REVIEW_CANDIDATE").length, 0),
    excludedCandidates: reports.reduce((sum, report) => sum + report.excluded.length, 0),
    categories: reports.map((report) => ({
      sourceCategoryCode: report.sourceCategoryCode,
      sourceCategoryPath: report.sourceCategoryPath,
      references: report.references,
      actual: report.actual,
      shortfall: report.shortfall,
      pass: true,
    })),
  };
  await writeFile(path.join(SOURCE_DISCOVERY_OUT_DIR, "summary.json"), `${JSON.stringify(summary, null, 2)}\n`);
  console.log(`${SOURCE_DISCOVERY_OUT_DIR}/summary.json 저장 완료`);
}

async function saveOpenApiProduct(product) {
  const existing = await readExistingProduct(product.itemNo);
  const dir = path.join(DEFAULT_OUT_DIR, product.itemNo);
  const imageDir = path.join(dir, "images");
  await mkdir(imageDir, { recursive: true });

  product.thumbnailImagePath = existing?.thumbnailImagePath && existsSync(existing.thumbnailImagePath)
    ? existing.thumbnailImagePath
    : await downloadImage(product.thumbnailImageUrl, path.join(imageDir, "thumb"));
  product.detailImagePaths = Array.isArray(existing?.detailImagePaths)
    ? existing.detailImagePaths.filter((filePath) => existsSync(filePath))
    : [];
  for (let index = product.detailImagePaths.length; index < product.detailImageUrls.length; index += 1) {
    const number = String(index + 1).padStart(2, "0");
    product.detailImagePaths.push(
      await downloadImage(product.detailImageUrls[index], path.join(imageDir, `detail-${number}`)),
    );
  }
  await writeOutputs(product, dir);
}

async function loadOpenApiOwners() {
  const owners = new Map();
  if (!existsSync(DEFAULT_OUT_DIR)) return owners;
  const entries = await readdir(DEFAULT_OUT_DIR, { withFileTypes: true });
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    try {
      const product = JSON.parse(await readFile(path.join(DEFAULT_OUT_DIR, entry.name, "product.json"), "utf8"));
      if (
        product.collectorVersion === OPEN_API_COLLECTOR_VERSION
        && Object.hasOwn(product, "minimumOrderQuantity")
        && product.collectionCategoryCode
        && !product.collectionReviewReasons?.includes("CATEGORY_AMBIGUOUS")
      ) {
        owners.set(String(product.itemNo), product.collectionCategoryCode);
      }
    } catch {
      // 깨진 수집 파일은 이번 실행에서 다시 수집한다.
    }
  }
  return owners;
}

function mergeListCandidates(target, items, keyword, sort) {
  for (const item of items) {
    const itemNo = String(item.no || "");
    if (!itemNo) continue;
    const existing = target.get(itemNo);
    if (existing) {
      existing.matches.push({ keyword, sort });
    } else {
      target.set(itemNo, { ...item, matches: [{ keyword, sort }] });
    }
  }
}

function processedItemNos(report) {
  return new Set([
    ...report.valid.map((item) => String(item.itemNo)),
    ...report.excluded.map((item) => String(item.itemNo)),
    ...report.duplicates.map((item) => String(item.itemNo)),
  ]);
}

async function collectOpenApiCategory(apiKey, category, args, owners, scoringCategories, reportPath, previous) {
  const report = previous || {
    collectorVersion: OPEN_API_COLLECTOR_VERSION,
    generatedAt: new Date().toISOString(),
    categoryCode: category.code,
    categoryLabel: category.label,
    target: args.targetPerCategory,
    queries: [],
    valid: [],
    excluded: [],
    duplicates: [],
    shortfall: false,
    completed: false,
  };
  const seen = processedItemNos(report);
  const primaryKeyword = cleanKeyword(category.label);
  const acceptedCount = () => report.valid.length;
  const checkpoint = async () => writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`);

  async function runKeyword(keyword) {
    const candidates = new Map();
    const result = await openApiList(apiKey, keyword, args.rankingCount);
    report.queries.push({
      keyword,
      sort: OPEN_API_RANKING_SORT,
      filters: {
        market: "supply",
        maxMinimumOrderQuantity: 10,
      },
      requested: args.rankingCount,
      returned: result.items.length,
      total: result.total,
      supplemental: false,
    });
    await checkpoint();
    mergeListCandidates(candidates, result.items, keyword, OPEN_API_RANKING_SORT);
    await sleep(args.delayMs);

    const orderedCandidates = [...candidates.values()];
    for (const candidate of orderedCandidates) {
      if (acceptedCount() >= args.targetPerCategory) break;
      const itemNo = String(candidate.no);
      if (seen.has(itemNo)) continue;
      seen.add(itemNo);

      const owner = owners.get(itemNo);
      if (owner && owner !== category.code) {
        report.duplicates.push({ itemNo, owner });
        await checkpoint();
        continue;
      }
      const listIssue = listCandidateIssue(candidate, category);
      if (listIssue) {
        report.excluded.push({ itemNo, title: candidate.title, reasons: [listIssue], stage: "list" });
        await checkpoint();
        continue;
      }

      try {
        const existing = await readExistingProduct(itemNo);
        if (
          !args.fresh
          && existing?.collectorVersion === OPEN_API_COLLECTOR_VERSION
          && Object.hasOwn(existing, "minimumOrderQuantity")
          && Object.hasOwn(existing, "orderQuantityStep")
          && existing?.collectionCategoryCode === category.code
          && existsSync(existing.thumbnailImagePath || "")
          && existing.detailImagePaths?.some((filePath) => existsSync(filePath))
          && existing.options?.some((option) => option.status === "ACTIVE")
          && Object.hasOwn(existing, "sellerReviewCount")
        ) {
          if (!existing.collectionReviewReasons?.includes("CATEGORY_AMBIGUOUS")) {
            owners.set(itemNo, category.code);
          }
          const entry = { itemNo, title: existing.title, reasons: existing.collectionReviewReasons || [], reused: true };
          if (existing.collectionDecision === "IMPORT_CANDIDATE") report.valid.push(entry);
          else report.excluded.push({ ...entry, stage: "existing" });
          await checkpoint();
          continue;
        }

        const detail = await openApiDetail(apiKey, itemNo);
        const parsed = openApiProduct(
          detail,
          candidate,
          category,
          candidate.matches[0]?.keyword || keyword,
          [...new Set(candidate.matches.map((match) => match.sort))],
          scoringCategories,
        );
        if (parsed.hardReasons.length) {
          report.excluded.push({ itemNo, title: candidate.title, reasons: parsed.hardReasons, stage: "detail" });
          await checkpoint();
          continue;
        }

        await saveOpenApiProduct(parsed.product);
        owners.set(itemNo, category.code);
        const entry = {
          itemNo,
          title: parsed.product.title,
          reasons: [],
          sourceCategoryPath: parsed.product.sourceCategoryPath,
        };
        report.valid.push(entry);
        await checkpoint();
        console.log(`${category.code}: ${parsed.product.collectionDecision} ${itemNo} ${parsed.product.title}`);
      } catch (error) {
        if (/일일 한도|호출 제한\(429\)/.test(String(error.message))) throw error;
        report.excluded.push({ itemNo, title: candidate.title, reasons: [error.message], stage: "collect" });
        await checkpoint();
      }
      await sleep(args.delayMs);
    }
  }

  await runKeyword(primaryKeyword);
  report.candidateCount = acceptedCount();
  report.actual = acceptedCount();
  report.shortfall = report.candidateCount < args.targetPerCategory;
  report.shortfallCount = Math.max(0, args.targetPerCategory - report.candidateCount);
  report.pass = true;
  report.completed = true;
  await checkpoint();
  return report;
}

async function openApiCoverage(args) {
  const apiKey = await readOpenApiKey();
  let categories = await readCategories();
  if (categories.length !== 81) throw new Error(`카테고리 정의가 81개가 아닙니다: ${categories.length}`);
  if (args.category) {
    categories = categories.filter((category) => category.code === args.category);
    if (categories.length === 0) throw new Error(`알 수 없는 카테고리: ${args.category}`);
  } else if (args.maxCategories > 0) {
    categories = categories.slice(0, args.maxCategories);
  }

  await mkdir(OPEN_API_COVERAGE_OUT_DIR, { recursive: true });
  const owners = await loadOpenApiOwners();
  const scoringCategories = readCategoryDefinitions();
  const reports = [];
  for (const category of categories) {
    const reportPath = path.join(OPEN_API_COVERAGE_OUT_DIR, `${category.code}.json`);
    let report;
    if (!args.fresh && existsSync(reportPath)) {
      const previous = JSON.parse(await readFile(reportPath, "utf8"));
      const reusable =
        previous.collectorVersion === OPEN_API_COLLECTOR_VERSION
        && previous.target === args.targetPerCategory
        && previous.completed === true;
      if (reusable) {
        report = previous;
        console.log(`${category.code}: 기존 완료 보고서 재사용`);
      } else if (
        previous.collectorVersion === OPEN_API_COLLECTOR_VERSION
        && previous.target === args.targetPerCategory
      ) {
        report = await collectOpenApiCategory(
          apiKey, category, args, owners, scoringCategories, reportPath, previous,
        );
      }
    }
    if (!report) {
      report = await collectOpenApiCategory(apiKey, category, args, owners, scoringCategories, reportPath);
    }
    reports.push(report);
    await writeFile(
      reportPath,
      `${JSON.stringify(report, null, 2)}\n`,
    );
    console.log(`${category.code}: valid=${report.valid.length}, shortfall=${report.shortfall}`);
  }

  const summary = {
    collectorVersion: OPEN_API_COLLECTOR_VERSION,
    generatedAt: new Date().toISOString(),
    categoryCount: reports.length,
    targetPerCategory: args.targetPerCategory,
    rankingCount: args.rankingCount,
    coveredCategories: reports.filter((report) => !report.shortfall).length,
    shortfallCategories: reports.filter((report) => report.shortfall).map((report) => ({
      categoryCode: report.categoryCode,
      categoryLabel: report.categoryLabel,
      valid: report.valid.length,
      shortfall: report.shortfallCount,
      pass: report.pass,
    })),
    validProducts: reports.reduce((sum, report) => sum + report.valid.length, 0),
    excludedCandidates: reports.reduce((sum, report) => sum + report.excluded.length, 0),
  };
  await writeFile(
    path.join(OPEN_API_COVERAGE_OUT_DIR, "summary.json"),
    `${JSON.stringify(summary, null, 2)}\n`,
  );
  console.log(`${OPEN_API_COVERAGE_OUT_DIR}/summary.json 저장 완료`);
}

async function refreshOpenApiProducts(args) {
  const apiKey = await readOpenApiKey();
  const categories = await readCategories();
  const categoryByCode = new Map(categories.map((category) => [category.code, category]));
  const scoringCategories = readCategoryDefinitions();
  const entries = (await readdir(DEFAULT_OUT_DIR, { withFileTypes: true }))
    .filter((entry) => entry.isDirectory())
    .sort((left, right) => left.name.localeCompare(right.name));
  const stale = [];
  for (const entry of entries) {
    const product = await readExistingProduct(entry.name);
    const current = Object.hasOwn(product || {}, "businessOrderAvailable")
      && Object.hasOwn(product, "minimumOrderQuantity")
      && Object.hasOwn(product, "orderQuantityStep")
      && Object.hasOwn(product, "minimumResalePrice")
      && product.sourceDeliveryInfo;
    if (!current) stale.push(entry);
  }
  const selected = args.limit ? stale.slice(0, args.limit) : stale;
  const report = { generatedAt: new Date().toISOString(), refreshed: [], failed: [] };

  for (const entry of selected) {
    try {
      const existing = await readExistingProduct(entry.name);
      const category = categoryByCode.get(existing?.collectionCategoryCode);
      if (!existing || !category) throw new Error("COLLECTION_CATEGORY_MISSING");
      const detail = await openApiDetail(apiKey, existing.itemNo);
      const parsed = openApiProduct(
        detail,
        existing,
        category,
        existing.collectionKeyword || cleanKeyword(category.label),
        [OPEN_API_RANKING_SORT],
        scoringCategories,
      );
      await saveOpenApiProduct(parsed.product);
      if (parsed.hardReasons.length) throw new Error(parsed.hardReasons.join(","));
      report.refreshed.push({ itemNo: existing.itemNo, title: parsed.product.title });
      console.log(`refresh: ${existing.itemNo} ${parsed.product.title}`);
    } catch (error) {
      report.failed.push({ itemNo: entry.name, reason: error.message });
    }
    await writeFile(OPEN_API_REFRESH_REPORT, `${JSON.stringify(report, null, 2)}\n`);
    await sleep(args.delayMs);
  }

  console.log(`refresh 완료: 성공 ${report.refreshed.length}개, 실패 ${report.failed.length}개`);
}

function selfCheck() {
  const listParams = openApiListParams("안전모", 60);
  if (
    listParams.so !== "rd"
    || listParams.mxq !== 10
    || Object.hasOwn(listParams, "sgd")
    || Object.hasOwn(listParams, "fdl")
  ) {
    throw new Error("Open API 후보 기준은 도매꾹랭킹순과 MOQ 10 이하여야 합니다");
  }
  if (listCandidateIssue({
    no: "1",
    title: "안전모 헬멧 통기 패드",
    price: "1000",
    unitQty: "1",
  }, {
    code: "PPE_SAFETY_HELMET",
    label: "안전모",
  }) !== "ACCESSORY_SUSPECT") {
    throw new Error("Open API 완제품 필터 self-check 실패");
  }
  const options = parseOpenApiOptions(JSON.stringify({
    data: {
      "00": { name: "백색", sup: "1", supPrice: "500", qty: "2", hid: "0" },
      "01": { name: "주황색", sup: "1", supPrice: "0", qty: "0", hid: "1" },
    },
  }));
  if (options.length !== 2 || options[0].status !== "ACTIVE" || options[1].status !== "SOLD_OUT") {
    throw new Error("Open API 옵션 파서 self-check 실패");
  }
  const shipping = openApiShipping({ deli: { supply: { pay: "선결제", type: "고정배송비", fee: "3000" } } });
  if (!shipping.known || shipping.fee !== 3000 || shipping.conditional) {
    throw new Error("Open API 배송비 파서 self-check 실패");
  }
  const sellerScore = openApiSellerScore({
    seller: { rank: "2", good: "true", score: { cnt: "10", avg: "97%" } },
  });
  if (
    sellerScore.sellerRank !== 2
    || !sellerScore.sellerGood
    || sellerScore.sellerReviewCount !== 10
    || sellerScore.sellerSatisfaction !== 97
  ) {
    throw new Error("Open API 판매자 후기 파서 self-check 실패");
  }
  const images = openApiDetailImageUrls({ desc: { contents: { item: '<img src="//example.com/a.jpg"><img data-src="/b.png">' } } });
  if (images.length !== 2 || !images[0].startsWith("https://")) {
    throw new Error("Open API 상세 이미지 파서 self-check 실패");
  }
  const rawInfo = openApiProduct({
    basis: { no: "1", title: "안전모", status: "판매중" },
    price: { supply: "1000", resale: { minimum: "1300" } },
    channel: { supply: "true" },
    qty: { supplyUnit: "1" },
    detail: {
      country: "상세정보별도표기",
      model: "해당없음",
      manufacturer: "상세정보 별도표기",
      size: "0x0x0",
      weight: "0g",
      infoDuty: { item: [{ type: "item", name: "품명 및 모델명", desc: "상세정보 별도표기" }] },
    },
    deli: { method: "택배", wating: "2일 후 발송", supply: { pay: "무료배송", type: "고정배송비" } },
    desc: { license: { usable: "true" }, contents: { item: '<img src="https://example.com/a.jpg">' } },
    selectOpt: null,
  }, {}, { code: "PPE_SAFETY_HELMET", label: "안전모" }, "안전모", ["rd"], [{
    code: "PPE_SAFETY_HELMET",
    label: "안전모",
    keywords: ["안전모"],
  }]).product;
  if (
    rawInfo.collectorVersion !== OPEN_API_COLLECTOR_VERSION
    || rawInfo.model !== "해당없음"
    || rawInfo.minimumResalePrice !== 1300
    || !rawInfo.businessOrderAvailable
    || rawInfo.minimumOrderQuantity !== 1
    || rawInfo.orderQuantityStep !== 1
    || rawInfo.productSize !== "0x0x0"
    || rawInfo.productWeight !== "0g"
    || rawInfo.productInfoDuty.item[0].desc !== "상세정보 별도표기"
  ) {
    throw new Error("Open API 상품정보 원문 보존 self-check 실패");
  }
  const selfCheckCategories = readCategoryDefinitions();
  const helmetCategory = selfCheckCategories.find((category) => category.code === "PPE_SAFETY_HELMET");
  const moqFixture = {
    basis: { no: "6", title: "안전모", status: "판매중" },
    price: { supply: "1000" },
    channel: { supply: "true" },
    qty: { supplyUnit: "6", domeMoq: "12", supplyLoq: "60" },
    detail: { country: "대한민국", manufacturer: "제조사" },
    deli: { supply: { pay: "무료배송", type: "고정배송비" } },
    desc: { license: { usable: "true" }, contents: { item: '<img src="https://example.com/a.jpg">' } },
  };
  const moqSix = openApiProduct(moqFixture, {}, helmetCategory, "안전모", ["rd"], selfCheckCategories);
  if (
    moqSix.hardReasons.length
    || moqSix.product.minimumOrderQuantity !== 6
    || moqSix.product.orderQuantityStep !== 6
    || moqSix.product.sourceWholesaleMinimumOrderQuantity !== 12
    || moqSix.product.sourceMaximumOrderQuantity !== 60
  ) throw new Error("Open API MOQ 6 파서 self-check 실패");
  const moqTen = openApiProduct({
    ...moqFixture,
    basis: { ...moqFixture.basis, no: "10" },
    qty: { supplyUnit: "10" },
  }, {}, helmetCategory, "안전모", ["rd"], selfCheckCategories);
  if (moqTen.hardReasons.length || moqTen.product.minimumOrderQuantity !== 10) {
    throw new Error("Open API MOQ 10 파서 self-check 실패");
  }
  const conditionalShipping = openApiProduct({
    ...moqFixture,
    basis: { ...moqFixture.basis, no: "conditional" },
    deli: { supply: { pay: "착불", type: "수량별비례", fee: "3000" } },
  }, {}, helmetCategory, "안전모", ["rd"], selfCheckCategories);
  if (!conditionalShipping.hardReasons.includes("SHIPPING_FEE_CONDITIONAL")) {
    throw new Error("조건부 배송비 제외 self-check 실패");
  }
  const moqEleven = listCandidateIssue({ no: "11", title: "안전모", price: "1000", unitQty: "11" }, {
    code: "PPE_SAFETY_HELMET", label: "안전모",
  });
  if (moqEleven !== "MIN_ORDER_QUANTITY_GT_10") throw new Error("MOQ 10 초과 제외 self-check 실패");
  const resumed = processedItemNos({
    valid: [{ itemNo: "1" }], excluded: [{ itemNo: "2" }], duplicates: [{ itemNo: "3" }],
  });
  if (!["1", "2", "3"].every((itemNo) => resumed.has(itemNo))) throw new Error("수집 재개 self-check 실패");
  const helmetIssue = listCandidateIssue({
    no: "1",
    title: "안전모 이름표 식별표 24개입",
    price: "1000",
    unitQty: "1",
    adultOnly: "false",
    deli: { fromOversea: "false" },
    market: { supply: "true" },
  }, { code: "PPE_SAFETY_HELMET" });
  if (helmetIssue !== "ACCESSORY_SUSPECT") throw new Error("부속품 필터 self-check 실패");
  const mismatch = listCandidateIssue({
    no: "2",
    title: "회전형 전기 모기채 2중안전버튼",
    price: "1000",
    unitQty: "1",
    adultOnly: "false",
    deli: { fromOversea: "false" },
    market: { supply: "true" },
  }, { code: "PPE_SAFETY_HELMET", label: "안전모" });
  if (mismatch !== "CATEGORY_MISMATCH") throw new Error("카테고리 연관성 self-check 실패");
  if (referenceItemNo("https://www.domeggook.com/44092831?from=lstGen") !== "44092831") {
    throw new Error("참조 상품번호 파서 self-check 실패");
  }
  if (commonListCandidateIssue({
    no: "44092831",
    title: "폴리에스터 NBR 그립왕 안전 장갑",
    price: "1040",
    unitQty: "1",
    market: { supply: "true" },
  })) throw new Error("원본 카테고리 후보 공통 필터 self-check 실패");
  if (openApiSourceCategoryListParams("12_16_05_07_00", 60).ca !== "12_16_05_07_00") {
    throw new Error("원본 카테고리 조회 파라미터 self-check 실패");
  }
  const vehicleBelt = listCandidateIssue({
    no: "3",
    title: "자동차 안전벨트 고정클립",
    price: "1000",
    unitQty: "1",
    adultOnly: "false",
    deli: { fromOversea: "false" },
    market: { supply: "true" },
  }, { code: "PPE_SAFETY_BELT", label: "안전벨트" });
  if (vehicleBelt !== "CATEGORY_MISMATCH") throw new Error("산업 안전 맥락 self-check 실패");
  const replacementLens = listCandidateIssue({
    no: "4",
    title: "보안경렌즈 교체형 투명커버 10개입",
    price: "1000",
    unitQty: "1",
    adultOnly: "false",
    deli: { fromOversea: "false" },
    market: { supply: "true" },
  }, { code: "PPE_SAFETY_GLASSES", label: "보안경" });
  if (replacementLens !== "ACCESSORY_SUSPECT") throw new Error("보안경 부속품 self-check 실패");
  const householdRail = listCandidateIssue({
    no: "5",
    title: "나무 핸드레일 계단 안전난간",
    price: "1000",
    unitQty: "1",
    adultOnly: "false",
    deli: { fromOversea: "false" },
    market: { supply: "true" },
  }, { code: "FALL_PREVENTION_GUARDRAIL", label: "안전난간" });
  if (householdRail !== "CATEGORY_MISMATCH") throw new Error("주택용 난간 self-check 실패");
  const truckNet = listCandidateIssue({
    no: "6",
    title: "화물차 추락방지망 안전망",
    price: "1000",
    unitQty: "1",
    adultOnly: "false",
    deli: { fromOversea: "false" },
    market: { supply: "true" },
  }, { code: "FALL_PREVENTION_NET", label: "추락방지망" });
  if (truckNet !== "CATEGORY_MISMATCH") throw new Error("차량용 안전망 self-check 실패");
  const householdLadder = listCandidateIssue({
    no: "7",
    title: "가정용 계단사다리 알루미늄 작업발판",
    price: "1000",
    unitQty: "1",
    adultOnly: "false",
    deli: { fromOversea: "false" },
    market: { supply: "true" },
  }, { code: "WORK_PLATFORM", label: "작업발판" });
  if (householdLadder !== "CATEGORY_MISMATCH") throw new Error("일반 사다리 self-check 실패");
  const genericWarningSign = listCandidateIssue({
    no: "8",
    title: "화장실 안내 경고표지판 깨끗이 사용합시다",
    price: "1000",
    unitQty: "1",
    adultOnly: "false",
    deli: { fromOversea: "false" },
    market: { supply: "true" },
  }, { code: "WARNING_SIGN", label: "경고표지" });
  if (genericWarningSign !== "NON_SAFETY_KEYWORD") throw new Error("일반 안내판 self-check 실패");
  const surfaceRoughness = listCandidateIssue({
    no: "9",
    title: "윤곽 게이지 표면조도측정기 내경측정기",
    price: "1000",
    unitQty: "1",
    adultOnly: "false",
    deli: { fromOversea: "false" },
    market: { supply: "true" },
  }, { code: "LIGHT_METER", label: "조도측정기" });
  if (surfaceRoughness !== "CATEGORY_MISMATCH") throw new Error("표면조도 측정기 self-check 실패");
  const categories = readCategoryDefinitions();
  const resolvedGasDetector = resolveReviewCategory({
    title: "휴대용 가스누설검지기",
    collectionCategoryCode: "GAS_DETECTOR",
    options: [],
  }, categories);
  if (resolvedGasDetector.code !== "GAS_DETECTOR" || resolvedGasDetector.resolution !== "COLLECTION_TARGET") {
    throw new Error("수집 목표 카테고리 재분류 self-check 실패");
  }
  const categoryConflict = resolveReviewCategory({
    title: "이동식 바리케이드 안전휀스",
    collectionCategoryCode: "BARRICADE",
    options: [],
  }, categories);
  if (categoryConflict.code !== "BARRICADE" || categoryConflict.resolution !== "BUSINESS_RULE") {
    throw new Error("바리케이드 우선순위 self-check 실패");
  }
  const ruledHarness = resolveReviewCategory({
    title: "전체식 안전벨트 추락방지 안전대",
    collectionCategoryCode: "PPE_FALL_ARREST_HARNESS",
    options: [],
  }, categories);
  if (ruledHarness.code !== "PPE_FALL_ARREST_HARNESS" || ruledHarness.resolution !== "BUSINESS_RULE") {
    throw new Error("안전대 형태 우선순위 self-check 실패");
  }
  const filteredOptions = stopCustomOptions([
    { name: "기성 피스식", status: "ACTIVE" },
    { name: "주문인쇄 피스식", status: "ACTIVE" },
  ]);
  if (filteredOptions[0].status !== "ACTIVE" || filteredOptions[1].status !== "STOPPED") {
    throw new Error("주문제작 옵션 차단 self-check 실패");
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(koreaDate())) throw new Error("호출 장부 날짜 self-check 실패");
  console.log("Open API collector self-check 통과");
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    usage();
    return;
  }
  if (args.selfCheck) {
    selfCheck();
    return;
  }
  if (args.openApiCoverage) {
    await openApiCoverage(args);
    return;
  }
  if (args.sourceCategoryDiscovery) {
    await sourceCategoryDiscovery(args);
    return;
  }
  if (args.openApiRefresh) {
    await refreshOpenApiProducts(args);
    return;
  }

  if (args.backfillOptions) {
    await backfillOptions(args);
    return;
  }
  if (args.backfillSellerScore) {
    await backfillSellerScore(args);
    return;
  }

  if (args.coverageScan) {
    await coverageScan(args);
    return;
  }

  const urls = await readUrls(args);
  for (const url of urls) {
    const product = await collect(url);
    console.log(`${product.itemNo} 수집 완료: ${product.title} / 옵션 ${product.options.length}개`);
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
