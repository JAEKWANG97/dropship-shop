import { randomUUID } from "node:crypto";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ApiError } from "@/lib/api";
import { SubmitButton } from "@/app/submit-button";
import {
  getAdminPortalSuppliers,
  getAdminSupplierApplication,
  supplierStatusLabel,
  type SupplierApplication,
  type SupplierPortalSummary,
} from "@/lib/supplier";
import { approveSupplierApplication, rejectSupplierApplication } from "../actions";

type PageProps = {
  params: Promise<{ applicationId: string }>;
  searchParams: Promise<{ message?: string | string[] }>;
};

const REJECTION_REASONS = [
  ["INCOMPLETE_INFORMATION", "정보 부족"],
  ["OUT_OF_SCOPE", "취급 범위 외"],
  ["POLICY_NOT_MET", "운영 기준 미충족"],
  ["DUPLICATE_OR_EXISTING_RELATIONSHIP", "중복 또는 기존 거래 관계"],
] as const;

export default async function AdminSupplierApplicationDetailPage({ params, searchParams }: PageProps) {
  const [{ applicationId }, query] = await Promise.all([params, searchParams]);
  const result = await load(applicationId);
  if (result.notFound) notFound();

  const application = result.application;
  const message = Array.isArray(query.message) ? query.message[0] : query.message;
  if (!application) {
    return <div className="admin-page"><div className="notice danger">신청 정보를 불러오지 못했습니다.</div></div>;
  }

  const reviewable = application.status === "SUBMITTED";
  const linkableSuppliers = result.suppliers.filter((supplier) => supplier.portalStatus === "DISABLED");

  return (
    <div className="admin-page">
      <div className="admin-heading">
        <div>
          <Link className="admin-text-link" href="/admin/supplier-applications">공급처 신청 목록</Link>
          <h1>{application.supplierName ?? "비식별 처리된 신청"}</h1>
          <p>신청 정보와 개인정보 동의 증적을 확인한 뒤 한 번만 처리합니다.</p>
        </div>
        <span className={`admin-badge ${application.status.toLowerCase()}`}>{supplierStatusLabel(application.status)}</span>
      </div>

      {message ? <div className="notice" role="status"><strong>알림</strong><span>{message}</span></div> : null}

      <div className="admin-inquiry-detail-grid">
        <section className="admin-panel">
          <div className="admin-panel-head"><h2>신청 정보</h2><span>{date(application.createdAt)}</span></div>
          <dl className="summary-list">
            <Row label="공급처명" value={application.supplierName} />
            <Row label="담당자" value={application.contactName} />
            <Row label="이메일" value={application.contactEmail} />
            <Row label="연락처" value={application.contactPhone} />
            <Row label="문의 메모" value={application.memo} />
          </dl>
          <p className="field-help">신청자의 개인정보입니다. 공급처 검토 목적으로만 사용하세요.</p>
        </section>

        <section className="admin-panel">
          <div className="admin-panel-head"><h2>동의·보관</h2><span>서버 증적</span></div>
          <dl className="summary-list">
            <Row label="동의 버전" value={application.consentPolicyVersion} />
            <Row label="동의 시각" value={date(application.consentedAt)} />
            <Row label="보관 만료" value={date(application.retentionExpiresAt)} />
            <Row label="비식별 시각" value={date(application.anonymizedAt)} />
          </dl>
        </section>
      </div>

      {reviewable ? (
        <div className="admin-inquiry-detail-grid">
          <section className="admin-panel">
            <div className="admin-panel-head"><h2>승인</h2><span>초대 이메일 발급</span></div>
            <form action={approveSupplierApplication} className="admin-form">
              <input name="applicationId" type="hidden" value={application.applicationId} />
              <input name="idempotencyKey" type="hidden" value={randomUUID()} />
              <label>처리 방식
                <select defaultValue="CREATE_NEW" name="approvalMode" required>
                  <option value="CREATE_NEW">신규 공급처 생성</option>
                  <option value="LINK_EXISTING">기존 공급처 연결</option>
                </select>
              </label>
              <label>연결할 기존 공급처
                <select defaultValue="" name="existingSupplierId">
                  <option value="">신규 생성 시 선택하지 않음</option>
                  {linkableSuppliers.map((supplier) => (
                    <option key={supplier.supplierId} value={supplier.supplierId}>{supplier.name}</option>
                  ))}
                </select>
                <span className="field-help">기존 연결은 포털 이력이 없는 DISABLED 공급처만 서버가 허용합니다.</span>
              </label>
              <label>내부 처리 사유
                <textarea maxLength={200} name="internalReason" required rows={3} />
              </label>
              <SubmitButton className="button primary" pendingLabel="승인 중...">승인 및 초대 발급</SubmitButton>
            </form>
          </section>

          <section className="admin-panel">
            <div className="admin-panel-head"><h2>거절</h2><span>90일 뒤 비식별</span></div>
            <form action={rejectSupplierApplication} className="admin-form">
              <input name="applicationId" type="hidden" value={application.applicationId} />
              <input name="idempotencyKey" type="hidden" value={randomUUID()} />
              <label>거절 사유
                <select name="reviewReasonCode" required>
                  {REJECTION_REASONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
              </label>
              <label>내부 처리 사유
                <textarea maxLength={200} name="internalReason" required rows={3} />
              </label>
              <SubmitButton className="button" pendingLabel="거절 중...">신청 거절</SubmitButton>
            </form>
          </section>
        </div>
      ) : (
        <section className="admin-panel">
          <div className="admin-panel-head"><h2>처리 결과</h2><span>{date(application.reviewedAt)}</span></div>
          <dl className="summary-list">
            <Row label="처리 관리자" value={application.reviewedByAdminId} />
            <Row label="사유 코드" value={application.reviewReasonCode} />
            <Row label="처리 사유" value={application.reviewReason} />
            <Row label="승인 방식" value={application.approvalMode} />
            <Row label="연결 요청 공급처" value={application.requestedExistingSupplierId} />
            <Row label="승인 공급처" value={application.approvedSupplierId} />
          </dl>
        </section>
      )}
    </div>
  );
}

function Row({ label, value }: { label: string; value: string | null }) {
  return <div><dt>{label}</dt><dd>{value || "-"}</dd></div>;
}

async function load(applicationId: string): Promise<{
  application: SupplierApplication | null;
  suppliers: SupplierPortalSummary[];
  notFound: boolean;
}> {
  try {
    const [application, suppliers] = await Promise.all([
      getAdminSupplierApplication(applicationId),
      getAdminPortalSuppliers().catch(() => []),
    ]);
    return { application, suppliers, notFound: false };
  } catch (error) {
    return { application: null, suppliers: [], notFound: error instanceof ApiError && error.status === 404 };
  }
}

function date(value: string | null) {
  return value ? new Date(value).toLocaleString("ko-KR") : "-";
}
