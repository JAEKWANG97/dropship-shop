/** @jsxImportSource react */
"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { SupplierOrderApiError } from "@/lib/supplier-orders";
import {
  claimTaskStatusLabel,
  claimTaskTypeLabel,
  listSupplierClaimTasks,
  type ClaimTaskStatus,
  type SupplierClaimTask,
} from "@/lib/supplier-claims";

const FILTER_STATUSES = ["", "OPEN", "ANSWERED", "CLOSED"] as const;

export default function SupplierClaimTasksPage() {
  const [status, setStatus] = useState<(typeof FILTER_STATUSES)[number]>("");
  const [tasks, setTasks] = useState<SupplierClaimTask[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  function changeStatus(next: (typeof FILTER_STATUSES)[number]) {
    setLoading(true);
    setError("");
    setStatus(next);
  }

  useEffect(() => {
    let active = true;
    listSupplierClaimTasks({ status: status || undefined })
      .then((value) => active && setTasks(value))
      .catch((reason) => active && setError(loadError(reason)))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [status]);

  return (
    <SupplierClaimTaskListView
      error={error}
      loading={loading}
      onStatusChange={changeStatus}
      status={status}
      tasks={tasks}
    />
  );
}

export function SupplierClaimTaskListView({
  error,
  loading,
  onStatusChange,
  status,
  tasks,
}: {
  error: string;
  loading: boolean;
  onStatusChange?: (status: (typeof FILTER_STATUSES)[number]) => void;
  status: string;
  tasks: SupplierClaimTask[];
}) {
  return (
    <div className="supplier-page">
      <div className="admin-heading">
        <div>
          <h1>클레임 작업</h1>
          <p>Coreable이 요청한 사실만 확인하고 답변하세요.</p>
        </div>
      </div>

      {onStatusChange ? (
        <label className="admin-filters">
          처리 상태
          <select
            aria-label="클레임 작업 상태"
            onChange={(event) => onStatusChange(event.target.value as (typeof FILTER_STATUSES)[number])}
            value={status}
          >
            <option value="">전체</option>
            <option value="OPEN">답변 대기</option>
            <option value="ANSWERED">답변 완료</option>
            <option value="CLOSED">처리 종료</option>
          </select>
        </label>
      ) : null}

      {error ? (
        <div className="notice danger">
          <strong>클레임 작업을 불러오지 못했습니다</strong>
          <span>{error}</span>
        </div>
      ) : null}

      <section className="admin-panel">
        <div className="admin-panel-head">
          <h2>요청 내역</h2>
          <span>{loading ? "불러오는 중" : `${tasks.length}건`}</span>
        </div>
        <div className="admin-inquiry-list">
          {tasks.map((task) => (
            <Link
              className="admin-inquiry-card"
              href={`/supplier/claim-tasks/${encodeURIComponent(task.taskId)}`}
              key={task.taskId}
            >
              <div>
                <strong>{claimTaskTypeLabel(task.requestedType)}</strong>
                <span className={`admin-badge ${task.status === "OPEN" ? "warning" : "neutral"}`}>
                  {claimTaskStatusLabel(task.status)}
                </span>
              </div>
              <dl>
                <div><dt>주문번호</dt><dd>{task.orderNumber}</dd></div>
                <div><dt>답변기한</dt><dd>{dateTime(task.dueAt)}</dd></div>
                <div><dt>요청시각</dt><dd>{dateTime(task.requestedAt)}</dd></div>
              </dl>
            </Link>
          ))}
          {!loading && !error && tasks.length === 0 ? (
            <div className="admin-empty compact">
              <strong>해당 상태의 요청이 없습니다</strong>
              <span>새 요청이 오면 이곳에 표시됩니다.</span>
            </div>
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

export function taskStatusFilterAllowed(value: string): value is Exclude<ClaimTaskStatus, "UNKNOWN"> | "" {
  return FILTER_STATUSES.includes(value as (typeof FILTER_STATUSES)[number]);
}
