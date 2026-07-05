import { expect, test } from "@playwright/test";
import {
  activeProductId,
  addCookie,
  expectNoHorizontalOverflow,
  firstAdminOrderLink,
  isLocalTarget,
} from "./helpers";

test("public customer pages render without horizontal overflow", async ({ page }) => {
  const productId = await activeProductId();
  const routes = [
    "/",
    "/products",
    "/products?category=PPE_SAFETY_HELMET",
    `/products/${productId}`,
    "/policies",
    "/company",
    "/support",
  ];

  for (const route of routes) {
    await page.goto(route);
    await expect(page.locator("h1").first()).toBeVisible();
    await expectNoHorizontalOverflow(page);
  }
});

test("customer account pages render with a session cookie", async ({ page, context }) => {
  test.skip(!process.env.E2E_CUSTOMER_COOKIE, "Set E2E_CUSTOMER_COOKIE to run customer auth smoke.");

  await addCookie(context, process.env.E2E_CUSTOMER_COOKIE!);
  for (const route of ["/cart", "/checkout", "/account", "/orders"]) {
    await page.goto(route);
    await expect(page.locator("body")).not.toContainText("API 서버");
    await expect(page.locator("h1").first()).toBeVisible();
    await expectNoHorizontalOverflow(page);
  }
});

test("admin pages render with an admin session cookie", async ({ page, context }) => {
  test.skip(!process.env.E2E_ADMIN_COOKIE, "Set E2E_ADMIN_COOKIE to run admin smoke.");

  await addCookie(context, process.env.E2E_ADMIN_COOKIE!);
  const productId = await activeProductId();
  const routes = [
    "/admin",
    "/admin/products",
    `/admin/products/${productId}`,
    "/admin/products/new",
    "/admin/pricing",
    "/admin/orders",
    "/admin/inquiries",
  ];

  for (const route of routes) {
    await page.goto(route);
    await expect(page.locator("h1").first()).toBeVisible();
    await expectNoHorizontalOverflow(page);
  }
});

test("admin order detail renders through selected order query", async ({ page, context }) => {
  test.skip(!process.env.E2E_ADMIN_COOKIE, "Set E2E_ADMIN_COOKIE to run admin order smoke.");

  await addCookie(context, process.env.E2E_ADMIN_COOKIE!);
  await page.goto("/admin/orders");
  const orderLink = await firstAdminOrderLink(page);

  await orderLink.click();
  await expect(page.locator("text=주문 상세").first()).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test("admin order action refreshes detail after successful memo update", async ({ page, context }) => {
  test.skip(!process.env.E2E_ADMIN_COOKIE, "Set E2E_ADMIN_COOKIE to run admin order action smoke.");

  await addCookie(context, process.env.E2E_ADMIN_COOKIE!);
  await page.goto("/admin/orders?status=PAYMENT_PENDING");
  const orderLink = await firstAdminOrderLink(page);
  await orderLink.click();

  const memo = `E2E 입금 불일치 메모 ${Date.now()}`;
  await page.getByLabel("입금 불일치 메모").fill(memo);
  await page.getByRole("button", { name: "메모 저장" }).click();

  await expect(page.locator(".notice").first()).toContainText("입금 불일치 메모를 저장했습니다.");
  await expect(page.locator("body")).toContainText(memo);
  await expectNoHorizontalOverflow(page);
});

test("admin order action failure shows backend reason", async ({ page, context }) => {
  test.skip(!process.env.E2E_ADMIN_COOKIE, "Set E2E_ADMIN_COOKIE to run admin order action smoke.");

  await addCookie(context, process.env.E2E_ADMIN_COOKIE!);
  await page.goto("/admin/orders?status=DELIVERED");
  const orderLink = await firstAdminOrderLink(page);
  await orderLink.click();

  await page.getByLabel("발주 시작 사유").fill("E2E 실패 메시지 확인");
  await page.getByRole("button", { name: "발주 시작" }).click();

  await expect(page.locator(".notice").first()).toContainText(
    "Supplier order work can start only once from supplier order pending",
  );
  await expectNoHorizontalOverflow(page);
});

test("mobile public smoke screenshots remain stable", async ({ page }, testInfo) => {
  test.skip(!isLocalTarget(), "Screenshot baselines use local seed data; skip on deployed targets.");
  test.skip(testInfo.project.name !== "mobile", "Screenshots are mobile-only.");

  await page.goto("/");
  await expectNoHorizontalOverflow(page);
  await expect(page).toHaveScreenshot("mobile-home.png");

  await page.goto("/products");
  await expectNoHorizontalOverflow(page);
  await expect(page).toHaveScreenshot("mobile-products.png", {
    mask: [
      page.locator(".catalog-heading > span"),
      page.locator(".catalog-tools > span"),
      page.locator(".product-grid"),
    ],
  });
});

test("mobile admin smoke screenshots remain stable", async ({ page, context }, testInfo) => {
  test.skip(!isLocalTarget(), "Screenshot baselines use local seed data; skip on deployed targets.");
  test.skip(testInfo.project.name !== "mobile", "Screenshots are mobile-only.");
  test.skip(!process.env.E2E_ADMIN_COOKIE, "Set E2E_ADMIN_COOKIE to run admin screenshot smoke.");

  await addCookie(context, process.env.E2E_ADMIN_COOKIE!);
  await page.goto("/admin/products");
  await expectNoHorizontalOverflow(page);
  await expect(page).toHaveScreenshot("mobile-admin-products.png", {
    mask: [page.locator(".admin-table.products")],
  });

  await page.goto("/admin/orders");
  const orderLink = await firstAdminOrderLink(page);
  await orderLink.click();
  await expect(page.locator("text=주문 상세").first()).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await expect(page).toHaveScreenshot("mobile-admin-order-detail.png", {
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
