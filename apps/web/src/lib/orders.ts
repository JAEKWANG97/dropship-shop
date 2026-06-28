import { cookies } from "next/headers";
import { apiGetWithCookie } from "./api";

export type OrderSummary = {
  orderId: string;
  orderNumber: string;
  paymentGroupId: string;
  checkoutNumber: string;
  displayStatus: string;
  totalAmount: number;
  createdAt: string;
};

export type OrderList = {
  orders: OrderSummary[];
};

export type OrderItem = {
  orderItemId: string;
  productName: string;
  productSummary: string;
  optionName: string;
  quantity: number;
  unitPrice: number;
  lineAmount: number;
  productDetailVersion: number;
  productNoticeVersion: number | null;
};

export type ShippingAddress = {
  recipientName: string;
  recipientPhone: string;
  postalCode: string;
  address1: string;
  address2: string | null;
};

export type OrderDetail = {
  orderId: string;
  orderNumber: string;
  displayStatus: string;
  subtotalAmount: number;
  shippingFee: number;
  discountAmount: number;
  totalAmount: number;
  createdAt: string;
  paymentGroup: {
    paymentGroupId: string;
    checkoutNumber: string;
    displayStatus: string;
    totalAmount: number;
    approvedAmount: number | null;
    approvedAt: string | null;
  };
  payment: {
    paymentId: string | null;
    displayStatus: string;
    approvedAmount: number | null;
    approvedAt: string | null;
  };
  shippingAddress: ShippingAddress;
  items: OrderItem[];
  fulfillment: { displayStatus: string };
  shipment: {
    displayStatus: string;
    carrier: string | null;
    trackingNumber: string | null;
  };
  refund: {
    displayStatus: string;
    amount: number | null;
  };
};

export async function getCustomerOrders() {
  return apiGetWithCookie<OrderList>("/api/orders", (await cookies()).toString());
}

export async function getCustomerOrder(orderId: string) {
  return apiGetWithCookie<OrderDetail>(
    `/api/orders/${orderId}`,
    (await cookies()).toString(),
  );
}
