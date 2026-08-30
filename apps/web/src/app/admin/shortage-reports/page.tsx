/** @jsxImportSource react */

import Link from "next/link";
import { getAdminShortageReport, getAdminShortageReports } from "@/lib/admin";
import {
  shortageReasonLabel,
  shortageStatusLabel,
  type AdminShortageReport,
} from "@/lib/supplier-claims";
import { approveShortageReport, rejectShortageReport } from "./actions";
import { ShortageReportDetail } from "./shortage-report-detail";

type PageProps = {
  searchParams: Promise<{
    idempotencyKey?: string;
    message?: string;
    orderId?: string;
    reportId?: string;
    retryAction?: string;
    status?: string;
  }>;
};

const FILTER_STATUSES = new Set(["", "REPORTED", "APPROVED", "REJECTED"]);

export default async function AdminShortageReportsPage({ searchParams }: PageProps) {
  const params = await searchParams;
  const status = params.status === undefined
    ? "REPORTED"
    : FILTER_STATUSES.has(params.status) ? params.status : "REPORTED";
  const orderId = params.orderId?.trim() || undefined;
  const listState = await loadReports(status || undefined, orderId);
  const selectedId = params.reportId ?? listState.reports[0]?.reportId;
  const detailState = selectedId ? await loadReport(selectedId) : { report: null, error: false as const };

  return (
    <div className="admin-page">
      <div className="admin-heading">
        <div>
          <h1>공급처 품절 보고</h1>
          <p>공급처 보고를 확인하고 배송 그룹 주문 전체의 품절 여부를 결정하세요.</p>
        </div>
      </div>

      {params.message ? <div className="notice"><strong>알림</strong><span>{params.message}</span></div> : null}
      {listState.error ? <div className="notice danger"><strong>품절 보고를 불러오지 못했습니다</strong><span>잠시 뒤 다시 시도해 주세요.</span></div> : null}

      <form action="/admin/shortage-reports" className="admin-filters">
        <input aria-label="주문 ID" defaultValue={orderId ?? ""} name="orderId" placeholder="주문 ID" />
        <select aria-label="품절 보고 상태" defaultValue={status} name="status">
          <option value="REPORTED">검토 대기</option>
          <option value="APPROVED">승인</option>
          <option value="REJECTED">거절</option>
          <option value="">전체</option>
        </select>
        <button className="button" type="submit">조회</button>
      </form>

      <div className="admin-orders-layout">
        <section className="admin-panel">
          <div className="admin-panel-head"><h2>보고 목록</h2><span>{listState.reports.length}건</span></div>
          <div className="admin-inquiry-list">
            {listState.reports.map((report) => (
              <Link
                className="admin-inquiry-card"
                href={reportHref(report.reportId, status, orderId)}
                key={report.reportId}
              >
                <div>
                  <strong>{report.orderNumber}</strong>
                  <span className={`admin-badge ${report.status === "REPORTED" ? "warning" : "neutral"}`}>
                    {shortageStatusLabel(report.status)}
                  </span>
                </div>
                <dl>
                  <div><dt>공급처</dt><dd>{report.supplierName || "확인 필요"}</dd></div>
                  <div><dt>사유</dt><dd>{shortageReasonLabel(report.reasonCode)}</dd></div>
                  <div><dt>보고시각</dt><dd>{dateTime(report.reportedAt)}</dd></div>
                </dl>
              </Link>
            ))}
            {!listState.error && listState.reports.length === 0 ? (
              <div className="admin-empty compact"><strong>조회된 품절 보고가 없습니다</strong><span>다른 상태를 선택해 보세요.</span></div>
            ) : null}
          </div>
        </section>

        <section className="admin-panel admin-order-detail">
          {detailState.error ? (
            <div className="notice danger"><strong>품절 보고 상세를 불러오지 못했습니다</strong><span>목록에서 다시 선택해 주세요.</span></div>
          ) : detailState.report ? (
            <ShortageReportDetail
              approveAction={approveShortageReport}
              rejectAction={rejectShortageReport}
              report={detailState.report}
              retry={{ action: params.retryAction, key: params.idempotencyKey }}
            />
          ) : (
            <div className="admin-empty"><strong>품절 보고를 선택하세요</strong><span>선택한 보고의 주문과 검토 상태를 확인할 수 있습니다.</span></div>
          )}
        </section>
      </div>
    </div>
  );
}

function dateTime(value: string | null) {
  return value ? new Date(value).toLocaleString("ko-KR") : "-";
}

function reportHref(reportId: string, status: string, orderId?: string) {
  const search = new URLSearchParams({ reportId });
  if (status) search.set("status", status);
  if (orderId) search.set("orderId", orderId);
  return `/admin/shortage-reports?${search}`;
}

async function loadReports(status?: string, orderId?: string) {
  try {
    return { reports: await getAdminShortageReports({ status, orderId }), error: false as const };
  } catch {
    return { reports: [] as AdminShortageReport[], error: true as const };
  }
}

async function loadReport(reportId: string) {
  try {
    return { report: await getAdminShortageReport(reportId), error: false as const };
  } catch {
    return { report: null, error: true as const };
  }
}
