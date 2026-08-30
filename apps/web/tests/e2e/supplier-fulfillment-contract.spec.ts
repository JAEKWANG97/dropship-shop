import { expect, test } from "@playwright/test";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { SupplierOrderDetailView, SupplierOrdersView } from "../../src/app/supplier/(portal)/orders/order-views";
import { adminPortalFulfillmentAction, type AdminOrder } from "../../src/lib/admin";
import {
  getSupplierOrder,
  listSupplierOrders,
  normalizeSupplierOrderDetail,
  normalizeSupplierOrderList,
  supplierOrderStatusView,
} from "../../src/lib/supplier-orders";

test("supplier order list keeps only the PII-free fulfillment allowlist", () => {
  const orders = normalizeSupplierOrderList({
    orders: [{
      orderNumber: "ORD-103",
      status: "FULFILLMENT_REQUESTED",
      requestedAt: "2026-08-30T00:00:00Z",
      customerEmail: "must-not-render@example.com",
      recipient: { name: "고객", phone: "01011112222", address1: "비공개 주소" },
      payment: { amount: 31_000 },
      items: [{
        productName: "안전모",
        optionName: "흰색",
        quantity: 2,
        sourceUnitPrice: 10_000,
        supplierId: "other-supplier",
      }],
    }],
  });

  expect(orders).toEqual([{
    orderNumber: "ORD-103",
    status: "FULFILLMENT_REQUESTED",
    requestedAt: "2026-08-30T00:00:00Z",
    items: [{ productName: "안전모", optionName: "흰색", quantity: 2 }],
  }]);
  expect(JSON.stringify(orders)).not.toContain("must-not-render");
  expect(JSON.stringify(orders)).not.toContain("비공개 주소");
  expect(JSON.stringify(orders)).not.toContain("sourceUnitPrice");
});

test("supplier order detail keeps minimum fulfillment fields and fails closed to MASKED", () => {
  const detail = normalizeSupplierOrderDetail({
    orderNumber: "ORD-103",
    status: "FULFILLMENT_REQUESTED",
    requestedAt: "2026-08-30T00:00:00Z",
    piiAccessLevel: "FULL",
    piiBasis: "NORMAL_WINDOW",
    piiAccessUntil: "2026-10-29T00:00:00Z",
    recipient: {
      name: "고객",
      phone: "01011112222",
      postalCode: "12345",
      address1: "서울시 테스트로 1",
      address2: "101호",
      deliveryMemo: "문 앞",
      email: "must-not-render@example.com",
    },
    customerId: "customer-1",
    refund: { amount: 31_000 },
    adminMemo: "internal",
    items: [{
      orderItemId: "item-1",
      productName: "안전모",
      optionName: "흰색",
      quantity: 2,
      allocatedQuantity: 0,
      remainingQuantity: 2,
      sourceUnitPrice: 10_000,
    }],
  });

  expect(detail).toEqual({
    orderNumber: "ORD-103",
    status: "FULFILLMENT_REQUESTED",
    requestedAt: "2026-08-30T00:00:00Z",
    piiAccessLevel: "FULL",
    piiBasis: "NORMAL_WINDOW",
    piiAccessUntil: "2026-10-29T00:00:00Z",
    recipient: {
      name: "고객",
      phone: "01011112222",
      postalCode: "12345",
      address1: "서울시 테스트로 1",
      address2: "101호",
      deliveryMemo: "문 앞",
    },
    items: [{
      orderItemId: "item-1",
      productName: "안전모",
      optionName: "흰색",
      quantity: 2,
      allocatedQuantity: 0,
      remainingQuantity: 2,
    }],
  });
  expect(JSON.stringify(detail)).not.toContain("customerId");
  expect(JSON.stringify(detail)).not.toContain("refund");
  expect(JSON.stringify(detail)).not.toContain("adminMemo");
  const unknownLevel = normalizeSupplierOrderDetail({
    piiAccessLevel: "UNKNOWN",
    recipient: {
      name: "노출 금지",
      phone: "01011112222",
      postalCode: "12345",
      address1: "비공개 주소",
      address2: "101호",
      deliveryMemo: "비공개 메모",
    },
  });
  expect(unknownLevel.piiAccessLevel).toBe("MASKED");
  expect(unknownLevel.recipient).toEqual({
    name: null,
    phone: null,
    postalCode: null,
    address1: null,
    address2: null,
    deliveryMemo: null,
  });

  const masked = normalizeSupplierOrderDetail({
    piiAccessLevel: "MASKED",
    recipient: {
      name: "고**",
      phone: "*******2222",
      postalCode: "must-not-survive",
      address1: "must-not-survive",
      deliveryMemo: "must-not-survive",
    },
  });
  expect(masked.recipient).toEqual({
    name: "고**",
    phone: "*******2222",
    postalCode: null,
    address1: null,
    address2: null,
    deliveryMemo: null,
  });

  const unsafeMasked = normalizeSupplierOrderDetail({
    piiAccessLevel: "MASKED",
    recipient: { name: "고객 이름", phone: "01011112222" },
  });
  expect(unsafeMasked.recipient.name).toBeNull();
  expect(unsafeMasked.recipient.phone).toBeNull();
});

test("supplier order list renders the PII-free contract", async ({ page }) => {
  const orders = normalizeSupplierOrderList({
    orders: [{
      orderNumber: "ORD-RENDER-103",
      status: "FULFILLMENT_REQUESTED",
      requestedAt: "2026-08-30T00:00:00Z",
      customerEmail: "private@example.com",
      recipient: { name: "노출 금지", address1: "비공개 주소" },
      items: [{ productName: "안전모", optionName: "흰색", quantity: 2, sourceUnitPrice: 10_000 }],
    }],
  });

  await page.setContent(renderToStaticMarkup(createElement(SupplierOrdersView, {
    orders,
    loading: false,
    error: "",
  })));

  await expect(page.getByRole("heading", { name: "출고 요청", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: /ORD-RENDER-103/ })).toContainText("안전모 / 흰색");
  await expect(page.locator("body")).not.toContainText("private@example.com");
  await expect(page.locator("body")).not.toContainText("비공개 주소");
});

test("supplier order detail renders FULL and MASKED privacy levels", async ({ page }) => {
  const full = normalizeSupplierOrderDetail({
    orderNumber: "ORD-FULL-103",
    status: "FULFILLMENT_REQUESTED",
    requestedAt: "2026-08-30T00:00:00Z",
    piiAccessLevel: "FULL",
    piiAccessUntil: "2026-10-29T00:00:00Z",
    recipient: {
      name: "고객",
      phone: "01011112222",
      postalCode: "12345",
      address1: "서울시 테스트로 1",
      address2: "101호",
      deliveryMemo: "문 앞",
      email: "private@example.com",
    },
    items: [{
      orderItemId: "item-1",
      productName: "안전모",
      optionName: "흰색",
      quantity: 2,
      allocatedQuantity: 0,
      remainingQuantity: 2,
      sourceUnitPrice: 10_000,
    }],
  });

  await page.setContent(renderToStaticMarkup(createElement(SupplierOrderDetailView, { order: full })));
  await expect(page.getByText("배송 목적으로만 확인해 주세요")).toBeVisible();
  await expect(page.getByText("서울시 테스트로 1", { exact: false })).toBeVisible();
  await expect(page.getByText("문 앞", { exact: true })).toBeVisible();
  await expect(page.locator("body")).not.toContainText("private@example.com");
  await expect(page.locator("body")).not.toContainText("10000");

  const masked = normalizeSupplierOrderDetail({
    ...full,
    orderNumber: "ORD-MASKED-103",
    piiAccessLevel: "MASKED",
    recipient: {
      name: "고**",
      phone: "*******2222",
      postalCode: "54321",
      address1: "가려져야 할 주소",
      address2: "202호",
      deliveryMemo: "가려져야 할 메모",
    },
  });

  await page.setContent(renderToStaticMarkup(createElement(SupplierOrderDetailView, { order: masked })));
  await expect(page.getByText("배송정보가 가려졌습니다")).toBeVisible();
  await expect(page.getByText("고**", { exact: true })).toBeVisible();
  await expect(page.getByText("*******2222", { exact: true })).toBeVisible();
  await expect(page.locator("body")).not.toContainText("가려져야 할 주소");
  await expect(page.locator("body")).not.toContainText("가려져야 할 메모");
  await expect(page.getByText("주소", { exact: true })).toHaveCount(0);
  await expect(page.getByText("배송 메모", { exact: true })).toHaveCount(0);
});

test("supplier fulfillment reads are same-origin and never cached", async () => {
  const originalFetch = globalThis.fetch;
  const calls: Array<{ path: string; init?: RequestInit }> = [];
  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    calls.push({ path: String(input), init });
    const body = calls.length === 1
      ? { orders: [] }
      : { orderNumber: "ORD 103/1", piiAccessLevel: "MASKED", recipient: {}, items: [] };
    return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
  }) as typeof fetch;

  try {
    await listSupplierOrders();
    await getSupplierOrder("ORD 103/1");
  } finally {
    globalThis.fetch = originalFetch;
  }

  expect(calls.map((call) => call.path)).toEqual([
    "/api/supplier/orders",
    "/api/supplier/orders/ORD%20103%2F1",
  ]);
  for (const call of calls) {
    expect(call.init?.cache).toBe("no-store");
    expect(call.init?.credentials).toBe("same-origin");
    expect(new Headers(call.init?.headers).get("Accept")).toBe("application/json");
  }
});

test("supplier status and admin portal actions fail closed", () => {
  expect(supplierOrderStatusView("FULFILLMENT_REQUESTED").label).toBe("출고 요청");
  expect(supplierOrderStatusView("INTERNAL_UNKNOWN").label).toBe("상태 확인 필요");

  const portal = (owner: string) => ({
    channel: "SUPPLIER_PORTAL",
    operationalOwner: owner,
  }) as AdminOrder["fulfillment"];

  expect(adminPortalFulfillmentAction(portal("SUPPLIER"))).toBe("TAKEOVER");
  expect(adminPortalFulfillmentAction(portal("COREABLE"))).toBe("COREABLE");
  expect(adminPortalFulfillmentAction(portal("UNKNOWN"))).toBe("COREABLE");
  expect(adminPortalFulfillmentAction({ channel: "COREABLE_MANUAL" } as AdminOrder["fulfillment"])).toBeNull();
});
