import { cookies } from "next/headers";
import { apiGetWithCookie } from "./api";
import type { ProductCategoryCode } from "./categories";
import type { ProductComplianceStatus, ProductDetail, ProductOptionStatus, SaleBlocker } from "./catalog";

export type AdminProductStatus = "ACTIVE" | "SOLD_OUT" | "HIDDEN" | "STOPPED";

export type AdminProduct = {
  id: string;
  supplierId: string;
  supplierName: string;
  name: string;
  summary: string;
  sourcePrice: number;
  sourceItemNo: string | null;
  sourceUrl: string | null;
  basePrice: number;
  categoryCode: ProductCategoryCode;
  status: AdminProductStatus;
  complianceStatus: ProductComplianceStatus;
  thumbnailImageUrl: string | null;
  detailVersion: number;
  saleReady: boolean;
  saleBlockers: SaleBlocker[];
  optionCount: number;
  hasThumbnail: boolean;
  hasProductNotice: boolean;
  hasDetailContent: boolean;
};

export type AdminProductPage = {
  products: AdminProduct[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
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
  itemCount: number;
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
      actualDepositorName: string | null;
      actualDepositAmount: number | null;
      depositReceivedAt: string | null;
      depositTransactionReference: string | null;
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
    fulfillmentId: string | null;
    status: string;
    supplierOrderStartedAt: string | null;
    supplierOrderNumber: string | null;
    expectedShipDate: string | null;
    purchaseProvider: string | null;
    purchaseStatus: string | null;
    expectedSourceAmount: number | null;
    actualSourceAmount: number | null;
    lastPurchaseError: string | null;
    purchaseSyncedAt: string | null;
    supplierCancelStatus: string | null;
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
		manualRefundBankName?: string | null;
		manualRefundAccountNumber?: string | null;
		manualRefundAccountHolder?: string | null;
		manualRefundTransferredAt?: string | null;
		manualRefundTransactionReference?: string | null;
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

export type AdminOrderActionHistory = {
  actionHistoryId: string;
  orderId: string;
  adminUserId: string;
  actionType: string;
  beforeStatus: string;
  afterStatus: string;
  reason: string;
  createdAt: string;
};

async function readAdmin<T>(path: string) {
	return apiGetWithCookie<T>(path, (await cookies()).toString());
}

export async function getAdminProducts(params: {
  q?: string;
  status?: string;
  category?: string;
  supplierId?: string;
  readiness?: string;
  page?: number;
  size?: number;
} = {}) {
  const query = new URLSearchParams();
  if (params.q) query.set("q", params.q);
  if (params.status) query.set("status", params.status);
  if (params.category) query.set("category", params.category);
  if (params.supplierId) query.set("supplierId", params.supplierId);
  if (params.readiness) query.set("readiness", params.readiness);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? 20));
	return readAdmin<AdminProductPage>(`/api/admin/products?${query}`);
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

export async function getAdminOrderActions(orderId: string) {
	const data = await readAdmin<{ actions: AdminOrderActionHistory[] }>(`/api/admin/actions?orderId=${encodeURIComponent(orderId)}`);
	return data.actions;
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
