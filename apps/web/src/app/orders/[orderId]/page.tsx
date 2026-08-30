import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { ApiError } from "@/lib/api";
import { formatPrice } from "@/lib/catalog";
import { BUSINESS_PROFILE } from "@/lib/legal";
import {
  claimReasonLabel,
  claimStatusLabel,
  claimTypeLabel,
  customerDirectCancelBlocked,
  customerOrderProjection,
  fulfillmentStatusLabel,
  getCustomerOrder,
  paymentStatusLabel,
  refundStatusLabel,
  shipmentStatusLabel,
  type ClaimEvidence,
  type OrderDetail,
} from "@/lib/orders";
import { getAdminUser, getCurrentUser } from "@/lib/session";
import { SubmitButton } from "../../submit-button";
import { cancelOrder, createClaim } from "../actions";
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
    redirect(`/login?redirectTo=${encodeURIComponent(`/orders/${orderId}`)}`);
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
        <p className="eyebrow">{customerOrderProjection(order).label}</p>
        <h1>주문 {order.orderNumber}</h1>
      </div>

      {query.message ? (
        <div className="notice">
          <strong>알림</strong>
          <span>{query.message}</span>
        </div>
      ) : null}

      <OrderSummaryPanel order={order} />
      <CustomerShipmentsPanel order={order} />
      <ClaimProgressPanel order={order} />
      <OrderItems order={order} />
      <OrderShippingAddressPanel order={order} />
      {!customerDirectCancelBlocked(order.status) ? <CancelOrderForm orderId={order.orderId} /> : null}
      <ClaimForm orderId={order.orderId} />
      <Link className="button" href="/orders">
        주문 목록
      </Link>
    </section>
  );
}

function CustomerShipmentsPanel({ order }: { order: OrderDetail }) {
  if (!Array.isArray(order.shipments)) {
    return null;
  }

  return (
    <section className="detail-section">
      <h2>배송조회</h2>
      {order.shipments.length === 0 ? (
        <div className="notice">
          <strong>등록된 송장이 없습니다</strong>
          <span>송장이 등록되면 여기에서 배송조회 정보를 확인할 수 있습니다.</span>
        </div>
      ) : (
        <div className="customer-shipment-list">
          {order.shipments.map((shipment) => {
            const officialTrackingUrl = officialShipmentHref(shipment.officialTrackingUrl ?? null);
            const allocations = shipment.allocations ?? [];
            return (
              <article className="customer-shipment-card" key={shipment.shipmentId}>
                <div>
                  <strong>{shipment.displayStatus ? shipmentStatusLabel(shipment.displayStatus) : "상태 확인 필요"}</strong>
                  <span>{shipment.carrierName || "택배사 확인 필요"} · {shipment.trackingNumber || "송장번호 확인 필요"}</span>
                </div>
                {allocations.length > 0 ? (
                  <ul>
                    {allocations.map((allocation) => {
                      const item = order.items.find((candidate) => candidate.orderItemId === allocation.orderItemId);
                      return (
                        <li key={allocation.orderItemId}>
                          {item?.productName ?? "주문 상품"} {allocation.quantity}개
                        </li>
                      );
                    })}
                  </ul>
                ) : null}
                {officialTrackingUrl ? (
                  <a className="button" href={officialTrackingUrl} rel="noreferrer" target="_blank">
                    배송조회
                  </a>
                ) : (
                  <span>공식 배송조회 링크를 제공하지 않는 택배사입니다.</span>
                )}
              </article>
            );
          })}
        </div>
      )}
      {order.shipmentAllocationComplete === false ? (
        <p className="field-help">아직 송장이 등록되지 않은 상품이 있습니다.</p>
      ) : null}
    </section>
  );
}

function officialShipmentHref(value: string | null) {
  if (!value) return null;
  try {
    const url = new URL(value);
    return url.protocol === "https:" ? url.toString() : null;
  } catch {
    return null;
  }
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
  const customerDisplay = customerOrderProjection(order);
  const refundAmount = customerDisplay.refundAmount;
  const refundProcessing = customerDisplay.status === "REFUND_PROCESSING";
  return (
    <section className="detail-section">
      <h2>상태</h2>
      <div className="summary-list">
        <div>
          <span>주문 상태</span>
          <strong>{customerDisplay.label}</strong>
        </div>
        <div>
          <span>발주</span>
          <strong>{refundProcessing ? "발주 없음" : fulfillmentStatusLabel(order.fulfillment.status)}</strong>
        </div>
        <div>
          <span>배송</span>
          <strong>{refundProcessing ? "배송 없음" : shipmentStatusLabel(order.shipment.status)}</strong>
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
          <strong>{refundProcessing ? customerDisplay.label : paymentStatusLabel(order.payment.status)}</strong>
        </div>
        <div>
          <span>환불</span>
          <strong>
            {refundProcessing
              ? `${customerDisplay.label}${refundAmount === null ? "" : ` ${formatPrice(refundAmount)}`}`
              : refundAmount === null
              ? refundStatusLabel(order.refund.status)
              : `${refundStatusLabel(order.refund.status)} ${formatPrice(refundAmount)}`}
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

function OrderShippingAddressPanel({ order }: { order: OrderDetail }) {
  const address = order.shippingAddress;

  return (
    <section className="checkout-form">
      <h2>배송지</h2>
      <div className="summary-list">
        <div>
          <span>받는 사람</span>
          <strong>{address.recipientName}</strong>
        </div>
        <div>
          <span>연락처</span>
          <strong>{address.recipientPhone}</strong>
        </div>
        <div>
          <span>주소</span>
          <strong>
            ({address.postalCode}) {address.address1} {address.address2 ?? ""}
          </strong>
        </div>
        <div>
          <span>배송 메모</span>
          <strong>{address.deliveryMemo ?? "없음"}</strong>
        </div>
      </div>
      <div className="notice">
        <strong>주문 정책 확인이 완료된 배송지입니다</strong>
        <span>변경이 필요하면 공급처 발주 전에 고객 문의를 남겨 주세요.</span>
        <Link href="/support">고객 문의</Link>
      </div>
    </section>
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
      <ClaimEvidenceInput />
      <SubmitButton className="button" pendingLabel="접수 중...">
        클레임 접수
      </SubmitButton>
    </form>
  );
}
