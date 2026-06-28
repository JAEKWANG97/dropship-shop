import Link from "next/link";
import { ApiError } from "@/lib/api";
import { getAddresses, getAgreementState, type Address } from "@/lib/account";
import { getCart, type Cart } from "@/lib/cart";
import { formatPrice } from "@/lib/catalog";
import { getCurrentUser } from "@/lib/session";
import { agreeRequiredPolicies, createCheckout } from "./actions";

type CheckoutPageProps = {
  searchParams: Promise<{ message?: string }>;
};

async function loadCheckoutStart() {
  try {
    const [agreement, cart, addresses] = await Promise.all([
      getAgreementState(),
      getCart(),
      getAddresses(),
    ]);
    return { agreement, cart, addresses: addresses.addresses, error: false };
  } catch (error) {
    if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
      return { agreement: null, cart: null, addresses: [] as Address[], error: false };
    }
    return { agreement: null, cart: null, addresses: [] as Address[], error: true };
  }
}

export default async function CheckoutPage({ searchParams }: CheckoutPageProps) {
  const [session, data, params] = await Promise.all([
    getCurrentUser(),
    loadCheckoutStart(),
    searchParams,
  ]);

  if (!session) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">Checkout</p>
        <h1>로그인이 필요합니다</h1>
        <p>주문서는 소셜 로그인 후 생성할 수 있습니다.</p>
        <Link className="button primary" href="/login">
          로그인
        </Link>
      </section>
    );
  }

  if (data.error || !data.agreement || !data.cart) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">Checkout</p>
        <h1>주문서를 시작하지 못했습니다</h1>
        <p>백엔드 API 연결 상태를 확인해 주세요.</p>
      </section>
    );
  }

  const defaultAddress = data.addresses.find((address) => address.defaultAddress) ?? data.addresses[0];

  return (
    <section className="checkout-page">
      <div className="section-heading">
        <p className="eyebrow">Checkout</p>
        <h1>주문 결제</h1>
        <p>주문자, 배송지, 결제수단과 필수 정책을 확인한 뒤 결제를 진행합니다.</p>
      </div>

      {params.message ? (
        <div className="notice">
          <strong>알림</strong>
          <span>{params.message}</span>
        </div>
      ) : null}

      {data.cart.items.length === 0 ? (
        <div className="notice">
          <strong>장바구니가 비어 있습니다</strong>
          <Link className="button primary" href="/products">
            상품 보기
          </Link>
        </div>
      ) : (
        <>
          {!data.agreement.requiredAgreed ? (
            <RequiredAgreementForm
              privacyVersion={data.agreement.requiredPrivacyVersion}
              termsVersion={data.agreement.requiredTermsVersion}
            />
          ) : null}
          <div className="checkout-layout">
            <CreateCheckoutForm
              cart={data.cart}
              defaultAddress={defaultAddress}
              disabled={!data.agreement.requiredAgreed || !data.cart.checkoutAvailable}
            />
            <CheckoutCartSummary cart={data.cart} />
          </div>
        </>
      )}
    </section>
  );
}

function RequiredAgreementForm({
  termsVersion,
  privacyVersion,
}: {
  termsVersion: string;
  privacyVersion: string;
}) {
  return (
    <form action={agreeRequiredPolicies} className="checkout-form">
      <h2>필수 약관 동의</h2>
      <input name="termsVersion" type="hidden" value={termsVersion} />
      <input name="privacyVersion" type="hidden" value={privacyVersion} />
      <label className="checkbox-row">
        <input name="termsAgreed" type="checkbox" required />
        이용약관 {termsVersion}에 동의합니다.
      </label>
      <label className="checkbox-row">
        <input name="privacyAgreed" type="checkbox" required />
        개인정보처리방침 {privacyVersion}에 동의합니다.
      </label>
      <button className="button" type="submit">
        필수 약관 동의 저장
      </button>
    </form>
  );
}

function CheckoutCartSummary({ cart }: { cart: Cart }) {
  return (
    <aside className="checkout-summary-card">
      <div className="catalog-heading">
        <h2>주문 상품</h2>
        <span>{cart.items.length}개 상품</span>
      </div>
      <div className="summary-list">
        {cart.items.map((item) => (
          <div key={item.id}>
            <span>
              {item.productName} / {item.optionName} x {item.quantity}
            </span>
            <strong>{formatPrice(item.lineAmount)}</strong>
          </div>
        ))}
        <div>
          <span>배송비</span>
          <strong>0원 (상품 가격에 포함)</strong>
        </div>
        <div>
          <span>구매 조건</span>
          <strong>세금계산서 가능 · 최소주문 상품별 확인</strong>
        </div>
        <div>
          <span>최종 결제금액</span>
          <strong>{formatPrice(cart.subtotalAmount)}</strong>
        </div>
      </div>
      {!cart.checkoutAvailable ? (
        <div className="notice">
          <strong>주문 불가</strong>
          {cart.issues.map((issue) => (
            <span key={`${issue.cartItemId}-${issue.code}`}>{issue.message}</span>
          ))}
        </div>
      ) : null}
    </aside>
  );
}

function CreateCheckoutForm({
  cart,
  defaultAddress,
  disabled,
}: {
  cart: Cart;
  defaultAddress: Address | undefined;
  disabled: boolean;
}) {
  return (
    <form action={createCheckout} className="checkout-form checkout-main-form">
      <h2>주문자 / 배송지</h2>
      <input name="clientSubmittedTotalAmount" type="hidden" value={cart.subtotalAmount} />
      <label>
        받는 사람
        <input
          name="recipientName"
          required
          defaultValue={defaultAddress?.recipientName ?? ""}
        />
      </label>
      <label>
        연락처
        <input
          name="recipientPhone"
          required
          defaultValue={defaultAddress?.recipientPhone ?? ""}
        />
      </label>
      <label>
        우편번호
        <input name="postalCode" required defaultValue={defaultAddress?.postalCode ?? ""} />
      </label>
      <label>
        주소
        <input name="address1" required defaultValue={defaultAddress?.address1 ?? ""} />
      </label>
      <label>
        상세 주소
        <input name="address2" defaultValue={defaultAddress?.address2 ?? ""} />
      </label>
      <button className="button primary" disabled={disabled} type="submit">
        주문 결제하기
      </button>
    </form>
  );
}
