import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { ApiError } from "@/lib/api";
import {
  checkoutCustomerProjection,
  getCheckout,
  type Checkout,
  type CheckoutOrder,
} from "@/lib/checkout";
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
    return { checkout: await getCheckout(checkoutNumber), error: false };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound();
    }
    return { checkout: null, error: true };
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

  const { checkout, error } = await loadCheckout(checkoutNumber);

  if (error || !checkout) {
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

  const customerDisplay = checkoutCustomerProjection(checkout);
  const refundProcessing = customerDisplay.status === "REFUND_PROCESSING";
  const paymentPending = checkout.status === "PAYMENT_PENDING" && !refundProcessing;
  const policyConfirmed = Boolean(checkout.policyConfirmedAt);

  return (
    <section className="checkout-page">
      <div className="section-heading">
        <p className="eyebrow">{customerDisplay.label || paymentGroupStatusLabel(checkout.status)}</p>
        <h1>주문서 {checkout.checkoutNumber}</h1>
      </div>

      {query.message ? (
        <div className="notice">
          <strong>알림</strong>
          <span>{query.message}</span>
        </div>
      ) : null}

      {refundProcessing ? (
        <div className="notice">
          <strong>{customerDisplay.label || "입금 확인 및 환불 처리 중"}</strong>
          <span>
            환불 예정 금액 {formatPrice(customerDisplay.refundAmount)}
          </span>
        </div>
      ) : null}

      <CheckoutSummary checkout={checkout} />
      <ShippingAddressSection checkout={checkout} editable={paymentPending && !policyConfirmed} />
      {paymentPending && !policyConfirmed ? (
        <PolicyConfirmationForm checkout={checkout} />
      ) : null}
      {paymentPending && policyConfirmed ? <BankTransferDepositPanel checkout={checkout} /> : null}
    </section>
  );
}

function CheckoutSummary({ checkout }: { checkout: Checkout }) {
  const displayStatus = checkoutCustomerProjection(checkout).label || paymentGroupStatusLabel(checkout.status);
  return (
    <section className="detail-section">
      <h2>결제 그룹</h2>
      <div className="summary-list">
        <div>
          <span>상태</span>
          <strong>{displayStatus}</strong>
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

function ShippingAddressSection({ checkout, editable }: { checkout: Checkout; editable: boolean }) {
  const address = checkout.shippingAddress;

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
      {editable ? (
        <form action={updateCheckoutShippingAddress} className="form-stack">
          <input name="checkoutNumber" type="hidden" value={checkout.checkoutNumber} />
          <label>
            받는 사람
            <input name="recipientName" required defaultValue={address.recipientName} />
          </label>
          <label>
            연락처
            <input name="recipientPhone" required defaultValue={address.recipientPhone} />
          </label>
          <AddressFields
            postalCode={address.postalCode}
            address1={address.address1}
            address2={address.address2 ?? undefined}
          />
          <label>
            배송 메모
            <textarea defaultValue={address.deliveryMemo ?? ""} maxLength={300} name="deliveryMemo" placeholder="예: 문 앞에 놓아 주세요" rows={3} />
          </label>
          <SubmitButton className="button" pendingLabel="변경 중...">
            배송지 변경
          </SubmitButton>
        </form>
      ) : (
        <CheckoutLockedNotice />
      )}
    </section>
  );
}

function CheckoutLockedNotice() {
  return (
    <div className="notice">
      <strong>주문서 수정이 제한됩니다</strong>
      <span>주문 정책 확인이 완료된 배송지는 고객 문의를 통해서만 변경할 수 있습니다.</span>
    </div>
  );
}

function PolicyConfirmationForm({
  checkout,
}: {
  checkout: Checkout;
}) {
  const evidence = checkout.policyEvidence;
  return (
    <form action={confirmCheckoutPolicies} className="checkout-form">
      <h2>주문 정책 확인</h2>
      <input name="checkoutNumber" type="hidden" value={checkout.checkoutNumber} />
      <input name="termsVersion" type="hidden" value={evidence.termsVersion} />
      <input name="privacyVersion" type="hidden" value={evidence.privacyVersion} />
      <input name="orderPolicyVersion" type="hidden" value={evidence.orderPolicyVersion} />
      <input
        name="cancellationRefundPolicyVersion"
        type="hidden"
        value={evidence.cancellationRefundPolicyVersion}
      />
      <input
        name="outOfStockNoticeVersion"
        type="hidden"
        value={evidence.outOfStockNoticeVersion}
      />
      <label className="checkbox-row">
        <input name="policyConfirmed" type="checkbox" required />
        {evidence.confirmedNoticeText}
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
