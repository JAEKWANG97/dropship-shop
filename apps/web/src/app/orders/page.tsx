import Link from "next/link";
import { ApiError } from "@/lib/api";
import { formatPrice } from "@/lib/catalog";
import { getCustomerOrders, orderStatusLabel, type OrderSummary } from "@/lib/orders";
import { getCurrentUser } from "@/lib/session";

async function loadOrders() {
  try {
    return { orders: (await getCustomerOrders()).orders, error: false };
  } catch (error) {
    if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
      return { orders: [] as OrderSummary[], error: false };
    }
    return { orders: [] as OrderSummary[], error: true };
  }
}

export default async function OrdersPage() {
  const [session, data] = await Promise.all([getCurrentUser(), loadOrders()]);

  if (!session) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">Orders</p>
        <h1>로그인이 필요합니다</h1>
        <p>주문 내역은 소셜 로그인 후 확인할 수 있습니다.</p>
        <Link className="button primary" href="/login">
          로그인
        </Link>
      </section>
    );
  }

  if (data.error) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">Orders</p>
        <h1>주문을 불러오지 못했습니다</h1>
        <p>백엔드 API 연결 상태를 확인해 주세요.</p>
      </section>
    );
  }

  return (
    <section className="orders-page">
      <div className="section-heading">
        <p className="eyebrow">Orders</p>
        <h1>주문 내역</h1>
      </div>

      {data.orders.length === 0 ? (
        <div className="notice">
          <strong>주문 내역이 없습니다</strong>
          <Link className="button primary" href="/products">
            상품 보기
          </Link>
        </div>
      ) : (
        <div className="order-group-list">
          {data.orders.map((order) => (
            <Link className="order-card" href={`/orders/${order.orderId}`} key={order.orderId}>
              <div>
                <strong>{order.orderNumber}</strong>
                <span>{orderStatusLabel(order.status)}</span>
              </div>
              <div className="summary-list compact">
                <div>
                  <span>주문일</span>
                  <strong>{new Date(order.createdAt).toLocaleString("ko-KR")}</strong>
                </div>
                <div>
                  <span>결제 그룹</span>
                  <strong>{order.checkoutNumber}</strong>
                </div>
                <div>
                  <span>금액</span>
                  <strong>{formatPrice(order.totalAmount)}</strong>
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </section>
  );
}
