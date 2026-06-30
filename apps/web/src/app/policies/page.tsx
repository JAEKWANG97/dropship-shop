import Link from "next/link";
import { POLICY_PAGES } from "@/lib/legal";

export default function PolicyIndexPage() {
  return (
    <section className="legal-page">
      <div className="section-heading">
        <p className="eyebrow">Policies</p>
        <h1>정책 안내</h1>
        <p>실제 운영 전 최종 검토가 필요한 정책 안내입니다.</p>
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
