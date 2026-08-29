"use server";

import { randomUUID } from "node:crypto";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { ApiError, apiSendWithCookie } from "@/lib/api";
import { getAdminUser } from "@/lib/session";
import { supplierMutationHeaders } from "@/lib/supplier";

function value(formData: FormData, name: string) {
  const field = formData.get(name);
  return typeof field === "string" ? field.trim() : "";
}

function detailPath(supplierId: string, message: string) {
  return `/admin/suppliers/${encodeURIComponent(supplierId)}?message=${encodeURIComponent(message)}`;
}

async function mutate(
  formData: FormData,
  path: string,
  method: "POST" | "PATCH",
  body: Record<string, unknown>,
) {
  await requireAdmin();
  return apiSendWithCookie(path, (await cookies()).toString(), {
    method,
    headers: supplierMutationHeaders(value(formData, "idempotencyKey") || randomUUID()),
    body: JSON.stringify(body),
  });
}

export async function reissueSupplierInvite(formData: FormData) {
  const supplierId = value(formData, "supplierId");
  let message = "새 초대 발급을 요청했습니다.";
  try {
    await mutate(formData, `/api/admin/suppliers/${encodeURIComponent(supplierId)}/invite/reissue`, "POST", {
      reasonCode: value(formData, "reasonCode"),
    });
  } catch (error) {
    message = actionError(error, "초대 재발급에 실패했습니다.");
  }
  redirect(detailPath(supplierId, message));
}

export async function updateSupplierPortalStatus(formData: FormData) {
  const supplierId = value(formData, "supplierId");
  let message = "포털 상태를 변경했습니다.";
  try {
    await mutate(formData, `/api/admin/suppliers/${encodeURIComponent(supplierId)}/portal-status`, "PATCH", {
      portalStatus: value(formData, "portalStatus"),
      salesAction: value(formData, "salesAction"),
      reason: value(formData, "reason"),
    });
  } catch (error) {
    message = actionError(error, "포털 상태 변경에 실패했습니다.");
  }
  redirect(detailPath(supplierId, message));
}

export async function updateSupplierSalesStatus(formData: FormData) {
  const supplierId = value(formData, "supplierId");
  let message = "판매 상태를 변경했습니다.";
  try {
    await mutate(formData, `/api/admin/suppliers/${encodeURIComponent(supplierId)}/sales-status`, "PATCH", {
      status: value(formData, "salesStatus"),
      reason: value(formData, "reason"),
    });
  } catch (error) {
    message = actionError(error, "판매 상태 변경에 실패했습니다.");
  }
  redirect(detailPath(supplierId, message));
}

export async function disconnectSupplierManager(formData: FormData) {
  const supplierId = value(formData, "supplierId");
  let message = "담당자 연결을 해제했습니다.";
  try {
    await mutate(formData, `/api/admin/suppliers/${encodeURIComponent(supplierId)}/manager-disconnect`, "POST", {
      salesAction: value(formData, "salesAction"),
      reason: value(formData, "reason"),
    });
  } catch (error) {
    message = actionError(error, "담당자 연결 해제에 실패했습니다.");
  }
  redirect(detailPath(supplierId, message));
}

export async function updateSupplierContactEmail(formData: FormData) {
  const supplierId = value(formData, "supplierId");
  let message = "연락 이메일을 변경하고 새 이메일로 초대를 발급했습니다.";
  try {
    await mutate(formData, `/api/admin/suppliers/${encodeURIComponent(supplierId)}/contact-email`, "PATCH", {
      contactEmail: value(formData, "contactEmail"),
      salesAction: value(formData, "salesAction"),
      reason: value(formData, "reason"),
    });
  } catch (error) {
    message = actionError(error, "연락 이메일 변경에 실패했습니다.");
  }
  redirect(detailPath(supplierId, message));
}

async function requireAdmin() {
  if (!(await getAdminUser())) redirect("/admin");
}

function actionError(error: unknown, fallback: string) {
  if (!(error instanceof ApiError)) return fallback;
  const mapped = ({
    SUPPLIER_PORTAL_NOT_RELEASED: "포털 공개 전이라 새 초대를 발급할 수 없습니다.",
    INVITE_REISSUE_NOT_ALLOWED: "현재 상태에서는 초대를 다시 발급할 수 없습니다.",
    CONTRACT_NOT_VERIFIED: "유효한 계약 확인 전에는 판매 또는 포털을 활성화할 수 없습니다.",
    IDEMPOTENCY_CONFLICT: "이미 처리된 요청과 내용이 다릅니다. 화면을 새로고침해 주세요.",
  } as Record<string, string>)[error.responseCode];
  return mapped ?? (error.responseMessage || fallback);
}
