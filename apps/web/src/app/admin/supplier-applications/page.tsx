import Link from "next/link";
import { redirect } from "next/navigation";
import {
  getAdminSupplierApplications,
  supplierStatusLabel,
  type SupplierApplicationPage,
} from "@/lib/supplier";

type PageProps = {
  searchParams: Promise<{ status?: string; page?: string }>;
};

const STATUSES = ["SUBMITTED", "APPROVED", "REJECTED", "EXPIRED"];

export default async function AdminSupplierApplicationsPage({ searchParams }: PageProps) {
  const query = await searchParams;
  const status = STATUSES.includes(query.status ?? "") ? query.status : undefined;
  const requestedPage = positivePage(query.page);
  const result = await loadApplications(status, requestedPage - 1);

  if (!result.error && result.data.totalPages > 0 && requestedPage > result.data.totalPages) {
    redirect(listPath(status, result.data.totalPages));
  }

  const data = result.error ? emptyPage() : result.data;
  const currentPage = data.page + 1;

  return (
    <div className="admin-page">
      <div className="admin-heading">
        <div>
          <h1>공급처 신청</h1>
          <p>공개 신청을 확인하고 신규 공급처 생성 또는 기존 공급처 연결을 결정합니다.</p>
        </div>
      </div>

      <nav className="admin-filter-links" aria-label="공급처 신청 상태">
        <Link className={!status ? "active" : ""} href="/admin/supplier-applications">전체</Link>
        {STATUSES.map((item) => (
          <Link className={status === item ? "active" : ""} href={listPath(item, 1)} key={item}>
            {supplierStatusLabel(item)}
          </Link>
        ))}
      </nav>

      {result.error ? (
        <div className="notice danger">
          <strong>신청 목록을 불러오지 못했습니다</strong>
          <span>API 서버와 관리자 권한을 확인해 주세요.</span>
        </div>
      ) : null}

      <section className="admin-panel">
        <div className="admin-panel-head"><h2>신청 목록</h2><span>총 {data.totalElements}건</span></div>
        <div className="admin-inquiry-list">
          {data.applications.map((application) => (
            <Link className="admin-inquiry-card" href={`/admin/supplier-applications/${application.applicationId}`} key={application.applicationId}>
              <div>
                <strong>{application.supplierName ?? "비식별 처리된 신청"}</strong>
                <span className={`admin-badge ${application.status.toLowerCase()}`}>{supplierStatusLabel(application.status)}</span>
              </div>
              <dl>
                <div><dt>담당자</dt><dd>{application.contactName ?? "-"}</dd></div>
                <div><dt>이메일</dt><dd>{application.contactEmail ?? "-"}</dd></div>
                <div><dt>신청일</dt><dd>{date(application.createdAt)}</dd></div>
              </dl>
              <p>{application.memo || "추가 문의 메모 없음"}</p>
            </Link>
          ))}
          {data.applications.length === 0 ? (
            <div className="admin-empty compact"><strong>해당 신청이 없습니다</strong><span>새 신청이나 상태 변경이 생기면 표시됩니다.</span></div>
          ) : null}
        </div>
        {data.totalPages > 1 ? (
          <nav className="admin-pagination" aria-label="공급처 신청 페이지">
            {currentPage > 1 ? <Link href={listPath(status, currentPage - 1)}>이전</Link> : <span aria-disabled="true">이전</span>}
            <strong aria-current="page">{currentPage}</strong>
            {currentPage < data.totalPages ? <Link href={listPath(status, currentPage + 1)}>다음</Link> : <span aria-disabled="true">다음</span>}
          </nav>
        ) : null}
      </section>
    </div>
  );
}

async function loadApplications(status: string | undefined, page: number) {
  try {
    return { error: false as const, data: await getAdminSupplierApplications({ status, page }) };
  } catch {
    return { error: true as const, data: emptyPage() };
  }
}

function emptyPage(): SupplierApplicationPage {
  return { applications: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
}

function positivePage(value?: string) {
  const page = Number.parseInt(value ?? "1", 10);
  return Number.isFinite(page) && page > 0 ? page : 1;
}

function listPath(status: string | undefined, page: number) {
  const query = new URLSearchParams();
  if (status) query.set("status", status);
  if (page > 1) query.set("page", String(page));
  const value = query.toString();
  return value ? `/admin/supplier-applications?${value}` : "/admin/supplier-applications";
}

function date(value: string | null) {
  return value ? new Date(value).toLocaleString("ko-KR") : "-";
}
