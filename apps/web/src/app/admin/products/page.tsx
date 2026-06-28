import Link from "next/link";
import { adminStatusLabel, getAdminProducts } from "@/lib/admin";
import { formatPrice } from "@/lib/catalog";

type AdminProductsPageProps = {
  searchParams: Promise<{ message?: string; q?: string }>;
};

export default async function AdminProductsPage({ searchParams }: AdminProductsPageProps) {
  const [products, params] = await Promise.all([getAdminProducts(), searchParams]);
  const keyword = params.q?.trim().toLowerCase();
  const filteredProducts = keyword
    ? products.filter((product) =>
        `${product.name} ${product.summary} ${product.supplierName}`.toLowerCase().includes(keyword),
      )
    : products;

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

      <form action="/admin/products" className="admin-filters">
        <input name="q" placeholder="상품명, 공급처 검색" defaultValue={params.q ?? ""} />
        <select name="status" defaultValue="">
          <option value="">전체 상태</option>
          <option value="ACTIVE">판매중</option>
          <option value="SOLD_OUT">품절</option>
          <option value="STOPPED">판매중지</option>
        </select>
        <button className="button" type="submit">
          검색
        </button>
      </form>

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
        </div>
        <div className="admin-pagination" aria-label="pagination">
          <span>1</span>
          <span>2</span>
          <span>3</span>
          <span>...</span>
          <span>13</span>
        </div>
      </section>
    </div>
  );
}
