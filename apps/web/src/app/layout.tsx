import type { Metadata } from "next";
import Link from "next/link";
import { BUSINESS_PROFILE, POLICY_PAGES } from "@/lib/legal";
import { getAdminUser, getCurrentUser } from "@/lib/session";
import { CategoryMenu } from "./category-menu";
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
            <Link href="/products">상품 목록</Link>
            <Link href="/support">고객 문의</Link>
            <Link href="/company">회사 정보</Link>
            {admin ? <Link className="admin-entry" href="/admin">운영관리</Link> : null}
          </nav>
        </div>
        <header className="site-header">
          <div className="site-header-main">
            <CategoryMenu />
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
              <Link href="/cart" aria-label="장바구니">
                <svg className="site-nav-icon" aria-hidden="true" viewBox="0 0 24 24">
                  <path d="M3 4h2l2.2 10.5h9.9l2-7H6.1" />
                  <circle cx="9" cy="19" r="1.5" />
                  <circle cx="17" cy="19" r="1.5" />
                </svg>
                <span>장바구니</span>
              </Link>
              <Link href="/orders" aria-label="주문 조회">
                <svg className="site-nav-icon" aria-hidden="true" viewBox="0 0 24 24">
                  <path d="M6 3h12v18H6zM9 8h6M9 12h6M9 16h4" />
                </svg>
                <span>주문 조회</span>
              </Link>
              {session ? (
                <>
                  <Link href="/account" aria-label="내 계정">
                    <svg className="site-nav-icon" aria-hidden="true" viewBox="0 0 24 24">
                      <circle cx="12" cy="8" r="4" />
                      <path d="M4.5 21a7.5 7.5 0 0 1 15 0" />
                    </svg>
                    <span>내 계정</span>
                  </Link>
                  <form action="/auth/logout" method="post">
                    <button type="submit">로그아웃</button>
                  </form>
                </>
              ) : (
                <>
                  <Link href="/account" aria-label="내 계정">
                    <svg className="site-nav-icon" aria-hidden="true" viewBox="0 0 24 24">
                      <circle cx="12" cy="8" r="4" />
                      <path d="M4.5 21a7.5 7.5 0 0 1 15 0" />
                    </svg>
                    <span>내 계정</span>
                  </Link>
                  <Link className="site-nav-login" href="/login">로그인</Link>
                </>
              )}
            </nav>
          </div>
        </header>
        <main className="page-shell">{children}</main>
        <nav className="mobile-bottom-nav" aria-label="모바일 주요 메뉴">
          <Link href="/" aria-label="홈">
            <svg aria-hidden="true" viewBox="0 0 24 24">
              <path d="m3 11 9-8 9 8v10h-6v-6H9v6H3z" />
            </svg>
            <span>홈</span>
          </Link>
          <Link href="/products" aria-label="카테고리">
            <svg aria-hidden="true" viewBox="0 0 24 24">
              <rect x="3" y="3" width="6" height="6" />
              <rect x="15" y="3" width="6" height="6" />
              <rect x="3" y="15" width="6" height="6" />
              <rect x="15" y="15" width="6" height="6" />
            </svg>
            <span>카테고리</span>
          </Link>
          <Link href="/products" aria-label="검색">
            <svg aria-hidden="true" viewBox="0 0 24 24">
              <circle cx="10.5" cy="10.5" r="6.5" />
              <path d="m16 16 5 5" />
            </svg>
            <span>검색</span>
          </Link>
          <Link href="/account" aria-label="내 계정">
            <svg aria-hidden="true" viewBox="0 0 24 24">
              <circle cx="12" cy="8" r="4" />
              <path d="M4.5 21a7.5 7.5 0 0 1 15 0" />
            </svg>
            <span>내 계정</span>
          </Link>
          <Link href="/cart" aria-label="장바구니">
            <svg aria-hidden="true" viewBox="0 0 24 24">
              <path d="M3 4h2l2.2 10.5h9.9l2-7H6.1" />
              <circle cx="9" cy="19" r="1.5" />
              <circle cx="17" cy="19" r="1.5" />
            </svg>
            <span>장바구니</span>
          </Link>
        </nav>
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
