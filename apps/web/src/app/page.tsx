import Image from "next/image";
import Link from "next/link";
import { FEATURED_CATEGORIES, categoryLabel } from "@/lib/categories";
import { formatPrice, getProducts, type ProductSummary } from "@/lib/catalog";
import { ProductImage } from "./products/product-image";

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
          <p className="eyebrow">현장 안전용품을 한곳에서</p>
          <h1>
            현장 안전용품
            <span>필요한 수량만 선택해 주문</span>
          </h1>
          <p>
            안전모, 안전화, 조끼, 장갑까지 현장에서 자주 쓰는 품목을 빠르게 찾고
            주문하세요.
          </p>
          <div className="action-row">
            <Link className="button primary" href="/products">
              상품 보러가기
            </Link>
            <Link className="button accent" href="/products">
              바로 구매하기
            </Link>
          </div>
          <Image
            alt="현장 준비대에 놓인 안전용품"
            className="hero-context-image"
            height={600}
            priority
            src="/images/hero-worksite-prep.png"
            width={1200}
          />
        </div>
        <div className="hero-visual" aria-label="대표 안전용품">
          {products.slice(0, 4).map((product) => (
            <Link className="hero-product-tile" href={`/products/${product.id}`} key={product.id}>
              <ProductImage
                alt={product.name}
                className="hero-product-image"
                src={product.thumbnailImageUrl}
              />
              <span>{product.name}</span>
              <strong>{formatPrice(product.basePrice)}</strong>
            </Link>
          ))}
        </div>
      </section>

      <section className="category-strip" aria-label="대표 카테고리">
        {FEATURED_CATEGORIES.map((category) => (
          <Link href={`/products?category=${encodeURIComponent(category)}`} key={category}>
            {categoryLabel(category)}
          </Link>
        ))}
        <Link href="/products">전체 보기</Link>
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
                  <strong className="product-card-price">{formatPrice(product.basePrice)}</strong>
                </div>
                <span className="product-card-cta">상세 보기</span>
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

    </div>
  );
}
