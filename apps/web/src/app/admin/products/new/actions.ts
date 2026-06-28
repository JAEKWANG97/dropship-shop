"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { apiSendWithCookie } from "@/lib/api";
import type { AdminProductStatus } from "@/lib/admin";

function text(formData: FormData, name: string) {
  const value = formData.get(name);
  return typeof value === "string" ? value : "";
}

export async function createAdminProduct(formData: FormData) {
  let message = "상품을 등록했습니다.";

  try {
    await apiSendWithCookie("/api/admin/products", (await cookies()).toString(), {
      method: "POST",
      body: JSON.stringify({
        supplierId: text(formData, "supplierId"),
        name: text(formData, "name"),
        summary: text(formData, "summary"),
        basePrice: Number(text(formData, "basePrice") || "0"),
        status: text(formData, "status") as AdminProductStatus,
      }),
    });
  } catch {
    message = "상품 등록 API 연결에 실패했습니다. 입력 화면은 유지됩니다.";
  }

  redirect(`/admin/products?message=${encodeURIComponent(message)}`);
}
