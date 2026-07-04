#!/usr/bin/env node
import { mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import path from "node:path";

const USER_AGENT =
  "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
const DEFAULT_OUT_DIR = "tmp/domeggook-products";
const CATEGORY_FILE = "apps/web/src/lib/categories.ts";
const COVERAGE_OUT_DIR = "tmp/domeggook-category-coverage";
const OPTION_BACKFILL_REPORT = "tmp/domeggook-option-backfill-report.json";

const CATEGORY_KEYWORD_OVERRIDES = {
  PPE_RESPIRATOR: ["방진마스크", "방독마스크", "호흡보호구"],
  PPE_EAR_PROTECTION: ["귀마개", "귀덮개", "이어플러그"],
  PPE_PROTECTIVE_CLOTHING: ["보호복", "방호복", "유해물질 보호복"],
  FALLING_OBJECT_NET: ["낙하물방지망", "낙하방지망"],
  OPENING_COVER: ["개구부덮개", "개구부 덮개"],
  LIFELINE: ["생명줄", "수직 생명줄", "수평 생명줄"],
  DANGER_AREA_BARRIER: ["위험구역 차단시설", "안전 바리케이드"],
  ACCESS_CONTROL_FACILITY: ["출입통제시설", "출입통제"],
  BARRIER_TAPE: ["안전띠", "차단테이프"],
  THERMAL_CAMERA_INSPECTION: ["열화상카메라", "열감지 카메라"],
  FIRST_AID_SUPPLIES: ["응급처치용품", "응급처치 키트"],
  AED: ["AED", "자동심장충격기"],
  EYEWASH_STATION: ["세안기", "눈세척기"],
  HEAT_COLD_PREVENTION_SUPPLIES: ["온열질환 예방", "냉감용품", "혹한기 용품"],
  VENTILATION_EQUIPMENT: ["환기설비", "유해작업 환기"],
  SMART_WATCH: ["스마트워치 낙상감지", "SOS 스마트워치"],
  SMART_CCTV_AI_VIDEO_ANALYTICS: ["AI 영상분석", "AI CCTV"],
  SMART_CCTV_AI_SAFETY_MANAGEMENT: ["AI 안전관리 CCTV", "안전관리 CCTV"],
  SMART_CCTV_AI_HELMET_DETECTION: ["AI 안전모 착용 인식 CCTV", "안전모 인식 CCTV"],
  SMART_CCTV_AI_VEST_DETECTION: ["AI 안전조끼 착용 인식 CCTV", "안전조끼 인식 CCTV"],
  SMART_CCTV_AI_DANGER_ZONE_INTRUSION: ["AI 위험구역 침입감지 CCTV", "침입감지 CCTV"],
  SMART_CCTV_AI_FALL_RISK_DETECTION: ["AI 추락위험 감지 CCTV", "추락 감지 CCTV"],
  SMART_CCTV_AI_FALLEN_WORKER_DETECTION: ["AI 쓰러짐 감지 CCTV", "낙상 감지 CCTV"],
  SMART_CCTV_AI_FIRE_SMOKE_DETECTION: ["AI 화재 연기 감지 CCTV", "화재 연기 감지 CCTV"],
  SMART_CCTV_GENERAL_SPECIAL: ["CCTV", "특수 CCTV"],
  SMART_CCTV_SOLAR_MOBILE: ["태양광 이동형 CCTV", "태양광 CCTV"],
  SMART_CCTV_PTZ: ["PTZ CCTV", "회전형 CCTV"],
  SMART_CCTV_DUAL_SPECTRUM: ["듀얼스펙트럼 CCTV", "열화상 광학 CCTV"],
  WORKER_LOCATION_ACCESS_MANAGEMENT: ["위치 출입 관리", "작업자 출입관리"],
  WORKER_LOCATION_TRACKING: ["작업자 위치추적", "UWB 위치추적", "BLE 위치추적", "RFID 위치추적"],
  WORKER_ACCESS_CONTROL: ["출입관리 시스템", "작업자 출입관리"],
  WORKER_ELECTRONIC_ACCESS_CONTROL: ["전자출입관리 시스템", "전자 출입관리"],
  WORKER_SOS_EMERGENCY_CALL: ["SOS 비상호출 시스템", "비상호출 시스템"],
  HEAVY_EQUIPMENT_PROXIMITY_ALARM: ["중장비 접근경보장치", "접근경보장치"],
  HEAVY_EQUIPMENT_COLLISION_PREVENTION: ["충돌방지장치", "중장비 충돌방지"],
  HEAVY_EQUIPMENT_REAR_DETECTOR: ["후방감지장치", "중장비 후방감지"],
  HEAVY_EQUIPMENT_PINCH_PREVENTION: ["협착방지장치", "중장비 협착방지"],
  CRANE_PROXIMITY_ALARM: ["크레인 접근경보장치", "크레인 경보장치"],
  OPENING_PROXIMITY_ALARM: ["개구부 접근경보장치", "개구부 경보장치"],
  FALL_DETECTION_SYSTEM: ["추락감지 시스템", "추락 감지"],
  SCAFFOLD_DISPLACEMENT_MONITORING: ["비계 변위 모니터링", "비계 계측"],
  RETAINING_WALL_MEASUREMENT_SYSTEM: ["흙막이 계측 시스템", "흙막이 계측"],
  IOT_TEMPERATURE_HUMIDITY_METER: ["IoT 온습도측정기", "IoT 온도 습도 측정기"],
};

function usage() {
  console.log(`Usage:
  node scripts/collect-domeggook-product.mjs https://mobile.domeggook.com/8667274
  node scripts/collect-domeggook-product.mjs --file tmp/domeggook-urls.txt
  node scripts/collect-domeggook-product.mjs --backfill-options --limit 5
  node scripts/collect-domeggook-product.mjs --coverage-scan --target-per-category 5
  node scripts/collect-domeggook-product.mjs --coverage-scan --target-per-category 1 --max-categories 3

Output:
  tmp/domeggook-products/{itemNo}/product.json
  tmp/domeggook-products/{itemNo}/product.csv
  tmp/domeggook-products/{itemNo}/images/*
  tmp/domeggook-option-backfill-report.json
  tmp/domeggook-category-coverage/*`);
}

function parseArgs(argv) {
  if (argv.includes("--help") || argv.includes("-h")) return { help: true };

  if (argv.includes("--backfill-options")) {
    return {
      backfillOptions: true,
      limit: numberArg(argv, "--limit", 0),
      delayMs: numberArg(argv, "--delay-ms", 250),
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
    "minOrderQuantityText",
    "sellerName",
    "origin",
    "manufacturer",
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

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    usage();
    return;
  }

  if (args.backfillOptions) {
    await backfillOptions(args);
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
