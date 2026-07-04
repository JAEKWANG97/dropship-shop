import { notFound } from "next/navigation";
import Link from "next/link";
import { addCartItem } from "@/app/cart/actions";
import { ApiError } from "@/lib/api";
import { categoryLabel } from "@/lib/categories";
import {
  formatPrice,
  getProduct,
  getProducts,
  type ProductDetail,
  type ProductSummary,
} from "@/lib/catalog";
import { POLICY_PAGES, policyHref, type PolicyPage } from "@/lib/legal";
import { SubmitButton } from "@/app/submit-button";
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
  const [{ product, error }, relatedProducts] = await Promise.all([
    loadProduct(productId),
    loadRelatedProducts(productId),
  ]);

  if (error || !product) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">상품</p>
        <h1>상품을 불러오지 못했습니다</h1>
        <div className="notice danger">
          <strong>API 연결 오류</strong>
          <span>백엔드 API 연결 상태를 확인해 주세요.</span>
        </div>
      </section>
    );
  }

  const activeOptions = product.options.filter((option) => option.status === "ACTIVE");
  const purchasable = product.status === "ACTIVE" && activeOptions.length > 0;
  const galleryImages = product.images.filter((image) => image.type === "GALLERY");
  const policyPages = product.policyLinks
    .map((policy) => policyPageForType(policy.policyType))
    .filter((policy): policy is PolicyPage => policy !== null);
  const purchaseFormId = `product-purchase-form-${product.id}`;

  return (
    <article className={purchasable ? "product-detail has-mobile-purchase-bar" : "product-detail"}>
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
          <div className="product-purchase-panel">
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
              <form action={addCartItem} className="cart-add-form" id={purchaseFormId}>
                <input name="productId" type="hidden" value={product.id} />
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
                  <SubmitButton
                    className="button"
                    name="intent"
                    pendingLabel="담는 중..."
                    value="cart"
                  >
                    장바구니
                  </SubmitButton>
                  <SubmitButton
                    className="button primary"
                    name="intent"
                    pendingLabel="이동 중..."
                    value="checkout"
                  >
                    바로구매
                  </SubmitButton>
                </div>
              </form>
            ) : (
              <div className="notice empty">
                <strong>현재 구매할 수 없습니다</strong>
                <span>판매 상태 또는 선택 가능한 옵션을 확인해 주세요.</span>
              </div>
            )}
          </div>
        </div>
      </section>

      {purchasable ? (
        <div className="mobile-purchase-bar" aria-label="모바일 구매 액션">
          <SubmitButton
            className="button mobile-purchase-secondary"
            form={purchaseFormId}
            name="intent"
            pendingLabel="담는 중..."
            value="cart"
          >
            장바구니 담기
          </SubmitButton>
          <SubmitButton
            className="button primary mobile-purchase-primary"
            form={purchaseFormId}
            name="intent"
            pendingLabel="이동 중..."
            value="checkout"
          >
            바로구매
          </SubmitButton>
        </div>
      ) : null}

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

      {policyPages.length > 0 ? (
        <section className="detail-section">
          <h2>배송/교환/환불 안내</h2>
          <div className="product-policy-board">
            {policyPages.map((policy) => (
              <section className="product-policy-card" key={policy.slug}>
                <h3>{policy.title}</h3>
                <dl className="product-policy-table">
                  <div>
                    <dt>요약</dt>
                    <dd>{policy.summary}</dd>
                  </div>
                  {policy.sections.map((section) => (
                    <div key={section.heading}>
                      <dt>{section.heading}</dt>
                      <dd>
                        <ul>
                          {section.paragraphs.map((paragraph) => (
                            <li key={paragraph}>{paragraph}</li>
                          ))}
                        </ul>
                      </dd>
                    </div>
                  ))}
                </dl>
                <Link className="policy-detail-link" href={`/policies/${policy.slug}`}>
                  상세 정책 보기
                </Link>
              </section>
            ))}
          </div>
        </section>
      ) : null}
    </article>
  );
}

function policyPageForType(policyType: string) {
  const href = policyHref(policyType);
  return POLICY_PAGES.find((policy) => `/policies/${policy.slug}` === href) ?? null;
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
