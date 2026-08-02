import { expect, test } from "@playwright/test";
import { activeProductId, API_BASE_URL, expectNoHorizontalOverflow, isLocalTarget, WEB_BASE_URL } from "./helpers";

const apiBaseUrl = process.env.E2E_API_BASE_URL ?? (isLocalTarget() ? API_BASE_URL : WEB_BASE_URL);

test("web root response includes security headers", async ({ request }) => {
  const response = await request.get(`${WEB_BASE_URL}/`);
  expect(response.status()).toBe(200);

  const headers = response.headers();
  expect(headers["strict-transport-security"]).toBe("max-age=31536000; includeSubDomains");
  expect(headers["x-content-type-options"]).toBe("nosniff");
  expect(headers["x-frame-options"]).toBe("DENY");
  expect(headers["referrer-policy"]).toBe("strict-origin-when-cross-origin");
  expect(headers["permissions-policy"]).toBe("camera=(), microphone=(), geolocation=()");
  expect(headers["content-security-policy"]).toContain("default-src 'self'");
  expect(headers["content-security-policy"]).toContain("frame-ancestors 'none'");
  expect(headers["x-powered-by"]).toBeUndefined();
});

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
  const productId = await activeProductId();
  const detailResponse = await fetch(`${apiBaseUrl}/api/products/${productId}`);
  expect(detailResponse.ok).toBeTruthy();
  const product = (await detailResponse.json()) as { salesEnabled: boolean };
  const response = await page.goto(`/products/${productId}`);
  expect(response?.status()).toBe(200);

  if (!product.salesEnabled) {
    await expect(page.getByText("판매 준비 중", { exact: true })).toBeVisible();
    await expect(page.locator(".cart-add-form")).toHaveCount(0);
    return;
  }

  const mobileBar = page.locator(".mobile-purchase-bar");
  if (testInfo.project.name === "mobile") {
    await expect(mobileBar.getByRole("button", { name: "장바구니", exact: true })).toBeVisible();
    await expect(mobileBar.getByRole("button", { name: "바로구매", exact: true })).toBeVisible();
  } else {
    const purchaseForm = page.locator(".desktop-purchase-form");
    await expect(purchaseForm.getByRole("button", { name: "장바구니", exact: true })).toBeVisible();
    await expect(purchaseForm.getByRole("button", { name: "바로구매", exact: true })).toBeVisible();
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
