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

  await expect(page.getByLabel("옵션")).toBeVisible();
  await expect(page.getByLabel("수량")).toBeVisible();
  await expect(page.getByRole("button", { name: "장바구니" })).toBeVisible();
  await expect(page.getByRole("button", { name: "바로구매" })).toBeVisible();
  await expect(page.getByRole("link", { name: "로그인하고 계속하기" })).toHaveCount(0);
  await expect(page.locator(".product-hero-copy")).not.toContainText("로그인이 필요합니다");
  await expectNoHorizontalOverflow(page);

  await page.getByRole("button", { name: "장바구니" }).click();
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
  await page.getByRole("button", { name: "장바구니" }).click();

  await page.waitForURL(/\/cart\?/);
  await expect(page.locator(".notice").first()).toContainText("장바구니에 담았습니다.");
  await expectNoHorizontalOverflow(page);
});
