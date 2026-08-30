"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { categoryPath } from "@/lib/categories";
import {
  adminReviewReasonLabel,
  adminReviewStatusLabel,
  listAdminProductReviews,
  type AdminProductReview,
} from "@/lib/supplier-products";

export default function AdminProductReviewsPage() {
  const [reviews, setReviews] = useState<AdminProductReview[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    listAdminProductReviews()
      .then((value) => active && setReviews(value))
      .catch(() => active && setFailed(true))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, []);

  return (
    <div className="admin-page">
      <div className="admin-heading"><div><h1>상품 검토</h1><p>공급처 상품의 구조화된 정보와 현재 버전을 확인하고 처리합니다.</p></div></div>
      {failed ? <div className="notice danger"><strong>검토 목록을 불러오지 못했습니다</strong><span>API 서버와 관리자 권한을 확인해 주세요.</span></div> : null}
      <section className="admin-panel">
        <div className="admin-panel-head"><h2>검토 대기</h2><span>{loading ? "불러오는 중" : `${reviews.length}건`}</span></div>
        <div className="admin-inquiry-list">
          {reviews.map((review) => {
            return (
              <Link className="admin-inquiry-card" href={`/admin/product-reviews/${encodeURIComponent(review.id)}`} key={review.id}>
                <div><strong>{review.name || "이름 없는 상품"}</strong><span className="admin-badge warning">{adminReviewStatusLabel(review.reviewStatus)}</span></div>
                <dl>
                  <div><dt>공급처</dt><dd>{review.supplierName}</dd></div>
                  <div><dt>카테고리</dt><dd>{categoryPath(review.categoryCode)}</dd></div>
                  <div><dt>버전</dt><dd>{review.version}</dd></div>
                </dl>
                <p>{review.reviewReasonCode ? `${review.reviewReasonCode} · ${adminReviewReasonLabel(review.reviewReasonCode)}` : "사유 코드 확인 필요"}</p>
              </Link>
            );
          })}
          {!loading && !failed && reviews.length === 0 ? <div className="admin-empty compact"><strong>검토 대기 상품이 없습니다</strong><span>새 요청이 생기면 이 목록에 표시됩니다.</span></div> : null}
        </div>
      </section>
    </div>
  );
}
