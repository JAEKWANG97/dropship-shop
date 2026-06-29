import Link from "next/link";
import { adminStatusLabel, getAdminProducts } from "@/lib/admin";
import { formatPrice } from "@/lib/catalog";

type AdminProductsPageProps = {
  searchParams: Promise<{ message?: string; q?: string; status?: string }>;
};

export default async function AdminProductsPage({ searchParams }: AdminProductsPageProps) {
  const [data, params] = await Promise.all([loadProducts(), searchParams]);
  const products = data.products;
  const keyword = params.q?.trim().toLowerCase();
  const status = params.status?.trim();
  const filteredProducts = products.filter((product) => {
    const matchesKeyword =
      !keyword || `${product.name} ${product.summary} ${product.supplierName}`.toLowerCase().includes(keyword);
    const matchesStatus = !status || product.status === status;

    return matchesKeyword && matchesStatus;
  });

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
        <form action="/admin/products" className="admin-filters">
          <input name="q" placeholder="상품명, 공급처 검색" defaultValue={params.q ?? ""} />
          <select name="status" defaultValue={params.status ?? ""}>
            <option value="">전체 상태</option>
            <option value="ACTIVE">판매중</option>
            <option value="SOLD_OUT">품절</option>
            <option value="STOPPED">판매중지</option>
          </select>
          <button className="button" type="submit">
            검색
          </button>
        </form>
      ) : null}

      {!data.error ? (
        <section className="admin-panel">
          <div className="admin-panel-head">
            <h2>상품 목록</h2>
            <span>총 {filteredProducts.length}개</span>
          </div>
          <div className="admin-table products">
            <div className="admin-table-row admin-table-head">
              <span>상품명</span>
              <span>공급처</span>
              <span>가격</span>
              <span>상태</span>
              <span>상세</span>
            </div>
            {filteredProducts.map((product) => (
              <div className="admin-table-row" key={product.id}>
                <strong>{product.name}</strong>
                <span>{product.supplierName}</span>
                <span>{formatPrice(product.basePrice)}</span>
                <span className={`admin-badge ${product.status.toLowerCase()}`}>
                  {adminStatusLabel(product.status)}
                </span>
                <span>v{product.detailVersion}</span>
              </div>
            ))}
            {filteredProducts.length === 0 ? (
              <div className="admin-empty">
                <strong>표시할 상품이 없습니다</strong>
                <span>검색어를 바꾸거나 상품을 먼저 등록하세요.</span>
              </div>
            ) : null}
          </div>
        </section>
      ) : null}
    </div>
  );
}

async function loadProducts() {
  try {
    return { error: false as const, products: await getAdminProducts() };
  } catch {
    return { error: true as const, products: [] };
  }
}
