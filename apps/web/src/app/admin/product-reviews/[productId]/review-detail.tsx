"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { categoryPath } from "@/lib/categories";
import {
  adminReviewStatusLabel,
  decideAdminProductReview,
  getAdminProductReview,
  productActionError,
  type AdminProductReview,
} from "@/lib/supplier-products";
import { ProductImage } from "@/app/products/product-image";
import { AdminReviewFacts, AdminReviewImages } from "./review-presentation";

type ReviewAction = "approve" | "supplement" | "reject";

export function AdminProductReviewDetail({ productId }: { productId: string }) {
  const router = useRouter();
  const [review, setReview] = useState<AdminProductReview | null>(null);
  const [failed, setFailed] = useState(false);
  const [saving, setSaving] = useState<ReviewAction | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getAdminProductReview(productId)
      .then((value) => active && setReview(value))
      .catch(() => active && setFailed(true));
    return () => { active = false; };
  }, [productId]);

  async function decide(event: React.FormEvent<HTMLFormElement>, action: ReviewAction) {
    event.preventDefault();
    if (!review || saving) return;
    const form = new FormData(event.currentTarget);
    const body: Record<string, unknown> = {
      expectedVersion: review.version,
      internalReason: value(form, "internalReason"),
    };
    if (action !== "approve") {
      body.reviewReasonCode = action === "supplement" ? "SUPPLEMENT_REQUIRED" : "REJECTED_POLICY";
      body.supplierReviewMessage = value(form, "supplierReviewMessage");
    }
    setSaving(action);
    setMessage(null);
    try {
      await decideAdminProductReview(productId, action, body);
      router.push("/admin/product-reviews");
      router.refresh();
    } catch (error) {
      setMessage(productActionError(error));
      if (error instanceof Error && "code" in error && error.code === "PRODUCT_VERSION_CONFLICT") {
        getAdminProductReview(productId).then(setReview).catch(() => setFailed(true));
      }
    } finally {
      setSaving(null);
    }
  }

  if (failed) return <div className="admin-page"><div className="notice danger">검토 정보를 불러오지 못했습니다.</div></div>;
  if (!review) return <div className="admin-page"><div className="notice">검토 정보를 불러오는 중입니다.</div></div>;

  const reviewable = review.reviewStatus === "REVIEW_REQUIRED";
  const statusTone = review.reviewStatus === "APPROVED"
    ? "success"
    : review.reviewStatus === "REJECTED"
      ? "danger"
      : "warning";
  return (
    <div className="admin-page">
      <div className="admin-heading">
        <div><Link className="admin-text-link" href="/admin/product-reviews">상품 검토 목록</Link><h1>{review.name}</h1><p>불러온 버전 {review.version}을 기준으로 한 번만 처리합니다.</p></div>
        <span className={`admin-badge ${statusTone}`}>{adminReviewStatusLabel(review.reviewStatus)}</span>
      </div>
      {message ? <div className="notice danger" role="alert"><strong>처리하지 못했습니다</strong><span>{message}</span></div> : null}

      <div className="admin-inquiry-detail-grid">
        <section className="admin-panel">
          <div className="admin-panel-head"><h2>기본 정보</h2><span>v{review.version}</span></div>
          <dl className="summary-list">
            <Row label="공급처" value={review.supplierName} />
            <Row label="카테고리" value={categoryPath(review.categoryCode)} />
            <Row label="요약" value={review.summary} />
            <Row label="공급가" value={`${review.sourcePrice.toLocaleString("ko-KR")}원`} />
            <Row label="계산 판매가" value={`${review.basePrice.toLocaleString("ko-KR")}원`} />
            <Row label="주문수량" value={`최소 ${review.minimumOrderQuantity} / ${review.orderQuantityStep}개 단위`} />
          </dl>
          <AdminReviewFacts review={review} />
        </section>
        <section className="admin-panel">
          <div className="admin-panel-head"><h2>옵션</h2><span>{review.options.length}개</span></div>
          <dl className="summary-list">
            {review.options.map((option) => <Row key={option.id} label={option.name} value={`추가 공급가 ${option.sourceAdditionalPrice.toLocaleString("ko-KR")}원 · 코드 ${option.sourceOptionCode || "-"}`} />)}
          </dl>
        </section>
      </div>

      <section className="admin-panel">
        <div className="admin-panel-head"><h2>전체 상품 이미지</h2><span>대표·갤러리·상세 {review.images.length}개</span></div>
        <AdminReviewImages images={review.images} />
      </section>

      <div className="admin-inquiry-detail-grid">
        <section className="admin-panel">
          <div className="admin-panel-head"><h2>상세 설명</h2><span>HTML은 텍스트로 검토</span></div>
          {review.detailBlocks.map((block) => block.type === "HTML"
            ? <pre className="admin-review-code" key={block.id}>{block.htmlContent}</pre>
            : block.type === "IMAGE"
              ? <div className="admin-review-detail-image" key={block.id}>
                <ProductImage alt={block.altText || "상세 이미지"} className="admin-review-image" src={block.imageUrl} />
                {block.imageUrl ? <a href={block.imageUrl} rel="noopener noreferrer" target="_blank">상세 이미지 안전하게 열기</a> : <span>이미지 URL 확인 필요</span>}
              </div>
              : <p key={block.id}>알 수 없는 상세 블록</p>)}
          {review.detailBlocks.length === 0 ? <p>상세 설명 없음</p> : null}
        </section>
        <section className="admin-panel">
          <div className="admin-panel-head"><h2>상품 고시</h2><span>구조화 정보</span></div>
          <dl className="summary-list">
            <Row label="상품정보" value={review.notice.productInfoNotice} />
            <Row label="배송" value={review.notice.shippingInfo} />
            <Row label="A/S" value={review.notice.asInfo} />
            <Row label="반품·교환" value={review.notice.returnExchangeInfo} />
            {review.notice.noticeRows.map((row, index) => <Row key={`${row.label}-${index}`} label={row.label} value={row.value} />)}
          </dl>
        </section>
      </div>

      {reviewable ? (
        <div className="admin-review-actions">
          <ReviewForm action="approve" button="승인" pending={saving} onSubmit={decide} />
          <ReviewForm action="supplement" button="보완 요청" pending={saving} onSubmit={decide} supplierMessage />
          <ReviewForm action="reject" button="등록 거절" pending={saving} onSubmit={decide} supplierMessage />
        </div>
      ) : <div className="notice"><strong>이미 처리된 상품입니다</strong><span>최신 검토 대기 목록으로 돌아가 주세요.</span></div>}
    </div>
  );
}

function ReviewForm({ action, button, pending, supplierMessage = false, onSubmit }: {
  action: ReviewAction;
  button: string;
  pending: ReviewAction | null;
  supplierMessage?: boolean;
  onSubmit: (event: React.FormEvent<HTMLFormElement>, action: ReviewAction) => void;
}) {
  return (
    <form className="admin-form admin-panel" onSubmit={(event) => onSubmit(event, action)}>
      <h2>{button}</h2>
      {supplierMessage ? <label>공급처 안내<input maxLength={500} name="supplierReviewMessage" required /></label> : null}
      <label>내부 처리 사유<input maxLength={500} name="internalReason" required /></label>
      <button className={action === "approve" ? "button primary" : "button"} disabled={pending !== null} type="submit">{pending === action ? "처리 중..." : button}</button>
    </form>
  );
}

function Row({ label, value }: { label: string; value: string | null }) {
  return <div><dt>{label}</dt><dd>{value || "-"}</dd></div>;
}

function value(form: FormData, name: string) {
  const item = form.get(name);
  return typeof item === "string" ? item.trim() : "";
}
