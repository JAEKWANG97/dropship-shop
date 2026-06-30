"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { apiSendWithCookie } from "@/lib/api";
import type { ProductOptionStatus, ProductStatus } from "@/lib/catalog";

function text(formData: FormData, name: string) {
  const value = formData.get(name);
  return typeof value === "string" ? value.trim() : "";
}

function detailPath(productId: string, message: string) {
  return `/admin/products/${productId}?message=${encodeURIComponent(message)}`;
}

export async function updateAdminProductStatus(formData: FormData) {
  const productId = text(formData, "productId");
  const cookieHeader = (await cookies()).toString();
  let message = "상품 판매 상태를 변경했습니다.";
  try {
    await apiSendWithCookie(`/api/admin/products/${productId}/status`, cookieHeader, {
      method: "PATCH",
      body: JSON.stringify({
        status: text(formData, "status") as ProductStatus,
        reason: text(formData, "reason"),
      }),
    });
  } catch {
    message = "상품 판매 상태 변경에 실패했습니다.";
  }
  redirect(detailPath(productId, message));
}

export async function createAdminProductOption(formData: FormData) {
  const productId = text(formData, "productId");
  const cookieHeader = (await cookies()).toString();
  let message = "옵션을 추가했습니다.";
  try {
    await apiSendWithCookie(`/api/admin/products/${productId}/options`, cookieHeader, {
      method: "POST",
      body: JSON.stringify({
        name: text(formData, "name"),
        additionalPrice: Number(text(formData, "additionalPrice") || "0"),
        status: text(formData, "status") as ProductOptionStatus,
      }),
    });
  } catch {
    message = "옵션 추가에 실패했습니다.";
  }
  redirect(detailPath(productId, message));
}

export async function updateAdminProductOption(formData: FormData) {
  const productId = text(formData, "productId");
  const optionId = text(formData, "optionId");
  const cookieHeader = (await cookies()).toString();
  let message = "옵션 정보를 변경했습니다.";
  try {
    await apiSendWithCookie(`/api/admin/products/${productId}/options/${optionId}`, cookieHeader, {
      method: "PATCH",
      body: JSON.stringify({
        name: text(formData, "name"),
        additionalPrice: Number(text(formData, "additionalPrice") || "0"),
        status: text(formData, "status") as ProductOptionStatus,
        reason: text(formData, "reason"),
      }),
    });
  } catch {
    message = "옵션 정보 변경에 실패했습니다.";
  }
  redirect(detailPath(productId, message));
}

export async function updateAdminProductOptionStatus(formData: FormData) {
  const productId = text(formData, "productId");
  const optionId = text(formData, "optionId");
  const cookieHeader = (await cookies()).toString();
  let message = "옵션 판매 상태를 변경했습니다.";
  try {
    await apiSendWithCookie(`/api/admin/products/${productId}/options/${optionId}/status`, cookieHeader, {
      method: "PATCH",
      body: JSON.stringify({
        status: text(formData, "status") as ProductOptionStatus,
        reason: text(formData, "reason"),
      }),
    });
  } catch {
    message = "옵션 판매 상태 변경에 실패했습니다.";
  }
  redirect(detailPath(productId, message));
}
