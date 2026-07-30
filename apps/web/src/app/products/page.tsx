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
  const groups = categoryGroups();
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
  const allProductCount = Object.values(categoryCounts).reduce((sum, count) => sum + (count ?? 0), 0);
  const visibleCategories = PRODUCT_CATEGORIES.filter((category) => category[0] === activeGroup);

  return (
    <section className="catalog-page">
      <div className="catalog-heading">
        <div className="section-heading">
          <p className="eyebrow">상품목록</p>
          <h1>안전장비 상품 목록</h1>
          <p>건설현장과 산업현장에 필요한 안전장비를 바로 구매하세요.</p>
        </div>
        <span>총 {totalElements}개 상품</span>
      </div>

      {error ? (
        <div className="notice danger">
          <strong>상품을 불러오지 못했습니다</strong>
          <span>백엔드 API 연결 상태를 확인해 주세요.</span>
        </div>
      ) : null}

      {!error && products.length === 0 ? (
        <div className="notice empty">
          <strong>판매중인 상품이 없습니다</strong>
          <span>검색 조건을 바꾸거나 관리자에서 상품을 등록해 주세요.</span>
        </div>
      ) : null}

      <div className="catalog-layout">
        <aside className="catalog-sidebar">
          <CategoryFilterPanel
            activeGroup={activeGroup}
            categoryCounts={categoryCounts}
            groups={groups}
            params={params}
            totalProducts={allProductCount}
            visibleCategories={visibleCategories}
          />
        </aside>

        <div className="catalog-results">
          <div className="catalog-tools">
            <span>전체 {totalElements}개 상품</span>
            <div>
              <details className="catalog-mobile-filters">
                <summary>필터</summary>
                <div>
                  <CategoryFilterPanel
                    activeGroup={activeGroup}
                    categoryCounts={categoryCounts}
                    groups={groups}
                    params={params}
                    totalProducts={allProductCount}
                    visibleCategories={visibleCategories}
                  />
                </div>
              </details>
              <Link href={withSort(params, "price-asc")}>낮은가격순</Link>
              <Link href={withSort(params, "price-desc")}>높은가격순</Link>
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
                  <span className="product-card-status">배송비 포함</span>
                  <span className="product-card-summary">{product.summary}</span>
                </div>
                <span className="product-card-cta">상세 보기</span>
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

function CategoryFilterPanel({
  activeGroup,
  categoryCounts,
  groups,
  params,
  totalProducts,
  visibleCategories,
}: {
  activeGroup: string;
  categoryCounts: Partial<Record<ProductCategoryCode, number>>;
  groups: string[];
  params: ProductSearchParams;
  totalProducts: number;
  visibleCategories: (typeof PRODUCT_CATEGORIES)[number][];
}) {
  return (
    <>
      <h2>카테고리</h2>
      <Link className={!params.category && !params.group ? "active" : ""} href="/products">
        전체 상품 <span>{totalProducts}</span>
      </Link>
      <h2>대분류</h2>
      <div className="catalog-group-list">
        {groups.map((group) => (
          <Link
            className={!params.category && params.group === group ? "active" : ""}
            href={withGroup(params, group)}
            key={group}
          >
            {group} <span>{groupProductCount(categoryCounts, group)}</span>
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

function categoryGroups() {
  return [...new Set(PRODUCT_CATEGORIES.map((category) => category[0]))];
}

function selectedGroup(group: string | undefined, categoryCode: string | undefined) {
  const categoryGroup = PRODUCT_CATEGORIES.find((category) => category[2] === categoryCode)?.[0];
  if (categoryGroup) return categoryGroup;
  if (group && PRODUCT_CATEGORIES.some((category) => category[0] === group)) return group;
  return PRODUCT_CATEGORIES[0][0];
}

function groupProductCount(
  categoryCounts: Partial<Record<ProductCategoryCode, number>>,
  group: string,
) {
  return PRODUCT_CATEGORIES
    .filter((category) => category[0] === group)
    .reduce((count, category) => count + (categoryCounts[category[2]] ?? 0), 0);
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
