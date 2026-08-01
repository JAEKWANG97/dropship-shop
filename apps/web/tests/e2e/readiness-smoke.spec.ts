import { expect, test } from "@playwright/test";
import {
  API_BASE_URL,
  activeProductId,
  addCookie,
  expectNoHorizontalOverflow,
  firstAdminOrderLink,
  isLocalTarget,
  requireAdminCookie,
  requireCustomerCookie,
} from "./helpers";

type AdminProductPage = {
  products: {
    id: string;
    name: string;
    status: string;
    categoryCode: string;
    supplierId: string;
    saleReady: boolean;
  }[];
  totalElements: number;
};

type AdminProductDetail = {
  id: string;
  supplierId: string;
  name: string;
  summary: string;
  sourcePrice: number;
  sourceUrl: string | null;
  basePrice: number;
  categoryCode: string;
  complianceStatus: string;
  status: string;
  saleReady: boolean;
  saleBlockers: string[];
};

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

test("admin product list preserves server filters and pagination", async ({ page, context }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "Admin product filtering runs once in the desktop project.");
  test.skip(!isLocalTarget(), "Admin product filtering uses local dev login and seed data.");

  const adminCookie = await requireAdminCookie();
  const response = await fetch(`${API_BASE_URL}/api/admin/products?size=1`, {
    headers: { Cookie: adminCookie },
  });
  if (!response.ok) {
    expect(response.ok, await response.text()).toBeTruthy();
  }
  const productPage = (await response.json()) as AdminProductPage;
  test.skip(productPage.products.length === 0, "No admin product exists for this smoke target.");
  const product = productPage.products[0];

  await addCookie(context, adminCookie);
  await page.goto("/admin/products");
  const filterForm = page.locator("form.admin-product-filters");
  await filterForm.getByPlaceholder("상품명, 공급처 검색").fill(product.name);
  await filterForm.locator('select[name="status"]').selectOption(product.status);
  await filterForm.locator('select[name="category"]').selectOption(product.categoryCode);
  await filterForm.locator('select[name="supplierId"]').selectOption(product.supplierId);
  await filterForm.locator('select[name="readiness"]').selectOption(product.saleReady ? "READY" : "BLOCKED");
  await filterForm.getByRole("button", { name: "검색" }).click();

  await expect.poll(() => new URL(page.url()).searchParams.get("q")).toBe(product.name);
  await expect(page.locator(".admin-table.products")).toContainText(product.name);
  await expectNoHorizontalOverflow(page);

  await page.getByRole("link", { name: "초기화" }).click();
  if (productPage.totalElements > 20) {
    await page.getByRole("link", { name: "다음" }).click();
    await expect.poll(() => new URL(page.url()).searchParams.get("page")).toBe("2");
  }
});

test("admin reviews a ready hidden product and activates it individually", async ({ page, context }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "Product review mutation runs once in the desktop project.");
  test.skip(!isLocalTarget(), "Product review mutation uses local seed data.");

  const adminCookie = await requireAdminCookie();
  const seedName = "보안경 김서림 방지형";
  const listResponse = await fetch(
    `${API_BASE_URL}/api/admin/products?q=${encodeURIComponent(seedName)}&status=HIDDEN&readiness=READY&size=10`,
    { headers: { Cookie: adminCookie } },
  );
  if (!listResponse.ok) expect(listResponse.ok, await listResponse.text()).toBeTruthy();
  const productPage = (await listResponse.json()) as AdminProductPage;
  const product = productPage.products.find((item) => item.name === seedName);
  expect(product, `Local ready HIDDEN seed product '${seedName}' is required`).toBeTruthy();

  const sourceUrl = `https://example.com/e2e-product-source-${Date.now()}`;
  await addCookie(context, adminCookie);

  try {
    await page.goto(`/admin/products/${product!.id}`);
    const readinessPanel = page.locator(".admin-readiness-panel");
    await expect(readinessPanel).toContainText("준비 완료");
    await expect(readinessPanel.locator("a.missing")).toHaveCount(0);

    const pricingForm = page.locator("#product-pricing form");
    await pricingForm.getByLabel("공급처 원본 URL").fill(sourceUrl);
    await pricingForm.getByLabel("변경 사유").fill("E2E 원본 추적 검증");
    await pricingForm.getByRole("button", { name: "입력 정보 저장" }).click();

    const sourceLink = page.getByRole("link", { name: "원본 보기" }).first();
    await expect(sourceLink).toHaveAttribute("href", sourceUrl);
    await expect(sourceLink).toHaveAttribute("target", "_blank");
    await expect(sourceLink).toHaveAttribute("rel", /noopener/);
    await expect(sourceLink).toHaveAttribute("rel", /noreferrer/);

    const statusForm = page.locator("form").filter({
      has: page.getByRole("button", { name: "상품 상태 변경" }),
    });
    await statusForm.getByLabel("판매 상태").selectOption("ACTIVE");
    await statusForm.getByLabel("변경 사유").fill("E2E 판매 준비 완료 검증");
    await statusForm.getByRole("button", { name: "상품 상태 변경" }).click();

    await expect(page.locator(".notice").first()).toContainText("상품 판매 상태를 변경했습니다.");
    await expect(statusForm.getByLabel("판매 상태")).toHaveValue("ACTIVE");
    await expectNoHorizontalOverflow(page);
  } finally {
    const detailResponse = await fetch(`${API_BASE_URL}/api/admin/products/${product!.id}`, {
      headers: { Cookie: adminCookie },
    });
    if (!detailResponse.ok) expect(detailResponse.ok, await detailResponse.text()).toBeTruthy();
    const detail = (await detailResponse.json()) as AdminProductDetail;

    const resetProductResponse = await fetch(`${API_BASE_URL}/api/admin/products/${product!.id}`, {
      method: "PATCH",
      headers: { Cookie: adminCookie, "Content-Type": "application/json" },
      body: JSON.stringify({
        supplierId: detail.supplierId,
        name: detail.name,
        summary: detail.summary,
        sourcePrice: detail.sourcePrice,
        sourceUrl: null,
        basePrice: detail.basePrice,
        categoryCode: detail.categoryCode,
        complianceStatus: detail.complianceStatus,
        reason: "E2E 원본 URL 복구",
      }),
    });
    if (!resetProductResponse.ok) {
      expect(resetProductResponse.ok, await resetProductResponse.text()).toBeTruthy();
    }

    const resetStatusResponse = await fetch(`${API_BASE_URL}/api/admin/products/${product!.id}/status`, {
      method: "PATCH",
      headers: { Cookie: adminCookie, "Content-Type": "application/json" },
      body: JSON.stringify({ status: "HIDDEN", reason: "E2E 상품 상태 복구" }),
    });
    if (!resetStatusResponse.ok) {
      expect(resetStatusResponse.ok, await resetStatusResponse.text()).toBeTruthy();
    }
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

  await expect(page.getByLabel("실제 입금자명")).toBeVisible();
  await expect(page.getByLabel("실제 입금액")).toBeVisible();
  await expect(page.getByLabel("입금시각")).toBeVisible();
  await expect(page.getByLabel("거래 식별 메모").first()).toBeVisible();

  const memo = `E2E 입금 불일치 메모 ${Date.now()}`;
  await page.getByLabel("입금 불일치 메모").fill(memo);
  await page.getByRole("button", { name: "메모 저장" }).click();

  await expect(page.locator(".notice").first()).toContainText("입금 불일치 메모를 저장했습니다.");
  await expect(page.locator("body")).toContainText(memo);
  await expectNoHorizontalOverflow(page);
});

test("admin order detail hides actions that do not match the current state", async ({ page, context }) => {
  test.skip(!process.env.E2E_ADMIN_COOKIE, "Set E2E_ADMIN_COOKIE to run admin order action smoke.");

  await addCookie(context, process.env.E2E_ADMIN_COOKIE!);
  await page.goto("/admin/orders?status=SUPPLIER_ORDER_PENDING");
  const orderLink = await firstAdminOrderLink(page);
  await orderLink.click();

  await expect(page.getByRole("button", { name: "발주 시작" })).toBeVisible();
  await expect(page.getByRole("button", { name: "발주 완료" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "송장 입력" })).toHaveCount(0);
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

test("mobile customer product layouts keep compact shopping density", async ({ page }, testInfo) => {
  test.skip(!isLocalTarget(), "Layout measurements use local seed data; skip on deployed targets.");
  test.skip(testInfo.project.name !== "mobile", "Layout measurements are mobile-only.");

  await page.goto("/");
  const featuredShelf = page.locator(".home-products .product-grid.featured");
  const featuredCards = featuredShelf.locator(".product-card");
  await expect(featuredShelf).toBeVisible();
  expect(await featuredCards.count()).toBeGreaterThan(2);
  expect(await featuredShelf.evaluate((element) => element.scrollWidth > element.clientWidth)).toBe(true);

  const featuredCardBox = await featuredCards.first().boundingBox();
  const featuredImageBox = await featuredCards.first().locator(".product-card-image").boundingBox();
  expect(featuredCardBox?.width).toBeGreaterThanOrEqual(148);
  expect(featuredCardBox?.width).toBeLessThanOrEqual(152);
  expect(featuredImageBox?.width).toBe(150);
  expect(featuredImageBox?.height).toBe(150);

  await page.goto("/products");
  const catalogCard = page.locator(".catalog-results .product-card").first();
  const catalogImage = catalogCard.locator(".product-card-image");
  const filterSummary = page.locator(".catalog-mobile-filters summary");
  await expect(catalogCard).toBeVisible();

  const [catalogImageBox, filterBox, priceFontSize, nameFontWeight] = await Promise.all([
    catalogImage.boundingBox(),
    filterSummary.boundingBox(),
    catalogCard.locator(".product-card-price").evaluate((element) => getComputedStyle(element).fontSize),
    catalogCard.locator(".product-card-name").evaluate((element) => getComputedStyle(element).fontWeight),
  ]);
  expect(catalogImageBox?.width).toBe(120);
  expect(catalogImageBox?.height).toBe(120);
  expect(filterBox?.height).toBeGreaterThanOrEqual(44);
  expect(priceFontSize).toBe("20px");
  expect(nameFontWeight).toBe("500");
  await expectNoHorizontalOverflow(page);
});

test("mobile account and orders keep compact customer hierarchy", async ({ page, context }, testInfo) => {
  test.skip(!isLocalTarget(), "Layout measurements use local seed data; skip on deployed targets.");
  test.skip(testInfo.project.name !== "mobile", "Layout measurements are mobile-only.");

  await addCookie(context, await requireCustomerCookie());
  await page.goto("/account");

  const profileSummary = page.locator(".account-collapsible > summary");
  const newAddress = page.locator(".account-new-address");
  await expect(profileSummary).toBeVisible();
  await expect(newAddress).not.toHaveAttribute("open", "");
  expect((await profileSummary.boundingBox())?.height).toBeGreaterThanOrEqual(44);
  await expectNoHorizontalOverflow(page);

  await page.goto("/orders");
  const orderCard = page.locator(".order-card").first();
  await expect(orderCard).toBeVisible();
  expect((await orderCard.boundingBox())?.height).toBeLessThanOrEqual(150);
  await expectNoHorizontalOverflow(page);
});

test("mobile admin product screenshot remains stable", async ({ page, context }, testInfo) => {
  test.skip(!isLocalTarget(), "Screenshot baselines use local seed data; skip on deployed targets.");
  test.skip(testInfo.project.name !== "mobile", "Screenshots are mobile-only.");

  await addCookie(context, await requireAdminCookie());
  await page.goto("/admin/products");
  await expectNoHorizontalOverflow(page);
  await expect(page).toHaveScreenshot("mobile-admin-products.png", {
    mask: [page.locator(".admin-table.products")],
  });
});

test("mobile admin order detail screenshot remains stable", async ({ page, context }, testInfo) => {
  test.skip(!isLocalTarget(), "Screenshot baselines use local seed data; skip on deployed targets.");
  test.skip(testInfo.project.name !== "mobile", "Screenshots are mobile-only.");

  await addCookie(context, await requireAdminCookie());
  await page.goto("/admin/orders");
  const orderLink = await firstAdminOrderLink(page);
  await orderLink.click();
  await expect(page.locator("text=주문 상세").first()).toBeVisible();
  await expectNoHorizontalOverflow(page);
  const summaryValue = (label: string) => page.getByText(label, { exact: true }).locator("..").locator("strong");
  await expect(page).toHaveScreenshot("mobile-admin-order-detail.png", {
    mask: [
      page.locator(".admin-order-detail > span").first(),
      summaryValue("입금확인"),
      summaryValue("미입금 취소"),
      summaryValue("출고시각"),
      summaryValue("배송완료시각"),
      summaryValue("마지막 조회"),
    ],
  });
});
