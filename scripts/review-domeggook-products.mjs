#!/usr/bin/env node
import assert from "node:assert/strict";
import { readdir, readFile, stat, writeFile } from "node:fs/promises";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";

const USER_AGENT =
  "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
const DEFAULT_API = "http://localhost:8080";
const DEFAULT_PRODUCTS_DIR = "tmp/domeggook-products";
const DEFAULT_REVIEW_JSON = "tmp/domeggook-product-review.json";
const DEFAULT_REVIEW_CSV = "tmp/domeggook-product-review.csv";
const DEFAULT_FILTERED_MANIFEST = "tmp/domeggook-import-manifest.filtered.json";
const DEFAULT_KOSHA_AUDIT = "tmp/domeggook-kosha-cert-audit.json";
const DEFAULT_TARGET_PER_CATEGORY = 30;
const CATEGORY_FILE = "apps/web/src/lib/categories.ts";
const KOSHA_CATEGORY_CODES = new Set([
  "PPE_SAFETY_HELMET",
  "PPE_SAFETY_SHOES",
  "PPE_FALL_ARREST_HARNESS",
  "PPE_SAFETY_BELT",
  "PPE_SAFETY_GLASSES",
  "PPE_RESPIRATOR",
  "PPE_EAR_PROTECTION",
  "PPE_INSULATED_GLOVES",
  "PPE_PROTECTIVE_CLOTHING",
  "SMART_SAFETY_HELMET",
  "SMART_SAFETY_HARNESS",
  "FALL_PREVENTION_GUARDRAIL",
  "WORK_PLATFORM",
]);
const DEFAULT_PRICING_POLICY = {
  commissionRate: 5,
  taxBufferRate: 10,
  overheadRate: 5,
  safetyMarginRate: 5,
  roundingUnit: 100,
  totalMarkupRate: 25,
};
const VERIFIED_KOSHA_STATUSES = new Set([
  "KOSHA_REGISTRY_MODEL_VERIFIED",
]);
const AUTO_NOT_REQUIRED_CATEGORY_CODES = new Set(["BARRIER_TAPE"]);
const COREABLE_AS_INFO =
  "A/S 및 상품 문의: 코어블SAF 고객센터 010-8277-7369 / contact@coreable-saf.com / 평일 10:00-18:00";
const COREABLE_RETURN_EXCHANGE_INFO =
  "단순 변심 반품·교환은 배송 완료일부터 7일 이내 접수하며 반환 비용은 고객 부담입니다. 하자·오배송은 운영자 부담을 기본으로 하며 자세한 기준은 /policies/cancellation-refund에서 확인할 수 있습니다.";

export const CATEGORY_KEYWORD_OVERRIDES = {
  PPE_SAFETY_HELMET: ["안전모", "헬멧", "산업용 헬멧", "현장 헬멧"],
  PPE_SAFETY_SHOES: ["안전화", "작업화", "건설화", "안전 신발", "작업 신발"],
  PPE_FALL_ARREST_HARNESS: ["안전대", "추락방지용 안전대", "전체식 안전대"],
  PPE_SAFETY_BELT: [
    "안전벨트",
    "보조벨트",
    "산업 안전벨트",
    "작업용 안전벨트",
    "추락방지 안전벨트",
    "주상 안전벨트",
    "안전그네",
  ],
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
  GAS_DETECTOR: ["가스측정기", "가스감지기", "가스검지기", "가스누설검지기"],
  OXYGEN_METER: ["산소농도측정기", "산소측정기", "산소 측정기"],
  NOISE_METER: ["소음측정기", "소음계", "데시벨측정기"],
  LIGHT_METER: ["조도측정기", "조도계"],
  ANEMOMETER: ["풍속계", "풍속측정기"],
  DUST_METER: ["분진측정기", "분진계"],
  VIBRATION_METER: ["진동측정기", "진동계"],
  THERMAL_CAMERA_INSPECTION: ["열화상카메라", "열화상 카메라", "열감지 카메라"],
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
  HEAVY_EQUIPMENT_REAR_DETECTOR: ["후방감지장치", "후방감지", "후방 감지기", "후방센서", "백부저"],
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

export const NON_SAFETY_KEYWORDS = [
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
  "강아지",
  "고양이",
  "어린이",
  "육아",
  "교구",
  "만들기 키트",
  "화장실",
  "골무",
  "원예",
  "알약통",
  "도어벨",
  "홈캠",
  "주방",
  "욕실",
];

export const CUSTOMER_EXPOSURE_KEYWORDS = ["도매꾹", "도매매"];
export const NON_COMPLETE_PRODUCT_KEYWORDS = [
  "악세사리",
  "액세서리",
  "교체용",
  "리필",
  "호환",
  "부속품",
  "부품",
  "거치대",
  "스티커",
  "보호필름",
];
export const CATEGORY_NON_COMPLETE_PRODUCT_KEYWORDS = {
  PPE_SAFETY_HELMET: ["내피", "턱끈", "햇빛가리개", "땀받이", "패드", "헤드랜턴", "헬멧랜턴"],
};
export const REVIEW_KEYWORDS = [
  "판촉",
  "사은품",
  "기념품",
  "로고",
  "벌초",
  "예초기",
  "산림",
  "농업",
  "양봉",
  "휠체어",
  "억제대",
  "발렛파킹",
];
export const CUSTOM_OPTION_KEYWORDS = ["주문인쇄", "맞춤", "로고", "문구선택", "시안"];

function usage() {
  console.log(`Usage:
  node scripts/review-domeggook-products.mjs
  node scripts/review-domeggook-products.mjs --api http://localhost:8080 --cookie-file tmp/admin-cookie.txt
  node scripts/review-domeggook-products.mjs --self-check

Options:
  --products-dir tmp/domeggook-products
  --kosha-audit tmp/domeggook-kosha-cert-audit.json
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
  if (argv.includes("--self-check")) return { selfCheck: true };
  return {
    productsDir: argValue(argv, "--products-dir", DEFAULT_PRODUCTS_DIR),
    koshaAudit: argValue(argv, "--kosha-audit", DEFAULT_KOSHA_AUDIT),
    api: argValue(argv, "--api", DEFAULT_API),
    cookie: argValue(argv, "--cookie"),
    cookieFile: argValue(argv, "--cookie-file"),
    limit: numberArg(argv, "--limit", 0),
    shippingConcurrency: numberArg(argv, "--shipping-concurrency", 4),
    fetchShipping: !argv.includes("--no-fetch-shipping"),
  };
}

export function koshaCollectionGate(categoryCode, audit) {
  if (!KOSHA_CATEGORY_CODES.has(categoryCode)) {
    return { hardReason: "", status: "NOT_APPLICABLE" };
  }
  if (!audit || audit.categoryCode !== categoryCode) {
    return { hardReason: "", status: "MISSING" };
  }
  if (audit.status === "NOT_APPLICABLE") {
    return { hardReason: "", status: "MISSING" };
  }
  if (audit.collectionDecision === "EXCLUDE") {
    return { hardReason: "KOSHA_COLLECTION_EXCLUDED", status: audit.status };
  }
  return { hardReason: "", status: audit.status };
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

function moqDecision(quantity) {
  if (!Number.isInteger(quantity)) return "MOQ_INVALID";
  if (quantity === 1) return "MOQ_1";
  if (quantity >= 2 && quantity <= 10) return "MOQ_2_TO_10";
  if (quantity > 10) return "MOQ_GT_10";
  return "MOQ_INVALID";
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

function calculateSalePrice(sourcePrice, minimumResalePrice, policy = DEFAULT_PRICING_POLICY) {
  const roundingUnit = Number(policy.roundingUnit || 100);
  const minimum = Math.ceil(Number(minimumResalePrice || 0) / roundingUnit) * roundingUnit;
  return Math.max(calculateBasePrice(sourcePrice, policy), minimum);
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
  ].filter(Boolean).join(" / "));
}

export function complianceStatusFor(product, categoryCode, koshaStatus) {
  if (VERIFIED_KOSHA_STATUSES.has(koshaStatus)) return "VERIFIED";
  if (
    koshaStatus === "NOT_APPLICABLE"
    && (
      AUTO_NOT_REQUIRED_CATEGORY_CODES.has(categoryCode)
      || /(?:인증|허가|KC).{0,12}(?:미대상|비대상|해당\s*없음)|해당\s*사항?\s*없음/i.test(sourceCertificationText(product))
    )
  ) return "NOT_REQUIRED";
  return "PENDING";
}

function sourceCertificationText(product) {
  const item = (product.productInfoDuty?.item || []).find((entry) => /인증|허가/.test(entry.name || ""));
  return cleanText(item?.desc || "");
}

function productNoticeRowsFor(product) {
  const rows = Array.isArray(product.productInfoDuty?.item) ? product.productInfoDuty.item : [];
  return rows
    .filter((entry) => entry.type !== "transaction")
    .map((entry) => ({
      label: String(entry.name ?? ""),
      value: String(entry.desc ?? ""),
    }))
    .filter((entry) => entry.label && entry.value);
}

function productInfoNoticeFor(product) {
  const noticeRows = productNoticeRowsFor(product);
  return noticeRows.map((row) => `${row.label}: ${row.value}`).join("\n");
}

export function readCategoryDefinitions() {
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

export function scoreCategory(product, categories) {
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

function firstKeywordIndex(source, keywords) {
  return keywords.reduce((first, keyword) => {
    const index = source.indexOf(normalize(keyword));
    return index === -1 ? first : Math.min(first, index);
  }, Number.POSITIVE_INFINITY);
}

function businessCategoryCode(product, scored) {
  const source = normalize(`${product.title || ""} ${(product.options || []).map((option) => option.name).join(" ")}`);
  const pair = new Set([product.collectionCategoryCode, scored.code].filter(Boolean));

  if (pair.has("OXYGEN_METER") && pair.has("GAS_DETECTOR") && /산소|o2/.test(source)) {
    return "OXYGEN_METER";
  }
  if (pair.has("OPENING_COVER") && pair.has("FALL_PREVENTION_NET") && /개구부.{0,6}덮개|철제덮개/.test(source)) {
    return "OPENING_COVER";
  }
  if (pair.has("FIRST_AID_SUPPLIES") && pair.has("FIRST_AID_KIT") && /구급함|구급상자|응급처치키트|구급약.{0,4}파우치|약보관함/.test(source)) {
    return "FIRST_AID_KIT";
  }
  if (pair.has("BARRICADE") && pair.has("SAFETY_FENCE")) {
    return /바리케이드|바리케이트/.test(source) ? "BARRICADE" : "SAFETY_FENCE";
  }
  if (pair.has("PPE_FALL_ARREST_HARNESS") && pair.has("PPE_SAFETY_BELT")) {
    const harnessIndex = firstKeywordIndex(source, ["전신하네스", "전체식", "상체식", "그네식", "하네스"]);
    const beltIndex = firstKeywordIndex(source, ["주상용", "주상안전", "허리벨트", "둔부", "벨트형"]);
    if (Number.isFinite(harnessIndex) || Number.isFinite(beltIndex)) {
      return harnessIndex <= beltIndex ? "PPE_FALL_ARREST_HARNESS" : "PPE_SAFETY_BELT";
    }
  }
  return "";
}

export function resolveReviewCategory(product, categories) {
  const scored = scoreCategory(product, categories);
  const collected = categories.find((category) => category.code === product.collectionCategoryCode);
  if (!collected) return { ...scored, resolution: "SCORED" };

  const ruledCode = businessCategoryCode(product, scored);
  if (ruledCode) {
    const ruled = categories.find((category) => category.code === ruledCode);
    return {
      ...scored,
      code: ruled.code,
      label: ruled.label,
      confidence: "HIGH",
      resolution: "BUSINESS_RULE",
      collectedCode: collected.code,
    };
  }

  const source = normalize(`${product.title || ""} ${(product.options || []).map((option) => option.name).join(" ")}`);
  const matchedKeywords = collected.keywords.filter((keyword) => {
    const normalizedKeyword = normalize(keyword);
    return normalizedKeyword && source.includes(normalizedKeyword);
  });
  if (!matchedKeywords.length) return { ...scored, resolution: "SCORED" };
  if (scored.code && scored.code !== collected.code) {
    return { ...scored, resolution: "CATEGORY_CONFLICT", collectedCode: collected.code };
  }

  return {
    ...scored,
    code: collected.code,
    label: collected.label,
    confidence: "HIGH",
    resolution: "COLLECTION_TARGET",
    matchedKeywords,
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

export function stopCustomOptions(options) {
  return options.map((option) => (
    hasAny(option.name, CUSTOM_OPTION_KEYWORDS).length
      ? { ...option, status: "STOPPED" }
      : option
  ));
}

function manifestOptionsFor(sourcePrice, minimumResalePrice, basePrice, options, policy) {
  return options.map((option) => {
    const sourceOptionPrice = sourcePrice + Number(option.sourceAdditionalPrice || 0);
    const calculatedSalePrice = calculateSalePrice(sourceOptionPrice, minimumResalePrice, policy);
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
  const saleOptions = stopCustomOptions(options);
  const customOptionCount = saleOptions.filter((option, index) => option.status !== options[index].status).length;
  const activeOptions = saleOptions.filter((option) => option.status === "ACTIVE");
  const sourcePrice = parsePrice(product.priceText);
  const minimumResalePrice = Number(product.minimumResalePrice || 0) || null;
  const minimumOrderQuantity = Number(product.minimumOrderQuantity || parseQuantity(product.minOrderQuantityText));
  const orderQuantityStep = Number(product.orderQuantityStep || minimumOrderQuantity);
  const minimumOrderQuantityDecision = moqDecision(minimumOrderQuantity);
  const detailImagePaths = Array.isArray(product.detailImagePaths) ? product.detailImagePaths : [];
  const category = resolveReviewCategory(product, context.categories);
  const shipping = await shippingInfoFor(product, context.fetchShipping);
  const thumbnailSize = await fileSize(product.thumbnailImagePath);
  const detailSizes = await Promise.all(detailImagePaths.map(fileSize));
  const hardReasons = [];
  const categoryResolved = ["COLLECTION_TARGET", "BUSINESS_RULE"].includes(category.resolution);
  const reviewReasons = (Array.isArray(product.collectionReviewReasons)
    ? [...product.collectionReviewReasons]
    : [])
    .filter((reason) => (
      reason !== "DOMAIN_OR_CUSTOM_PRODUCT_SUSPECT"
      && reason !== "OPTION_COUNT_GT_20"
      && (!categoryResolved || !["CATEGORY_AMBIGUOUS", "CATEGORY_LOW_CONFIDENCE"].includes(reason))
    ));
  const textForKeyword = `${title} ${saleOptions.filter((option) => option.status !== "STOPPED").map((option) => option.name).join(" ")}`;
  const koshaAudit = context.koshaAudit.get(String(product.itemNo));
  const koshaGate = koshaCollectionGate(category.code, koshaAudit);

  if (!sourcePrice) hardReasons.push("PRICE_MISSING");
  if (product.sourceStatus === "NOT_FOUND") hardReasons.push("SOURCE_ITEM_UNAVAILABLE");
  if (product.businessOrderAvailable !== true) hardReasons.push("BUSINESS_ORDER_NOT_AVAILABLE");
  if (!String(product.imageUsage || "").includes("허용")) hardReasons.push("IMAGE_USAGE_NOT_ALLOWED");
  if (activeOptions.length === 0) {
    hardReasons.push(customOptionCount === options.length ? "CUSTOM_OPTIONS_ONLY" : "NO_ACTIVE_OPTIONS");
  }
  if (detailImagePaths.length === 0) hardReasons.push("DETAIL_IMAGE_MISSING");
  if (!product.thumbnailImagePath || thumbnailSize === 0) hardReasons.push("THUMBNAIL_MISSING");
  if (productNoticeRowsFor(product).length === 0) hardReasons.push("PRODUCT_NOTICE_MISSING");
  if (hasAny(textForKeyword, CUSTOMER_EXPOSURE_KEYWORDS).length) hardReasons.push("CUSTOMER_EXPOSURE_KEYWORD");
  if (hasAny(textForKeyword, NON_SAFETY_KEYWORDS).length) hardReasons.push("NON_SAFETY_KEYWORD");
  if (
    hasAny(textForKeyword, NON_COMPLETE_PRODUCT_KEYWORDS).length
    || hasAny(textForKeyword, CATEGORY_NON_COMPLETE_PRODUCT_KEYWORDS[category.code] || []).length
  ) {
    hardReasons.push("NON_COMPLETE_PRODUCT");
  }
  if (koshaGate.hardReason) hardReasons.push(koshaGate.hardReason);

  if (category.confidence !== "HIGH") reviewReasons.push("CATEGORY_LOW_CONFIDENCE");
  if (
    minimumOrderQuantityDecision === "MOQ_INVALID"
    || !Number.isInteger(orderQuantityStep)
    || orderQuantityStep < 1
    || orderQuantityStep > 99
  ) {
    hardReasons.push("MIN_ORDER_QUANTITY_MISSING");
  }
  if (minimumOrderQuantityDecision === "MOQ_GT_10") hardReasons.push("MIN_ORDER_QUANTITY_GT_10");
  if (!shipping.known) reviewReasons.push("SHIPPING_FEE_MISSING");
  if (shipping.conditional) hardReasons.push("SHIPPING_FEE_CONDITIONAL");
  if (!String(product.origin || "").trim() || !String(product.manufacturer || "").trim()) reviewReasons.push("ORIGIN_OR_MANUFACTURER_MISSING");
  if (hasAny(textForKeyword, REVIEW_KEYWORDS).length) reviewReasons.push("DOMAIN_OR_CUSTOM_PRODUCT_SUSPECT");

  const calculatedBasePrice = sourcePrice
    ? calculateSalePrice(sourcePrice, minimumResalePrice, context.policy)
    : null;
  const reasonCodes = [...new Set([...hardReasons, ...reviewReasons])];
  const decision = reasonCodes.length ? "EXCLUDE" : "IMPORT";
  const complianceStatus = complianceStatusFor(product, category.code, koshaGate.status);
  const status = decision === "IMPORT" && complianceStatus !== "REJECTED" ? "ACTIVE" : "HIDDEN";
  const manifestOptions = manifestOptionsFor(
    sourcePrice,
    minimumResalePrice,
    calculatedBasePrice || calculateSalePrice(sourcePrice, minimumResalePrice, context.policy),
    saleOptions,
    context.policy,
  );

  return {
    itemNo: product.itemNo,
    productFile,
    name: title,
    decision,
    reasonCodes,
    categoryCode: category.code,
    categoryConfidence: category,
    categoryResolution: category.resolution,
    collectionCategoryCode: product.collectionCategoryCode || "",
    collectionCategoryLabel: product.collectionCategoryLabel || "",
    sourceCategoryCode: product.sourceCategoryCode || "",
    sourceCategoryPath: product.sourceCategoryPath || "",
    sourcePrice,
    minimumResalePrice,
    shippingFee: shipping.fee,
    shippingFeeKnown: shipping.known,
    shippingFeeConditional: shipping.conditional,
    shippingText: shipping.text,
    shippingSource: shipping.source,
    calculatedBasePrice,
    minimumOrderQuantity,
    orderQuantityStep,
    minimumOrderQuantityDecision,
    optionCount: options.length,
    activeOptionCount: activeOptions.length,
    customOptionCount,
    sellerReviewCount: Number(product.sellerReviewCount),
    sellerSatisfaction: product.sellerSatisfaction !== null
      && product.sellerSatisfaction !== undefined
      && product.sellerSatisfaction !== ""
      && Number.isFinite(Number(product.sellerSatisfaction))
      ? Number(product.sellerSatisfaction)
      : null,
    koshaAuditStatus: koshaGate.status,
    koshaCollectionDecision: context.koshaAudit.get(String(product.itemNo))?.collectionDecision || "",
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
      status,
      complianceStatus,
      name: title,
      summary: summaryFor(product),
      sourceUrl: product.sourceUrl || `https://mobile.domeggook.com/${product.itemNo}`,
      sourcePrice,
      minimumResalePrice,
      basePrice: calculatedBasePrice || calculateSalePrice(sourcePrice, minimumResalePrice, context.policy),
      minimumOrderQuantity,
      orderQuantityStep,
      minimumOrderQuantityDecision,
      options: manifestOptions,
      supplierName: product.sellerName || "외부 공급처",
      productInfoNotice: productInfoNoticeFor(product),
      noticeRows: productNoticeRowsFor(product),
      shippingInfo: shipping.known ? "고객에게 별도 배송비를 청구하지 않습니다." : "",
      asInfo: COREABLE_AS_INFO,
      returnExchangeInfo: COREABLE_RETURN_EXCHANGE_INFO,
      memo: `B-090 review=${decision}; MOQ=${minimumOrderQuantity}/${orderQuantityStep}; ${reasonCodes.join(",") || "AUTO_IMPORT_CANDIDATE"}; KOSHA=${koshaGate.status}; compliance=${complianceStatus}; target=${status}`,
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

async function loadKoshaAudit(file) {
  try {
    const audit = JSON.parse(await readFile(file, "utf8"));
    return new Map((audit.items || []).map((item) => [String(item.itemNo), item]));
  } catch {
    return new Map();
  }
}

function reviewCsv(items) {
  const headers = [
    "itemNo",
    "decision",
    "reasonCodes",
    "collectionCategoryCode",
    "categoryCode",
    "categoryConfidence",
    "categoryResolution",
    "sourceCategoryPath",
    "sourcePrice",
    "minimumResalePrice",
    "shippingFee",
    "calculatedBasePrice",
    "minimumOrderQuantity",
    "orderQuantityStep",
    "minimumOrderQuantityDecision",
    "optionCount",
    "activeOptionCount",
    "sellerReviewCount",
    "sellerSatisfaction",
    "detailImageCount",
    "koshaAuditStatus",
    "koshaCollectionDecision",
    "name",
  ];
  const rows = items.map((item) => headers.map((header) => {
    if (header === "categoryConfidence") return item.categoryConfidence.confidence;
    return item[header];
  }).map(csvCell).join(","));
  return `${headers.join(",")}\n${rows.join("\n")}\n`;
}

function summarize(items, categories) {
  const itemNos = items.map((item) => String(item.itemNo));
  const summary = {
    total: items.length,
    import: items.filter((item) => item.decision === "IMPORT").length,
    exclude: items.filter((item) => item.decision === "EXCLUDE").length,
    duplicateItemNumbers: itemNos.length - new Set(itemNos).size,
    moq: {
      one: items.filter((item) => item.minimumOrderQuantityDecision === "MOQ_1").length,
      twoToTen: items.filter((item) => item.minimumOrderQuantityDecision === "MOQ_2_TO_10").length,
      overTen: items.filter((item) => item.minimumOrderQuantityDecision === "MOQ_GT_10").length,
      invalid: items.filter((item) => item.minimumOrderQuantityDecision === "MOQ_INVALID").length,
    },
    categories: categories.map((category) => {
      const actual = items.filter((item) => item.decision === "IMPORT" && item.categoryCode === category.code).length;
      return {
        categoryCode: category.code,
        categoryLabel: category.label,
        target: DEFAULT_TARGET_PER_CATEGORY,
        actual,
        shortfall: Math.max(0, DEFAULT_TARGET_PER_CATEGORY - actual),
        pass: true,
      };
    }),
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
  if (args.selfCheck) {
    assert.equal(calculateBasePrice(3700), 4600);
    assert.equal(moqDecision(1), "MOQ_1");
    assert.equal(moqDecision(6), "MOQ_2_TO_10");
    assert.equal(moqDecision(10), "MOQ_2_TO_10");
    assert.equal(moqDecision(11), "MOQ_GT_10");
    assert.equal(calculateSalePrice(990, 1900), 1900);
    assert.equal(calculateSalePrice(900, 1130), 1200);
    assert.equal(koshaCollectionGate("TRAFFIC_CONE").status, "NOT_APPLICABLE");
    assert.equal(koshaCollectionGate("PPE_SAFETY_HELMET").hardReason, "");
    assert.equal(koshaCollectionGate("WORK_PLATFORM", {
      categoryCode: "WORK_PLATFORM",
      status: "NOT_APPLICABLE",
      collectionDecision: "ALLOW",
    }).status, "MISSING");
    assert.equal(koshaCollectionGate("PPE_SAFETY_HELMET", {
      categoryCode: "PPE_SAFETY_HELMET",
      status: "KOSHA_REGISTRY_MODEL_VERIFIED",
      collectionDecision: "ALLOW",
    }).hardReason, "");
    assert.equal(koshaCollectionGate("PPE_SAFETY_HELMET", {
      categoryCode: "PPE_SAFETY_HELMET",
      status: "OUT_OF_SCOPE_LIGHT_DUTY_HEADGEAR",
      collectionDecision: "EXCLUDE",
    }).hardReason, "KOSHA_COLLECTION_EXCLUDED");
    assert.equal(complianceStatusFor({}, "BARRIER_TAPE", "NOT_APPLICABLE"), "NOT_REQUIRED");
    assert.equal(complianceStatusFor({
      productInfoDuty: { item: [{ name: "인증·허가 사항", desc: "KC 인증 미대상 품목" }] },
    }, "HEAVY_EQUIPMENT_REAR_DETECTOR", "NOT_APPLICABLE"), "NOT_REQUIRED");
    assert.equal(complianceStatusFor({}, "PPE_SAFETY_HELMET", "KOSHA_REGISTRY_MODEL_VERIFIED"), "VERIFIED");
    assert.equal(complianceStatusFor({}, "PPE_SAFETY_HELMET", "KOSHA_REGISTRY_VERIFIED"), "PENDING");
    assert.equal(complianceStatusFor({}, "PPE_SAFETY_HELMET", "SOURCE_EVIDENCE_MISSING"), "PENDING");
    assert.equal(productInfoNoticeFor({ title: "안전용품" }), "");
    const rawNotice = productInfoNoticeFor({
      origin: "상세정보별도표기",
      model: "해당없음",
      manufacturer: "상세정보 별도표기",
      productSize: "1",
      productWeight: "1",
      deliveryMethod: "택배",
      deliveryWaiting: "2일 후 발송",
      productInfoDuty: {
        item: [
          { type: "item", name: "품명 및 모델명", desc: "상세정보 별도표기" },
          { type: "transaction", name: "청약철회 및 계약의 해제에 관한 사항", desc: "상세정보 별도표기" },
        ],
      },
    });
    assert.equal(rawNotice, "품명 및 모델명: 상세정보 별도표기");
    assert.deepEqual(productNoticeRowsFor({
      productInfoDuty: {
        item: [
          { type: "item", name: "품명 및 모델명", desc: "상세정보 별도표기" },
          { type: "transaction", name: "청약철회", desc: "공급처 조건" },
        ],
      },
    }), [{ label: "품명 및 모델명", value: "상세정보 별도표기" }]);
    console.log("Domeggook product review self-check passed");
    return;
  }

  const categories = readCategoryDefinitions();
  if (categories.length !== 81) {
    throw new Error(`카테고리 정의가 81개가 아닙니다: ${categories.length}`);
  }

  let entries = await readCollectedProducts(args.productsDir);
  if (args.limit) entries = entries.slice(0, args.limit);

  const [policy, koshaAudit] = await Promise.all([
    loadPricingPolicy(args),
    loadKoshaAudit(args.koshaAudit),
  ]);
  const context = {
    categories,
    fetchShipping: args.fetchShipping,
    policy,
    koshaAudit,
  };

  const reviewed = await mapLimit(entries, args.shippingConcurrency, (entry) => reviewProduct(entry, context));
  const summary = summarize(reviewed, categories);
  const manifest = {
    generatedAt: new Date().toISOString(),
    source: "B-090 domeggook MOQ product selection",
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

  console.log(`선별 완료: total=${summary.total}, IMPORT=${summary.import}, EXCLUDE=${summary.exclude}`);
  console.log(`- ${DEFAULT_REVIEW_JSON}`);
  console.log(`- ${DEFAULT_REVIEW_CSV}`);
  console.log(`- ${DEFAULT_FILTERED_MANIFEST}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
