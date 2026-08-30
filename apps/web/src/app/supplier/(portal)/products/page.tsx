"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { categoryPath } from "@/lib/categories";
import { listSupplierProducts, supplierStatusView, type SupplierProduct } from "@/lib/supplier-products";

export default function SupplierProductsPage() {
  const [products, setProducts] = useState<SupplierProduct[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    listSupplierProducts()
      .then((value) => active && setProducts(value))
      .catch(() => active && setFailed(true))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, []);

  return (
    <div className="supplier-page">
      <div className="admin-heading">
        <div><h1>상품</h1><p>Coreable에 등록한 상품과 검토 상태를 확인합니다.</p></div>
        <Link className="button primary" href="/supplier/products/new">상품 등록</Link>
      </div>

      {failed ? <div className="notice danger"><strong>상품 목록을 불러오지 못했습니다</strong><span>잠시 뒤 다시 시도해 주세요.</span></div> : null}
      <section className="admin-panel">
        <div className="admin-panel-head"><h2>내 상품</h2><span>{loading ? "불러오는 중" : `${products.length}개`}</span></div>
        <div className="admin-inquiry-list">
          {products.map((product) => {
            const status = supplierStatusView(product);
            return (
              <Link className="admin-inquiry-card" href={`/supplier/products/${encodeURIComponent(product.id)}`} key={product.id}>
                <div><strong>{product.name || "이름 없는 상품"}</strong><span className={`admin-badge ${status.tone}`}>{status.label}</span></div>
                <dl>
                  <div><dt>카테고리</dt><dd>{categoryPath(product.categoryCode)}</dd></div>
                  <div><dt>공급가</dt><dd>{product.sourcePrice.toLocaleString("ko-KR")}원</dd></div>
                  <div><dt>수정일</dt><dd>{date(product.updatedAt)}</dd></div>
                </dl>
                <p>{status.message ?? status.nextLabel}</p>
              </Link>
            );
          })}
          {!loading && !failed && products.length === 0 ? <div className="admin-empty compact"><strong>등록한 상품이 없습니다</strong><span>상품 등록에서 첫 상품을 등록해 주세요.</span></div> : null}
        </div>
      </section>
    </div>
  );
}

function date(value: string | null) {
  return value ? new Date(value).toLocaleDateString("ko-KR") : "-";
}
