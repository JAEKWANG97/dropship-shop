import { supplierPortalRequest } from "./supplier-orders";

export const SHORTAGE_REASON_CODES = ["OUT_OF_STOCK", "OPTION_UNAVAILABLE", "QUANTITY_UNAVAILABLE"] as const;
export const CLAIM_TASK_TYPES = [
  "SHIPMENT_STOP_RESULT",
  "RETURN_INSTRUCTIONS",
  "RETURN_RECEIVED",
  "INSPECTION_RESULT",
] as const;

export type ShortageReasonCode = typeof SHORTAGE_REASON_CODES[number];
export type ClaimTaskType = typeof CLAIM_TASK_TYPES[number];
export type ClaimTaskStatus = "OPEN" | "ANSWERED" | "CLOSED" | "UNKNOWN";
export type AdminClaimTaskCloseReason = "RESPONSE_ACCEPTED" | "SUPERSEDED" | "NO_LONGER_NEEDED";
export type ShortageStatus = "REPORTED" | "APPROVED" | "REJECTED" | "UNKNOWN";
export type ShortageNextAction = "WAIT" | "NONE" | "CONTACT_COREABLE" | "UNKNOWN";

export const CLAIM_TASK_INSTRUCTIONS: Record<ClaimTaskType, { code: string; label: string }> = {
  SHIPMENT_STOP_RESULT: { code: "CHECK_SHIPMENT_STOP", label: "상품 발송을 멈출 수 있는지 확인해 주세요." },
  RETURN_INSTRUCTIONS: { code: "PROVIDE_RETURN_METHOD", label: "반품 수거 방법을 선택해 주세요." },
  RETURN_RECEIVED: { code: "CONFIRM_RETURN_RECEIPT", label: "반품 상품 수령 여부를 확인해 주세요." },
  INSPECTION_RESULT: { code: "INSPECT_RETURNED_ITEM", label: "반품 상품의 상태를 확인해 주세요." },
};

export type SupplierShortageReport = {
  reportId: string;
  orderNumber: string;
  reasonCode: ShortageReasonCode | "UNKNOWN";
  status: ShortageStatus;
  reportedAt: string | null;
  reviewedAt: string | null;
  reviewReasonCode: "SHORTAGE_CONFIRMED" | "INSUFFICIENT_EVIDENCE" | "FULFILLMENT_CAN_CONTINUE" | "UNKNOWN" | null;
  nextAction: ShortageNextAction;
};

export type AdminShortageReport = SupplierShortageReport & {
  orderId: string;
  supplierId: string;
  supplierName: string;
  reviewedByAdminId: string | null;
};

export type SupplierClaimTaskItem = {
  productName: string;
  optionName: string;
  quantity: number;
};

export type ShipmentStopPayload = {
  resultCode: "STOPPED" | "ALREADY_SHIPPED" | "UNCONFIRMED";
  checkedAt: string;
};

export type ReturnInstructionsPayload = {
  methodCode: "COURIER_PICKUP" | "CUSTOMER_PREPAID" | "CUSTOMER_COD";
  carrierCode: string | null;
};

export type ReturnReceivedPayload = {
  resultCode: "RECEIVED" | "NOT_RECEIVED";
  checkedAt: string;
};

export type InspectionResultPayload = {
  resultCode: "DEFECT_CONFIRMED" | "NO_DEFECT" | "DAMAGED_IN_TRANSIT" | "UNDETERMINED";
  inspectedAt: string;
};

export type SupplierClaimFactPayload =
  | ShipmentStopPayload
  | ReturnInstructionsPayload
  | ReturnReceivedPayload
  | InspectionResultPayload;

export type SupplierClaimFact = {
  factId: string;
  type: ClaimTaskType | "UNKNOWN";
  payload: SupplierClaimFactPayload | null;
  correctsFactId: string | null;
  createdAt: string | null;
  valid: boolean;
};

export type SupplierClaimTask = {
  taskId: string;
  orderNumber: string;
  items: SupplierClaimTaskItem[];
  requestedType: ClaimTaskType | "UNKNOWN";
  status: ClaimTaskStatus;
  instructionCode: string | null;
  instructions: string;
  dueAt: string | null;
  requestedAt: string | null;
  answeredAt: string | null;
  closedAt: string | null;
  closeReasonCode: string | null;
  orderDetailAvailable: boolean;
  facts: SupplierClaimFact[];
};

export type AdminSupplierClaimTask = SupplierClaimTask & {
  claimId: string;
  orderId: string;
  supplierId: string;
  supplierName: string;
  requestedByAdminId: string | null;
  closedByAdminId: string | null;
};

export type SupplierClaimFactInput = {
  type: ClaimTaskType;
  payload: SupplierClaimFactPayload;
  correctsFactId: string | null;
};

const SHORTAGE_REVIEW_CODES = ["SHORTAGE_CONFIRMED", "INSUFFICIENT_EVIDENCE", "FULFILLMENT_CAN_CONTINUE"] as const;

export async function listSupplierShortageReports(params: { status?: string } = {}) {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  const suffix = query.size ? `?${query}` : "";
  return normalizeSupplierShortageReportList(await supplierPortalRequest(`/api/supplier/shortage-reports${suffix}`));
}

export async function getSupplierShortageReport(reportId: string) {
  return normalizeSupplierShortageReport(await supplierPortalRequest(
    `/api/supplier/shortage-reports/${encodeURIComponent(reportId)}`,
  ));
}

export async function reportSupplierShortage(
  orderNumber: string,
  reasonCode: ShortageReasonCode,
  idempotencyKey: string,
) {
  return normalizeSupplierShortageReport(await supplierPortalRequest(
    `/api/supplier/orders/${encodeURIComponent(orderNumber)}/shortage-reports`,
    {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify({ reasonCode }),
    },
  ));
}

export async function listSupplierClaimTasks(params: { status?: string } = {}) {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  const suffix = query.size ? `?${query}` : "";
  return normalizeSupplierClaimTaskList(await supplierPortalRequest(`/api/supplier/claim-tasks${suffix}`));
}

export async function getSupplierClaimTask(taskId: string) {
  return normalizeSupplierClaimTask(await supplierPortalRequest(
    `/api/supplier/claim-tasks/${encodeURIComponent(taskId)}`,
  ));
}

export async function submitSupplierClaimFact(
  taskId: string,
  input: SupplierClaimFactInput,
  idempotencyKey: string,
) {
  return normalizeSupplierClaimTask(await supplierPortalRequest(
    `/api/supplier/claim-tasks/${encodeURIComponent(taskId)}/facts`,
    {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(input),
    },
  ));
}

export function normalizeSupplierShortageReportList(value: unknown): SupplierShortageReport[] {
  const wrapper = record(value);
  return array(wrapper.reports ?? value)
    .map(normalizeSupplierShortageReport)
    .filter((report) => report.reportId && report.orderNumber);
}

export function normalizeSupplierShortageReport(value: unknown): SupplierShortageReport {
  const report = record(value);
  return {
    reportId: text(report.reportId) || text(report.id),
    orderNumber: text(report.orderNumber),
    reasonCode: known(report.reasonCode, SHORTAGE_REASON_CODES, "UNKNOWN"),
    status: known(report.status, ["REPORTED", "APPROVED", "REJECTED"] as const, "UNKNOWN"),
    reportedAt: timestamp(report.reportedAt ?? report.createdAt),
    reviewedAt: timestamp(report.reviewedAt),
    reviewReasonCode: nullableKnown(report.reviewReasonCode, SHORTAGE_REVIEW_CODES),
    nextAction: known(report.nextAction, ["WAIT", "NONE", "CONTACT_COREABLE"] as const, "UNKNOWN"),
  };
}

export function normalizeAdminShortageReportList(value: unknown): AdminShortageReport[] {
  const wrapper = record(value);
  return array(wrapper.reports ?? value)
    .map(normalizeAdminShortageReport)
    .filter((report) => report.reportId && report.orderId && report.orderNumber);
}

export function normalizeAdminShortageReport(value: unknown): AdminShortageReport {
  const report = record(value);
  const order = record(report.order);
  const supplier = record(report.supplier);
  return {
    ...normalizeSupplierShortageReport(report),
    orderId: text(report.orderId) || text(order.orderId) || text(order.id),
    orderNumber: text(report.orderNumber) || text(order.orderNumber),
    supplierId: text(report.supplierId) || text(supplier.supplierId) || text(supplier.id),
    supplierName: text(report.supplierName) || text(supplier.name),
    reviewedByAdminId: nullableText(report.reviewedByAdminId),
  };
}

export function normalizeSupplierClaimTaskList(value: unknown): SupplierClaimTask[] {
  const wrapper = record(value);
  return array(wrapper.tasks ?? value)
    .map(normalizeSupplierClaimTask)
    .filter((task) => task.taskId && task.orderNumber);
}

export function normalizeSupplierClaimTask(value: unknown): SupplierClaimTask {
  const task = record(value);
  const requestedType = known(task.requestedType, CLAIM_TASK_TYPES, "UNKNOWN");
  const expectedInstruction = requestedType === "UNKNOWN" ? null : CLAIM_TASK_INSTRUCTIONS[requestedType];
  const instructionCode = text(task.instructionCode);
  const recognizedInstruction = expectedInstruction?.code === instructionCode;
  return {
    taskId: text(task.taskId) || text(task.id),
    orderNumber: text(task.orderNumber),
    items: array(task.items).map(normalizeTaskItem).filter((item) => item.quantity > 0),
    requestedType,
    status: known(task.status, ["OPEN", "ANSWERED", "CLOSED"] as const, "UNKNOWN"),
    instructionCode: recognizedInstruction ? instructionCode : null,
    instructions: recognizedInstruction ? expectedInstruction.label : "요청 내용을 확인할 수 없습니다.",
    dueAt: timestamp(task.dueAt),
    requestedAt: timestamp(task.requestedAt),
    answeredAt: timestamp(task.answeredAt),
    closedAt: timestamp(task.closedAt),
    closeReasonCode: nullableText(task.closeReasonCode),
    orderDetailAvailable: task.orderDetailAvailable === true,
    facts: array(task.facts).map(normalizeSupplierClaimFact).filter((fact) => fact.factId),
  };
}

export function normalizeAdminSupplierClaimTaskList(value: unknown): AdminSupplierClaimTask[] {
  const wrapper = record(value);
  return array(wrapper.tasks ?? value)
    .map(normalizeAdminSupplierClaimTask)
    .filter((task) => task.taskId && task.claimId && task.orderId);
}

export function normalizeAdminSupplierClaimTask(value: unknown): AdminSupplierClaimTask {
  const task = record(value);
  const order = record(task.order);
  const supplier = record(task.supplier);
  return {
    ...normalizeSupplierClaimTask(task),
    claimId: text(task.claimId),
    orderId: text(task.orderId) || text(order.orderId) || text(order.id),
    orderNumber: text(task.orderNumber) || text(order.orderNumber),
    supplierId: text(task.supplierId) || text(supplier.supplierId) || text(supplier.id),
    supplierName: text(task.supplierName) || text(supplier.name),
    requestedByAdminId: nullableText(task.requestedByAdminId),
    closedByAdminId: nullableText(task.closedByAdminId),
  };
}

export function normalizeSupplierClaimFact(value: unknown): SupplierClaimFact {
  const fact = record(value);
  const type = known(fact.type, CLAIM_TASK_TYPES, "UNKNOWN");
  const payload = type === "UNKNOWN" ? null : normalizeFactPayload(type, fact.payload);
  return {
    factId: text(fact.factId) || text(fact.id),
    type,
    payload,
    correctsFactId: nullableText(fact.correctsFactId),
    createdAt: timestamp(fact.createdAt),
    valid: type !== "UNKNOWN" && payload !== null,
  };
}

export function supplierClaimTaskCanAnswer(task: SupplierClaimTask, now = Date.now()) {
  const requestedAt = task.requestedAt === null ? NaN : Date.parse(task.requestedAt);
  const dueAt = task.dueAt === null ? NaN : Date.parse(task.dueAt);
  return (task.status === "OPEN" || task.status === "ANSWERED")
    && task.requestedType !== "UNKNOWN"
    && task.instructionCode === CLAIM_TASK_INSTRUCTIONS[task.requestedType].code
    && Number.isFinite(requestedAt)
    && Number.isFinite(dueAt)
    && dueAt > requestedAt
    && dueAt <= requestedAt + 30 * 24 * 60 * 60 * 1000
    && dueAt > now
    && (task.status === "OPEN" ? task.facts.length === 0 : latestCorrectableFact(task) !== null);
}

export function latestCorrectableFact(task: SupplierClaimTask) {
  if (task.facts.length === 0 || task.requestedType === "UNKNOWN") return null;
  const ids = new Set<string>();
  for (const [index, fact] of task.facts.entries()) {
    if (!fact.valid || fact.type !== task.requestedType || ids.has(fact.factId)) return null;
    if (index === 0 ? fact.correctsFactId !== null : fact.correctsFactId !== task.facts[index - 1].factId) return null;
    ids.add(fact.factId);
  }
  return task.facts.at(-1) ?? null;
}

export function shortageReasonLabel(code: SupplierShortageReport["reasonCode"]) {
  return ({
    OUT_OF_STOCK: "상품 전체 품절",
    OPTION_UNAVAILABLE: "주문 옵션 품절",
    QUANTITY_UNAVAILABLE: "주문 수량 확보 불가",
    UNKNOWN: "사유 확인 필요",
  } as const)[code];
}

export function shortageStatusLabel(status: ShortageStatus) {
  return ({
    REPORTED: "Coreable 확인 중",
    APPROVED: "품절 확인 완료",
    REJECTED: "품절 보고 반려",
    UNKNOWN: "상태 확인 필요",
  } as const)[status];
}

export function claimTaskTypeLabel(type: ClaimTaskType | "UNKNOWN") {
  return ({
    SHIPMENT_STOP_RESULT: "출고 중단 확인",
    RETURN_INSTRUCTIONS: "반품 방법 확인",
    RETURN_RECEIVED: "반품 수령 확인",
    INSPECTION_RESULT: "반품 상품 검수",
    UNKNOWN: "요청 내용 확인 필요",
  } as const)[type];
}

export function claimTaskStatusLabel(status: ClaimTaskStatus) {
  return ({ OPEN: "답변 대기", ANSWERED: "답변 완료", CLOSED: "처리 종료", UNKNOWN: "상태 확인 필요" } as const)[status];
}

export function adminSupplierTaskDueAt(raw: string, now = Date.now()) {
  const dueAt = Date.parse(raw ? `${raw}:00+09:00` : "");
  return Number.isFinite(dueAt) && dueAt > now && dueAt <= now + 30 * 24 * 60 * 60 * 1000
    ? new Date(dueAt).toISOString()
    : null;
}

export function adminSupplierTaskCloseAllowed(status: string, reason: string): reason is AdminClaimTaskCloseReason {
  return (status === "OPEN" || status === "ANSWERED")
    && ["RESPONSE_ACCEPTED", "SUPERSEDED", "NO_LONGER_NEEDED"].includes(reason);
}

export function adminSupplierClaimTaskCanClose(task: AdminSupplierClaimTask) {
  const expectedInstruction = task.requestedType === "UNKNOWN"
    ? null
    : CLAIM_TASK_INSTRUCTIONS[task.requestedType];
  return (task.status === "OPEN" || task.status === "ANSWERED")
    && expectedInstruction !== null
    && task.instructionCode === expectedInstruction.code
    && Boolean(task.taskId && task.orderId && task.claimId);
}

export function adminSupplierClaimTaskCreateCommand(requestedType: string, dueAt: string) {
  const type = CLAIM_TASK_TYPES.includes(requestedType as ClaimTaskType)
    ? requestedType as ClaimTaskType
    : null;
  if (!type || !dueAt) return null;
  return {
    requestedType: type,
    instructionCode: CLAIM_TASK_INSTRUCTIONS[type].code,
    dueAt,
  };
}

export function adminSupplierClaimTaskCloseCommand(expectedStatus: string, closeReasonCode: string) {
  if (!adminSupplierTaskCloseAllowed(expectedStatus, closeReasonCode)) return null;
  return { expectedStatus, closeReasonCode };
}

export function adminShortageReviewCommand(
  action: "approve" | "reject",
  expectedStatus: string,
  reviewReasonCode: string,
) {
  const allowed = action === "approve"
    ? reviewReasonCode === "SHORTAGE_CONFIRMED"
    : ["INSUFFICIENT_EVIDENCE", "FULFILLMENT_CAN_CONTINUE"].includes(reviewReasonCode);
  return expectedStatus === "REPORTED" && allowed
    ? { expectedStatus: "REPORTED" as const, reviewReasonCode }
    : null;
}

export function claimFactSummary(fact: SupplierClaimFact) {
  if (!fact.valid || fact.payload === null) return null;
  const payload = fact.payload;
  if (fact.type === "RETURN_INSTRUCTIONS" && "methodCode" in payload) {
    const result = ({
      COURIER_PICKUP: "택배사 수거",
      CUSTOMER_PREPAID: "고객 선불 발송",
      CUSTOMER_COD: "고객 착불 발송",
    } as const)[payload.methodCode];
    return { result: `${result}${payload.carrierCode ? ` · ${payload.carrierCode}` : ""}`, observedAt: null };
  }
  if (fact.type === "SHIPMENT_STOP_RESULT" && "checkedAt" in payload && "resultCode" in payload) {
    const result = ({ STOPPED: "출고 중단 완료", ALREADY_SHIPPED: "이미 출고됨", UNCONFIRMED: "확인 불가" } as const)
      [payload.resultCode as "STOPPED" | "ALREADY_SHIPPED" | "UNCONFIRMED"];
    return result ? { result, observedAt: payload.checkedAt } : null;
  }
  if (fact.type === "RETURN_RECEIVED" && "checkedAt" in payload && "resultCode" in payload) {
    const result = ({ RECEIVED: "수령함", NOT_RECEIVED: "아직 수령하지 못함" } as const)
      [payload.resultCode as "RECEIVED" | "NOT_RECEIVED"];
    return result ? { result, observedAt: payload.checkedAt } : null;
  }
  if (fact.type === "INSPECTION_RESULT" && "inspectedAt" in payload && "resultCode" in payload) {
    const result = ({
      DEFECT_CONFIRMED: "상품 하자 확인",
      NO_DEFECT: "하자 없음",
      DAMAGED_IN_TRANSIT: "배송 중 파손",
      UNDETERMINED: "판단 불가",
    } as const)[payload.resultCode as "DEFECT_CONFIRMED" | "NO_DEFECT" | "DAMAGED_IN_TRANSIT" | "UNDETERMINED"];
    return result ? { result, observedAt: payload.inspectedAt } : null;
  }
  return null;
}

function normalizeTaskItem(value: unknown): SupplierClaimTaskItem {
  const item = record(value);
  return {
    productName: text(item.productName),
    optionName: text(item.optionName),
    quantity: positiveInteger(item.quantity),
  };
}

function normalizeFactPayload(type: ClaimTaskType, value: unknown): SupplierClaimFactPayload | null {
  const payload = record(value);
  if (type === "SHIPMENT_STOP_RESULT") {
    const resultCode = knownOrNull(payload.resultCode, ["STOPPED", "ALREADY_SHIPPED", "UNCONFIRMED"] as const);
    const checkedAt = timestamp(payload.checkedAt);
    return resultCode && checkedAt ? { resultCode, checkedAt } : null;
  }
  if (type === "RETURN_INSTRUCTIONS") {
    const methodCode = knownOrNull(payload.methodCode, ["COURIER_PICKUP", "CUSTOMER_PREPAID", "CUSTOMER_COD"] as const);
    return methodCode ? { methodCode, carrierCode: nullableText(payload.carrierCode) } : null;
  }
  if (type === "RETURN_RECEIVED") {
    const resultCode = knownOrNull(payload.resultCode, ["RECEIVED", "NOT_RECEIVED"] as const);
    const checkedAt = timestamp(payload.checkedAt);
    return resultCode && checkedAt ? { resultCode, checkedAt } : null;
  }
  const resultCode = knownOrNull(payload.resultCode, ["DEFECT_CONFIRMED", "NO_DEFECT", "DAMAGED_IN_TRANSIT", "UNDETERMINED"] as const);
  const inspectedAt = timestamp(payload.inspectedAt);
  return resultCode && inspectedAt ? { resultCode, inspectedAt } : null;
}

function record(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

function array(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function text(value: unknown) {
  return typeof value === "string" ? value.trim() : "";
}

function nullableText(value: unknown) {
  return text(value) || null;
}

function timestamp(value: unknown) {
  const result = text(value);
  return result && Number.isFinite(Date.parse(result)) ? result : null;
}

function positiveInteger(value: unknown) {
  return typeof value === "number" && Number.isInteger(value) && value > 0 ? value : 0;
}

function known<const T extends readonly string[]>(value: unknown, values: T, fallback: "UNKNOWN"): T[number] | "UNKNOWN" {
  const result = text(value);
  return values.includes(result) ? result as T[number] : fallback;
}

function knownOrNull<const T extends readonly string[]>(value: unknown, values: T): T[number] | null {
  const result = text(value);
  return values.includes(result) ? result as T[number] : null;
}

function nullableKnown<const T extends readonly string[]>(value: unknown, values: T): T[number] | "UNKNOWN" | null {
  if (value === null || value === undefined || value === "") return null;
  const result = text(value);
  return values.includes(result) ? result as T[number] : "UNKNOWN";
}
