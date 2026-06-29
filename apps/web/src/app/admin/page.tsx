import Link from "next/link";
import { adminStatusLabel, getAdminOrders, getAdminProducts } from "@/lib/admin";
import { formatPrice } from "@/lib/catalog";

export default async function AdminDashboardPage() {
  const data = await loadDashboard();

  if (data.error) {
    return (
      <div className="admin-page">
        <div className="admin-heading">
          <div>
            <h1>관리자 대시보드</h1>
            <p>주문, 배송, 상품 상태를 한 화면에서 확인하세요.</p>
          </div>
        </div>
        <AdminDataError />
      </div>
    );
  }

  const { orders, products } = data;
  const pendingOrders = orders.filter((order) => order.status === "SUPPLIER_ORDER_PENDING");
  const shippedOrders = orders.filter((order) => order.status === "SHIPPED");
  const lowStockProducts = products.filter((product) => product.status === "SOLD_OUT");
  const revenue = orders.reduce((sum, order) => sum + order.totalAmount, 0);

  return (
    <div className="admin-page">
      <div className="admin-heading">
        <div>
          <h1>관리자 대시보드</h1>
          <p>주문, 배송, 상품 상태를 한 화면에서 확인하세요.</p>
        </div>
        <Link className="button primary" href="/admin/products/new">
          상품 등록
        </Link>
      </div>

      <div className="admin-metrics">
        <Metric label="주문" value={`${orders.length}건`} sub={formatPrice(revenue)} />
        <Metric label="매출" value={formatPrice(revenue)} sub="배송비 포함 가격" />
        <Metric label="배송 대기" value={`${pendingOrders.length}건`} sub="발주 시작 필요" />
        <Metric label="재고 확인" value={`${lowStockProducts.length}건`} sub="판매 상태 확인" />
      </div>

      <div className="admin-dashboard-grid">
        <section className="admin-panel">
          <div className="admin-panel-head">
            <h2>운영 집계</h2>
            <span>현재 데이터</span>
          </div>
          <div className="admin-status-list">
            <div>
              <span>주문</span>
              <strong>{orders.length}건</strong>
            </div>
            <div>
              <span>매출</span>
              <strong>{formatPrice(revenue)}</strong>
            </div>
            <div>
              <span>상품</span>
              <strong>{products.length}개</strong>
            </div>
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
            {orders.length === 0 ? (
              <div className="admin-empty">
                <strong>처리할 주문이 없습니다</strong>
                <span>새 주문이 들어오면 이 영역에 표시됩니다.</span>
              </div>
            ) : null}
          </div>
        </section>

        <section className="admin-panel">
          <div className="admin-panel-head">
            <h2>상태 확인 상품</h2>
            <Link href="/admin/products">상품 관리</Link>
          </div>
          <div className="admin-list">
            {lowStockProducts.slice(0, 4).map((product) => (
              <div key={product.id}>
                <strong>{product.name}</strong>
                <span>{product.supplierName}</span>
                <span className={`admin-badge ${product.status.toLowerCase()}`}>
                  {adminStatusLabel(product.status)}
                </span>
              </div>
            ))}
            {lowStockProducts.length === 0 ? (
              <div className="admin-empty compact">
                <strong>확인이 필요한 상품이 없습니다</strong>
                <span>품절 상품이 생기면 이 영역에 표시됩니다.</span>
              </div>
            ) : null}
          </div>
        </section>
      </div>
    </div>
  );
}

async function loadDashboard() {
  try {
    const [products, orders] = await Promise.all([getAdminProducts(), getAdminOrders()]);
    return { error: false as const, orders, products };
  } catch {
    return { error: true as const, orders: [], products: [] };
  }
}

function AdminDataError() {
  return (
    <div className="notice">
      <strong>운영 데이터를 불러오지 못했습니다</strong>
      <span>권한, API 서버, 네트워크 상태를 확인한 뒤 다시 시도하세요.</span>
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
