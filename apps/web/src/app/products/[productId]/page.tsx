import { notFound } from "next/navigation";
import Link from "next/link";
import { addCartItem } from "@/app/cart/actions";
import { ApiError, apiUrl } from "@/lib/api";
import { categoryLabel } from "@/lib/categories";
import {
  formatPrice,
  getProduct,
  getProducts,
  type ProductDetail,
  type ProductSummary,
} from "@/lib/catalog";
import { getCurrentUser } from "@/lib/session";
import { ProductImage } from "../product-image";

type ProductPageProps = {
  params: Promise<{ productId: string }>;
};

async function loadProduct(productId: string) {
  try {
    return { product: await getProduct(productId), error: false };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound();
    }
    return { product: null, error: true };
  }
}

async function loadRelatedProducts(productId: string) {
  try {
    return (await getProducts()).filter((product) => product.id !== productId).slice(0, 5);
  } catch {
    return [] as ProductSummary[];
  }
}

export default async function ProductDetailPage({ params }: ProductPageProps) {
  const { productId } = await params;
  const [{ product, error }, session, relatedProducts] = await Promise.all([
    loadProduct(productId),
    getCurrentUser(),
    loadRelatedProducts(productId),
  ]);

  if (error || !product) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">Catalog</p>
        <h1>상품을 불러오지 못했습니다</h1>
        <p>백엔드 API 연결 상태를 확인해 주세요.</p>
      </section>
    );
  }

  const activeOptions = product.options.filter((option) => option.status === "ACTIVE");
  const purchasable = product.status === "ACTIVE" && activeOptions.length > 0;
  const galleryImages = product.images.filter((image) => image.type === "GALLERY");

  return (
    <article className="product-detail">
      <nav className="breadcrumb" aria-label="breadcrumb">
        <Link href="/">홈</Link>
        <span>/</span>
        <Link href="/products">상품목록</Link>
        <span>/</span>
        <strong>{product.name}</strong>
      </nav>
      <section className="product-hero">
        <div className="product-gallery">
          <ProductImage
            alt={product.name}
            className="product-hero-image"
            src={product.thumbnailImageUrl}
          />
          <div className="product-thumbnails">
            <ProductImage
              alt={product.name}
              className="product-thumbnail-image"
              src={product.thumbnailImageUrl}
            />
            {galleryImages.slice(0, 5).map((image) => (
              <ProductImage
                alt={image.altText ?? product.name}
                className="product-thumbnail-image"
                key={image.id}
                src={image.imageUrl}
              />
            ))}
          </div>
        </div>
        <div className="product-hero-copy">
          <h1>{product.name}</h1>
          <p>{product.summary}</p>
          <strong className="product-price">{formatPrice(product.basePrice)}</strong>
          <div className="product-buy-info">
            <span>카테고리</span>
            <strong>{categoryLabel(product.categoryCode)}</strong>
            <span>최소 주문</span>
            <strong>옵션별 1개</strong>
            <span>판매 상태</span>
            <strong>{purchasable ? "주문 가능" : "구매 불가"}</strong>
          </div>
          {purchasable ? (
            session ? (
              <form action={addCartItem} className="cart-add-form">
                <label>
                  옵션
                  <select name="productOptionId" required>
                    {activeOptions.map((option) => (
                      <option key={option.id} value={option.id}>
                        {option.name} {formatOptionPrice(option.additionalPrice)}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  수량
                  <input max="99" min="1" name="quantity" type="number" defaultValue="1" />
                </label>
                <div className="product-action-row">
                  <button className="button" name="intent" value="cart" type="submit">
                    장바구니
                  </button>
                  <button className="button primary" name="intent" value="checkout" type="submit">
                    바로구매
                  </button>
                </div>
              </form>
            ) : (
              <div className="notice">
                <strong>로그인이 필요합니다</strong>
                <span>장바구니와 주문은 소셜 로그인 후 이용할 수 있습니다.</span>
                <Link className="button primary" href="/login">
                  로그인
                </Link>
              </div>
            )
          ) : null}
        </div>
      </section>

      {relatedProducts.length > 0 ? (
        <section className="detail-section">
          <div className="catalog-heading">
            <h2>관련 제품</h2>
            <Link href="/products">더보기</Link>
          </div>
          <div className="related-product-row">
            {relatedProducts.map((relatedProduct) => (
              <Link
                className="related-product-card"
                href={`/products/${relatedProduct.id}`}
                key={relatedProduct.id}
              >
                <ProductImage
                  alt={relatedProduct.name}
                  className="related-product-image"
                  src={relatedProduct.thumbnailImageUrl}
                />
                <span>{relatedProduct.name}</span>
                <strong>{formatPrice(relatedProduct.basePrice)}</strong>
              </Link>
            ))}
          </div>
        </section>
      ) : null}

      <section className="detail-section">
        <h2>제품 사양</h2>
        <div className="option-list">
          {product.options.map((option) => (
            <div className="option-row" key={option.id}>
              <span>{option.name}</span>
              <span>{formatOptionPrice(option.additionalPrice)}</span>
              <strong>{option.status === "ACTIVE" ? "선택 가능" : "선택 불가"}</strong>
            </div>
          ))}
        </div>
      </section>

      {product.images.length > 0 ? (
        <section className="detail-section">
          <h2>이미지</h2>
          <div className="gallery-grid">
            {product.images.map((image) => (
              <ProductImage
                alt={image.altText ?? product.name}
                className="gallery-image"
                key={image.id}
                src={image.imageUrl}
              />
            ))}
          </div>
        </section>
      ) : null}

      {product.detailBlocks.length > 0 ? (
        <section className="detail-section">
          <h2>상세</h2>
          <div className="detail-blocks">
            {product.detailBlocks.map((block) => (
              <DetailBlock block={block} key={block.id} product={product} />
            ))}
          </div>
        </section>
      ) : null}

      {product.productNotice ? (
        <section className="detail-section">
          <h2>상품 고시</h2>
          <dl className="notice-list">
            <div>
              <dt>상품 정보</dt>
              <dd>{product.productNotice.productInfoNotice}</dd>
            </div>
            <div>
              <dt>배송</dt>
              <dd>{product.productNotice.shippingInfo}</dd>
            </div>
            <div>
              <dt>AS</dt>
              <dd>{product.productNotice.asInfo}</dd>
            </div>
            <div>
              <dt>반품/교환</dt>
              <dd>{product.productNotice.returnExchangeInfo}</dd>
            </div>
          </dl>
        </section>
      ) : null}

      {product.policyLinks.length > 0 ? (
        <section className="detail-section">
          <h2>정책</h2>
          <div className="policy-links">
            {product.policyLinks.map((policy) => (
              <a href={apiUrl(policy.href)} key={policy.policyType}>
                {policy.label}
              </a>
            ))}
          </div>
        </section>
      ) : null}
    </article>
  );
}

function DetailBlock({
  block,
  product,
}: {
  block: ProductDetail["detailBlocks"][number];
  product: ProductDetail;
}) {
  if (block.type === "HTML" && block.htmlContent) {
    return (
      <div
        className="html-detail"
        dangerouslySetInnerHTML={{ __html: block.htmlContent }}
      />
    );
  }

  return (
    <ProductImage
      alt={block.altText ?? product.name}
      className="detail-image"
      src={block.imageUrl}
    />
  );
}

function formatOptionPrice(value: number) {
  if (value === 0) {
    return "추가금 없음";
  }
  return `+${formatPrice(value)}`;
}
