import { apiGet } from "./api";
import type { ProductCategoryCode } from "./categories";

export type ProductStatus = "ACTIVE" | "SOLD_OUT" | "HIDDEN" | "STOPPED";
export type ProductComplianceStatus = "PENDING" | "NOT_REQUIRED" | "VERIFIED" | "REJECTED";
export type SaleBlocker = "BASE_PRICE" | "THUMBNAIL" | "ACTIVE_OPTION" | "PRODUCT_NOTICE" | "COMPLIANCE";
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

export type ProductPage = {
  products: ProductSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  categoryCounts: Partial<Record<ProductCategoryCode, number>>;
};

export type ProductQuery = {
  q?: string;
  category?: ProductCategoryCode;
  categories?: ProductCategoryCode[];
  minPrice?: number;
  maxPrice?: number;
  sort?: string;
  page?: number;
  size?: number;
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
  supplierId?: string;
  supplierName?: string;
  sourcePrice?: number;
  sourceUrl?: string | null;
  complianceStatus?: ProductComplianceStatus;
  saleReady?: boolean;
  saleBlockers?: SaleBlocker[];
  optionCount?: number;
  hasThumbnail?: boolean;
  hasProductNotice?: boolean;
  hasDetailContent?: boolean;
  detailVersion: number;
  productNoticeVersion: number | null;
  images: ProductImage[];
  options: ProductOption[];
  detailBlocks: ProductDetailBlock[];
  productNotice: ProductNotice | null;
  policyLinks: PolicyLink[];
};

export function getProducts(query: ProductQuery = {}) {
  const params = new URLSearchParams();
  if (query.q) params.set("q", query.q);
  if (query.category) params.set("category", query.category);
  query.categories?.forEach((category) => params.append("categories", category));
  if (query.minPrice !== undefined) params.set("minPrice", String(query.minPrice));
  if (query.maxPrice !== undefined) params.set("maxPrice", String(query.maxPrice));
  if (query.sort) params.set("sort", query.sort);
  if (query.page !== undefined) params.set("page", String(query.page));
  if (query.size !== undefined) params.set("size", String(query.size));
  const value = params.toString();
  return apiGet<ProductPage>(value ? `/api/products?${value}` : "/api/products");
}

export function getProduct(productId: string) {
  return apiGet<ProductDetail>(`/api/products/${productId}`);
}

export function formatPrice(value: number) {
  return `${value.toLocaleString("ko-KR")}원`;
}
