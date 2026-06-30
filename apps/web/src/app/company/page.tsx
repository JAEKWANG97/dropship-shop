import Link from "next/link";
import { BUSINESS_PROFILE } from "@/lib/legal";

export default function CompanyPage() {
  return (
    <section className="legal-page">
      <div className="section-heading">
        <p className="eyebrow">Company</p>
        <h1>회사 정보</h1>
        <p>코어블SAF 운영 및 소비자 고지 정보입니다.</p>
      </div>
      <section className="detail-section">
        <h2>사업자 정보</h2>
        <dl className="notice-list">
          <Info label="상호" value={BUSINESS_PROFILE.companyName} />
          <Info label="대표자명" value={BUSINESS_PROFILE.representativeName} />
          <Info label="사업자등록번호" value={BUSINESS_PROFILE.businessRegistrationNumber} />
          <Info label="통신판매업 신고번호" value={BUSINESS_PROFILE.mailOrderSalesRegistrationNumber} />
          <Info label="통신판매업 신고 기관" value={BUSINESS_PROFILE.mailOrderSalesRegistrationAuthority} />
          <Info label="사업장 주소" value={BUSINESS_PROFILE.businessAddress} />
          <Info label="반품 주소" value={BUSINESS_PROFILE.returnAddress} />
          <Info label="호스팅 제공자" value={BUSINESS_PROFILE.hostingProvider} />
        </dl>
      </section>
      <section className="detail-section">
        <h2>고객 지원</h2>
        <dl className="notice-list">
          <Info label="문의 접수" value="사이트 고객 문의 페이지" />
          <Info label="고객센터 전화번호" value={BUSINESS_PROFILE.customerCenterPhone} />
          <Info label="고객센터 이메일" value={BUSINESS_PROFILE.customerCenterEmail} />
          <Info label="운영 시간" value={BUSINESS_PROFILE.customerCenterHours} />
          <Info label="개인정보 보호책임자" value={BUSINESS_PROFILE.privacyOfficerName} />
          <Info label="개인정보 문의" value={BUSINESS_PROFILE.privacyOfficerEmail} />
        </dl>
        <div className="action-row">
          <Link className="button primary" href="/support">
            고객 문의하기
          </Link>
          <Link className="button" href="/policies/privacy">
            개인정보처리방침
          </Link>
        </div>
      </section>
      <section className="detail-section">
        <h2>결제/구매안전 안내</h2>
        <p>{BUSINESS_PROFILE.purchaseSafetyNotice}</p>
      </section>
    </section>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}
