import Link from "next/link";
import { cookies } from "next/headers";
import { ApiError, apiGetWithCookie } from "@/lib/api";
import { AdminInquiry, InquiryStatus, inquiryStatusLabel } from "./model";

type CustomerInquiryListResponse = { inquiries: AdminInquiry[] };
type PageProps = { searchParams: Promise<{ status?: string }> };

const statuses: InquiryStatus[] = ["RECEIVED", "IN_PROGRESS", "ANSWERED", "CLOSED"];

async function loadInquiries(status?: InquiryStatus) {
  try {
    const path = status ? `/api/admin/customer-inquiries?status=${status}` : "/api/admin/customer-inquiries";
    const data = await apiGetWithCookie<CustomerInquiryListResponse>(path, (await cookies()).toString());
    return { error: false as const, inquiries: data.inquiries };
  } catch (error) {
    return { error: error instanceof ApiError, inquiries: [] };
  }
}

export default async function AdminInquiriesPage({ searchParams }: PageProps) {
  const query = await searchParams;
  const selectedStatus = statuses.includes(query.status as InquiryStatus) ? (query.status as InquiryStatus) : undefined;
  const { error, inquiries } = await loadInquiries(selectedStatus);

  return (
    <div className="admin-page">
      <div className="admin-heading">
        <div>
          <h1>고객 문의</h1>
          <p>접수된 문의의 진행 상태와 답변을 관리합니다.</p>
        </div>
      </div>

      <nav className="admin-filter-links" aria-label="문의 상태 필터">
        <Link className={!selectedStatus ? "active" : ""} href="/admin/inquiries">전체</Link>
        {statuses.map((status) => (
          <Link className={selectedStatus === status ? "active" : ""} href={`/admin/inquiries?status=${status}`} key={status}>
            {inquiryStatusLabel[status]}
          </Link>
        ))}
      </nav>

      {error ? (
        <div className="notice danger">
          <strong>문의 목록을 불러오지 못했습니다</strong>
          <span>API 서버 또는 관리자 권한을 확인하세요.</span>
        </div>
      ) : null}

      <section className="admin-panel">
        <div className="admin-panel-head">
          <h2>{selectedStatus ? inquiryStatusLabel[selectedStatus] : "전체 문의"}</h2>
          <span>총 {inquiries.length}건</span>
        </div>
        <div className="admin-inquiry-list">
          {inquiries.map((inquiry) => (
            <Link className="admin-inquiry-card" href={`/admin/inquiries/${inquiry.inquiryId}`} key={inquiry.inquiryId}>
              <div>
                <strong>{inquiry.subject}</strong>
                <span className={`inquiry-status ${inquiry.status.toLowerCase()}`}>{inquiryStatusLabel[inquiry.status]}</span>
              </div>
              <dl>
                <div><dt>고객</dt><dd>{inquiry.customerName}</dd></div>
                <div><dt>이메일</dt><dd>{inquiry.email}</dd></div>
                <div><dt>접수</dt><dd>{new Date(inquiry.createdAt).toLocaleString("ko-KR")}</dd></div>
              </dl>
              <p>{inquiry.message}</p>
            </Link>
          ))}
          {inquiries.length === 0 ? (
            <div className="admin-empty compact">
              <strong>해당 상태의 문의가 없습니다</strong>
              <span>새 문의가 접수되거나 상태가 변경되면 표시됩니다.</span>
            </div>
          ) : null}
        </div>
      </section>
    </div>
  );
}
