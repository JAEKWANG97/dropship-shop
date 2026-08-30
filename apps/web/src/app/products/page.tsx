import Link from "next/link";
import { redirect } from "next/navigation";
import { PRODUCT_CATEGORIES, categoryLabel, type ProductCategoryCode } from "@/lib/categories";
import { formatPrice, getProducts } from "@/lib/catalog";
import { ProductImage } from "./product-image";

const PAGE_SIZE = 24;

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
  page?: string;
};

async function loadProducts(params: ProductSearchParams, page: number, activeGroup: string) {
  try {
    const category = productCategoryCode(params.category);
    const categories = !category && params.group === activeGroup
      ? PRODUCT_CATEGORIES
          .filter((item) => item[0] === activeGroup)
          .map((item) => item[2])
      : undefined;
    return {
      result: await getProducts({
        q: params.q?.trim(),
        category,
        categories,
        minPrice: nonNegativeNumber(params.minPrice),
        maxPrice: nonNegativeNumber(params.maxPrice),
        sort: productSort(params.sort),
        page,
        size: PAGE_SIZE,
      }),
      error: false as const,
    };
  } catch {
    return { result: null, error: true as const };
  }
}

export default async function ProductsPage({ searchParams }: ProductsPageProps) {
  const params = await searchParams;
  const activeGroup = selectedGroup(params.group, params.category);
  const requestedPage = positivePage(params.page);
  const { result, error } = await loadProducts(params, requestedPage - 1, activeGroup);

  if (!error && result.totalPages > 0 && requestedPage > result.totalPages) {
    redirect(pageHref(params, result.totalPages));
  }

  const products = result?.products ?? [];
  const currentPage = result ? result.page + 1 : 1;
  const totalPages = result?.totalPages ?? 0;
  const totalElements = result?.totalElements ?? 0;
  const categoryCounts = result?.categoryCounts ?? {};
  const searchTerm = params.q?.trim() ?? "";
  const selectedCategory = productCategoryCode(params.category);
  const hasSearchFilters = searchTerm.length > 0;
  const heading = selectedCategory
    ? `${categoryLabel(selectedCategory)} 상품`
    : hasSearchFilters
      ? `“${searchTerm}” 검색 결과`
      : "안전장비 상품 목록";

  return (
    <section className="catalog-page">
      <div className="catalog-heading">
        <div className="section-heading">
          <p className="eyebrow">상품 목록</p>
          <h1>{heading}</h1>
          <p>건설현장과 산업현장에 필요한 안전장비를 바로 구매하세요.</p>
        </div>
        <span>총 {totalElements}개 상품</span>
      </div>

      {searchTerm || selectedCategory ? (
        <div className="catalog-active-filters" aria-label="적용한 검색 조건">
          {searchTerm ? <Link href={withoutSearch(params)}>검색어: {searchTerm} ×</Link> : null}
          {selectedCategory ? <Link href={withoutCategory(params)}>{categoryLabel(selectedCategory)} ×</Link> : null}
        </div>
      ) : null}

      {error ? (
        <div className="notice danger">
          <strong>상품을 불러오지 못했습니다</strong>
          <span>백엔드 API 연결 상태를 확인해 주세요.</span>
        </div>
      ) : null}

      {!error && products.length === 0 ? (
        <div className="notice empty">
          <strong>판매 중인 상품이 없습니다</strong>
          <span>다른 검색어 또는 카테고리를 확인해 주세요.</span>
        </div>
      ) : null}

      <div className={`catalog-layout${hasSearchFilters ? " has-search-filters" : ""}`}>
        {hasSearchFilters ? (
          <aside className="catalog-sidebar">
            <RelatedCategoryFilterPanel categoryCounts={categoryCounts} params={params} totalProducts={totalElements} />
          </aside>
        ) : null}

        <div className="catalog-results">
          <div className="catalog-tools">
            <span>전체 {totalElements}개 상품</span>
            <div>
              {hasSearchFilters ? (
                <details className="catalog-mobile-filters">
                  <summary>필터</summary>
                  <div>
                    <RelatedCategoryFilterPanel categoryCounts={categoryCounts} params={params} totalProducts={totalElements} />
                  </div>
                </details>
              ) : null}
              <Link href={withSort(params, "price-asc")}>낮은 가격순</Link>
              <Link href={withSort(params, "price-desc")}>높은 가격순</Link>
            </div>
          </div>

          <div className="product-grid">
            {products.map((product) => (
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
                  <span className="product-card-meta">
                    <span>배송비 포함</span>
                    {product.minimumOrderQuantity > 1 ? (
                      <span>최소 {product.minimumOrderQuantity}개</span>
                    ) : null}
                  </span>
                  <span className="product-card-summary">{product.summary}</span>
                </div>
                <span className="product-card-cta">
                  {product.purchasable === false || product.status === "SOLD_OUT" ? "품절" : "상세 보기"}
                </span>
              </Link>
            ))}
          </div>
          {totalPages > 0 ? (
            <nav className="catalog-pagination" aria-label="상품 목록 페이지">
              {currentPage > 1 ? (
                <Link href={pageHref(params, currentPage - 1)}>이전</Link>
              ) : (
                <span aria-disabled="true">이전</span>
              )}
              {pageNumbers(currentPage, totalPages).map((page) =>
                page === currentPage ? (
                  <strong aria-current="page" key={page}>{page}</strong>
                ) : (
                  <Link href={pageHref(params, page)} key={page}>{page}</Link>
                ),
              )}
              {currentPage < totalPages ? (
                <Link href={pageHref(params, currentPage + 1)}>다음</Link>
              ) : (
                <span aria-disabled="true">다음</span>
              )}
            </nav>
          ) : null}
        </div>
      </div>
    </section>
  );
}

function RelatedCategoryFilterPanel({
  categoryCounts,
  params,
  totalProducts,
}: {
  categoryCounts: Partial<Record<ProductCategoryCode, number>>;
  params: ProductSearchParams;
  totalProducts: number;
}) {
  const categories = PRODUCT_CATEGORIES.filter((category) => (categoryCounts[category[2]] ?? 0) > 0);

  return (
    <>
      <h2>관련 카테고리</h2>
      <Link className={!params.category ? "active" : ""} href={withoutCategory(params)}>
        전체 검색 결과 <span>{totalProducts}</span>
      </Link>
      <div className="catalog-category-chips">
        {categories.map((category) => (
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

function selectedGroup(group: string | undefined, categoryCode: string | undefined) {
  const categoryGroup = PRODUCT_CATEGORIES.find((category) => category[2] === categoryCode)?.[0];
  if (categoryGroup) return categoryGroup;
  if (group && PRODUCT_CATEGORIES.some((category) => category[0] === group)) return group;
  return PRODUCT_CATEGORIES[0][0];
}

function productCategoryCode(value?: string) {
  return PRODUCT_CATEGORIES.find((category) => category[2] === value)?.[2];
}

function nonNegativeNumber(value?: string) {
  if (value === undefined || value === "") return undefined;
  const number = Number(value);
  return Number.isFinite(number) && number >= 0 ? number : undefined;
}

function productSort(value?: string) {
  return value === "price-asc" || value === "price-desc" ? value : "latest";
}

function positivePage(value?: string) {
  const page = Number.parseInt(value ?? "1", 10);
  return Number.isFinite(page) && page > 0 ? page : 1;
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

function withoutSearch(params: ProductSearchParams) {
  const searchParams = new URLSearchParams();
  if (params.category) searchParams.set("category", params.category);
  if (params.group && !params.category) searchParams.set("group", params.group);
  if (params.minPrice) searchParams.set("minPrice", params.minPrice);
  if (params.maxPrice) searchParams.set("maxPrice", params.maxPrice);
  if (params.sort) searchParams.set("sort", params.sort);
  const query = searchParams.toString();
  return query ? `/products?${query}` : "/products";
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

function withoutCategory(params: ProductSearchParams) {
  const searchParams = new URLSearchParams();
  if (params.q) searchParams.set("q", params.q);
  if (params.minPrice) searchParams.set("minPrice", params.minPrice);
  if (params.maxPrice) searchParams.set("maxPrice", params.maxPrice);
  if (params.sort) searchParams.set("sort", params.sort);
  const query = searchParams.toString();
  return query ? `/products?${query}` : "/products";
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

function pageHref(params: ProductSearchParams, page: number) {
  const searchParams = new URLSearchParams();
  for (const key of ["category", "group", "maxPrice", "minPrice", "q", "sort"] as const) {
    if (params[key]) searchParams.set(key, params[key]);
  }
  if (page > 1) searchParams.set("page", String(page));
  const query = searchParams.toString();
  return query ? `/products?${query}` : "/products";
}

function pageNumbers(currentPage: number, totalPages: number) {
  const start = Math.max(1, Math.min(currentPage - 2, totalPages - 4));
  const end = Math.min(totalPages, start + 4);
  return Array.from({ length: end - start + 1 }, (_, index) => start + index);
}
