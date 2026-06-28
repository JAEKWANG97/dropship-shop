import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  title: "Dropship Shop",
  description: "Single-operator supplier fulfillment shop",
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
          <Link href="/" className="brand" aria-label="Dropship Shop home">
            Dropship Shop
          </Link>
          <nav className="site-nav" aria-label="Primary navigation">
            <Link href="/products">상품</Link>
            <Link href="/policies">정책</Link>
            <Link href="/login">로그인</Link>
            <Link href="/admin">관리자</Link>
          </nav>
        </header>
        <main className="page-shell">{children}</main>
        <footer className="site-footer">
          <span>배송비는 상품 가격에 포함됩니다.</span>
          <span>주문 후 공급처 품절 시 배송 그룹 주문 단위로 환불됩니다.</span>
        </footer>
      </body>
    </html>
  );
}
