import Link from "next/link";
import { redirect } from "next/navigation";
import { ApiError } from "@/lib/api";
import { getAddresses, getAgreementState, type Address } from "@/lib/account";
import { getCart, type Cart } from "@/lib/cart";
import { formatPrice } from "@/lib/catalog";
import { getAdminUser, getCurrentUser } from "@/lib/session";
import { AddressFields } from "../address-fields";
import { SubmitButton } from "../submit-button";
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
    return { agreement, cart, addresses: addresses.addresses, status: "ok" as const };
  } catch (error) {
    if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
      return { agreement: null, cart: null, addresses: [] as Address[], status: "forbidden" as const };
    }
    return { agreement: null, cart: null, addresses: [] as Address[], status: "error" as const };
  }
}

export default async function CheckoutPage({ searchParams }: CheckoutPageProps) {
  const [session, admin, data, params] = await Promise.all([
    getCurrentUser(),
    getAdminUser(),
    loadCheckoutStart(),
    searchParams,
  ]);

  if (!session) {
    redirect("/login?redirectTo=%2Fcheckout");
  }

  if (data.status === "forbidden" && admin) {
    return <AdminCustomerFlowNotice />;
  }

  if (data.status === "error" || !data.agreement || !data.cart) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">주문서</p>
        <h1>주문서를 시작하지 못했습니다</h1>
        <div className="notice danger">
          <strong>API 연결 오류</strong>
          <span>백엔드 API 연결 상태를 확인해 주세요.</span>
        </div>
      </section>
    );
  }

  const defaultAddress = data.addresses.find((address) => address.defaultAddress) ?? data.addresses[0];

  return (
    <section className="checkout-page">
      <div className="section-heading">
        <p className="eyebrow">주문서</p>
        <h1>주문 결제</h1>
        <p>주문자, 배송지, 결제수단과 필수 정책을 확인한 뒤 결제를 진행합니다.</p>
      </div>

      {params.message ? (
        <div className="notice">
          <strong>알림</strong>
          <span>{params.message}</span>
        </div>
      ) : null}

      {!data.cart.salesEnabled ? (
        <div className="notice">
          <strong>판매 준비 중</strong>
          <span>{data.cart.salesNotice}</span>
        </div>
      ) : null}

      {data.cart.items.length === 0 ? (
        <div className="notice empty">
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

function AdminCustomerFlowNotice() {
  return (
    <section className="narrow-page">
      <p className="eyebrow">주문서</p>
      <h1>관리자 계정은 고객 구매 기능을 사용할 수 없습니다</h1>
      <p>주문서는 고객 계정으로 생성해 주세요. 운영 처리는 관리자 화면에서 계속할 수 있습니다.</p>
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
        이용약관에 동의합니다.
      </label>
      <label className="checkbox-row">
        <input name="privacyAgreed" type="checkbox" required />
        개인정보처리방침에 동의합니다.
      </label>
      <SubmitButton className="button" pendingLabel="저장 중...">
        필수 약관 동의 저장
      </SubmitButton>
    </form>
  );
}

function CheckoutCartSummary({ cart }: { cart: Cart }) {
  const itemIssues = cart.issues.filter((issue) => issue.code !== "SALES_NOT_OPEN");

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
          <strong>최소 주문 수량은 상품별로 확인</strong>
        </div>
        <div>
          <span>최종 결제금액</span>
          <strong>{formatPrice(cart.subtotalAmount)}</strong>
        </div>
      </div>
      {!cart.checkoutAvailable ? (
        <div className="notice danger">
          <strong>주문 불가</strong>
          {itemIssues.map((issue) => (
            <span key={`${issue.cartItemId}-${issue.code}`}>{issue.message}</span>
          ))}
          {!cart.salesEnabled ? <span>{cart.salesNotice}</span> : null}
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
      <AddressFields
        postalCode={defaultAddress?.postalCode}
        address1={defaultAddress?.address1}
        address2={defaultAddress?.address2 ?? undefined}
      />
      <label>
        입금자명
        <input name="depositorName" placeholder="비워두면 받는 사람 이름으로 안내됩니다" />
      </label>
      <SubmitButton className="button primary" disabled={disabled} pendingLabel="주문서 생성 중...">
        주문서 만들기
      </SubmitButton>
    </form>
  );
}
