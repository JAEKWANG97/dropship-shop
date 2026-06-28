import Link from "next/link";
import { ApiError } from "@/lib/api";
import { getCart, type Cart } from "@/lib/cart";
import { formatPrice } from "@/lib/catalog";
import { getCurrentUser } from "@/lib/session";
import { ProductImage } from "../products/product-image";
import { removeCartItem, updateCartItem, validateCart } from "./actions";

type CartPageProps = {
  searchParams: Promise<{ message?: string }>;
};

async function loadCart() {
  try {
    return { cart: await getCart(), error: false };
  } catch (error) {
    if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
      return { cart: null, error: false };
    }
    return { cart: null, error: true };
  }
}

export default async function CartPage({ searchParams }: CartPageProps) {
  const [{ cart, error }, session, params] = await Promise.all([
    loadCart(),
    getCurrentUser(),
    searchParams,
  ]);

  if (!session) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">Cart</p>
        <h1>로그인이 필요합니다</h1>
        <p>장바구니는 소셜 로그인 후 사용할 수 있습니다.</p>
        <Link className="button primary" href="/login">
          로그인
        </Link>
      </section>
    );
  }

  if (error || !cart) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">Cart</p>
        <h1>장바구니를 불러오지 못했습니다</h1>
        <p>백엔드 API 연결 상태를 확인해 주세요.</p>
      </section>
    );
  }

  return (
    <section className="cart-page">
      <div className="section-heading">
        <p className="eyebrow">Cart</p>
        <h1>장바구니</h1>
      </div>

      {params.message ? (
        <div className="notice">
          <strong>알림</strong>
          <span>{params.message}</span>
        </div>
      ) : null}

      {cart.items.length === 0 ? <EmptyCart /> : <CartContents cart={cart} />}
    </section>
  );
}

function EmptyCart() {
  return (
    <div className="notice">
      <strong>장바구니가 비어 있습니다</strong>
      <span>상품을 선택해 장바구니에 담아 주세요.</span>
      <Link className="button primary" href="/products">
        상품 보기
      </Link>
    </div>
  );
}

function CartContents({ cart }: { cart: Cart }) {
  return (
    <>
      <div className="cart-list">
        {cart.items.map((item) => (
          <article className="cart-item" key={item.id}>
            <ProductImage
              alt={item.productName}
              className="cart-item-image"
              src={item.thumbnailImageUrl}
            />
            <div className="cart-item-main">
              <Link href={`/products/${item.productId}`}>{item.productName}</Link>
              <span>{item.optionName}</span>
              <span>{formatPrice(item.unitPrice)}</span>
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
                <button className="button" type="submit">
                  변경
                </button>
              </form>
              <form action={removeCartItem}>
                <input name="cartItemId" type="hidden" value={item.id} />
                <button className="button" type="submit">
                  삭제
                </button>
              </form>
              <strong>{formatPrice(item.lineAmount)}</strong>
            </div>
          </article>
        ))}
      </div>

      {cart.issues.length > 0 ? (
        <div className="notice">
          <strong>주문 불가 항목</strong>
          {cart.issues.map((issue) => (
            <span key={`${issue.cartItemId}-${issue.code}`}>{issue.message}</span>
          ))}
        </div>
      ) : null}

      <aside className="cart-summary">
        <div>
          <span>상품 금액</span>
          <strong>{formatPrice(cart.subtotalAmount)}</strong>
        </div>
        <div>
          <span>배송비</span>
          <strong>0원</strong>
        </div>
        <div>
          <span>주문 가능</span>
          <strong>{cart.checkoutAvailable ? "가능" : "불가"}</strong>
        </div>
        <form action={validateCart}>
          <button className="button" type="submit">
            주문 가능 상태 확인
          </button>
        </form>
        {cart.checkoutAvailable ? (
          <Link className="button primary" href="/checkout">
            주문서 작성
          </Link>
        ) : null}
      </aside>
    </>
  );
}
