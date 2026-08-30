export const PRODUCT_IMAGE_ACCEPT = "image/jpeg,image/png,image/webp";
export const PRODUCT_IMAGE_MAX_BYTES = 10 * 1024 * 1024;

export type SupplierDisplayStatus =
  | "EDITING"
  | "APPROVED"
  | "UNDER_REVIEW"
  | "CHANGES_REQUESTED"
  | "REJECTED"
  | "PAUSED_BY_COREABLE";

export type SupplierNextAction = "WAIT" | "EDIT_AND_RESUBMIT" | "CONTACT_COREABLE" | "NONE";

export type SupplierReviewReasonCode =
  | "CERTIFICATION_REVIEW"
  | "CATEGORY_REVIEW"
  | "REQUIRED_INFO_MISSING"
  | "SAFETY_REVIEW"
  | "SUPPLEMENT_REQUIRED"
  | "REJECTED_POLICY";

export type SupplierProductOption = {
  id: string;
  name: string;
  sourceOptionCode: string;
  sourceAdditionalPrice: number;
  sortOrder: number;
  deletable: boolean;
};

export type SupplierProductImage = {
  id: string;
  type: "THUMBNAIL" | "GALLERY" | "DETAIL";
  imageUrl: string;
  sortOrder: number;
  altText: string;
  deletable: boolean;
};

export type SupplierProductDetailBlock = {
  id: string;
  type: "HTML" | "IMAGE";
  htmlContent: string;
  productImageId: string;
  sortOrder: number;
  altText: string;
};

export type SupplierProductDetailBlockInput = {
  type: "HTML" | "IMAGE";
  productImageId?: string;
  htmlContent?: string;
  sortOrder: number;
  altText?: string;
};

export type SupplierProductNotice = {
  productInfoNotice: string;
  shippingInfo: string;
  asInfo: string;
  returnExchangeInfo: string;
  noticeRows: Array<{ label: string; value: string }>;
};

export type SupplierProduct = {
  id: string;
  name: string;
  summary: string;
  sourcePrice: number;
  minimumOrderQuantity: number;
  orderQuantityStep: number;
  categoryCode: string;
  version: number;
  deletable: boolean;
  supplierDisplayStatus: string;
  reviewReasonCode: string | null;
  reviewMessage: string | null;
  nextAction: string;
  options: SupplierProductOption[];
  images: SupplierProductImage[];
  detailBlocks: SupplierProductDetailBlock[];
  notice: SupplierProductNotice;
  createdAt: string | null;
  updatedAt: string | null;
};

export type SupplierProductInput = Pick<
  SupplierProduct,
  "name" | "summary" | "sourcePrice" | "minimumOrderQuantity" | "orderQuantityStep" | "categoryCode"
>;

export type SupplierOptionInput = Pick<
  SupplierProductOption,
  "name" | "sourceOptionCode" | "sourceAdditionalPrice" | "sortOrder"
>;

export type SupplierStatusView = {
  label: string;
  editable: boolean;
  tone: "neutral" | "success" | "warning" | "danger";
  reasonLabel: string | null;
  message: string | null;
  nextLabel: string;
  editWarning: string | null;
};

export type AdminProductReviewOption = {
  id: string;
  name: string;
  additionalPrice: number;
  status: string;
  sourceOptionCode: string;
  sourceAdditionalPrice: number;
  sortOrder: number;
};

export type AdminProductReviewImage = {
  id: string;
  type: "THUMBNAIL" | "GALLERY" | "DETAIL" | "UNKNOWN";
  imageUrl: string | null;
  sortOrder: number;
  altText: string;
};

export type AdminProductReviewDetailBlock = {
  id: string;
  type: "HTML" | "IMAGE" | "UNKNOWN";
  imageUrl: string | null;
  htmlContent: string;
  sortOrder: number;
  altText: string;
};

export type AdminProductReview = {
  id: string;
  version: number;
  supplierId: string;
  supplierName: string;
  name: string;
  summary: string;
  sourcePrice: number;
  basePrice: number;
  minimumOrderQuantity: number;
  orderQuantityStep: number;
  categoryCode: string;
  productStatus: string;
  reviewStatus: string;
  complianceStatus: string;
  reviewReasonCode: string | null;
  supplierReviewMessage: string | null;
  firstSubmittedAt: string | null;
  options: AdminProductReviewOption[];
  images: AdminProductReviewImage[];
  detailBlocks: AdminProductReviewDetailBlock[];
  notice: SupplierProductNotice;
};

export class SupplierProductApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
  ) {
    super(code || `API request failed: ${status}`);
  }
}

const STATUS_PAIRS: Record<string, { action: SupplierNextAction; label: string; editable: boolean; tone: SupplierStatusView["tone"]; nextLabel: string; editWarning: string | null }> = {
  EDITING: { action: "EDIT_AND_RESUBMIT", label: "작성 중", editable: true, tone: "neutral", nextLabel: "내용을 완성해 등록해 주세요.", editWarning: null },
  APPROVED: { action: "NONE", label: "등록 승인", editable: true, tone: "warning", nextLabel: "상품 등록 승인이 완료되었습니다.", editWarning: "승인된 상품을 수정하면 즉시 일시 비공개되고 저장 후 다시 분류·검토됩니다." },
  UNDER_REVIEW: { action: "WAIT", label: "Coreable 검토 중", editable: true, tone: "warning", nextLabel: "검토 결과를 기다려 주세요.", editWarning: "검토 중인 상품을 수정하면 현재 검토가 취소되고 저장 후 새 버전으로 다시 분류됩니다." },
  CHANGES_REQUESTED: { action: "EDIT_AND_RESUBMIT", label: "보완 필요", editable: true, tone: "warning", nextLabel: "요청 내용을 보완한 뒤 다시 등록해 주세요.", editWarning: null },
  REJECTED: { action: "CONTACT_COREABLE", label: "등록 불가", editable: false, tone: "danger", nextLabel: "추가 확인은 Coreable에 문의해 주세요.", editWarning: null },
  PAUSED_BY_COREABLE: { action: "CONTACT_COREABLE", label: "판매 보류", editable: false, tone: "warning", nextLabel: "판매 재개는 Coreable에 문의해 주세요.", editWarning: null },
};

const REASON_LABELS: Record<SupplierReviewReasonCode, string> = {
  CERTIFICATION_REVIEW: "인증 정보 확인",
  CATEGORY_REVIEW: "카테고리 확인",
  REQUIRED_INFO_MISSING: "필수 정보 확인",
  SAFETY_REVIEW: "안전 기준 확인",
  SUPPLEMENT_REQUIRED: "정보 보완 요청",
  REJECTED_POLICY: "등록 정책 미충족",
};

const COMPLIANCE_LABELS: Record<string, string> = {
  PENDING: "인증 검토 대기",
  NOT_REQUIRED: "인증 확인 불필요",
  VERIFIED: "인증 확인 완료",
  REJECTED: "인증 기준 미충족",
};

const ADMIN_REVIEW_STATUS_LABELS: Record<string, string> = {
  DRAFT: "작성 중",
  AUTO_APPROVED: "자동 승인",
  REVIEW_REQUIRED: "검토 필요",
  SUPPLEMENT_REQUESTED: "보완 요청",
  APPROVED: "승인",
  REJECTED: "거절",
};

const UNDER_REVIEW_REASONS = new Set<SupplierReviewReasonCode>([
  "CERTIFICATION_REVIEW",
  "CATEGORY_REVIEW",
  "REQUIRED_INFO_MISSING",
  "SAFETY_REVIEW",
]);

export function supplierStatusView(product: Pick<SupplierProduct, "supplierDisplayStatus" | "nextAction" | "reviewReasonCode" | "reviewMessage">): SupplierStatusView {
  const status = STATUS_PAIRS[product.supplierDisplayStatus];
  if (!status || status.action !== product.nextAction || !validReasonForStatus(product.supplierDisplayStatus, product.reviewReasonCode)) {
    return {
      label: "상태 확인 필요",
      editable: false,
      tone: "warning",
      reasonLabel: null,
      message: null,
      nextLabel: "화면을 새로고침한 뒤 계속되면 Coreable에 문의해 주세요.",
      editWarning: null,
    };
  }

  const reasonLabel = isReviewReason(product.reviewReasonCode)
    ? REASON_LABELS[product.reviewReasonCode]
    : null;
  const mayShowMessage = product.supplierDisplayStatus !== "PAUSED_BY_COREABLE" && reasonLabel !== null;

  return {
    label: status.label,
    editable: status.editable,
    tone: status.tone,
    reasonLabel,
    message: mayShowMessage ? safeSingleLine(product.reviewMessage, 500) : null,
    nextLabel: status.nextLabel,
    editWarning: status.editWarning,
  };
}

export function adminReviewReasonLabel(value: string | null) {
  return isReviewReason(value) ? REASON_LABELS[value] : "사유 코드 확인 필요";
}

export function adminComplianceLabel(value: string) {
  return COMPLIANCE_LABELS[value] ?? "인증 상태 확인 필요";
}

export function adminReviewStatusLabel(value: string) {
  return ADMIN_REVIEW_STATUS_LABELS[value] ?? "상태 확인 필요";
}

function validReasonForStatus(status: string, reason: string | null) {
  if (status === "EDITING" || status === "APPROVED" || status === "PAUSED_BY_COREABLE") return reason === null;
  if (status === "CHANGES_REQUESTED") return reason === "SUPPLEMENT_REQUIRED";
  if (status === "REJECTED") return reason === "REJECTED_POLICY";
  return status === "UNDER_REVIEW" && isReviewReason(reason) && UNDER_REVIEW_REASONS.has(reason);
}

export function validateProductImage(file: File) {
  const extension = file.name.split(".").pop()?.toLowerCase() ?? "";
  if (!["jpg", "jpeg", "png", "webp"].includes(extension)) return "JPG, PNG, WEBP 파일만 등록할 수 있습니다.";
  if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) return "이미지 파일 형식을 확인해 주세요.";
  if (file.size > PRODUCT_IMAGE_MAX_BYTES) return "이미지는 파일당 10MB 이하여야 합니다.";
  return null;
}

export async function listSupplierProducts() {
  return collection(await request("/api/supplier/products")).map(normalizeSupplierProduct);
}

export async function getSupplierProduct(productId: string) {
  return normalizeSupplierProduct(await request(`/api/supplier/products/${encodeURIComponent(productId)}`));
}

export async function createSupplierProduct(input: SupplierProductInput) {
  return mutation(await request("/api/supplier/products", { method: "POST", body: JSON.stringify(input) }));
}

export async function updateSupplierProduct(productId: string, input: SupplierProductInput, expectedVersion: number) {
  return mutation(await request(`/api/supplier/products/${encodeURIComponent(productId)}`, {
    method: "PATCH",
    body: JSON.stringify({ ...input, expectedVersion }),
  }));
}

export async function saveSupplierOption(productId: string, optionId: string | null, input: SupplierOptionInput, expectedVersion: number) {
  const suffix = optionId ? `/options/${encodeURIComponent(optionId)}` : "/options";
  return mutation(await request(`/api/supplier/products/${encodeURIComponent(productId)}${suffix}`, {
    method: optionId ? "PATCH" : "POST",
    body: JSON.stringify({ ...input, sourceOptionCode: input.sourceOptionCode || null, expectedVersion }),
  }));
}

export async function uploadSupplierImage(productId: string, file: File, type: SupplierProductImage["type"], altText: string, expectedVersion: number) {
  const body = new FormData();
  body.set("file", file);
  body.set("type", type);
  body.set("altText", altText);
  body.set("expectedVersion", String(expectedVersion));
  return mutation(await request(`/api/supplier/products/${encodeURIComponent(productId)}/images`, { method: "POST", body }));
}

export async function orderSupplierImages(productId: string, images: SupplierProductImage[], expectedVersion: number) {
  const presentationImages = images.filter((image) => image.type !== "DETAIL");
  return mutation(await request(`/api/supplier/products/${encodeURIComponent(productId)}/images/order`, {
    method: "PUT",
    body: JSON.stringify({
      expectedVersion,
      images: presentationImages.map((image, index) => ({
        imageId: image.id,
        type: image.type,
        sortOrder: index,
        altText: image.altText || null,
      })),
    }),
  }));
}

export async function replaceSupplierDetailBlocks(productId: string, detailBlocks: SupplierProductDetailBlockInput[], expectedVersion: number) {
  return mutation(await request(`/api/supplier/products/${encodeURIComponent(productId)}/detail-blocks`, {
    method: "PUT",
    body: JSON.stringify({ expectedVersion, detailBlocks }),
  }));
}

export function supplierDetailBlocksWithoutImage(
  detailBlocks: SupplierProductDetailBlock[],
  imageId: string,
): SupplierProductDetailBlockInput[] {
  return [...detailBlocks]
    .sort((left, right) => left.sortOrder - right.sortOrder)
    .filter((block) => block.type !== "IMAGE" || block.productImageId !== imageId)
    .map((block, sortOrder) => block.type === "IMAGE"
      ? {
        type: "IMAGE",
        productImageId: block.productImageId,
        sortOrder,
        altText: block.altText || undefined,
      }
      : {
        type: "HTML",
        htmlContent: block.htmlContent,
        sortOrder,
        altText: block.altText || undefined,
      });
}

export async function replaceSupplierNotice(productId: string, notice: SupplierProductNotice, expectedVersion: number) {
  return mutation(await request(`/api/supplier/products/${encodeURIComponent(productId)}/notice`, {
    method: "PUT",
    body: JSON.stringify({ expectedVersion, ...notice }),
  }));
}

export async function submitSupplierProduct(productId: string, expectedVersion: number) {
  return mutation(await request(`/api/supplier/products/${encodeURIComponent(productId)}/submit`, {
    method: "POST",
    body: JSON.stringify({ expectedVersion }),
  }));
}

export async function deleteSupplierProduct(productId: string, version: number) {
  await request(`/api/supplier/products/${encodeURIComponent(productId)}`, {
    method: "DELETE",
    headers: { "If-Match": `\"${version}\"` },
  });
}

export async function deleteSupplierOption(productId: string, optionId: string, version: number) {
  return deleteVersionedResource(`/api/supplier/products/${encodeURIComponent(productId)}/options/${encodeURIComponent(optionId)}`, version);
}

export async function deleteSupplierImage(productId: string, imageId: string, version: number) {
  return deleteVersionedResource(`/api/supplier/products/${encodeURIComponent(productId)}/images/${encodeURIComponent(imageId)}`, version);
}

export async function listAdminProductReviews() {
  return collection(await request("/api/admin/product-reviews")).map(normalizeAdminProductReview);
}

export async function getAdminProductReview(productId: string) {
  return normalizeAdminProductReview(await request(`/api/admin/product-reviews/${encodeURIComponent(productId)}`));
}

export async function decideAdminProductReview(productId: string, action: "approve" | "supplement" | "reject", body: Record<string, unknown>) {
  return mutation(await request(`/api/admin/product-reviews/${encodeURIComponent(productId)}/${action}`, {
    method: "POST",
    body: JSON.stringify(body),
  }));
}

export function productActionError(error: unknown) {
  if (!(error instanceof SupplierProductApiError)) return "요청을 처리하지 못했습니다. 잠시 뒤 다시 시도해 주세요.";
  if (isProductVersionError(error)) return "다른 변경이 먼저 저장되었습니다. 최신 내용을 다시 불러온 뒤 다시 시도해 주세요.";
  return ({
    PRODUCT_VERSION_REQUIRED: "최신 버전을 확인한 뒤 다시 시도해 주세요.",
    PRODUCT_NOT_DRAFT: "현재 상태에서는 이 작업을 할 수 없습니다.",
    PRODUCT_ALREADY_SUBMITTED: "이미 등록된 상품은 삭제할 수 없습니다.",
    PRODUCT_REFERENCED: "주문 또는 장바구니에서 사용 중인 상품은 삭제할 수 없습니다.",
    IMAGE_TOO_LARGE: "이미지는 파일당 10MB 이하여야 합니다.",
    IMAGE_TYPE_NOT_ALLOWED: "JPG, PNG, WEBP 이미지만 등록할 수 있습니다.",
    LAST_OPTION_REQUIRED: "마지막 옵션은 삭제할 수 없습니다.",
    OPTION_REFERENCED: "주문 또는 장바구니에서 사용 중인 옵션은 삭제할 수 없습니다.",
    DETAIL_IMAGE_REFERENCED: "상세 설명에서 사용 중인 이미지는 먼저 상세 블록에서 제거해 주세요.",
  } as Record<string, string>)[error.code] ?? "요청을 처리하지 못했습니다. 입력 내용을 확인해 주세요.";
}

export function isProductVersionError(error: unknown) {
  return error instanceof SupplierProductApiError
    && (error.code === "PRODUCT_VERSION_CONFLICT" || error.status === 412);
}

export function normalizeSupplierProduct(value: unknown): SupplierProduct {
  const item = record(value);
  const noticeValue = record(item.notice ?? item.productNotice);
  return {
    id: text(item.productId) || text(item.id),
    name: text(item.name),
    summary: text(item.summary),
    sourcePrice: integer(item.sourcePrice, 0),
    minimumOrderQuantity: integer(item.minimumOrderQuantity, 1),
    orderQuantityStep: integer(item.orderQuantityStep, 1),
    categoryCode: text(item.categoryCode),
    version: integer(item.version, 0),
    deletable: item.deletable === true,
    supplierDisplayStatus: text(item.supplierDisplayStatus),
    reviewReasonCode: nullableText(item.reviewReasonCode),
    reviewMessage: nullableText(item.reviewMessage),
    nextAction: text(item.nextAction),
    options: array(item.options).map((optionValue) => {
      const option = record(optionValue);
      return {
        id: text(option.optionId) || text(option.id),
        name: text(option.name) || "기본",
        sourceOptionCode: text(option.sourceOptionCode) || text(option.supplierOptionCode),
        sourceAdditionalPrice: integer(option.sourceAdditionalPrice, 0),
        sortOrder: integer(option.sortOrder, 0),
        deletable: option.deletable === true,
      };
    }),
    images: array(item.images).map((imageValue) => {
      const image = record(imageValue);
      const type = text(image.type);
      return {
        id: text(image.imageId) || text(image.id),
        type: type === "GALLERY" || type === "DETAIL" ? type : "THUMBNAIL",
        imageUrl: text(image.imageUrl),
        sortOrder: integer(image.sortOrder, 0),
        altText: text(image.altText),
        deletable: image.deletable === true,
      };
    }),
    detailBlocks: array(item.detailBlocks).map((blockValue) => {
      const block = record(blockValue);
      return {
        id: text(block.blockId) || text(block.id),
        type: text(block.type) === "IMAGE" ? "IMAGE" : "HTML",
        htmlContent: text(block.htmlContent),
        productImageId: text(block.productImageId),
        sortOrder: integer(block.sortOrder, 0),
        altText: text(block.altText),
      };
    }),
    notice: {
      productInfoNotice: text(noticeValue.productInfoNotice),
      shippingInfo: text(noticeValue.shippingInfo),
      asInfo: text(noticeValue.asInfo),
      returnExchangeInfo: text(noticeValue.returnExchangeInfo),
      noticeRows: array(noticeValue.noticeRows).map((rowValue) => {
        const row = record(rowValue);
        return { label: text(row.label), value: text(row.value) };
      }),
    },
    createdAt: nullableText(item.createdAt),
    updatedAt: nullableText(item.updatedAt),
  };
}

export function normalizeAdminProductReview(value: unknown): AdminProductReview {
  const item = record(value);
  const noticeValue = record(item.productNotice ?? item.notice);
  return {
    id: text(item.productId) || text(item.id),
    version: integer(item.version, 0),
    supplierId: text(item.supplierId),
    supplierName: text(item.supplierName) || "공급처",
    name: text(item.name),
    summary: text(item.summary),
    sourcePrice: integer(item.sourcePrice, 0),
    basePrice: integer(item.basePrice, 0),
    minimumOrderQuantity: integer(item.minimumOrderQuantity, 1),
    orderQuantityStep: integer(item.orderQuantityStep, 1),
    categoryCode: text(item.categoryCode),
    productStatus: text(item.status),
    reviewStatus: text(item.reviewStatus) || "UNKNOWN",
    complianceStatus: text(item.complianceStatus),
    reviewReasonCode: nullableText(item.reviewReasonCode),
    supplierReviewMessage: nullableText(item.supplierReviewMessage),
    firstSubmittedAt: nullableText(item.firstSubmittedAt),
    options: array(item.options).map((optionValue) => {
      const option = record(optionValue);
      return {
        id: text(option.id) || text(option.optionId),
        name: text(option.name) || "기본",
        additionalPrice: integer(option.additionalPrice, 0),
        status: text(option.status),
        sourceOptionCode: text(option.sourceOptionCode),
        sourceAdditionalPrice: integer(option.sourceAdditionalPrice, 0),
        sortOrder: integer(option.sortOrder, 0),
      };
    }),
    images: array(item.images).map((imageValue) => {
      const image = record(imageValue);
      const type = text(image.type);
      return {
        id: text(image.id) || text(image.imageId),
        type: type === "THUMBNAIL" || type === "GALLERY" || type === "DETAIL" ? type : "UNKNOWN",
        imageUrl: safeAdminImageUrl(image.imageUrl),
        sortOrder: integer(image.sortOrder, 0),
        altText: text(image.altText),
      };
    }),
    detailBlocks: array(item.detailBlocks).map((blockValue) => {
      const block = record(blockValue);
      const type = text(block.type);
      return {
        id: text(block.id) || text(block.blockId),
        type: type === "HTML" || type === "IMAGE" ? type : "UNKNOWN",
        imageUrl: safeAdminImageUrl(block.imageUrl),
        htmlContent: text(block.htmlContent),
        sortOrder: integer(block.sortOrder, 0),
        altText: text(block.altText),
      };
    }),
    notice: {
      productInfoNotice: text(noticeValue.productInfoNotice),
      shippingInfo: text(noticeValue.shippingInfo),
      asInfo: text(noticeValue.asInfo),
      returnExchangeInfo: text(noticeValue.returnExchangeInfo),
      noticeRows: array(noticeValue.noticeRows).map((rowValue) => {
        const row = record(rowValue);
        return { label: text(row.label), value: text(row.value) };
      }),
    },
  };
}

function safeAdminImageUrl(value: unknown) {
  const url = text(value).trim();
  if (url.startsWith("/uploads/products/") && !url.startsWith("//")) return url;
  try {
    const parsed = new URL(url);
    const isHttp = parsed.protocol === "http:" || parsed.protocol === "https:";
    return isHttp && !parsed.username && !parsed.password ? parsed.toString() : null;
  } catch {
    return null;
  }
}

async function request(path: string, init: RequestInit = {}) {
  const response = await requestResponse(path, init);
  if (response.status === 204) return null;
  return response.json() as Promise<unknown>;
}

async function requestResponse(path: string, init: RequestInit = {}) {
  const isForm = typeof FormData !== "undefined" && init.body instanceof FormData;
  const response = await fetch(path, {
    ...init,
    credentials: "same-origin",
    cache: "no-store",
    headers: {
      Accept: "application/json",
      ...(!isForm && init.body ? { "Content-Type": "application/json" } : {}),
      ...init.headers,
    },
  });

  if (!response.ok) {
    let code = "";
    try {
      const body = record(await response.json());
      code = text(body.code);
    } catch {
      code = "";
    }
    throw new SupplierProductApiError(response.status, code);
  }
  return response;
}

async function deleteVersionedResource(path: string, version: number) {
  const response = await requestResponse(path, {
    method: "DELETE",
    headers: { "If-Match": `"${version}"` },
  });
  const etag = response.headers.get("etag")?.replace(/^W\//, "").replaceAll('"', "") ?? "";
  const nextVersion = Number(etag);
  return Number.isInteger(nextVersion) && nextVersion >= 0 ? nextVersion : null;
}

function mutation(value: unknown) {
  const item = record(value);
  return {
    productId: text(item.productId) || text(item.id),
    version: integer(item.version ?? item.productVersion, -1),
  };
}

function collection(value: unknown) {
  const wrapper = record(value);
  return Array.isArray(value)
    ? value
    : array(wrapper.products ?? wrapper.reviews ?? wrapper.items ?? wrapper.content);
}

function isReviewReason(value: string | null): value is SupplierReviewReasonCode {
  return value !== null && Object.prototype.hasOwnProperty.call(REASON_LABELS, value);
}

function safeSingleLine(value: unknown, maxLength: number) {
  if (typeof value !== "string") return null;
  const result = value.replace(/[\r\n\t]+/g, " ").replace(/\s+/g, " ").trim().slice(0, maxLength);
  return result || null;
}

function record(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

function array(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function text(value: unknown) {
  return typeof value === "string" ? value : "";
}

function nullableText(value: unknown) {
  const result = text(value);
  return result || null;
}

function integer(value: unknown, fallback: number) {
  return typeof value === "number" && Number.isInteger(value) ? value : fallback;
}
