import { expect, test, type Page } from "@playwright/test";
import {
  API_BASE_URL,
  activeProductId,
  addCookie,
  type CustomerOrderSummary,
  expectNoHorizontalOverflow,
  requireAdminCookie,
  requireCustomerCookie,
  requireSeedOrderByStatus,
} from "./helpers";

function collectCspViolations(page: Page) {
  const violations: string[] = [];
  page.on("console", (message) => {
    const text = message.text();
    if (text.includes("Content Security Policy") || text.includes("violates the following")) {
      violations.push(text);
    }
  });
  page.on("pageerror", (error) => {
    const text = error.message;
    if (text.includes("Content Security Policy") || text.includes("violates the following")) {
      violations.push(text);
    }
  });
  return violations;
}

async function visitAndExpectNoCspViolation(page: Page, routes: string[]) {
  const violations = collectCspViolations(page);
  for (const route of routes) {
    const response = await page.goto(route, { waitUntil: "load" });
    expect(response?.status(), route).toBeLessThan(500);
    await expect(page.locator("body")).toBeVisible();
    await expectNoHorizontalOverflow(page);
  }
  expect(violations).toEqual([]);
}

test("public pages render without CSP console violations", async ({ page }) => {
  const productId = await activeProductId();
  await visitAndExpectNoCspViolation(page, [
    "/",
    "/products",
    `/products/${productId}`,
    "/cart",
    "/checkout",
    "/login",
    "/welcome",
    "/policies",
    "/policies/terms",
    "/policies/privacy",
    "/policies/shipping",
    "/company",
    "/support",
  ]);
});

test("CSP allows the embedded address search", async ({ request }) => {
  const response = await request.get("/");
  expect(response.headers()["content-security-policy"]).toContain(
    "frame-src 'self' https://postcode.map.kakao.com",
  );
});

test("customer pages render without CSP console violations", async ({ page, context }) => {
  const customerCookie = await requireCustomerCookie();
  await addCookie(context, customerCookie);
  const response = await fetch(`${API_BASE_URL}/api/orders`, {
    headers: { Cookie: customerCookie },
  });
  const responseText = await response.text();
  expect(response.ok, responseText).toBeTruthy();
  const orders = JSON.parse(responseText) as { orders: CustomerOrderSummary[] };
  const routes = ["/cart", "/checkout", "/account", "/orders"];
  if (orders.orders[0]) {
    routes.push(`/orders/${orders.orders[0].orderId}`);
  }
  await visitAndExpectNoCspViolation(page, routes);
});

test("admin pages render without CSP console violations", async ({ page, context }) => {
  const adminCookie = await requireAdminCookie();
  await addCookie(context, adminCookie);
  const productId = await activeProductId();
  const order = await requireSeedOrderByStatus("PAYMENT_PENDING");
  await visitAndExpectNoCspViolation(page, [
    "/admin",
    "/admin/products",
    `/admin/products/${productId}`,
    "/admin/products/new",
    "/admin/pricing",
    "/admin/referrals",
    "/admin/orders",
    `/admin/orders?orderId=${order.orderId}`,
    "/admin/inquiries",
  ]);
});
