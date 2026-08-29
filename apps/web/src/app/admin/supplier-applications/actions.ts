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

function path(applicationId: string, message: string) {
  return `/admin/supplier-applications/${encodeURIComponent(applicationId)}?message=${encodeURIComponent(message)}`;
}

export async function approveSupplierApplication(formData: FormData) {
  await requireAdmin();
  const applicationId = value(formData, "applicationId");
  const approvalMode = value(formData, "approvalMode");
  let message = "공급처 신청을 승인하고 초대 발급을 요청했습니다.";
  try {
    await apiSendWithCookie(`/api/admin/supplier-applications/${encodeURIComponent(applicationId)}/approve`, (await cookies()).toString(), {
      method: "POST",
      headers: supplierMutationHeaders(value(formData, "idempotencyKey") || randomUUID()),
      body: JSON.stringify({
        approvalMode,
        existingSupplierId: approvalMode === "LINK_EXISTING" ? value(formData, "existingSupplierId") || null : null,
        reviewReasonCode: "APPLICATION_APPROVED",
        internalReason: value(formData, "internalReason"),
      }),
    });
  } catch (error) {
    message = actionError(error, "신청 승인에 실패했습니다.");
  }
  redirect(path(applicationId, message));
}

export async function rejectSupplierApplication(formData: FormData) {
  await requireAdmin();
  const applicationId = value(formData, "applicationId");
  let message = "공급처 신청을 거절했습니다.";
  try {
    await apiSendWithCookie(`/api/admin/supplier-applications/${encodeURIComponent(applicationId)}/reject`, (await cookies()).toString(), {
      method: "POST",
      headers: supplierMutationHeaders(value(formData, "idempotencyKey") || randomUUID()),
      body: JSON.stringify({
        reviewReasonCode: value(formData, "reviewReasonCode"),
        internalReason: value(formData, "internalReason"),
      }),
    });
  } catch (error) {
    message = actionError(error, "신청 거절에 실패했습니다.");
  }
  redirect(path(applicationId, message));
}

async function requireAdmin() {
  if (!(await getAdminUser())) redirect("/admin");
}

function actionError(error: unknown, fallback: string) {
  if (!(error instanceof ApiError)) return fallback;
  return ({
    APPLICATION_EXPIRED: "보관 기한이 지나 신청이 만료되었습니다.",
    SUPPLIER_PORTAL_NOT_RELEASED: "포털 공개 전이라 새 초대를 발급할 수 없습니다.",
    IDEMPOTENCY_CONFLICT: "이미 처리된 요청과 내용이 다릅니다. 화면을 새로고침해 주세요.",
  } as Record<string, string>)[error.responseCode] ?? (error.responseMessage || fallback);
}
