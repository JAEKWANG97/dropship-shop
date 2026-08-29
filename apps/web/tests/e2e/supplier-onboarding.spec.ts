import { expect, test } from "@playwright/test";
import { expectNoHorizontalOverflow } from "./helpers";

test("supplier activation removes the fragment before showing Kakao connection", async ({ page }) => {
  let submittedToken = "";
  await page.route("**/api/supplier-invites/session", async (route) => {
    const body = route.request().postDataJSON() as { token?: unknown };
    submittedToken = typeof body.token === "string" ? body.token : "";
    await route.fulfill({ status: 204 });
  });

  await page.goto("/supplier/activate#token=e2e-invite-token");

  await expect.poll(() => submittedToken).toBe("e2e-invite-token");
  await expect(page).toHaveURL(/\/supplier\/activate$/);
  await expect(page.getByRole("heading", { name: "초대 링크를 확인했습니다" })).toBeVisible();
  await expect(page.getByRole("link", { name: "카카오로 연결" })).toHaveAttribute(
    "href",
    "/api/supplier/auth/kakao/authorize",
  );
  await expect(page.getByText("구글", { exact: false })).toHaveCount(0);
  await expect(page.getByText("네이버", { exact: false })).toHaveCount(0);
  await expectNoHorizontalOverflow(page);
});

test("supplier activation without a token exposes only a safe recovery message", async ({ page }) => {
  await page.goto("/supplier/activate");

  await expect(page.getByRole("heading", { name: "초대 링크를 다시 확인해 주세요" })).toBeVisible();
  await expect(page.getByText("새 초대를 요청해 주세요", { exact: false })).toBeVisible();
  await expect(page.locator("body")).not.toContainText("supplierId");
  await expectNoHorizontalOverflow(page);
});

test("temporary Kakao failure retries with the retained invite context", async ({ page }) => {
  let authorizationRetried = false;
  await page.route("**/api/supplier/auth/kakao/authorize", async (route) => {
    authorizationRetried = true;
    await route.fulfill({ status: 204 });
  });

  await page.goto("/supplier/activate?error=OAUTH_TEMPORARY_FAILURE");

  await expect(page).toHaveURL(/\/supplier\/activate$/);
  await expect(page.getByRole("heading", { name: "지금은 연결할 수 없습니다" })).toBeVisible();
  await page.getByRole("link", { name: "카카오 연결 다시 시도" }).click();
  await expect.poll(() => authorizationRetried).toBe(true);
});
