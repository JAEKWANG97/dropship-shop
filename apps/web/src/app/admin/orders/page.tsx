import { adminStatusLabel, getAdminOrders } from "@/lib/admin";
import { formatPrice } from "@/lib/catalog";

type AdminOrdersPageProps = {
  searchParams: Promise<{ from?: string; q?: string; status?: string; to?: string }>;
};

export default async function AdminOrdersPage({ searchParams }: AdminOrdersPageProps) {
  const [data, params] = await Promise.all([loadOrders(), searchParams]);
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
  const selectedOrder = filteredOrders[0];

  return (
    <div className="admin-page">
      <div className="admin-heading">
        <div>
          <h1>주문 관리</h1>
          <p>고객 주문 내역과 결제, 배송 상태를 확인하고 처리하세요.</p>
        </div>
      </div>

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
            <option value="">전체 주문상태</option>
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
                <div className="admin-table-row" key={order.orderId}>
                  <strong>{order.orderNumber}</strong>
                  <span>{order.customerEmail}</span>
                  <span>{order.items?.length ?? 0}개</span>
                  <span>{formatPrice(order.totalAmount)}</span>
                  <span className={`admin-badge ${order.status.toLowerCase()}`}>
                    {adminStatusLabel(order.status)}
                  </span>
                </div>
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
              <div className="admin-panel-head">
                <h2>주문 상세</h2>
                <span className={`admin-badge ${selectedOrder.status.toLowerCase()}`}>
                  {adminStatusLabel(selectedOrder.status)}
                </span>
              </div>
              <strong>{selectedOrder.orderNumber}</strong>
              <span>{new Date(selectedOrder.createdAt).toLocaleString("ko-KR")}</span>
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
              <p>{selectedOrder.shippingAddress ?? "배송지 상세는 주문 상세 API에서 확인합니다."}</p>
              <h3>결제 정보</h3>
              <div className="summary-list compact">
                <div>
                  <span>결제수단</span>
                  <strong>{selectedOrder.paymentMethod ?? "확인 필요"}</strong>
                </div>
                <div>
                  <span>결제금액</span>
                  <strong>{formatPrice(selectedOrder.totalAmount)}</strong>
                </div>
              </div>
            </aside>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

async function loadOrders() {
  try {
    return { error: false as const, orders: await getAdminOrders() };
  } catch {
    return { error: true as const, orders: [] };
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
