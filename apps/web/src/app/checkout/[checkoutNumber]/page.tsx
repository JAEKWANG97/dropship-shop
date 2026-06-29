import Link from "next/link";
import { notFound } from "next/navigation";
import { ApiError, apiUrl } from "@/lib/api";
import { getAgreementState, type AgreementState } from "@/lib/account";
import { getCheckout, type Checkout, type CheckoutOrder } from "@/lib/checkout";
import { formatPrice } from "@/lib/catalog";
import { getCurrentUser } from "@/lib/session";
import {
  confirmCheckoutPolicies,
  updateCheckoutShippingAddress,
} from "../actions";
import { TossPaymentLauncher } from "./toss-payment-launcher";

type CheckoutDetailPageProps = {
  params: Promise<{ checkoutNumber: string }>;
  searchParams: Promise<{ message?: string }>;
};

async function loadCheckout(checkoutNumber: string) {
  try {
    const [checkout, agreement] = await Promise.all([
      getCheckout(checkoutNumber),
      getAgreementState(),
    ]);
    return { checkout, agreement, error: false };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound();
    }
    return { checkout: null, agreement: null, error: true };
  }
}

export default async function CheckoutDetailPage({
  params,
  searchParams,
}: CheckoutDetailPageProps) {
  const [{ checkoutNumber }, query, session] = await Promise.all([
    params,
    searchParams,
    getCurrentUser(),
  ]);

  if (!session) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">Checkout</p>
        <h1>로그인이 필요합니다</h1>
        <Link className="button primary" href="/login">
          로그인
        </Link>
      </section>
    );
  }

  const { checkout, agreement, error } = await loadCheckout(checkoutNumber);

  if (error || !checkout || !agreement) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">Checkout</p>
        <h1>주문서를 불러오지 못했습니다</h1>
        <p>백엔드 API 연결 상태를 확인해 주세요.</p>
      </section>
    );
  }

  const paymentPending = checkout.status === "PAYMENT_PENDING";
  const policyConfirmed = Boolean(checkout.policyConfirmedAt);

  return (
    <section className="checkout-page">
      <div className="section-heading">
        <p className="eyebrow">{checkout.status}</p>
        <h1>주문서 {checkout.checkoutNumber}</h1>
      </div>

      {query.message ? (
        <div className="notice">
          <strong>알림</strong>
          <span>{query.message}</span>
        </div>
      ) : null}

      <CheckoutSummary checkout={checkout} />
      {paymentPending ? <ShippingAddressForm checkout={checkout} /> : <CheckoutLockedNotice />}
      {paymentPending && !policyConfirmed ? (
        <PolicyConfirmationForm agreement={agreement} checkout={checkout} />
      ) : null}
      {paymentPending && policyConfirmed ? <TossPaymentForm checkout={checkout} /> : null}
    </section>
  );
}

function CheckoutSummary({ checkout }: { checkout: Checkout }) {
  return (
    <section className="detail-section">
      <h2>결제 그룹</h2>
      <div className="summary-list">
        <div>
          <span>상태</span>
          <strong>{checkout.status}</strong>
        </div>
        <div>
          <span>결제 금액</span>
          <strong>{formatPrice(checkout.totalAmount)}</strong>
        </div>
        <div>
          <span>환불 가능 금액</span>
          <strong>{formatPrice(checkout.refundableAmount)}</strong>
        </div>
        <div>
          <span>만료 시각</span>
          <strong>{new Date(checkout.expiresAt).toLocaleString("ko-KR")}</strong>
        </div>
        <div>
          <span>정책 확인</span>
          <strong>
            {checkout.policyConfirmedAt
              ? new Date(checkout.policyConfirmedAt).toLocaleString("ko-KR")
              : "미확인"}
          </strong>
        </div>
      </div>

      <div className="policy-links">
        {checkout.policyLinks.map((policy) => (
          <a href={apiUrl(policy.href)} key={policy.policyType}>
            {policy.label}
          </a>
        ))}
      </div>

      <div className="order-group-list">
        {checkout.orders.map((order) => (
          <CheckoutOrderCard key={order.id} order={order} />
        ))}
      </div>
    </section>
  );
}

function CheckoutOrderCard({ order }: { order: CheckoutOrder }) {
  return (
    <article className="order-card">
      <div>
        <strong>{order.deliveryGroupName}</strong>
        <span>{order.status}</span>
      </div>
      <div className="summary-list compact">
        {order.items.map((item) => (
          <div key={item.id}>
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
    </article>
  );
}

function ShippingAddressForm({ checkout }: { checkout: Checkout }) {
  return (
    <form action={updateCheckoutShippingAddress} className="checkout-form">
      <h2>배송지 변경</h2>
      <input name="checkoutNumber" type="hidden" value={checkout.checkoutNumber} />
      <label>
        받는 사람
        <input name="recipientName" required />
      </label>
      <label>
        연락처
        <input name="recipientPhone" required />
      </label>
      <label>
        우편번호
        <input name="postalCode" required />
      </label>
      <label>
        주소
        <input name="address1" required />
      </label>
      <label>
        상세 주소
        <input name="address2" />
      </label>
      <button className="button" type="submit">
        배송지 변경
      </button>
    </form>
  );
}

function CheckoutLockedNotice() {
  return (
    <div className="notice">
      <strong>주문서 수정이 제한됩니다</strong>
      <span>결제 대기 상태가 아니므로 배송지 변경이나 결제 승인 재시도를 진행하지 않습니다.</span>
    </div>
  );
}

function PolicyConfirmationForm({
  checkout,
  agreement,
}: {
  checkout: Checkout;
  agreement: AgreementState;
}) {
  return (
    <form action={confirmCheckoutPolicies} className="checkout-form">
      <h2>주문 정책 확인</h2>
      <input name="checkoutNumber" type="hidden" value={checkout.checkoutNumber} />
      <input name="termsVersion" type="hidden" value={agreement.requiredTermsVersion} />
      <input name="privacyVersion" type="hidden" value={agreement.requiredPrivacyVersion} />
      <label className="checkbox-row">
        <input name="policyConfirmed" type="checkbox" required />
        주문 상품, 결제 금액, 배송지, 배송/취소/환불 정책, 품절 시 배송 그룹 주문 금액 환불 안내를
        확인했습니다.
      </label>
      <button className="button" type="submit">
        정책 확인 저장
      </button>
    </form>
  );
}

function TossPaymentForm({ checkout }: { checkout: Checkout }) {
  const firstItem = checkout.orders[0]?.items[0];
  const itemCount = checkout.orders.reduce((sum, order) => sum + order.items.length, 0);
  const orderName = firstItem
    ? `${firstItem.productName}${itemCount > 1 ? ` 외 ${itemCount - 1}건` : ""}`
    : `주문 ${checkout.checkoutNumber}`;

  return (
    <TossPaymentLauncher
      amount={checkout.totalAmount}
      checkoutNumber={checkout.checkoutNumber}
      clientKey={process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY ?? ""}
      orderName={orderName}
    />
  );
}
