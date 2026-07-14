#!/usr/bin/env node
import { readdir, readFile, stat, writeFile } from "node:fs/promises";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

const USER_AGENT =
  "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
const DEFAULT_API = "http://localhost:8080";
const DEFAULT_PRODUCTS_DIR = "tmp/domeggook-products";
const DEFAULT_REVIEW_JSON = "tmp/domeggook-product-review.json";
const DEFAULT_REVIEW_CSV = "tmp/domeggook-product-review.csv";
const DEFAULT_FILTERED_MANIFEST = "tmp/domeggook-import-manifest.filtered.json";
const CATEGORY_FILE = "apps/web/src/lib/categories.ts";
const DEFAULT_PRICING_POLICY = {
  commissionRate: 5,
  taxBufferRate: 10,
  overheadRate: 5,
  safetyMarginRate: 5,
  roundingUnit: 100,
  totalMarkupRate: 25,
};

const CATEGORY_KEYWORD_OVERRIDES = {
  PPE_SAFETY_HELMET: ["안전모", "헬멧", "산업용 헬멧", "현장 헬멧"],
  PPE_SAFETY_SHOES: ["안전화", "작업화", "건설화", "안전 신발", "작업 신발"],
  PPE_FALL_ARREST_HARNESS: ["안전대", "추락방지용 안전대", "전체식 안전대"],
  PPE_SAFETY_BELT: ["안전벨트", "보조벨트"],
  PPE_SAFETY_GLASSES: ["보안경", "고글", "보호안경", "보호경"],
  PPE_RESPIRATOR: ["방진마스크", "방독마스크", "분진마스크", "호흡보호구"],
  PPE_EAR_PROTECTION: ["귀마개", "귀덮개", "이어플러그"],
  PPE_INSULATED_GLOVES: ["절연장갑", "전기장갑"],
  PPE_WELDING_GLOVES: ["용접장갑", "알곤장갑"],
  PPE_HIGH_VISIBILITY_VEST: ["안전조끼", "형광조끼", "반사조끼", "고가시성 조끼"],
  PPE_PROTECTIVE_CLOTHING: ["보호복", "방호복", "화학복", "유해물질 보호복"],
  FALL_PREVENTION_GUARDRAIL: ["안전난간", "가드레일"],
  FALL_PREVENTION_NET: ["추락방지망", "추락 방지망"],
  FALLING_OBJECT_NET: ["낙하물방지망", "낙하방지망", "낙하물망"],
  OPENING_COVER: ["개구부덮개", "개구부 덮개"],
  WORK_PLATFORM: ["작업발판", "작업대", "우마", "사다리"],
  SAFE_PASSAGE: ["안전통로", "통로발판"],
  LIFELINE: ["생명줄", "수직 생명줄", "수평 생명줄"],
  SAFETY_BLOCK: ["안전블록", "안전블럭", "추락 방지 블록"],
  DANGER_AREA_BARRIER: ["위험구역 차단시설", "차단시설"],
  SAFETY_SIGN: ["안전표지판", "안전 표지"],
  WARNING_SIGN: ["경고표지", "주의표지", "위험표지"],
  TRAFFIC_CONE: ["라바콘", "칼라콘", "안전콘", "꼬깔콘"],
  SAFETY_FENCE: ["안전휀스", "안전펜스", "휀스", "펜스"],
  BARRICADE: ["바리케이드", "바리케이트"],
  ACCESS_CONTROL_FACILITY: ["출입통제시설", "출입통제"],
  WARNING_LIGHT: ["경광등", "경고등", "점멸등"],
  SIGNAL_BATON: ["신호봉", "유도봉"],
  BARRIER_TAPE: ["안전띠", "차단테이프", "위험테이프"],
  GAS_DETECTOR: ["가스측정기", "가스감지기"],
  OXYGEN_METER: ["산소농도측정기", "산소측정기"],
  NOISE_METER: ["소음측정기", "소음계", "데시벨측정기"],
  LIGHT_METER: ["조도측정기", "조도계"],
  ANEMOMETER: ["풍속계", "풍속측정기"],
  DUST_METER: ["분진측정기", "분진계"],
  VIBRATION_METER: ["진동측정기", "진동계"],
  THERMAL_CAMERA_INSPECTION: ["열화상카메라", "열감지 카메라"],
  FIRST_AID_KIT: ["구급함", "구급상자"],
  FIRST_AID_SUPPLIES: ["응급처치용품", "응급처치 키트"],
  AED: ["AED", "자동심장충격기"],
  EYEWASH_STATION: ["세안기", "눈세척기"],
  HEAT_COLD_PREVENTION_SUPPLIES: ["냉감", "온열질환", "쿨링", "아이스팩", "혹한기"],
  VENTILATION_EQUIPMENT: ["환기설비", "환풍기", "송풍기"],
  SMART_SAFETY_HELMET: ["스마트 안전모"],
  SMART_SAFETY_VEST: ["스마트 안전조끼"],
  SMART_SAFETY_HARNESS: ["스마트 안전대"],
  SMART_WATCH: ["스마트워치", "낙상감지", "SOS 워치"],
  SMART_GAS_DETECTOR: ["스마트 가스감지기"],
  SMART_CCTV_AI_VIDEO_ANALYTICS: ["AI 영상분석", "AI CCTV"],
  SMART_CCTV_AI_SAFETY_MANAGEMENT: ["AI 안전관리 CCTV", "안전관리 CCTV"],
  SMART_CCTV_AI_HELMET_DETECTION: ["안전모 착용 인식", "안전모 인식 CCTV"],
  SMART_CCTV_AI_VEST_DETECTION: ["안전조끼 착용 인식", "안전조끼 인식 CCTV"],
  SMART_CCTV_AI_DANGER_ZONE_INTRUSION: ["위험구역 침입감지", "침입감지 CCTV"],
  SMART_CCTV_AI_FALL_RISK_DETECTION: ["추락위험 감지", "추락 감지 CCTV"],
  SMART_CCTV_AI_FALLEN_WORKER_DETECTION: ["쓰러짐 감지", "낙상 감지 CCTV"],
  SMART_CCTV_AI_FIRE_SMOKE_DETECTION: ["화재 감지 CCTV", "연기 감지 CCTV"],
  SMART_CCTV_GENERAL_SPECIAL: ["CCTV", "특수 CCTV"],
  SMART_CCTV_MOBILE: ["이동식 CCTV"],
  SMART_CCTV_SOLAR_MOBILE: ["태양광 CCTV", "태양광 이동형 CCTV"],
  SMART_CCTV_PTZ: ["PTZ CCTV", "회전형 CCTV"],
  SMART_CCTV_THERMAL: ["열화상 CCTV"],
  SMART_CCTV_DUAL_SPECTRUM: ["듀얼스펙트럼 CCTV"],
  WORKER_LOCATION_ACCESS_MANAGEMENT: ["위치 출입 관리", "작업자 출입관리"],
  WORKER_LOCATION_TRACKING: ["작업자 위치추적", "UWB", "BLE", "RFID"],
  WORKER_ACCESS_CONTROL: ["출입관리 시스템"],
  WORKER_ELECTRONIC_ACCESS_CONTROL: ["전자출입관리 시스템"],
  WORKER_SOS_EMERGENCY_CALL: ["SOS 비상호출", "비상호출 시스템"],
  HEAVY_EQUIPMENT_PROXIMITY_ALARM: ["중장비 접근경보", "접근경보장치"],
  HEAVY_EQUIPMENT_COLLISION_PREVENTION: ["충돌방지장치", "중장비 충돌방지"],
  HEAVY_EQUIPMENT_REAR_DETECTOR: ["후방감지장치", "후방감지"],
  HEAVY_EQUIPMENT_PINCH_PREVENTION: ["협착방지장치", "협착방지"],
  CRANE_PROXIMITY_ALARM: ["크레인 접근경보", "크레인 경보"],
  OPENING_PROXIMITY_ALARM: ["개구부 접근경보", "개구부 경보"],
  FALL_DETECTION_SYSTEM: ["추락감지 시스템", "추락 감지"],
  SCAFFOLD_DISPLACEMENT_MONITORING: ["비계 변위", "비계 모니터링"],
  RETAINING_WALL_MEASUREMENT_SYSTEM: ["흙막이 계측", "흙막이 모니터링"],
  IOT_GAS_DETECTOR: ["IoT 가스감지기"],
  IOT_DUST_METER: ["IoT 분진측정기"],
  IOT_NOISE_METER: ["IoT 소음측정기"],
  IOT_TEMPERATURE_HUMIDITY_METER: ["IoT 온습도측정기", "온습도측정기"],
  IOT_ANEMOMETER: ["IoT 풍속계"],
};

const NON_SAFETY_KEYWORDS = [
  "완구",
  "장난감",
  "키링",
  "스마트폰",
  "핸드폰",
  "휴대폰",
  "화장품",
  "식품",
  "과자",
  "빵",
  "커피",
  "낚시",
  "캠핑",
  "반려동물",
  "애견",
  "주방",
  "욕실",
];

const CUSTOMER_EXPOSURE_KEYWORDS = ["도매꾹", "도매매"];
const REVIEW_KEYWORDS = [
  "3M",
  "DUPONT",
  "OTOS",
  "판촉",
  "사은품",
  "기념품",
  "인쇄",
  "로고",
  "정품",
  "브랜드",
  "벌초",
  "예초기",
  "산림",
  "농업",
  "양봉",
  "휠체어",
  "억제대",
  "층간소음",
  "생활소음",
  "발렛파킹",
  "주차금지",
  "호환",
  "리필",
  "교체용",
  "부속",
  "부품",
  "패드",
  "내피",
  "턱끈",
  "햇빛가리개",
  "그늘막",
  "차광막",
  "차양",
  "땀받이",
  "쿨링",
  "아이스젤",
  "헤드랜턴",
  "랜턴",
  "커버",
  "케이스",
  "거치대",
  "스티커",
  "필름",
  "끈",
];

function usage() {
  console.log(`Usage:
  node scripts/review-domeggook-products.mjs
  node scripts/review-domeggook-products.mjs --api http://localhost:8080 --cookie-file tmp/admin-cookie.txt

Options:
  --products-dir tmp/domeggook-products
  --api http://localhost:8080
  --cookie "ACCESS_TOKEN=..."
  --cookie-file tmp/admin-cookie.txt
  --limit 10
  --shipping-concurrency 4
  --no-fetch-shipping`);
}

function argValue(argv, name, fallback = "") {
  const index = argv.indexOf(name);
  return index === -1 ? fallback : argv[index + 1] || "";
}

function numberArg(argv, name, fallback) {
  const value = Number(argValue(argv, name, ""));
  return Number.isFinite(value) && value > 0 ? value : fallback;
}

function parseArgs(argv) {
  if (argv.includes("--help") || argv.includes("-h")) return { help: true };
  return {
    productsDir: argValue(argv, "--products-dir", DEFAULT_PRODUCTS_DIR),
    api: argValue(argv, "--api", DEFAULT_API),
    cookie: argValue(argv, "--cookie"),
    cookieFile: argValue(argv, "--cookie-file"),
    limit: numberArg(argv, "--limit", 0),
    shippingConcurrency: numberArg(argv, "--shipping-concurrency", 4),
    fetchShipping: !argv.includes("--no-fetch-shipping"),
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

function normalize(value) {
  return String(value || "")
    .toLowerCase()
    .replace(/[(){}\[\]·,./+_~:;|"'`!?<>-]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function parsePrice(priceText) {
  return Number(String(priceText || "").match(/[\d,]+/)?.[0].replace(/[^\d]/g, "")) || 0;
}

function parseQuantity(value) {
  return Number(String(value || "").match(/\d+/)?.[0]) || 0;
}

function totalMarkupRate(policy) {
  return Number(policy.totalMarkupRate ?? (
    Number(policy.commissionRate || 0) +
    Number(policy.taxBufferRate || 0) +
    Number(policy.overheadRate || 0) +
    Number(policy.safetyMarginRate || 0)
  ));
}

function calculateBasePrice(sourcePrice, policy = DEFAULT_PRICING_POLICY) {
  const roundingUnit = Number(policy.roundingUnit || 100);
  const rawPrice = Number(sourcePrice || 0) * (1 + totalMarkupRate(policy) / 100);
  return Math.round(rawPrice / roundingUnit) * roundingUnit;
}

function hasAny(text, keywords) {
  const normalized = normalize(text);
  return keywords.filter((keyword) => normalized.includes(normalize(keyword)));
}

function csvCell(value) {
  const text = Array.isArray(value) ? value.join("|") : String(value ?? "");
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

function sanitizePublicSummary(summary) {
  return String(summary || "")
    .split(/\s+\/\s+/)
    .map((part) => String(part || "").trim())
    .filter((part) => part && !part.includes("도매꾹") && !part.includes("상품번호") && !part.startsWith("최대구매수량"))
    .join(" / ")
    .slice(0, 500);
}

function summaryFor(product) {
  return sanitizePublicSummary([
    product.minOrderQuantityText,
    product.origin ? `원산지 ${product.origin}` : "",
    product.manufacturer ? `제조사 ${product.manufacturer}` : "",
    "인증/KC 및 상품고시 검수 필요",
  ].filter(Boolean).join(" / "));
}

function readCategoryDefinitions() {
  const text = existsSync(CATEGORY_FILE) ? readFileSync(CATEGORY_FILE, "utf8") : "";
  return [...text.matchAll(/\["([^"]*)",\s*"([^"]*)",\s*"([^"]*)",\s*"([^"]*)"\]/g)]
    .map(([, group, subgroup, code, label]) => ({
      group,
      subgroup,
      code,
      label,
      keywords: [
        label,
        label.replace(/\([^)]*\)/g, "").trim(),
        ...(CATEGORY_KEYWORD_OVERRIDES[code] || []),
      ].filter(Boolean),
    }));
}

function scoreCategory(product, categories) {
  const optionNames = (product.options || []).map((option) => option.name).join(" ");
  const source = normalize(`${product.title || ""} ${optionNames}`);
  const scored = categories.map((category) => {
    let score = 0;
    for (const keyword of category.keywords) {
      const normalizedKeyword = normalize(keyword);
      if (!normalizedKeyword) continue;
      if (source.includes(normalizedKeyword)) score += normalizedKeyword.length >= 4 ? 4 : 3;
      for (const token of normalizedKeyword.split(" ").filter((token) => token.length >= 2)) {
        if (source.includes(token)) score += 1;
      }
    }
    return { ...category, score };
  }).sort((a, b) => b.score - a.score || a.code.localeCompare(b.code));

  const best = scored[0] || { score: 0, code: "", label: "" };
  const second = scored[1] || { score: 0, code: "", label: "" };
  const highConfidence = best.score >= 4 && best.score >= second.score + 2;
  return {
    code: highConfidence ? best.code : "",
    label: highConfidence ? best.label : "",
    score: best.score,
    secondScore: second.score,
    confidence: highConfidence ? "HIGH" : best.score >= 3 ? "LOW" : "NONE",
    candidateCode: best.code,
    candidateLabel: best.label,
  };
}

async function readCollectedProducts(productsDir) {
  const entries = await readdir(productsDir, { withFileTypes: true });
  const products = [];
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    const productFile = path.join(productsDir, entry.name, "product.json");
    try {
      const product = JSON.parse(await readFile(productFile, "utf8"));
      products.push({ productFile, product });
    } catch {
      // 깨진 수집 파일은 리뷰 대상에서 제외한다. 원본 수집을 다시 돌리면 된다.
    }
  }
  return products.sort((a, b) => String(a.product.itemNo).localeCompare(String(b.product.itemNo)));
}

async function fetchText(url) {
  const response = await fetch(url, {
    headers: {
      "user-agent": USER_AGENT,
      accept: "text/html,application/xhtml+xml",
      "accept-language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
    },
  });
  if (!response.ok) throw new Error(`배송비 파싱용 원본 접근 실패: ${response.status}`);
  return response.text();
}

function parseDeliveryTableText(html) {
  const table = html.match(/<table[^>]*class="[^"]*lDeliDetail[^"]*"[^>]*>([\s\S]*?)<\/table>/i)?.[1] || "";
  if (!table) return "";
  const rows = [...table.matchAll(/<tr>\s*<th>([\s\S]*?)<\/th>\s*<td>([\s\S]*?)<\/td>\s*<\/tr>/gi)];
  const row = rows.find(([, th]) => cleanText(th).includes("배송금액"));
  return row ? cleanText(row[2]) : cleanText(table);
}

function parseShippingFeeFromText(text) {
  const normalized = cleanText(text);
  if (!normalized) return { known: false, fee: null, text: "", conditional: false };
  const conditional = /조건|수량|비례|착불|협의|무료배송 조건|이상 구매/i.test(normalized);
  if (/무료배송|무료\s*$/i.test(normalized)) {
    return { known: true, fee: 0, text: normalized, conditional };
  }
  const fees = [...normalized.matchAll(/(\d[\d,]*)\s*원/g)]
    .map(([, value]) => parsePrice(value))
    .filter((value) => value > 0);
  const fee = fees.length ? Math.min(...fees) : parsePrice(normalized);
  return fee > 0
    ? { known: true, fee, text: normalized, conditional }
    : { known: false, fee: null, text: normalized, conditional };
}

function parseShippingFeeFromHtml(html) {
  const tableText = parseDeliveryTableText(html);
  const tableParsed = parseShippingFeeFromText(tableText);
  if (tableParsed.known || tableParsed.conditional) return tableParsed;

  const jsFee = html.match(/\bvar\s+fee\s*=\s*"([\d,]+)"/)?.[1];
  if (jsFee) {
    const type = html.match(/\bvar\s+type\s*=\s*"([^"]+)"/)?.[1] || "";
    const who = html.match(/\bvar\s+who\s*=\s*"([^"]+)"/)?.[1] || "";
    const text = [who, type, `${jsFee}원`].filter(Boolean).join(" / ");
    return { known: true, fee: parsePrice(jsFee), text, conditional: !["fix", ""].includes(type) };
  }
  return { known: false, fee: null, text: "", conditional: false };
}

function localShippingInfo(product) {
  const candidates = [
    product.shippingFee,
    product.deliveryFee,
    product.shippingFeeText,
    product.deliveryFeeText,
    product.delivery?.fee,
    product.deli?.fee,
  ].filter((value) => value !== undefined && value !== null && value !== "");
  if (!candidates.length) return null;
  return parseShippingFeeFromText(candidates.join(" "));
}

async function shippingInfoFor(product, fetchShipping) {
  const local = localShippingInfo(product);
  if (local?.known || local?.conditional) return { ...local, source: "product-json" };
  if (!fetchShipping) return { known: false, fee: null, text: "", conditional: false, source: "skipped" };
  const sourceUrl = product.sourceUrl || `https://mobile.domeggook.com/${product.itemNo}`;
  try {
    const html = await fetchText(sourceUrl);
    return { ...parseShippingFeeFromHtml(html), source: "source-html" };
  } catch (error) {
    return { known: false, fee: null, text: error.message, conditional: false, source: "source-html-error" };
  }
}

async function fileSize(filePath) {
  if (!filePath) return 0;
  try {
    return (await stat(filePath)).size;
  } catch {
    return 0;
  }
}

function normalizeOptions(options) {
  return (Array.isArray(options) ? options : []).map((option, index) => ({
    sourceOptionCode: String(option.sourceOptionCode || option.code || index).slice(0, 100),
    name: String(option.name || `옵션 ${index + 1}`).trim().slice(0, 200),
    sourceAdditionalPrice: Number(option.sourceAdditionalPrice ?? option.additionalPrice ?? 0) || 0,
    sourceStockQuantity: option.sourceStockQuantity === null || option.sourceStockQuantity === undefined
      ? null
      : Number(option.sourceStockQuantity),
    status: ["ACTIVE", "SOLD_OUT", "STOPPED"].includes(option.status) ? option.status : "ACTIVE",
    sortOrder: Number(option.sortOrder ?? index) || index,
  }));
}

function manifestOptionsFor(sourcePrice, basePrice, options, policy) {
  return options.map((option) => {
    const sourceOptionPrice = sourcePrice + Number(option.sourceAdditionalPrice || 0);
    const calculatedSalePrice = calculateBasePrice(sourceOptionPrice, policy);
    return {
      sourceOptionCode: option.sourceOptionCode,
      name: option.name,
      sourceAdditionalPrice: option.sourceAdditionalPrice,
      sourceStockQuantity: Number.isFinite(option.sourceStockQuantity) ? option.sourceStockQuantity : null,
      status: option.status,
      sortOrder: option.sortOrder,
      calculatedSalePrice,
      additionalPrice: Math.max(0, calculatedSalePrice - basePrice),
    };
  });
}

async function reviewProduct(entry, context) {
  const { product, productFile } = entry;
  const title = String(product.title || "");
  const options = normalizeOptions(product.options);
  const activeOptions = options.filter((option) => option.status === "ACTIVE");
  const sourcePrice = parsePrice(product.priceText);
  const minOrderQuantity = parseQuantity(product.minOrderQuantityText);
  const detailImagePaths = Array.isArray(product.detailImagePaths) ? product.detailImagePaths : [];
  const category = scoreCategory(product, context.categories);
  const shipping = await shippingInfoFor(product, context.fetchShipping);
  const thumbnailSize = await fileSize(product.thumbnailImagePath);
  const detailSizes = await Promise.all(detailImagePaths.map(fileSize));
  const hardReasons = [];
  const reviewReasons = [];
  const textForKeyword = `${title} ${(options || []).map((option) => option.name).join(" ")}`;

  if (!sourcePrice) hardReasons.push("PRICE_MISSING");
  if (!String(product.imageUsage || "").includes("허용")) hardReasons.push("IMAGE_USAGE_NOT_ALLOWED");
  if (activeOptions.length === 0) hardReasons.push("NO_ACTIVE_OPTIONS");
  if (detailImagePaths.length === 0) hardReasons.push("DETAIL_IMAGE_MISSING");
  if (!product.thumbnailImagePath || thumbnailSize === 0) hardReasons.push("THUMBNAIL_MISSING");
  if (hasAny(textForKeyword, CUSTOMER_EXPOSURE_KEYWORDS).length) hardReasons.push("CUSTOMER_EXPOSURE_KEYWORD");
  if (hasAny(textForKeyword, NON_SAFETY_KEYWORDS).length) hardReasons.push("NON_SAFETY_KEYWORD");
  if (context.existingProductNames.has(title)) hardReasons.push("DUPLICATE_NAME");

  if (category.confidence !== "HIGH") reviewReasons.push("CATEGORY_LOW_CONFIDENCE");
  if (minOrderQuantity > 1) reviewReasons.push("MIN_ORDER_QUANTITY_GT_1");
  if (!shipping.known) reviewReasons.push("SHIPPING_FEE_MISSING");
  if (shipping.conditional) reviewReasons.push("SHIPPING_FEE_CONDITIONAL");
  if (options.length > 20) reviewReasons.push("OPTION_COUNT_GT_20");
  if (!String(product.origin || "").trim() || !String(product.manufacturer || "").trim()) reviewReasons.push("ORIGIN_OR_MANUFACTURER_MISSING");
  if (thumbnailSize > 0 && thumbnailSize < 15_000) reviewReasons.push("THUMBNAIL_QUALITY_SUSPECT");
  if (detailSizes.some((size) => size > 0 && size < 30_000)) reviewReasons.push("DETAIL_IMAGE_QUALITY_SUSPECT");
  if (hasAny(textForKeyword, REVIEW_KEYWORDS).length) reviewReasons.push("BRAND_PROMO_ACCESSORY_SUSPECT");

  const effectiveSourcePrice = shipping.known ? sourcePrice + Number(shipping.fee || 0) : null;
  const calculatedBasePrice = effectiveSourcePrice ? calculateBasePrice(effectiveSourcePrice, context.policy) : null;
  const decision = hardReasons.length ? "EXCLUDE" : reviewReasons.length ? "REVIEW" : "IMPORT";
  const reasonCodes = [...hardReasons, ...reviewReasons];
  const manifestOptions = effectiveSourcePrice && calculatedBasePrice
    ? manifestOptionsFor(effectiveSourcePrice, calculatedBasePrice, options, context.policy)
    : manifestOptionsFor(sourcePrice, calculateBasePrice(sourcePrice, context.policy), options, context.policy);

  return {
    itemNo: product.itemNo,
    productFile,
    name: title,
    decision,
    reasonCodes,
    categoryCode: category.code,
    categoryConfidence: category,
    sourcePrice,
    shippingFee: shipping.fee,
    shippingFeeKnown: shipping.known,
    shippingFeeConditional: shipping.conditional,
    shippingText: shipping.text,
    shippingSource: shipping.source,
    effectiveSourcePrice,
    calculatedBasePrice,
    minOrderQuantity,
    optionCount: options.length,
    activeOptionCount: activeOptions.length,
    detailImageCount: detailImagePaths.length,
    thumbnailSize,
    minDetailImageSize: detailSizes.length ? Math.min(...detailSizes) : 0,
    supplierName: product.sellerName || "외부 공급처",
    manifestItem: {
      itemNo: product.itemNo,
      import: decision === "IMPORT",
      decision,
      reasonCodes,
      productFile,
      categoryCode: decision === "IMPORT" ? category.code : "",
      status: "HIDDEN",
      name: title,
      summary: summaryFor(product),
      sourceUrl: product.sourceUrl || `https://mobile.domeggook.com/${product.itemNo}`,
      sourcePrice: effectiveSourcePrice || sourcePrice,
      basePrice: calculatedBasePrice || calculateBasePrice(sourcePrice, context.policy),
      options: manifestOptions,
      supplierName: product.sellerName || "외부 공급처",
      productInfoNotice: "",
      shippingInfo: shipping.known
        ? `배송비 포함 가격입니다. 공급처 기본 배송비 ${Number(shipping.fee || 0).toLocaleString("ko-KR")}원을 원가에 반영했습니다.`
        : "",
      asInfo: "",
      returnExchangeInfo: "",
      memo: `B-054 review=${decision}; ${reasonCodes.join(",") || "AUTO_IMPORT_CANDIDATE"}; ACTIVE 전환 전 인증/KC, 상품고시, 이미지 품질 확인`,
    },
  };
}

async function mapLimit(items, limit, fn) {
  const results = new Array(items.length);
  let nextIndex = 0;
  async function worker() {
    while (nextIndex < items.length) {
      const index = nextIndex;
      nextIndex += 1;
      results[index] = await fn(items[index], index);
    }
  }
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker));
  return results;
}

async function cookieHeader(args) {
  if (args.cookie) return args.cookie;
  if (args.cookieFile) return (await readFile(args.cookieFile, "utf8")).trim();
  return "";
}

async function apiFetch(args, pathName) {
  const cookie = await cookieHeader(args);
  const response = await fetch(`${args.api}${pathName}`, {
    headers: {
      ...(cookie ? { cookie } : {}),
    },
  });
  if (!response.ok) throw new Error(`GET ${pathName} failed: ${response.status}`);
  return response.json();
}

async function loadPricingPolicy(args) {
  try {
    if (!(await cookieHeader(args))) return DEFAULT_PRICING_POLICY;
    return await apiFetch(args, "/api/admin/pricing-policy");
  } catch {
    return DEFAULT_PRICING_POLICY;
  }
}

async function loadExistingProductNames(args) {
  try {
    if (!(await cookieHeader(args))) return new Set();
    const products = await apiFetch(args, "/api/admin/products");
    return new Set((Array.isArray(products) ? products : []).map((product) => product.name).filter(Boolean));
  } catch {
    return new Set();
  }
}

function reviewCsv(items) {
  const headers = [
    "itemNo",
    "decision",
    "reasonCodes",
    "categoryCode",
    "categoryConfidence",
    "sourcePrice",
    "shippingFee",
    "effectiveSourcePrice",
    "calculatedBasePrice",
    "minOrderQuantity",
    "optionCount",
    "activeOptionCount",
    "detailImageCount",
    "name",
  ];
  const rows = items.map((item) => headers.map((header) => {
    if (header === "categoryConfidence") return item.categoryConfidence.confidence;
    return item[header];
  }).map(csvCell).join(","));
  return `${headers.join(",")}\n${rows.join("\n")}\n`;
}

function summarize(items) {
  const summary = {
    total: items.length,
    import: items.filter((item) => item.decision === "IMPORT").length,
    review: items.filter((item) => item.decision === "REVIEW").length,
    exclude: items.filter((item) => item.decision === "EXCLUDE").length,
    reasonCounts: {},
  };
  for (const item of items) {
    for (const reason of item.reasonCodes) {
      summary.reasonCounts[reason] = (summary.reasonCounts[reason] || 0) + 1;
    }
  }
  return summary;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) return usage();

  const categories = readCategoryDefinitions();
  if (categories.length !== 81) {
    throw new Error(`카테고리 정의가 81개가 아닙니다: ${categories.length}`);
  }

  let entries = await readCollectedProducts(args.productsDir);
  if (args.limit) entries = entries.slice(0, args.limit);

  const [policy, existingProductNames] = await Promise.all([
    loadPricingPolicy(args),
    loadExistingProductNames(args),
  ]);
  const context = {
    categories,
    fetchShipping: args.fetchShipping,
    policy,
    existingProductNames,
  };

  const reviewed = await mapLimit(entries, args.shippingConcurrency, (entry) => reviewProduct(entry, context));
  const summary = summarize(reviewed);
  const manifest = {
    generatedAt: new Date().toISOString(),
    source: "B-054 domeggook product review",
    summary,
    items: reviewed.map((item) => item.manifestItem),
  };
  const review = {
    generatedAt: manifest.generatedAt,
    summary,
    items: reviewed.map(({ manifestItem, ...item }) => item),
  };

  await writeFile(DEFAULT_REVIEW_JSON, `${JSON.stringify(review, null, 2)}\n`);
  await writeFile(DEFAULT_REVIEW_CSV, reviewCsv(reviewed));
  await writeFile(DEFAULT_FILTERED_MANIFEST, `${JSON.stringify(manifest, null, 2)}\n`);

  console.log(`review 완료: total=${summary.total}, IMPORT=${summary.import}, REVIEW=${summary.review}, EXCLUDE=${summary.exclude}`);
  console.log(`- ${DEFAULT_REVIEW_JSON}`);
  console.log(`- ${DEFAULT_REVIEW_CSV}`);
  console.log(`- ${DEFAULT_FILTERED_MANIFEST}`);
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
