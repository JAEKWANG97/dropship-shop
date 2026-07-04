import { cookies } from "next/headers";
import { apiGetWithCookie } from "./api";
import type { ProductCategoryCode } from "./categories";
import type { ProductDetail, ProductOptionStatus } from "./catalog";

export type AdminProductStatus = "ACTIVE" | "SOLD_OUT" | "HIDDEN" | "STOPPED";

export type AdminProduct = {
  id: string;
  supplierId: string;
  supplierName: string;
  name: string;
  summary: string;
  sourcePrice: number;
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

export type AdminProductDetail = ProductDetail;

export type PricingPolicy = {
  id: string | null;
  name: string;
  commissionRate: number;
  taxBufferRate: number;
  overheadRate: number;
  safetyMarginRate: number;
  roundingUnit: number;
  totalMarkupRate: number;
};

export type AdminProductChange = {
  changeId: string;
  productOptionId: string | null;
  adminUserId: string;
  changeType: string;
  beforeValue: string | null;
  afterValue: string | null;
  reason: string;
  createdAt: string;
};

export type AdminReferral = {
  referrerUserId: string;
  referrerDisplayName: string;
  referralCode: string;
  referredUserId: string;
  referredDisplayName: string;
  referredAt: string;
};

type AdminProductChangesResponse = {
  changes: AdminProductChange[];
};

type AdminReferralListResponse = {
  referrals: AdminReferral[];
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
    bankTransferDeposit: {
      bankName: string | null;
      accountNumber: string | null;
      accountHolder: string | null;
      depositorName: string | null;
      cashReceiptNotice: string | null;
      depositConfirmedByAdminId: string | null;
      depositConfirmedAt: string | null;
      depositConfirmationReason: string | null;
      depositMismatchMemo: string | null;
      depositMismatchRecordedByAdminId: string | null;
      depositMismatchRecordedAt: string | null;
      unpaidCancelledByAdminId: string | null;
      unpaidCancelledAt: string | null;
      unpaidCancelReason: string | null;
    } | null;
  };
  payment?: { provider: string | null; status: string; method: string | null; approvedAmount: number | null };
  fulfillment?: {
    status: string;
    supplierOrderStartedAt: string | null;
    supplierOrderNumber: string | null;
    expectedShipDate: string | null;
  } | null;
  shipment?: {
    shipmentId: string;
    status: string;
    carrier: string;
    trackingNumber: string;
    shippedAt: string | null;
    deliveredAt: string | null;
    trackingSyncedAt: string | null;
    trackingSyncFailureReason: string | null;
    manualOverride: boolean;
    manualCorrectedByAdminId: string | null;
    manualCorrectedAt: string | null;
    manualCorrectionReason: string | null;
  } | null;
  refund?: {
    refundId: string;
    reason?: string;
    status: string;
    refundAmount: number;
    failureMessage: string | null;
    manualRefundedAt?: string | null;
    manualRefundReason?: string | null;
  } | null;
  claim?: {
    claimId: string;
    claimType: string;
    claimReason: string;
    status: string;
    requestedAction: string;
    customerMemo: string;
    reviewedByAdminId: string | null;
    adminReviewReason: string | null;
    reviewedAt: string | null;
    returnReceivedByAdminId: string | null;
    returnReceivedAt: string | null;
    returnReceivedMemo: string | null;
    refundId: string | null;
    completedAt: string | null;
    createdAt: string;
    evidenceFiles: {
      evidenceId: string;
      fileUrl: string;
      originalFilename: string | null;
      contentType: string;
      sizeBytes: number;
      uploadedAt: string;
    }[];
  } | null;
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

export async function getAdminProduct(productId: string) {
	return readAdmin<AdminProductDetail>(`/api/admin/products/${productId}`);
}

export async function getAdminProductChanges(productId: string) {
	const data = await readAdmin<AdminProductChangesResponse>(`/api/admin/products/${productId}/changes`);
	return data.changes;
}

export async function getAdminPricingPolicy() {
	return readAdmin<PricingPolicy>("/api/admin/pricing-policy");
}

export async function getAdminSuppliers() {
	return readAdmin<AdminSupplier[]>("/api/admin/suppliers");
}

export async function getAdminOrders(status?: string) {
  const path = status ? `/api/admin/orders?status=${encodeURIComponent(status)}` : "/api/admin/orders";
	const data = await readAdmin<AdminOrderListResponse>(path);
	return data.orders;
}

export async function getAdminReferrals() {
	const data = await readAdmin<AdminReferralListResponse>("/api/admin/referrals");
	return data.referrals;
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
      PAYMENT_PENDING: "입금대기",
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

export function adminOptionStatusLabel(status: ProductOptionStatus) {
  return adminStatusLabel(status);
}
