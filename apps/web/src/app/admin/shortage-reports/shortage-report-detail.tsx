/** @jsxImportSource react */

import { randomUUID } from "node:crypto";
import Link from "next/link";
import type { ComponentProps } from "react";
import { retryCommandKey, type RetryCommand } from "@/lib/admin-payment";
import {
  shortageReasonLabel,
  shortageStatusLabel,
  type AdminShortageReport,
} from "@/lib/supplier-claims";

type FormAction = NonNullable<ComponentProps<"form">["action"]>;

export function ShortageReportDetail({
  approveAction,
  rejectAction,
  report,
  retry,
}: {
  approveAction: FormAction;
  rejectAction: FormAction;
  report: AdminShortageReport;
  retry: RetryCommand;
}) {
  const reviewable = report.status === "REPORTED"
    && report.reasonCode !== "UNKNOWN"
    && Boolean(report.reportId && report.orderId);
  return (
    <>
      <div className="admin-panel-head">
        <h2>보고 상세</h2>
        <span className={`admin-badge ${reviewable ? "warning" : "neutral"}`}>{shortageStatusLabel(report.status)}</span>
      </div>
      <div className="summary-list compact">
        <Row label="주문번호" value={report.orderNumber} />
        <Row label="공급처" value={report.supplierName || "확인 필요"} />
        <Row label="보고 사유" value={shortageReasonLabel(report.reasonCode)} />
        <Row label="보고시각" value={dateTime(report.reportedAt)} />
        <Row label="검토시각" value={dateTime(report.reviewedAt)} />
        <Row label="검토 코드" value={reviewReasonLabel(report.reviewReasonCode)} />
      </div>
      {report.orderId ? <Link className="button" href={`/admin/orders?orderId=${encodeURIComponent(report.orderId)}`}>주문 상세</Link> : null}

      {reviewable ? (
        <div className="admin-order-actions">
          <form action={approveAction} className="admin-inline-form">
            <ReviewFields action={`shortage-approve-${report.reportId}`} report={report} retry={retry} />
            <input name="reviewReasonCode" type="hidden" value="SHORTAGE_CONFIRMED" />
            <button className="button primary" type="submit">품절 승인</button>
          </form>
          <form action={rejectAction} className="admin-inline-form">
            <ReviewFields action={`shortage-reject-${report.reportId}`} report={report} retry={retry} />
            <label className="wide">
              거절 사유
              <select defaultValue="INSUFFICIENT_EVIDENCE" name="reviewReasonCode" required>
                <option value="INSUFFICIENT_EVIDENCE">확인 근거 부족</option>
                <option value="FULFILLMENT_CAN_CONTINUE">출고 계속 가능</option>
              </select>
            </label>
            <button className="button" type="submit">품절 보고 거절</button>
          </form>
        </div>
      ) : report.status === "UNKNOWN" || report.reasonCode === "UNKNOWN" ? (
        <div className="notice danger"><strong>보고 계약을 확인할 수 없습니다</strong><span>검토 액션을 사용할 수 없습니다.</span></div>
      ) : null}
    </>
  );
}

function ReviewFields({ action, report, retry }: { action: string; report: AdminShortageReport; retry: RetryCommand }) {
  return (
    <>
      <input name="reportId" type="hidden" value={report.reportId} />
      <input name="expectedStatus" type="hidden" value={report.status} />
      <input name="idempotencyKey" type="hidden" value={retryCommandKey(retry, action) ?? randomUUID()} />
    </>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return <div><span>{label}</span><strong>{value}</strong></div>;
}

function reviewReasonLabel(value: AdminShortageReport["reviewReasonCode"]) {
  if (value === "SHORTAGE_CONFIRMED") return "품절 확인";
  if (value === "INSUFFICIENT_EVIDENCE") return "확인 근거 부족";
  if (value === "FULFILLMENT_CAN_CONTINUE") return "출고 계속 가능";
  if (value === "UNKNOWN") return "검토 코드 확인 필요";
  return "-";
}

function dateTime(value: string | null) {
  return value ? new Date(value).toLocaleString("ko-KR") : "-";
}
