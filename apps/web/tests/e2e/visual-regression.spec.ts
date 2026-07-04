import { expect, test } from "@playwright/test";
import {
  activeProductId,
  addCookie,
  expectNoHorizontalOverflow,
  firstAdminOrderLink,
  requireCustomerCookie,
  requireSeedOrderByStatus,
} from "./helpers";

test("desktop home screenshot remains stable", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "Desktop baselines run only in the desktop project.");

  await page.goto("/");

  await expectNoHorizontalOverflow(page);
  await expect(page).toHaveScreenshot("desktop-home.png", { fullPage: true });
});

test("desktop product detail screenshot remains stable", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "Desktop baselines run only in the desktop project.");

  const productId = await activeProductId();
  await page.goto(`/products/${productId}`);

  await expectNoHorizontalOverflow(page);
  await expect(page).toHaveScreenshot("desktop-product-detail.png", { fullPage: true });
});

test("desktop checkout bank-transfer screenshot remains stable", async ({ page, context }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "Desktop baselines run only in the desktop project.");
  const [customerCookie, paymentPendingOrder] = await Promise.all([
    requireCustomerCookie(),
    requireSeedOrderByStatus("PAYMENT_PENDING"),
  ]);

  await addCookie(context, customerCookie);
  await page.goto(`/checkout/${paymentPendingOrder.checkoutNumber}`);

  await expect(page.getByRole("heading", { name: "계좌입금 안내" })).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await expect(page).toHaveScreenshot("desktop-checkout-bank-transfer.png", {
    fullPage: true,
    mask: [
      page.locator(".summary-list div").filter({ hasText: "입금 기한" }),
      page.locator(".summary-list div").filter({ hasText: "정책 확인" }),
    ],
  });
});

test("desktop admin order detail screenshot remains stable", async ({ page, context }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "Desktop baselines run only in the desktop project.");
  test.skip(!process.env.E2E_ADMIN_COOKIE, "Set E2E_ADMIN_COOKIE to run admin screenshot smoke.");

  await addCookie(context, process.env.E2E_ADMIN_COOKIE!);
  await page.goto("/admin/orders");
  const orderLink = await firstAdminOrderLink(page);
  await orderLink.click();

  await expect(page.locator("text=주문 상세").first()).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await expect(page).toHaveScreenshot("desktop-admin-order-detail.png", {
    fullPage: true,
    mask: [
      page.locator(".admin-order-detail > span").first(),
      page.locator(".admin-order-detail .summary-list div").filter({ hasText: "입금확인" }),
      page.locator(".admin-order-detail .summary-list div").filter({ hasText: "미입금 취소" }),
      page.locator(".admin-order-detail .summary-list div").filter({ hasText: "출고시각" }),
      page.locator(".admin-order-detail .summary-list div").filter({ hasText: "배송완료시각" }),
      page.locator(".admin-order-detail .summary-list div").filter({ hasText: "마지막 조회" }),
    ],
  });
});

test("mobile home screenshot remains stable", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "mobile", "Mobile baselines run only in the mobile project.");

  await page.goto("/");

  await expectNoHorizontalOverflow(page);
  await expect(page).toHaveScreenshot("mobile-home.png", { fullPage: true });
});

test("mobile product detail screenshot remains stable", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "mobile", "Mobile baselines run only in the mobile project.");

  const productId = await activeProductId();
  await page.goto(`/products/${productId}`);

  await expectNoHorizontalOverflow(page);
  await expect(page).toHaveScreenshot("mobile-product-detail.png", { fullPage: true });
});

test("mobile checkout bank-transfer screenshot remains stable", async ({ page, context }, testInfo) => {
  test.skip(testInfo.project.name !== "mobile", "Mobile baselines run only in the mobile project.");
  const [customerCookie, paymentPendingOrder] = await Promise.all([
    requireCustomerCookie(),
    requireSeedOrderByStatus("PAYMENT_PENDING"),
  ]);

  await addCookie(context, customerCookie);
  await page.goto(`/checkout/${paymentPendingOrder.checkoutNumber}`);

  await expect(page.getByRole("heading", { name: "계좌입금 안내" })).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await expect(page).toHaveScreenshot("mobile-checkout-bank-transfer.png", {
    fullPage: true,
    mask: [
      page.locator(".summary-list div").filter({ hasText: "입금 기한" }),
      page.locator(".summary-list div").filter({ hasText: "정책 확인" }),
    ],
  });
});
