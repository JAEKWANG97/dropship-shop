#!/usr/bin/env node
import assert from "node:assert/strict";
import { readdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";

const DEFAULT_API = "http://localhost:8080";
const DEFAULT_PRODUCTS_DIR = "tmp/domeggook-products";
const DEFAULT_MANIFEST = "tmp/domeggook-import-manifest.json";
const DEFAULT_RESULT = "tmp/domeggook-import-result.json";
const MAX_DETAIL_BLOCKS = 50;
const MAX_IMAGE_SIZE = 10 * 1024 * 1024;
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
  node scripts/import-domeggook-products.mjs --self-check

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
  if (argv.includes("--self-check")) return { selfCheck: true };
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

function numberOrNull(value) {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number(String(value).replace(/,/g, ""));
  return Number.isFinite(parsed) ? parsed : null;
}

function optionStatus(value) {
  return ["ACTIVE", "SOLD_OUT", "STOPPED"].includes(value) ? value : "ACTIVE";
}

function optionName(value, index) {
  const name = String(value || "").trim();
  return (name || `옵션 ${index + 1}`).slice(0, 200);
}

function defaultSourceOption() {
  return {
    sourceOptionCode: "00",
    name: "기본",
    sourceAdditionalPrice: 0,
    sourceStockQuantity: null,
    status: "ACTIVE",
    sortOrder: 0,
  };
}

function normalizeSourceOption(option, index) {
  const sourceAdditionalPrice = numberOrNull(option.sourceAdditionalPrice ?? option.additionalPrice) ?? 0;
  return {
    sourceOptionCode: String(option.sourceOptionCode || option.code || index).slice(0, 100),
    name: optionName(option.name, index),
    sourceAdditionalPrice,
    sourceStockQuantity: numberOrNull(option.sourceStockQuantity ?? option.stockQuantity),
    status: optionStatus(option.status),
    sortOrder: numberOrNull(option.sortOrder) ?? index,
  };
}

function sourceOptionsFor(item, product) {
  const options = Array.isArray(item.options) && item.options.length > 0
    ? item.options
    : Array.isArray(product.options) && product.options.length > 0
      ? product.options
      : [defaultSourceOption()];
  return options.map(normalizeSourceOption);
}

function pricedOptionsFor(item, product, policy = DEFAULT_PRICING_POLICY) {
  const sourcePrice = Number(item.sourcePrice || parsePrice(product.priceText));
  const sourceOptions = sourceOptionsFor(item, product);
  const pricedOptions = sourceOptions.map((option) => {
    const sourceOptionPrice = sourcePrice + Number(option.sourceAdditionalPrice || 0);
    const calculatedSalePrice = calculateBasePrice(sourceOptionPrice, policy);
    return {
      ...option,
      sourceOptionPrice,
      calculatedSalePrice,
    };
  });
  const salePrices = pricedOptions
    .map((option) => option.calculatedSalePrice)
    .filter((price) => Number.isFinite(price) && price > 0);
  const basePrice = salePrices.length === 0 ? 0 : Math.min(...salePrices);
  return {
    sourcePrice,
    basePrice,
    options: pricedOptions.map((option) => ({
      sourceOptionCode: option.sourceOptionCode,
      name: option.name,
      sourceAdditionalPrice: option.sourceAdditionalPrice,
      sourceStockQuantity: option.sourceStockQuantity,
      status: option.status,
      sortOrder: option.sortOrder,
      sourceOptionPrice: option.sourceOptionPrice,
      calculatedSalePrice: option.calculatedSalePrice,
      additionalPrice: Math.max(0, option.calculatedSalePrice - basePrice),
    })),
  };
}

function manifestOption(option) {
  return {
    sourceOptionCode: option.sourceOptionCode,
    name: option.name,
    sourceAdditionalPrice: option.sourceAdditionalPrice,
    sourceStockQuantity: option.sourceStockQuantity,
    status: option.status,
    sortOrder: option.sortOrder,
    calculatedSalePrice: option.calculatedSalePrice,
    additionalPrice: option.additionalPrice,
  };
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
    const pricing = pricedOptionsFor({ sourcePrice, options: product.options }, product);
    return {
      itemNo: product.itemNo,
      import: false,
      productFile: path.join(DEFAULT_PRODUCTS_DIR, product.itemNo, "product.json"),
      categoryCode: "",
      status: "HIDDEN",
      complianceStatus: "PENDING",
      name: product.title,
      summary: summaryFor(product),
      sourceUrl: product.sourceUrl || `https://mobile.domeggook.com/${product.itemNo}`,
      sourceItemNo: String(product.itemNo),
      sourcePrice,
      basePrice: pricing.basePrice,
      options: pricing.options.map(manifestOption),
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

async function allAdminProducts(args) {
  const products = [];
  let page = 0;

  while (true) {
    const response = await apiFetch(args, `/api/admin/products?page=${page}&size=100`);
    products.push(...response.products);
    page += 1;
    if (page >= response.totalPages) return products;
  }
}

function imageFormat(bytes) {
  if (bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) {
    return { extension: "jpg", type: "image/jpeg" };
  }
  if (bytes.length >= 8 && bytes.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) {
    return { extension: "png", type: "image/png" };
  }
  if (bytes.length >= 12 && bytes.subarray(0, 4).toString() === "RIFF" && bytes.subarray(8, 12).toString() === "WEBP") {
    return { extension: "webp", type: "image/webp" };
  }
  return null;
}

async function prepareImage(filePath) {
  const bytes = await readFile(filePath);
  if (bytes.length > MAX_IMAGE_SIZE) return null;
  const format = imageFormat(bytes);
  if (!format) return null;
  return {
    bytes,
    filename: `${path.parse(filePath).name}.${format.extension}`,
    type: format.type,
  };
}

async function uploadImage(args, productId, image) {
  const form = new FormData();
  form.set("file", new File([image.bytes], image.filename, { type: image.type }));
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

function manifestIssue(item, pricing) {
  if (!item.import) return "";
  if (!item.categoryCode) return "categoryCode is required";
  if (!item.summary) return "summary is required";
  if (!Number.isFinite(Number(pricing.sourcePrice)) || Number(pricing.sourcePrice) <= 0) return "sourcePrice must be a positive number";
  if (!Number.isFinite(Number(pricing.basePrice)) || Number(pricing.basePrice) <= 0) return "basePrice must be a positive number";
  if (!pricing.options.length) return "options are required";
  const invalidOption = pricing.options.find((option) => !option.name || !Number.isFinite(option.calculatedSalePrice) || option.calculatedSalePrice <= 0);
  if (invalidOption) return `invalid option price: ${invalidOption.name || invalidOption.sourceOptionCode}`;
  if (item.status === "ACTIVE" && item.complianceStatus === "REJECTED") {
    return "ACTIVE import rejects products with rejected compliance";
  }
  if (item.status === "ACTIVE" && ![item.productInfoNotice, item.shippingInfo, item.asInfo, item.returnExchangeInfo].every(Boolean)) {
    return "ACTIVE import requires complete product notice";
  }
  return "";
}

async function importItem(args, item, product, suppliers, products, policy) {
  if (!item.import) return { itemNo: item.itemNo, status: "SKIPPED", reason: "manifest import=false" };
  const pricing = pricedOptionsFor(item, product, policy);
  item.sourcePrice = pricing.sourcePrice;
  item.basePrice = pricing.basePrice;
  item.summary = sanitizePublicSummary(item.summary || summaryFor(product));
  const issue = manifestIssue(item, pricing);
  if (issue) return { itemNo: item.itemNo, status: "FAILED", reason: issue };
  const sourceUrl = item.sourceUrl || product.sourceUrl || null;
  const existing = products.find((candidate) => sourceUrl && candidate.sourceUrl === sourceUrl)
    || products.find((candidate) => !candidate.sourceUrl && candidate.name === item.name);

  const supplier = existing
    ? suppliers.find((candidate) => candidate.id === existing.supplierId)
    : await ensureSupplier(args, suppliers, item.supplierName);
  let created = existing
    ? await apiFetch(args, `/api/admin/products/${existing.id}`)
    : await apiFetch(args, "/api/admin/products", {
        method: "POST",
        body: JSON.stringify({
          supplierId: supplier.id,
          name: item.name,
          summary: item.summary,
          sourcePrice: pricing.sourcePrice,
          sourceItemNo: String(item.itemNo),
          sourceUrl,
          basePrice: pricing.basePrice,
          categoryCode: item.categoryCode,
          status: "HIDDEN",
        }),
      });
  if (existing && sourceUrl && !existing.sourceUrl) {
    created = await apiFetch(args, `/api/admin/products/${created.id}`, {
      method: "PATCH",
      body: JSON.stringify({
        supplierId: created.supplierId,
        name: item.name,
        summary: item.summary,
        sourcePrice: pricing.sourcePrice,
        sourceItemNo: String(item.itemNo),
        sourceUrl,
        basePrice: pricing.basePrice,
        categoryCode: item.categoryCode,
        complianceStatus: created.complianceStatus,
        reason: "기존 수집 상품 원본 연결",
      }),
    });
    existing.sourceUrl = sourceUrl;
  }
  const existingOptionKeys = new Set((created.options || []).map((option) => option.sourceOptionCode || option.name));

  for (const option of pricing.options) {
    if (existingOptionKeys.has(option.sourceOptionCode || option.name)) continue;
    await apiFetch(args, `/api/admin/products/${created.id}/options`, {
      method: "POST",
      body: JSON.stringify({
        name: option.name,
        additionalPrice: option.additionalPrice,
        status: option.status,
        sourceOptionCode: option.sourceOptionCode,
        sourceAdditionalPrice: option.sourceAdditionalPrice,
        sourceStockQuantity: option.sourceStockQuantity,
        sortOrder: option.sortOrder,
      }),
    });
  }

  if (!created.thumbnailImageUrl) {
    const thumbnailImage = await prepareImage(product.thumbnailImagePath);
    if (!thumbnailImage) throw new Error("thumbnail must be jpg, png, or webp and at most 10MB");
    const thumbnail = await uploadImage(args, created.id, thumbnailImage);
    await apiFetch(args, `/api/admin/products/${created.id}/images`, {
      method: "PUT",
      body: JSON.stringify({
        images: [{ type: "THUMBNAIL", imageUrl: thumbnail.imageUrl, sortOrder: 0, altText: item.name }],
        reason: "도매꾹 import 대표 이미지 설정",
      }),
    });
  }

  const detailImagePaths = (product.detailImagePaths || []).slice(0, MAX_DETAIL_BLOCKS);
  const detailImages = (await Promise.all(detailImagePaths.map(prepareImage))).filter(Boolean);
  if (detailImages.length === 0) throw new Error("at least one supported detail image is required");
  if ((created.detailBlocks || []).length !== detailImages.length) {
    const detailBlocks = [];
    for (let index = 0; index < detailImages.length; index += 1) {
      const uploaded = await uploadImage(args, created.id, detailImages[index]);
      detailBlocks.push({ type: "IMAGE", imageUrl: uploaded.imageUrl, htmlContent: null, sortOrder: index, altText: item.name });
    }
    await apiFetch(args, `/api/admin/products/${created.id}/detail-blocks`, {
      method: "PUT",
      body: JSON.stringify({ detailBlocks, reason: "도매꾹 import 상세 이미지 설정" }),
    });
  }

  if (item.productInfoNotice && !created.productNotice) {
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

  if (item.complianceStatus && item.complianceStatus !== "PENDING") {
    await apiFetch(args, `/api/admin/products/${created.id}`, {
      method: "PATCH",
      body: JSON.stringify({
        supplierId: created.supplierId,
        name: item.name,
        summary: item.summary,
        sourcePrice: pricing.sourcePrice,
        sourceItemNo: String(item.itemNo),
        sourceUrl,
        basePrice: pricing.basePrice,
        categoryCode: item.categoryCode,
        complianceStatus: item.complianceStatus,
        reason: "자동 수집 상품 인증 상태 반영",
      }),
    });
  }

  if (created.status !== item.status) {
    await apiFetch(args, `/api/admin/products/${created.id}/status`, {
      method: "PATCH",
      body: JSON.stringify({
        status: item.status || "HIDDEN",
        reason: item.status === "ACTIVE" ? "자동 검수 조건 충족 상품 공개" : "자동 검수 대기 상품 비공개",
      }),
    });
  }

  if (!existing) products.push({ ...created, status: item.status || "HIDDEN" });
  return {
    itemNo: item.itemNo,
    status: existing ? "UPDATED" : "IMPORTED",
    productId: created.id,
    supplierId: supplier.id,
    sourcePrice: pricing.sourcePrice,
    basePrice: pricing.basePrice,
    optionCount: pricing.options.length,
    complianceStatus: item.complianceStatus || "PENDING",
    productStatus: item.status || "HIDDEN",
  };
}

async function runManifest(args) {
  const manifest = JSON.parse(await readFile(args.manifest, "utf8"));
  const results = [];
  if (!args.apply) {
    const policy = await pricingPolicy(args);
    for (const item of manifest.items || []) {
      try {
        const product = JSON.parse(await readFile(item.productFile, "utf8"));
        const pricing = pricedOptionsFor(item, product, policy);
        const issue = manifestIssue(item, pricing);
        results.push({
          itemNo: item.itemNo,
          status: item.import ? (issue ? "FAILED" : "DRY_RUN") : "SKIPPED",
          reason: item.import ? issue : "manifest import=false",
          sourcePrice: pricing.sourcePrice,
          currentBasePrice: Number(item.basePrice || 0),
          calculatedBasePrice: pricing.basePrice,
          optionCount: pricing.options.length,
          minOptionSalePrice: pricing.basePrice,
          maxOptionSalePrice: Math.max(...pricing.options.map((option) => option.calculatedSalePrice)),
          complianceStatus: item.complianceStatus || "PENDING",
          productStatus: item.status || "HIDDEN",
        });
      } catch (error) {
        results.push({ itemNo: item.itemNo, status: "FAILED", reason: error.message });
      }
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
    allAdminProducts(args),
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
  console.log(`import 완료: 신규 ${results.filter((result) => result.status === "IMPORTED").length}개, 이어서 처리 ${results.filter((result) => result.status === "UPDATED").length}개`);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) return usage();
  if (args.selfCheck) {
    const pricing = { sourcePrice: 1000, basePrice: 1300, options: [{ name: "기본", calculatedSalePrice: 1300 }] };
    const active = {
      import: true,
      categoryCode: "TRAFFIC_CONE",
      summary: "라바콘",
      status: "ACTIVE",
      complianceStatus: "NOT_REQUIRED",
      productInfoNotice: "상품 고시",
      shippingInfo: "배송 안내",
      asInfo: "A/S 안내",
      returnExchangeInfo: "반품 안내",
    };
    assert.equal(manifestIssue(active, pricing), "");
    assert.equal(manifestIssue({ ...active, complianceStatus: "PENDING" }, pricing), "");
    assert.match(manifestIssue({ ...active, complianceStatus: "REJECTED" }, pricing), /compliance/);
    assert.match(manifestIssue({ ...active, asInfo: "" }, pricing), /product notice/);
    assert.equal(imageFormat(Buffer.from([0xff, 0xd8, 0xff])).extension, "jpg");
    assert.equal(imageFormat(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])).extension, "png");
    assert.equal(imageFormat(Buffer.from("RIFF0000WEBP")).extension, "webp");
    assert.equal(imageFormat(Buffer.from("GIF89a")), null);
    console.log("Domeggook product import self-check passed");
    return;
  }
  if (args.initManifest) return initManifest();
  return runManifest(args);
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
