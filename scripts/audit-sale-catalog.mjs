#!/usr/bin/env node
import assert from "node:assert/strict";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";

const DEFAULT_API = "http://localhost:8080";
const OUTPUT_JSON = "tmp/sale-catalog-audit.json";
const OUTPUT_CSV = "tmp/sale-catalog-audit.csv";

function argValue(argv, name, fallback = "") {
  const index = argv.indexOf(name);
  return index === -1 ? fallback : argv[index + 1] || "";
}

function usage() {
  console.log(`Usage:
  node scripts/audit-sale-catalog.mjs --cookie-file tmp/admin-cookie.txt
  node scripts/audit-sale-catalog.mjs --api http://localhost:8080 --cookie "ACCESS_TOKEN=..."
  node scripts/audit-sale-catalog.mjs --self-check`);
}

function csvCell(value) {
  return `"${String(value ?? "").replaceAll('"', '""')}"`;
}

function hasNoticeFact(text, label) {
  return new RegExp(`(?:${label})\\s*[:：]?\\s*[^/|\\n]+`, "i").test(text);
}

function issueCodes(product) {
  const notice = product.productNotice?.productInfoNotice || "";
  const issues = [];
  if (!product.saleReady) issues.push(...(product.saleBlockers || []).map((code) => `SALE_${code}`));
  if (!product.sourceItemNo) issues.push("SOURCE_ITEM_NO_MISSING");
  if (!hasNoticeFact(notice, "모델명|품명")) issues.push("MISSING_MODEL_OR_PRODUCT_NAME");
  if (!hasNoticeFact(notice, "제조사|수입자")) issues.push("MISSING_MANUFACTURER");
  if (!hasNoticeFact(notice, "원산지")) issues.push("MISSING_ORIGIN");
  if (!product.productNotice?.shippingInfo) issues.push("MISSING_SHIPPING_INFO");
  if (!product.productNotice?.returnExchangeInfo) issues.push("MISSING_RETURN_INFO");
  if (!["VERIFIED", "NOT_REQUIRED"].includes(product.complianceStatus)) {
    issues.push("COMPLIANCE_REVIEW_REQUIRED");
  }
  return [...new Set(issues)];
}

async function apiFetch(api, cookie, pathname) {
  const response = await fetch(`${api}${pathname}`, { headers: { cookie } });
  if (!response.ok) throw new Error(`${response.status} ${pathname}`);
  return response.json();
}

async function allActiveProducts(api, cookie) {
  const products = [];
  for (let page = 0; ; page += 1) {
    const result = await apiFetch(api, cookie, `/api/admin/products?status=ACTIVE&page=${page}&size=100`);
    products.push(...result.products);
    if (page + 1 >= result.totalPages) return products;
  }
}

async function detailsFor(api, cookie, products) {
  const details = [];
  for (let index = 0; index < products.length; index += 10) {
    const batch = products.slice(index, index + 10);
    details.push(...await Promise.all(
      batch.map((product) => apiFetch(api, cookie, `/api/admin/products/${product.id}`)),
    ));
  }
  return details;
}

async function main() {
  const argv = process.argv.slice(2);
  if (argv.includes("--help") || argv.includes("-h")) {
    usage();
    return;
  }
  if (argv.includes("--self-check")) {
    assert.equal(issueCodes({
      name: "안전모",
      summary: "",
      saleReady: true,
      complianceStatus: "NOT_REQUIRED",
      productNotice: {
        productInfoNotice: "품명 및 모델명: 상세정보 별도표기\n원산지: 해당없음\n제조사: 상세정보 별도표기",
        shippingInfo: "배송 안내",
        returnExchangeInfo: "반품 안내",
      },
    }).includes("PLACEHOLDER_TEXT"), false);
    assert.equal(hasNoticeFact("원산지: 대한민국 / 제조사: 코어블", "원산지"), true);
    console.log("self-check passed");
    return;
  }

  const api = argValue(argv, "--api", DEFAULT_API);
  const cookieFile = argValue(argv, "--cookie-file");
  const cookie = argValue(argv, "--cookie") || (cookieFile ? (await readFile(cookieFile, "utf8")).trim() : "");
  if (!cookie) throw new Error("--cookie or --cookie-file is required");

  const summaries = await allActiveProducts(api, cookie);
  const details = await detailsFor(api, cookie, summaries);
  const products = details.map((product) => {
    const issues = issueCodes(product);
    const blockers = issues.filter((issue) => issue !== "COMPLIANCE_REVIEW_REQUIRED");
    return {
      id: product.id,
      name: product.name,
      categoryCode: product.categoryCode,
      complianceStatus: product.complianceStatus,
      recommendationReady: blockers.length === 0,
      blockers,
      issues,
    };
  });
  const selected = products.filter((product) => product.recommendationReady).slice(0, 20);
  const report = {
    generatedAt: new Date().toISOString(),
    totalActive: products.length,
    recommendationReady: products.filter((product) => product.recommendationReady).length,
    selectedCount: selected.length,
    selectedProductIds: selected.map((product) => product.id),
    products,
  };

  await mkdir(path.dirname(OUTPUT_JSON), { recursive: true });
  await writeFile(OUTPUT_JSON, `${JSON.stringify(report, null, 2)}\n`);
  await writeFile(
    OUTPUT_CSV,
    [
      ["id", "name", "categoryCode", "complianceStatus", "recommendationReady", "issues"],
      ...products.map((product) => [
        product.id,
        product.name,
        product.categoryCode,
        product.complianceStatus,
        product.recommendationReady,
        product.issues.join("|"),
      ]),
    ].map((row) => row.map(csvCell).join(",")).join("\n") + "\n",
  );
  console.log(`audited ${products.length}, recommendation-ready ${selected.length}/20`);
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
