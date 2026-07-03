import { expect, test } from "@playwright/test";
import { addCookie, expectNoHorizontalOverflow, requireCustomerCookie } from "./helpers";

test("policy detail page renders by slug", async ({ page }) => {
  await page.goto("/policies/shipping");

  await expect(page.getByRole("heading", { name: "배송 정책" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "배송비와 배송 그룹" })).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test("product list empty state is distinct from API failure", async ({ page }) => {
  await page.goto("/products?q=e2e-no-matching-product-keyword");

  await expect(page.getByText("판매중인 상품이 없습니다")).toBeVisible();
  await expect(page.locator("body")).not.toContainText("상품을 불러오지 못했습니다");
  await expectNoHorizontalOverflow(page);
});

test("missing product detail returns a nonblank 404 page", async ({ page }) => {
  const response = await page.goto("/products/00000000-0000-0000-0000-000000000000");

  expect(response?.status()).toBe(404);
  await expect(page.locator("body")).toContainText(/404|This page could not be found|찾을 수 없습니다/);
  await expectNoHorizontalOverflow(page);
});

test("missing customer order detail returns a nonblank 404 page", async ({ page, context }) => {
  const customerCookie = await requireCustomerCookie();

  await addCookie(context, customerCookie);
  const response = await page.goto("/orders/00000000-0000-0000-0000-000000000000");

  expect(response?.status()).toBe(404);
  await expect(page.locator("body")).toContainText(/404|This page could not be found|찾을 수 없습니다/);
  await expectNoHorizontalOverflow(page);
});

test("unauthenticated admin route shows login-required state", async ({ page }) => {
  await page.goto("/admin");

  await expect(page.getByRole("heading", { name: "관리자 로그인이 필요합니다" })).toBeVisible();
  await expect(page.getByRole("main").getByRole("link", { name: "로그인" })).toBeVisible();
  await expect(page.locator("body")).not.toContainText("권한, API 서버, 네트워크 상태");
  await expectNoHorizontalOverflow(page);
});
