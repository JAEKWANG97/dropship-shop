import { notFound } from "next/navigation";
import { ApiError, apiUrl } from "@/lib/api";
import { formatPrice, getProduct, type ProductDetail } from "@/lib/catalog";
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

export default async function ProductDetailPage({ params }: ProductPageProps) {
  const { productId } = await params;
  const { product, error } = await loadProduct(productId);

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

  return (
    <article className="product-detail">
      <section className="product-hero">
        <ProductImage
          alt={product.name}
          className="product-hero-image"
          src={product.thumbnailImageUrl}
        />
        <div className="product-hero-copy">
          <p className="eyebrow">{purchasable ? "Available" : "Unavailable"}</p>
          <h1>{product.name}</h1>
          <p>{product.summary}</p>
          <strong className="product-price">{formatPrice(product.basePrice)}</strong>
          <span className={purchasable ? "status-pill success" : "status-pill"}>
            {purchasable ? "구매 가능" : "구매 불가"}
          </span>
        </div>
      </section>

      <section className="detail-section">
        <h2>옵션</h2>
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
