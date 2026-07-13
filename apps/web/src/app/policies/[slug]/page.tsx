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
        <p className="eyebrow">정책</p>
        <h1>{policy.title}</h1>
        <p>{policy.summary}</p>
        <span>
          버전 {policy.version} · 시행일 {policy.effectiveDate}
        </span>
      </div>
      <div className="legal-notice">
        정책 내용이 변경되면 시행일과 함께 이 페이지에서 안내합니다.
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
