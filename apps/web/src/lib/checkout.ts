import { cookies } from "next/headers";
import { apiGetWithCookie } from "./api";

export type CheckoutOrderItem = {
  id: string;
  productName: string;
  optionName: string;
  quantity: number;
  unitPrice: number;
  lineAmount: number;
  productDetailVersion: number;
  productNoticeVersion: number | null;
};

export type CheckoutOrder = {
  id: string;
  orderNumber: string;
  supplierId: string;
  deliveryGroupName: string;
  status: string;
  subtotalAmount: number;
  shippingFee: number;
  discountAmount: number;
  totalAmount: number;
  customerDisplayStatus?: string;
  customerDisplayLabel?: string;
  refundAmount?: number | null;
  items: CheckoutOrderItem[];
};

export type CheckoutPolicyLink = {
  label: string;
  href: string;
  policyType: string;
};

export type BankTransferDeposit = {
  bankName: string;
  accountNumber: string;
  accountHolder: string;
  depositorName: string;
  amount: number;
  deadline: string;
  cashReceiptNotice: string;
};

export type CheckoutShippingAddress = {
  recipientName: string;
  recipientPhone: string;
  postalCode: string;
  address1: string;
  address2: string | null;
};

export type CheckoutPolicyEvidence = {
  termsVersion: string;
  privacyVersion: string;
  orderPolicyVersion: string;
  cancellationRefundPolicyVersion: string;
  outOfStockNoticeVersion: string;
  confirmedNoticeText: string;
};

export type Checkout = {
  paymentGroupId: string;
  checkoutNumber: string;
  status: string;
  totalAmount: number;
  refundableAmount: number;
  customerDisplayStatus?: string;
  customerDisplayLabel?: string;
  refundAmount?: number | null;
  expiresAt: string;
  policyConfirmedAt: string | null;
  bankTransferDeposit: BankTransferDeposit;
  shippingAddress: CheckoutShippingAddress;
  policyEvidence: CheckoutPolicyEvidence;
  policyLinks: CheckoutPolicyLink[];
  orders: CheckoutOrder[];
};

export async function getCheckout(checkoutNumber: string) {
  return apiGetWithCookie<Checkout>(
    `/api/checkouts/${checkoutNumber}`,
    (await cookies()).toString(),
  );
}

export function checkoutCustomerProjection(checkout: Checkout) {
  const status = checkout.customerDisplayStatus || checkout.status;
  return {
    status,
    label: checkout.customerDisplayLabel
      || (status === "REFUND_PROCESSING" ? "입금 확인 및 환불 처리 중" : null),
    refundAmount: checkout.refundAmount ?? checkout.refundableAmount,
  };
}
