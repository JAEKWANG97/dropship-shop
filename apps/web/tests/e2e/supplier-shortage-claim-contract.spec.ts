import { expect, test } from "@playwright/test";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { SupplierShortageReportDetailView } from "../../src/app/supplier/(portal)/shortage-reports/[reportId]/page";
import { SupplierShortageReportListView } from "../../src/app/supplier/(portal)/shortage-reports/page";
import {
  buildFactInput,
  SupplierClaimTaskDetailView,
} from "../../src/app/supplier/(portal)/claim-tasks/[taskId]/page";
import { SupplierClaimTaskListView } from "../../src/app/supplier/(portal)/claim-tasks/page";
import { ShortageReportDetail } from "../../src/app/admin/shortage-reports/shortage-report-detail";
import { AdminSupplierClaimTaskDetail } from "../../src/app/admin/supplier-claim-tasks/task-detail";
import { ApiError } from "../../src/lib/api";
import { uncertainAdminCommandKey } from "../../src/lib/admin-payment";
import { supplierMutationHeaders } from "../../src/lib/supplier-mutation";
import {
  normalizeSupplierShipments,
  supplierShortageReportingAllowed,
  type SupplierCarrier,
} from "../../src/lib/supplier-orders";
import {
  adminSupplierClaimTaskCanClose,
  adminSupplierClaimTaskCloseCommand,
  adminSupplierClaimTaskCreateCommand,
  adminShortageReviewCommand,
  adminSupplierTaskDueAt,
  adminSupplierTaskCloseAllowed,
  CLAIM_TASK_INSTRUCTIONS,
  claimFactSummary,
  getSupplierClaimTask,
  getSupplierShortageReport,
  latestCorrectableFact,
  listSupplierClaimTasks,
  listSupplierShortageReports,
  normalizeAdminShortageReportList,
  normalizeAdminSupplierClaimTaskList,
  normalizeSupplierClaimTask,
  normalizeSupplierClaimTaskList,
  normalizeSupplierShortageReport,
  normalizeSupplierShortageReportList,
  reportSupplierShortage,
  submitSupplierClaimFact,
  supplierClaimTaskCanAnswer,
} from "../../src/lib/supplier-claims";

const REQUESTED_AT = "2026-08-30T00:00:00Z";
const DUE_AT = "2026-08-31T00:00:00Z";
const NOW = Date.parse("2026-08-30T12:00:00Z");

test("B-105 shortage projections preserve response order and remove non-allowlisted context", () => {
  const reports = normalizeSupplierShortageReportList({
    reports: [
      {
        reportId: "report-new",
        orderNumber: "ORD-NEW",
        reasonCode: "OUT_OF_STOCK",
        status: "REPORTED",
        reportedAt: "2026-08-30T02:00:00Z",
        nextAction: "WAIT",
        orderDetailAvailable: true,
        customerEmail: "must-not-render@example.com",
        recipient: { address1: "비공개 주소" },
        refund: { amount: 1000 },
      },
      {
        reportId: "report-old",
        orderNumber: "ORD-OLD",
        reasonCode: "OPTION_UNAVAILABLE",
        status: "REJECTED",
        reportedAt: "2026-08-30T01:00:00Z",
        reviewReasonCode: "INSUFFICIENT_EVIDENCE",
        nextAction: "CONTACT_COREABLE",
      },
    ],
  });

  expect(reports.map((report) => report.reportId)).toEqual(["report-new", "report-old"]);
  expect(JSON.stringify(reports)).not.toContain("orderDetailAvailable");
  expect(JSON.stringify(reports)).not.toContain("must-not-render");
  expect(JSON.stringify(reports)).not.toContain("비공개 주소");
  expect(JSON.stringify(reports)).not.toContain("refund");

  const unknown = normalizeSupplierShortageReport({
    reportId: "report-unknown",
    orderNumber: "ORD-UNKNOWN",
    reasonCode: "INTERNAL_REASON",
    status: "PROCESSING_INTERNAL",
    reviewReasonCode: "INTERNAL_REVIEW",
    nextAction: "SHIP_NOW",
  });
  expect(unknown).toMatchObject({
    reasonCode: "UNKNOWN",
    status: "UNKNOWN",
    reviewReasonCode: "UNKNOWN",
    nextAction: "UNKNOWN",
  });

  const admin = normalizeAdminShortageReportList({
    reports: [{
      reportId: "admin-report",
      orderId: "order-1",
      orderNumber: "ORD-1",
      supplierId: "supplier-1",
      supplierName: "공급처",
      status: "REPORTED",
      reasonCode: "OUT_OF_STOCK",
      nextAction: "WAIT",
      internalMemo: "must-not-survive",
    }],
  });
  expect(admin).toHaveLength(1);
  expect(JSON.stringify(admin)).not.toContain("internalMemo");
});

test("B-105 shortage submit authority requires the explicit shipment flag", () => {
  const omitted = normalizeSupplierShipments({ shipments: [], unallocatedItems: [] });
  const falseValue = normalizeSupplierShipments({ shipments: [], unallocatedItems: [], canReportShortage: false });
  const trueValue = normalizeSupplierShipments({ shipments: [], unallocatedItems: [], canReportShortage: true });
  const truthyValue = normalizeSupplierShipments({ shipments: [], unallocatedItems: [], canReportShortage: "true" });

  expect(omitted.canReportShortage).toBeNull();
  expect(supplierShortageReportingAllowed(omitted)).toBe(false);
  expect(supplierShortageReportingAllowed(falseValue)).toBe(false);
  expect(supplierShortageReportingAllowed(truthyValue)).toBe(false);
  expect(supplierShortageReportingAllowed(trueValue)).toBe(true);
});

test("B-105 task projection derives the four fixed instructions and fails closed", () => {
  expect(CLAIM_TASK_INSTRUCTIONS).toEqual({
    SHIPMENT_STOP_RESULT: {
      code: "CHECK_SHIPMENT_STOP",
      label: "상품 발송을 멈출 수 있는지 확인해 주세요.",
    },
    RETURN_INSTRUCTIONS: {
      code: "PROVIDE_RETURN_METHOD",
      label: "반품 수거 방법을 선택해 주세요.",
    },
    RETURN_RECEIVED: {
      code: "CONFIRM_RETURN_RECEIPT",
      label: "반품 상품 수령 여부를 확인해 주세요.",
    },
    INSPECTION_RESULT: {
      code: "INSPECT_RETURNED_ITEM",
      label: "반품 상품의 상태를 확인해 주세요.",
    },
  });

  const task = normalizeSupplierClaimTask({
    ...taskResponse(),
    instructions: "관리자 임의 문구 must-not-render",
    customerEmail: "must-not-render@example.com",
    claim: { customerMemo: "비공개" },
    adminMemo: "internal",
  });
  expect(task.instructions).toBe("상품 발송을 멈출 수 있는지 확인해 주세요.");
  expect(JSON.stringify(task)).not.toContain("must-not-render");
  expect(JSON.stringify(task)).not.toContain("비공개");
  expect(supplierClaimTaskCanAnswer(task, NOW)).toBe(true);

  const mismatched = normalizeSupplierClaimTask({
    ...taskResponse(),
    instructionCode: "PROVIDE_RETURN_METHOD",
    orderDetailAvailable: "true",
  });
  expect(mismatched.instructionCode).toBeNull();
  expect(mismatched.orderDetailAvailable).toBe(false);
  expect(mismatched.instructions).toBe("요청 내용을 확인할 수 없습니다.");
  expect(supplierClaimTaskCanAnswer(mismatched, NOW)).toBe(false);

  const unknown = normalizeSupplierClaimTask({
    ...taskResponse(),
    requestedType: "INTERNAL_WORK",
    status: "INTERNAL_STATUS",
  });
  expect(unknown.requestedType).toBe("UNKNOWN");
  expect(unknown.status).toBe("UNKNOWN");
  expect(supplierClaimTaskCanAnswer(unknown, NOW)).toBe(false);

  const tooLong = normalizeSupplierClaimTask({
    ...taskResponse(),
    dueAt: "2026-10-01T00:00:00Z",
  });
  expect(supplierClaimTaskCanAnswer(tooLong, NOW)).toBe(false);
});

test("B-105 task lists preserve backend ordering and admin linkage stays admin-only", () => {
  const supplier = normalizeSupplierClaimTaskList({
    tasks: [taskResponse({ taskId: "task-new", orderNumber: "ORD-NEW" }), taskResponse({ taskId: "task-old", orderNumber: "ORD-OLD" })],
  });
  expect(supplier.map((task) => task.taskId)).toEqual(["task-new", "task-old"]);

  const admin = normalizeAdminSupplierClaimTaskList({
    tasks: [taskResponse({
      taskId: "task-admin",
      claimId: "claim-1",
      orderId: "order-1",
      supplierId: "supplier-1",
      supplierName: "공급처",
      requestedByAdminId: "admin-1",
      closedByAdminId: null,
    })],
  });
  expect(admin[0]).toMatchObject({ claimId: "claim-1", orderId: "order-1", supplierId: "supplier-1" });
  expect("claimId" in supplier[0]).toBe(false);
  expect("requestedByAdminId" in supplier[0]).toBe(false);
});

test("B-105 correction targets only a valid root-to-current-head chain", () => {
  const answered = normalizeSupplierClaimTask(taskResponse({
    status: "ANSWERED",
    answeredAt: "2026-08-30T01:00:00Z",
    facts: [
      factResponse("fact-root", null, "STOPPED"),
      factResponse("fact-head", "fact-root", "ALREADY_SHIPPED"),
    ],
  }));
  expect(latestCorrectableFact(answered)?.factId).toBe("fact-head");
  expect(supplierClaimTaskCanAnswer(answered, NOW)).toBe(true);
  expect(claimFactSummary(answered.facts[1])).toMatchObject({ result: "이미 출고됨" });

  const branched = normalizeSupplierClaimTask(taskResponse({
    status: "ANSWERED",
    facts: [
      factResponse("fact-root", null, "STOPPED"),
      factResponse("fact-head", "another-fact", "ALREADY_SHIPPED"),
    ],
  }));
  expect(latestCorrectableFact(branched)).toBeNull();
  expect(supplierClaimTaskCanAnswer(branched, NOW)).toBe(false);

  const invalidLatest = normalizeSupplierClaimTask(taskResponse({
    status: "ANSWERED",
    facts: [factResponse("fact-root", null, "STOPPED"), {
      factId: "fact-unknown",
      type: "INTERNAL_TYPE",
      payload: { resultCode: "STOPPED", checkedAt: "2026-08-30T01:00:00Z" },
      correctsFactId: "fact-root",
    }],
  }));
  expect(latestCorrectableFact(invalidLatest)).toBeNull();
});

test("B-105 fact input enforces requestedAt-to-now timestamps and current head correction", () => {
  const open = normalizeSupplierClaimTask(taskResponse());
  expect(buildFactInput(open, form({
    resultCode: "STOPPED",
    checkedAt: "2026-08-30T01:00:00Z",
  }), NOW)).toEqual({
    type: "SHIPMENT_STOP_RESULT",
    payload: { resultCode: "STOPPED", checkedAt: "2026-08-30T01:00:00.000Z" },
    correctsFactId: null,
  });
  expect(buildFactInput(open, form({ resultCode: "STOPPED", checkedAt: "2026-08-29T23:59:59Z" }), NOW)).toBeNull();
  expect(buildFactInput(open, form({ resultCode: "STOPPED", checkedAt: "2026-08-30T12:00:01Z" }), NOW)).toBeNull();
  expect(buildFactInput(open, form({ resultCode: "FORGED", checkedAt: "2026-08-30T01:00:00Z" }), NOW)).toBeNull();

  const answered = normalizeSupplierClaimTask(taskResponse({
    status: "ANSWERED",
    facts: [factResponse("fact-current", null, "STOPPED")],
  }));
  expect(buildFactInput(answered, form({
    resultCode: "ALREADY_SHIPPED",
    checkedAt: "2026-08-30T02:00:00Z",
  }), NOW)).toMatchObject({ correctsFactId: "fact-current" });

  const returnTask = normalizeSupplierClaimTask(taskResponse({
    requestedType: "RETURN_INSTRUCTIONS",
    instructionCode: "PROVIDE_RETURN_METHOD",
  }));
  expect(buildFactInput(returnTask, form({ methodCode: "COURIER_PICKUP", carrierCode: "FORGED" }), NOW, ["CJ_LOGISTICS"]))
    .toBeNull();
  expect(buildFactInput(returnTask, form({ methodCode: "COURIER_PICKUP", carrierCode: "CJ_LOGISTICS" }), NOW, ["CJ_LOGISTICS"]))
    .toEqual({
      type: "RETURN_INSTRUCTIONS",
      payload: { methodCode: "COURIER_PICKUP", carrierCode: "CJ_LOGISTICS" },
      correctsFactId: null,
    });
});

test("B-105 due time is future and capped at 30 days in Korean local time", () => {
  const now = Date.parse("2026-08-30T00:00:00Z");
  expect(adminSupplierTaskDueAt("2026-08-31T09:00", now)).toBe("2026-08-31T00:00:00.000Z");
  expect(adminSupplierTaskDueAt("2026-08-30T08:59", now)).toBeNull();
  expect(adminSupplierTaskDueAt("2026-09-29T09:01", now)).toBeNull();
  expect(adminSupplierTaskDueAt("not-a-date", now)).toBeNull();
});

test("B-105 admin close permits all three reasons for OPEN and ANSWERED only", () => {
  for (const status of ["OPEN", "ANSWERED"]) {
    expect(adminSupplierTaskCloseAllowed(status, "RESPONSE_ACCEPTED")).toBe(true);
    expect(adminSupplierTaskCloseAllowed(status, "SUPERSEDED")).toBe(true);
    expect(adminSupplierTaskCloseAllowed(status, "NO_LONGER_NEEDED")).toBe(true);
  }
  expect(adminSupplierTaskCloseAllowed("CLOSED", "RESPONSE_ACCEPTED")).toBe(false);
  expect(adminSupplierTaskCloseAllowed("UNKNOWN", "SUPERSEDED")).toBe(false);
  expect(adminSupplierTaskCloseAllowed("OPEN", "DUE_AT_EXPIRED")).toBe(false);
});

test("B-105 admin mutation contracts use exact bodies, Origin and retry keys", () => {
  expect(adminSupplierClaimTaskCreateCommand("RETURN_RECEIVED", "2026-08-31T00:00:00Z")).toEqual({
    requestedType: "RETURN_RECEIVED",
    instructionCode: "CONFIRM_RETURN_RECEIPT",
    dueAt: "2026-08-31T00:00:00Z",
  });
  expect(adminSupplierClaimTaskCreateCommand("INTERNAL_TYPE", "2026-08-31T00:00:00Z")).toBeNull();
  expect(adminSupplierClaimTaskCloseCommand("ANSWERED", "RESPONSE_ACCEPTED")).toEqual({
    expectedStatus: "ANSWERED",
    closeReasonCode: "RESPONSE_ACCEPTED",
  });
  expect(adminSupplierClaimTaskCloseCommand("OPEN", "CLAIM_TERMINAL")).toBeNull();
  expect(adminShortageReviewCommand("approve", "REPORTED", "SHORTAGE_CONFIRMED")).toEqual({
    expectedStatus: "REPORTED",
    reviewReasonCode: "SHORTAGE_CONFIRMED",
  });
  expect(adminShortageReviewCommand("reject", "REPORTED", "FULFILLMENT_CAN_CONTINUE")).toEqual({
    expectedStatus: "REPORTED",
    reviewReasonCode: "FULFILLMENT_CAN_CONTINUE",
  });
  expect(adminShortageReviewCommand("approve", "REPORTED", "INSUFFICIENT_EVIDENCE")).toBeNull();
  expect(supplierMutationHeaders("b105-key", "https://shop.example/admin/path")).toEqual({
    Origin: "https://shop.example",
    "Idempotency-Key": "b105-key",
  });
  expect(uncertainAdminCommandKey(new Error("network"), "b105-key")).toBe("b105-key");
  expect(uncertainAdminCommandKey(new ApiError(409, "conflict"), "b105-key")).toBeUndefined();
});

test("B-105 supplier requests use exact filters, encoded paths, no-store and idempotency", async () => {
  const originalFetch = globalThis.fetch;
  const calls: Array<{ path: string; init?: RequestInit }> = [];
  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input);
    calls.push({ path, init });
    const body = path.includes("shortage-reports")
      ? path.endsWith("/facts") ? taskResponse() : path.includes("/claim-tasks") ? taskResponse() : shortageResponse()
      : path.includes("claim-tasks") ? (path.includes("?") ? { tasks: [taskResponse()] } : taskResponse())
        : path.includes("?status=") ? { reports: [shortageResponse()] } : shortageResponse();
    return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
  }) as typeof fetch;

  try {
    await listSupplierShortageReports({ status: "REPORTED" });
    await getSupplierShortageReport("report/1");
    await reportSupplierShortage("ORD 105/1", "OUT_OF_STOCK", "shortage-key");
    await listSupplierClaimTasks({ status: "OPEN" });
    await getSupplierClaimTask("task/1");
    await submitSupplierClaimFact("task/1", {
      type: "SHIPMENT_STOP_RESULT",
      payload: { resultCode: "STOPPED", checkedAt: "2026-08-30T01:00:00Z" },
      correctsFactId: null,
    }, "fact-key");
  } finally {
    globalThis.fetch = originalFetch;
  }

  expect(calls.map((call) => call.path)).toEqual([
    "/api/supplier/shortage-reports?status=REPORTED",
    "/api/supplier/shortage-reports/report%2F1",
    "/api/supplier/orders/ORD%20105%2F1/shortage-reports",
    "/api/supplier/claim-tasks?status=OPEN",
    "/api/supplier/claim-tasks/task%2F1",
    "/api/supplier/claim-tasks/task%2F1/facts",
  ]);
  expect(JSON.parse(String(calls[2].init?.body))).toEqual({ reasonCode: "OUT_OF_STOCK" });
  expect(new Headers(calls[2].init?.headers).get("Idempotency-Key")).toBe("shortage-key");
  expect(new Headers(calls[5].init?.headers).get("Idempotency-Key")).toBe("fact-key");
  expect(JSON.parse(String(calls[5].init?.body))).toEqual({
    type: "SHIPMENT_STOP_RESULT",
    payload: { resultCode: "STOPPED", checkedAt: "2026-08-30T01:00:00Z" },
    correctsFactId: null,
  });
  for (const call of calls) {
    expect(call.init?.cache).toBe("no-store");
    expect(call.init?.credentials).toBe("same-origin");
  }
});

test("B-105 supplier views expose no shortage order link and gate task links/actions", async ({ page }) => {
  const shortage = normalizeSupplierShortageReport({
    ...shortageResponse(),
    orderDetailAvailable: true,
    customerEmail: "must-not-render@example.com",
  });
  await page.setContent(renderToStaticMarkup(createElement(SupplierShortageReportDetailView, { report: shortage })));
  await expect(page.getByRole("heading", { name: "ORD-105" })).toBeVisible();
  await expect(page.locator('a[href^="/supplier/orders/"]')).toHaveCount(0);
  await expect(page.locator("body")).not.toContainText("must-not-render@example.com");

  const hiddenLinkTask = normalizeSupplierClaimTask({ ...taskResponse(), orderDetailAvailable: false });
  await page.setContent(renderToStaticMarkup(createElement(SupplierClaimTaskDetailView, {
    carriers: [] as SupplierCarrier[],
    initialTask: hiddenLinkTask,
    loadedAt: NOW,
  })));
  await expect(page.locator('a[href^="/supplier/orders/"]')).toHaveCount(0);
  await expect(page.getByRole("button", { name: "답변 저장" })).toBeVisible();
  await expect(page.locator('input[name="checkedAt"]')).toHaveAttribute("step", "1");
  const expectedLocalNow = new Date(NOW - new Date(NOW).getTimezoneOffset() * 60_000)
    .toISOString()
    .slice(0, 19);
  await expect(page.locator('input[name="checkedAt"]')).toHaveAttribute("value", expectedLocalNow);

  const linkedTask = normalizeSupplierClaimTask({ ...taskResponse(), orderDetailAvailable: true });
  await page.setContent(renderToStaticMarkup(createElement(SupplierClaimTaskDetailView, {
    carriers: [],
    initialTask: linkedTask,
    loadedAt: NOW,
  })));
  await expect(page.locator('a[href="/supplier/orders/ORD-105"]')).toHaveCount(1);

  const unknownTask = normalizeSupplierClaimTask({ ...taskResponse(), requestedType: "INTERNAL_TASK" });
  await page.setContent(renderToStaticMarkup(createElement(SupplierClaimTaskDetailView, {
    carriers: [],
    initialTask: unknownTask,
    loadedAt: NOW,
  })));
  await expect(page.locator("form")).toHaveCount(0);
  await expect(page.getByText("알 수 없는 요청에는 답변하지 않습니다", { exact: false })).toBeVisible();

  await page.setContent(renderToStaticMarkup(createElement(SupplierShortageReportListView, {
    error: "",
    loading: false,
    reports: [shortage],
  })));
  await expect(page.getByRole("link", { name: /ORD-105/ })).toHaveCount(1);

  await page.setContent(renderToStaticMarkup(createElement(SupplierClaimTaskListView, {
    error: "",
    loading: false,
    status: "",
    tasks: [linkedTask],
  })));
  await expect(page.getByRole("link", { name: /출고 중단 확인/ })).toHaveCount(1);
});

test("B-105 admin master-detail views expose linkage and fail closed on unknown contracts", async ({ page }) => {
  const openTask = normalizeAdminSupplierClaimTaskList({
    tasks: [taskResponse({
      taskId: "task-admin-open",
      claimId: "claim-admin",
      orderId: "order-admin",
      supplierId: "supplier-admin",
      supplierName: "안전 공급처",
      status: "OPEN",
    })],
  })[0];
  await page.setContent(renderToStaticMarkup(createElement(AdminSupplierClaimTaskDetail, {
    closeAction: "#",
    retry: {},
    task: openTask,
  })));
  await expect(page.locator('a[href="/admin/orders?orderId=order-admin"]')).toHaveCount(1);
  await expect(page.locator('input[name="expectedStatus"]')).toHaveValue("OPEN");
  await expect(page.locator('input[name="returnTo"]')).toHaveValue("queue");
  await expect(page.locator('select[name="closeReasonCode"] option')).toHaveCount(3);

  const reported = normalizeAdminShortageReportList({
    reports: [{
      ...shortageResponse(),
      reportId: "report-admin-open",
      orderId: "order-admin",
      supplierId: "supplier-admin",
      supplierName: "안전 공급처",
    }],
  })[0];
  await page.setContent(renderToStaticMarkup(createElement(ShortageReportDetail, {
    approveAction: "#",
    report: reported,
    rejectAction: "#",
    retry: {},
  })));
  await expect(page.locator("form")).toHaveCount(2);
  await expect(page.locator('input[name="expectedStatus"]')).toHaveCount(2);
  expect(await page.locator('input[name="expectedStatus"]').evaluateAll((inputs) => inputs.map((input) => (input as HTMLInputElement).value)))
    .toEqual(["REPORTED", "REPORTED"]);
  await expect(page.locator('select[name="reviewReasonCode"] option')).toHaveCount(2);

  const unknownTask = normalizeAdminSupplierClaimTaskList({
    tasks: [taskResponse({
      taskId: "task-admin-unknown",
      claimId: "claim-admin",
      orderId: "order-admin",
      supplierId: "supplier-admin",
      requestedType: "INTERNAL_TYPE",
      status: "INTERNAL_STATUS",
    })],
  })[0];
  await page.setContent(renderToStaticMarkup(createElement(AdminSupplierClaimTaskDetail, {
    closeAction: "#",
    retry: {},
    task: unknownTask,
  })));
  await expect(page.locator("form")).toHaveCount(0);
  await expect(page.getByText("작업 계약을 확인할 수 없습니다", { exact: true })).toBeVisible();

  const unknownOpenTask = normalizeAdminSupplierClaimTaskList({
    tasks: [taskResponse({
      taskId: "task-admin-unknown-open",
      claimId: "claim-admin",
      orderId: "order-admin",
      supplierId: "supplier-admin",
      requestedType: "INTERNAL_TYPE",
      status: "OPEN",
    })],
  })[0];
  expect(adminSupplierClaimTaskCanClose(unknownOpenTask)).toBe(false);

  const unknownReport = normalizeAdminShortageReportList({
    reports: [{
      reportId: "report-admin-unknown",
      orderId: "order-admin",
      orderNumber: "ORD-ADMIN",
      supplierId: "supplier-admin",
      status: "INTERNAL_STATUS",
      reasonCode: "INTERNAL_REASON",
      nextAction: "INTERNAL_ACTION",
    }],
  })[0];
  await page.setContent(renderToStaticMarkup(createElement(ShortageReportDetail, {
    approveAction: "#",
    report: unknownReport,
    rejectAction: "#",
    retry: {},
  })));
  await expect(page.locator("form")).toHaveCount(0);
  await expect(page.getByText("보고 계약을 확인할 수 없습니다", { exact: true })).toBeVisible();

  const unknownReason = normalizeAdminShortageReportList({
    reports: [{
      ...shortageResponse(),
      reportId: "report-admin-unknown-reason",
      orderId: "order-admin",
      supplierId: "supplier-admin",
      reasonCode: "FUTURE_REASON",
    }],
  })[0];
  await page.setContent(renderToStaticMarkup(createElement(ShortageReportDetail, {
    approveAction: "#",
    report: unknownReason,
    rejectAction: "#",
    retry: {},
  })));
  await expect(page.locator("form")).toHaveCount(0);
  await expect(page.getByText("보고 계약을 확인할 수 없습니다", { exact: true })).toBeVisible();
});

function shortageResponse() {
  return {
    reportId: "report-105",
    orderNumber: "ORD-105",
    reasonCode: "OUT_OF_STOCK",
    status: "REPORTED",
    reportedAt: "2026-08-30T00:00:00Z",
    reviewedAt: null,
    reviewReasonCode: null,
    nextAction: "WAIT",
  };
}

function taskResponse(overrides: Record<string, unknown> = {}) {
  return {
    taskId: "task-105",
    orderNumber: "ORD-105",
    orderDetailAvailable: false,
    items: [{ productName: "안전모", optionName: "흰색", quantity: 1 }],
    requestedType: "SHIPMENT_STOP_RESULT",
    status: "OPEN",
    instructionCode: "CHECK_SHIPMENT_STOP",
    instructions: "상품 발송을 멈출 수 있는지 확인해 주세요.",
    requestedAt: REQUESTED_AT,
    dueAt: DUE_AT,
    answeredAt: null,
    closedAt: null,
    closeReasonCode: null,
    facts: [],
    ...overrides,
  };
}

function factResponse(factId: string, correctsFactId: string | null, resultCode: string) {
  return {
    factId,
    type: "SHIPMENT_STOP_RESULT",
    payload: { resultCode, checkedAt: "2026-08-30T01:00:00Z" },
    correctsFactId,
    createdAt: "2026-08-30T01:01:00Z",
  };
}

function form(fields: Record<string, string>) {
  const data = new FormData();
  for (const [name, value] of Object.entries(fields)) data.set(name, value);
  return data;
}
