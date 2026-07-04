import Link from "next/link";
import { notFound } from "next/navigation";
import { ApiError } from "@/lib/api";
import { formatPrice } from "@/lib/catalog";
import { BUSINESS_PROFILE } from "@/lib/legal";
import {
  claimReasonLabel,
  claimStatusLabel,
  claimTypeLabel,
  fulfillmentStatusLabel,
  getCustomerOrder,
  orderStatusLabel,
  paymentStatusLabel,
  refundStatusLabel,
  shipmentStatusLabel,
  type ClaimEvidence,
  type OrderDetail,
} from "@/lib/orders";
import { getAdminUser, getCurrentUser } from "@/lib/session";
import { SubmitButton } from "../../submit-button";
import { cancelOrder, createClaim, updateOrderShippingAddress } from "../actions";
import { ClaimEvidenceInput } from "../claim-evidence-input";

type OrderDetailPageProps = {
  params: Promise<{ orderId: string }>;
  searchParams: Promise<{ message?: string }>;
};

async function loadOrder(orderId: string) {
  try {
    return { order: await getCustomerOrder(orderId), status: "ok" as const };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound();
    }
    if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
      return { order: null, status: "forbidden" as const };
    }
    return { order: null, status: "error" as const };
  }
}

export default async function OrderDetailPage({ params, searchParams }: OrderDetailPageProps) {
  const [{ orderId }, query, session, admin] = await Promise.all([
    params,
    searchParams,
    getCurrentUser(),
    getAdminUser(),
  ]);

  if (!session) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">주문</p>
        <h1>로그인이 필요합니다</h1>
        <Link className="button primary" href="/login">
          로그인
        </Link>
      </section>
    );
  }

  const { order, status } = await loadOrder(orderId);

  if (status === "forbidden" && admin) {
    return <AdminCustomerOrderNotice />;
  }

  if (status !== "ok" || !order) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">주문</p>
        <h1>주문을 불러오지 못했습니다</h1>
        <div className="notice danger">
          <strong>API 연결 오류</strong>
          <span>백엔드 API 연결 상태를 확인해 주세요.</span>
        </div>
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
      <ClaimProgressPanel order={order} />
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

function AdminCustomerOrderNotice() {
  return (
    <section className="narrow-page">
      <p className="eyebrow">주문</p>
      <h1>관리자 계정은 고객 주문 기능을 사용할 수 없습니다</h1>
      <p>주문 조회와 운영 처리는 관리자 화면에서 확인해 주세요.</p>
      <div className="link-list">
        <Link className="button primary" href="/products">
          상품 보기
        </Link>
        <Link className="button" href="/admin">
          관리자 홈
        </Link>
      </div>
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

function ClaimProgressPanel({ order }: { order: OrderDetail }) {
  const claims = order.claims.length > 0 ? order.claims : order.claim ? [order.claim] : [];
  if (claims.length === 0) {
    return null;
  }

  return (
    <section className="detail-section">
      <h2>클레임 처리 상태</h2>
      <div className="claim-list">
        {claims.map((claim) => (
          <article className="claim-card" key={claim.claimId}>
            <div className="summary-list">
              <div>
                <span>유형</span>
                <strong>{claimTypeLabel(claim.claimType)}</strong>
              </div>
              <div>
                <span>접수 상태</span>
                <strong>{claim.customerStatusLabel || claimStatusLabel(claim.status)}</strong>
              </div>
              <div>
                <span>접수 사유</span>
                <strong>{claimReasonLabel(claim.claimReason)}</strong>
              </div>
              <div>
                <span>고객 메모</span>
                <strong>{claim.customerMemo}</strong>
              </div>
              {claim.adminReviewReason ? (
                <div>
                  <span>처리 사유</span>
                  <strong>{claim.adminReviewReason}</strong>
                </div>
              ) : null}
              {claim.returnReceivedAt ? (
                <div>
                  <span>반품 수령</span>
                  <strong>{new Date(claim.returnReceivedAt).toLocaleString("ko-KR")}</strong>
                </div>
              ) : null}
              {claim.completedAt ? (
                <div>
                  <span>완료 시각</span>
                  <strong>{new Date(claim.completedAt).toLocaleString("ko-KR")}</strong>
                </div>
              ) : null}
            </div>
            <EvidenceGrid files={claim.evidenceFiles} />
            {claim.claimType === "RETURN" && claim.status === "RETURN_WAITING" ? (
              <div className="notice">
                <strong>반송 안내</strong>
                <span>반송지: {BUSINESS_PROFILE.returnAddress}</span>
              </div>
            ) : null}
            {claim.status === "REFUND_PROCESSING" ? (
              <div className="notice">
                <strong>환불 처리 중</strong>
                <span>관리자 확인 후 계좌 환불 완료 시 주문 상태가 환불 완료로 변경됩니다.</span>
              </div>
            ) : null}
            {claim.status === "REJECTED" ? (
              <div className="notice">
                <strong>클레임이 거부되었습니다</strong>
                <span>{claim.adminReviewReason ?? "거부 사유는 고객센터 문의로 확인해 주세요."}</span>
              </div>
            ) : null}
          </article>
        ))}
      </div>
    </section>
  );
}

function EvidenceGrid({ files }: { files: ClaimEvidence[] }) {
  if (!Array.isArray(files) || files.length === 0) {
    return null;
  }
  return (
    <div className="evidence-grid" aria-label="증빙 사진">
      {files.map((file) => (
        <a href={file.fileUrl} key={file.evidenceId} target="_blank" rel="noreferrer">
          <img alt={file.originalFilename ?? "클레임 증빙 사진"} src={file.fileUrl} />
          <span>{file.originalFilename ?? "증빙 사진"}</span>
        </a>
      ))}
    </div>
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
      <SubmitButton className="button" pendingLabel="변경 중...">
        배송지 변경
      </SubmitButton>
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
      <SubmitButton className="button" pendingLabel="요청 중...">
        취소 요청
      </SubmitButton>
    </form>
  );
}

function ClaimForm({ orderId }: { orderId: string }) {
  return (
    <form action={createClaim} className="claim-form" encType="multipart/form-data">
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
      <ClaimEvidenceInput />
      <SubmitButton className="button" pendingLabel="접수 중...">
        클레임 접수
      </SubmitButton>
    </form>
  );
}
