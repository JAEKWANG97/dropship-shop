import type { Metadata } from "next";
import Link from "next/link";
import { getAdminUser, getCurrentUser } from "@/lib/session";
import "./globals.css";

export const metadata: Metadata = {
  title: "SafeHub Pro",
  description: "Business safety gear commerce",
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
        <header className="site-header">
          <Link href="/" className="brand" aria-label="SafeHub Pro home">
            <strong>
              <span>SafeHub</span>
              <em>Pro</em>
            </strong>
            <small>건설안전장비 B2B 전문몰</small>
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
            <Link href="/products">상품목록</Link>
            <Link href="/products">카테고리</Link>
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
                <Link href="/account">사업자회원</Link>
                <Link href="/login">로그인</Link>
              </>
            )}
            {admin ? <Link href="/admin">관리자</Link> : null}
          </nav>
        </header>
        <main className="page-shell">{children}</main>
        <footer className="site-footer">
          <strong>SafeHub Pro</strong>
          <span>건설 안전장비 B2B 전문몰</span>
          <span>배송비는 상품 가격에 포함됩니다.</span>
          <span>공급처 품절 시 배송 그룹 주문 단위로 환불됩니다.</span>
        </footer>
      </body>
    </html>
  );
}
