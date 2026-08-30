import { expect, test } from "@playwright/test";
import {
  API_BASE_URL,
  addCookie,
  activeProductId,
  ensureSimpleReturnClaim,
  expectNoHorizontalOverflow,
  isLocalTarget,
  requireAdminCookie,
  requireCustomerCookie,
  requireSeedOrderByStatus,
} from "./helpers";

const POSTCODE_SCRIPT =
  "https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js";

test("checkout detail shows bank-transfer deposit instructions", async ({ page, context }) => {
  const [customerCookie, paymentPendingOrder] = await Promise.all([
    requireCustomerCookie(),
    requireSeedOrderByStatus("PAYMENT_PENDING"),
  ]);

  await addCookie(context, customerCookie);
  await page.goto(`/checkout/${paymentPendingOrder.checkoutNumber}`);

  await expect(page.getByRole("heading", { name: new RegExp(`주문서 ${paymentPendingOrder.checkoutNumber}`) })).toBeVisible();
  await expect(page.getByRole("heading", { name: "계좌입금 안내" })).toBeVisible();
  await expect(page.locator("body")).toContainText("로컬 테스트 은행");
  await expect(page.locator("body")).toContainText("000-0000-0000");
  await expect(page.locator("body")).toContainText("가라사니");
  await expect(page.locator("body")).toContainText("로컬 주문 고객");
  await expect(page.locator("body")).toContainText("입금 금액");
  await expect(page.locator("body")).toContainText("입금 기한");
  await expectNoHorizontalOverflow(page);
});

test("checkout shows, updates, and locks the confirmed shipping address", async ({ page, context }) => {
  test.skip(!isLocalTarget(), "Checkout mutation smoke runs only against the local API.");
  const customerCookie = await requireCustomerCookie();
  await page.route(POSTCODE_SCRIPT, async (route) => {
    await route.fulfill({
      contentType: "application/javascript",
      body: `window.daum={Postcode:function(options){this.embed=function(){if(options.width!=="100%"||options.height!=="100%")throw new Error("Responsive postcode size is required");options.oncomplete({zonecode:"05555",roadAddress:"서울특별시 송파구 테스트로 1",jibunAddress:"",userSelectedType:"R"});};}};`,
    });
  });
  const checkout = await createPendingCheckout(customerCookie);
  await addCookie(context, customerCookie);
  try {
    await page.goto(`/checkout/${checkout.checkoutNumber}`);
    await expect(page.getByRole("heading", { name: "배송지" })).toBeVisible();
    await expect(page.locator("body")).toContainText("서울특별시 송파구 초기로 1");
    await expect(page.getByLabel("받는 사람")).toHaveValue("E2E 주문 고객");
    await expect(page.getByLabel("배송 메모")).toHaveValue("초기 배송 메모");

    await page.getByLabel("받는 사람").fill("변경된 주문 고객");
    await page.getByLabel("연락처").fill("010-9999-8888");
    await page.getByRole("button", { name: "주소 검색" }).click();
    await expect(page.getByLabel("우편번호")).toHaveValue("05555");
    await expect(page.getByLabel("주소", { exact: true })).toHaveValue("서울특별시 송파구 테스트로 1");
    await page.getByLabel("상세 주소").fill("202호");
    await page.getByLabel("배송 메모").fill("문 앞에 놓아 주세요");
    await page.getByRole("button", { name: "배송지 변경" }).click();

    await expect(page.locator("body")).toContainText("배송지를 변경했습니다.");
    await expect(page.locator("body")).toContainText("서울특별시 송파구 테스트로 1 202호");
    await expect(page.locator("body")).toContainText("문 앞에 놓아 주세요");
    await page.getByRole("checkbox").check();
    await page.getByRole("button", { name: "정책 확인 저장" }).click();

    await expect(page.getByRole("heading", { name: "계좌입금 안내" })).toBeVisible();
    await expect(page.getByText("주문 정책 확인이 완료된 배송지는 고객 문의를 통해서만 변경할 수 있습니다.")).toBeVisible();
    await expect(page.getByRole("button", { name: "배송지 변경" })).toHaveCount(0);
    await expect(page.locator("body")).toContainText("서울특별시 송파구 테스트로 1 202호");
    await expect(page.locator("body")).toContainText("문 앞에 놓아 주세요");
    await expectNoHorizontalOverflow(page);
  } finally {
    await cancelUnpaidCheckout(checkout.orderId);
  }
});

test("customer orders page links to order detail with claim form and claim status", async ({ page, context }) => {
  const [customerCookie, deliveredOrder] = await Promise.all([
    requireCustomerCookie(),
    requireSeedOrderByStatus("DELIVERED"),
  ]);
  await ensureSimpleReturnClaim(deliveredOrder.orderId, customerCookie);

  await addCookie(context, customerCookie);
  await page.goto("/orders");
  await page.locator(`a[href="/orders/${deliveredOrder.orderId}"]`).click();

  await expect(page).toHaveURL(new RegExp(`/orders/${deliveredOrder.orderId}$`));
  await expect(page.getByRole("heading", { name: new RegExp(`주문 ${deliveredOrder.orderNumber}`) })).toBeVisible();
  await expect(page.getByText("주문 상태")).toBeVisible();
  await expect(page.getByRole("heading", { name: "클레임 처리 상태" })).toBeVisible();
  await expect(page.getByText("접수 상태")).toBeVisible();
  await expect(page.getByRole("heading", { name: "클레임 접수" })).toBeVisible();
  await expect(page.locator('input[name="evidenceFiles"]')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test("order detail without claims still renders a stable claim entry form", async ({ page, context }) => {
  const [customerCookie, supplierOrderedOrder] = await Promise.all([
    requireCustomerCookie(),
    requireSeedOrderByStatus("SUPPLIER_ORDERED"),
  ]);

  await addCookie(context, customerCookie);
  await page.goto(`/orders/${supplierOrderedOrder.orderId}`);

  await expect(page.getByRole("heading", { name: new RegExp(`주문 ${supplierOrderedOrder.orderNumber}`) })).toBeVisible();
  await expect(page.getByRole("heading", { name: "클레임 처리 상태" })).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "클레임 접수" })).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

type ProductDetail = {
  options: { id: string; status: string }[];
};

type Cart = {
  items: { id: string }[];
};

type CheckoutFixture = {
  checkoutNumber: string;
  orderId: string;
};

async function createPendingCheckout(customerCookie: string): Promise<CheckoutFixture> {
  await apiRequest("/api/me/profile", customerCookie, {
    method: "PATCH",
    body: JSON.stringify({
      displayName: "E2E 주문 고객",
      email: "checkout-e2e@coreable.local",
      phoneNumber: "010-1111-2222",
    }),
  });
  await apiRequest("/api/me/agreements", customerCookie, {
    method: "POST",
    body: JSON.stringify({
      termsAgreed: true,
      privacyAgreed: true,
      termsVersion: "2026-08-02",
      privacyVersion: "2026-08-04",
    }),
  });
  const cart = (await apiRequest("/api/cart", customerCookie)) as Cart;
  for (const item of cart.items) {
    await apiRequest(`/api/cart/items/${item.id}`, customerCookie, { method: "DELETE" });
  }
  const productId = await activeProductId();
  const product = (await apiRequest(`/api/products/${productId}`, customerCookie)) as ProductDetail;
  const option = product.options.find((item) => item.status === "ACTIVE");
  expect(option, "An active product option is required for checkout smoke").toBeTruthy();
  await apiRequest("/api/cart/items", customerCookie, {
    method: "POST",
    body: JSON.stringify({ productOptionId: option!.id, quantity: 1 }),
  });
  const checkout = (await apiRequest("/api/checkouts", customerCookie, {
    method: "POST",
    body: JSON.stringify({
      recipientName: "E2E 주문 고객",
      recipientPhone: "010-1111-2222",
      postalCode: "05554",
      address1: "서울특별시 송파구 초기로 1",
      address2: "101호",
      deliveryMemo: "초기 배송 메모",
    }),
  })) as { checkoutNumber: string; orders: { id: string }[] };
  return { checkoutNumber: checkout.checkoutNumber, orderId: checkout.orders[0].id };
}

async function cancelUnpaidCheckout(orderId: string) {
  const adminCookie = await requireAdminCookie();
  await apiRequest(`/api/admin/orders/${orderId}/unpaid-cancel`, adminCookie, {
    method: "POST",
    body: JSON.stringify({ reason: "B-080 Playwright checkout cleanup" }),
  });
}

async function apiRequest(path: string, cookieHeader: string, init: RequestInit = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      Cookie: cookieHeader,
      ...(init.body ? { "Content-Type": "application/json" } : {}),
      ...init.headers,
    },
  });
  if (!response.ok) {
    expect(response.ok, await response.text()).toBeTruthy();
  }
  return response.status === 204 ? null : response.json();
}
