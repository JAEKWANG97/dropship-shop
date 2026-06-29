import type { Metadata } from "next";
import Link from "next/link";
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
            <Link href="/products">입점상품</Link>
            <Link href="/orders">주문배송</Link>
            <Link href="/cart">장바구니</Link>
            {admin ? <Link href="/admin">관리자</Link> : null}
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
                검색
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
              <Link className="nav-primary" href="/products">
                바로 구매
              </Link>
            </nav>
          </div>
          <nav className="category-nav" aria-label="Category navigation">
            <Link className="category-all" href="/products">
              전체 카테고리
            </Link>
            <Link href="/products?q=안전모">안전모</Link>
            <Link href="/products?q=안전화">안전화</Link>
            <Link href="/products?q=형광조끼">형광조끼</Link>
            <Link href="/products?q=안전장갑">안전장갑</Link>
            <Link href="/products?q=추락방지">추락방지</Link>
            <Link href="/products?q=보안경">보안경</Link>
          </nav>
        </header>
        <main className="page-shell">{children}</main>
        <footer className="site-footer">
          <div>
            <strong>코어블SAF</strong>
            <span>건설 안전용품 쇼핑몰</span>
          </div>
          <div>
            <strong>고객 지원</strong>
            <span>주문/배송 문의는 주문조회에서 확인</span>
            <span>운영 권한 계정 기준 관리</span>
          </div>
          <div>
            <strong>거래 정책</strong>
            <span>배송비는 상품 가격에 포함됩니다.</span>
            <span>공급처 품절 시 배송 그룹 주문 단위로 환불됩니다.</span>
          </div>
        </footer>
      </body>
    </html>
  );
}
