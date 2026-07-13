import type { Metadata } from "next";
import { InquiryLookup } from "./inquiry-lookup";

export const metadata: Metadata = {
  title: "문의 처리 현황 | 코어블SAF",
  robots: { index: false, follow: false },
};

export default async function InquiryLookupPage({ params }: { params: Promise<{ inquiryId: string }> }) {
  const { inquiryId } = await params;

  return (
    <section className="narrow-page inquiry-lookup-page">
      <p className="eyebrow">고객지원</p>
      <h1>문의 처리 현황</h1>
      <InquiryLookup inquiryId={inquiryId} />
    </section>
  );
}
