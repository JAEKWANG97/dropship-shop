import Link from "next/link";
import { redirect } from "next/navigation";
import { ApiError } from "@/lib/api";
import { getCart, type Cart } from "@/lib/cart";
import { formatPrice } from "@/lib/catalog";
import { getAdminUser, getCurrentUser } from "@/lib/session";
import { SubmitButton } from "../submit-button";
import { ProductImage } from "../products/product-image";
import { removeCartItem, updateCartItem, validateCart } from "./actions";

type CartPageProps = {
  searchParams: Promise<{ message?: string }>;
};

async function loadCart() {
  try {
    return { cart: await getCart(), status: "ok" as const };
  } catch (error) {
    if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
      return { cart: null, status: "forbidden" as const };
    }
    return { cart: null, status: "error" as const };
  }
}

export default async function CartPage({ searchParams }: CartPageProps) {
  const [{ cart, status }, session, admin, params] = await Promise.all([
    loadCart(),
    getCurrentUser(),
    getAdminUser(),
    searchParams,
  ]);

  if (!session) {
    redirect("/login?redirectTo=%2Fcart");
  }

  if (status === "forbidden" && admin) {
    return <AdminCustomerFlowNotice eyebrow="Cart" />;
  }

  if (status === "error" || !cart) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">장바구니</p>
        <h1>장바구니를 불러오지 못했습니다</h1>
        <div className="notice danger">
          <strong>API 연결 오류</strong>
          <span>백엔드 API 연결 상태를 확인해 주세요.</span>
        </div>
      </section>
    );
  }

  return (
    <section className="cart-page">
      <div className="section-heading">
        <p className="eyebrow">장바구니</p>
        <h1>장바구니</h1>
        <p>선택하신 상품의 수량을 확인하고 주문 결제를 진행하세요.</p>
      </div>

      {params.message ? (
        <div className="notice">
          <strong>알림</strong>
          <span>{params.message}</span>
        </div>
      ) : null}

      {!cart.salesEnabled ? (
        <div className="notice">
          <strong>판매 준비 중</strong>
          <span>{cart.salesNotice}</span>
        </div>
      ) : null}

      {cart.items.length === 0 ? <EmptyCart /> : <CartContents cart={cart} />}
    </section>
  );
}

function AdminCustomerFlowNotice({ eyebrow }: { eyebrow: string }) {
  return (
    <section className="narrow-page">
      <p className="eyebrow">{eyebrow}</p>
      <h1>관리자 계정은 고객 구매 기능을 사용할 수 없습니다</h1>
      <p>상품 확인은 가능하지만 장바구니와 주문서는 고객 계정으로 이용해 주세요.</p>
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

function EmptyCart() {
  return (
    <div className="notice empty">
      <strong>장바구니가 비어 있습니다</strong>
      <span>상품을 선택해 장바구니에 담아 주세요.</span>
      <Link className="button primary" href="/products">
        상품 보기
      </Link>
    </div>
  );
}

function CartContents({ cart }: { cart: Cart }) {
  const itemIssues = cart.issues.filter((issue) => issue.code !== "SALES_NOT_OPEN");

  return (
    <div className="cart-layout">
      <div>
        <div className="cart-list">
          <div className="cart-list-head">
            <span>전체 선택 ({cart.items.length})</span>
            <span>상품 정보</span>
            <span>수량</span>
            <span>금액</span>
          </div>
          {cart.items.map((item) => (
            <article className="cart-item" key={item.id}>
              <input aria-label={`${item.productName} 선택`} defaultChecked type="checkbox" />
              <ProductImage
                alt={item.productName}
                className="cart-item-image"
                src={item.thumbnailImageUrl}
              />
              <div className="cart-item-main">
                <Link href={`/products/${item.productId}`}>{item.productName}</Link>
                <span>{item.optionName}</span>
                <span>단가 {formatPrice(item.unitPrice)}</span>
                {!item.sellable ? (
                  <strong className="danger-text">
                    {item.unavailableReason ?? "현재 주문할 수 없습니다."}
                  </strong>
                ) : null}
              </div>
              <div className="cart-item-actions">
                <form action={updateCartItem} className="quantity-form">
                  <input name="cartItemId" type="hidden" value={item.id} />
                  <input
                    aria-label={`${item.productName} quantity`}
                    max="99"
                    min="1"
                    name="quantity"
                    type="number"
                    defaultValue={item.quantity}
                  />
                  <SubmitButton className="button" pendingLabel="변경 중...">
                    변경
                  </SubmitButton>
                </form>
                <form action={removeCartItem}>
                  <input name="cartItemId" type="hidden" value={item.id} />
                  <SubmitButton className="button" pendingLabel="삭제 중...">
                    삭제
                  </SubmitButton>
                </form>
                <strong>{formatPrice(item.lineAmount)}</strong>
              </div>
            </article>
          ))}
        </div>

        {itemIssues.length > 0 ? (
          <div className="notice danger">
            <strong>주문 불가 항목</strong>
            {itemIssues.map((issue) => (
              <span key={`${issue.cartItemId}-${issue.code}`}>{issue.message}</span>
            ))}
          </div>
        ) : null}
      </div>

      <aside className="cart-summary">
        <h2>주문 요약</h2>
        <div>
          <span>상품 금액 ({cart.items.length}개)</span>
          <strong>{formatPrice(cart.subtotalAmount)}</strong>
        </div>
        <div>
          <span>배송비</span>
          <strong>0원 (상품 가격에 포함)</strong>
        </div>
        <div>
          <span>구매 조건</span>
          <strong>세금계산서 가능 · 최소주문 상품별 확인</strong>
        </div>
        <div>
          <span>주문 가능</span>
          <strong>{cart.checkoutAvailable ? "가능" : "불가"}</strong>
        </div>
        <form action={validateCart}>
          <SubmitButton className="button" pendingLabel="확인 중...">
            주문 가능 상태 확인
          </SubmitButton>
        </form>
        {cart.checkoutAvailable ? (
          <Link className="button primary" href="/checkout">
            주문서 작성
          </Link>
        ) : null}
      </aside>
    </div>
  );
}
