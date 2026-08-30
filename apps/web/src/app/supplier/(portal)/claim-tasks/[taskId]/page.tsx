/** @jsxImportSource react */
"use client";

import Link from "next/link";
import { use, useEffect, useRef, useState, type FormEvent } from "react";
import {
  listSupplierCarriers,
  releaseSupplierCommandKey,
  supplierCommandKey,
  SupplierOrderApiError,
  type SupplierCarrier,
} from "@/lib/supplier-orders";
import {
  claimFactSummary,
  claimTaskStatusLabel,
  claimTaskTypeLabel,
  getSupplierClaimTask,
  latestCorrectableFact,
  submitSupplierClaimFact,
  supplierClaimTaskCanAnswer,
  type ClaimTaskType,
  type SupplierClaimFact,
  type SupplierClaimFactInput,
  type SupplierClaimTask,
} from "@/lib/supplier-claims";

type PageProps = { params: Promise<{ taskId: string }> };

export default function SupplierClaimTaskPage({ params }: PageProps) {
  const { taskId } = use(params);
  const [task, setTask] = useState<SupplierClaimTask | null>(null);
  const [carriers, setCarriers] = useState<SupplierCarrier[]>([]);
  const [loadedAt, setLoadedAt] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    Promise.allSettled([getSupplierClaimTask(taskId), listSupplierCarriers()])
      .then(([taskResult, carrierResult]) => {
        if (!active) return;
        if (taskResult.status === "fulfilled") {
          setTask(taskResult.value);
          setLoadedAt(Date.now());
        }
        else setError(loadError(taskResult.reason));
        if (carrierResult.status === "fulfilled") setCarriers(carrierResult.value);
      })
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [taskId]);

  if (loading) return <div className="supplier-page"><div className="notice">클레임 작업을 불러오는 중입니다.</div></div>;
  if (!task) {
    return (
      <div className="supplier-page">
        <div className="notice danger">{error}</div>
        <Link className="button" href="/supplier/claim-tasks">클레임 작업 목록</Link>
      </div>
    );
  }
  return <SupplierClaimTaskDetailView carriers={carriers} initialTask={task} loadedAt={loadedAt ?? 0} />;
}

export function SupplierClaimTaskDetailView({
  carriers,
  initialTask,
  loadedAt,
}: {
  carriers: SupplierCarrier[];
  initialTask: SupplierClaimTask;
  loadedAt: number;
}) {
  const [task, setTask] = useState(initialTask);
  const [viewNow, setViewNow] = useState(loadedAt);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const commandKeys = useRef(new Map<string, string>());
  const correctsFact = task.status === "ANSWERED" ? latestCorrectableFact(task) : null;
  const canAnswer = supplierClaimTaskCanAnswer(task, viewNow);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    const input = buildFactInput(
      task,
      new FormData(event.currentTarget),
      Date.now(),
      carriers.map((carrier) => carrier.carrierCode),
    );
    if (!input) {
      setError("입력값과 확인 시각을 다시 확인해 주세요.");
      return;
    }
    const action = `fact:${task.taskId}:${JSON.stringify(input)}`;
    const key = supplierCommandKey(commandKeys.current, action);
    setBusy(true);
    try {
      const updated = await submitSupplierClaimFact(task.taskId, input, key);
      commandKeys.current.delete(action);
      setTask(updated);
      setViewNow(Date.now());
    } catch (reason) {
      releaseSupplierCommandKey(commandKeys.current, action, reason);
      if (reason instanceof SupplierOrderApiError && [403, 404, 409].includes(reason.status)) {
        try {
          setTask(await getSupplierClaimTask(task.taskId));
          setViewNow(Date.now());
        } catch {
          // Keep the last safe projection visible when conflict refresh also fails.
        }
      }
      setError(mutationError(reason));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="supplier-page">
      <div className="admin-heading">
        <div>
          <Link className="admin-text-link" href="/supplier/claim-tasks">클레임 작업 목록</Link>
          <h1>{claimTaskTypeLabel(task.requestedType)}</h1>
          <p>{task.instructions}</p>
        </div>
        <span className={`admin-badge ${task.status === "OPEN" ? "warning" : "neutral"}`}>
          {claimTaskStatusLabel(task.status)}
        </span>
      </div>

      {error ? <div className="notice danger"><strong>답변을 저장하지 못했습니다</strong><span>{error}</span></div> : null}

      <section className="admin-panel">
        <div className="admin-panel-head"><h2>요청 정보</h2><span>고객정보 없음</span></div>
        <dl className="summary-list">
          <Row label="주문번호" value={task.orderNumber} />
          <Row label="요청시각" value={dateTime(task.requestedAt)} />
          <Row label="답변기한" value={dateTime(task.dueAt)} />
          <Row label="답변시각" value={dateTime(task.answeredAt)} />
        </dl>
        {task.orderDetailAvailable === true ? (
          <Link className="button" href={`/supplier/orders/${encodeURIComponent(task.orderNumber)}`}>출고 요청 보기</Link>
        ) : null}
        <div className="admin-list compact">
          {task.items.map((item, index) => (
            <div key={`${item.productName}-${item.optionName}-${index}`}>
              <strong>{item.productName || "상품명 확인 필요"}</strong>
              <span>{item.optionName || "옵션 없음"} / {item.quantity}개</span>
            </div>
          ))}
        </div>
      </section>

      <section className="admin-panel">
        <div className="admin-panel-head"><h2>답변 이력</h2><span>{task.facts.length}건</span></div>
        <div className="admin-list compact">
          {task.facts.map((fact, index) => (
            <div key={fact.factId}>
              <strong>{index === 0 ? "최초 답변" : `${index}차 정정`}</strong>
              <span>{factDisplay(fact)}</span>
              <span>{dateTime(fact.createdAt)}</span>
            </div>
          ))}
          {task.facts.length === 0 ? <div><span>아직 등록된 답변이 없습니다.</span></div> : null}
        </div>
      </section>

      {canAnswer ? (
        <section className="admin-panel">
          <div className="admin-panel-head">
            <h2>{task.status === "OPEN" ? "사실 답변" : "최신 답변 정정"}</h2>
            <span>{correctsFact ? "이전 기록은 보존됩니다" : "정해진 값만 저장됩니다"}</span>
          </div>
          <form className="admin-inline-form" onSubmit={submit}>
            <FactFields carriers={carriers} now={viewNow} task={task} />
            <button className="button primary" disabled={busy} type="submit">
              {busy ? "저장 중" : task.status === "OPEN" ? "답변 저장" : "정정 저장"}
            </button>
          </form>
        </section>
      ) : (
        <div className={`notice ${task.status === "UNKNOWN" || task.requestedType === "UNKNOWN" ? "danger" : ""}`}>
          <strong>현재 답변을 입력할 수 없습니다</strong>
          <span>{answerBlockedMessage(task, viewNow)}</span>
        </div>
      )}
    </div>
  );
}

function FactFields({ carriers, now, task }: { carriers: SupplierCarrier[]; now: number; task: SupplierClaimTask }) {
  const min = localDateTime(task.requestedAt ? Date.parse(task.requestedAt) : NaN);
  const max = localDateTime(now);

  if (task.requestedType === "SHIPMENT_STOP_RESULT") {
    return (
      <>
        <ResultSelect options={[['STOPPED', '출고 중단 완료'], ['ALREADY_SHIPPED', '이미 출고됨'], ['UNCONFIRMED', '확인 불가']]} />
        <TimestampField max={max} min={min} name="checkedAt" title="확인 시각" />
      </>
    );
  }
  if (task.requestedType === "RETURN_INSTRUCTIONS") {
    return (
      <>
        <label>
          반품 방법
          <select defaultValue="" name="methodCode" required>
            <option disabled value="">방법을 선택하세요</option>
            <option value="COURIER_PICKUP">택배사 수거</option>
            <option value="CUSTOMER_PREPAID">고객 선불 발송</option>
            <option value="CUSTOMER_COD">고객 착불 발송</option>
          </select>
        </label>
        <label>
          택배사 (선택)
          <select defaultValue="" name="carrierCode">
            <option value="">선택하지 않음</option>
            {carriers.map((carrier) => <option key={carrier.carrierCode} value={carrier.carrierCode}>{carrier.carrierName}</option>)}
          </select>
        </label>
      </>
    );
  }
  if (task.requestedType === "RETURN_RECEIVED") {
    return (
      <>
        <ResultSelect options={[['RECEIVED', '수령함'], ['NOT_RECEIVED', '아직 수령하지 못함']]} />
        <TimestampField max={max} min={min} name="checkedAt" title="확인 시각" />
      </>
    );
  }
  if (task.requestedType === "INSPECTION_RESULT") {
    return (
      <>
        <ResultSelect options={[
          ['DEFECT_CONFIRMED', '상품 하자 확인'],
          ['NO_DEFECT', '하자 없음'],
          ['DAMAGED_IN_TRANSIT', '배송 중 파손'],
          ['UNDETERMINED', '판단 불가'],
        ]} />
        <TimestampField max={max} min={min} name="inspectedAt" title="검수 시각" />
      </>
    );
  }
  return null;
}

function ResultSelect({ options }: { options: ReadonlyArray<readonly [string, string]> }) {
  return (
    <label>
      확인 결과
      <select defaultValue="" name="resultCode" required>
        <option disabled value="">결과를 선택하세요</option>
        {options.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select>
    </label>
  );
}

function TimestampField({ max, min, name, title }: { max: string; min: string; name: string; title: string }) {
  return (
    <label>
      {title}
      <input defaultValue={max} max={max || undefined} min={min || undefined} name={name} required step="1" type="datetime-local" />
    </label>
  );
}

export function buildFactInput(
  task: SupplierClaimTask,
  formData: FormData,
  now: number,
  allowedCarrierCodes: string[] = [],
): SupplierClaimFactInput | null {
  if (!supplierClaimTaskCanAnswer(task, now) || task.requestedAt === null) return null;
  const correctsFactId = task.status === "ANSWERED" ? latestCorrectableFact(task)?.factId ?? null : null;
  if (task.status === "ANSWERED" && !correctsFactId) return null;
  const resultCode = value(formData, "resultCode");

  if (task.requestedType === "RETURN_INSTRUCTIONS") {
    const methodCode = value(formData, "methodCode");
    const carrierCode = value(formData, "carrierCode");
    if (!['COURIER_PICKUP', 'CUSTOMER_PREPAID', 'CUSTOMER_COD'].includes(methodCode)) return null;
    if (carrierCode && !allowedCarrierCodes.includes(carrierCode)) return null;
    return {
      type: task.requestedType,
      payload: {
        methodCode: methodCode as "COURIER_PICKUP" | "CUSTOMER_PREPAID" | "CUSTOMER_COD",
        carrierCode: carrierCode || null,
      },
      correctsFactId,
    };
  }

  const timeField = task.requestedType === "INSPECTION_RESULT" ? "inspectedAt" : "checkedAt";
  const observedAt = canonicalObservedAt(value(formData, timeField), task.requestedAt, now);
  if (!observedAt) return null;

  if (task.requestedType === "SHIPMENT_STOP_RESULT" && ['STOPPED', 'ALREADY_SHIPPED', 'UNCONFIRMED'].includes(resultCode)) {
    return { type: task.requestedType, payload: { resultCode: resultCode as "STOPPED" | "ALREADY_SHIPPED" | "UNCONFIRMED", checkedAt: observedAt }, correctsFactId };
  }
  if (task.requestedType === "RETURN_RECEIVED" && ['RECEIVED', 'NOT_RECEIVED'].includes(resultCode)) {
    return { type: task.requestedType, payload: { resultCode: resultCode as "RECEIVED" | "NOT_RECEIVED", checkedAt: observedAt }, correctsFactId };
  }
  if (task.requestedType === "INSPECTION_RESULT" && ['DEFECT_CONFIRMED', 'NO_DEFECT', 'DAMAGED_IN_TRANSIT', 'UNDETERMINED'].includes(resultCode)) {
    return { type: task.requestedType, payload: { resultCode: resultCode as "DEFECT_CONFIRMED" | "NO_DEFECT" | "DAMAGED_IN_TRANSIT" | "UNDETERMINED", inspectedAt: observedAt }, correctsFactId };
  }
  return null;
}

function canonicalObservedAt(raw: string, requestedAt: string, now: number) {
  const observed = Date.parse(raw);
  const requested = Date.parse(requestedAt);
  return Number.isFinite(observed) && Number.isFinite(requested) && observed >= requested && observed <= now
    ? new Date(observed).toISOString()
    : null;
}

function factDisplay(fact: SupplierClaimFact) {
  const summary = claimFactSummary(fact);
  return summary
    ? `${summary.result}${summary.observedAt ? ` · ${dateTime(summary.observedAt)}` : ""}`
    : "답변 내용을 확인할 수 없습니다.";
}

function answerBlockedMessage(task: SupplierClaimTask, now: number) {
  if (task.status === "CLOSED") return "Coreable이 작업을 종료했습니다. 새 답변이나 정정은 저장할 수 없습니다.";
  if (task.status === "UNKNOWN" || task.requestedType === "UNKNOWN" || task.instructionCode === null) {
    return "알 수 없는 요청에는 답변하지 않습니다. Coreable에 문의해 주세요.";
  }
  if (task.dueAt === null || Date.parse(task.dueAt) <= now) return "답변 기한이 지났습니다. Coreable에 새 요청을 문의해 주세요.";
  return "답변 이력을 안전하게 확인할 수 없습니다. Coreable에 문의해 주세요.";
}

function loadError(error: unknown) {
  if (error instanceof SupplierOrderApiError && error.status === 404) return "클레임 작업을 찾을 수 없습니다.";
  if (error instanceof SupplierOrderApiError && error.status === 403) return "현재 계약 또는 포털 권한으로 확인할 수 없습니다.";
  return "클레임 작업을 불러오지 못했습니다. 잠시 뒤 다시 시도해 주세요.";
}

function mutationError(error: unknown) {
  if (error instanceof SupplierOrderApiError && error.status === 409) return "작업 상태 또는 최신 답변이 변경되었습니다. 갱신된 내용을 확인해 주세요.";
  if (error instanceof SupplierOrderApiError && [400, 403, 404].includes(error.status)) return "현재 요청에는 이 답변을 저장할 수 없습니다. 갱신된 내용을 확인해 주세요.";
  return "처리 결과가 불확실합니다. 같은 입력으로 다시 시도해 주세요.";
}

function Row({ label, value }: { label: string; value: string }) {
  return <div><dt>{label}</dt><dd>{value}</dd></div>;
}

function value(formData: FormData, name: string) {
  const result = formData.get(name);
  return typeof result === "string" ? result.trim() : "";
}

function localDateTime(time: number) {
  if (!Number.isFinite(time)) return "";
  const date = new Date(time - new Date(time).getTimezoneOffset() * 60_000);
  return date.toISOString().slice(0, 19);
}

function dateTime(value: string | null) {
  return value ? new Date(value).toLocaleString("ko-KR") : "-";
}

export function instructionForTaskType(type: ClaimTaskType) {
  return claimTaskTypeLabel(type);
}
