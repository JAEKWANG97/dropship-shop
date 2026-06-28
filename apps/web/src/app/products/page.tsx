import Link from "next/link";
import { formatPrice, getProducts, type ProductSummary } from "@/lib/catalog";
import { ProductImage } from "./product-image";

async function loadProducts() {
  try {
    return { products: await getProducts(), error: false };
  } catch {
    return { products: [] as ProductSummary[], error: true };
  }
}

export default async function ProductsPage() {
  const { products, error } = await loadProducts();

  return (
    <section className="catalog-page">
      <div className="section-heading">
        <p className="eyebrow">Catalog</p>
        <h1>상품</h1>
      </div>

      {error ? (
        <div className="notice">
          <strong>상품을 불러오지 못했습니다</strong>
          <span>백엔드 API 연결 상태를 확인해 주세요.</span>
        </div>
      ) : null}

      {!error && products.length === 0 ? (
        <div className="notice">
          <strong>판매중인 상품이 없습니다</strong>
          <span>관리자에서 상품을 등록하면 여기에 표시됩니다.</span>
        </div>
      ) : null}

      <div className="product-grid">
        {products.map((product) => (
          <Link className="product-card" href={`/products/${product.id}`} key={product.id}>
            <ProductImage
              alt={product.name}
              className="product-card-image"
              src={product.thumbnailImageUrl}
            />
            <span className="product-card-name">{product.name}</span>
            <span className="product-card-summary">{product.summary}</span>
            <strong>{formatPrice(product.basePrice)}</strong>
          </Link>
        ))}
      </div>
    </section>
  );
}
