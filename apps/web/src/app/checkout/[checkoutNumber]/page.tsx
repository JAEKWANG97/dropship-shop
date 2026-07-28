import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { ApiError } from "@/lib/api";
import { getAgreementState, type AgreementState } from "@/lib/account";
import { getCheckout, type Checkout, type CheckoutOrder } from "@/lib/checkout";
import { formatPrice } from "@/lib/catalog";
import { policyHref } from "@/lib/legal";
import { orderStatusLabel, paymentGroupStatusLabel } from "@/lib/orders";
import { getCurrentUser } from "@/lib/session";
import { AddressFields } from "../../address-fields";
import { SubmitButton } from "../../submit-button";
import {
  confirmCheckoutPolicies,
  updateCheckoutShippingAddress,
} from "../actions";

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
    redirect(`/login?redirectTo=${encodeURIComponent(`/checkout/${checkoutNumber}`)}`);
  }

  const { checkout, agreement, error } = await loadCheckout(checkoutNumber);

  if (error || !checkout || !agreement) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">주문서</p>
        <h1>주문서를 불러오지 못했습니다</h1>
        <div className="notice danger">
          <strong>API 연결 오류</strong>
          <span>백엔드 API 연결 상태를 확인해 주세요.</span>
        </div>
      </section>
    );
  }

  const paymentPending = checkout.status === "PAYMENT_PENDING";
  const policyConfirmed = Boolean(checkout.policyConfirmedAt);

  return (
    <section className="checkout-page">
      <div className="section-heading">
        <p className="eyebrow">{paymentGroupStatusLabel(checkout.status)}</p>
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
      {paymentPending && policyConfirmed ? <BankTransferDepositPanel checkout={checkout} /> : null}
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
          <strong>{paymentGroupStatusLabel(checkout.status)}</strong>
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
          <span>입금 기한</span>
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
          <Link href={policyHref(policy.policyType)} key={policy.policyType}>
            {policy.label}
          </Link>
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
        <span>{orderStatusLabel(order.status)}</span>
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
      <AddressFields />
      <SubmitButton className="button" pendingLabel="변경 중...">
        배송지 변경
      </SubmitButton>
    </form>
  );
}

function CheckoutLockedNotice() {
  return (
    <div className="notice">
      <strong>주문서 수정이 제한됩니다</strong>
      <span>입금대기 상태가 아니므로 배송지 변경을 진행하지 않습니다.</span>
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
        주문 상품, 입금 금액, 배송지, 배송/취소/환불 정책, 품절 시 배송 그룹 주문 금액 환불 안내를
        확인했습니다. 현금영수증은 요청 시 발급됩니다.
      </label>
      <SubmitButton className="button" pendingLabel="저장 중...">
        정책 확인 저장
      </SubmitButton>
    </form>
  );
}

function BankTransferDepositPanel({ checkout }: { checkout: Checkout }) {
  const deposit = checkout.bankTransferDeposit;
  return (
    <section className="checkout-form bank-transfer-panel">
      <h2>계좌입금 안내</h2>
      <p>
        아래 금액을 입금해 주세요. 관리자가 입금 내역을 확인한 뒤 주문이 확정되고 공급처 발주가
        시작됩니다.
      </p>
      <div className="summary-list">
        <div>
          <span>은행</span>
          <strong>{deposit.bankName}</strong>
        </div>
        <div>
          <span>계좌번호</span>
          <strong>{deposit.accountNumber}</strong>
        </div>
        <div>
          <span>예금주</span>
          <strong>{deposit.accountHolder}</strong>
        </div>
        <div>
          <span>입금자명</span>
          <strong>{deposit.depositorName}</strong>
        </div>
        <div>
          <span>입금 금액</span>
          <strong>{formatPrice(deposit.amount)}</strong>
        </div>
        <div>
          <span>입금 기한</span>
          <strong>{new Date(deposit.deadline).toLocaleString("ko-KR")}</strong>
        </div>
      </div>
      <div className="notice">
        <strong>입금 확인 전에는 주문이 확정되지 않습니다</strong>
        <span>입금자명이나 금액이 다르면 고객 문의로 확인이 필요할 수 있습니다.</span>
        <span>{deposit.cashReceiptNotice}</span>
      </div>
    </section>
  );
}
