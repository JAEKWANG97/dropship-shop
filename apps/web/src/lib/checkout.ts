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

export type Checkout = {
  paymentGroupId: string;
  checkoutNumber: string;
  status: string;
  totalAmount: number;
  refundableAmount: number;
  expiresAt: string;
  policyConfirmedAt: string | null;
  bankTransferDeposit: BankTransferDeposit;
  policyLinks: CheckoutPolicyLink[];
  orders: CheckoutOrder[];
};

export async function getCheckout(checkoutNumber: string) {
  return apiGetWithCookie<Checkout>(
    `/api/checkouts/${checkoutNumber}`,
    (await cookies()).toString(),
  );
}
