import type { Metadata } from "next";
import Link from "next/link";
import { BUSINESS_PROFILE, POLICY_PAGES } from "@/lib/legal";
import { getAdminUser, getCurrentUser } from "@/lib/session";
import "./globals.css";

export const metadata: Metadata = {
  title: "코어블SAF",
  description: "건설 안전용품 쇼핑몰",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const [session, admin] = await Promise.all([getCurrentUser(), getAdminUser()]);

  return (
    <html lang="ko">
      <body>
        <div className="site-utility">
          <nav aria-label="Utility navigation">
            <Link href="/products">상품목록</Link>
            <Link href="/support">고객문의</Link>
            <Link href="/company">회사정보</Link>
            {admin ? <Link className="admin-entry" href="/admin">운영관리</Link> : null}
          </nav>
        </div>
        <header className="site-header">
          <div className="site-header-main">
            <Link href="/" className="brand" aria-label="코어블SAF home">
              <strong>
                <span>코어블</span>
                <em>SAF</em>
              </strong>
              <small>건설 안전용품 쇼핑몰</small>
            </Link>
            <form action="/products" className="site-search">
              <input
                aria-label="상품 검색"
                name="q"
                placeholder="상품명, 브랜드, 키워드로 검색하세요"
              />
              <button aria-label="검색" type="submit">
                <svg aria-hidden="true" viewBox="0 0 24 24">
                  <path d="m21 21-4.3-4.3" />
                  <circle cx="11" cy="11" r="7" />
                </svg>
              </button>
            </form>
            <nav className="site-nav" aria-label="Primary navigation">
              <Link href="/cart">장바구니</Link>
              <Link href="/orders">주문조회</Link>
              {session ? (
                <>
                  <Link href="/account">내 계정</Link>
                  <form action="/auth/logout" method="post">
                    <button type="submit">로그아웃</button>
                  </form>
                </>
              ) : (
                <>
                  <Link href="/account">내 계정</Link>
                  <Link href="/login">로그인</Link>
                </>
              )}
            </nav>
          </div>
        </header>
        <main className="page-shell">{children}</main>
        <footer className="site-footer">
          <div>
            <strong>{BUSINESS_PROFILE.brandName}</strong>
            <span>건설 안전용품 쇼핑몰</span>
            <span>상호 {BUSINESS_PROFILE.companyName}</span>
            <span>대표 {BUSINESS_PROFILE.representativeName}</span>
            <span>사업자등록번호 {BUSINESS_PROFILE.businessRegistrationNumber}</span>
            <span>통신판매업 신고번호 {BUSINESS_PROFILE.mailOrderSalesRegistrationNumber}</span>
            <span>주소 {BUSINESS_PROFILE.businessAddress}</span>
          </div>
          <div>
            <strong>고객 지원</strong>
            <Link href="/support">고객 문의 접수</Link>
            <Link href="/company">회사 정보</Link>
            <span>반품 주소 {BUSINESS_PROFILE.returnAddress}</span>
            <span>{BUSINESS_PROFILE.purchaseSafetyNotice}</span>
          </div>
          <div>
            <strong>거래 정책</strong>
            <div className="footer-links">
              {POLICY_PAGES.map((policy) => (
                <Link href={`/policies/${policy.slug}`} key={policy.slug}>
                  {policy.title}
                </Link>
              ))}
            </div>
          </div>
        </footer>
      </body>
    </html>
  );
}
