import { expect, test, type BrowserContext, type Page } from "@playwright/test";

const API_BASE_URL = process.env.E2E_API_BASE_URL ?? "http://localhost:8080";
const WEB_BASE_URL = process.env.E2E_WEB_BASE_URL ?? "http://localhost:3000";

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
  const orderLink = page.locator("a[href^='/admin/orders?orderId=']").first();
  test.skip((await orderLink.count()) === 0, "No admin order exists for order detail smoke.");

  await orderLink.click();
  await expect(page.locator("text=주문 상세").first()).toBeVisible();
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
  const orderLink = page.locator("a[href^='/admin/orders?orderId=']").first();
  test.skip((await orderLink.count()) === 0, "No admin order exists for order detail screenshot.");
  await orderLink.click();
  await expect(page.locator("text=주문 상세").first()).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await expect(page).toHaveScreenshot("mobile-admin-order-detail.png", { fullPage: true });
});
