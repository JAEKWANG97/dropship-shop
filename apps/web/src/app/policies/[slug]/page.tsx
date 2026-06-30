import Link from "next/link";
import { notFound } from "next/navigation";
import { getPolicyPage, POLICY_PAGES } from "@/lib/legal";

type PolicyPageProps = {
  params: Promise<{ slug: string }>;
};

export default async function PolicyPage({ params }: PolicyPageProps) {
  const { slug } = await params;
  const policy = getPolicyPage(slug);
  if (!policy) {
    notFound();
  }

  return (
    <article className="legal-page">
      <div className="section-heading">
        <p className="eyebrow">Policy</p>
        <h1>{policy.title}</h1>
        <p>{policy.summary}</p>
        <span>
          버전 {policy.version} · 시행일 {policy.effectiveDate}
        </span>
      </div>
      <div className="legal-notice">
        이 문서는 실제 운영 전 최종 검토가 필요한 정책 안내입니다. 실결제 오픈 전 약관, 개인정보처리방침, 결제/구매안전서비스 고지를 확정해야 합니다.
      </div>
      {policy.sections.map((section) => (
        <section className="detail-section" key={section.heading}>
          <h2>{section.heading}</h2>
          {section.paragraphs.map((paragraph) => (
            <p key={paragraph}>{paragraph}</p>
          ))}
        </section>
      ))}
      <nav className="policy-links" aria-label="관련 정책">
        {POLICY_PAGES.map((related) => (
          <Link href={`/policies/${related.slug}`} key={related.slug}>
            {related.title}
          </Link>
        ))}
      </nav>
    </article>
  );
}
