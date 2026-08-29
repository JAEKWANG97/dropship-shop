import { randomUUID } from "node:crypto";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ApiError } from "@/lib/api";
import { SubmitButton } from "@/app/submit-button";
import {
  getAdminPortalSupplier,
  supplierStatusLabel,
  type SupplierPortalSummary,
} from "@/lib/supplier";
import {
  disconnectSupplierManager,
  reissueSupplierInvite,
  updateSupplierContactEmail,
  updateSupplierPortalStatus,
  updateSupplierSalesStatus,
} from "../actions";

type PageProps = {
  params: Promise<{ supplierId: string }>;
  searchParams: Promise<{ message?: string | string[] }>;
};

const REISSUE_REASONS = [
  ["DELIVERY_FAILED", "이메일 발송 실패"],
  ["INVITE_EXPIRED", "초대 만료"],
  ["RECIPIENT_CHANGED", "수신자 변경"],
  ["ADMIN_REISSUE", "관리자 재발급"],
] as const;

export default async function AdminSupplierDetailPage({ params, searchParams }: PageProps) {
  const [{ supplierId }, query] = await Promise.all([params, searchParams]);
  const result = await loadSupplier(supplierId);
  if (result.notFound) notFound();

  const supplier = result.supplier;
  const message = Array.isArray(query.message) ? query.message[0] : query.message;
  if (!supplier) {
    return <div className="admin-page"><div className="notice danger">공급처 정보를 불러오지 못했습니다.</div></div>;
  }

  const targets = portalTargets(supplier.portalStatus);
  const canReissue = supplier.portalStatus === "PENDING_ACTIVATION"
    && !supplier.managerUserId
    && Boolean(supplier.contactEmail)
    && !supplier.contactEmailVerifiedAt;

  return (
    <div className="admin-page">
      <div className="admin-heading">
        <div>
          <Link className="admin-text-link" href="/admin/suppliers">공급처 목록</Link>
          <h1>{supplier.name}</h1>
          <p>포털 접근과 판매 상태는 별도로 변경하며, 안전한 기본값은 판매 중지입니다.</p>
        </div>
        <span className={`admin-badge ${supplier.portalStatus.toLowerCase()}`}>{supplierStatusLabel(supplier.portalStatus)}</span>
      </div>

      {message ? <div className="notice" role="status"><strong>알림</strong><span>{message}</span></div> : null}

      <section className="admin-panel">
        <div className="admin-panel-head"><h2>현재 상태</h2><span>서로 독립적으로 관리</span></div>
        <dl className="summary-list supplier-admin-summary">
          <Row label="포털" value={supplierStatusLabel(supplier.portalStatus)} />
          <Row label="판매" value={supplierStatusLabel(supplier.salesStatus)} />
          <Row label="계약" value={supplierStatusLabel(supplier.contractStatus)} />
          <Row label="담당자" value={supplier.managerUserId ? "연결됨" : "없음"} />
          <Row label="연락 이메일" value={supplier.contactEmail} />
          <Row label="이메일 검증" value={supplier.contactEmailVerifiedAt ? date(supplier.contactEmailVerifiedAt) : "미검증"} />
        </dl>
      </section>

      <div className="admin-inquiry-detail-grid">
        <section className="admin-panel">
          <div className="admin-panel-head"><h2>판매 상태</h2><span>포털과 별도</span></div>
          <form action={updateSupplierSalesStatus} className="admin-form">
            <CommandFields supplierId={supplier.supplierId} />
            <label>판매 상태
              <select defaultValue={supplier.salesStatus === "ACTIVE" ? "INACTIVE" : "ACTIVE"} name="salesStatus" required>
                <option value="ACTIVE">판매 활성</option>
                <option value="INACTIVE">판매 중지</option>
              </select>
            </label>
            <ReasonField />
            <SubmitButton className="button primary" pendingLabel="변경 중...">판매 상태 변경</SubmitButton>
          </form>
        </section>

        <section className="admin-panel">
          <div className="admin-panel-head"><h2>포털 상태</h2><span>판매 중지가 기본</span></div>
          {targets.length > 0 ? (
            <form action={updateSupplierPortalStatus} className="admin-form">
              <CommandFields supplierId={supplier.supplierId} />
              <label>변경할 상태
                <select name="portalStatus" required>
                  {targets.map((target) => <option key={target} value={target}>{supplierStatusLabel(target)}</option>)}
                </select>
              </label>
              <SalesActionField />
              <ReasonField />
              <SubmitButton className="button" pendingLabel="변경 중...">포털 상태 변경</SubmitButton>
            </form>
          ) : <p>영구 종료된 포털은 다시 활성화하지 않습니다.</p>}
        </section>
      </div>

      {supplier.portalStatus !== "DISABLED" ? (
        <div className="admin-inquiry-detail-grid">
          <section className="admin-panel">
            <div className="admin-panel-head"><h2>연락 이메일 변경</h2><span>담당자 재연결 필요</span></div>
            <form action={updateSupplierContactEmail} className="admin-form">
              <CommandFields supplierId={supplier.supplierId} />
              <label>새 연락 이메일
                <input maxLength={320} name="contactEmail" placeholder={supplier.contactEmail ?? "새 이메일"} required type="email" />
              </label>
              <SalesActionField />
              <ReasonField />
              <SubmitButton className="button" pendingLabel="변경 중...">이메일 변경</SubmitButton>
            </form>
          </section>

          <section className="admin-panel">
            <div className="admin-panel-head"><h2>담당자 연결 해제</h2><span>자동 복구 없음</span></div>
            {supplier.managerUserId ? (
              <form action={disconnectSupplierManager} className="admin-form">
                <CommandFields supplierId={supplier.supplierId} />
                <SalesActionField />
                <ReasonField />
                <SubmitButton className="button" pendingLabel="해제 중...">담당자 연결 해제</SubmitButton>
              </form>
            ) : <p>현재 연결된 담당자가 없습니다.</p>}
          </section>
        </div>
      ) : null}

      <section className="admin-panel">
        <div className="admin-panel-head"><h2>초대 재발급</h2><span>기존 초대 폐기 후 새 링크</span></div>
        {canReissue ? (
          <form action={reissueSupplierInvite} className="admin-form supplier-reissue-form">
            <CommandFields supplierId={supplier.supplierId} />
            <label>재발급 사유
              <select defaultValue="" name="reasonCode" required>
                <option disabled value="">사유 선택</option>
                {REISSUE_REASONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </label>
            <SubmitButton className="button primary" pendingLabel="발급 중...">새 초대 발급</SubmitButton>
          </form>
        ) : <p>담당자 연결 대기·담당자 없음·연락 이메일 미검증 상태에서만 재발급할 수 있습니다.</p>}
      </section>
    </div>
  );
}

function CommandFields({ supplierId }: { supplierId: string }) {
  return <><input name="supplierId" type="hidden" value={supplierId} /><input name="idempotencyKey" type="hidden" value={randomUUID()} /></>;
}

function SalesActionField() {
  return <label>신규 판매 처리<select defaultValue="PAUSE" name="salesAction" required><option value="PAUSE">판매 중지 (권장)</option><option value="KEEP">판매 유지·Coreable 처리</option></select></label>;
}

function ReasonField() {
  return <label>내부 처리 사유<textarea maxLength={200} name="reason" required rows={3} /><span className="field-help">고객·담당자 연락처나 배송정보를 입력하지 마세요.</span></label>;
}

function Row({ label, value }: { label: string; value: string | null }) {
  return <div><dt>{label}</dt><dd>{value || "-"}</dd></div>;
}

async function loadSupplier(supplierId: string): Promise<{ supplier: SupplierPortalSummary | null; notFound: boolean }> {
  try {
    return { supplier: await getAdminPortalSupplier(supplierId), notFound: false };
  } catch (error) {
    return { supplier: null, notFound: error instanceof ApiError && error.status === 404 };
  }
}

function portalTargets(status: string) {
  if (status === "ACTIVE") return ["SUSPENDED", "DISABLED"];
  if (status === "SUSPENDED") return ["ACTIVE", "DISABLED"];
  if (status === "PENDING_ACTIVATION") return ["DISABLED"];
  return [];
}

function date(value: string) {
  return new Date(value).toLocaleString("ko-KR");
}
