/** @jsxImportSource react */

import { randomUUID } from "node:crypto";
import Link from "next/link";
import type { ComponentProps } from "react";
import { retryCommandKey, type RetryCommand } from "@/lib/admin-payment";
import {
  adminSupplierClaimTaskCanClose,
  claimFactSummary,
  claimTaskStatusLabel,
  claimTaskTypeLabel,
  type AdminSupplierClaimTask,
} from "@/lib/supplier-claims";

type FormAction = NonNullable<ComponentProps<"form">["action"]>;

export function AdminSupplierClaimTaskDetail({
  closeAction,
  task,
  retry,
}: {
  closeAction: FormAction;
  task: AdminSupplierClaimTask;
  retry: RetryCommand;
}) {
  const closeable = adminSupplierClaimTaskCanClose(task);

  return (
    <>
      <div className="admin-panel-head">
        <h2>작업 상세</h2>
        <span className={`admin-badge ${task.status === "OPEN" ? "warning" : "neutral"}`}>
          {claimTaskStatusLabel(task.status)}
        </span>
      </div>
      <div className="summary-list compact">
        <Row label="요청 유형" value={claimTaskTypeLabel(task.requestedType)} />
        <Row label="고정 요청 문구" value={task.instructions} />
        <Row label="주문번호" value={task.orderNumber} />
        <Row label="공급처" value={task.supplierName || "확인 필요"} />
        <Row label="클레임 ID" value={task.claimId} />
        <Row label="요청시각" value={dateTime(task.requestedAt)} />
        <Row label="답변기한" value={dateTime(task.dueAt)} />
        <Row label="답변시각" value={dateTime(task.answeredAt)} />
        <Row label="종료시각" value={dateTime(task.closedAt)} />
        <Row label="종료 사유" value={closeReasonLabel(task.closeReasonCode)} />
      </div>
      {task.orderId ? <Link className="button" href={`/admin/orders?orderId=${encodeURIComponent(task.orderId)}`}>주문·클레임 상세</Link> : null}

      <h3>공급처 상품</h3>
      <div className="admin-list compact">
        {task.items.map((item, index) => (
          <div key={`${item.productName}-${item.optionName}-${index}`}>
            <strong>{item.productName || "상품명 확인 필요"}</strong>
            <span>{item.optionName || "옵션 없음"} / {item.quantity}개</span>
          </div>
        ))}
      </div>

      <h3>append-only 답변 이력</h3>
      <div className="admin-list compact">
        {task.facts.map((fact, index) => {
          const summary = claimFactSummary(fact);
          return (
            <div key={fact.factId}>
              <strong>{index === 0 ? "최초 답변" : `${index}차 정정`}</strong>
              <span>{summary ? summary.result : "알 수 없는 답변은 의사결정에 사용하지 마세요."}</span>
              {summary?.observedAt ? <span>사실 확인 {dateTime(summary.observedAt)}</span> : null}
              <span>기록 {dateTime(fact.createdAt)}</span>
            </div>
          );
        })}
        {task.facts.length === 0 ? <div><span>아직 등록된 공급처 답변이 없습니다.</span></div> : null}
      </div>

      {closeable ? (
        <form action={closeAction} className="admin-inline-form">
          <input name="returnTo" type="hidden" value="queue" />
          <input name="orderId" type="hidden" value={task.orderId} />
          <input name="taskId" type="hidden" value={task.taskId} />
          <input name="expectedStatus" type="hidden" value={task.status} />
          <input name="idempotencyKey" type="hidden" value={retryCommandKey(retry, `supplier-task-close-${task.taskId}`) ?? randomUUID()} />
          <label className="wide">
            종료 사유
            <select defaultValue={task.status === "ANSWERED" ? "RESPONSE_ACCEPTED" : "NO_LONGER_NEEDED"} name="closeReasonCode" required>
              <option value="RESPONSE_ACCEPTED">답변 확인 완료</option>
              <option value="SUPERSEDED">새 작업으로 대체</option>
              <option value="NO_LONGER_NEEDED">추가 확인 불필요</option>
            </select>
            <span className="field-help">현재 화면의 OPEN/ANSWERED 상태를 함께 보내므로 상태가 바뀌면 종료하지 않습니다.</span>
          </label>
          <button className="button" type="submit">작업 종료</button>
        </form>
      ) : task.status === "UNKNOWN" || task.requestedType === "UNKNOWN" || task.instructionCode === null ? (
        <div className="notice danger"><strong>작업 계약을 확인할 수 없습니다</strong><span>종료 액션을 사용할 수 없습니다.</span></div>
      ) : null}
    </>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return <div><span>{label}</span><strong>{value}</strong></div>;
}

function closeReasonLabel(value: string | null) {
  if (value === "RESPONSE_ACCEPTED") return "답변 확인 완료";
  if (value === "SUPERSEDED") return "새 작업으로 대체";
  if (value === "NO_LONGER_NEEDED") return "추가 확인 불필요";
  if (value === "DUE_AT_EXPIRED") return "답변 기한 만료";
  if (value === "CLAIM_TERMINAL") return "클레임 종료";
  return value ? "종료 사유 확인 필요" : "-";
}

function dateTime(value: string | null) {
  return value ? new Date(value).toLocaleString("ko-KR") : "-";
}
