/** @jsxImportSource react */

import Link from "next/link";
import {
  getAdminSupplierClaimTask,
  getAdminSupplierClaimTasks,
} from "@/lib/admin";
import {
  claimTaskStatusLabel,
  claimTaskTypeLabel,
  type AdminSupplierClaimTask,
} from "@/lib/supplier-claims";
import { closeSupplierClaimTask } from "../orders/actions";
import { AdminSupplierClaimTaskDetail } from "./task-detail";

type PageProps = {
  searchParams: Promise<{
    claimId?: string;
    idempotencyKey?: string;
    message?: string;
    orderId?: string;
    retryAction?: string;
    status?: string;
    taskId?: string;
  }>;
};

const FILTER_STATUSES = new Set(["", "OPEN", "ANSWERED", "CLOSED"]);

export default async function AdminSupplierClaimTasksPage({ searchParams }: PageProps) {
  const params = await searchParams;
  const status = params.status === undefined
    ? "OPEN"
    : FILTER_STATUSES.has(params.status) ? params.status : "OPEN";
  const claimId = params.claimId?.trim() || undefined;
  const orderId = params.orderId?.trim() || undefined;
  const listState = await loadTasks({ status: status || undefined, claimId, orderId });
  const selectedId = params.taskId ?? listState.tasks[0]?.taskId;
  const detailState = selectedId ? await loadTask(selectedId) : { error: false as const, task: null };

  return (
    <div className="admin-page">
      <div className="admin-heading">
        <div>
          <h1>공급처 클레임 작업</h1>
          <p>전체 주문의 공급처 사실 확인 요청과 append-only 답변 이력을 검토하세요.</p>
        </div>
      </div>

      {params.message ? <div className="notice"><strong>알림</strong><span>{params.message}</span></div> : null}
      {listState.error ? (
        <div className="notice danger"><strong>공급처 작업 목록을 불러오지 못했습니다</strong><span>필터와 API 상태를 확인한 뒤 다시 시도해 주세요.</span></div>
      ) : null}

      <form action="/admin/supplier-claim-tasks" className="admin-filters">
        <select aria-label="공급처 클레임 작업 상태" defaultValue={status} name="status">
          <option value="OPEN">답변 대기</option>
          <option value="ANSWERED">답변 완료</option>
          <option value="CLOSED">처리 종료</option>
          <option value="">전체</option>
        </select>
        <input aria-label="클레임 ID" defaultValue={claimId ?? ""} name="claimId" placeholder="클레임 ID" />
        <input aria-label="주문 ID" defaultValue={orderId ?? ""} name="orderId" placeholder="주문 ID" />
        <button className="button" type="submit">조회</button>
      </form>

      <div className="admin-orders-layout">
        <section className="admin-panel">
          <div className="admin-panel-head"><h2>작업 목록</h2><span>{listState.tasks.length}건</span></div>
          <div className="admin-inquiry-list">
            {listState.tasks.map((task) => (
              <Link
                className="admin-inquiry-card"
                href={taskHref(task.taskId, { status, claimId, orderId })}
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
                  <div><dt>공급처</dt><dd>{task.supplierName || "확인 필요"}</dd></div>
                  <div><dt>답변기한</dt><dd>{dateTime(task.dueAt)}</dd></div>
                </dl>
              </Link>
            ))}
            {!listState.error && listState.tasks.length === 0 ? (
              <div className="admin-empty compact"><strong>조회된 공급처 작업이 없습니다</strong><span>다른 상태나 연결 ID로 검색해 보세요.</span></div>
            ) : null}
          </div>
        </section>

        <section className="admin-panel admin-order-detail">
          {detailState.error ? (
            <div className="notice danger"><strong>작업 상세를 불러오지 못했습니다</strong><span>목록에서 다시 선택해 주세요.</span></div>
          ) : detailState.task ? (
            <AdminSupplierClaimTaskDetail
              closeAction={closeSupplierClaimTask}
              retry={{ action: params.retryAction, key: params.idempotencyKey }}
              task={detailState.task}
            />
          ) : (
            <div className="admin-empty"><strong>공급처 작업을 선택하세요</strong><span>선택한 작업의 연결 정보와 전체 답변 이력을 확인할 수 있습니다.</span></div>
          )}
        </section>
      </div>
    </div>
  );
}

async function loadTasks(params: { status?: string; claimId?: string; orderId?: string }) {
  try {
    return { error: false as const, tasks: await getAdminSupplierClaimTasks(params) };
  } catch {
    return { error: true as const, tasks: [] as AdminSupplierClaimTask[] };
  }
}

async function loadTask(taskId: string) {
  try {
    return { error: false as const, task: await getAdminSupplierClaimTask(taskId) };
  } catch {
    return { error: true as const, task: null };
  }
}

function taskHref(
  taskId: string,
  filters: { status: string; claimId?: string; orderId?: string },
) {
  const search = new URLSearchParams({ taskId });
  if (filters.status) search.set("status", filters.status);
  if (filters.claimId) search.set("claimId", filters.claimId);
  if (filters.orderId) search.set("orderId", filters.orderId);
  return `/admin/supplier-claim-tasks?${search}`;
}

function dateTime(value: string | null) {
  return value ? new Date(value).toLocaleString("ko-KR") : "-";
}
