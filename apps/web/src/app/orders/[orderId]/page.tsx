import Link from "next/link";
import { notFound } from "next/navigation";
import { ApiError } from "@/lib/api";
import { formatPrice } from "@/lib/catalog";
import {
  fulfillmentStatusLabel,
  getCustomerOrder,
  orderStatusLabel,
  paymentStatusLabel,
  refundStatusLabel,
  shipmentStatusLabel,
  type OrderDetail,
} from "@/lib/orders";
import { getCurrentUser } from "@/lib/session";
import { cancelOrder, createClaim, updateOrderShippingAddress } from "../actions";

type OrderDetailPageProps = {
  params: Promise<{ orderId: string }>;
  searchParams: Promise<{ message?: string }>;
};

async function loadOrder(orderId: string) {
  try {
    return { order: await getCustomerOrder(orderId), error: false };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound();
    }
    return { order: null, error: true };
  }
}

export default async function OrderDetailPage({ params, searchParams }: OrderDetailPageProps) {
  const [{ orderId }, query, session] = await Promise.all([
    params,
    searchParams,
    getCurrentUser(),
  ]);

  if (!session) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">Orders</p>
        <h1>로그인이 필요합니다</h1>
        <Link className="button primary" href="/login">
          로그인
        </Link>
      </section>
    );
  }

  const { order, error } = await loadOrder(orderId);

  if (error || !order) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">Orders</p>
        <h1>주문을 불러오지 못했습니다</h1>
        <p>백엔드 API 연결 상태를 확인해 주세요.</p>
      </section>
    );
  }

  return (
    <section className="order-detail-page">
      <div className="section-heading">
        <p className="eyebrow">{orderStatusLabel(order.status)}</p>
        <h1>주문 {order.orderNumber}</h1>
      </div>

      {query.message ? (
        <div className="notice">
          <strong>알림</strong>
          <span>{query.message}</span>
        </div>
      ) : null}

      <OrderSummaryPanel order={order} />
      <OrderItems order={order} />
      <OrderShippingAddressForm order={order} />
      <CancelOrderForm orderId={order.orderId} />
      <ClaimForm orderId={order.orderId} />
      <Link className="button" href="/orders">
        주문 목록
      </Link>
    </section>
  );
}

function OrderSummaryPanel({ order }: { order: OrderDetail }) {
  return (
    <section className="detail-section">
      <h2>상태</h2>
      <div className="summary-list">
        <div>
          <span>주문 상태</span>
          <strong>{orderStatusLabel(order.status)}</strong>
        </div>
        <div>
          <span>발주</span>
          <strong>{fulfillmentStatusLabel(order.fulfillment.status)}</strong>
        </div>
        <div>
          <span>배송</span>
          <strong>{shipmentStatusLabel(order.shipment.status)}</strong>
        </div>
        <div>
          <span>운송장</span>
          <strong>
            {order.shipment.carrier && order.shipment.trackingNumber
              ? `${order.shipment.carrier} ${order.shipment.trackingNumber}`
              : "없음"}
          </strong>
        </div>
        <div>
          <span>결제</span>
          <strong>{paymentStatusLabel(order.payment.status)}</strong>
        </div>
        <div>
          <span>환불</span>
          <strong>
            {order.refund.amount === null
              ? refundStatusLabel(order.refund.status)
              : `${refundStatusLabel(order.refund.status)} ${formatPrice(order.refund.amount)}`}
          </strong>
        </div>
        <div>
          <span>금액</span>
          <strong>{formatPrice(order.totalAmount)}</strong>
        </div>
      </div>
    </section>
  );
}

function OrderItems({ order }: { order: OrderDetail }) {
  return (
    <section className="detail-section">
      <h2>상품</h2>
      <div className="summary-list">
        {order.items.map((item) => (
          <div key={item.orderItemId}>
            <span>
              {item.productName} / {item.optionName} x {item.quantity}
            </span>
            <strong>{formatPrice(item.lineAmount)}</strong>
          </div>
        ))}
        <div>
          <span>배송비</span>
          <strong>{formatPrice(order.shippingFee)}</strong>
        </div>
        <div>
          <span>합계</span>
          <strong>{formatPrice(order.totalAmount)}</strong>
        </div>
      </div>
    </section>
  );
}

function OrderShippingAddressForm({ order }: { order: OrderDetail }) {
  const address = order.shippingAddress;

  return (
    <form action={updateOrderShippingAddress} className="checkout-form">
      <h2>배송지 변경</h2>
      <input name="orderId" type="hidden" value={order.orderId} />
      <label>
        받는 사람
        <input name="recipientName" required defaultValue={address.recipientName} />
      </label>
      <label>
        연락처
        <input name="recipientPhone" required defaultValue={address.recipientPhone} />
      </label>
      <label>
        우편번호
        <input name="postalCode" required defaultValue={address.postalCode} />
      </label>
      <label>
        주소
        <input name="address1" required defaultValue={address.address1} />
      </label>
      <label>
        상세 주소
        <input name="address2" defaultValue={address.address2 ?? ""} />
      </label>
      <button className="button" type="submit">
        배송지 변경
      </button>
    </form>
  );
}

function CancelOrderForm({ orderId }: { orderId: string }) {
  return (
    <form action={cancelOrder} className="claim-form">
      <h2>취소 요청</h2>
      <input name="orderId" type="hidden" value={orderId} />
      <label>
        사유
        <textarea name="reason" required />
      </label>
      <button className="button" type="submit">
        취소 요청
      </button>
    </form>
  );
}

function ClaimForm({ orderId }: { orderId: string }) {
  return (
    <form action={createClaim} className="claim-form">
      <h2>클레임 접수</h2>
      <input name="orderId" type="hidden" value={orderId} />
      <label>
        유형
        <select name="claimType" required defaultValue="RETURN">
          <option value="CANCEL">취소</option>
          <option value="RETURN">반품</option>
          <option value="EXCHANGE">교환</option>
        </select>
      </label>
      <label>
        사유
        <select name="claimReason" required defaultValue="SIMPLE_CHANGE_OF_MIND">
          <option value="SIMPLE_CHANGE_OF_MIND">단순 변심</option>
          <option value="DEFECT">상품 하자</option>
          <option value="WRONG_DELIVERY">오배송</option>
          <option value="DIFFERENT_FROM_PRODUCT_INFO">상품 정보와 다름</option>
          <option value="DELIVERY_ISSUE">배송 문제</option>
        </select>
      </label>
      <label>
        메모
        <textarea name="customerMemo" required />
      </label>
      <button className="button" type="submit">
        클레임 접수
      </button>
    </form>
  );
}
