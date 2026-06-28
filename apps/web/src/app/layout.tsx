import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  title: "SafeHub Pro",
  description: "Business safety gear commerce",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body>
        <header className="site-header">
          <Link href="/" className="brand" aria-label="SafeHub Pro home">
            <span>SafeHub</span>
            <em>Pro</em>
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
            <Link href="/cart">장바구니</Link>
            <Link href="/orders">주문</Link>
            <Link href="/policies">정책</Link>
            <Link href="/account">내 계정</Link>
            <Link href="/login">로그인</Link>
            <Link href="/admin">관리자</Link>
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
