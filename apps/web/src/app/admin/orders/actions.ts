"use server";

import { revalidatePath } from "next/cache";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { ApiError, apiSendWithCookie } from "@/lib/api";
import { supplierMutationHeaders } from "@/lib/supplier";
import {
  ADMIN_DEPOSIT_PATHS,
  ADMIN_REFUND_PATHS,
  adminDepositCommand,
  idempotencyHeaders,
  koreanLocalDateTime,
  parseAdminExpectedVersion,
  refundApprovalCommand,
  uncertainAdminCommandKey,
} from "@/lib/admin-payment";

function value(formData: FormData, name: string) {
  const raw = formData.get(name);
  return typeof raw === "string" ? raw.trim() : "";
}

function done(orderId: string, message: string, retryAction?: string, idempotencyKey?: string): never {
  const search = new URLSearchParams({ orderId, message });
  if (retryAction && idempotencyKey) {
    search.set("retryAction", retryAction);
    search.set("idempotencyKey", idempotencyKey);
  }
  redirect(`/admin/orders?${search.toString()}`);
}

function failureMessage(error: unknown, fallback: string) {
  return error instanceof ApiError && error.message.trim() ? error.message : fallback;
}

async function postOrderAction(
  orderId: string,
  path: string,
  body: Record<string, unknown>,
  idempotencyKey?: string,
) {
  await apiSendWithCookie(`/api/admin/orders/${orderId}${path}`, (await cookies()).toString(), {
    method: "POST",
    headers: idempotencyHeaders(idempotencyKey ?? ""),
    body: JSON.stringify(body),
  });
}

async function postShipmentAction(shipmentId: string, path: string, body: Record<string, string>) {
  await apiSendWithCookie(`/api/admin/shipments/${shipmentId}${path}`, (await cookies()).toString(), {
    method: "POST",
    body: JSON.stringify(body),
  });
}

async function mutatePortalShipment(
  shipmentId: string,
  path: string,
  method: "POST" | "PATCH",
  body: Record<string, unknown>,
  idempotencyKey: string,
) {
  await apiSendWithCookie(`/api/admin/shipments/${shipmentId}${path}`, (await cookies()).toString(), {
    method,
    headers: idempotencyHeaders(idempotencyKey),
    body: JSON.stringify(body),
  });
}

async function postRefundAction(
  refundId: string,
  path: string,
  body: Record<string, string | number>,
  idempotencyKey?: string,
) {
  await apiSendWithCookie(`/api/admin/refunds/${refundId}${path}`, (await cookies()).toString(), {
    method: "POST",
    headers: idempotencyHeaders(idempotencyKey ?? ""),
    body: JSON.stringify(body),
  });
}

async function postClaimAction(claimId: string, path: string, body: Record<string, string>) {
  await apiSendWithCookie(`/api/admin/claims/${claimId}${path}`, (await cookies()).toString(), {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function startSupplierWork(formData: FormData) {
  const orderId = value(formData, "orderId");

  try {
    await postOrderAction(orderId, "/supplier-work-start", { reason: value(formData, "reason") });
  } catch (error) {
    done(orderId, failureMessage(error, "발주 시작 처리에 실패했습니다."));
  }

  revalidatePath("/admin/orders");
  done(orderId, "발주 시작 처리했습니다.");
}

export async function completeSupplierOrder(formData: FormData) {
  const orderId = value(formData, "orderId");

  try {
    await postOrderAction(orderId, "/supplier-order-completed", {
      supplierOrderNumber: value(formData, "supplierOrderNumber"),
      expectedShipDate: value(formData, "expectedShipDate"),
      supplierResponseMemo: value(formData, "supplierResponseMemo"),
      reason: value(formData, "reason"),
    });
  } catch (error) {
    done(orderId, failureMessage(error, "공급처 발주 완료 처리에 실패했습니다."));
  }

  revalidatePath("/admin/orders");
  done(orderId, "공급처 발주 완료 처리했습니다.");
}

export async function markOrderOutOfStock(formData: FormData) {
  const orderId = value(formData, "orderId");

  try {
    await postOrderAction(orderId, "/out-of-stock", { reason: value(formData, "reason") });
  } catch (error) {
    done(orderId, failureMessage(error, "공급처 품절 처리에 실패했습니다."));
  }

  revalidatePath("/admin/orders");
  done(orderId, "공급처 품절 처리했습니다.");
}

export async function createOrderShipment(formData: FormData) {
  const orderId = value(formData, "orderId");

  try {
    await postOrderAction(orderId, "/shipments", {
      carrier: value(formData, "carrier"),
      trackingNumber: value(formData, "trackingNumber"),
    });
  } catch (error) {
    done(orderId, failureMessage(error, "송장 입력에 실패했습니다."));
  }

  revalidatePath("/admin/orders");
  done(orderId, "송장을 입력했습니다.");
}

export async function createPortalShipment(formData: FormData) {
  const orderId = value(formData, "orderId");
  const idempotencyKey = value(formData, "idempotencyKey");
  const allocations = shipmentAllocations(formData);

  try {
    await apiSendWithCookie(`/api/admin/orders/${orderId}/portal-shipments`, (await cookies()).toString(), {
      method: "POST",
      headers: idempotencyHeaders(idempotencyKey),
      body: JSON.stringify({
        carrierCode: value(formData, "carrierCode"),
        trackingNumber: value(formData, "trackingNumber"),
        ...(allocations.length > 0 ? { allocations } : {}),
      }),
    });
  } catch (error) {
    done(
      orderId,
      failureMessage(error, "포털 송장 등록에 실패했습니다."),
      "portal-shipment-create",
      uncertainAdminCommandKey(error, idempotencyKey),
    );
  }

  revalidatePath("/admin/orders");
  done(orderId, "포털 송장을 등록했습니다.");
}

export async function correctPortalShipmentTracking(formData: FormData) {
  const orderId = value(formData, "orderId");
  const shipmentId = value(formData, "shipmentId");
  const idempotencyKey = value(formData, "idempotencyKey");
  const retryAction = `portal-shipment-tracking-${shipmentId}`;
  const expectedVersion = parseAdminExpectedVersion(value(formData, "expectedVersion"));
  if (expectedVersion === null) done(orderId, "송장 버전 정보를 확인할 수 없습니다. 주문을 새로고침해 주세요.");

  try {
    await mutatePortalShipment(shipmentId, "/tracking-correction", "PATCH", {
      expectedVersion,
      carrierCode: value(formData, "carrierCode"),
      trackingNumber: value(formData, "trackingNumber"),
      reason: value(formData, "reason"),
    }, idempotencyKey);
  } catch (error) {
    done(
      orderId,
      failureMessage(error, "포털 송장 정정에 실패했습니다."),
      retryAction,
      uncertainAdminCommandKey(error, idempotencyKey),
    );
  }

  revalidatePath("/admin/orders");
  done(orderId, "택배사와 송장번호를 정정했습니다.");
}

export async function voidPortalShipment(formData: FormData) {
  const orderId = value(formData, "orderId");
  const shipmentId = value(formData, "shipmentId");
  const idempotencyKey = value(formData, "idempotencyKey");
  const retryAction = `portal-shipment-void-${shipmentId}`;
  const expectedVersion = parseAdminExpectedVersion(value(formData, "expectedVersion"));
  if (expectedVersion === null) done(orderId, "송장 버전 정보를 확인할 수 없습니다. 주문을 새로고침해 주세요.");

  try {
    await mutatePortalShipment(shipmentId, "/void", "POST", {
      expectedVersion,
      reason: value(formData, "reason"),
    }, idempotencyKey);
  } catch (error) {
    done(
      orderId,
      failureMessage(error, "포털 송장 무효 처리에 실패했습니다."),
      retryAction,
      uncertainAdminCommandKey(error, idempotencyKey),
    );
  }

  revalidatePath("/admin/orders");
  done(orderId, "송장을 무효 처리하고 할당 수량을 되돌렸습니다.");
}

export async function completePortalShipmentDelivery(formData: FormData) {
  const orderId = value(formData, "orderId");
  const shipmentId = value(formData, "shipmentId");
  const idempotencyKey = value(formData, "idempotencyKey");
  const retryAction = `portal-shipment-delivery-${shipmentId}`;
  const expectedVersion = parseAdminExpectedVersion(value(formData, "expectedVersion"));
  if (expectedVersion === null) done(orderId, "송장 버전 정보를 확인할 수 없습니다. 주문을 새로고침해 주세요.");

  try {
    await mutatePortalShipment(shipmentId, "/delivery-complete", "POST", {
      expectedVersion,
      deliveredAt: koreanLocalDateTime(value(formData, "deliveredAt")),
      evidenceObservedAt: koreanLocalDateTime(value(formData, "evidenceObservedAt")),
      reason: value(formData, "reason"),
    }, idempotencyKey);
  } catch (error) {
    done(
      orderId,
      failureMessage(error, "배송완료 증적 반영에 실패했습니다."),
      retryAction,
      uncertainAdminCommandKey(error, idempotencyKey),
    );
  }

  revalidatePath("/admin/orders");
  done(orderId, "배송완료 증적을 반영했습니다.");
}

export async function correctPortalShipmentDelivery(formData: FormData) {
  const orderId = value(formData, "orderId");
  const shipmentId = value(formData, "shipmentId");
  const idempotencyKey = value(formData, "idempotencyKey");
  const correctionType = value(formData, "correctionType");
  const retryAction = correctionType === "REOPEN_TRACKING"
    ? `portal-shipment-reopen-${shipmentId}`
    : `portal-shipment-delivery-time-${shipmentId}`;
  const timeCorrection = correctionType === "CORRECT_DELIVERED_AT";
  const expectedVersion = parseAdminExpectedVersion(value(formData, "expectedVersion"));
  if (expectedVersion === null) done(orderId, "송장 버전 정보를 확인할 수 없습니다. 주문을 새로고침해 주세요.");

  try {
    await mutatePortalShipment(shipmentId, "/delivery-correction", "POST", {
      expectedVersion,
      correctionType,
      ...(timeCorrection ? {
        correctedDeliveredAt: koreanLocalDateTime(value(formData, "correctedDeliveredAt")),
        evidenceObservedAt: koreanLocalDateTime(value(formData, "evidenceObservedAt")),
      } : {}),
      reason: value(formData, "reason"),
    }, idempotencyKey);
  } catch (error) {
    done(
      orderId,
      failureMessage(error, "배송완료 보정에 실패했습니다."),
      retryAction,
      uncertainAdminCommandKey(error, idempotencyKey),
    );
  }

  revalidatePath("/admin/orders");
  done(orderId, timeCorrection ? "배송완료 시각을 정정했습니다." : "배송완료 처리를 취소하고 배송조회 상태로 되돌렸습니다.");
}

function shipmentAllocations(formData: FormData) {
  return Array.from(formData.entries()).flatMap(([name, raw]) => {
    if (!name.startsWith("allocation:") || typeof raw !== "string") return [];
    const quantity = Number(raw);
    return Number.isInteger(quantity) && quantity > 0
      ? [{ orderItemId: name.slice("allocation:".length), quantity }]
      : [];
  });
}

export async function syncShipmentTracking(formData: FormData) {
  const orderId = value(formData, "orderId");
  const shipmentId = value(formData, "shipmentId");
  const failureReason = value(formData, "failureReason");
  const trackingStatus = value(formData, "trackingStatus");

  try {
    await postShipmentAction(shipmentId, "/tracking-sync", failureReason ? { failureReason } : { trackingStatus });
  } catch (error) {
    done(orderId, failureMessage(error, "배송조회 결과 반영에 실패했습니다."));
  }

  revalidatePath("/admin/orders");
  done(orderId, failureReason ? "배송조회 실패 사유를 기록했습니다." : "배송조회 결과를 반영했습니다.");
}

export async function correctShipmentDelivered(formData: FormData) {
  const orderId = value(formData, "orderId");
  const shipmentId = value(formData, "shipmentId");

  try {
    await postShipmentAction(shipmentId, "/manual-correction", {
      status: "DELIVERED",
      reason: value(formData, "reason"),
    });
  } catch (error) {
    done(orderId, failureMessage(error, "수동 배송완료 보정에 실패했습니다."));
  }

  revalidatePath("/admin/orders");
  done(orderId, "수동 배송완료 보정을 반영했습니다.");
}

export async function confirmDeposit(formData: FormData) {
  const orderId = value(formData, "orderId");
  const idempotencyKey = value(formData, "idempotencyKey");

  try {
    await postOrderAction(orderId, ADMIN_DEPOSIT_PATHS.confirm, adminDepositCommand(formData), idempotencyKey);
  } catch (error) {
    done(
      orderId,
      failureMessage(error, "입금 확인 처리에 실패했습니다."),
      "confirm-deposit",
      uncertainAdminCommandKey(error, idempotencyKey),
    );
  }

  revalidatePath("/admin/orders");
  done(orderId, "입금 확인 처리했습니다.");
}

export async function cancelUnpaidDeposit(formData: FormData) {
  const orderId = value(formData, "orderId");

  try {
    await postOrderAction(orderId, "/unpaid-cancel", { reason: value(formData, "reason") });
  } catch (error) {
    done(orderId, failureMessage(error, "미입금 취소 처리에 실패했습니다."));
  }

  revalidatePath("/admin/orders");
  done(orderId, "미입금 취소 처리했습니다.");
}

export async function recordDepositMismatch(formData: FormData) {
  const orderId = value(formData, "orderId");
  const idempotencyKey = value(formData, "idempotencyKey");

  try {
    await postOrderAction(orderId, ADMIN_DEPOSIT_PATHS.mismatch, adminDepositCommand(formData), idempotencyKey);
  } catch (error) {
    done(
      orderId,
      failureMessage(error, "입금 불일치 처리에 실패했습니다."),
      "deposit-mismatch",
      uncertainAdminCommandKey(error, idempotencyKey),
    );
  }

  revalidatePath("/admin/orders");
  done(orderId, "입금 불일치와 결제그룹 환불 요청을 기록했습니다.");
}

export async function recordLateDeposit(formData: FormData) {
  const orderId = value(formData, "orderId");
  const idempotencyKey = value(formData, "idempotencyKey");

  try {
    await postOrderAction(orderId, ADMIN_DEPOSIT_PATHS.late, adminDepositCommand(formData), idempotencyKey);
  } catch (error) {
    done(
      orderId,
      failureMessage(error, "뒤늦은 입금 처리에 실패했습니다."),
      "late-deposit",
      uncertainAdminCommandKey(error, idempotencyKey),
    );
  }

  revalidatePath("/admin/orders");
  done(orderId, "뒤늦게 확인한 입금을 처리했습니다.");
}

export async function validateSupplierPurchase(formData: FormData) {
  const orderId = value(formData, "orderId");
  try {
    await postOrderAction(orderId, "/supplier-order/validate", {});
  } catch (error) {
    done(orderId, failureMessage(error, "공급처 주문 검증에 실패했습니다."));
  }
  revalidatePath("/admin/orders");
  done(orderId, "공급처 재고·가격·배송비를 검증했습니다.");
}

export async function retrySupplierPurchase(formData: FormData) {
  const orderId = value(formData, "orderId");
  try {
    await postOrderAction(orderId, "/supplier-order/retry", {});
  } catch (error) {
    done(orderId, failureMessage(error, "자동 발주 재시도 등록에 실패했습니다."));
  }
  revalidatePath("/admin/orders");
  done(orderId, "자동 발주 재시도를 등록했습니다.");
}

export async function reconcileSupplierPurchase(formData: FormData) {
  const orderId = value(formData, "orderId");
  try {
    await postOrderAction(orderId, "/supplier-order/reconcile", {});
  } catch (error) {
    done(orderId, failureMessage(error, "공급처 주문 대사에 실패했습니다."));
  }
  revalidatePath("/admin/orders");
  done(orderId, "공급처 주문 대사를 완료했습니다.");
}

export async function cancelSupplierPurchase(formData: FormData) {
  const orderId = value(formData, "orderId");
  try {
    await postOrderAction(orderId, "/supplier-order/cancel", { reason: value(formData, "reason") });
  } catch (error) {
    done(orderId, failureMessage(error, "공급처 주문 취소 요청에 실패했습니다."));
  }
  revalidatePath("/admin/orders");
  done(orderId, "공급처 주문 취소를 요청했습니다.");
}

export async function takeOverPortalFulfillment(formData: FormData) {
  const orderId = value(formData, "orderId");
  const idempotencyKey = value(formData, "idempotencyKey");

  try {
    await apiSendWithCookie(`/api/admin/orders/${orderId}/portal-takeover`, (await cookies()).toString(), {
      method: "POST",
      headers: supplierMutationHeaders(idempotencyKey),
      body: JSON.stringify({ reason: value(formData, "reason") }),
    });
  } catch (error) {
    done(
      orderId,
      failureMessage(error, "Coreable 인계 처리에 실패했습니다."),
      "portal-takeover",
      uncertainAdminCommandKey(error, idempotencyKey),
    );
  }

  revalidatePath("/admin/orders");
  done(orderId, "출고 요청을 Coreable 처리로 인계했습니다.");
}

export async function completeManualRefund(formData: FormData) {
  const orderId = value(formData, "orderId");
  const refundId = value(formData, "refundId");
  const idempotencyKey = value(formData, "idempotencyKey");

  try {
    await postRefundAction(refundId, ADMIN_REFUND_PATHS.manualComplete, {
      transferredAmount: Number(value(formData, "transferredAmount")),
      reason: value(formData, "reason"),
      bankName: value(formData, "bankName"),
      accountNumber: value(formData, "accountNumber"),
      accountHolder: value(formData, "accountHolder"),
		transferredAt: koreanLocalDateTime(value(formData, "transferredAt")),
		transactionReference: value(formData, "transactionReference"),
    }, idempotencyKey);
  } catch (error) {
    done(
      orderId,
      failureMessage(error, "수동 환불 완료 처리에 실패했습니다."),
      `manual-refund-${refundId}`,
      uncertainAdminCommandKey(error, idempotencyKey),
    );
  }

  revalidatePath("/admin/orders");
  done(orderId, "수동 환불 완료를 기록했습니다.");
}

export async function approveRefund(formData: FormData) {
  const orderId = value(formData, "orderId");
  const refundId = value(formData, "refundId");

  try {
    await postRefundAction(refundId, ADMIN_REFUND_PATHS.approve, refundApprovalCommand(formData));
  } catch (error) {
    done(orderId, failureMessage(error, "환불 승인 처리에 실패했습니다."));
  }

  revalidatePath("/admin/orders");
  done(orderId, "환불 요청을 승인했습니다. 실제 이체 후 수동 환불 완료를 기록하세요.");
}

export async function recordReturnReceived(formData: FormData) {
  const orderId = value(formData, "orderId");
  const claimId = value(formData, "claimId");

  try {
    await postClaimAction(claimId, "/return-received", { memo: value(formData, "memo") });
  } catch (error) {
    done(orderId, failureMessage(error, "반품 수령 기록에 실패했습니다."));
  }

  revalidatePath("/admin/orders");
  done(orderId, "반품 수령을 기록했습니다.");
}

export async function startReturnRefund(formData: FormData) {
  const orderId = value(formData, "orderId");
  const claimId = value(formData, "claimId");

  try {
    await postClaimAction(claimId, "/return-refund", { reason: value(formData, "reason") });
  } catch (error) {
    done(orderId, failureMessage(error, "반품 환불 시작에 실패했습니다."));
  }

  revalidatePath("/admin/orders");
  done(orderId, "반품 환불을 시작했습니다.");
}

export async function rejectClaim(formData: FormData) {
  const orderId = value(formData, "orderId");
  const claimId = value(formData, "claimId");

  try {
    await postClaimAction(claimId, "/reject", { reason: value(formData, "reason") });
  } catch (error) {
    done(orderId, failureMessage(error, "클레임 거부 처리에 실패했습니다."));
  }

  revalidatePath("/admin/orders");
  done(orderId, "클레임을 거부 처리했습니다.");
}
