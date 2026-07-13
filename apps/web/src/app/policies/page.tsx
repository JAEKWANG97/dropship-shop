import Link from "next/link";
import { POLICY_PAGES } from "@/lib/legal";

export default function PolicyIndexPage() {
  return (
    <section className="legal-page">
      <div className="section-heading">
        <p className="eyebrow">정책</p>
        <h1>정책 안내</h1>
        <p>주문, 배송, 개인정보 처리와 취소·환불 기준을 확인할 수 있습니다.</p>
      </div>
      <div className="legal-card-grid">
        {POLICY_PAGES.map((policy) => (
          <Link className="legal-card" href={`/policies/${policy.slug}`} key={policy.slug}>
            <strong>{policy.title}</strong>
            <span>{policy.summary}</span>
            <small>
              {policy.version} · 시행일 {policy.effectiveDate}
            </small>
          </Link>
        ))}
      </div>
    </section>
  );
}
