import { expect, test } from "@playwright/test";
import { addCookie, expectNoHorizontalOverflow, requireCustomerCookie } from "./helpers";

test("login page renders social provider entry points", async ({ page }, testInfo) => {
  await page.goto("/login");

  await expect(page.getByRole("heading")).toContainText("안전용품");
  await expect(page.getByText("소셜 로그인").first()).toBeVisible();
  await expect(page.getByText("주문·배송 조회", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "카카오로 계속하기" })).toBeVisible();
  await expect(page.getByRole("link", { name: "구글로 계속하기" })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "네이버로 계속하기" })).toHaveCount(0);
  if (testInfo.project.name === "mobile") {
    await expect(page.locator(".site-header")).toBeHidden();
    await expect(page.locator(".mobile-bottom-nav")).toBeHidden();
  }
  await expectNoHorizontalOverflow(page);
});

test("oauth success pass-through redirects to a safe local path", async ({ page }) => {
  await page.goto("/auth/callback/success?redirectTo=%2Fproducts");

  await expect(page).toHaveURL(/\/products$/);
  await expect(page.getByRole("heading", { name: "안전장비 상품 목록" })).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test("logged-in login page honors redirectTo without provider automation", async ({ page, context }) => {
  const customerCookie = await requireCustomerCookie();

  await addCookie(context, customerCookie);
  await page.goto("/login?redirectTo=%2Faccount");

  await expect(page).toHaveURL(/\/account$/);
  await expect(page.getByRole("heading", { name: "내 계정" })).toBeVisible();
  const phoneNumber = page.getByLabel("배송 연락처");
  if (!(await phoneNumber.isVisible())) {
    await page.locator(".account-collapsible > summary").click();
  }
  await expect(phoneNumber).toBeVisible();
  await expect(page.getByRole("button", { name: "인증번호 받기" })).toHaveCount(0);
  await expectNoHorizontalOverflow(page);
});
