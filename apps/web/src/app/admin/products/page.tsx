import Link from "next/link";
import { redirect } from "next/navigation";
import { adminStatusLabel, getAdminProducts, getAdminSuppliers } from "@/lib/admin";
import { categoryLabel, PRODUCT_CATEGORIES } from "@/lib/categories";
import { formatPrice } from "@/lib/catalog";
import type { SaleBlocker } from "@/lib/catalog";
import { ProductImage } from "@/app/products/product-image";

type ProductSearchParams = {
  message?: string;
  q?: string;
  status?: string;
  category?: string;
  supplierId?: string;
  readiness?: string;
  page?: string;
};

type AdminProductsPageProps = {
  searchParams: Promise<ProductSearchParams>;
};

export default async function AdminProductsPage({ searchParams }: AdminProductsPageProps) {
  const params = await searchParams;
  const requestedPage = positivePage(params.page);
  const data = await loadProducts(params, requestedPage - 1);

  if (!data.error && data.products.totalPages > 0 && requestedPage > data.products.totalPages) {
    redirect(pageHref(params, data.products.totalPages));
  }

  const products = data.error ? [] : data.products.products;
  const currentPage = data.error ? 1 : data.products.page + 1;
  const totalPages = data.error ? 0 : data.products.totalPages;

  return (
    <div className="admin-page">
      <div className="admin-heading">
        <div>
          <h1>상품 관리</h1>
          <p>상품 판매 상태와 공급처, 가격을 확인하고 관리하세요.</p>
        </div>
        <Link className="button primary" href="/admin/products/new">
          상품 등록
        </Link>
      </div>

      {params.message ? (
        <div className="notice">
          <strong>알림</strong>
          <span>{params.message}</span>
        </div>
      ) : null}

      {data.error ? (
        <div className="notice">
          <strong>상품 데이터를 불러오지 못했습니다</strong>
          <span>권한, API 서버, 네트워크 상태를 확인한 뒤 다시 시도하세요.</span>
        </div>
      ) : null}

      {!data.error ? (
        <form action="/admin/products" className="admin-filters admin-product-filters">
          <input name="q" placeholder="상품명, 공급처 검색" defaultValue={params.q ?? ""} />
          <select name="status" defaultValue={params.status ?? ""}>
            <option value="">전체 상태</option>
            <option value="ACTIVE">판매중</option>
            <option value="SOLD_OUT">품절</option>
            <option value="HIDDEN">숨김</option>
            <option value="STOPPED">판매중지</option>
          </select>
          <select name="category" defaultValue={params.category ?? ""}>
            <option value="">전체 카테고리</option>
            {categoryGroups().map(([group, categories]) => (
              <optgroup key={group} label={group}>
                {categories.map((category) => (
                  <option key={category[2]} value={category[2]}>
                    {category[1] ? `${category[1]} · ${category[3]}` : category[3]}
                  </option>
                ))}
              </optgroup>
            ))}
          </select>
          <select name="supplierId" defaultValue={params.supplierId ?? ""}>
            <option value="">전체 공급처</option>
            {data.suppliers.map((supplier) => (
              <option key={supplier.id} value={supplier.id}>
                {supplier.name}
              </option>
            ))}
          </select>
          <select name="readiness" defaultValue={params.readiness ?? ""}>
            <option value="">전체 검수 상태</option>
            <option value="READY">판매 준비 완료</option>
            <option value="BLOCKED">필수정보 부족</option>
          </select>
          <button className="button" type="submit">
            검색
          </button>
          <Link className="button" href="/admin/products">
            초기화
          </Link>
        </form>
      ) : null}

      {!data.error ? (
        <section className="admin-panel">
          <div className="admin-panel-head">
            <h2>상품 목록</h2>
            <span>총 {data.error ? 0 : data.products.totalElements}개</span>
          </div>
          <div className="admin-table products">
            <div className="admin-table-row admin-table-head">
              <span>이미지</span>
              <span>상품</span>
              <span>가격</span>
              <span>판매 준비</span>
              <span>상태</span>
              <span>관리</span>
            </div>
            {products.map((product) => {
              const increase = product.basePrice - product.sourcePrice;
              const increaseRate = product.sourcePrice > 0 ? (increase / product.sourcePrice) * 100 : null;
              return (
              <div className="admin-table-row" key={product.id}>
                <ProductImage
                  alt={product.name}
                  className="admin-product-image"
                  src={product.thumbnailImageUrl}
                />
                <div className="admin-product-identity">
                  <strong>{product.name}</strong>
                  <span>{categoryLabel(product.categoryCode)} · {product.supplierName}</span>
                  {product.sourceUrl ? (
                    <a href={product.sourceUrl} rel="noopener noreferrer" target="_blank">원본 보기</a>
                  ) : <span>원본 없음</span>}
                  {product.sourceSyncError ? (
                    <span title={product.sourceSyncError}>공급처 동기화 실패</span>
                  ) : product.sourceSyncedAt ? (
                    <span>공급처 확인 완료</span>
                  ) : null}
                </div>
                <div className="admin-product-price">
                  <strong>{formatPrice(product.basePrice)}</strong>
                  <span>공급가 {formatPrice(product.sourcePrice)}</span>
                  <span>
                    인상 {formatPrice(increase)}{increaseRate === null ? "" : ` · ${increaseRate.toFixed(1)}%`}
                  </span>
                </div>
                <div className="admin-product-readiness">
                  <strong className={`admin-badge ${product.saleReady ? "ready" : "blocked"}`}>
                    {product.saleReady ? "준비 완료" : "필수정보 부족"}
                  </strong>
                  <span>옵션 {product.optionCount}개</span>
                  {!product.saleReady ? <span>{product.saleBlockers.map(saleBlockerLabel).join(", ")}</span> : null}
                </div>
                <span className={`admin-badge admin-product-status ${product.status.toLowerCase()}`}>
                  {adminStatusLabel(product.status)}
                </span>
                <Link className="admin-text-link admin-product-manage" href={`/admin/products/${product.id}`}>
                  관리
                </Link>
              </div>
              );
            })}
            {products.length === 0 ? (
              <div className="admin-empty">
                <strong>조건에 맞는 상품이 없습니다</strong>
                <span>검색어나 필터를 바꾸거나 상품을 먼저 등록하세요.</span>
              </div>
            ) : null}
          </div>
          {totalPages > 0 ? (
            <nav className="admin-pagination" aria-label="상품 목록 페이지">
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
        </section>
      ) : null}
    </div>
  );
}

async function loadProducts(params: ProductSearchParams, page: number) {
  try {
    const [products, suppliers] = await Promise.all([
      getAdminProducts({
        q: params.q?.trim(),
        status: params.status,
        category: params.category,
        supplierId: params.supplierId,
        readiness: params.readiness,
        page,
      }),
      getAdminSuppliers(),
    ]);
    return { error: false as const, products, suppliers };
  } catch {
    return { error: true as const, products: null, suppliers: [] };
  }
}

function positivePage(value?: string) {
  const page = Number.parseInt(value ?? "1", 10);
  return Number.isFinite(page) && page > 0 ? page : 1;
}

function pageHref(params: ProductSearchParams, page: number) {
  const query = new URLSearchParams();
  for (const key of ["q", "status", "category", "supplierId", "readiness"] as const) {
    if (params[key]) query.set(key, params[key]);
  }
  if (page > 1) query.set("page", String(page));
  const value = query.toString();
  return value ? `/admin/products?${value}` : "/admin/products";
}

function pageNumbers(currentPage: number, totalPages: number) {
  const start = Math.max(1, Math.min(currentPage - 2, totalPages - 4));
  const end = Math.min(totalPages, start + 4);
  return Array.from({ length: end - start + 1 }, (_, index) => start + index);
}

function categoryGroups() {
  return Object.entries(Object.groupBy(PRODUCT_CATEGORIES, (category) => category[0]));
}

function saleBlockerLabel(blocker: SaleBlocker) {
  return ({
    BASE_PRICE: "판매가",
    THUMBNAIL: "대표 이미지",
    ACTIVE_OPTION: "판매 옵션",
    PRODUCT_NOTICE: "상품 고시",
    COMPLIANCE: "인증 검수",
  }[blocker]);
}
