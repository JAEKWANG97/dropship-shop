import { mkdir } from "node:fs/promises";
import path from "node:path";
import { expect, test } from "@playwright/test";
import {
  API_BASE_URL,
  MOQ_PRODUCT_NAME,
  addCookie,
  expectNoHorizontalOverflow,
  isLocalTarget,
  moqProductId,
  requireAdminCookie,
  requireCustomerCookie,
} from "./helpers";

const screenshotDir = path.resolve(process.cwd(), "../../tmp/qa/b-089");

test("MOQ card and purchase panel show quantity rules with inline validation", async ({ page }, testInfo) => {
  const productId = await moqProductId();
  await mkdir(screenshotDir, { recursive: true });

  await page.goto(`/products?q=${encodeURIComponent(MOQ_PRODUCT_NAME)}`);
  const card = page.locator(`a.product-card[href="/products/${productId}"]`);
  await expect(card).toContainText(MOQ_PRODUCT_NAME);
  await expect(card).toContainText("최소 6개");
  await page.screenshot({ path: path.join(screenshotDir, `product-card-${testInfo.project.name}.png`) });

  await page.goto(`/products/${productId}`);
  const mobile = testInfo.project.name === "mobile";
  if (mobile) {
    await expect(page.locator(".mobile-purchase-total")).toContainText("34,800원");
    await page.locator(".mobile-purchase-bar").getByRole("button", { name: "장바구니", exact: true }).click();
  }
  const form = page.locator(mobile ? ".mobile-purchase-form" : ".desktop-purchase-form");
  const quantity = form.getByLabel("수량");
  await expect(quantity).toHaveValue("6");
  await expect(quantity).toHaveAttribute("min", "6");
  await expect(quantity).toHaveAttribute("step", "6");
  await expect(form).toContainText("개당 가격");
  await expect(form).toContainText("최소 주문 수량");
  await expect(form).toContainText("주문 단위");
  await expect(form).toContainText("34,800원");

  await quantity.fill("5");
  await expect(form.getByText("최소 6개부터 주문할 수 있습니다.")).toBeVisible();
  await expect(form.getByRole("button", { name: "장바구니", exact: true })).toBeDisabled();
  await expect(form.getByRole("button", { name: "바로구매", exact: true })).toBeDisabled();

  await quantity.fill("7");
  await expect(form.getByText("6개 단위로 입력해 주세요. 예: 6, 12, 18")).toBeVisible();

  await quantity.fill("12");
  await expect(form).toContainText("69,600원");
  await expect(form.getByRole("button", { name: "장바구니", exact: true })).toBeEnabled();
  await expectNoHorizontalOverflow(page);
  await page.screenshot({ path: path.join(screenshotDir, `product-detail-${testInfo.project.name}.png`) });
});

test("MOQ cart and checkout show unit price, rules, and line total", async ({ page, context }, testInfo) => {
  const [productId, customerCookie] = await Promise.all([moqProductId(), requireCustomerCookie()]);
  await mkdir(screenshotDir, { recursive: true });
  await clearCart(customerCookie);
  const product = await apiRequest<ProductDetail>(`/api/products/${productId}`, customerCookie);
  const option = product.options.find((item) => item.status === "ACTIVE");
  expect(option).toBeTruthy();
  await apiRequest("/api/cart/items", customerCookie, {
    method: "POST",
    body: JSON.stringify({ productOptionId: option!.id, quantity: 6 }),
  });
  await addCookie(context, customerCookie);

  try {
    await page.goto("/cart");
    await expect(page.locator(".cart-item")).toContainText("최소 6개 · 6개 단위");
    await expect(page.locator(".cart-item")).toContainText("34,800원");
    await expect(page.getByRole("link", { name: "주문서 작성" })).toBeVisible();
    await expectNoHorizontalOverflow(page);
    await page.screenshot({ path: path.join(screenshotDir, `cart-${testInfo.project.name}.png`) });

    await page.goto("/checkout");
    const summary = page.locator(".checkout-summary-card");
    await expect(summary).toContainText("개당 5,800원");
    await expect(summary).toContainText("수량 6개");
    await expect(summary).toContainText("최소 6개 / 6개 단위");
    await expect(summary).toContainText("34,800원");
    await expectNoHorizontalOverflow(page);
    await page.screenshot({ path: path.join(screenshotDir, `checkout-${testInfo.project.name}.png`) });
  } finally {
    await clearCart(customerCookie);
  }
});

test("saved cart blocks checkout after MOQ changes without changing quantity", async ({ page, context }) => {
  test.skip(!isLocalTarget(), "MOQ mutation smoke uses local admin and seed data.");
  const [productId, customerCookie, adminCookie] = await Promise.all([
    moqProductId(),
    requireCustomerCookie(),
    requireAdminCookie(),
  ]);
  await clearCart(customerCookie);
  const product = await apiRequest<ProductDetail>(`/api/products/${productId}`, customerCookie);
  const adminProduct = await apiRequest<AdminProduct>(`/api/admin/products/${productId}`, adminCookie);
  const option = product.options.find((item) => item.status === "ACTIVE");
  expect(option).toBeTruthy();
  await apiRequest("/api/cart/items", customerCookie, {
    method: "POST",
    body: JSON.stringify({ productOptionId: option!.id, quantity: 6 }),
  });
  await updateMoq(adminProduct, adminCookie, 8);
  await addCookie(context, customerCookie);

  try {
    await page.goto("/cart");
    const quantity = page.locator(".cart-item").getByLabel(`${MOQ_PRODUCT_NAME} quantity`);
    await expect(quantity).toHaveValue("6");
    await expect(page.locator(".cart-item .quantity-error")).toContainText(
      "현재 수량은 6개입니다. 최소 8개부터 주문할 수 있습니다.",
    );
    await expect(page.getByRole("link", { name: "주문서 작성" })).toHaveCount(0);

    await page.goto("/checkout");
    await expect(page.getByText("주문 불가", { exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: "장바구니로 돌아가기" })).toBeVisible();
  } finally {
    await updateMoq(adminProduct, adminCookie, 6);
    await clearCart(customerCookie);
  }
});

type ProductDetail = {
  options: { id: string; status: string }[];
};

type AdminProduct = {
  id: string;
  supplierId: string;
  name: string;
  summary: string;
  sourcePrice: number;
  sourceItemNo: string | null;
  sourceUrl: string | null;
  basePrice: number;
  categoryCode: string;
  complianceStatus: string;
};

type Cart = { items: { id: string }[] };

async function clearCart(cookie: string) {
  const cart = await apiRequest<Cart>("/api/cart", cookie);
  for (const item of cart.items) {
    await apiRequest(`/api/cart/items/${item.id}`, cookie, { method: "DELETE" });
  }
}

async function updateMoq(product: AdminProduct, cookie: string, quantity: number) {
  await apiRequest(`/api/admin/products/${product.id}`, cookie, {
    method: "PATCH",
    body: JSON.stringify({
      supplierId: product.supplierId,
      name: product.name,
      summary: product.summary,
      sourcePrice: product.sourcePrice,
      sourceItemNo: product.sourceItemNo,
      sourceUrl: product.sourceUrl,
      basePrice: product.basePrice,
      minimumOrderQuantity: quantity,
      orderQuantityStep: quantity,
      categoryCode: product.categoryCode,
      complianceStatus: product.complianceStatus,
      reason: "B-089 Playwright MOQ validation",
    }),
  });
}

async function apiRequest<T = unknown>(pathValue: string, cookie: string, init: RequestInit = {}) {
  const response = await fetch(`${API_BASE_URL}${pathValue}`, {
    ...init,
    headers: {
      Cookie: cookie,
      ...(init.body ? { "Content-Type": "application/json" } : {}),
      ...init.headers,
    },
  });
  if (!response.ok) {
    expect(response.ok, await response.text()).toBeTruthy();
  }
  return response.status === 204 ? (null as T) : (response.json() as Promise<T>);
}
