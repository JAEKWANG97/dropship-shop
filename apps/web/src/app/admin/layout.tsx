import Link from "next/link";
import { getAdminUser, getCurrentUser } from "@/lib/session";

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  const [session, admin] = await Promise.all([getCurrentUser(), getAdminUser()]);

  if (!session) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">관리자</p>
        <h1>관리자 로그인이 필요합니다</h1>
        <p>관리자도 소셜 로그인 후 DB에 등록된 ADMIN 권한으로만 접근합니다.</p>
        <Link className="button primary" href="/login">
          로그인
        </Link>
      </section>
    );
  }

  if (!admin) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">관리자</p>
        <h1>관리자 권한이 없습니다</h1>
        <p>현재 계정은 로그인되어 있지만 관리자 권한이 등록되어 있지 않습니다.</p>
      </section>
    );
  }

  return (
    <section className="admin-shell">
      <aside className="admin-sidebar">
        <Link className="admin-brand" href="/admin">
          <span>코어블</span>
          <em>SAF</em>
          <small>관리자</small>
        </Link>
        <nav aria-label="Admin navigation">
          <Link href="/admin">대시보드</Link>
          <Link href="/admin/products">상품 관리</Link>
          <Link href="/admin/products/new">상품 등록</Link>
          <Link href="/admin/pricing">가격 정책</Link>
          <Link href="/admin/referrals">추천인 관리</Link>
          <Link href="/admin/supplier-applications">공급처 신청</Link>
          <Link href="/admin/suppliers">공급처 포털</Link>
          <Link href="/admin/orders">주문 관리</Link>
          <Link href="/admin/inquiries">고객 문의</Link>
        </nav>
      </aside>
      <div className="admin-main">
        <header className="admin-topbar">
          <form action="/admin/products">
            <input name="q" placeholder="상품, 주문, 회원, 문의를 검색하세요." />
            <button type="submit">검색</button>
          </form>
          <div className="admin-user">
            <span>운영 관리자</span>
            <small>관리자 권한 확인됨</small>
          </div>
        </header>
        {children}
      </div>
    </section>
  );
}
