#!/usr/bin/env node
import { readdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";

const DEFAULT_API = "http://localhost:8080";
const DEFAULT_PRODUCTS_DIR = "tmp/domeggook-products";
const DEFAULT_MANIFEST = "tmp/domeggook-import-manifest.json";
const DEFAULT_RESULT = "tmp/domeggook-import-result.json";
const DEFAULT_PRICING_POLICY = {
  commissionRate: 5,
  taxBufferRate: 10,
  overheadRate: 5,
  safetyMarginRate: 5,
  roundingUnit: 100,
  totalMarkupRate: 25,
};

function usage() {
  console.log(`Usage:
  node scripts/import-domeggook-products.mjs --init-manifest
  node scripts/import-domeggook-products.mjs --manifest tmp/domeggook-import-manifest.json
  node scripts/import-domeggook-products.mjs --manifest tmp/domeggook-import-manifest.json --cookie "ACCESS_TOKEN=..." --apply
  node scripts/import-domeggook-products.mjs --manifest tmp/domeggook-import-manifest.json --cookie-file tmp/admin-cookie.txt --apply

Options:
  --api http://localhost:8080
  --apply    실제 관리자 API 적재. 없으면 dry-run`);
}

function argValue(argv, name, fallback = "") {
  const index = argv.indexOf(name);
  return index === -1 ? fallback : argv[index + 1] || "";
}

function parseArgs(argv) {
  if (argv.includes("--help") || argv.includes("-h")) return { help: true };
  return {
    initManifest: argv.includes("--init-manifest"),
    apply: argv.includes("--apply"),
    api: argValue(argv, "--api", DEFAULT_API),
    manifest: argValue(argv, "--manifest", DEFAULT_MANIFEST),
    cookie: argValue(argv, "--cookie"),
    cookieFile: argValue(argv, "--cookie-file"),
  };
}

function parsePrice(priceText) {
  return Number(String(priceText || "").match(/[\d,]+/)?.[0].replace(/[^\d]/g, "")) || 0;
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

function normalizedBasePrice(item, sourcePrice, policy = DEFAULT_PRICING_POLICY) {
  const roundingUnit = Number(policy.roundingUnit || 100);
  const basePrice = Number(item.basePrice || 0);
  if (Number.isFinite(basePrice) && basePrice > 0 && basePrice % roundingUnit === 0) {
    return basePrice;
  }
  return calculateBasePrice(sourcePrice, policy);
}

function publicSummaryPart(part) {
  const value = String(part || "").trim();
  if (!value) return "";
  if (value.includes("도매꾹")) return "";
  if (value.includes("상품번호")) return "";
  if (value.startsWith("최대구매수량")) return "";
  return value;
}

function sanitizePublicSummary(summary) {
  return String(summary || "")
    .split(/\s+\/\s+/)
    .map(publicSummaryPart)
    .filter(Boolean)
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

async function readCollectedProducts() {
  const entries = await readdir(DEFAULT_PRODUCTS_DIR, { withFileTypes: true });
  const products = [];
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    const filePath = path.join(DEFAULT_PRODUCTS_DIR, entry.name, "product.json");
    try {
      products.push(JSON.parse(await readFile(filePath, "utf8")));
    } catch {
      // ponytail: 수집 산출물 폴더만 훑는다. 깨진 파일은 manifest에서 제외하고 다시 수집하면 된다.
    }
  }
  return products.sort((a, b) => String(a.itemNo).localeCompare(String(b.itemNo)));
}

async function initManifest() {
  const products = await readCollectedProducts();
  const items = products.map((product) => {
    const sourcePrice = parsePrice(product.priceText);
    return {
      itemNo: product.itemNo,
      import: false,
      productFile: path.join(DEFAULT_PRODUCTS_DIR, product.itemNo, "product.json"),
      categoryCode: "",
      status: "HIDDEN",
      name: product.title,
      summary: summaryFor(product),
      sourcePrice,
      basePrice: calculateBasePrice(sourcePrice),
      optionName: "기본",
      additionalPrice: 0,
      supplierName: product.sellerName || "도매꾹 공급처",
      productInfoNotice: "",
      shippingInfo: "",
      asInfo: "",
      returnExchangeInfo: "",
      memo: "ACTIVE 전환 전 인증/KC, 상품고시, 가격, 이미지 품질 확인",
    };
  });
  await writeFile(DEFAULT_MANIFEST, `${JSON.stringify({ items }, null, 2)}\n`);
  console.log(`${DEFAULT_MANIFEST} 생성 완료: ${items.length}개`);
}

async function cookieHeader(args) {
  if (args.cookie) return args.cookie;
  if (args.cookieFile) return (await readFile(args.cookieFile, "utf8")).trim();
  return "";
}

async function apiFetch(args, pathName, init = {}) {
  const cookie = await cookieHeader(args);
  const response = await fetch(`${args.api}${pathName}`, {
    ...init,
    headers: {
      ...(init.body && !(init.body instanceof FormData) ? { "content-type": "application/json" } : {}),
      ...(cookie ? { cookie } : {}),
      ...init.headers,
    },
  });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`${init.method || "GET"} ${pathName} failed: ${response.status} ${body.slice(0, 200)}`);
  }
  return response.status === 204 ? null : response.json();
}

async function pricingPolicy(args) {
  try {
    return await apiFetch(args, "/api/admin/pricing-policy");
  } catch {
    return DEFAULT_PRICING_POLICY;
  }
}

async function uploadImage(args, productId, filePath) {
  const form = new FormData();
  const bytes = await readFile(filePath);
  form.set("file", new File([bytes], path.basename(filePath)));
  return apiFetch(args, `/api/admin/products/${productId}/images/upload`, { method: "POST", body: form });
}

async function ensureSupplier(args, suppliers, name) {
  const found = suppliers.find((supplier) => supplier.name === name);
  if (found) return found;
  const created = await apiFetch(args, "/api/admin/suppliers", {
    method: "POST",
    body: JSON.stringify({ name, contactName: "", phone: "", email: "", memo: "도매꾹 import", status: "ACTIVE" }),
  });
  suppliers.push(created);
  return created;
}

function manifestIssue(item) {
  if (!item.import) return "";
  if (!item.categoryCode) return "categoryCode is required";
  if (!item.summary) return "summary is required";
  if (!Number.isFinite(Number(item.sourcePrice)) || Number(item.sourcePrice) <= 0) return "sourcePrice must be a positive number";
  if (!Number.isFinite(Number(item.basePrice)) || Number(item.basePrice) <= 0) return "basePrice must be a positive number";
  if (item.status === "ACTIVE" && !item.productInfoNotice) return "ACTIVE import requires product notice";
  return "";
}

async function importItem(args, item, product, suppliers, products, policy) {
  if (!item.import) return { itemNo: item.itemNo, status: "SKIPPED", reason: "manifest import=false" };
  item.sourcePrice = Number(item.sourcePrice || item.basePrice || parsePrice(product.priceText));
  item.basePrice = normalizedBasePrice(item, item.sourcePrice, policy);
  item.summary = sanitizePublicSummary(item.summary || summaryFor(product));
  const issue = manifestIssue(item);
  if (issue) return { itemNo: item.itemNo, status: "FAILED", reason: issue };
  if (products.some((existing) => existing.name === item.name)) {
    return { itemNo: item.itemNo, status: "SKIPPED", reason: "same product name already exists" };
  }

  const supplier = await ensureSupplier(args, suppliers, item.supplierName);
  const created = await apiFetch(args, "/api/admin/products", {
    method: "POST",
    body: JSON.stringify({
      supplierId: supplier.id,
      name: item.name,
      summary: item.summary,
      sourcePrice: item.sourcePrice,
      basePrice: item.basePrice,
      categoryCode: item.categoryCode,
      status: item.status || "HIDDEN",
    }),
  });

  await apiFetch(args, `/api/admin/products/${created.id}/options`, {
    method: "POST",
    body: JSON.stringify({
      name: item.optionName || "기본",
      additionalPrice: item.additionalPrice || 0,
      status: "ACTIVE",
    }),
  });

  const thumbnail = await uploadImage(args, created.id, product.thumbnailImagePath);
  await apiFetch(args, `/api/admin/products/${created.id}/images`, {
    method: "PUT",
    body: JSON.stringify({
      images: [{ type: "THUMBNAIL", imageUrl: thumbnail.imageUrl, sortOrder: 0, altText: item.name }],
      reason: "도매꾹 import 대표 이미지 설정",
    }),
  });

  const detailBlocks = [];
  for (let index = 0; index < product.detailImagePaths.length; index += 1) {
    const uploaded = await uploadImage(args, created.id, product.detailImagePaths[index]);
    detailBlocks.push({ type: "IMAGE", imageUrl: uploaded.imageUrl, htmlContent: null, sortOrder: index, altText: item.name });
  }
  await apiFetch(args, `/api/admin/products/${created.id}/detail-blocks`, {
    method: "PUT",
    body: JSON.stringify({ detailBlocks, reason: "도매꾹 import 상세 이미지 설정" }),
  });

  if (item.productInfoNotice) {
    await apiFetch(args, `/api/admin/products/${created.id}/notice`, {
      method: "PUT",
      body: JSON.stringify({
        productInfoNotice: item.productInfoNotice,
        shippingInfo: item.shippingInfo,
        asInfo: item.asInfo,
        returnExchangeInfo: item.returnExchangeInfo,
        reason: "도매꾹 import 상품 고시 설정",
      }),
    });
  }

  products.push(created);
  return { itemNo: item.itemNo, status: "IMPORTED", productId: created.id, supplierId: supplier.id };
}

async function runManifest(args) {
  const manifest = JSON.parse(await readFile(args.manifest, "utf8"));
  const results = [];
  if (!args.apply) {
    const policy = await pricingPolicy(args);
    for (const item of manifest.items || []) {
      const sourcePrice = Number(item.sourcePrice || item.basePrice || 0);
      const checkedItem = {
        ...item,
        sourcePrice,
        basePrice: normalizedBasePrice(item, sourcePrice, policy),
      };
      const issue = manifestIssue(checkedItem);
      results.push({
        itemNo: item.itemNo,
        status: item.import ? (issue ? "FAILED" : "DRY_RUN") : "SKIPPED",
        reason: item.import ? issue : "manifest import=false",
        sourcePrice,
        currentBasePrice: Number(item.basePrice || 0),
        calculatedBasePrice: calculateBasePrice(sourcePrice, policy),
      });
    }
    await writeFile(DEFAULT_RESULT, `${JSON.stringify(results, null, 2)}\n`);
    console.log(`dry-run 완료: ${results.length}개. 실제 적재는 --apply 필요`);
    return;
  }

  if (!(await cookieHeader(args))) {
    throw new Error("--apply requires --cookie or --cookie-file");
  }

  const [suppliers, products] = await Promise.all([
    apiFetch(args, "/api/admin/suppliers"),
    apiFetch(args, "/api/admin/products"),
  ]);
  const policy = await pricingPolicy(args);

  for (const item of manifest.items || []) {
    try {
      const product = JSON.parse(await readFile(item.productFile, "utf8"));
      results.push(await importItem(args, item, product, suppliers, products, policy));
    } catch (error) {
      results.push({ itemNo: item.itemNo, status: "FAILED", reason: error.message });
    }
  }
  await writeFile(DEFAULT_RESULT, `${JSON.stringify(results, null, 2)}\n`);
  console.log(`import 완료: ${results.filter((result) => result.status === "IMPORTED").length}개`);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) return usage();
  if (args.initManifest) return initManifest();
  return runManifest(args);
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
