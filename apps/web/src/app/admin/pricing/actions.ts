"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { apiSendWithCookie } from "@/lib/api";

function text(formData: FormData, name: string) {
  const value = formData.get(name);
  return typeof value === "string" ? value.trim() : "";
}

function numberValue(formData: FormData, name: string) {
  const value = Number(text(formData, name) || "0");
  return Number.isFinite(value) ? value : 0;
}

export async function updatePricingPolicy(formData: FormData) {
  const cookieHeader = (await cookies()).toString();
  let message = "가격 정책을 저장했습니다.";

  try {
    await apiSendWithCookie("/api/admin/pricing-policy", cookieHeader, {
      method: "PUT",
      body: JSON.stringify({
        name: text(formData, "name"),
        commissionRate: numberValue(formData, "commissionRate"),
        taxBufferRate: numberValue(formData, "taxBufferRate"),
        overheadRate: numberValue(formData, "overheadRate"),
        safetyMarginRate: numberValue(formData, "safetyMarginRate"),
        roundingUnit: numberValue(formData, "roundingUnit"),
      }),
    });
  } catch {
    message = "가격 정책 저장에 실패했습니다.";
  }

  redirect(`/admin/pricing?message=${encodeURIComponent(message)}`);
}
