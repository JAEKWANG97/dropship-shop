/** @jsxImportSource react */
"use client";

import Link from "next/link";
import { use, useEffect, useState } from "react";
import { SupplierOrderApiError } from "@/lib/supplier-orders";
import {
  getSupplierShortageReport,
  shortageReasonLabel,
  shortageStatusLabel,
  type SupplierShortageReport,
} from "@/lib/supplier-claims";

type PageProps = { params: Promise<{ reportId: string }> };

export default function SupplierShortageReportPage({ params }: PageProps) {
  const { reportId } = use(params);
  const [report, setReport] = useState<SupplierShortageReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    getSupplierShortageReport(reportId)
      .then((value) => active && setReport(value))
      .catch((reason) => active && setError(loadError(reason)))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [reportId]);

  if (loading) return <div className="supplier-page"><div className="notice">품절 보고를 불러오는 중입니다.</div></div>;
  if (!report) return <div className="supplier-page"><div className="notice danger">{error}</div><Link className="button" href="/supplier/shortage-reports">품절 보고 목록</Link></div>;
  return <SupplierShortageReportDetailView report={report} />;
}

export function SupplierShortageReportDetailView({ report }: { report: SupplierShortageReport }) {
  return (
    <div className="supplier-page">
      <div className="admin-heading">
        <div>
          <Link className="admin-text-link" href="/supplier/shortage-reports">품절 보고 목록</Link>
          <h1>{report.orderNumber}</h1>
          <p>이 보고는 배송 그룹 주문 전체에 적용됩니다.</p>
        </div>
        <span className={`admin-badge ${report.status === "REPORTED" ? "warning" : "neutral"}`}>{shortageStatusLabel(report.status)}</span>
      </div>
      <section className="admin-panel">
        <div className="admin-panel-head"><h2>보고 정보</h2><span>고객정보 없음</span></div>
        <dl className="summary-list">
          <Row label="보고 사유" value={shortageReasonLabel(report.reasonCode)} />
          <Row label="보고시각" value={dateTime(report.reportedAt)} />
          <Row label="검토시각" value={dateTime(report.reviewedAt)} />
          <Row label="검토 결과" value={reviewLabel(report)} />
        </dl>
      </section>
      <div className={`notice ${report.status === "UNKNOWN" ? "danger" : ""}`}>
        <strong>{nextActionTitle(report)}</strong>
        <span>{nextActionMessage(report)}</span>
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return <div><dt>{label}</dt><dd>{value}</dd></div>;
}

function reviewLabel(report: SupplierShortageReport) {
  return ({
    SHORTAGE_CONFIRMED: "품절 확인",
    INSUFFICIENT_EVIDENCE: "확인 근거 부족",
    FULFILLMENT_CAN_CONTINUE: "출고 계속 가능",
    UNKNOWN: "결과 확인 필요",
  } as const)[report.reviewReasonCode ?? "UNKNOWN"];
}

function nextActionTitle(report: SupplierShortageReport) {
  if (report.nextAction === "WAIT") return "Coreable에서 확인 중입니다";
  if (report.nextAction === "CONTACT_COREABLE") return "Coreable에 문의해 주세요";
  if (report.nextAction === "NONE") return "추가로 처리할 작업이 없습니다";
  return "다음 작업을 확인할 수 없습니다";
}

function nextActionMessage(report: SupplierShortageReport) {
  if (report.nextAction === "WAIT") return "검토가 끝나면 이 화면의 상태가 변경됩니다.";
  if (report.nextAction === "CONTACT_COREABLE") return "출고 작업은 자동으로 다시 열리지 않습니다.";
  if (report.nextAction === "NONE") return "주문·환불 처리는 Coreable에서 이어서 진행합니다.";
  return "임의로 출고하지 말고 Coreable에 문의해 주세요.";
}

function dateTime(value: string | null) {
  return value ? new Date(value).toLocaleString("ko-KR") : "-";
}

function loadError(error: unknown) {
  if (error instanceof SupplierOrderApiError && error.status === 404) return "품절 보고를 찾을 수 없습니다.";
  if (error instanceof SupplierOrderApiError && error.status === 403) return "현재 계약 또는 포털 권한으로 확인할 수 없습니다.";
  return "품절 보고를 불러오지 못했습니다. 잠시 뒤 다시 시도해 주세요.";
}
