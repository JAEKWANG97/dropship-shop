import { expect, test, type BrowserContext, type Page } from "@playwright/test";

const API_BASE_URL = process.env.E2E_API_BASE_URL ?? "http://localhost:8080";
const WEB_BASE_URL = process.env.E2E_WEB_BASE_URL ?? "http://localhost:3000";
const REQUIRE_ADMIN_SEED_ORDERS_VALUE =
  process.env.E2E_REQUIRE_ADMIN_SEED_ORDERS ??
  (API_BASE_URL.includes("localhost") || API_BASE_URL.includes("127.0.0.1") ? "true" : "false");
const REQUIRE_ADMIN_SEED_ORDERS = ["1", "true", "yes"].includes(REQUIRE_ADMIN_SEED_ORDERS_VALUE.toLowerCase());

type ProductSummary = {
  id: string;
  name: string;
};

async function activeProductId() {
  const response = await fetch(`${API_BASE_URL}/api/products`);
  expect(response.ok).toBeTruthy();
  const products = (await response.json()) as ProductSummary[];
  expect(products.length).toBeGreaterThan(0);
  return products[0].id;
}

async function addCookie(context: BrowserContext, cookieHeader: string) {
  const host = new URL(WEB_BASE_URL).hostname;
  const accessToken = cookieHeader
    .split(";")
    .map((item) => item.trim())
    .find((item) => item.startsWith("ACCESS_TOKEN="))
    ?.slice("ACCESS_TOKEN=".length);

  expect(accessToken, "ACCESS_TOKEN cookie is required").toBeTruthy();
  await context.addCookies([
    {
      name: "ACCESS_TOKEN",
      value: accessToken!,
      domain: host,
      path: "/",
      httpOnly: true,
      sameSite: "Lax",
    },
  ]);
}

async function expectNoHorizontalOverflow(page: Page) {
  const result = await page.evaluate(() => {
    const offenders = [...document.querySelectorAll("body *")]
      .filter((element) => {
        const rect = element.getBoundingClientRect();
        return rect.width > 0 && rect.height > 0 && (rect.left < -1 || rect.right > innerWidth + 1);
      })
      .slice(0, 5)
      .map((element) => ({
        tag: element.tagName,
        className: String(element.className),
        text: element.textContent?.trim().slice(0, 80),
      }));

    return {
      hasOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
      clientWidth: document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
      offenders,
    };
  });

  expect(result, JSON.stringify(result, null, 2)).toMatchObject({ hasOverflow: false });
}

async function firstAdminOrderLink(page: Page) {
  const orderLink = page.locator("a[href^='/admin/orders?orderId=']").first();
  if (REQUIRE_ADMIN_SEED_ORDERS) {
    await expect(orderLink, "Local/dev admin order seed data is required").toHaveCount(1);
  } else {
    test.skip((await orderLink.count()) === 0, "No admin order exists for this smoke target.");
  }
  return orderLink;
}

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
  test.skip(testInfo.project.name !== "mobile", "Screenshots are mobile-only.");

  await page.goto("/");
  await expectNoHorizontalOverflow(page);
  await expect(page).toHaveScreenshot("mobile-home.png", { fullPage: true });

  await page.goto("/products");
  await expectNoHorizontalOverflow(page);
  await expect(page).toHaveScreenshot("mobile-products.png", { fullPage: true });
});

test("mobile admin smoke screenshots remain stable", async ({ page, context }, testInfo) => {
  test.skip(testInfo.project.name !== "mobile", "Screenshots are mobile-only.");
  test.skip(!process.env.E2E_ADMIN_COOKIE, "Set E2E_ADMIN_COOKIE to run admin screenshot smoke.");

  await addCookie(context, process.env.E2E_ADMIN_COOKIE!);
  await page.goto("/admin/products");
  await expectNoHorizontalOverflow(page);
  await expect(page).toHaveScreenshot("mobile-admin-products.png", { fullPage: true });

  await page.goto("/admin/orders");
  const orderLink = await firstAdminOrderLink(page);
  await orderLink.click();
  await expect(page.locator("text=주문 상세").first()).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await expect(page).toHaveScreenshot("mobile-admin-order-detail.png", { fullPage: true });
});
