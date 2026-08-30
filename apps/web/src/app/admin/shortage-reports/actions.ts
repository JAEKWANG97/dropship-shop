"use server";

import { cookies } from "next/headers";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { ApiError, apiSendWithCookie } from "@/lib/api";
import { uncertainAdminCommandKey } from "@/lib/admin-payment";
import { supplierMutationHeaders } from "@/lib/supplier";
import { adminShortageReviewCommand } from "@/lib/supplier-claims";

function field(formData: FormData, name: string) {
  const value = formData.get(name);
  return typeof value === "string" ? value.trim() : "";
}

function done(reportId: string, message: string, retryAction?: string, idempotencyKey?: string): never {
  const search = new URLSearchParams({ reportId, message });
  if (retryAction && idempotencyKey) {
    search.set("retryAction", retryAction);
    search.set("idempotencyKey", idempotencyKey);
  }
  redirect(`/admin/shortage-reports?${search}`);
}

async function reviewShortage(formData: FormData, action: "approve" | "reject") {
  const reportId = field(formData, "reportId");
  const expectedStatus = field(formData, "expectedStatus");
  const reviewReasonCode = field(formData, "reviewReasonCode");
  const idempotencyKey = field(formData, "idempotencyKey");
  const command = adminShortageReviewCommand(action, expectedStatus, reviewReasonCode);

  if (!reportId || !command || !idempotencyKey) {
    done(reportId, "최신 품절 보고 상태와 검토 사유를 다시 확인해 주세요.");
  }

  const retryAction = `shortage-${action}-${reportId}`;
  try {
    await apiSendWithCookie(
      `/api/admin/supplier-shortage-reports/${encodeURIComponent(reportId)}/${action}`,
      (await cookies()).toString(),
      {
        method: "POST",
        headers: supplierMutationHeaders(idempotencyKey),
        body: JSON.stringify(command),
      },
    );
  } catch (error) {
    const message = error instanceof ApiError && error.message.trim()
      ? error.message
      : "품절 보고 검토를 완료하지 못했습니다.";
    done(reportId, message, retryAction, uncertainAdminCommandKey(error, idempotencyKey));
  }

  revalidatePath("/admin/shortage-reports");
  revalidatePath("/admin/orders");
  done(reportId, action === "approve" ? "품절을 승인하고 환불 흐름을 시작했습니다." : "품절 보고를 거절했습니다.");
}

export async function approveShortageReport(formData: FormData) {
  return reviewShortage(formData, "approve");
}

export async function rejectShortageReport(formData: FormData) {
  return reviewShortage(formData, "reject");
}
