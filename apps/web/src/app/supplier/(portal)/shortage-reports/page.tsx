/** @jsxImportSource react */
"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { SupplierOrderApiError } from "@/lib/supplier-orders";
import {
  listSupplierShortageReports,
  shortageReasonLabel,
  shortageStatusLabel,
  type SupplierShortageReport,
} from "@/lib/supplier-claims";

export default function SupplierShortageReportsPage() {
  const [status, setStatus] = useState("");
  const [reports, setReports] = useState<SupplierShortageReport[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  function changeStatus(next: string) {
    setLoading(true);
    setError("");
    setStatus(next);
  }

  useEffect(() => {
    let active = true;
    listSupplierShortageReports({ status: status || undefined })
      .then((value) => active && setReports(value))
      .catch((reason) => active && setError(loadError(reason)))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [status]);

  return <SupplierShortageReportListView error={error} loading={loading} onStatusChange={changeStatus} reports={reports} status={status} />;
}

export function SupplierShortageReportListView({
  error,
  loading,
  onStatusChange,
  reports,
  status = "",
}: {
  error: string;
  loading: boolean;
  onStatusChange?: (status: string) => void;
  reports: SupplierShortageReport[];
  status?: string;
}) {
  return (
    <div className="supplier-page">
      <div className="admin-heading">
        <div>
          <h1>품절 보고</h1>
          <p>Coreable에 인계한 품절 보고의 검토 상태를 확인하세요.</p>
        </div>
      </div>
      {onStatusChange ? (
        <label className="admin-filters">
          검토 상태
          <select aria-label="품절 보고 상태" onChange={(event) => onStatusChange(event.target.value)} value={status}>
            <option value="">전체</option>
            <option value="REPORTED">확인 중</option>
            <option value="APPROVED">품절 확인 완료</option>
            <option value="REJECTED">보고 반려</option>
          </select>
        </label>
      ) : null}
      {error ? <div className="notice danger"><strong>품절 보고를 불러오지 못했습니다</strong><span>{error}</span></div> : null}
      <section className="admin-panel">
        <div className="admin-panel-head"><h2>보고 내역</h2><span>{loading ? "불러오는 중" : `${reports.length}건`}</span></div>
        <div className="admin-inquiry-list">
          {reports.map((report) => (
            <Link className="admin-inquiry-card" href={`/supplier/shortage-reports/${encodeURIComponent(report.reportId)}`} key={report.reportId}>
              <div>
                <strong>{report.orderNumber}</strong>
                <span className={`admin-badge ${report.status === "REPORTED" ? "warning" : "neutral"}`}>
                  {shortageStatusLabel(report.status)}
                </span>
              </div>
              <dl>
                <div><dt>보고 사유</dt><dd>{shortageReasonLabel(report.reasonCode)}</dd></div>
                <div><dt>보고시각</dt><dd>{dateTime(report.reportedAt)}</dd></div>
              </dl>
            </Link>
          ))}
          {!loading && !error && reports.length === 0 ? (
            <div className="admin-empty compact"><strong>품절 보고 내역이 없습니다</strong><span>출고 요청에서 보고한 내역이 이곳에 표시됩니다.</span></div>
          ) : null}
        </div>
      </section>
    </div>
  );
}

function loadError(error: unknown) {
  if (error instanceof SupplierOrderApiError && error.status === 403) {
    return "현재 계약 또는 포털 권한으로 확인할 수 없습니다.";
  }
  return "잠시 뒤 다시 시도해 주세요.";
}

function dateTime(value: string | null) {
  return value ? new Date(value).toLocaleString("ko-KR") : "-";
}
