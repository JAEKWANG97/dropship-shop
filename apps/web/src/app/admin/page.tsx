import Link from "next/link";
import { adminStatusLabel, getAdminOrders, getAdminProducts } from "@/lib/admin";
import { formatPrice } from "@/lib/catalog";

export default async function AdminDashboardPage() {
  const [products, orders] = await Promise.all([getAdminProducts(), getAdminOrders()]);
  const pendingOrders = orders.filter((order) => order.status === "SUPPLIER_ORDER_PENDING");
  const shippedOrders = orders.filter((order) => order.status === "SHIPPED");
  const lowStockProducts = products.filter((product) => product.status === "SOLD_OUT");
  const revenue = orders.reduce((sum, order) => sum + order.totalAmount, 0);

  return (
    <div className="admin-page">
      <div className="admin-heading">
        <div>
          <h1>관리자 대시보드</h1>
          <p>오늘 주문, 배송, 상품 상태를 한 화면에서 확인하세요.</p>
        </div>
        <Link className="button primary" href="/admin/products/new">
          상품 등록
        </Link>
      </div>

      <div className="admin-metrics">
        <Metric label="오늘 주문" value={`${orders.length}건`} sub={formatPrice(revenue)} />
        <Metric label="오늘 매출" value={formatPrice(revenue)} sub="배송비 포함 가격" />
        <Metric label="배송 대기" value={`${pendingOrders.length}건`} sub="발주 시작 필요" />
        <Metric label="재고 확인" value={`${lowStockProducts.length}건`} sub="판매 상태 확인" />
      </div>

      <div className="admin-dashboard-grid">
        <section className="admin-panel">
          <div className="admin-panel-head">
            <h2>매출 흐름</h2>
            <span>최근 7일</span>
          </div>
          <div className="admin-bars" aria-label="최근 7일 매출">
            {[48, 64, 42, 78, 56, 88, 72].map((height, index) => (
              <span key={index} style={{ height: `${height}%` }} />
            ))}
          </div>
        </section>

        <section className="admin-panel">
          <div className="admin-panel-head">
            <h2>처리 현황</h2>
            <span>주문 상태</span>
          </div>
          <div className="admin-status-list">
            <StatusRow label="발주대기" value={pendingOrders.length} />
            <StatusRow label="배송중" value={shippedOrders.length} />
            <StatusRow label="품절" value={orders.filter((order) => order.status === "OUT_OF_STOCK").length} />
          </div>
        </section>
      </div>

      <div className="admin-dashboard-grid">
        <section className="admin-panel">
          <div className="admin-panel-head">
            <h2>최근 주문</h2>
            <Link href="/admin/orders">전체보기</Link>
          </div>
          <div className="admin-table">
            <div className="admin-table-row admin-table-head">
              <span>주문번호</span>
              <span>고객</span>
              <span>금액</span>
              <span>상태</span>
            </div>
            {orders.slice(0, 5).map((order) => (
              <div className="admin-table-row" key={order.orderId}>
                <strong>{order.orderNumber}</strong>
                <span>{order.customerEmail}</span>
                <span>{formatPrice(order.totalAmount)}</span>
                <span className={`admin-badge ${order.status.toLowerCase()}`}>
                  {adminStatusLabel(order.status)}
                </span>
              </div>
            ))}
          </div>
        </section>

        <section className="admin-panel">
          <div className="admin-panel-head">
            <h2>상태 확인 상품</h2>
            <Link href="/admin/products">상품 관리</Link>
          </div>
          <div className="admin-list">
            {lowStockProducts.concat(products).slice(0, 4).map((product) => (
              <div key={product.id}>
                <strong>{product.name}</strong>
                <span>{product.supplierName}</span>
                <span className={`admin-badge ${product.status.toLowerCase()}`}>
                  {adminStatusLabel(product.status)}
                </span>
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}

function Metric({ label, sub, value }: { label: string; sub: string; value: string }) {
  return (
    <article className="admin-metric">
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{sub}</small>
    </article>
  );
}

function StatusRow({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <span>{label}</span>
      <strong>{value}건</strong>
    </div>
  );
}
