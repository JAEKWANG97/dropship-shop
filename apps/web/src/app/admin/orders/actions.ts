"use server";

import { revalidatePath } from "next/cache";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { ApiError, apiSendWithCookie } from "@/lib/api";

function value(formData: FormData, name: string) {
  const raw = formData.get(name);
  return typeof raw === "string" ? raw.trim() : "";
}

function done(orderId: string, message: string) {
  redirect(`/admin/orders?orderId=${encodeURIComponent(orderId)}&message=${encodeURIComponent(message)}`);
}

function failureMessage(error: unknown, fallback: string) {
  return error instanceof ApiError && error.message.trim() ? error.message : fallback;
}

async function postOrderAction(orderId: string, path: string, body: Record<string, string | number>) {
  await apiSendWithCookie(`/api/admin/orders/${orderId}${path}`, (await cookies()).toString(), {
    method: "POST",
    body: JSON.stringify(body),
  });
}

async function postShipmentAction(shipmentId: string, path: string, body: Record<string, string>) {
  await apiSendWithCookie(`/api/admin/shipments/${shipmentId}${path}`, (await cookies()).toString(), {
    method: "POST",
    body: JSON.stringify(body),
  });
}

async function postRefundAction(refundId: string, path: string, body: Record<string, string | number>) {
  await apiSendWithCookie(`/api/admin/refunds/${refundId}${path}`, (await cookies()).toString(), {
    method: "POST",
    body: JSON.stringify(body),
  });
}

function koreanLocalDateTime(value: string) {
  return value ? `${value}:00+09:00` : "";
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

  try {
    await postOrderAction(orderId, "/confirm-deposit", {
      actualDepositorName: value(formData, "actualDepositorName"),
      actualAmount: Number(value(formData, "actualAmount")),
      depositedAt: koreanLocalDateTime(value(formData, "depositedAt")),
      transactionReference: value(formData, "transactionReference"),
      reason: value(formData, "reason"),
    });
  } catch (error) {
    done(orderId, failureMessage(error, "입금 확인 처리에 실패했습니다."));
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

  try {
    await postOrderAction(orderId, "/deposit-mismatch", { memo: value(formData, "memo") });
  } catch (error) {
    done(orderId, failureMessage(error, "입금 불일치 메모 저장에 실패했습니다."));
  }

  revalidatePath("/admin/orders");
  done(orderId, "입금 불일치 메모를 저장했습니다.");
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

export async function completeManualRefund(formData: FormData) {
  const orderId = value(formData, "orderId");
  const refundId = value(formData, "refundId");

  try {
    await postRefundAction(refundId, "/manual-complete", {
      reason: value(formData, "reason"),
      bankName: value(formData, "bankName"),
      accountNumber: value(formData, "accountNumber"),
      accountHolder: value(formData, "accountHolder"),
		transferredAt: koreanLocalDateTime(value(formData, "transferredAt")),
		transactionReference: value(formData, "transactionReference"),
    });
  } catch (error) {
    done(orderId, failureMessage(error, "수동 환불 완료 처리에 실패했습니다."));
  }

  revalidatePath("/admin/orders");
  done(orderId, "수동 환불 완료를 기록했습니다.");
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
