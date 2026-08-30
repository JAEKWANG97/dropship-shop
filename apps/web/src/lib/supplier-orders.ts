export type SupplierOrderItemSummary = {
  productName: string;
  optionName: string;
  quantity: number;
};

export type SupplierOrderSummary = {
  orderNumber: string;
  status: string;
  requestedAt: string | null;
  items: SupplierOrderItemSummary[];
};

export type SupplierOrderItemDetail = SupplierOrderItemSummary & {
  orderItemId: string;
  allocatedQuantity: number;
  remainingQuantity: number;
};

export type SupplierOrderRecipient = {
  name: string | null;
  phone: string | null;
  postalCode: string | null;
  address1: string | null;
  address2: string | null;
  deliveryMemo: string | null;
};

export type SupplierOrderDetail = Omit<SupplierOrderSummary, "items"> & {
  piiAccessLevel: "FULL" | "MASKED";
  piiBasis: string | null;
  piiAccessUntil: string | null;
  recipient: SupplierOrderRecipient;
  items: SupplierOrderItemDetail[];
};

export type SupplierCarrier = {
  carrierCode: string;
  carrierName: string;
  officialTrackingSupported: boolean;
};

export type ShipmentAllocation = {
  orderItemId: string;
  quantity: number;
};

export type SupplierShipment = {
  shipmentId: string;
  version: number | null;
  status: string;
  carrierCode: string | null;
  carrierName: string;
  trackingNumber: string;
  officialTrackingUrl: string | null;
  editable: boolean;
  countsTowardAllocation: boolean;
  registeredAt: string | null;
  deliveredAt: string | null;
  allocations: ShipmentAllocation[];
};

export type SupplierUnallocatedItem = {
  orderItemId: string;
  productName: string;
  optionName: string;
  remainingQuantity: number;
};

export type SupplierShipmentCollection = {
  shipments: SupplierShipment[];
  unallocatedItems: SupplierUnallocatedItem[];
  allocationComplete: boolean | null;
  canRegisterShipment: boolean | null;
  canReportShortage: boolean | null;
  nextAction: string | null;
};

export type SupplierShipmentCreateInput = {
  carrierCode: string;
  trackingNumber: string;
  allocations?: ShipmentAllocation[];
};

export type SupplierShipmentCorrectionInput = {
  expectedVersion: number;
  carrierCode: string;
  trackingNumber: string;
  reason: string;
};

export class SupplierOrderApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
  ) {
    super(code || `API request failed: ${status}`);
  }
}

const STATUS_VIEWS: Record<string, { label: string; tone: "neutral" | "success" | "warning" }> = {
  FULFILLMENT_REQUESTED: { label: "출고 요청", tone: "warning" },
  TRACKING_REGISTERED: { label: "송장 등록 · 배송조회 가능", tone: "neutral" },
  DELIVERED: { label: "배송 완료", tone: "success" },
  CLOSED: { label: "처리 종료", tone: "neutral" },
  SHORTAGE_REPORTED: { label: "품절 확인 중", tone: "warning" },
};

export function supplierOrderStatusView(status: string) {
  return STATUS_VIEWS[status] ?? { label: "상태 확인 필요", tone: "warning" as const };
}

export function supplierShipmentRegistrationAllowed(value: SupplierShipmentCollection) {
  return value.canRegisterShipment === true;
}

export function supplierShortageReportingAllowed(value: SupplierShipmentCollection) {
  return value.canReportShortage === true;
}

export async function recoverSupplierShipmentConflict(
  error: unknown,
  refresh: () => Promise<void>,
) {
  if (!(error instanceof SupplierOrderApiError) || ![403, 404, 409].includes(error.status)) {
    return false;
  }
  await refresh();
  return true;
}

export function supplierShipmentCommandKey(
  keys: Map<string, string>,
  action: string,
  create: () => string = () => crypto.randomUUID(),
) {
  const current = keys.get(action);
  if (current) return current;
  const created = create();
  keys.set(action, created);
  return created;
}

export function releaseSupplierShipmentCommandKey(
  keys: Map<string, string>,
  action: string,
  error: unknown,
) {
  if (error instanceof SupplierOrderApiError && error.status < 500) keys.delete(action);
}

export const supplierCommandKey = supplierShipmentCommandKey;
export const releaseSupplierCommandKey = releaseSupplierShipmentCommandKey;

export async function loadSupplierShipmentRefresh(orderNumber: string) {
  const [orderResult, shipmentResult] = await Promise.allSettled([
    getSupplierOrder(orderNumber),
    listSupplierShipments(orderNumber),
  ]);
  return {
    order: orderResult.status === "fulfilled" ? orderResult.value : null,
    orderError: orderResult.status === "rejected" ? orderResult.reason : null,
    shipmentState: shipmentResult.status === "fulfilled" ? shipmentResult.value : null,
    shipmentError: shipmentResult.status === "rejected" ? shipmentResult.reason : null,
  };
}

export async function listSupplierOrders() {
  return normalizeSupplierOrderList(await supplierPortalRequest("/api/supplier/orders"));
}

export async function getSupplierOrder(orderNumber: string) {
  return normalizeSupplierOrderDetail(await supplierPortalRequest(`/api/supplier/orders/${encodeURIComponent(orderNumber)}`));
}

export async function listSupplierCarriers() {
  return normalizeSupplierCarriers(await supplierPortalRequest("/api/supplier/carriers"));
}

export async function listSupplierShipments(orderNumber: string) {
  return normalizeSupplierShipments(await supplierPortalRequest(
    `/api/supplier/orders/${encodeURIComponent(orderNumber)}/shipments`,
  ));
}

export async function createSupplierShipment(
  orderNumber: string,
  input: SupplierShipmentCreateInput,
  idempotencyKey: string,
) {
  return supplierPortalRequest(`/api/supplier/orders/${encodeURIComponent(orderNumber)}/shipments`, {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify(input),
  });
}

export async function correctSupplierShipment(
  orderNumber: string,
  shipmentId: string,
  input: SupplierShipmentCorrectionInput,
  idempotencyKey: string,
) {
  return supplierPortalRequest(
    `/api/supplier/orders/${encodeURIComponent(orderNumber)}/shipments/${encodeURIComponent(shipmentId)}`,
    {
      method: "PATCH",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(input),
    },
  );
}

export function normalizeSupplierOrderList(value: unknown): SupplierOrderSummary[] {
  const wrapper = record(value);
  return array(wrapper.orders ?? value)
    .map(normalizeSupplierOrderSummary)
    .filter((order) => order.orderNumber.length > 0);
}

export function normalizeSupplierOrderDetail(value: unknown): SupplierOrderDetail {
  const item = record(value);
  const recipient = record(item.recipient);
  const piiAccessLevel = item.piiAccessLevel === "FULL" ? "FULL" : "MASKED";
  const recognizedPiiLevel = item.piiAccessLevel === "FULL" || item.piiAccessLevel === "MASKED";
  return {
    ...normalizeSupplierOrderSummary(item),
    piiAccessLevel,
    piiBasis: nullableText(item.piiBasis),
    piiAccessUntil: nullableText(item.piiAccessUntil),
    recipient: {
      name: piiAccessLevel === "FULL"
        ? nullableText(recipient.name)
        : recognizedPiiLevel ? maskedName(recipient.name) : null,
      phone: piiAccessLevel === "FULL"
        ? nullableText(recipient.phone)
        : recognizedPiiLevel ? maskedPhone(recipient.phone) : null,
      postalCode: piiAccessLevel === "FULL" ? nullableText(recipient.postalCode) : null,
      address1: piiAccessLevel === "FULL" ? nullableText(recipient.address1) : null,
      address2: piiAccessLevel === "FULL" ? nullableText(recipient.address2) : null,
      deliveryMemo: piiAccessLevel === "FULL" ? nullableText(recipient.deliveryMemo) : null,
    },
    items: array(item.items).map((value) => {
      const orderItem = record(value);
      return {
        ...normalizeSupplierOrderItem(orderItem),
        orderItemId: text(orderItem.orderItemId),
        allocatedQuantity: nonNegativeInteger(orderItem.allocatedQuantity),
        remainingQuantity: nonNegativeInteger(orderItem.remainingQuantity),
      };
    }),
  };
}

export function normalizeSupplierCarriers(value: unknown): SupplierCarrier[] {
  const wrapper = record(value);
  return array(wrapper.carriers ?? value)
    .map((value) => {
      const carrier = record(value);
      return {
        carrierCode: text(carrier.carrierCode) || text(carrier.code),
        carrierName: text(carrier.carrierName) || text(carrier.displayName) || text(carrier.name),
        officialTrackingSupported: carrier.officialTrackingSupported === true
          || carrier.officialLinkSupported === true
          || carrier.trackingUrlSupported === true,
      };
    })
    .filter((carrier) => carrier.carrierCode && carrier.carrierName);
}

export function normalizeSupplierShipments(value: unknown): SupplierShipmentCollection {
  const wrapper = record(value);
  return {
    shipments: array(wrapper.shipments).map(normalizeSupplierShipment)
      .filter((shipment) => shipment.shipmentId),
    unallocatedItems: array(wrapper.unallocatedItems).map((value) => {
      const item = record(value);
      return {
        orderItemId: text(item.orderItemId),
        productName: text(item.productName),
        optionName: text(item.optionName),
        remainingQuantity: nonNegativeInteger(item.remainingQuantity ?? item.quantity),
      };
    }).filter((item) => item.orderItemId && item.remainingQuantity > 0),
    allocationComplete: typeof wrapper.allocationComplete === "boolean"
      ? wrapper.allocationComplete
      : null,
    canRegisterShipment: typeof wrapper.canRegisterShipment === "boolean"
      ? wrapper.canRegisterShipment
      : null,
    canReportShortage: typeof wrapper.canReportShortage === "boolean"
      ? wrapper.canReportShortage
      : null,
    nextAction: nullableText(wrapper.nextAction),
  };
}

function normalizeSupplierShipment(value: unknown): SupplierShipment {
  const shipment = record(value);
  const status = text(shipment.status);
  const version = nullableNonNegativeInteger(shipment.version);
  return {
    shipmentId: text(shipment.shipmentId),
    version,
    status,
    carrierCode: nullableText(shipment.carrierCode),
    carrierName: text(shipment.carrierName) || text(shipment.carrier),
    trackingNumber: text(shipment.trackingNumber),
    officialTrackingUrl: safeOfficialTrackingUrl(shipment.officialTrackingUrl),
    editable: shipment.editable === true && status === "TRACKING_REGISTERED" && version !== null,
    countsTowardAllocation: shipment.countsTowardAllocation !== false && status !== "VOIDED",
    registeredAt: nullableText(shipment.registeredAt),
    deliveredAt: nullableText(shipment.deliveredAt),
    allocations: array(shipment.allocations).map((value) => {
      const allocation = record(value);
      return {
        orderItemId: text(allocation.orderItemId),
        quantity: nonNegativeInteger(allocation.quantity),
      };
    }).filter((allocation) => allocation.orderItemId && allocation.quantity > 0),
  };
}

function normalizeSupplierOrderSummary(value: unknown): SupplierOrderSummary {
  const item = record(value);
  return {
    orderNumber: text(item.orderNumber),
    status: text(item.status),
    requestedAt: nullableText(item.requestedAt),
    items: array(item.items).map(normalizeSupplierOrderItem),
  };
}

function normalizeSupplierOrderItem(value: unknown): SupplierOrderItemSummary {
  const item = record(value);
  return {
    productName: text(item.productName),
    optionName: text(item.optionName),
    quantity: nonNegativeInteger(item.quantity),
  };
}

export async function supplierPortalRequest(path: string, init: RequestInit = {}) {
  const response = await fetch(path, {
    ...init,
    credentials: "same-origin",
    cache: "no-store",
    headers: {
      Accept: "application/json",
      ...(init.body ? { "Content-Type": "application/json" } : {}),
      ...init.headers,
    },
  });
  if (!response.ok) {
    let code = "";
    try {
      code = text(record(await response.json()).code);
    } catch {
      code = "";
    }
    throw new SupplierOrderApiError(response.status, code);
  }
  return response.json() as Promise<unknown>;
}

function record(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

function array(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function text(value: unknown) {
  return typeof value === "string" ? value : "";
}

function nullableText(value: unknown) {
  const result = text(value);
  return result || null;
}

export function safeOfficialTrackingUrl(value: unknown) {
  const result = nullableText(value);
  if (!result) return null;
  try {
    const url = new URL(result);
    return url.protocol === "https:" ? url.toString() : null;
  } catch {
    return null;
  }
}

function maskedName(value: unknown) {
  const points = Array.from(text(value));
  if (points.length === 1 && points[0] === "*") return "*";
  return points.length === 3 && points[1] === "*" && points[2] === "*"
    ? points.join("")
    : null;
}

function maskedPhone(value: unknown) {
  const result = text(value);
  return /^\*+$/.test(result) || /^\*+\d{4}$/.test(result) ? result : null;
}

function nonNegativeInteger(value: unknown) {
  return typeof value === "number" && Number.isInteger(value) && value >= 0 ? value : 0;
}

function nullableNonNegativeInteger(value: unknown) {
  return typeof value === "number" && Number.isInteger(value) && value >= 0 ? value : null;
}
