import { cookies } from "next/headers";
import { apiGetWithCookie } from "./api";
import type { ProductCategoryCode } from "./categories";

export type AdminProductStatus = "ACTIVE" | "SOLD_OUT" | "HIDDEN" | "STOPPED";

export type AdminProduct = {
  id: string;
  supplierId: string;
  supplierName: string;
  name: string;
  summary: string;
  basePrice: number;
  categoryCode: ProductCategoryCode;
  status: AdminProductStatus;
  thumbnailImageUrl: string | null;
  detailVersion: number;
};

export type AdminSupplier = {
  id: string;
  name: string;
};

export type AdminOrder = {
  orderId: string;
  orderNumber: string;
  status: string;
  supplier?: { name: string };
  customer?: { email: string; displayName: string | null };
  supplierName: string;
  customerEmail: string;
  checkoutNumber: string;
  totalAmount: number;
  createdAt: string;
  paymentGroup?: {
    checkoutNumber: string;
    status: string;
    totalAmount: number;
    approvedAmount: number | null;
    approvedAt: string | null;
  };
  payment?: { status: string; method: string | null; approvedAmount: number | null };
  fulfillment?: {
    status: string;
    supplierOrderStartedAt: string | null;
    supplierOrderNumber: string | null;
    expectedShipDate: string | null;
  } | null;
  shipment?: { status: string; carrier: string; trackingNumber: string; shippedAt: string | null } | null;
  refund?: { status: string; refundAmount: number; failureMessage: string | null } | null;
  items?: { productName: string; optionName: string; quantity: number; unitPrice: number }[];
  shippingAddress?: string | {
    recipientName: string;
    recipientPhone: string;
    postalCode: string;
    address1: string;
    address2: string | null;
  };
  paymentMethod?: string;
};

type AdminOrderListResponse = {
  orders: AdminOrder[];
};

async function readAdmin<T>(path: string) {
	return apiGetWithCookie<T>(path, (await cookies()).toString());
}

export async function getAdminProducts() {
	return readAdmin<AdminProduct[]>("/api/admin/products");
}

export async function getAdminSuppliers() {
	return readAdmin<AdminSupplier[]>("/api/admin/suppliers");
}

export async function getAdminOrders() {
	const data = await readAdmin<AdminOrderListResponse>("/api/admin/orders");
	return data.orders;
}

export async function getAdminOrder(orderId: string) {
	return readAdmin<AdminOrder>(`/api/admin/orders/${orderId}`);
}

export function adminStatusLabel(status: string) {
  return (
    {
      ACTIVE: "판매중",
      SOLD_OUT: "품절",
      HIDDEN: "숨김",
      STOPPED: "판매중지",
      PAYMENT_PENDING: "결제대기",
      SUPPLIER_ORDER_PENDING: "발주대기",
      SUPPLIER_ORDERED: "발주완료",
      SHIPPED: "배송중",
      DELIVERED: "배송완료",
      OUT_OF_STOCK: "품절",
      REFUND_REQUESTED: "환불중",
      REFUNDED: "환불완료",
      CANCELLED: "취소완료",
    }[status] ?? status
  );
}
