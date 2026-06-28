import { cookies } from "next/headers";
import { apiGetWithCookie } from "./api";
import { adminOrders, adminProducts, adminSuppliers } from "./admin-mock";

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

async function readWithFallback<T>(path: string, fallback: T, emptyFallback = false) {
  try {
    const data = await apiGetWithCookie<T>(path, (await cookies()).toString());
    if (emptyFallback && Array.isArray(data) && data.length === 0) return fallback;
    return data;
  } catch {
    return fallback;
  }
}

export async function getAdminProducts() {
  return readWithFallback<AdminProduct[]>("/api/admin/products", adminProducts, true);
}

export async function getAdminSuppliers() {
  return readWithFallback<AdminSupplier[]>("/api/admin/suppliers", adminSuppliers, true);
}

export async function getAdminOrders() {
  const data = await readWithFallback<AdminOrderListResponse>(
    "/api/admin/orders",
    { orders: adminOrders },
  );
  return data.orders.length > 0 ? data.orders : adminOrders;
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
