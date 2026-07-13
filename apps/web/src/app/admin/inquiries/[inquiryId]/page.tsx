import Link from "next/link";
import { notFound } from "next/navigation";
import { cookies } from "next/headers";
import { ApiError, apiGetWithCookie } from "@/lib/api";
import { SubmitButton } from "../../../submit-button";
import { answerInquiry, changeInquiryStatus, retryInquiryEmail } from "../actions";
import { AdminInquiry, InquiryStatus, inquiryStatusLabel } from "../model";

type PageProps = {
  params: Promise<{ inquiryId: string }>;
  searchParams: Promise<{ message?: string }>;
};

const statuses: InquiryStatus[] = ["RECEIVED", "IN_PROGRESS", "CLOSED"];

async function loadInquiry(inquiryId: string) {
  try {
    return await apiGetWithCookie<AdminInquiry>(
      `/api/admin/customer-inquiries/${inquiryId}`,
      (await cookies()).toString(),
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    return null;
  }
}

function date(value: string | null) {
  return value ? new Date(value).toLocaleString("ko-KR") : "-";
}

export default async function AdminInquiryDetailPage({ params, searchParams }: PageProps) {
  const [{ inquiryId }, query] = await Promise.all([params, searchParams]);
  const inquiry = await loadInquiry(inquiryId);

  if (!inquiry) {
    return <div className="notice danger">문의 정보를 불러오지 못했습니다.</div>;
  }

  const notification = inquiry.latestAnswerNotification;
  const retryable = notification?.status === "FAILED" || notification?.status === "SKIPPED";

  return (
    <div className="admin-page">
      <div className="admin-heading">
        <div>
          <Link className="admin-text-link" href="/admin/inquiries">고객 문의 목록</Link>
          <h1>{inquiry.subject}</h1>
          <p>{inquiry.customerName} · {inquiry.email}</p>
        </div>
        <span className={`inquiry-status ${inquiry.status.toLowerCase()}`}>{inquiryStatusLabel[inquiry.status]}</span>
      </div>

      {query.message ? <div className="notice"><strong>알림</strong><span>{query.message}</span></div> : null}

      <div className="admin-inquiry-detail-grid">
        <section className="admin-panel">
          <div className="admin-panel-head"><h2>문의 내용</h2><span>{date(inquiry.createdAt)}</span></div>
          <p className="inquiry-message">{inquiry.message}</p>
          <dl className="summary-list">
            <div><dt>연락처</dt><dd>{inquiry.phone ?? "-"}</dd></div>
            <div><dt>보관 만료</dt><dd>{date(inquiry.retentionExpiresAt)}</dd></div>
            <div><dt>동의 증적</dt><dd>{inquiry.consentedAt ? `${inquiry.consentPolicyVersion} · ${date(inquiry.consentedAt)}` : "기존 데이터 · 동의 증적 없음"}</dd></div>
          </dl>
        </section>

        <section className="admin-panel">
          <div className="admin-panel-head"><h2>처리 상태</h2><span>최종 변경 {date(inquiry.updatedAt)}</span></div>
          <form action={changeInquiryStatus} className="admin-form">
            <input name="inquiryId" type="hidden" value={inquiry.inquiryId} />
            <label>상태
              <select name="status" defaultValue={inquiry.status === "ANSWERED" ? "CLOSED" : inquiry.status}>
                {statuses.map((status) => <option key={status} value={status}>{inquiryStatusLabel[status]}</option>)}
              </select>
            </label>
            <label>관리자 메모
              <textarea name="adminMemo" defaultValue={inquiry.adminMemo ?? ""} maxLength={5000} rows={4} />
            </label>
            <SubmitButton className="button secondary" pendingLabel="변경 중...">상태 저장</SubmitButton>
          </form>
        </section>
      </div>

      <section className="admin-panel">
        <div className="admin-panel-head"><h2>고객 답변</h2><span>{inquiry.answeredAt ? `답변 ${date(inquiry.answeredAt)}` : "답변 전"}</span></div>
        <form action={answerInquiry} className="admin-form">
          <input name="inquiryId" type="hidden" value={inquiry.inquiryId} />
          <label>답변 내용
            <textarea name="answer" defaultValue={inquiry.answer ?? ""} required maxLength={5000} rows={8} />
          </label>
          <label>관리자 메모
            <textarea name="adminMemo" defaultValue={inquiry.adminMemo ?? ""} maxLength={5000} rows={3} />
          </label>
          <SubmitButton className="button primary" pendingLabel="저장 중..." disabled={inquiry.status === "CLOSED"}>
            답변 저장 및 이메일 발송
          </SubmitButton>
        </form>
        {inquiry.status === "CLOSED" ? <p className="legal-note">종료된 문의는 처리 중으로 다시 연 뒤 답변할 수 있습니다.</p> : null}
      </section>

      {notification ? (
        <section className="admin-panel">
          <div className="admin-panel-head"><h2>답변 이메일</h2><span>{notification.status}</span></div>
          <p>{notification.failureReason ?? (notification.sentAt ? `발송 완료 ${date(notification.sentAt)}` : "발송 처리 중")}</p>
          {retryable ? (
            <form action={retryInquiryEmail}>
              <input name="inquiryId" type="hidden" value={inquiry.inquiryId} />
              <input name="notificationId" type="hidden" value={notification.notificationId} />
              <SubmitButton className="button secondary" pendingLabel="재시도 중...">이메일 재시도</SubmitButton>
            </form>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}
