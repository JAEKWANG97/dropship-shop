import Link from "next/link";
import { getAdminPortalSuppliers, supplierStatusLabel } from "@/lib/supplier";

export default async function AdminSuppliersPage() {
  const result = await loadSuppliers();

  return (
    <div className="admin-page">
      <div className="admin-heading">
        <div>
          <h1>공급처 포털 관리</h1>
          <p>포털 접근, 판매, 계약 상태를 각각 확인하고 필요한 작업만 실행합니다.</p>
        </div>
      </div>

      {result.error ? (
        <div className="notice danger"><strong>공급처 목록을 불러오지 못했습니다</strong><span>API 서버와 관리자 권한을 확인해 주세요.</span></div>
      ) : null}

      <section className="admin-panel">
        <div className="admin-panel-head"><h2>공급처</h2><span>총 {result.suppliers.length}개</span></div>
        <div className="admin-inquiry-list">
          {result.suppliers.map((supplier) => (
            <Link className="admin-inquiry-card" href={`/admin/suppliers/${supplier.supplierId}`} key={supplier.supplierId}>
              <div><strong>{supplier.name}</strong><span className={`admin-badge ${supplier.portalStatus.toLowerCase()}`}>{supplierStatusLabel(supplier.portalStatus)}</span></div>
              <dl>
                <div><dt>판매</dt><dd>{supplierStatusLabel(supplier.salesStatus)}</dd></div>
                <div><dt>계약</dt><dd>{supplierStatusLabel(supplier.contractStatus)}</dd></div>
                <div><dt>담당자</dt><dd>{supplier.managerUserId ? "연결됨" : "없음"}</dd></div>
              </dl>
              <p>{supplier.contactEmail ?? "연락 이메일 없음"}</p>
            </Link>
          ))}
          {result.suppliers.length === 0 ? (
            <div className="admin-empty compact"><strong>등록된 공급처가 없습니다</strong><span>승인된 공급처가 생기면 표시됩니다.</span></div>
          ) : null}
        </div>
      </section>
    </div>
  );
}

async function loadSuppliers() {
  try {
    return { error: false as const, suppliers: await getAdminPortalSuppliers() };
  } catch {
    return { error: true as const, suppliers: [] };
  }
}
