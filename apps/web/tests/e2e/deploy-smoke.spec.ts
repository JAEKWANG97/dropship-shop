import { expect, test } from "@playwright/test";
import { API_BASE_URL, expectNoHorizontalOverflow, isLocalTarget, WEB_BASE_URL } from "./helpers";

const apiBaseUrl = process.env.E2E_API_BASE_URL ?? (isLocalTarget() ? API_BASE_URL : WEB_BASE_URL);

type ProductSummary = {
  id: string;
};

async function deployedProductId() {
  const response = await fetch(`${apiBaseUrl}/api/products`);
  expect(response.ok).toBeTruthy();
  const products = (await response.json()) as ProductSummary[];
  expect(products.length).toBeGreaterThan(0);
  return products[0].id;
}

test("deployed public pages render without horizontal overflow", async ({ page }) => {
  test.setTimeout(60_000);
  const routes = [
    "/",
    "/products",
    "/policies",
    "/policies/terms",
    "/policies/privacy",
    "/policies/shipping",
    "/policies/cancellation-refund",
    "/policies/stock-risk",
    "/company",
    "/support",
    "/login",
  ];

  for (const route of routes) {
    const response = await page.goto(route, { waitUntil: "domcontentloaded" });
    expect(response?.status(), route).toBe(200);
    await expect(page.locator("h1").first()).toBeVisible();
    await expect(page.locator(".brand").first()).toBeVisible();
    await expect(page.getByLabel("상품 검색")).toBeVisible();
    await expectNoHorizontalOverflow(page);
  }
});

test("deployed product detail exposes purchase CTAs", async ({ page }, testInfo) => {
  const productId = await deployedProductId();
  const response = await page.goto(`/products/${productId}`);
  expect(response?.status()).toBe(200);

  const purchaseForm = page.locator(".cart-add-form");
  await expect(purchaseForm.getByRole("button", { name: "장바구니", exact: true })).toBeVisible();
  await expect(purchaseForm.getByRole("button", { name: "바로구매", exact: true })).toBeVisible();

  const mobileBar = page.locator(".mobile-purchase-bar");
  if (testInfo.project.name === "mobile") {
    await expect(mobileBar.getByRole("button", { name: "장바구니 담기" })).toBeVisible();
    await expect(mobileBar.getByRole("button", { name: "바로구매", exact: true })).toBeVisible();
  } else {
    await expect(mobileBar).toBeHidden();
  }
  await expectNoHorizontalOverflow(page);
});

test("deployed health is public and dev login is not exposed", async ({ request }) => {
  const health = await request.get(`${apiBaseUrl}/api/health`);
  expect(health.status()).toBe(200);
  await expect(health).toBeOK();
  await expect(await health.json()).toMatchObject({ status: "ok" });

  const devLogin = await request.get(`${apiBaseUrl}/api/dev/login?role=ADMIN`);
  expect(devLogin.status()).toBe(isLocalTarget() ? 200 : 404);
});
