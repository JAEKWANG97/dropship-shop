import { cookies } from "next/headers";
import { apiGetWithCookie } from "./api";

export type OrderSummary = {
  orderId: string;
  orderNumber: string;
  paymentGroupId: string;
  checkoutNumber: string;
  status: string;
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
  status: string;
  subtotalAmount: number;
  shippingFee: number;
  discountAmount: number;
  totalAmount: number;
  createdAt: string;
  paymentGroup: {
    paymentGroupId: string;
    checkoutNumber: string;
    status: string;
    totalAmount: number;
    approvedAmount: number | null;
    approvedAt: string | null;
  };
  payment: {
    paymentId: string | null;
    status: string | null;
    approvedAmount: number | null;
    approvedAt: string | null;
  };
  shippingAddress: ShippingAddress;
  items: OrderItem[];
  fulfillment: { status: string };
  shipment: {
    status: string;
    carrier: string | null;
    trackingNumber: string | null;
  };
  refund: {
    status: string | null;
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

export function orderStatusLabel(status: string) {
  return (
    {
      PAYMENT_EXCEPTION: "결제 확인 중",
      SUPPLIER_ORDER_PENDING: "결제 완료",
      SUPPLIER_ORDERED: "상품 준비 중",
      SHIPPED: "배송 중",
      DELIVERED: "배송 완료",
      OUT_OF_STOCK: "품절 안내",
      CANCELLED: "취소 완료",
      REFUND_REQUESTED: "환불 처리 중",
      REFUNDED: "환불 완료",
    }[status] ?? status
  );
}

export function paymentGroupStatusLabel(status: string) {
  return (
    {
      PAYMENT_PENDING: "결제 대기",
      APPROVED: "결제 완료",
      PARTIALLY_REFUNDED: "부분 환불",
      REFUNDED: "환불 완료",
      PAYMENT_EXCEPTION: "결제 확인 중",
      EXPIRED: "주문 만료",
      CANCELLED: "취소 완료",
      CANCEL_FAILED: "결제 취소 확인 필요",
    }[status] ?? status
  );
}

export function paymentStatusLabel(status: string | null) {
  if (!status) return "결제 정보 없음";
  return (
    {
      READY: "결제 대기",
      APPROVED: "결제 완료",
      FAILED: "결제 실패",
      CANCEL_REQUIRED: "결제 확인 중",
      CANCEL_REQUESTED: "결제 확인 중",
      CANCELLED: "결제 취소 완료",
      CANCEL_FAILED: "결제 확인 중",
      REFUND_REQUESTED: "환불 처리 중",
      PARTIALLY_REFUNDED: "환불 처리 중",
      REFUNDED: "환불 처리 중",
      REFUND_FAILED: "환불 처리 중",
      REVIEW_REQUIRED: "결제 확인 중",
    }[status] ?? status
  );
}

export function fulfillmentStatusLabel(status: string) {
  return ({ PENDING: "발주 대기", ORDERED: "발주 완료", OUT_OF_STOCK: "품절", CANCELLED: "취소" }[status] ?? status);
}

export function shipmentStatusLabel(status: string) {
  return ({ READY: "배송 전", IN_TRANSIT: "배송 중", SHIPPED: "배송 중", DELIVERED: "배송 완료" }[status] ?? status);
}

export function refundStatusLabel(status: string | null) {
  if (!status) return "환불 없음";
  return (
    {
      REQUESTED: "환불 처리 중",
      APPROVED: "환불 처리 중",
      PG_CANCEL_REQUESTED: "환불 처리 중",
      PROCESSING: "환불 처리 중",
      COMPLETED: "환불 완료",
      FAILED: "환불 확인 중",
      RETRY_REQUIRED: "환불 확인 중",
      MANUAL_REVIEW_REQUIRED: "환불 확인 중",
      REJECTED: "환불 거절",
    }[status] ?? status
  );
}
