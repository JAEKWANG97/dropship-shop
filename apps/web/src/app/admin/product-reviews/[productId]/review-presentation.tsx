import { ProductImage } from "@/app/products/product-image";
import {
  adminComplianceLabel,
  adminReviewReasonLabel,
  type AdminProductReview,
  type AdminProductReviewImage,
} from "@/lib/supplier-products";

export function AdminReviewFacts({ review }: { review: AdminProductReview }) {
  return (
    <dl className="summary-list" data-testid="admin-review-facts">
      <Row
        label="검토 사유 코드"
        value={review.reviewReasonCode
          ? `${review.reviewReasonCode} · ${adminReviewReasonLabel(review.reviewReasonCode)}`
          : "사유 코드 없음"}
      />
      <Row
        label="인증 상태"
        value={review.complianceStatus
          ? `${review.complianceStatus} · ${adminComplianceLabel(review.complianceStatus)}`
          : "인증 상태 확인 필요"}
      />
    </dl>
  );
}

export function AdminReviewImages({ images }: { images: AdminProductReviewImage[] }) {
  if (images.length === 0) return <p>등록된 이미지 없음</p>;
  return (
    <div className="admin-review-image-grid" data-testid="admin-review-images">
      {[...images].sort((left, right) => left.sortOrder - right.sortOrder).map((image) => (
        <figure className="admin-review-image-card" data-image-type={image.type} key={image.id}>
          <ProductImage
            alt={image.altText || `${imageTypeLabel(image.type)} 이미지`}
            className="admin-review-image"
            src={image.imageUrl}
          />
          <figcaption>
            <strong>{image.type} · {imageTypeLabel(image.type)}</strong>
            <span>정렬 {image.sortOrder}</span>
            {image.imageUrl ? (
              <a href={image.imageUrl} rel="noopener noreferrer" target="_blank">원본 이미지 안전하게 열기</a>
            ) : <span>허용되지 않은 이미지 URL</span>}
          </figcaption>
        </figure>
      ))}
    </div>
  );
}

function imageTypeLabel(type: AdminProductReviewImage["type"]) {
  return ({
    THUMBNAIL: "대표",
    GALLERY: "갤러리",
    DETAIL: "상세",
    UNKNOWN: "유형 확인 필요",
  } as const)[type];
}

function Row({ label, value }: { label: string; value: string }) {
  return <div><dt>{label}</dt><dd>{value}</dd></div>;
}
