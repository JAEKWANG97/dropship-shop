"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { apiSendWithCookie, apiUrl } from "@/lib/api";
import type { PricingPolicy } from "@/lib/admin";
import { getAdminPricingPolicy } from "@/lib/admin";
import type { ProductCategoryCode } from "@/lib/categories";
import type { ProductDetailBlockType, ProductOptionStatus, ProductStatus } from "@/lib/catalog";

function text(formData: FormData, name: string) {
  const value = formData.get(name);
  return typeof value === "string" ? value.trim() : "";
}

function detailPath(productId: string, message: string) {
  return `/admin/products/${productId}?message=${encodeURIComponent(message)}`;
}

function numberValue(formData: FormData, name: string) {
  const parsed = Number(text(formData, name) || "0");
  return Number.isFinite(parsed) ? parsed : 0;
}

function calculatedBasePrice(sourcePrice: number, policy: PricingPolicy) {
  const rawPrice = Math.ceil(sourcePrice * (1 + policy.totalMarkupRate / 100));
  return Math.ceil(rawPrice / policy.roundingUnit) * policy.roundingUnit;
}

type ProductImageUploadResponse = {
  imageUrl: string;
  objectKey: string;
  size: number;
  contentType: string;
};

type DetailBlockPayload = {
  type: ProductDetailBlockType;
  imageUrl: string | null;
  htmlContent: string | null;
  sortOrder: number;
  altText: string | null;
};

async function uploadDetailImage(productId: string, file: File, cookieHeader: string) {
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
  return uploaded.imageUrl;
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

export async function updateAdminProductPrices(formData: FormData) {
  const productId = text(formData, "productId");
  const cookieHeader = (await cookies()).toString();
  const sourcePrice = numberValue(formData, "sourcePrice");
  let basePrice = numberValue(formData, "basePrice");
  let message = "상품 가격을 저장했습니다.";

  try {
    if (text(formData, "priceMode") === "apply") {
      basePrice = calculatedBasePrice(sourcePrice, await getAdminPricingPolicy());
    }
    await apiSendWithCookie(`/api/admin/products/${productId}`, cookieHeader, {
      method: "PATCH",
      body: JSON.stringify({
        supplierId: text(formData, "supplierId"),
        name: text(formData, "name"),
        summary: text(formData, "summary"),
        sourcePrice,
        basePrice,
        categoryCode: text(formData, "categoryCode") as ProductCategoryCode,
        reason: text(formData, "reason"),
      }),
    });
  } catch {
    message = "상품 가격 저장에 실패했습니다.";
  }

  redirect(detailPath(productId, message));
}

export async function updateAdminProductDetailBlocks(formData: FormData) {
  const productId = text(formData, "productId");
  const cookieHeader = (await cookies()).toString();
  let message = "상세 콘텐츠를 저장했습니다.";

  try {
    const detailBlocks: DetailBlockPayload[] = [];
    const blockCount = numberValue(formData, "blockCount");

    for (let index = 0; index < blockCount; index += 1) {
      if (text(formData, `blockInclude-${index}`) !== "true") {
        continue;
      }

      const type = text(formData, `blockType-${index}`) as ProductDetailBlockType;
      if (type === "IMAGE") {
        const file = formData.get(`blockImageFile-${index}`);
        const imageUrl = file instanceof File && file.size > 0
          ? await uploadDetailImage(productId, file, cookieHeader)
          : text(formData, `blockImageUrl-${index}`);
        detailBlocks.push({
          type,
          imageUrl,
          htmlContent: null,
          sortOrder: numberValue(formData, `blockSortOrder-${index}`),
          altText: text(formData, `blockAltText-${index}`) || null,
        });
      } else {
        detailBlocks.push({
          type,
          imageUrl: null,
          htmlContent: text(formData, `blockHtmlContent-${index}`),
          sortOrder: numberValue(formData, `blockSortOrder-${index}`),
          altText: null,
        });
      }
    }

    const newImageFile = formData.get("newImageFile");
    if (newImageFile instanceof File && newImageFile.size > 0) {
      detailBlocks.push({
        type: "IMAGE",
        imageUrl: await uploadDetailImage(productId, newImageFile, cookieHeader),
        htmlContent: null,
        sortOrder: numberValue(formData, "newImageSortOrder"),
        altText: text(formData, "newImageAltText") || null,
      });
    }

    const newHtmlContent = text(formData, "newHtmlContent");
    if (newHtmlContent) {
      detailBlocks.push({
        type: "HTML",
        imageUrl: null,
        htmlContent: newHtmlContent,
        sortOrder: numberValue(formData, "newHtmlSortOrder"),
        altText: null,
      });
    }

    await apiSendWithCookie(`/api/admin/products/${productId}/detail-blocks`, cookieHeader, {
      method: "PUT",
      body: JSON.stringify({
        detailBlocks,
        reason: text(formData, "reason"),
      }),
    });
  } catch {
    message = "상세 콘텐츠 저장에 실패했습니다.";
  }

  redirect(detailPath(productId, message));
}

export async function updateAdminProductNotice(formData: FormData) {
  const productId = text(formData, "productId");
  const cookieHeader = (await cookies()).toString();
  let message = "상품 고시를 저장했습니다.";
  try {
    await apiSendWithCookie(`/api/admin/products/${productId}/notice`, cookieHeader, {
      method: "PUT",
      body: JSON.stringify({
        productInfoNotice: text(formData, "productInfoNotice"),
        shippingInfo: text(formData, "shippingInfo"),
        asInfo: text(formData, "asInfo"),
        returnExchangeInfo: text(formData, "returnExchangeInfo"),
        reason: text(formData, "reason"),
      }),
    });
  } catch {
    message = "상품 고시 저장에 실패했습니다.";
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
