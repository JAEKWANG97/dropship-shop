#!/usr/bin/env node
import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { mkdir, readdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";
import { promisify } from "node:util";
import {
  readCategoryDefinitions,
  resolveReviewCategory,
} from "./review-domeggook-products.mjs";

const DEFAULT_PRODUCTS_DIR = "tmp/domeggook-products";
const DEFAULT_JSON = "tmp/domeggook-kosha-cert-audit.json";
const DEFAULT_CSV = "tmp/domeggook-kosha-cert-audit.csv";
const DEFAULT_OCR_DIR = "tmp/domeggook-kosha-ocr";
const KOSHA_API_URL = "https://apis.data.go.kr/B552468/oshci/getoshci";
const execFileAsync = promisify(execFile);
const OCR_POSITIVE_PATTERN = /안전\s*인증서|KCs?\s*인증|한국산업안전보건공단.{0,20}인증|안전\s*인증\s*(?:필|번호)/i;
const OCR_EXPLICIT_NOT_CERTIFIED_PATTERN = /(?:KCs?|한국산업안전보건공단).{0,30}인증제품이\s*아닙니다/i;
const OCR_CERT_FIELD_EMPTY_PATTERN = /인증번호.{0,20}해당\s*사항?\s*없음/i;
const LIGHT_DUTY_ONLY_PATTERN = /(?:경작업모|경작업\s*현장|위험한\s*작업\s*현장.{0,20}사용을?\s*금)/i;
const CERT_NUMBER_PATTERN = /\b\d{2}-AV[0-9A-Za-z]{3}-\d{4}\b/g;

const KOSHA_REQUIREMENTS = {
  PPE_SAFETY_HELMET: "KCS_OR_SELF_DECLARATION",
  PPE_SAFETY_SHOES: "KCS",
  PPE_FALL_ARREST_HARNESS: "KCS",
  PPE_SAFETY_BELT: "KCS",
  PPE_SAFETY_GLASSES: "KCS_OR_SELF_DECLARATION",
  PPE_RESPIRATOR: "KCS",
  PPE_EAR_PROTECTION: "KCS",
  PPE_INSULATED_GLOVES: "KCS",
  PPE_PROTECTIVE_CLOTHING: "KCS",
  SMART_SAFETY_HELMET: "KCS_AND_DEVICE_CERTS",
  SMART_SAFETY_HARNESS: "KCS_AND_DEVICE_CERTS",
  FALL_PREVENTION_GUARDRAIL: "KCS_OR_SELF_DECLARATION",
  WORK_PLATFORM: "KCS_OR_SELF_DECLARATION",
};

const KOSHA_EVIDENCE_PATTERN = /KCs|산업안전|안전인증|자율안전확인|보호구\s*인증/i;
const EMPTY_EVIDENCE_PATTERN = /해당.*없음|없음|비대상|참고|참조|별도\s*(?:표기|표시)|^-$/i;

function usage() {
  console.log(`Usage:
  node scripts/audit-domeggook-kosha-certifications.mjs
  node scripts/audit-domeggook-kosha-certifications.mjs --run-ocr
  node scripts/audit-domeggook-kosha-certifications.mjs --limit 10
  node scripts/audit-domeggook-kosha-certifications.mjs --self-check

Options:
  --products-dir tmp/domeggook-products
  --json tmp/domeggook-kosha-cert-audit.json
  --csv tmp/domeggook-kosha-cert-audit.csv
  --ocr-dir /tmp/coreable-kosha-ocr
  --run-ocr
  --data-go-key <service key>
  --limit 10`);
}

function argValue(argv, name, fallback = "") {
  const index = argv.indexOf(name);
  return index === -1 ? fallback : argv[index + 1] || fallback;
}

function parseArgs(argv) {
  const cliKey = argValue(argv, "--data-go-key");
  return {
    help: argv.includes("--help") || argv.includes("-h"),
    selfCheck: argv.includes("--self-check"),
    productsDir: argValue(argv, "--products-dir", DEFAULT_PRODUCTS_DIR),
    json: argValue(argv, "--json", DEFAULT_JSON),
    csv: argValue(argv, "--csv", DEFAULT_CSV),
    runOcr: argv.includes("--run-ocr"),
    ocrDir: argValue(argv, "--ocr-dir", DEFAULT_OCR_DIR),
    dataGoKeys: cliKey
      ? [{ name: "CLI", value: cliKey, encoded: cliKey.includes("%") }]
      : [
          { name: "DECODED", value: process.env.DATA_GO_KR_SERVICE_KEY_DECODED || process.env.DATA_GO_KR_SERVICE_KEY || "", encoded: false },
          { name: "ENCODED", value: process.env.DATA_GO_KR_SERVICE_KEY_ENCODED || "", encoded: true },
        ].filter((key) => key.value),
    limit: Number(argValue(argv, "--limit", "0")) || 0,
  };
}

function certificationNumbers(claims, ocrText) {
  return [...new Set([
    ...claims.flatMap((claim) => String(claim.number || "").match(CERT_NUMBER_PATTERN) || []),
    ...(ocrText.match(CERT_NUMBER_PATTERN) || []),
  ].map((number) => number.toUpperCase()))];
}

function normalizedModel(value) {
  return String(value || "").toUpperCase().replace(/[^0-9A-Z가-힣]/g, "");
}

function hasRegistryModelMatch(product, ocrText, registryMatches) {
  const source = normalizedModel(`${product.title || ""} ${(product.options || []).map((option) => option.name).join(" ")} ${ocrText}`);
  return registryMatches.some((match) => {
    const model = normalizedModel(match.modelName);
    return model.length >= 3 && source.includes(model);
  });
}

function xmlTag(xml, tag) {
  return xml.match(new RegExp(`<${tag}>(?:<!\\[CDATA\\[)?([\\s\\S]*?)(?:\\]\\]>)?</${tag}>`))?.[1]?.trim() || "";
}

function parseRegistryResponse(xml, certNumber) {
  const resultCode = xmlTag(xml, "resultCode");
  if (resultCode && resultCode !== "00") {
    throw new Error(`KOSHA registry ${resultCode}: ${xmlTag(xml, "resultMsg")}`);
  }
  return [...xml.matchAll(/<item>([\s\S]*?)<\/item>/g)]
    .map((match) => ({
      certificationNumber: xmlTag(match[1], "crtfcNo"),
      manufacturer: xmlTag(match[1], "mfplntNm"),
      productName: xmlTag(match[1], "ptqgrCrtfcPrdlstNm") || xmlTag(match[1], "pteqgrCrtfcPrdlstNm"),
      modelName: xmlTag(match[1], "pteqgrFomNm"),
      capacityGrade: xmlTag(match[1], "pteqgrCpctyGradNm"),
      cancellationReason: xmlTag(match[1], "pteqgrCanclResnSeNm"),
    }))
    .filter((item) => item.certificationNumber.toUpperCase() === certNumber.toUpperCase());
}

async function lookupRegistry(certNumber, credential) {
  const url = new URL(KOSHA_API_URL);
  url.searchParams.set("pageNo", "1");
  url.searchParams.set("numOfRows", "10");
  url.searchParams.set("pteqgrCrtfcTyCd", "BH");
  url.searchParams.set("crtfcNo", certNumber);
  if (credential.encoded) url.search = `serviceKey=${credential.value}&${url.searchParams}`;
  else url.searchParams.set("serviceKey", credential.value);
  const response = await fetch(url);
  if (!response.ok) throw new Error(`KOSHA registry HTTP ${response.status}`);
  return parseRegistryResponse(await response.text(), certNumber);
}

function infoDutyRows(product) {
  const duty = product.productInfoDuty;
  if (Array.isArray(duty)) return duty;
  if (Array.isArray(duty?.item)) return duty.item;
  return [];
}

function certificationClaims(product) {
  const structured = (Array.isArray(product.safetyCert) ? product.safetyCert : [])
    .filter((cert) => cert && typeof cert === "object")
    .map((cert) => ({
      source: "safetyCert",
      type: String(cert.certType || cert.type || ""),
      name: String(cert.certName || cert.name || ""),
      number: String(cert.no || "").trim(),
      text: [
        cert.certType,
        cert.type,
        cert.certName,
        cert.name,
        cert.no,
      ].filter(Boolean).join(" "),
    }))
    .filter((claim) => (
      (claim.number && !EMPTY_EVIDENCE_PATTERN.test(claim.number))
      || KOSHA_EVIDENCE_PATTERN.test(`${claim.type} ${claim.name} ${claim.text}`)
    ));

  const notices = infoDutyRows(product)
    .filter((row) => /인증|허가|KC/i.test(String(row?.name || "")))
    .map((row) => ({
      source: "productInfoDuty",
      type: String(row.name || ""),
      name: String(row.name || ""),
      number: "",
      text: String(row.desc || row.value || row.content || "").trim(),
    }))
    .filter((claim) => claim.text && !EMPTY_EVIDENCE_PATTERN.test(claim.text));

  return [...structured, ...notices];
}

export function auditCertification(product, categoryCode, ocrEvidence = [], registryMatches = []) {
  const requirement = KOSHA_REQUIREMENTS[categoryCode] || "NOT_APPLICABLE";
  const claims = certificationClaims(product);
  const koshaClaims = claims.filter((claim) => KOSHA_EVIDENCE_PATTERN.test(`${claim.type} ${claim.name} ${claim.text}`));
  const otherClaims = claims.filter((claim) => !koshaClaims.includes(claim));
  const ocrText = ocrEvidence.map((evidence) => evidence.text).join("\n");
  const ocrSnippets = ocrText.split("\n")
    .map((line) => line.trim())
    .filter((line) => (
      OCR_POSITIVE_PATTERN.test(line)
      || OCR_EXPLICIT_NOT_CERTIFIED_PATTERN.test(line)
      || OCR_CERT_FIELD_EMPTY_PATTERN.test(line)
    ))
    .slice(0, 20);
  const numbers = certificationNumbers(claims, ocrText);

  if (requirement === "NOT_APPLICABLE") {
    return {
      requirement,
      status: "NOT_APPLICABLE",
      collectionDecision: "ALLOW",
      action: "CONTINUE_COLLECTION_REVIEW",
      claims,
      certificationNumbers: numbers,
      ocrSnippets,
    };
  }
  if (OCR_EXPLICIT_NOT_CERTIFIED_PATTERN.test(ocrText)) {
    if (LIGHT_DUTY_ONLY_PATTERN.test(`${product.title || ""}\n${ocrText}`)) {
      return {
        requirement,
        status: "OUT_OF_SCOPE_LIGHT_DUTY_HEADGEAR",
        collectionDecision: "EXCLUDE",
        action: "EXCLUDE_FROM_SAFETY_HELMET_CATALOG",
        claims,
        certificationNumbers: numbers,
        ocrSnippets,
      };
    }
    return {
      requirement,
      status: "SOURCE_EXPLICIT_NOT_CERTIFIED",
      collectionDecision: "EXCLUDE",
      action: "EXCLUDE_FROM_COLLECTION",
      claims,
      certificationNumbers: numbers,
      ocrSnippets,
    };
  }
  if (registryMatches.some((match) => match.cancellationReason)) {
    return {
      requirement,
      status: "KOSHA_REGISTRY_CANCELLED",
      collectionDecision: "EXCLUDE",
      action: "EXCLUDE_FROM_COLLECTION",
      claims,
      certificationNumbers: numbers,
      ocrSnippets,
    };
  }
  if (hasRegistryModelMatch(product, ocrText, registryMatches)) {
    return {
      requirement,
      status: "KOSHA_REGISTRY_MODEL_VERIFIED",
      collectionDecision: "ALLOW",
      action: "CONTINUE_COLLECTION_REVIEW",
      claims,
      certificationNumbers: numbers,
      ocrSnippets,
    };
  }
  if (registryMatches.length) {
    return {
      requirement,
      status: "KOSHA_REGISTRY_VERIFIED",
      collectionDecision: "ALLOW",
      action: "COLLECT_WITH_COMPLIANCE_PENDING",
      claims,
      certificationNumbers: numbers,
      ocrSnippets,
    };
  }
  if (OCR_POSITIVE_PATTERN.test(ocrText) || numbers.length) {
    return {
      requirement,
      status: "SOURCE_KOSHA_EVIDENCE_UNVERIFIED",
      collectionDecision: "ALLOW",
      action: "COLLECT_WITH_COMPLIANCE_PENDING",
      claims,
      certificationNumbers: numbers,
      ocrSnippets,
    };
  }
  if (OCR_CERT_FIELD_EMPTY_PATTERN.test(ocrText)) {
    return {
      requirement,
      status: "SOURCE_CERT_FIELD_NOT_APPLICABLE",
      collectionDecision: "ALLOW",
      action: "COLLECT_WITH_COMPLIANCE_PENDING",
      claims,
      certificationNumbers: numbers,
      ocrSnippets,
    };
  }
  if (koshaClaims.length) {
    return {
      requirement,
      status: "SOURCE_KOSHA_EVIDENCE_UNVERIFIED",
      collectionDecision: "ALLOW",
      action: "COLLECT_WITH_COMPLIANCE_PENDING",
      claims,
      certificationNumbers: numbers,
      ocrSnippets,
    };
  }
  return {
    requirement,
    status: otherClaims.length ? "NON_KOSHA_CERT_ONLY" : "SOURCE_EVIDENCE_MISSING",
    collectionDecision: "ALLOW",
    action: "COLLECT_WITH_COMPLIANCE_PENDING",
    claims,
    certificationNumbers: numbers,
    ocrSnippets,
  };
}

async function readProducts(productsDir) {
  const entries = await readdir(productsDir, { withFileTypes: true });
  const products = [];
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    try {
      products.push(JSON.parse(await readFile(path.join(productsDir, entry.name, "product.json"), "utf8")));
    } catch {
      // A broken collection artifact is omitted; rerun collection for that item.
    }
  }
  return products.sort((a, b) => String(a.itemNo).localeCompare(String(b.itemNo)));
}

function csvCell(value) {
  const text = Array.isArray(value)
    ? value.map((item) => typeof item === "object" ? JSON.stringify(item) : item).join("|")
    : String(value ?? "");
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

function toCsv(items) {
  const headers = [
    "itemNo",
    "categoryCode",
    "requirement",
    "status",
    "collectionDecision",
    "action",
    "certificationNumbers",
    "registryMatches",
    "ocrSnippets",
    "name",
    "sourceUrl",
  ];
  const rows = items.map((item) => headers.map((header) => csvCell(item[header])).join(","));
  return `${headers.join(",")}\n${rows.join("\n")}\n`;
}

function summarize(items) {
  const statusCounts = {};
  for (const item of items) statusCounts[item.status] = (statusCounts[item.status] || 0) + 1;
  return {
    total: items.length,
    koshaScope: items.filter((item) => item.requirement !== "NOT_APPLICABLE").length,
    allow: items.filter((item) => item.collectionDecision === "ALLOW").length,
    exclude: items.filter((item) => item.collectionDecision === "EXCLUDE").length,
    statusCounts,
    note: "Certification filtering is one input to the product collection decision.",
  };
}

async function ocrEvidenceFor(ocrDir, itemNo) {
  if (!ocrDir) return [];
  try {
    const files = (await readdir(ocrDir))
      .filter((file) => file.startsWith(`${itemNo}-`) && file.endsWith(".txt"));
    return Promise.all(files.map(async (file) => ({
      file,
      text: await readFile(path.join(ocrDir, file), "utf8"),
    })));
  } catch {
    return [];
  }
}

async function generateOcr(productsDir, ocrDir, products, categories) {
  await mkdir(ocrDir, { recursive: true });
  const jobs = [];
  for (const product of products) {
    if (!KOSHA_REQUIREMENTS[resolveReviewCategory(product, categories).code]) continue;
    const imageDir = path.join(productsDir, String(product.itemNo), "images");
    try {
      for (const file of await readdir(imageDir)) {
        if (!/^detail-.*\.(?:jpe?g|png|webp)$/i.test(file)) continue;
        const output = path.join(ocrDir, `${product.itemNo}-${path.parse(file).name}.txt`);
        try {
          await readFile(output);
          continue;
        } catch {
          // Generate missing OCR text.
        }
        jobs.push({
          image: path.join(imageDir, file),
          output,
        });
      }
    } catch {
      // Missing detail images remain missing evidence in the audit.
    }
  }
  let index = 0;
  async function worker() {
    while (index < jobs.length) {
      const job = jobs[index++];
      const { stdout } = await execFileAsync("tesseract", [
        job.image,
        "stdout",
        "-l",
        "kor+eng",
      ], { maxBuffer: 20 * 1024 * 1024 });
      await writeFile(job.output, stdout);
    }
  }
  await Promise.all(Array.from({ length: Math.min(4, jobs.length) }, worker));
  console.log(`OCR generated: ${jobs.length} detail images -> ${ocrDir}`);
}

function selfCheck() {
  assert.equal(auditCertification({}, "TRAFFIC_CONE").status, "NOT_APPLICABLE");
  assert.equal(auditCertification({}, "PPE_SAFETY_SHOES").status, "SOURCE_EVIDENCE_MISSING");
  assert.equal(auditCertification({
    safetyCert: [{ certType: "방송통신기자재", no: "R-R-TEST" }],
  }, "SMART_SAFETY_HELMET").status, "NON_KOSHA_CERT_ONLY");
  assert.equal(auditCertification({
    safetyCert: [{ certType: "보호구 안전인증", certName: "KCs", no: "TEST" }],
  }, "PPE_SAFETY_HELMET").status, "SOURCE_KOSHA_EVIDENCE_UNVERIFIED");
  assert.equal(auditCertification({}, "PPE_SAFETY_HELMET", [{
    text: "한국산업안전보건공단 KCs 인증제품이 아닙니다.",
  }]).collectionDecision, "EXCLUDE");
  assert.equal(auditCertification({ title: "경작업모" }, "PPE_SAFETY_HELMET", [{
    text: "KCS 인증제품이 아닙니다. 위험한 작업 현장에서는 사용을 금합니다.",
  }]).status, "OUT_OF_SCOPE_LIGHT_DUTY_HEADGEAR");
  assert.equal(auditCertification({ title: "MODEL-1 안전모" }, "PPE_SAFETY_HELMET", [{
    text: "안전인증번호 20-AV2CQ-0001 MODEL-1",
  }], [{
    certificationNumber: "20-AV2CQ-0001",
    modelName: "MODEL-1",
    cancellationReason: "",
  }]).status, "KOSHA_REGISTRY_MODEL_VERIFIED");
  assert.deepEqual(parseRegistryResponse(`
    <response><header><resultCode>00</resultCode></header><body><items><item>
      <crtfcNo>20-AV2CQ-0001</crtfcNo><mfplntNm>제조사</mfplntNm>
      <ptqgrCrtfcPrdlstNm>안전모</ptqgrCrtfcPrdlstNm><pteqgrFomNm>MODEL-1</pteqgrFomNm>
    </item></items></body></response>
  `, "20-AV2CQ-0001")[0].modelName, "MODEL-1");
  console.log("KOSHA certification audit self-check passed");
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) return usage();
  if (args.selfCheck) return selfCheck();

  const categories = readCategoryDefinitions();
  let products = await readProducts(args.productsDir);
  if (args.limit) products = products.slice(0, args.limit);
  if (args.runOcr) await generateOcr(args.productsDir, args.ocrDir, products, categories);

  const evidence = await Promise.all(products.map(async (product) => ({
    product,
    category: resolveReviewCategory(product, categories),
    ocr: await ocrEvidenceFor(args.ocrDir, product.itemNo),
  })));
  const numbers = [...new Set(evidence.flatMap(({ product, category, ocr }) => (
    auditCertification(product, category.code, ocr).certificationNumbers
  )))];
  const registry = new Map();
  let registryLookupCount = 0;
  let activeDataGoKey = args.dataGoKeys[0];
  if (activeDataGoKey) {
    for (const number of numbers) {
      try {
        registry.set(number, await lookupRegistry(number, activeDataGoKey));
      } catch (firstError) {
        const fallback = args.dataGoKeys.find((key) => key !== activeDataGoKey);
        if (!fallback) throw firstError;
        registry.set(number, await lookupRegistry(number, fallback));
        activeDataGoKey = fallback;
      }
      registryLookupCount++;
    }
  }

  const items = evidence.map(({ product, category, ocr }) => {
    const initial = auditCertification(product, category.code, ocr);
    const registryMatches = initial.certificationNumbers.flatMap((number) => registry.get(number) || []);
    const audit = auditCertification(product, category.code, ocr, registryMatches);
    return {
      itemNo: String(product.itemNo || ""),
      name: String(product.title || ""),
      sourceUrl: product.sourceUrl || `https://mobile.domeggook.com/${product.itemNo}`,
      categoryCode: category.code,
      categoryResolution: category.resolution,
      requirement: audit.requirement,
      status: audit.status,
      collectionDecision: audit.collectionDecision,
      action: audit.action,
      certificationNumbers: audit.certificationNumbers,
      registryMatches,
      claims: audit.claims,
      ocrEvidenceFiles: ocr
        .filter((evidence) => audit.ocrSnippets.some((snippet) => evidence.text.includes(snippet)))
        .map((evidence) => evidence.file),
      ocrSnippets: audit.ocrSnippets,
    };
  });
  const output = {
    generatedAt: new Date().toISOString(),
    officialRegistryLookup: !activeDataGoKey
      ? "NOT_EXECUTED_DATA_GO_KR_KEY_MISSING"
      : registryLookupCount
        ? `EXECUTED_DATA_GO_KR_${activeDataGoKey.name}`
        : "NOT_EXECUTED_NO_CERTIFICATION_NUMBERS",
    officialRegistryLookupCount: registryLookupCount,
    summary: summarize(items),
    items,
  };

  await writeFile(args.json, `${JSON.stringify(output, null, 2)}\n`);
  await writeFile(args.csv, toCsv(items));
  console.log(JSON.stringify(output.summary, null, 2));
  console.log(`- ${args.json}`);
  console.log(`- ${args.csv}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
