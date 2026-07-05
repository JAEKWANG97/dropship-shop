import { expect, test } from "@playwright/test";
import {
  activeProductId,
  addCookie,
  expectNoHorizontalOverflow,
  requireCustomerCookie,
} from "./helpers";

test("guest product detail shows purchase controls and redirects to login on submit", async ({ page }) => {
  const productId = await activeProductId();
  await page.goto(`/products/${productId}`);
  const purchasePanel = page.locator(".product-purchase-panel");
  const purchaseForm = purchasePanel.locator(".cart-add-form");

  await expect(page.getByLabel("옵션")).toBeVisible();
  await expect(page.getByLabel("수량")).toBeVisible();
  await expect(purchaseForm.getByRole("button", { name: "장바구니", exact: true })).toBeVisible();
  await expect(purchaseForm.getByRole("button", { name: "바로구매", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "로그인하고 계속하기" })).toHaveCount(0);
  await expect(page.locator(".product-hero-copy")).not.toContainText("로그인이 필요합니다");
  await expectNoHorizontalOverflow(page);

  await purchaseForm.getByRole("button", { name: "장바구니", exact: true }).click();
  await page.waitForURL(/\/login\?/);

  const url = new URL(page.url());
  expect(url.pathname).toBe("/login");
  expect(url.searchParams.get("redirectTo")).toBe(`/products/${productId}`);
});

test("mobile purchase bar submits the product form", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "mobile", "Mobile purchase bar is mobile-only.");

  const productId = await activeProductId();
  await page.goto(`/products/${productId}`);

  const mobileBar = page.locator(".mobile-purchase-bar");
  await expect(mobileBar).toBeVisible();
  await expect(mobileBar.getByRole("button", { name: "장바구니 담기" })).toBeVisible();
  await expect(mobileBar.getByRole("button", { name: "바로구매", exact: true })).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await mobileBar.getByRole("button", { name: "장바구니 담기" }).click();
  await page.waitForURL(/\/login\?/);

  const url = new URL(page.url());
  expect(url.pathname).toBe("/login");
  expect(url.searchParams.get("redirectTo")).toBe(`/products/${productId}`);
});

test("customer can add product detail item to cart", async ({ page, context }) => {
  const [productId, customerCookie] = await Promise.all([
    activeProductId(),
    requireCustomerCookie(),
  ]);

  await addCookie(context, customerCookie);
  await page.goto(`/products/${productId}`);
  await page.locator(".cart-add-form").getByRole("button", { name: "장바구니", exact: true }).click();

  await page.waitForURL(/\/cart\?/);
  await expect(page.locator(".notice").first()).toContainText("장바구니에 담았습니다.");
  await expectNoHorizontalOverflow(page);
});
