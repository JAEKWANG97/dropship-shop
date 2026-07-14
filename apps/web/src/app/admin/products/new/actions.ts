"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { apiSendWithCookie, apiUrl } from "@/lib/api";
import type { AdminProduct, AdminProductStatus } from "@/lib/admin";
import type { ProductCategoryCode } from "@/lib/categories";

function text(formData: FormData, name: string) {
  const value = formData.get(name);
  return typeof value === "string" ? value.trim() : "";
}

type ProductImageUploadResponse = {
  imageUrl: string;
  objectKey: string;
  size: number;
  contentType: string;
};

async function uploadThumbnail(productId: string, formData: FormData, cookieHeader: string) {
  const file = formData.get("thumbnailFile");
  if (!(file instanceof File) || file.size === 0) {
    return;
  }

  const uploadForm = new FormData();
  uploadForm.set("file", file);

  const uploadResponse = await fetch(apiUrl(`/api/admin/products/${productId}/images/upload`), {
    method: "POST",
    headers: cookieHeader ? { Cookie: cookieHeader } : {},
    body: uploadForm,
    cache: "no-store",
  });
  if (!uploadResponse.ok) {
    throw new Error(`Image upload failed: ${uploadResponse.status}`);
  }

  const uploaded = (await uploadResponse.json()) as ProductImageUploadResponse;
  const altText = text(formData, "thumbnailAltText") || text(formData, "name");
  await apiSendWithCookie(`/api/admin/products/${productId}/images`, cookieHeader, {
    method: "PUT",
    body: JSON.stringify({
      images: [{ type: "THUMBNAIL", imageUrl: uploaded.imageUrl, sortOrder: 0, altText }],
      reason: "관리자 상품 등록 이미지 설정",
    }),
  });
}

export async function createAdminProduct(formData: FormData) {
  let message = "상품을 등록했습니다.";
  const cookieHeader = (await cookies()).toString();
  let product: AdminProduct;

  try {
    product = await apiSendWithCookie<AdminProduct>("/api/admin/products", cookieHeader, {
      method: "POST",
      body: JSON.stringify({
        supplierId: text(formData, "supplierId"),
        name: text(formData, "name"),
        summary: text(formData, "summary"),
        sourcePrice: Number(text(formData, "sourcePrice") || text(formData, "basePrice") || "0"),
        sourceUrl: text(formData, "sourceUrl") || null,
        basePrice: Number(text(formData, "basePrice") || "0"),
        categoryCode: text(formData, "categoryCode") as ProductCategoryCode,
        status: text(formData, "status") as AdminProductStatus,
      }),
    });
  } catch {
    redirect(`/admin/products/new?message=${encodeURIComponent("상품 등록 API 연결에 실패했습니다.")}`);
  }

  try {
    await uploadThumbnail(product.id, formData, cookieHeader);
  } catch {
    message = "상품은 등록했지만 대표 이미지 업로드에 실패했습니다.";
  }

  redirect(`/admin/products?message=${encodeURIComponent(message)}`);
}
