import { randomUUID } from "node:crypto";
import Link from "next/link";
import { redirect } from "next/navigation";
import {
  adminStatusLabel,
  adminPortalFulfillmentAction,
  adminRefundProjection,
  getAdminOrder,
  getAdminOrderActions,
  getAdminOrders,
  type AdminOrder,
  type AdminOrderActionHistory,
} from "@/lib/admin";
import { adminRefundNextAction, retryCommandKey, type RetryCommand } from "@/lib/admin-payment";
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
  approveRefund,
  cancelUnpaidDeposit,
  cancelSupplierPurchase,
  completeManualRefund,
  completeSupplierOrder,
  confirmDeposit,
  correctShipmentDelivered,
  createOrderShipment,
  markOrderOutOfStock,
  recordDepositMismatch,
  recordLateDeposit,
  recordReturnReceived,
  reconcileSupplierPurchase,
  rejectClaim,
  startSupplierWork,
  startReturnRefund,
  syncShipmentTracking,
  takeOverPortalFulfillment,
  retrySupplierPurchase,
  validateSupplierPurchase,
} from "./actions";

type AdminOrdersPageProps = {
  searchParams: Promise<{
    from?: string;
    idempotencyKey?: string;
    message?: string;
    orderId?: string;
    page?: string;
    q?: string;
    retryAction?: string;
    status?: string;
    to?: string;
  }>;
};

export default async function AdminOrdersPage({ searchParams }: AdminOrdersPageProps) {
  const params = await searchParams;
  const requestedPage = positivePage(params.page);
  const data = await loadOrders(params, requestedPage - 1);

  if (!data.error && data.orders.totalPages > 0 && requestedPage > data.orders.totalPages) {
    redirect(orderPageHref(params, data.orders.totalPages));
  }

  const orders = data.error ? [] : data.orders.orders;
  const currentPage = data.error ? 1 : data.orders.page + 1;
  const totalPages = data.error ? 0 : data.orders.totalPages;
  const selectedOrderId = params.orderId ?? orders[0]?.orderId;
  const selectedSummary = orders.find((order) => order.orderId === selectedOrderId) ?? orders[0];
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
            <option value="EXPIRED">입금기한 만료</option>
            <option value="CANCELLED">취소완료</option>
            <option value="SUPPLIER_ORDER_PENDING">발주대기</option>
            <option value="SUPPLIER_ORDERED">발주완료</option>
            <option value="SHIPPED">배송중</option>
            <option value="REFUND_REQUESTED">환불요청</option>
            <option value="REFUNDED">환불완료</option>
            <option value="OUT_OF_STOCK">품절</option>
          </select>
          <input name="q" placeholder="주문번호 또는 고객 이메일 검색" defaultValue={params.q ?? ""} />
          <button className="button" type="submit">
            검색
          </button>
        </form>
      ) : null}

      {!data.error ? (
        <div className="admin-orders-layout">
          <section className="admin-panel">
            <div className="admin-panel-head">
              <h2>주문 목록</h2>
              <span>총 {data.orders.totalElements}건</span>
            </div>
            <div className="admin-table orders">
              <div className="admin-table-row admin-table-head">
                <span>주문번호</span>
                <span>고객사</span>
                <span>상품수</span>
                <span>결제금액</span>
                <span>주문상태</span>
              </div>
              {orders.map((order) => (
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
              {orders.length === 0 ? (
                <div className="admin-empty">
                  <strong>조회된 주문이 없습니다</strong>
                  <span>검색 조건을 바꾸거나 새 주문이 들어온 뒤 다시 확인하세요.</span>
                </div>
              ) : null}
            </div>
            {totalPages > 0 ? (
              <nav className="admin-pagination" aria-label="주문 목록 페이지">
                {currentPage > 1 ? (
                  <Link href={orderPageHref(params, currentPage - 1)}>이전</Link>
                ) : (
                  <span aria-disabled="true">이전</span>
                )}
                {pageNumbers(currentPage, totalPages).map((page) =>
                  page === currentPage ? (
                    <strong aria-current="page" key={page}>{page}</strong>
                  ) : (
                    <Link href={orderPageHref(params, page)} key={page}>{page}</Link>
                  ),
                )}
                {currentPage < totalPages ? (
                  <Link href={orderPageHref(params, currentPage + 1)}>다음</Link>
                ) : (
                  <span aria-disabled="true">다음</span>
                )}
              </nav>
            ) : null}
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
              {!detail.error ? <ShipmentPanel order={selectedOrder} /> : null}
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
              {!detail.error ? <SupplierPurchasePanel order={selectedOrder} /> : null}
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
              {!detail.error ? <ClaimPanel order={selectedOrder} /> : null}
					<RefundEvidencePanel order={selectedOrder} />
					<AdminActionHistoryPanel actions={actionHistory.actions} error={actionHistory.error} />
              {!detail.error ? (
                <AdminOrderActions
                  order={selectedOrder}
                  retry={{ action: params.retryAction, key: params.idempotencyKey }}
                />
              ) : null}
            </aside>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

async function loadOrders(
  params: { from?: string; q?: string; status?: string; to?: string },
  page: number,
) {
  try {
    return {
      error: false as const,
      orders: await getAdminOrders({
        q: params.q?.trim(),
        status: params.status,
        from: params.from,
        to: params.to,
        page,
      }),
    };
  } catch {
    return { error: true as const, orders: null };
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
  params: { from?: string; page?: string; q?: string; status?: string; to?: string },
) {
  const search = new URLSearchParams({ orderId });
  for (const key of ["from", "page", "q", "status", "to"] as const) {
    if (params[key]) {
      search.set(key, params[key]);
    }
  }
  return `/admin/orders?${search.toString()}`;
}

function orderListHref(params: { from?: string; page?: string; q?: string; status?: string; to?: string }) {
  const search = new URLSearchParams();
  for (const key of ["from", "page", "q", "status", "to"] as const) {
    if (params[key]) {
      search.set(key, params[key]);
    }
  }
  return `/admin/orders${search.size ? `?${search.toString()}` : ""}`;
}

function positivePage(value?: string) {
  const page = Number.parseInt(value ?? "1", 10);
  return Number.isFinite(page) && page > 0 ? page : 1;
}

function orderPageHref(
  params: { from?: string; q?: string; status?: string; to?: string },
  page: number,
) {
  const search = new URLSearchParams();
  for (const key of ["from", "q", "status", "to"] as const) {
    if (params[key]) search.set(key, params[key]);
  }
  if (page > 1) search.set("page", String(page));
  const value = search.toString();
  return value ? `/admin/orders?${value}` : "/admin/orders";
}

function pageNumbers(currentPage: number, totalPages: number) {
  const start = Math.max(1, Math.min(currentPage - 2, totalPages - 4));
  const end = Math.min(totalPages, start + 4);
  return Array.from({ length: end - start + 1 }, (_, index) => start + index);
}

function shippingAddressText(address: AdminOrder["shippingAddress"]) {
  if (!address) {
    return "배송지 상세는 주문 상세 API에서 확인합니다.";
  }
  if (typeof address === "string") {
    return address;
  }
  const deliveryMemo = address.deliveryMemo ? ` / 배송 메모: ${address.deliveryMemo}` : "";
  return `${address.recipientName} / ${address.recipientPhone} / ${address.postalCode} ${address.address1} ${address.address2 ?? ""}${deliveryMemo}`;
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
  if (!refund) {
    return null;
  }
  const projection = adminRefundProjection(refund);

  return (
    <section className="admin-claim-panel">
      <h3>{refund.refundScope === "PAYMENT_GROUP" ? "결제그룹 환불" : "환불"}</h3>
      <div className="summary-list compact">
        <SummaryItem
          label="환불 범위"
          value={projection.scopeLabel}
        />
        <SummaryItem label="환불 금액" value={formatPrice(projection.refundAmount)} />
        <SummaryItem label="환불 사유" value={refund.reason ?? "-"} />
        {refund.refundScope === "PAYMENT_GROUP" ? (
          <SummaryItem label="결제그룹 주문서" value={order.checkoutNumber} />
        ) : null}
        {projection.paymentGroupId ? <SummaryItem label="결제그룹" value={projection.paymentGroupId} /> : null}
        {projection.appliedOrderIds.length > 0 ? (
          <SummaryItem label="적용 주문 ID" value={projection.appliedOrderIds.join(", ")} />
        ) : null}
        {refund.manualRefundedAt ? (
          <>
            <SummaryItem label="환불 은행" value={refund.manualRefundBankName ?? "-"} />
            <SummaryItem label="환불 계좌번호" value={refund.manualRefundAccountNumber ?? "-"} />
            <SummaryItem label="예금주" value={refund.manualRefundAccountHolder ?? "-"} />
            <SummaryItem label="실제 이체시각" value={formatDateTime(refund.manualRefundTransferredAt ?? null)} />
            <SummaryItem label="거래 식별 메모" value={refund.manualRefundTransactionReference ?? "-"} />
            <SummaryItem label="처리 사유" value={refund.manualRefundReason ?? "-"} />
          </>
        ) : null}
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

function AdminOrderActions({ order, retry }: { order: AdminOrder; retry: RetryCommand }) {
  const paymentGroupStatus = order.paymentGroup?.status ?? order.status;
  if (paymentGroupStatus === "PAYMENT_PENDING") {
    return (
      <div className="admin-order-actions">
        <h3>입금 처리</h3>
        <div className="notice">
          <strong>은행 거래내역을 기준으로 입력하세요</strong>
          <span>응답 결과가 불확실하면 입력한 증적을 바꾸지 말고 같은 요청으로 다시 시도하세요.</span>
        </div>
        <form action={confirmDeposit} className="admin-inline-form">
          <input name="orderId" type="hidden" value={order.orderId} />
          <input name="idempotencyKey" type="hidden" value={stableCommandKey(retry, "confirm-deposit")} />
          <DepositEvidenceFields defaultAmount={order.paymentGroup?.totalAmount ?? order.totalAmount} reasonPlaceholder="예: 입금액과 입금자명 확인" />
          <button className="button primary" type="submit">
            입금 확인
          </button>
        </form>

        <DepositMismatchForm order={order} retry={retry} />

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

  if (paymentGroupStatus === "EXPIRED" || paymentGroupStatus === "CANCELLED") {
    return (
      <div className="admin-order-actions">
        <h3>뒤늦은 입금 처리</h3>
        <div className="notice">
          <strong>은행 거래내역을 먼저 확인하세요</strong>
          <span>정확한 입금이면 뒤늦은 입금으로, 금액이 다르면 입금 불일치로 기록합니다.</span>
        </div>
        <form action={recordLateDeposit} className="admin-inline-form">
          <input name="orderId" type="hidden" value={order.orderId} />
          <input name="idempotencyKey" type="hidden" value={stableCommandKey(retry, "late-deposit")} />
          <DepositEvidenceFields defaultAmount={order.paymentGroup?.totalAmount ?? order.totalAmount} reasonPlaceholder="예: 주문 만료 후 은행 거래내역에서 입금 확인" />
          <button className="button primary" type="submit">
            뒤늦은 입금 처리
          </button>
        </form>
        <DepositMismatchForm order={order} retry={retry} />
      </div>
    );
  }

  const refundAction = adminRefundNextAction(order.refund?.status);
  if (refundAction === "APPROVE") {
    return <RefundApprovalForm order={order} />;
  }

  if (refundAction === "MANUAL_COMPLETE") {
    return <ManualRefundForm order={order} retry={retry} />;
  }

  const portalAction = adminPortalFulfillmentAction(order.fulfillment);
  if (portalAction === "TAKEOVER") {
    return (
      <div className="admin-order-actions">
        <h3>공급처 포털 출고</h3>
        <div className="notice">
          <strong>현재 공급처가 처리 중입니다</strong>
          <span>Coreable이 직접 이어서 처리할 때만 인계하세요. 인계 후 공급처에 자동 재배정되지 않습니다.</span>
        </div>
        <form action={takeOverPortalFulfillment} className="admin-inline-form">
          <input name="orderId" type="hidden" value={order.orderId} />
          <input name="idempotencyKey" type="hidden" value={stableCommandKey(retry, "portal-takeover")} />
          <label className="wide">
            인계 사유
            <select defaultValue="COREABLE_FULFILLMENT_TAKEOVER" name="reason" required>
              <option value="COREABLE_FULFILLMENT_TAKEOVER">Coreable 직접 출고 처리</option>
              <option value="SUPPLIER_SUPPORT_REQUIRED">공급처 지원 요청</option>
              <option value="OPERATIONAL_RISK">운영 위험 대응</option>
            </select>
            <span className="field-help">고객 정보가 남지 않도록 정해진 운영 사유만 기록합니다.</span>
          </label>
          <button className="button" type="submit">Coreable 처리로 인계</button>
        </form>
      </div>
    );
  }

  if (portalAction === "COREABLE") {
    return (
      <div className="notice">
        <strong>Coreable 처리로 인계된 포털 주문입니다</strong>
        <span>기존 수동 발주 액션은 사용할 수 없습니다. 송장 처리는 복수 송장 기능에서 이어집니다.</span>
      </div>
    );
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

function RefundApprovalForm({ order }: { order: AdminOrder }) {
  if (!order.refund) return null;

  return (
    <div className="admin-order-actions">
      <h3>{order.refund.refundScope === "PAYMENT_GROUP" ? "결제그룹 전체 환불 승인" : "환불 승인"}</h3>
      <form action={approveRefund} className="admin-inline-form">
        <input name="orderId" type="hidden" value={order.orderId} />
        <input name="refundId" type="hidden" value={order.refund.refundId} />
        <label className="wide">
          승인 사유
          <input name="reason" required maxLength={1000} placeholder="예: 입금 증적과 환불 금액 확인" />
        </label>
        <button className="button primary" type="submit">
          환불 승인
        </button>
      </form>
    </div>
  );
}

function DepositMismatchForm({ order, retry }: { order: AdminOrder; retry: RetryCommand }) {
  return (
    <form action={recordDepositMismatch} className="admin-inline-form">
      <input name="orderId" type="hidden" value={order.orderId} />
      <input name="idempotencyKey" type="hidden" value={stableCommandKey(retry, "deposit-mismatch")} />
      <DepositEvidenceFields reasonPlaceholder="예: 실제 입금액이 주문서 금액과 다름" />
      <button className="button" type="submit">
        불일치 입금·환불 기록
      </button>
    </form>
  );
}

function DepositEvidenceFields({
  defaultAmount,
  reasonPlaceholder,
}: {
  defaultAmount?: number;
  reasonPlaceholder: string;
}) {
  return (
    <>
      <label>
        실제 입금자명
        <input name="actualDepositorName" required />
      </label>
      <label>
        실제 입금액
        <input defaultValue={defaultAmount} min="1" name="actualAmount" required step="1" type="number" />
      </label>
      <label>
        입금시각
        <input name="depositedAt" required step="60" type="datetime-local" />
      </label>
      <label>
        거래 식별 메모
        <input name="transactionReference" required placeholder="예: 은행 거래번호 또는 이체 메모" />
      </label>
      <label className="wide">
        처리 사유
        <input name="reason" required placeholder={reasonPlaceholder} />
      </label>
    </>
  );
}

function ManualRefundForm({ order, retry }: { order: AdminOrder; retry: RetryCommand }) {
  if (!order.refund) return null;

  return (
    <div className="admin-order-actions">
      <h3>{order.refund.refundScope === "PAYMENT_GROUP" ? "결제그룹 전체 환불" : "다음 처리"}</h3>
      <div className="notice">
        <strong>이미 이체했다면 새로 이체하지 마세요</strong>
        <span>처리 결과가 불확실할 때는 이 화면의 같은 요청으로 다시 시도하거나 거래내역을 대사하세요.</span>
      </div>
      <form action={completeManualRefund} className="admin-inline-form">
        <input name="orderId" type="hidden" value={order.orderId} />
        <input name="refundId" type="hidden" value={order.refund.refundId} />
        <input name="idempotencyKey" type="hidden" value={stableCommandKey(retry, `manual-refund-${order.refund.refundId}`)} />
        <label>
          실제 환불 이체액
          <input name="transferredAmount" readOnly type="number" value={order.refund.refundAmount} />
        </label>
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

function stableCommandKey(retry: RetryCommand, action: string) {
  return retryCommandKey(retry, action) ?? randomUUID();
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
