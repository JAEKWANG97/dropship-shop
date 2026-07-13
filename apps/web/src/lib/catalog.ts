import { apiGet } from "./api";
import type { ProductCategoryCode } from "./categories";

export type ProductStatus = "ACTIVE" | "SOLD_OUT" | "HIDDEN" | "STOPPED";
export type ProductComplianceStatus = "PENDING" | "NOT_REQUIRED" | "VERIFIED" | "REJECTED";
export type ProductOptionStatus = "ACTIVE" | "SOLD_OUT" | "STOPPED";
export type ProductImageType = "THUMBNAIL" | "GALLERY";
export type ProductDetailBlockType = "IMAGE" | "HTML";

export type ProductSummary = {
  id: string;
  name: string;
  summary: string;
  basePrice: number;
  categoryCode: ProductCategoryCode;
  status: ProductStatus;
  thumbnailImageUrl: string | null;
};

export type ProductImage = {
  id: string;
  type: ProductImageType;
  imageUrl: string;
  sortOrder: number;
  altText: string | null;
};

export type ProductOption = {
  id: string;
  name: string;
  additionalPrice: number;
  status: ProductOptionStatus;
  sourceOptionCode?: string;
  sourceAdditionalPrice?: number;
  sourceStockQuantity?: number;
  sortOrder?: number;
};

export type ProductDetailBlock = {
  id: string;
  type: ProductDetailBlockType;
  imageUrl: string | null;
  htmlContent: string | null;
  sortOrder: number;
  altText: string | null;
};

export type ProductNotice = {
  id: string;
  version: number;
  productInfoNotice: string;
  shippingInfo: string;
  asInfo: string;
  returnExchangeInfo: string;
};

export type PolicyLink = {
  label: string;
  href: string;
  policyType: string;
};

export type ProductDetail = ProductSummary & {
  sourcePrice?: number;
  complianceStatus?: ProductComplianceStatus;
  detailVersion: number;
  productNoticeVersion: number | null;
  images: ProductImage[];
  options: ProductOption[];
  detailBlocks: ProductDetailBlock[];
  productNotice: ProductNotice | null;
  policyLinks: PolicyLink[];
};

export function getProducts() {
  return apiGet<ProductSummary[]>("/api/products");
}

export function getProduct(productId: string) {
  return apiGet<ProductDetail>(`/api/products/${productId}`);
}

export function formatPrice(value: number) {
  return `${value.toLocaleString("ko-KR")}원`;
}
