import { ApiError, apiGetWithCookie } from "@/lib/api";
import { cookies } from "next/headers";

type CustomerInquiry = {
  inquiryId: string;
  customerName: string;
  email: string;
  phone: string | null;
  subject: string;
  message: string;
  createdAt: string;
};

type CustomerInquiryListResponse = {
  inquiries: CustomerInquiry[];
};

async function loadInquiries() {
  try {
    const data = await apiGetWithCookie<CustomerInquiryListResponse>(
      "/api/admin/customer-inquiries",
      (await cookies()).toString(),
    );
    return { error: false as const, inquiries: data.inquiries };
  } catch (error) {
    if (error instanceof ApiError) {
      return { error: true as const, inquiries: [] };
    }
    return { error: true as const, inquiries: [] };
  }
}

export default async function AdminInquiriesPage() {
  const { error, inquiries } = await loadInquiries();

  return (
    <div className="admin-page">
      <div className="admin-heading">
        <div>
          <h1>고객 문의</h1>
          <p>사이트 고객 문의 접수 내역을 확인합니다.</p>
        </div>
      </div>

      {error ? (
        <div className="notice danger">
          <strong>문의 목록을 불러오지 못했습니다</strong>
          <span>API 서버 또는 관리자 권한을 확인하세요.</span>
        </div>
      ) : null}

      <section className="admin-panel">
        <div className="admin-panel-head">
          <h2>접수 내역</h2>
          <span>총 {inquiries.length}건</span>
        </div>
        <div className="admin-inquiry-list">
          {inquiries.map((inquiry) => (
            <article className="admin-inquiry-card" key={inquiry.inquiryId}>
              <div>
                <strong>{inquiry.subject}</strong>
                <time dateTime={inquiry.createdAt}>{new Date(inquiry.createdAt).toLocaleString("ko-KR")}</time>
              </div>
              <dl>
                <div>
                  <dt>고객</dt>
                  <dd>{inquiry.customerName}</dd>
                </div>
                <div>
                  <dt>이메일</dt>
                  <dd>{inquiry.email}</dd>
                </div>
                <div>
                  <dt>연락처</dt>
                  <dd>{inquiry.phone ?? "-"}</dd>
                </div>
              </dl>
              <p>{inquiry.message}</p>
            </article>
          ))}
          {inquiries.length === 0 ? (
            <div className="admin-empty compact">
              <strong>접수된 문의가 없습니다</strong>
              <span>고객 문의가 접수되면 이 영역에 표시됩니다.</span>
            </div>
          ) : null}
        </div>
      </section>
    </div>
  );
}
