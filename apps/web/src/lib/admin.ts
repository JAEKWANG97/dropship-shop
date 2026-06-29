import { cookies } from "next/headers";
import { apiGetWithCookie } from "./api";

export type AdminProductStatus = "ACTIVE" | "SOLD_OUT" | "HIDDEN" | "STOPPED";

export type AdminProduct = {
  id: string;
  supplierId: string;
  supplierName: string;
  name: string;
  summary: string;
  basePrice: number;
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
  supplierName: string;
  customerEmail: string;
  checkoutNumber: string;
  totalAmount: number;
  createdAt: string;
  items?: { productName: string; optionName: string; quantity: number; unitPrice: number }[];
  shippingAddress?: string;
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
