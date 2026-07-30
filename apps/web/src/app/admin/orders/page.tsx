import Link from "next/link";
import {
  adminStatusLabel,
  getAdminOrder,
  getAdminOrderActions,
  getAdminOrders,
  type AdminOrder,
  type AdminOrderActionHistory,
} from "@/lib/admin";
import { formatPrice } from "@/lib/catalog";
import {
  claimReasonLabel,
  claimStatusLabel,
  claimTypeLabel,
  fulfillmentStatusLabel,
  paymentGroupStatusLabel,
  paymentStatusLabel,
  refundStatusLabel,
  shipmentStatusLabel,
} from "@/lib/orders";
import {
  cancelUnpaidDeposit,
  cancelSupplierPurchase,
  completeManualRefund,
  completeSupplierOrder,
  confirmDeposit,
  correctShipmentDelivered,
  createOrderShipment,
  markOrderOutOfStock,
  recordDepositMismatch,
  recordReturnReceived,
  reconcileSupplierPurchase,
  rejectClaim,
  startSupplierWork,
  startReturnRefund,
  syncShipmentTracking,
  retrySupplierPurchase,
  validateSupplierPurchase,
} from "./actions";

type AdminOrdersPageProps = {
  searchParams: Promise<{ from?: string; message?: string; orderId?: string; q?: string; status?: string; to?: string }>;
};

export default async function AdminOrdersPage({ searchParams }: AdminOrdersPageProps) {
  const params = await searchParams;
  const data = await loadOrders(params.status);
  const orders = data.orders;
  const keyword = params.q?.trim().toLowerCase();
  const fromTime = params.from ? new Date(params.from).getTime() : undefined;
  const toTime = params.to ? new Date(`${params.to}T23:59:59`).getTime() : undefined;
  const filteredOrders = orders.filter((order) => {
    const createdAt = new Date(order.createdAt).getTime();
    const matchesKeyword =
      !keyword || `${order.orderNumber} ${order.customerEmail}`.toLowerCase().includes(keyword);
    const matchesStatus = !params.status || order.status === params.status;
    const matchesFrom = fromTime === undefined || createdAt >= fromTime;
    const matchesTo = toTime === undefined || createdAt <= toTime;

    return matchesKeyword && matchesStatus && matchesFrom && matchesTo;
  });
  const selectedOrderId = params.orderId ?? filteredOrders[0]?.orderId;
  const selectedSummary = filteredOrders.find((order) => order.orderId === selectedOrderId) ?? filteredOrders[0];
  const [detail, actionHistory] = selectedOrderId
    ? await Promise.all([loadOrderDetail(selectedOrderId), loadOrderActions(selectedOrderId)])
    : [{ error: false as const, order: null }, { error: false as const, actions: [] }];
  const selectedOrder = mergeOrderDetail(selectedSummary, detail.order);

  return (
    <div className={`admin-page${params.orderId ? " has-selected-order" : ""}`}>
      <div className="admin-heading">
        <div>
          <h1>주문 관리</h1>
          <p>고객 주문 내역과 결제, 배송 상태를 확인하고 처리하세요.</p>
        </div>
      </div>

      {params.message ? (
        <div className="notice">
          <strong>알림</strong>
          <span>{params.message}</span>
        </div>
      ) : null}

      {data.error ? (
        <div className="notice">
          <strong>주문 데이터를 불러오지 못했습니다</strong>
          <span>권한, API 서버, 네트워크 상태를 확인한 뒤 다시 시도하세요.</span>
        </div>
      ) : null}

      {!data.error ? (
        <form action="/admin/orders" className="admin-filters">
          <input type="date" name="from" defaultValue={params.from ?? ""} aria-label="시작일" />
          <input type="date" name="to" defaultValue={params.to ?? ""} aria-label="종료일" />
          <select name="status" defaultValue={params.status ?? ""}>
            <option value="">발주대기 기본</option>
            <option value="PAYMENT_PENDING">입금대기</option>
            <option value="SUPPLIER_ORDER_PENDING">발주대기</option>
            <option value="SUPPLIER_ORDERED">발주완료</option>
            <option value="SHIPPED">배송중</option>
            <option value="REFUND_REQUESTED">환불요청</option>
            <option value="REFUNDED">환불완료</option>
            <option value="OUT_OF_STOCK">품절</option>
          </select>
          <input name="q" placeholder="주문번호 또는 고객사 검색" defaultValue={params.q ?? ""} />
          <button className="button" type="submit">
            검색
          </button>
        </form>
      ) : null}

      {!data.error ? (
        <div className="admin-metrics">
          <Metric label="입금대기" value={filteredOrders.filter((order) => order.status === "PAYMENT_PENDING").length} />
          <Metric label="발주대기" value={filteredOrders.filter((order) => order.status === "SUPPLIER_ORDER_PENDING").length} />
          <Metric label="배송중" value={filteredOrders.filter((order) => order.status === "SHIPPED").length} />
          <Metric label="취소/환불" value={filteredOrders.filter((order) => order.status.includes("REFUND")).length} />
          <Metric label="품절" value={filteredOrders.filter((order) => order.status === "OUT_OF_STOCK").length} />
        </div>
      ) : null}

      {!data.error ? (
        <div className="admin-orders-layout">
          <section className="admin-panel">
            <div className="admin-panel-head">
              <h2>주문 목록</h2>
              <span>총 {filteredOrders.length}건</span>
            </div>
            <div className="admin-table orders">
              <div className="admin-table-row admin-table-head">
                <span>주문번호</span>
                <span>고객사</span>
                <span>상품수</span>
                <span>결제금액</span>
                <span>주문상태</span>
              </div>
              {filteredOrders.map((order) => (
                <Link
                  className={`admin-table-row ${order.orderId === selectedOrder?.orderId ? "selected" : ""}`}
                  href={orderHref(order.orderId, params)}
                  key={order.orderId}
                >
                  <strong>{order.orderNumber}</strong>
                  <span>{order.customerEmail}</span>
                  <span>{order.itemCount}개</span>
                  <span>{formatPrice(order.totalAmount)}</span>
                  <span className={`admin-badge ${order.status.toLowerCase()}`}>
                    {adminStatusLabel(order.status)}
                  </span>
                </Link>
              ))}
              {filteredOrders.length === 0 ? (
                <div className="admin-empty">
                  <strong>조회된 주문이 없습니다</strong>
                  <span>검색 조건을 바꾸거나 새 주문이 들어온 뒤 다시 확인하세요.</span>
                </div>
              ) : null}
            </div>
          </section>

          {selectedOrder ? (
            <aside className="admin-panel admin-order-detail">
              <Link className="admin-order-back-link" href={orderListHref(params)}>
                주문 목록으로
              </Link>
              <div className="admin-panel-head">
                <h2>주문 상세</h2>
                <span className={`admin-badge ${selectedOrder.status.toLowerCase()}`}>
                  {adminStatusLabel(selectedOrder.status)}
                </span>
              </div>
              <strong>{selectedOrder.orderNumber}</strong>
              <span>{new Date(selectedOrder.createdAt).toLocaleString("ko-KR")}</span>
              {detail.error ? (
                <div className="notice">
                  <strong>상세 데이터를 불러오지 못했습니다</strong>
                  <span>주문 목록 정보만 표시합니다.</span>
                </div>
              ) : null}
              <h3>주문 상품</h3>
              <div className="admin-list">
                {(selectedOrder.items ?? []).map((item) => (
                  <div key={`${item.productName}-${item.optionName}`}>
                    <strong>{item.productName}</strong>
                    <span>
                      {item.optionName} / {item.quantity}개
                    </span>
                    <span>{formatPrice(item.unitPrice * item.quantity)}</span>
                  </div>
                ))}
              </div>
              <h3>배송 정보</h3>
              <p>{shippingAddressText(selectedOrder.shippingAddress)}</p>
              <ShipmentPanel order={selectedOrder} />
              <h3>결제 정보</h3>
              <div className="summary-list compact">
                <div>
                  <span>입금/결제상태</span>
                  <strong>{adminPaymentLabel(selectedOrder)}</strong>
                </div>
                <div>
                  <span>주문금액</span>
                  <strong>{formatPrice(selectedOrder.totalAmount)}</strong>
                </div>
              </div>
              <BankTransferAdminPanel order={selectedOrder} />
              <SupplierPurchasePanel order={selectedOrder} />
              <h3>운영 상태</h3>
              <div className="summary-list compact">
                <div>
                  <span>발주</span>
                  <strong>{selectedOrder.fulfillment ? fulfillmentStatusLabel(selectedOrder.fulfillment.status) : "없음"}</strong>
                </div>
                <div>
                  <span>배송</span>
                  <strong>{selectedOrder.shipment ? shipmentStatusLabel(selectedOrder.shipment.status) : "없음"}</strong>
                </div>
                <div>
                  <span>환불</span>
                  <strong>{selectedOrder.refund ? refundStatusLabel(selectedOrder.refund.status) : "없음"}</strong>
                </div>
                <div>
                  <span>클레임</span>
                  <strong>{selectedOrder.claim ? claimStatusLabel(selectedOrder.claim.status) : "없음"}</strong>
                </div>
              </div>
              <ClaimPanel order={selectedOrder} />
					<RefundEvidencePanel order={selectedOrder} />
					<AdminActionHistoryPanel actions={actionHistory.actions} error={actionHistory.error} />
              <AdminOrderActions order={selectedOrder} />
            </aside>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

async function loadOrders(status?: string) {
  try {
    return { error: false as const, orders: await getAdminOrders(status || undefined) };
  } catch {
    return { error: true as const, orders: [] };
  }
}

async function loadOrderDetail(orderId: string) {
  try {
    return { error: false as const, order: await getAdminOrder(orderId) };
  } catch {
    return { error: true as const, order: null };
  }
}

async function loadOrderActions(orderId: string) {
  try {
    return { error: false as const, actions: await getAdminOrderActions(orderId) };
  } catch {
    return { error: true as const, actions: [] as AdminOrderActionHistory[] };
  }
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <article className="admin-metric">
      <span>{label}</span>
      <strong>{value}건</strong>
      <small>상태 기준 집계</small>
    </article>
  );
}

function mergeOrderDetail(summary?: AdminOrder, detail?: AdminOrder | null) {
  if (!detail) {
    return summary;
  }
  return {
    ...summary,
    ...detail,
    supplierName: detail.supplierName ?? detail.supplier?.name ?? summary?.supplierName ?? "",
    customerEmail: detail.customerEmail ?? detail.customer?.email ?? summary?.customerEmail ?? "",
    checkoutNumber: detail.checkoutNumber ?? detail.paymentGroup?.checkoutNumber ?? summary?.checkoutNumber ?? "",
    totalAmount: detail.totalAmount ?? detail.paymentGroup?.totalAmount ?? summary?.totalAmount ?? 0,
  };
}

function orderHref(
  orderId: string,
  params: { from?: string; q?: string; status?: string; to?: string },
) {
  const search = new URLSearchParams({ orderId });
  for (const key of ["from", "q", "status", "to"] as const) {
    if (params[key]) {
      search.set(key, params[key]);
    }
  }
  return `/admin/orders?${search.toString()}`;
}

function orderListHref(params: { from?: string; q?: string; status?: string; to?: string }) {
  const search = new URLSearchParams();
  for (const key of ["from", "q", "status", "to"] as const) {
    if (params[key]) {
      search.set(key, params[key]);
    }
  }
  return `/admin/orders${search.size ? `?${search.toString()}` : ""}`;
}

function shippingAddressText(address: AdminOrder["shippingAddress"]) {
  if (!address) {
    return "배송지 상세는 주문 상세 API에서 확인합니다.";
  }
  if (typeof address === "string") {
    return address;
  }
  return `${address.recipientName} / ${address.recipientPhone} / ${address.postalCode} ${address.address1} ${address.address2 ?? ""}`;
}

function adminPaymentLabel(order: AdminOrder) {
  if (order.payment?.status) {
    return paymentStatusLabel(order.payment.status);
  }
  if (order.paymentGroup?.status) {
    return paymentGroupStatusLabel(order.paymentGroup.status);
  }
  return "확인 필요";
}

function BankTransferAdminPanel({ order }: { order: AdminOrder }) {
  const deposit = order.paymentGroup?.bankTransferDeposit;
  if (!deposit) {
    return null;
  }

  return (
    <div className="summary-list compact">
      <SummaryItem label="입금 계좌" value={`${deposit.bankName ?? "-"} ${deposit.accountNumber ?? ""}`} />
      <SummaryItem label="예금주" value={deposit.accountHolder ?? "-"} />
      <SummaryItem label="입금자명" value={deposit.depositorName ?? "-"} />
		<SummaryItem label="실제 입금자명" value={deposit.actualDepositorName ?? "-"} />
		<SummaryItem label="실제 입금액" value={deposit.actualDepositAmount == null ? "-" : formatPrice(deposit.actualDepositAmount)} />
		<SummaryItem label="입금시각" value={formatDateTime(deposit.depositReceivedAt)} />
		<SummaryItem label="거래 식별 메모" value={deposit.depositTransactionReference ?? "-"} />
      <SummaryItem label="입금확인" value={formatDateTime(deposit.depositConfirmedAt)} />
      <SummaryItem label="입금확인 사유" value={deposit.depositConfirmationReason ?? "-"} />
      <SummaryItem label="불일치 메모" value={deposit.depositMismatchMemo ?? "-"} />
      <SummaryItem label="미입금 취소" value={formatDateTime(deposit.unpaidCancelledAt)} />
    </div>
  );
}

function RefundEvidencePanel({ order }: { order: AdminOrder }) {
  const refund = order.refund;
  if (!refund?.manualRefundedAt) {
    return null;
  }

  return (
    <section className="admin-claim-panel">
      <h3>환불 이체 증적</h3>
      <div className="summary-list compact">
        <SummaryItem label="환불 은행" value={refund.manualRefundBankName ?? "-"} />
        <SummaryItem label="환불 계좌번호" value={refund.manualRefundAccountNumber ?? "-"} />
        <SummaryItem label="예금주" value={refund.manualRefundAccountHolder ?? "-"} />
        <SummaryItem label="실제 이체시각" value={formatDateTime(refund.manualRefundTransferredAt ?? null)} />
        <SummaryItem label="거래 식별 메모" value={refund.manualRefundTransactionReference ?? "-"} />
        <SummaryItem label="처리 사유" value={refund.manualRefundReason ?? "-"} />
      </div>
    </section>
  );
}

function AdminActionHistoryPanel({ actions, error }: { actions: AdminOrderActionHistory[]; error: boolean }) {
  return (
    <section className="admin-claim-panel">
      <h3>작업 이력</h3>
      {error ? (
        <div className="admin-empty compact">
          <strong>작업 이력을 불러오지 못했습니다</strong>
        </div>
      ) : actions.length === 0 ? (
        <div className="admin-empty compact">
          <strong>기록된 작업 이력이 없습니다</strong>
        </div>
      ) : (
        <div className="admin-list">
          {actions.map((action) => (
            <div key={action.actionHistoryId}>
              <strong>{adminActionLabel(action.actionType)}</strong>
              <span>{formatDateTime(action.createdAt)}</span>
              <span>{action.reason}</span>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function adminActionLabel(actionType: string) {
  return (
    {
      BANK_TRANSFER_DEPOSIT_CONFIRMED: "입금 확인",
      BANK_TRANSFER_DEPOSIT_MISMATCH_RECORDED: "입금 불일치 기록",
      BANK_TRANSFER_UNPAID_CANCELLED: "미입금 취소",
      MANUAL_REFUND_COMPLETED: "수동 환불 완료",
    }[actionType] ?? actionType
  );
}

function ShipmentPanel({ order }: { order: AdminOrder }) {
  const shipment = order.shipment;
  if (!shipment) {
    return (
      <div className="admin-empty compact">
        <strong>등록된 송장이 없습니다</strong>
        <span>공급처 발주 완료 후 송장을 입력하면 배송조회 상태를 관리할 수 있습니다.</span>
      </div>
    );
  }

  return (
    <div className="admin-shipment-panel">
      {shipment.trackingSyncFailureReason ? (
        <div className="notice danger">
          <strong>배송조회 실패</strong>
          <span>{shipment.trackingSyncFailureReason}</span>
        </div>
      ) : null}
      <div className="summary-list compact">
        <SummaryItem label="배송상태" value={shipmentStatusLabel(shipment.status)} />
        <SummaryItem label="택배사" value={shipment.carrier} />
        <SummaryItem label="송장번호" value={shipment.trackingNumber} />
        <SummaryItem label="출고시각" value={formatDateTime(shipment.shippedAt)} />
        <SummaryItem label="배송완료시각" value={formatDateTime(shipment.deliveredAt)} />
        <SummaryItem label="마지막 조회" value={formatDateTime(shipment.trackingSyncedAt)} />
        <SummaryItem label="수동 보정" value={shipment.manualOverride ? "적용됨" : "없음"} />
        <SummaryItem label="보정 사유" value={shipment.manualCorrectionReason ?? "-"} />
      </div>
      {order.status === "SHIPPED" ? (
        <div className="admin-order-actions">
        <form action={syncShipmentTracking} className="admin-inline-form">
          <input name="orderId" type="hidden" value={order.orderId} />
          <input name="shipmentId" type="hidden" value={shipment.shipmentId} />
          <label>
            배송조회 상태
            <select name="trackingStatus" required defaultValue="IN_TRANSIT">
              <option value="IN_TRANSIT">배송 중</option>
              <option value="DELIVERED">배송 완료</option>
            </select>
          </label>
          <button className="button" type="submit">
            조회 결과 반영
          </button>
        </form>

        <form action={syncShipmentTracking} className="admin-inline-form">
          <input name="orderId" type="hidden" value={order.orderId} />
          <input name="shipmentId" type="hidden" value={shipment.shipmentId} />
          <label className="wide">
            배송조회 실패 사유
            <input name="failureReason" required placeholder="예: 택배사 조회 지연" />
          </label>
          <button className="button" type="submit">
            실패 사유 기록
          </button>
        </form>

        <form action={correctShipmentDelivered} className="admin-inline-form">
          <input name="orderId" type="hidden" value={order.orderId} />
          <input name="shipmentId" type="hidden" value={shipment.shipmentId} />
          <label className="wide">
            수동 배송완료 사유
            <input name="reason" required placeholder="예: 택배사 사이트에서 배송완료 확인" />
          </label>
          <button className="button" type="submit">
            수동 배송완료
          </button>
        </form>
        </div>
      ) : null}
    </div>
  );
}

function ClaimPanel({ order }: { order: AdminOrder }) {
  const claim = order.claim;
  if (!claim) {
    return null;
  }
  const evidenceFiles = claim.evidenceFiles ?? [];

  return (
    <section className="admin-claim-panel">
      <h3>클레임</h3>
      <div className="summary-list compact">
        <SummaryItem label="유형" value={claimTypeLabel(claim.claimType)} />
        <SummaryItem label="사유" value={claimReasonLabel(claim.claimReason)} />
        <SummaryItem label="상태" value={claimStatusLabel(claim.status)} />
        <SummaryItem label="고객 메모" value={claim.customerMemo} />
        <SummaryItem label="심사 사유" value={claim.adminReviewReason ?? "-"} />
        <SummaryItem label="반품 수령" value={formatDateTime(claim.returnReceivedAt)} />
        <SummaryItem label="수령 메모" value={claim.returnReceivedMemo ?? "-"} />
        <SummaryItem label="완료 시각" value={formatDateTime(claim.completedAt)} />
      </div>
      {evidenceFiles.length > 0 ? (
        <div className="evidence-grid admin-evidence-grid" aria-label="클레임 증빙">
          {evidenceFiles.map((file) => (
            <a href={file.fileUrl} key={file.evidenceId} target="_blank" rel="noreferrer">
              <img alt={file.originalFilename ?? "클레임 증빙 사진"} src={file.fileUrl} />
              <span>{file.originalFilename ?? "증빙 사진"}</span>
            </a>
          ))}
        </div>
      ) : (
        <div className="admin-empty compact">
          <strong>등록된 증빙 사진이 없습니다</strong>
          <span>단순 변심이 아닌 클레임은 고객 접수 시 사진 증빙이 필요합니다.</span>
        </div>
      )}

      {claim.status === "RETURN_WAITING" ? (
        <div className="admin-order-actions">
          <form action={recordReturnReceived} className="admin-inline-form">
            <input name="orderId" type="hidden" value={order.orderId} />
            <input name="claimId" type="hidden" value={claim.claimId} />
            <label className="wide">
              반품 수령/검수 메모
              <input name="memo" required placeholder="예: 반품 상품 입고 및 구성품 확인" />
            </label>
            <button className="button primary" type="submit">
              반품 수령 기록
            </button>
          </form>

          <form action={rejectClaim} className="admin-inline-form">
            <input name="orderId" type="hidden" value={order.orderId} />
            <input name="claimId" type="hidden" value={claim.claimId} />
            <label className="wide">
              반품 거부 사유
              <input name="reason" required placeholder="예: 상품 사용 흔적 확인" />
            </label>
            <button className="button" type="submit">
              반품 거부
            </button>
          </form>
        </div>
      ) : null}

      {claim.status === "RETURN_RECEIVED" ? (
        <div className="admin-order-actions">
          <form action={startReturnRefund} className="admin-inline-form">
            <input name="orderId" type="hidden" value={order.orderId} />
            <input name="claimId" type="hidden" value={claim.claimId} />
            <label className="wide">
              환불 시작 사유
              <input name="reason" required placeholder="예: 반품 검수 완료 후 계좌 환불 진행" />
            </label>
            <button className="button primary" type="submit">
              환불 시작
            </button>
          </form>

          <form action={rejectClaim} className="admin-inline-form">
            <input name="orderId" type="hidden" value={order.orderId} />
            <input name="claimId" type="hidden" value={claim.claimId} />
            <label className="wide">
              검수 불합격 사유
              <input name="reason" required placeholder="예: 반품 불가 상태로 입고" />
            </label>
            <button className="button" type="submit">
              검수 불합격
            </button>
          </form>
        </div>
      ) : null}
    </section>
  );
}

function SummaryItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function formatDateTime(value: string | null) {
  if (!value) {
    return "-";
  }
  return new Date(value).toLocaleString("ko-KR");
}

function AdminOrderActions({ order }: { order: AdminOrder }) {
  if (order.status === "PAYMENT_PENDING") {
    return (
      <div className="admin-order-actions">
        <h3>입금 처리</h3>
        <form action={confirmDeposit} className="admin-inline-form">
          <input name="orderId" type="hidden" value={order.orderId} />
			<label>
				실제 입금자명
				<input name="actualDepositorName" required />
			</label>
			<label>
				실제 입금액
				<input name="actualAmount" type="number" min="1" step="1" required />
			</label>
			<label>
				입금시각
				<input name="depositedAt" type="datetime-local" step="60" required />
			</label>
			<label>
				거래 식별 메모
				<input name="transactionReference" required placeholder="예: 은행 거래번호 또는 이체 메모" />
			</label>
          <label className="wide">
            입금 확인 사유
            <input name="reason" required placeholder="예: 입금액과 입금자명 확인" />
          </label>
          <button className="button primary" type="submit">
            입금 확인
          </button>
        </form>

        <form action={recordDepositMismatch} className="admin-inline-form">
          <input name="orderId" type="hidden" value={order.orderId} />
          <label className="wide">
            입금 불일치 메모
            <input name="memo" required placeholder="예: 입금자명이 주문서와 다름" />
          </label>
          <button className="button" type="submit">
            메모 저장
          </button>
        </form>

        <form action={cancelUnpaidDeposit} className="admin-inline-form">
          <input name="orderId" type="hidden" value={order.orderId} />
          <label className="wide">
            미입금 취소 사유
            <input name="reason" required placeholder="예: 입금 기한 경과" />
          </label>
          <button className="button" type="submit">
            미입금 취소
          </button>
        </form>
      </div>
    );
  }

  if (order.refund?.status === "APPROVED") {
    return <ManualRefundForm order={order} />;
  }

  if (order.fulfillment?.purchaseProvider === "DOMEGGOOK") {
    return null;
  }

  if (order.status === "SUPPLIER_ORDER_PENDING" && !order.fulfillment?.supplierOrderStartedAt) {
    return (
      <div className="admin-order-actions">
        <h3>다음 처리</h3>
        <form action={startSupplierWork} className="admin-inline-form">
          <input name="orderId" type="hidden" value={order.orderId} />
          <label>
            발주 시작 사유
            <input name="reason" required placeholder="예: 결제 확인 후 공급처 발주 준비" />
          </label>
          <button className="button primary" type="submit">
            발주 시작
          </button>
        </form>
      </div>
    );
  }

  if (order.status === "SUPPLIER_ORDER_PENDING") {
    return (
      <div className="admin-order-actions">
        <h3>다음 처리</h3>
        <form action={completeSupplierOrder} className="admin-inline-form">
          <input name="orderId" type="hidden" value={order.orderId} />
          <label>
            공급처 발주번호
            <input name="supplierOrderNumber" required placeholder="공급처 주문번호" />
          </label>
          <label>
            예상 출고일
            <input name="expectedShipDate" type="date" />
          </label>
          <label>
            공급처 메모
            <input name="supplierResponseMemo" placeholder="선택 입력" />
          </label>
          <label>
            처리 사유
            <input name="reason" required placeholder="예: 공급처 발주 완료" />
          </label>
          <button className="button primary" type="submit">
            발주 완료
          </button>
        </form>

        <form action={markOrderOutOfStock} className="admin-inline-form">
          <input name="orderId" type="hidden" value={order.orderId} />
          <label>
            품절 사유
            <input name="reason" required placeholder="예: 공급처 재고 없음" />
          </label>
          <button className="button" type="submit">
            품절 처리
          </button>
        </form>
      </div>
    );
  }

  if (order.status === "SUPPLIER_ORDERED") {
    return (
      <div className="admin-order-actions">
        <h3>다음 처리</h3>
        <form action={createOrderShipment} className="admin-inline-form">
          <input name="orderId" type="hidden" value={order.orderId} />
          <label>
            택배사
            <input name="carrier" required placeholder="예: CJ대한통운" />
          </label>
          <label>
            송장번호
            <input name="trackingNumber" required />
          </label>
          <button className="button primary" type="submit">
            송장 입력
          </button>
        </form>

        <form action={markOrderOutOfStock} className="admin-inline-form">
          <input name="orderId" type="hidden" value={order.orderId} />
          <label>
            품절 사유
            <input name="reason" required placeholder="예: 공급처 재고 없음" />
          </label>
          <button className="button" type="submit">
            품절 처리
          </button>
        </form>
      </div>
    );
  }

  return null;
}

function ManualRefundForm({ order }: { order: AdminOrder }) {
  if (!order.refund) return null;

  return (
    <div className="admin-order-actions">
      <h3>다음 처리</h3>
      <form action={completeManualRefund} className="admin-inline-form">
        <input name="orderId" type="hidden" value={order.orderId} />
        <input name="refundId" type="hidden" value={order.refund.refundId} />
        <label>
          환불 은행
          <input name="bankName" required />
        </label>
        <label>
          환불 계좌번호
          <input name="accountNumber" required />
        </label>
        <label>
          예금주
          <input name="accountHolder" required />
        </label>
        <label>
          실제 이체시각
          <input name="transferredAt" type="datetime-local" step="60" required />
        </label>
        <label>
          거래 식별 메모
          <input name="transactionReference" required placeholder="예: 은행 거래번호 또는 이체 메모" />
        </label>
        <label className="wide">
          수동 환불 완료 사유
          <input name="reason" required placeholder="예: 고객 계좌로 환불 이체 완료" />
        </label>
        <button className="button primary" type="submit">
          수동 환불 완료
        </button>
      </form>
    </div>
  );
}

function SupplierPurchasePanel({ order }: { order: AdminOrder }) {
  const purchase = order.fulfillment;
  if (purchase?.purchaseProvider !== "DOMEGGOOK") {
    return null;
  }
  const status = purchase.purchaseStatus ?? "READY";

  return (
    <section className="admin-claim-panel">
      <h3>도매꾹 자동 발주</h3>
      <div className="summary-list compact">
        <SummaryItem label="자동 발주 상태" value={supplierPurchaseStatusLabel(status)} />
        <SummaryItem
          label="예상 공급처 결제액"
          value={purchase.expectedSourceAmount == null ? "-" : formatPrice(purchase.expectedSourceAmount)}
        />
        <SummaryItem
          label="실제 공급처 결제액"
          value={purchase.actualSourceAmount == null ? "-" : formatPrice(purchase.actualSourceAmount)}
        />
        <SummaryItem label="공급처 주문번호" value={purchase.supplierOrderNumber ?? "-"} />
        <SummaryItem label="최근 동기화" value={formatDateTime(purchase.purchaseSyncedAt)} />
        <SummaryItem label="최근 오류" value={purchase.lastPurchaseError ?? "-"} />
      </div>
      <div className="admin-inline-form">
        {status === "READY" ? (
          <form action={validateSupplierPurchase}>
            <input name="orderId" type="hidden" value={order.orderId} />
            <button className="button" type="submit">재고·가격 검증</button>
          </form>
        ) : null}
        {status === "FAILED" ? (
          <form action={retrySupplierPurchase}>
            <input name="orderId" type="hidden" value={order.orderId} />
            <button className="button" type="submit">자동 발주 재시도</button>
          </form>
        ) : null}
        {status === "PROCESSING" || status === "RECONCILIATION_REQUIRED" ? (
          <form action={reconcileSupplierPurchase}>
            <input name="orderId" type="hidden" value={order.orderId} />
            <button className="button" type="submit">공급처 주문 대사</button>
          </form>
        ) : null}
        {status === "ORDERED" ? (
          <form action={cancelSupplierPurchase}>
            <input name="orderId" type="hidden" value={order.orderId} />
            <label>
              취소 사유
              <input name="reason" required maxLength={500} />
            </label>
            <button className="button" type="submit">공급처 주문 취소 요청</button>
          </form>
        ) : null}
      </div>
    </section>
  );
}

function supplierPurchaseStatusLabel(status: string) {
  return {
    READY: "자동 발주 대기",
    PROCESSING: "자동 발주 처리 중",
    RECONCILIATION_REQUIRED: "중복 방지 대사 필요",
    ORDERED: "공급처 주문 완료",
    FAILED: "자동 발주 실패",
    CANCEL_REQUESTED: "공급처 취소 요청",
    CANCELLED: "공급처 취소 완료",
  }[status] ?? status;
}
