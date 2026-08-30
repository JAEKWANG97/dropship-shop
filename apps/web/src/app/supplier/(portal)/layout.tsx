import Link from "next/link";
import { notFound } from "next/navigation";
import { getCurrentUser } from "@/lib/session";
import {
  getSupplierSession,
  supplierPortalEnabled,
  supplierStatusLabel,
  type SupplierPortalSummary,
} from "@/lib/supplier";

export default async function SupplierLayout({ children }: { children: React.ReactNode }) {
  if (!supplierPortalEnabled()) notFound();

  const [currentUser, supplier] = await Promise.all([
    getCurrentUser(),
    loadSupplierSession(),
  ]);

  if (!currentUser) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">공급처 포털</p>
        <h1>로그인이 필요합니다</h1>
        <p>초대 수락에 사용한 카카오 계정으로 로그인해 주세요.</p>
        <Link className="button primary" href="/login?redirectTo=%2Fsupplier">카카오로 로그인</Link>
      </section>
    );
  }

  if (!supplier) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">공급처 포털</p>
        <h1>공급처 권한을 확인할 수 없습니다</h1>
        <p>초대 링크로 담당자 연결을 완료했는지 확인해 주세요.</p>
      </section>
    );
  }

  return (
    <section className="supplier-shell">
      <aside className="supplier-sidebar">
        <Link className="admin-brand" href="/supplier">
          <span>코어블</span>
          <em>SAF</em>
          <small>공급처 포털</small>
        </Link>
        <nav aria-label="공급처 메뉴">
          <Link href="/supplier">홈</Link>
          <Link href="/supplier/products">상품</Link>
          <Link href="/supplier/orders">출고 요청</Link>
        </nav>
        <form action="/auth/logout" method="post">
          <button className="button" type="submit">로그아웃</button>
        </form>
      </aside>
      <div className="supplier-main">
        <header className="supplier-topbar">
          <div>
            <strong>{supplier.name}</strong>
            <span>
              {supplier.contactEmail
                ? `${supplier.contactEmail} · ${supplier.contactEmailVerifiedAt ? "이메일 확인됨" : "이메일 미확인"}`
                : "담당자 1명 연결"}
            </span>
          </div>
          <div className="supplier-status-row">
            <Status label="포털" value={supplier.portalStatus} />
            <Status label="판매" value={supplier.salesStatus} />
            <Status label="계약" value={supplier.contractStatus} />
          </div>
        </header>
        {children}
      </div>
    </section>
  );
}

function Status({ label, value }: { label: string; value: string }) {
  return <span><small>{label}</small>{supplierStatusLabel(value)}</span>;
}

async function loadSupplierSession(): Promise<SupplierPortalSummary | null> {
  try {
    return await getSupplierSession();
  } catch {
    return null;
  }
}
