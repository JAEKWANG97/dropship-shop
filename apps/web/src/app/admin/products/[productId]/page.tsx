import Link from "next/link";
import { ApiError } from "@/lib/api";
import {
  adminOptionStatusLabel,
  adminStatusLabel,
  getAdminProduct,
  getAdminProductChanges,
  getAdminProducts,
  type AdminProduct,
  type AdminProductChange,
} from "@/lib/admin";
import { categoryLabel } from "@/lib/categories";
import { formatPrice, type ProductDetail } from "@/lib/catalog";
import { ProductImage } from "@/app/products/product-image";
import {
  createAdminProductOption,
  updateAdminProductOption,
  updateAdminProductOptionStatus,
  updateAdminProductStatus,
} from "./actions";

type AdminProductDetailPageProps = {
  params: Promise<{ productId: string }>;
  searchParams: Promise<{ message?: string }>;
};

const PRODUCT_STATUSES = ["ACTIVE", "SOLD_OUT", "HIDDEN", "STOPPED"] as const;
const OPTION_STATUSES = ["ACTIVE", "SOLD_OUT", "STOPPED"] as const;

async function loadProduct(productId: string) {
  try {
    const [product, products, changes] = await Promise.all([
      getAdminProduct(productId),
      getAdminProducts(),
      loadChanges(productId),
    ]);
    return {
      changes,
      error: false as const,
      listProduct: products.find((item) => item.id === productId) ?? null,
      product,
    };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return { changes: [], error: true as const, listProduct: null, product: null };
    }
    return { changes: [], error: true as const, listProduct: null, product: null };
  }
}

async function loadChanges(productId: string) {
  try {
    return [...(await getAdminProductChanges(productId))].reverse();
  } catch {
    return [] as AdminProductChange[];
  }
}

export default async function AdminProductDetailPage({
  params,
  searchParams,
}: AdminProductDetailPageProps) {
  const [{ productId }, query] = await Promise.all([params, searchParams]);
  const { changes, error, listProduct, product } = await loadProduct(productId);

  if (error || !product) {
    return (
      <div className="admin-page">
        <div className="admin-heading">
          <div>
            <h1>상품을 불러오지 못했습니다</h1>
            <p>권한, API 서버, 네트워크 상태를 확인한 뒤 다시 시도하세요.</p>
          </div>
          <Link className="button" href="/admin/products">
            상품 목록
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="admin-page">
      <div className="admin-heading">
        <div>
          <h1>{product.name}</h1>
          <p>상품 판매 상태와 옵션 가격, 옵션 판매 상태를 관리하세요.</p>
        </div>
        <div className="action-row">
          <Link className="button" href="/admin/products">
            상품 목록
          </Link>
          <Link className="button" href={`/products/${product.id}`}>
            고객 상세 보기
          </Link>
        </div>
      </div>

      {query.message ? (
        <div className="notice">
          <strong>알림</strong>
          <span>{query.message}</span>
        </div>
      ) : null}

      <section className="admin-product-layout">
        <ProductSummaryPanel listProduct={listProduct} product={product} />
        <ProductStatusPanel product={product} />
      </section>

      <section className="admin-panel">
        <div className="admin-panel-head">
          <h2>옵션 관리</h2>
          <span>총 {product.options.length}개</span>
        </div>
        <div className="admin-option-list">
          {product.options.map((option) => (
            <article className="admin-option-card" key={option.id}>
              <div>
                <strong>{option.name}</strong>
                <span className={`admin-badge ${option.status.toLowerCase()}`}>
                  {adminOptionStatusLabel(option.status)}
                </span>
              </div>
              <dl>
                <div>
                  <dt>추가금액</dt>
                  <dd>{formatPrice(option.additionalPrice)}</dd>
                </div>
                <div>
                  <dt>최종 판매가</dt>
                  <dd>{formatPrice(product.basePrice + option.additionalPrice)}</dd>
                </div>
              </dl>
              <form action={updateAdminProductOption} className="admin-inline-form">
                <input name="productId" type="hidden" value={product.id} />
                <input name="optionId" type="hidden" value={option.id} />
                <input name="status" type="hidden" value={option.status} />
                <label>
                  옵션명
                  <input name="name" required defaultValue={option.name} />
                </label>
                <label>
                  추가금액
                  <input min="0" name="additionalPrice" required type="number" defaultValue={option.additionalPrice} />
                </label>
                <label className="wide">
                  변경 사유
                  <input name="reason" required placeholder="예: 옵션 가격 조정" />
                </label>
                <button className="button" type="submit">
                  옵션 정보 저장
                </button>
              </form>
              <form action={updateAdminProductOptionStatus} className="admin-inline-form">
                <input name="productId" type="hidden" value={product.id} />
                <input name="optionId" type="hidden" value={option.id} />
                <label>
                  옵션 판매 상태
                  <select name="status" required defaultValue={option.status}>
                    {OPTION_STATUSES.map((status) => (
                      <option key={status} value={status}>
                        {adminOptionStatusLabel(status)}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  변경 사유
                  <input name="reason" required placeholder="예: 공급처 품절" />
                </label>
                <button className="button" type="submit">
                  상태 변경
                </button>
              </form>
            </article>
          ))}
          {product.options.length === 0 ? (
            <div className="admin-empty compact">
              <strong>등록된 옵션이 없습니다</strong>
              <span>기본 옵션을 추가해야 고객이 장바구니에 담을 수 있습니다.</span>
            </div>
          ) : null}
        </div>
      </section>

      <section className="admin-panel">
        <h2>옵션 추가</h2>
        <form action={createAdminProductOption} className="admin-form-grid">
          <input name="productId" type="hidden" value={product.id} />
          <label>
            옵션명
            <input name="name" required placeholder="예: 260mm" />
          </label>
          <label>
            추가금액
            <input defaultValue="0" min="0" name="additionalPrice" required type="number" />
          </label>
          <label>
            상태
            <select name="status" defaultValue="ACTIVE">
              {OPTION_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {adminOptionStatusLabel(status)}
                </option>
              ))}
            </select>
          </label>
          <div className="admin-form-actions wide">
            <button className="button primary" type="submit">
              옵션 추가
            </button>
          </div>
        </form>
      </section>

      <section className="admin-panel">
        <div className="admin-panel-head">
          <h2>변경 이력</h2>
          <span>최근 {changes.length}건</span>
        </div>
        <div className="admin-change-list">
          {changes.map((change) => (
            <div key={change.changeId}>
              <strong>{changeTypeLabel(change.changeType)}</strong>
              <span>
                {change.beforeValue ?? "-"} → {change.afterValue ?? "-"}
              </span>
              <span>{change.reason}</span>
              <time dateTime={change.createdAt}>{formatDateTime(change.createdAt)}</time>
            </div>
          ))}
          {changes.length === 0 ? (
            <div className="admin-empty compact">
              <strong>변경 이력이 없습니다</strong>
              <span>상품 상태나 옵션 정보를 변경하면 이력이 기록됩니다.</span>
            </div>
          ) : null}
        </div>
      </section>
    </div>
  );
}

function ProductSummaryPanel({
  listProduct,
  product,
}: {
  listProduct: AdminProduct | null;
  product: ProductDetail;
}) {
  return (
    <section className="admin-panel">
      <div className="admin-product-summary">
        <ProductImage
          alt={product.name}
          className="admin-product-detail-image"
          src={product.thumbnailImageUrl}
        />
        <div>
          <h2>기본 정보</h2>
          <dl>
            <div>
              <dt>카테고리</dt>
              <dd>{categoryLabel(product.categoryCode)}</dd>
            </div>
            <div>
              <dt>공급처</dt>
              <dd>{listProduct?.supplierName ?? "목록에서 확인 불가"}</dd>
            </div>
            <div>
              <dt>기본 가격</dt>
              <dd>{formatPrice(product.basePrice)}</dd>
            </div>
            <div>
              <dt>상세 버전</dt>
              <dd>v{product.detailVersion}</dd>
            </div>
          </dl>
          <p>{product.summary}</p>
        </div>
      </div>
    </section>
  );
}

function ProductStatusPanel({ product }: { product: ProductDetail }) {
  return (
    <section className="admin-panel">
      <div className="admin-panel-head">
        <h2>상품 판매 상태</h2>
        <span className={`admin-badge ${product.status.toLowerCase()}`}>
          {adminStatusLabel(product.status)}
        </span>
      </div>
      <form action={updateAdminProductStatus} className="admin-inline-form">
        <input name="productId" type="hidden" value={product.id} />
        <label>
          판매 상태
          <select name="status" required defaultValue={product.status}>
            {PRODUCT_STATUSES.map((status) => (
              <option key={status} value={status}>
                {adminStatusLabel(status)}
              </option>
            ))}
          </select>
        </label>
        <label>
          변경 사유
          <input name="reason" required placeholder="예: 공급처 품절" />
        </label>
        <button className="button primary" type="submit">
          상품 상태 변경
        </button>
      </form>
    </section>
  );
}

function changeTypeLabel(changeType: string) {
  return (
    {
      PRICE: "가격 변경",
      PRODUCT_STATUS: "상품 상태 변경",
      PRODUCT_CATEGORY: "카테고리 변경",
      OPTION_STATUS: "옵션 상태 변경",
      SUPPLIER: "공급처 변경",
      PRODUCT_BASE: "상품 기본 정보 변경",
      OPTION_BASE: "옵션 기본 정보 변경",
      IMAGES: "이미지 변경",
      DETAIL_BLOCKS: "상세 블록 변경",
      NOTICE: "상품 고시 변경",
    }[changeType] ?? changeType
  );
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
