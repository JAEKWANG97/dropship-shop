import Link from "next/link";
import { BUSINESS_PROFILE } from "@/lib/legal";
import { createCustomerInquiry } from "./actions";

type SupportPageProps = {
  searchParams: Promise<{ message?: string }>;
};

export default async function SupportPage({ searchParams }: SupportPageProps) {
  const query = await searchParams;

  return (
    <section className="legal-page">
      <div className="section-heading">
        <p className="eyebrow">Support</p>
        <h1>고객 문의</h1>
        <p>주문, 배송, 반품, 교환, 환불 문의를 사이트에서 접수합니다.</p>
      </div>

      {query.message ? (
        <div className="notice">
          <strong>알림</strong>
          <span>{query.message}</span>
        </div>
      ) : null}

      <section className="detail-section">
        <h2>문의 접수</h2>
        <form action={createCustomerInquiry} className="support-form">
          <label>
            이름
            <input name="customerName" required maxLength={100} />
          </label>
          <label>
            이메일
            <input name="email" type="email" required maxLength={320} />
          </label>
          <label>
            연락처
            <input name="phone" maxLength={50} placeholder="선택 입력" />
          </label>
          <label>
            제목
            <input name="subject" required maxLength={200} />
          </label>
          <label className="wide">
            문의 내용
            <textarea name="message" required maxLength={2000} rows={8} />
          </label>
          <label className="support-consent wide">
            <input type="checkbox" required />
            문의 처리를 위해 입력한 개인정보를 수집·이용하는 데 동의합니다.
          </label>
          <div className="admin-form-actions wide">
            <button className="button primary" type="submit">
              문의 접수
            </button>
          </div>
        </form>
      </section>

      <section className="detail-section">
        <h2>운영 안내</h2>
        <p>반품 주소는 {BUSINESS_PROFILE.returnAddress} 입니다.</p>
        <p>{BUSINESS_PROFILE.purchaseSafetyNotice}</p>
        <Link className="admin-text-link" href="/company">
          회사 정보 보기
        </Link>
      </section>
    </section>
  );
}
