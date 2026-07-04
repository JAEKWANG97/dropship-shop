import { expect, test } from "@playwright/test";
import { addCookie, expectNoHorizontalOverflow, requireCustomerCookie } from "./helpers";

test("oauth success onboarding flag routes to welcome", async ({ page, context }) => {
  const customerCookie = await requireCustomerCookie();
  await addCookie(context, customerCookie);

  await page.goto("/auth/callback/success?onboarding=1&redirectTo=%2Faccount");

  await expect(page).toHaveURL(/\/welcome/);
});

test("guest welcome redirects to login", async ({ page }) => {
  await page.goto("/welcome?redirectTo=%2Faccount");

  await expect(page).toHaveURL(/\/login/);
});

test("customer can skip referral onboarding", async ({ page, context }) => {
  const customerCookie = await requireCustomerCookie();
  await addCookie(context, customerCookie);

  await page.goto("/welcome?redirectTo=%2Faccount");
  await expect(page.getByRole("heading", { name: "추천인 코드" })).toBeVisible();

  const input = page.getByLabel("추천인 코드");
  if ((await input.count()) > 0) {
    await expect(input).toBeVisible();
  } else {
    await expect(page.locator(".notice").filter({ hasText: "이미 등록되어 있습니다" })).toBeVisible();
  }

  await page.getByRole("button", { name: "건너뛰기" }).click();
  await expect(page).toHaveURL(/\/account$/);
  await expectNoHorizontalOverflow(page);
});

test("account shows my referral code", async ({ page, context }) => {
  const customerCookie = await requireCustomerCookie();
  await addCookie(context, customerCookie);

  await page.goto("/account");

  await expect(page.getByRole("heading", { name: "내 계정" })).toBeVisible();
  await expect(page.getByText("내 추천 코드")).toBeVisible();
  await expectNoHorizontalOverflow(page);
});
