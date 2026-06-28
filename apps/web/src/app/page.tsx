import Link from "next/link";
import { formatPrice, getProducts, type ProductSummary } from "@/lib/catalog";
import { ProductImage } from "./products/product-image";

const categories = ["안전모", "안전화", "형광조끼", "안전장갑", "추락방지", "보안경"];

async function loadProducts() {
  try {
    return (await getProducts()).slice(0, 6);
  } catch {
    return [] as ProductSummary[];
  }
}

export default async function Home() {
  const products = await loadProducts();

  return (
    <div className="home-page">
      <section className="home-hero">
        <div className="home-copy">
          <p className="eyebrow">건설현장의 안전, 빠르게 확실하게!</p>
          <h1>
            건설안전용품 도매 쇼핑몰
            <span>바로 구매, 즉시 결제!</span>
          </h1>
          <p>
            사업자 전용가로 합리적으로, 빠른 배송으로 현장에서 바로 사용할 수 있게
            준비합니다.
          </p>
          <div className="hero-benefits">
            <span>사업자 전용가</span>
            <span>빠른 배송</span>
            <span>세금계산서 가능</span>
            <span>즉시 결제</span>
          </div>
          <div className="action-row">
            <Link className="button primary" href="/products">
              상품 보러가기
            </Link>
            <Link className="button accent" href="/products">
              바로 구매하기
            </Link>
          </div>
        </div>
        <div className="hero-visual" aria-label="대표 안전용품">
          {products.slice(0, 4).map((product) => (
            <ProductImage
              alt={product.name}
              className="hero-product-image"
              key={product.id}
              src={product.thumbnailImageUrl}
            />
          ))}
        </div>
      </section>

      <section className="category-strip" aria-label="대표 카테고리">
        {categories.map((category) => (
          <Link href={`/products?q=${encodeURIComponent(category)}`} key={category}>
            {category}
          </Link>
        ))}
      </section>

      <section className="home-products">
        <div className="catalog-heading">
          <div className="section-heading">
            <h2>추천 상품</h2>
          </div>
          <Link href="/products">더보기</Link>
        </div>
        {products.length > 0 ? (
          <div className="product-grid featured">
            {products.map((product) => (
              <Link className="product-card" href={`/products/${product.id}`} key={product.id}>
                <ProductImage
                  alt={product.name}
                  className="product-card-image"
                  src={product.thumbnailImageUrl}
                />
                <div className="product-card-copy">
                  <span className="product-card-name">{product.name}</span>
                  <strong>{formatPrice(product.basePrice)}</strong>
                  <span className="product-card-summary">
                    {product.status === "ACTIVE" ? "주문 가능" : "상태 확인 필요"}
                  </span>
                </div>
                <span className="product-card-cta">바로구매</span>
              </Link>
            ))}
          </div>
        ) : (
          <div className="notice">
            <strong>추천 상품을 불러오지 못했습니다</strong>
            <span>백엔드 API 연결 상태를 확인해 주세요.</span>
          </div>
        )}
      </section>

      <section className="status-panel" aria-label="service benefits">
        <div>
          <span className="panel-label">사업자 전용가</span>
          <strong>배송비 포함 가격으로 바로 주문</strong>
        </div>
        <div>
          <span className="panel-label">빠른 배송</span>
          <strong>결제 완료 후 공급처 출고 진행</strong>
        </div>
        <div>
          <span className="panel-label">세금계산서 가능</span>
          <strong>정책 문서 기준으로 거래 증빙 제공</strong>
        </div>
        <div>
          <span className="panel-label">간편 결제</span>
          <strong>카드, 계좌이체, 간편결제 지원</strong>
        </div>
      </section>
    </div>
  );
}
