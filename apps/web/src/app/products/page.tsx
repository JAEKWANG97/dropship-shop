import Link from "next/link";
import { PRODUCT_CATEGORIES, categoryLabel } from "@/lib/categories";
import { formatPrice, getProducts, type ProductSummary } from "@/lib/catalog";
import { ProductImage } from "./product-image";

type ProductsPageProps = {
  searchParams: Promise<ProductSearchParams>;
};

type ProductSearchParams = {
  category?: string;
  group?: string;
  maxPrice?: string;
  minPrice?: string;
  q?: string;
  sort?: string;
};

async function loadProducts() {
  try {
    return { products: await getProducts(), error: false };
  } catch {
    return { products: [] as ProductSummary[], error: true };
  }
}

export default async function ProductsPage({ searchParams }: ProductsPageProps) {
  const [{ products, error }, params] = await Promise.all([loadProducts(), searchParams]);
  const filteredProducts = filterProducts(products, params);
  const groups = categoryGroups();
  const activeGroup = selectedGroup(params.group, params.category);
  const visibleCategories = PRODUCT_CATEGORIES.filter((category) => category[0] === activeGroup);

  return (
    <section className="catalog-page">
      <div className="catalog-heading">
        <div className="section-heading">
          <p className="eyebrow">상품목록</p>
          <h1>안전장비 상품 목록</h1>
          <p>건설현장과 산업현장에 필요한 안전장비를 바로 구매하세요.</p>
        </div>
        <span>총 {filteredProducts.length}개 상품</span>
      </div>

      {error ? (
        <div className="notice danger">
          <strong>상품을 불러오지 못했습니다</strong>
          <span>백엔드 API 연결 상태를 확인해 주세요.</span>
        </div>
      ) : null}

      {!error && filteredProducts.length === 0 ? (
        <div className="notice empty">
          <strong>판매중인 상품이 없습니다</strong>
          <span>검색 조건을 바꾸거나 관리자에서 상품을 등록해 주세요.</span>
        </div>
      ) : null}

      <div className="catalog-layout">
        <aside className="catalog-sidebar">
          <CategoryFilterPanel
            activeGroup={activeGroup}
            groups={groups}
            params={params}
            products={products}
            visibleCategories={visibleCategories}
          />
        </aside>

        <div className="catalog-results">
          <div className="catalog-tools">
            <span>전체 {filteredProducts.length}개 상품</span>
            <div>
              <details className="catalog-mobile-filters">
                <summary>필터</summary>
                <div>
                  <CategoryFilterPanel
                    activeGroup={activeGroup}
                    groups={groups}
                    params={params}
                    products={products}
                    visibleCategories={visibleCategories}
                  />
                </div>
              </details>
              <Link href={withSort(params, "price-asc")}>낮은가격순</Link>
              <Link href={withSort(params, "price-desc")}>높은가격순</Link>
            </div>
          </div>

          <div className="product-grid">
            {filteredProducts.map((product) => (
              <Link className="product-card" href={`/products/${product.id}`} key={product.id}>
                <ProductImage
                  alt={product.name}
                  className="product-card-image"
                  src={product.thumbnailImageUrl}
                />
                <div className="product-card-copy">
                  <span className="product-card-name">{product.name}</span>
                  <span className="product-card-category">{categoryLabel(product.categoryCode)}</span>
                  <strong className="product-card-price">{formatPrice(product.basePrice)}</strong>
                  <span className="product-card-status">배송비 포함</span>
                  <span className="product-card-summary">{product.summary}</span>
                </div>
                <span className="product-card-cta">상세 보기</span>
              </Link>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

function CategoryFilterPanel({
  activeGroup,
  groups,
  params,
  products,
  visibleCategories,
}: {
  activeGroup: string;
  groups: string[];
  params: ProductSearchParams;
  products: ProductSummary[];
  visibleCategories: (typeof PRODUCT_CATEGORIES)[number][];
}) {
  return (
    <>
      <h2>카테고리</h2>
      <Link className={!params.category && !params.group ? "active" : ""} href="/products">
        전체 상품 <span>{products.length}</span>
      </Link>
      <h2>대분류</h2>
      <div className="catalog-group-list">
        {groups.map((group) => (
          <Link
            className={!params.category && activeGroup === group ? "active" : ""}
            href={withGroup(params, group)}
            key={group}
          >
            {group} <span>{groupProductCount(products, group)}</span>
          </Link>
        ))}
      </div>
      <h2>{activeGroup}</h2>
      <div className="catalog-category-chips">
        {visibleCategories.map((category) => (
          <Link
            className={params.category === category[2] ? "active" : ""}
            href={withCategory(params, category[2])}
            key={category[2]}
          >
            {category[3]}
          </Link>
        ))}
      </div>
      <h2>가격대</h2>
      <Link href={withPriceRange(params)}>전체</Link>
      <Link href={withPriceRange(params, undefined, "10000")}>1만원 이하</Link>
      <Link href={withPriceRange(params, "10000", "50000")}>1만원 - 5만원</Link>
      <Link href={withPriceRange(params, "50000")}>5만원 이상</Link>
    </>
  );
}

function filterProducts(
  products: ProductSummary[],
  params: ProductSearchParams,
) {
  const keyword = params.q?.trim().toLowerCase();
  const minPrice = Number(params.minPrice ?? 0);
  const maxPrice = Number(params.maxPrice ?? Number.MAX_SAFE_INTEGER);
  const filtered = products.filter((product) => {
    const matchesKeyword = keyword
      ? `${product.name} ${product.summary}`.toLowerCase().includes(keyword)
      : true;
    const matchesCategory = params.category ? product.categoryCode === params.category : true;
    const matchesGroup = params.category || !params.group ? true : productGroup(product) === params.group;
    return matchesKeyword && matchesCategory && matchesGroup && product.basePrice >= minPrice && product.basePrice <= maxPrice;
  });

  return filtered.sort((a, b) => {
    if (params.sort === "price-asc") return a.basePrice - b.basePrice;
    if (params.sort === "price-desc") return b.basePrice - a.basePrice;
    return 0;
  });
}

function categoryGroups() {
  return [...new Set(PRODUCT_CATEGORIES.map((category) => category[0]))];
}

function selectedGroup(group: string | undefined, categoryCode: string | undefined) {
  const categoryGroup = PRODUCT_CATEGORIES.find((category) => category[2] === categoryCode)?.[0];
  if (categoryGroup) return categoryGroup;
  if (group && PRODUCT_CATEGORIES.some((category) => category[0] === group)) return group;
  return PRODUCT_CATEGORIES[0][0];
}

function groupProductCount(products: ProductSummary[], group: string) {
  return products.filter((product) => productGroup(product) === group).length;
}

function productGroup(product: ProductSummary) {
  return PRODUCT_CATEGORIES.find((item) => item[2] === product.categoryCode)?.[0];
}

function withSort(params: ProductSearchParams, sort: string) {
  const searchParams = new URLSearchParams();
  if (params.category) searchParams.set("category", params.category);
  if (params.group && !params.category) searchParams.set("group", params.group);
  if (params.q) searchParams.set("q", params.q);
  if (params.minPrice) searchParams.set("minPrice", params.minPrice);
  if (params.maxPrice) searchParams.set("maxPrice", params.maxPrice);
  searchParams.set("sort", sort);
  return `/products?${searchParams.toString()}`;
}

function withGroup(params: ProductSearchParams, group: string) {
  const searchParams = new URLSearchParams();
  searchParams.set("group", group);
  if (params.q) searchParams.set("q", params.q);
  if (params.minPrice) searchParams.set("minPrice", params.minPrice);
  if (params.maxPrice) searchParams.set("maxPrice", params.maxPrice);
  if (params.sort) searchParams.set("sort", params.sort);
  return `/products?${searchParams.toString()}`;
}

function withCategory(params: ProductSearchParams, category: string) {
  const searchParams = new URLSearchParams();
  searchParams.set("category", category);
  if (params.q) searchParams.set("q", params.q);
  if (params.minPrice) searchParams.set("minPrice", params.minPrice);
  if (params.maxPrice) searchParams.set("maxPrice", params.maxPrice);
  if (params.sort) searchParams.set("sort", params.sort);
  return `/products?${searchParams.toString()}`;
}

function withPriceRange(params: ProductSearchParams, minPrice?: string, maxPrice?: string) {
  const searchParams = new URLSearchParams();
  if (params.category) searchParams.set("category", params.category);
  if (params.group && !params.category) searchParams.set("group", params.group);
  if (params.q) searchParams.set("q", params.q);
  if (params.sort) searchParams.set("sort", params.sort);
  if (minPrice) searchParams.set("minPrice", minPrice);
  if (maxPrice) searchParams.set("maxPrice", maxPrice);
  const query = searchParams.toString();
  return query ? `/products?${query}` : "/products";
}
