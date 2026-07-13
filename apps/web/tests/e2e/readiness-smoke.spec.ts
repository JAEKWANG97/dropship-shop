import { expect, test } from "@playwright/test";
import {
  activeProductId,
  addCookie,
  expectNoHorizontalOverflow,
  firstAdminOrderLink,
  isLocalTarget,
  requireAdminCookie,
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

test("customer inquiry flows from public receipt through admin answer to protected lookup", async ({ page, context }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "Inquiry mutation runs once in the desktop project.");
  test.skip(!isLocalTarget(), "Inquiry mutation uses local dev login and disabled SES.");

  const adminCookie = await requireAdminCookie();
  const nonce = Date.now();
  const email = `e2e-inquiry-${nonce}@example.com`;
  const subject = `E2E 문의 ${nonce}`;
  const adminMemo = `E2E 관리자 메모 ${nonce}`;
  const answer = `E2E 답변 ${nonce}`;

  await page.goto("/support");
  await expect(page.getByText("접수일로부터 3년간 보관")).toBeVisible();
  await page.getByLabel("이름", { exact: true }).fill("E2E 고객");
  await page.getByLabel("이메일", { exact: true }).fill(email);
  await page.getByLabel("제목", { exact: true }).fill(subject);
  await page.getByLabel("문의 내용", { exact: true }).fill("문의 접수와 조회 링크를 확인합니다.");
  await page.getByRole("checkbox", { name: /개인정보 수집·이용/ }).check();
  await page.getByRole("button", { name: "문의 접수" }).click();

  await expect(page).toHaveURL(/\/support\/inquiries\/[0-9a-f-]+#token=/);
  await expect(page.getByText(subject)).toBeVisible();
  await expect(page.getByText("접수", { exact: true })).toBeVisible();
  await expectNoHorizontalOverflow(page);

  const lookupUrl = page.url();
  const inquiryId = new URL(lookupUrl).pathname.split("/").at(-1)!;
  await addCookie(context, adminCookie);
  await page.goto("/admin/inquiries?status=RECEIVED");

  const inquiryLink = page.locator(`a[href="/admin/inquiries/${inquiryId}"]`);
  await expect(inquiryLink).toContainText(subject);
  await inquiryLink.click();

  const statusForm = page.locator("form").filter({ has: page.getByRole("button", { name: "상태 저장" }) });
  await statusForm.getByLabel("상태").selectOption("IN_PROGRESS");
  await statusForm.getByLabel("관리자 메모").fill(adminMemo);
  await statusForm.getByRole("button", { name: "상태 저장" }).click();

  await expect(page.locator(".notice").first()).toContainText("문의 상태를 변경했습니다.");
  await expect(page.locator(".inquiry-status")).toHaveText("처리 중");
  await expect(statusForm.getByLabel("관리자 메모")).toHaveValue(adminMemo);

  const answerForm = page.locator("form").filter({
    has: page.getByRole("button", { name: "답변 저장 및 이메일 발송" }),
  });
  await answerForm.getByLabel("답변 내용").fill(answer);
  await answerForm.getByRole("button", { name: "답변 저장 및 이메일 발송" }).click();

  await expect(page.locator(".notice").first()).toContainText("답변을 저장했습니다.");
  await expect(page.locator(".inquiry-status")).toHaveText("답변 완료");
  const emailPanel = page.locator("section.admin-panel").filter({
    has: page.getByRole("heading", { name: "답변 이메일" }),
  });
  await expect(emailPanel).toContainText("SKIPPED");
  await expect(emailPanel).toContainText("AWS SES is disabled");
  await emailPanel.getByRole("button", { name: "이메일 재시도" }).click();

  await expect(page.locator(".notice").first()).toContainText(
    "답변 이메일 재시도를 요청했습니다. 발송 상태를 확인하세요.",
  );
  await expect(emailPanel).toContainText("SKIPPED");
  await expect(emailPanel).toContainText("AWS SES is disabled");

  await context.clearCookies();
  await page.goto(lookupUrl);
  await expect(page.locator(".inquiry-lookup-head span")).toHaveText("답변 완료");
  await expect(page.getByText(answer)).toBeVisible();
  await expect(page.locator("body")).not.toContainText(email);
  await expect(page.locator("body")).not.toContainText(adminMemo);
  await expect(page.locator("body")).not.toContainText("support-inquiry-privacy-2026-07-13");
  await expectNoHorizontalOverflow(page);
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

  await page.goto("/admin/inquiries");
  const firstInquiry = page.locator(".admin-inquiry-card").first();
  if (await firstInquiry.count()) {
    await firstInquiry.click();
    await expect(page.getByRole("heading", { name: "문의 내용" })).toBeVisible();
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
