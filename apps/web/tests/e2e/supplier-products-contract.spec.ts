import { expect, test } from "@playwright/test";
import {
  AdminReviewFacts,
  AdminReviewImages,
} from "../../src/app/admin/product-reviews/[productId]/review-presentation";
import {
  deleteSupplierImage,
  deleteSupplierOption,
  isProductVersionError,
  normalizeAdminProductReview,
  normalizeSupplierProduct,
  orderSupplierImages,
  productActionError,
  replaceSupplierDetailBlocks,
  supplierDetailBlocksWithoutImage,
  supplierStatusView,
  SupplierProductApiError,
  type SupplierProductDetailBlock,
  type SupplierProductImage,
} from "../../src/lib/supplier-products";

test("supplier product statuses expose only documented safe actions", () => {
  const cases = [
    ["EDITING", "EDIT_AND_RESUBMIT", null, true, "작성 중"],
    ["APPROVED", "NONE", null, true, "등록 승인"],
    ["UNDER_REVIEW", "WAIT", "CATEGORY_REVIEW", true, "Coreable 검토 중"],
    ["CHANGES_REQUESTED", "EDIT_AND_RESUBMIT", "SUPPLEMENT_REQUIRED", true, "보완 필요"],
    ["REJECTED", "CONTACT_COREABLE", "REJECTED_POLICY", false, "등록 불가"],
    ["PAUSED_BY_COREABLE", "CONTACT_COREABLE", null, false, "판매 보류"],
  ] as const;

  for (const [supplierDisplayStatus, nextAction, reviewReasonCode, editable, label] of cases) {
    const view = supplierStatusView({ supplierDisplayStatus, nextAction, reviewReasonCode, reviewMessage: null });
    expect(view).toMatchObject({ editable, label });
  }

  const approved = supplierStatusView({
    supplierDisplayStatus: "APPROVED",
    nextAction: "NONE",
    reviewReasonCode: null,
    reviewMessage: null,
  });
  expect(approved.editWarning).toContain("일시 비공개");
  expect(approved.editWarning).toContain("다시 분류·검토");

  const paused = supplierStatusView({
    supplierDisplayStatus: "PAUSED_BY_COREABLE",
    nextAction: "CONTACT_COREABLE",
    reviewReasonCode: null,
    reviewMessage: "internal hold reason",
  });
  expect(paused).toMatchObject({ editable: false, editWarning: null, message: null });
});

test("unknown or mismatched statuses fail closed without echoing server values", () => {
  const unknown = supplierStatusView({
    supplierDisplayStatus: "INTERNAL_HOLD_WITH_NOTE",
    nextAction: "EDIT_AND_RESUBMIT",
    reviewReasonCode: "SECRET_RULE",
    reviewMessage: "internal note",
  });
  const mismatch = supplierStatusView({
    supplierDisplayStatus: "APPROVED",
    nextAction: "EDIT_AND_RESUBMIT",
    reviewReasonCode: null,
    reviewMessage: "should not render",
  });

  expect(unknown).toMatchObject({ label: "상태 확인 필요", editable: false, message: null });
  expect(mismatch).toMatchObject({ label: "상태 확인 필요", editable: false, message: null });

  const mismatchedReason = supplierStatusView({
    supplierDisplayStatus: "CHANGES_REQUESTED",
    nextAction: "EDIT_AND_RESUBMIT",
    reviewReasonCode: "CATEGORY_REVIEW",
    reviewMessage: "raw message",
  });
  expect(mismatchedReason).toMatchObject({ label: "상태 확인 필요", editable: false, message: null });

  const terminalReasonInReview = supplierStatusView({
    supplierDisplayStatus: "UNDER_REVIEW",
    nextAction: "WAIT",
    reviewReasonCode: "REJECTED_POLICY",
    reviewMessage: "raw message",
  });
  expect(terminalReasonInReview).toMatchObject({ label: "상태 확인 필요", editable: false, message: null });
});

test("response normalization never defaults unknown state to editable and keeps server delete guards", () => {
  const product = normalizeSupplierProduct({
    id: "product-1",
    version: 3,
    supplierDisplayStatus: "",
    nextAction: "",
    images: [
      { id: "image-1", type: "THUMBNAIL", imageUrl: "/uploads/products/one.png", deletable: false },
      { id: "image-2", type: "GALLERY", imageUrl: "/uploads/products/two.png", deletable: true },
    ],
    options: [
      { id: "option-1", name: "기본", deletable: false },
      { id: "option-2", name: "대형", deletable: true },
    ],
  });

  expect(supplierStatusView(product).editable).toBe(false);
  expect(product.images.map((image) => image.deletable)).toEqual([false, true]);
  expect(product.options.map((option) => option.deletable)).toEqual([false, true]);
});

test("admin review presentation renders every allowlisted reason family and compliance state", () => {
  const reasonCases = [
    ["CATEGORY_REVIEW", "카테고리 확인"],
    ["CERTIFICATION_REVIEW", "인증 정보 확인"],
    ["SAFETY_REVIEW", "안전 기준 확인"],
    ["REQUIRED_INFO_MISSING", "필수 정보 확인"],
    ["SUPPLEMENT_REQUIRED", "정보 보완 요청"],
    ["REJECTED_POLICY", "등록 정책 미충족"],
  ] as const;

  for (const [reviewReasonCode, label] of reasonCases) {
    const review = normalizeAdminProductReview({
      productId: "product-1",
      reviewStatus: "REVIEW_REQUIRED",
      reviewReasonCode,
      complianceStatus: "PENDING",
    });
    const rendered = renderedText(AdminReviewFacts({ review }));
    expect(rendered).toContain(reviewReasonCode);
    expect(rendered).toContain(label);
    expect(rendered).toContain("PENDING");
    expect(rendered).toContain("인증 검토 대기");
  }

  const complianceCases = [
    ["PENDING", "인증 검토 대기"],
    ["NOT_REQUIRED", "인증 확인 불필요"],
    ["VERIFIED", "인증 확인 완료"],
    ["REJECTED", "인증 기준 미충족"],
  ] as const;
  for (const [complianceStatus, label] of complianceCases) {
    const review = normalizeAdminProductReview({
      productId: "product-1",
      reviewStatus: "REVIEW_REQUIRED",
      reviewReasonCode: "CERTIFICATION_REVIEW",
      complianceStatus,
    });
    const rendered = renderedText(AdminReviewFacts({ review }));
    expect(rendered).toContain(complianceStatus);
    expect(rendered).toContain(label);
  }

  const unknown = normalizeAdminProductReview({ productId: "product-2" });
  expect(unknown.reviewStatus).toBe("UNKNOWN");
});

test("admin review image presentation previews thumbnail gallery and detail with protocol-safe links", () => {
  const review = normalizeAdminProductReview({
    productId: "product-1",
    images: [
      { id: "thumb", type: "THUMBNAIL", imageUrl: "/uploads/products/thumb.png", sortOrder: 0, altText: "대표" },
      { id: "gallery", type: "GALLERY", imageUrl: "https://cdn.example.com/gallery.webp", sortOrder: 1, altText: "갤러리" },
      { id: "detail", type: "DETAIL", imageUrl: "/uploads/products/detail.jpg", sortOrder: 2, altText: "상세" },
      { id: "unsafe", type: "DETAIL", imageUrl: "javascript:alert(1)", sortOrder: 3, altText: "차단" },
    ],
  });
  const rendered = AdminReviewImages({ images: review.images });
  const nodes = renderedNodes(rendered);
  const cards = nodes.filter((node) => node.type === "figure");
  const previews = nodes.filter((node) => node.props.className === "admin-review-image");
  const links = nodes.filter((node) => node.type === "a");

  expect(cards.map((node) => node.props["data-image-type"])).toEqual(["THUMBNAIL", "GALLERY", "DETAIL", "DETAIL"]);
  expect(previews).toHaveLength(4);
  expect(previews.map((node) => node.props.src)).toEqual([
    "/uploads/products/thumb.png",
    "https://cdn.example.com/gallery.webp",
    "/uploads/products/detail.jpg",
    null,
  ]);
  expect(links).toHaveLength(3);
  expect(links.every((node) => node.props.rel === "noopener noreferrer" && node.props.target === "_blank")).toBe(true);
  expect(renderedText(rendered)).toContain("허용되지 않은 이미지 URL");
  expect(renderedText(rendered)).not.toContain("javascript:");
});

test("option and image delete helpers send aggregate If-Match and preserve the returned version", async () => {
  const originalFetch = globalThis.fetch;
  const calls: Array<{ path: string; init?: RequestInit }> = [];
  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    calls.push({ path: String(input), init });
    return new Response(null, { status: 204, headers: { ETag: '"8"' } });
  }) as typeof fetch;

  try {
    await expect(deleteSupplierOption("product-1", "option-1", 7)).resolves.toBe(8);
    await expect(deleteSupplierImage("product-1", "image-1", 7)).resolves.toBe(8);
  } finally {
    globalThis.fetch = originalFetch;
  }

  expect(calls.map((call) => [call.path, call.init?.method])).toEqual([
    ["/api/supplier/products/product-1/options/option-1", "DELETE"],
    ["/api/supplier/products/product-1/images/image-1", "DELETE"],
  ]);
  for (const call of calls) {
    expect(new Headers(call.init?.headers).get("If-Match")).toBe('"7"');
    expect(call.init?.body).toBeUndefined();
  }
});

test("thumbnail reorder sends every presentation image once and excludes detail images", async () => {
  const originalFetch = globalThis.fetch;
  let requestBody: unknown;
  globalThis.fetch = (async (_input: RequestInfo | URL, init?: RequestInit) => {
    requestBody = JSON.parse(String(init?.body));
    return new Response(JSON.stringify({ productId: "product-1", version: 8 }), {
      status: 200,
      headers: { "Content-Type": "application/json", ETag: '"8"' },
    });
  }) as typeof fetch;
  const images: SupplierProductImage[] = [
    supplierImage("gallery", "THUMBNAIL", 0),
    supplierImage("old-thumb", "GALLERY", 1),
    supplierImage("detail", "DETAIL", 2),
  ];

  try {
    await expect(orderSupplierImages("product-1", images, 7)).resolves.toEqual({ productId: "product-1", version: 8 });
  } finally {
    globalThis.fetch = originalFetch;
  }

  expect(requestBody).toEqual({
    expectedVersion: 7,
    images: [
      { imageId: "gallery", type: "THUMBNAIL", sortOrder: 0, altText: "gallery" },
      { imageId: "old-thumb", type: "GALLERY", sortOrder: 1, altText: "old-thumb" },
    ],
  });
});

test("detail image unlink advances the version before If-Match delete and stale retry requires refresh", async () => {
  const blocks: SupplierProductDetailBlock[] = [
    supplierDetailBlock("html", "HTML", 0),
    supplierDetailBlock("linked", "IMAGE", 1, "detail-image"),
    supplierDetailBlock("kept", "IMAGE", 2, "other-image"),
  ];
  const remaining = supplierDetailBlocksWithoutImage(blocks, "detail-image");
  expect(remaining).toEqual([
    { type: "HTML", htmlContent: "설명", sortOrder: 0, altText: undefined },
    { type: "IMAGE", productImageId: "other-image", sortOrder: 1, altText: "kept" },
  ]);

  const originalFetch = globalThis.fetch;
  const calls: Array<{ path: string; init?: RequestInit }> = [];
  let staleError: unknown;
  try {
    globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
      calls.push({ path: String(input), init });
      if (init?.method === "PUT") {
        return new Response(JSON.stringify({ productId: "product-1", version: 8 }), {
          status: 200,
          headers: { "Content-Type": "application/json", ETag: '"8"' },
        });
      }
      return new Response(null, { status: 204, headers: { ETag: '"9"' } });
    }) as typeof fetch;

    const unlinked = await replaceSupplierDetailBlocks("product-1", remaining, 7);
    expect(unlinked.version).toBe(8);
    await expect(deleteSupplierImage("product-1", "detail-image", unlinked.version)).resolves.toBe(9);

    globalThis.fetch = (async () => new Response(JSON.stringify({ code: "PRODUCT_VERSION_CONFLICT" }), {
      status: 409,
      headers: { "Content-Type": "application/json" },
    })) as typeof fetch;
    try {
      await deleteSupplierImage("product-1", "detail-image", 7);
    } catch (error) {
      staleError = error;
    }
  } finally {
    globalThis.fetch = originalFetch;
  }

  expect(JSON.parse(String(calls[0].init?.body))).toEqual({ expectedVersion: 7, detailBlocks: remaining });
  expect(new Headers(calls[1].init?.headers).get("If-Match")).toBe('"8"');
  expect(staleError).toMatchObject({ status: 409, code: "PRODUCT_VERSION_CONFLICT" });
  expect(productActionError(staleError)).toContain("최신 내용을 다시 불러온 뒤 다시 시도");
});

test("stale 409 and 412 errors require refresh before retry", () => {
  const conflict = new SupplierProductApiError(409, "PRODUCT_VERSION_CONFLICT");
  const precondition = new SupplierProductApiError(412, "PRECONDITION_FAILED");

  for (const error of [conflict, precondition]) {
    expect(isProductVersionError(error)).toBe(true);
    expect(productActionError(error)).toBe("다른 변경이 먼저 저장되었습니다. 최신 내용을 다시 불러온 뒤 다시 시도해 주세요.");
  }
  expect(isProductVersionError(new SupplierProductApiError(409, "CONFLICT"))).toBe(false);
});

function supplierImage(id: string, type: SupplierProductImage["type"], sortOrder: number): SupplierProductImage {
  return {
    id,
    type,
    imageUrl: `/uploads/products/${id}.png`,
    sortOrder,
    altText: id,
    deletable: true,
  };
}

function supplierDetailBlock(
  id: string,
  type: SupplierProductDetailBlock["type"],
  sortOrder: number,
  productImageId = "",
): SupplierProductDetailBlock {
  return {
    id,
    type,
    htmlContent: type === "HTML" ? "설명" : "",
    productImageId,
    sortOrder,
    altText: type === "IMAGE" ? id : "",
  };
}

type RenderedNode = {
  type: unknown;
  props: Record<string, unknown>;
};

function renderedNodes(value: unknown): RenderedNode[] {
  if (Array.isArray(value)) return value.flatMap(renderedNodes);
  if (!value || typeof value !== "object") return [];
  const candidate = value as { __pw_type?: unknown; type?: unknown; props?: unknown };
  if (!candidate.__pw_type || !candidate.props || typeof candidate.props !== "object") return [];
  const node = { type: candidate.type, props: candidate.props as Record<string, unknown> };
  return [node, ...renderedNodes(node.props.children)];
}

function renderedText(value: unknown): string {
  if (typeof value === "string" || typeof value === "number") return String(value);
  if (Array.isArray(value)) return value.map(renderedText).join(" ");
  if (!value || typeof value !== "object") return "";
  const candidate = value as { __pw_type?: unknown; props?: unknown };
  if (!candidate.__pw_type || !candidate.props || typeof candidate.props !== "object") return "";
  const props = candidate.props as Record<string, unknown>;
  return [props.label, props.value, props.children].map(renderedText).join(" ");
}
