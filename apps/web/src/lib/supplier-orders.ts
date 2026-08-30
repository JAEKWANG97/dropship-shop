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

export async function listSupplierOrders() {
  return normalizeSupplierOrderList(await request("/api/supplier/orders"));
}

export async function getSupplierOrder(orderNumber: string) {
  return normalizeSupplierOrderDetail(await request(`/api/supplier/orders/${encodeURIComponent(orderNumber)}`));
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

async function request(path: string) {
  const response = await fetch(path, {
    credentials: "same-origin",
    cache: "no-store",
    headers: { Accept: "application/json" },
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
