import Link from "next/link";
import { FEATURED_CATEGORIES, PRODUCT_CATEGORIES, categoryLabel } from "@/lib/categories";
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
  const groups = [...new Set(PRODUCT_CATEGORIES.map((category) => category[0]))];

  return (
    <div className="home-page">
      <section className="home-hero">
        <div className="home-copy">
          <p className="eyebrow">건설 안전용품 쇼핑몰</p>
          <h1>
            필요한 안전용품을
            <span>바로 찾고 주문</span>
          </h1>
          <p>
            안전모, 안전화, 보호구, 추락방지 장비까지 현장에서 자주 쓰는 품목을
            빠르게 확인하세요.
          </p>
          <div className="action-row">
            <Link className="button primary" href="/products">
              상품 보러가기
            </Link>
          </div>
        </div>
        <form action="/products" className="home-category-form">
          <label htmlFor="home-category-group">필요한 품목 찾기</label>
          <div>
            <select id="home-category-group" name="group" defaultValue={groups[0]}>
              {groups.map((group) => (
                <option key={group} value={group}>
                  {group}
                </option>
              ))}
            </select>
            <button className="button" type="submit">
              상품 보기
            </button>
          </div>
          <nav className="home-category-chips" aria-label="자주 찾는 품목">
            <span>자주 찾는 품목</span>
            {FEATURED_CATEGORIES.map((category) => (
              <Link href={`/products?category=${encodeURIComponent(category)}`} key={category}>
                {categoryLabel(category)}
              </Link>
            ))}
            <Link href="/products">전체 보기</Link>
          </nav>
        </form>
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
