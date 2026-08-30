import { expect, test } from "@playwright/test";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { SupplierOrderDetailView, SupplierOrdersView } from "../../src/app/supplier/(portal)/orders/order-views";
import { ApiError } from "../../src/lib/api";
import {
  adminPortalShipmentMutationAllowed,
  adminPortalFulfillmentAction,
  adminStatusLabel,
  hasCanonicalAdminShipmentAllocations,
  normalizeAdminCarriers,
  type AdminOrder,
} from "../../src/lib/admin";
import { parseAdminExpectedVersion, uncertainAdminCommandKey } from "../../src/lib/admin-payment";
import { customerDirectCancelBlocked, orderStatusLabel, shipmentStatusLabel } from "../../src/lib/orders";
import {
  correctSupplierShipment,
  createSupplierShipment,
  getSupplierOrder,
  listSupplierCarriers,
  listSupplierOrders,
  listSupplierShipments,
  loadSupplierShipmentRefresh,
  normalizeSupplierCarriers,
  normalizeSupplierOrderDetail,
  normalizeSupplierOrderList,
  normalizeSupplierShipments,
  releaseSupplierShipmentCommandKey,
  recoverSupplierShipmentConflict,
  safeOfficialTrackingUrl,
  SupplierOrderApiError,
  supplierOrderStatusView,
  supplierShipmentCommandKey,
  supplierShipmentRegistrationAllowed,
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

test("B-104 carrier and shipment normalizers keep the canonical allowlist", () => {
  expect(normalizeSupplierCarriers({
    carriers: [{
      carrierCode: "CJ_LOGISTICS",
      carrierName: "CJ대한통운",
      officialTrackingSupported: true,
      trackingTemplate: "must-not-survive",
    }],
  })).toEqual([{
    carrierCode: "CJ_LOGISTICS",
    carrierName: "CJ대한통운",
    officialTrackingSupported: true,
  }]);

  const collection = normalizeSupplierShipments({
    shipments: [
      {
        shipmentId: "shipment-1",
        version: 2,
        status: "TRACKING_REGISTERED",
        carrierCode: "CJ_LOGISTICS",
        carrierName: "CJ대한통운",
        trackingNumber: "1234567890",
        officialTrackingUrl: "https://carrier.example/track/1234567890",
        editable: true,
        countsTowardAllocation: true,
        registeredAt: "2026-08-29T01:00:00Z",
        allocations: [{ orderItemId: "item-1", quantity: 2, unitPrice: 99_000 }],
        customerEmail: "must-not-survive@example.com",
      },
      {
        shipmentId: "shipment-void",
        version: 3,
        status: "VOIDED",
        carrierName: "알 수 없음",
        trackingNumber: "unsafe",
        officialTrackingUrl: "javascript:alert(1)",
        editable: true,
        countsTowardAllocation: true,
        allocations: [{ orderItemId: "item-1", quantity: 2 }],
      },
      {
        shipmentId: "shipment-malformed-version",
        status: "TRACKING_REGISTERED",
        carrierName: "CJ대한통운",
        trackingNumber: "malformed",
        editable: true,
        allocations: [],
      },
    ],
    unallocatedItems: [{
      orderItemId: "item-2",
      productName: "안전화",
      optionName: "270",
      orderedQuantity: 3,
      allocatedQuantity: 1,
      remainingQuantity: 2,
      sourceUnitPrice: 50_000,
    }],
    allocationComplete: false,
    canRegisterShipment: true,
    nextAction: "REGISTER_SHIPMENT",
  });

  expect(collection.allocationComplete).toBe(false);
  expect(collection.canRegisterShipment).toBe(true);
  expect(collection.unallocatedItems).toEqual([{
    orderItemId: "item-2",
    productName: "안전화",
    optionName: "270",
    remainingQuantity: 2,
  }]);
  expect(collection.shipments[0].officialTrackingUrl).toBe("https://carrier.example/track/1234567890");
  expect(collection.shipments[1].officialTrackingUrl).toBeNull();
  expect(collection.shipments[1].editable).toBe(false);
  expect(collection.shipments[1].countsTowardAllocation).toBe(false);
  expect(collection.shipments[2].version).toBeNull();
  expect(collection.shipments[2].editable).toBe(false);
  expect(JSON.stringify(collection)).not.toContain("must-not-survive");
  expect(JSON.stringify(collection)).not.toContain("unitPrice");
  expect(safeOfficialTrackingUrl("http://carrier.example/track/1")).toBeNull();
});

test("B-104 supplier mutation authority is fail-closed when the API omits it", () => {
  const omitted = normalizeSupplierShipments({
    shipments: [],
    unallocatedItems: [{ orderItemId: "item-1", remainingQuantity: 1 }],
  });
  const allowed = normalizeSupplierShipments({
    shipments: [],
    unallocatedItems: [{ orderItemId: "item-1", remainingQuantity: 1 }],
    canRegisterShipment: true,
  });

  expect(omitted.canRegisterShipment).toBeNull();
  expect(supplierShipmentRegistrationAllowed(omitted)).toBe(false);
  expect(supplierShipmentRegistrationAllowed(allowed)).toBe(true);
});

test("B-104 supplier mutation recovery refreshes stale or lost authority state", async () => {
  let refreshes = 0;
  const refresh = async () => { refreshes += 1; };

  expect(await recoverSupplierShipmentConflict(new SupplierOrderApiError(409, "STALE_VERSION"), refresh)).toBe(true);
  expect(refreshes).toBe(1);
  expect(await recoverSupplierShipmentConflict(new SupplierOrderApiError(404, "RESOURCE_NOT_FOUND"), refresh)).toBe(true);
  expect(await recoverSupplierShipmentConflict(new SupplierOrderApiError(403, "FORBIDDEN"), refresh)).toBe(true);
  expect(await recoverSupplierShipmentConflict(new SupplierOrderApiError(400, "VALIDATION_FAILED"), refresh)).toBe(false);
  expect(refreshes).toBe(3);
});

test("B-104 partial refresh keeps the new masked order and drops stale shipment actions", async () => {
  const originalFetch = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = (async () => {
    calls += 1;
    if (calls === 1) {
      return new Response(JSON.stringify({
        orderNumber: "ORD-104-MASKED",
        status: "TRACKING_REGISTERED",
        requestedAt: "2026-08-30T00:00:00Z",
        piiAccessLevel: "MASKED",
        piiBasis: "CUTOFF_REACHED",
        piiAccessUntil: null,
        recipient: {
          name: "고**",
          phone: "*******2222",
          postalCode: null,
          address1: null,
          address2: null,
          deliveryMemo: null,
        },
        items: [],
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    }
    return new Response(JSON.stringify({ code: "RESOURCE_NOT_FOUND" }), {
      status: 404,
      headers: { "Content-Type": "application/json" },
    });
  }) as typeof fetch;

  try {
    const refreshed = await loadSupplierShipmentRefresh("ORD-104-MASKED");
    expect(refreshed.order?.piiAccessLevel).toBe("MASKED");
    expect(refreshed.order?.recipient.address1).toBeNull();
    expect(refreshed.shipmentState).toBeNull();
    expect(refreshed.shipmentError).toBeInstanceOf(SupplierOrderApiError);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("B-104 shipment command keys survive uncertain outcomes and rotate after definite rejection", () => {
  const supplierKeys = new Map<string, string>();
  let created = 0;
  const createKey = () => `supplier-command-${++created}`;

  expect(supplierShipmentCommandKey(supplierKeys, "create", createKey)).toBe("supplier-command-1");
  expect(supplierShipmentCommandKey(supplierKeys, "create", createKey)).toBe("supplier-command-1");
  releaseSupplierShipmentCommandKey(supplierKeys, "create", new Error("refresh failed after API success"));
  expect(supplierShipmentCommandKey(supplierKeys, "create", createKey)).toBe("supplier-command-1");
  releaseSupplierShipmentCommandKey(supplierKeys, "create", new SupplierOrderApiError(500, "UPSTREAM_FAILURE"));
  expect(supplierShipmentCommandKey(supplierKeys, "create", createKey)).toBe("supplier-command-1");
  releaseSupplierShipmentCommandKey(supplierKeys, "create", new SupplierOrderApiError(409, "IDEMPOTENCY_CONFLICT"));
  expect(supplierShipmentCommandKey(supplierKeys, "create", createKey)).toBe("supplier-command-2");

  const adminKey = "11111111-1111-4111-8111-111111111111";
  expect(uncertainAdminCommandKey(new Error("network failure"), adminKey)).toBe(adminKey);
  expect(uncertainAdminCommandKey(new ApiError(500, "temporary"), adminKey)).toBe(adminKey);
  expect(uncertainAdminCommandKey(new ApiError(409, "conflict"), adminKey)).toBeUndefined();
});

test("B-104 admin carrier and allocation contracts fail closed on missing fields", () => {
  expect(normalizeAdminCarriers({})).toEqual([]);
  expect(normalizeAdminCarriers({ carriers: [{ carrierCode: "", carrierName: "이름 없음" }] })).toEqual([]);
  expect(normalizeAdminCarriers({
    carriers: [{
      carrierCode: "CJ_LOGISTICS",
      carrierName: "CJ대한통운",
      officialTrackingSupported: true,
      trackingTemplate: "must-not-survive",
    }],
  })).toEqual([{
    carrierCode: "CJ_LOGISTICS",
    carrierName: "CJ대한통운",
    officialTrackingSupported: true,
  }]);

  expect(hasCanonicalAdminShipmentAllocations(undefined)).toBe(false);
  expect(hasCanonicalAdminShipmentAllocations([])).toBe(true);
  expect(hasCanonicalAdminShipmentAllocations([{ shipmentId: "missing" }])).toBe(false);
  expect(hasCanonicalAdminShipmentAllocations([{ shipmentId: "complete", allocations: [] }])).toBe(true);
  expect(adminPortalShipmentMutationAllowed(true, "SUPPLIER", 0)).toBe(true);
  expect(adminPortalShipmentMutationAllowed(true, "COREABLE", 2)).toBe(true);
  expect(adminPortalShipmentMutationAllowed(true, null, 2)).toBe(false);
  expect(adminPortalShipmentMutationAllowed(true, "SUPPLIER", null)).toBe(false);
  expect(parseAdminExpectedVersion("0")).toBe(0);
  expect(parseAdminExpectedVersion("12")).toBe(12);
  expect(parseAdminExpectedVersion("")).toBeNull();
  expect(parseAdminExpectedVersion("-1")).toBeNull();
  expect(parseAdminExpectedVersion("1.5")).toBeNull();
});

test("B-104 supplier shipment requests preserve paths, methods, allocations and idempotency", async () => {
  const originalFetch = globalThis.fetch;
  const calls: Array<{ path: string; init?: RequestInit }> = [];
  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    calls.push({ path: String(input), init });
    const body = calls.length === 1
      ? { carriers: [] }
      : calls.length === 2 ? { shipments: [], unallocatedItems: [], allocationComplete: false } : {};
    return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
  }) as typeof fetch;

  try {
    await listSupplierCarriers();
    await listSupplierShipments("ORD 104/1");
    await createSupplierShipment("ORD 104/1", {
      carrierCode: "CJ_LOGISTICS",
      trackingNumber: "first",
    }, "first-command");
    await createSupplierShipment("ORD 104/1", {
      carrierCode: "CJ_LOGISTICS",
      trackingNumber: "split",
      allocations: [{ orderItemId: "item-1", quantity: 1 }],
    }, "split-command");
    await correctSupplierShipment("ORD 104/1", "shipment/1", {
      expectedVersion: 2,
      carrierCode: "HANJIN",
      trackingNumber: "corrected",
      reason: "오입력 정정",
    }, "correction-command");
  } finally {
    globalThis.fetch = originalFetch;
  }

  expect(calls.map((call) => call.path)).toEqual([
    "/api/supplier/carriers",
    "/api/supplier/orders/ORD%20104%2F1/shipments",
    "/api/supplier/orders/ORD%20104%2F1/shipments",
    "/api/supplier/orders/ORD%20104%2F1/shipments",
    "/api/supplier/orders/ORD%20104%2F1/shipments/shipment%2F1",
  ]);
  expect(calls.map((call) => call.init?.method ?? "GET")).toEqual(["GET", "GET", "POST", "POST", "PATCH"]);
  expect(JSON.parse(String(calls[2].init?.body))).toEqual({
    carrierCode: "CJ_LOGISTICS",
    trackingNumber: "first",
  });
  expect(JSON.parse(String(calls[3].init?.body))).toEqual({
    carrierCode: "CJ_LOGISTICS",
    trackingNumber: "split",
    allocations: [{ orderItemId: "item-1", quantity: 1 }],
  });
  expect(new Headers(calls[2].init?.headers).get("Idempotency-Key")).toBe("first-command");
  expect(new Headers(calls[4].init?.headers).get("Idempotency-Key")).toBe("correction-command");
  for (const call of calls) {
    expect(call.init?.cache).toBe("no-store");
    expect(call.init?.credentials).toBe("same-origin");
  }
});

test("supplier status and admin portal actions fail closed", () => {
  expect(supplierOrderStatusView("FULFILLMENT_REQUESTED").label).toBe("출고 요청");
  expect(supplierOrderStatusView("TRACKING_REGISTERED").label).toBe("송장 등록 · 배송조회 가능");
  expect(orderStatusLabel("TRACKING_REGISTERED")).toBe("송장 등록 · 배송조회 가능");
  expect(shipmentStatusLabel("TRACKING_REGISTERED")).toBe("송장 등록 · 배송조회 가능");
  expect(adminStatusLabel("TRACKING_REGISTERED")).toBe("송장 등록 · 배송조회 가능");
  expect(customerDirectCancelBlocked("TRACKING_REGISTERED")).toBe(true);
  expect(customerDirectCancelBlocked("SUPPLIER_ORDER_PENDING")).toBe(false);
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
