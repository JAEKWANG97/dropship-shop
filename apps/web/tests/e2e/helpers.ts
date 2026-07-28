import { expect, test, type BrowserContext, type Page } from "@playwright/test";

export const API_BASE_URL = process.env.E2E_API_BASE_URL ?? "http://localhost:8080";
export const WEB_BASE_URL = process.env.E2E_WEB_BASE_URL ?? "http://localhost:3000";
const PRIMARY_PRODUCT_NAME = "K2 안전모 K2-THINK 1";

const REQUIRE_ADMIN_SEED_ORDERS_VALUE =
  process.env.E2E_REQUIRE_ADMIN_SEED_ORDERS ??
  (API_BASE_URL.includes("localhost") || API_BASE_URL.includes("127.0.0.1") ? "true" : "false");

export const REQUIRE_ADMIN_SEED_ORDERS = ["1", "true", "yes"].includes(
  REQUIRE_ADMIN_SEED_ORDERS_VALUE.toLowerCase(),
);

export function isLocalTarget() {
  return WEB_BASE_URL.includes("localhost") || WEB_BASE_URL.includes("127.0.0.1");
}

export type ProductSummary = {
  id: string;
  name: string;
};

type ProductPage = {
  products: ProductSummary[];
};

export type AdminOrderSummary = {
  orderId: string;
  orderNumber: string;
  status: string;
  checkoutNumber: string;
  totalAmount: number;
};

export type CustomerOrderSummary = {
  orderId: string;
  orderNumber: string;
  checkoutNumber: string;
  status: string;
};

export type CustomerOrderDetail = CustomerOrderSummary & {
  claims: { claimId: string; status: string; customerMemo: string }[];
};

export async function activeProductId() {
  const response = await fetch(
    `${API_BASE_URL}/api/products?q=${encodeURIComponent(PRIMARY_PRODUCT_NAME)}&size=10`,
  );
  expect(response.ok).toBeTruthy();
  const products = ((await response.json()) as ProductPage).products;
  const primaryProduct = products.find((product) => product.name === PRIMARY_PRODUCT_NAME);
  if (isLocalTarget()) {
    expect(primaryProduct, `Local seed product '${PRIMARY_PRODUCT_NAME}' is required`).toBeTruthy();
  }
  const fallbackProducts = primaryProduct
    ? products
    : ((await (await fetch(`${API_BASE_URL}/api/products?size=1`)).json()) as ProductPage).products;
  const product = primaryProduct ?? fallbackProducts[0];
  expect(product, "At least one active product is required").toBeTruthy();
  return product.id;
}

export async function addCookie(context: BrowserContext, cookieHeader: string) {
  const host = new URL(WEB_BASE_URL).hostname;
  const accessToken = accessTokenValue(cookieHeader);

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

export function accessTokenValue(cookieHeader: string) {
  return cookieHeader
    .split(";")
    .map((item) => item.trim())
    .find((item) => item.startsWith("ACCESS_TOKEN="))
    ?.slice("ACCESS_TOKEN=".length);
}

export async function expectNoHorizontalOverflow(page: Page) {
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

export async function firstAdminOrderLink(page: Page) {
  const seedOrderLink = page.locator("a[href^='/admin/orders?orderId=']").filter({ hasText: "LOCAL-B003-" }).first();
  const orderLink = (await seedOrderLink.count()) > 0
    ? seedOrderLink
    : page.locator("a[href^='/admin/orders?orderId=']").first();

  if (REQUIRE_ADMIN_SEED_ORDERS) {
    await expect(orderLink, "Local/dev admin order seed data is required").toHaveCount(1);
  } else {
    test.skip((await orderLink.count()) === 0, "No admin order exists for this smoke target.");
  }
  return orderLink;
}

export async function requireAdminCookie() {
  if (process.env.E2E_ADMIN_COOKIE) {
    return process.env.E2E_ADMIN_COOKIE;
  }
  return devLoginCookie("ADMIN");
}

export async function requireCustomerCookie() {
  if (process.env.E2E_CUSTOMER_COOKIE) {
    return process.env.E2E_CUSTOMER_COOKIE;
  }
  return devLoginCookie("CUSTOMER");
}

export async function requireSeedOrderByStatus(status: string) {
  const adminCookie = await requireAdminCookie();
  const data = await apiGet<{ orders: AdminOrderSummary[] }>(`/api/admin/orders?status=${status}`, adminCookie);
  const order = data.orders.find((item) => item.orderNumber.startsWith("LOCAL-B003-")) ?? data.orders[0];

  if (REQUIRE_ADMIN_SEED_ORDERS) {
    expect(order, `Local/dev seed order is required for ${status}`).toBeTruthy();
  } else {
    test.skip(!order, `No ${status} order exists for this smoke target.`);
  }
  return order!;
}

export async function customerOrderDetail(orderId: string, customerCookie: string) {
  return apiGet<CustomerOrderDetail>(`/api/orders/${orderId}`, customerCookie);
}

export async function ensureSimpleReturnClaim(orderId: string, customerCookie: string) {
  const detail = await customerOrderDetail(orderId, customerCookie);
  if (detail.claims?.length > 0) {
    return;
  }

  const response = await fetch(`${API_BASE_URL}/api/orders/${orderId}/claims`, {
    method: "POST",
    headers: {
      Cookie: customerCookie,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      claimType: "RETURN",
      claimReason: "SIMPLE_CHANGE_OF_MIND",
      customerMemo: "E2E 클레임 상태 표시 확인",
    }),
  });

  if (response.status === 409) {
    return;
  }
  if (!response.ok) {
    expect(response.ok, await response.text()).toBeTruthy();
  }
}

async function apiGet<T>(path: string, cookieHeader: string) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: { Cookie: cookieHeader },
  });
  if (!response.ok) {
    expect(response.ok, await response.text()).toBeTruthy();
  }
  return response.json() as Promise<T>;
}

async function devLoginCookie(role: "ADMIN" | "CUSTOMER") {
  const response = await fetch(`${API_BASE_URL}/api/dev/login?role=${role}`);
  if (!response.ok) {
    test.skip(true, `Set E2E_${role}_COOKIE or enable local app.dev-login for ${role} smoke.`);
    throw new Error(`Dev login failed with ${response.status}`);
  }

  const cookieHeader = response.headers.get("set-cookie") ?? "";
  if (!accessTokenValue(cookieHeader)) {
    test.skip(true, `Dev login did not return ACCESS_TOKEN for ${role} smoke.`);
    throw new Error("Dev login did not return ACCESS_TOKEN");
  }
  return cookieHeader;
}
