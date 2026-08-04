import Link from "next/link";
import { ApiError } from "@/lib/api";
import {
  adminOptionStatusLabel,
  adminStatusLabel,
  getAdminPricingPolicy,
  getAdminProduct,
  getAdminProductChanges,
  type AdminProductChange,
  type PricingPolicy,
} from "@/lib/admin";
import { categoryLabel } from "@/lib/categories";
import { formatPrice, type ProductDetail, type SaleBlocker } from "@/lib/catalog";
import { ProductImage } from "@/app/products/product-image";
import {
  createAdminProductOption,
  updateAdminProductDetailBlocks,
  updateAdminProductNotice,
  updateAdminProductOption,
  updateAdminProductOptionStatus,
  updateAdminProductPrices,
  updateAdminProductStatus,
  updateAdminProductThumbnail,
} from "./actions";

type AdminProductDetailPageProps = {
  params: Promise<{ productId: string }>;
  searchParams: Promise<{ message?: string }>;
};

const PRODUCT_STATUSES = ["ACTIVE", "SOLD_OUT", "HIDDEN", "STOPPED"] as const;
const OPTION_STATUSES = ["ACTIVE", "SOLD_OUT", "STOPPED"] as const;
const COMPLIANCE_STATUSES = ["PENDING", "NOT_REQUIRED", "VERIFIED", "REJECTED"] as const;

async function loadProduct(productId: string) {
  try {
    const [product, changes] = await Promise.all([
      getAdminProduct(productId),
      loadChanges(productId),
    ]);
    const pricingPolicy = await getAdminPricingPolicy();
    return {
      changes,
      error: false as const,
      pricingPolicy,
      product,
    };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return { changes: [], error: true as const, pricingPolicy: null, product: null };
    }
    return { changes: [], error: true as const, pricingPolicy: null, product: null };
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
  const { changes, error, pricingPolicy, product } = await loadProduct(productId);

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

      <ProductReadinessPanel product={product} />

      <section className="admin-product-layout">
        <ProductSummaryPanel product={product} />
        <ProductStatusPanel product={product} />
      </section>

      <ProductThumbnailPanel product={product} />

      {pricingPolicy ? (
        <ProductPricingPanel pricingPolicy={pricingPolicy} product={product} />
      ) : null}

      <ProductDetailBlocksPanel product={product} />
      <ProductNoticePanel product={product} />

      <section className="admin-panel" id="product-options">
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
                <div>
                  <dt>원본 옵션코드</dt>
                  <dd>{option.sourceOptionCode ?? "-"}</dd>
                </div>
                <div>
                  <dt>원본 추가금</dt>
                  <dd>{option.sourceAdditionalPrice === undefined ? "-" : formatPrice(option.sourceAdditionalPrice)}</dd>
                </div>
                <div>
                  <dt>원본 재고</dt>
                  <dd>{option.sourceStockQuantity === undefined ? "-" : `${option.sourceStockQuantity.toLocaleString("ko-KR")}개`}</dd>
                </div>
                <div>
                  <dt>정렬값</dt>
                  <dd>{option.sortOrder ?? "-"}</dd>
                </div>
              </dl>
              <form action={updateAdminProductOption} className="admin-inline-form">
                <input name="productId" type="hidden" value={product.id} />
                <input name="optionId" type="hidden" value={option.id} />
                <input name="status" type="hidden" value={option.status} />
                <input name="sourceOptionCode" type="hidden" value={option.sourceOptionCode ?? ""} />
                <input name="sourceAdditionalPrice" type="hidden" value={option.sourceAdditionalPrice ?? ""} />
                <input name="sourceStockQuantity" type="hidden" value={option.sourceStockQuantity ?? ""} />
                <input name="sortOrder" type="hidden" value={option.sortOrder ?? ""} />
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

function ProductReadinessPanel({ product }: { product: ProductDetail }) {
  const blockers = new Set(product.saleBlockers ?? []);
  const items: Array<{ blocker: SaleBlocker; label: string; href: string }> = [
    { blocker: "BASE_PRICE", label: "판매가", href: "#product-pricing" },
    { blocker: "THUMBNAIL", label: "대표 이미지", href: "#product-images" },
    { blocker: "ACTIVE_OPTION", label: "판매 가능한 옵션", href: "#product-options" },
    { blocker: "PRODUCT_NOTICE", label: "상품 고시", href: "#product-notice" },
    { blocker: "COMPLIANCE", label: "인증 검수", href: "#product-pricing" },
  ];

  return (
    <section className={`admin-panel admin-readiness-panel ${product.saleReady ? "ready" : "blocked"}`}>
      <div className="admin-panel-head">
        <div>
          <h2>판매 준비 상태</h2>
          <span>{product.saleReady ? "고객에게 공개할 수 있습니다." : "부족한 필수정보를 먼저 입력하세요."}</span>
        </div>
        <strong className={`admin-badge ${product.saleReady ? "ready" : "blocked"}`}>
          {product.saleReady ? "준비 완료" : `필수정보 부족 ${blockers.size}개`}
        </strong>
      </div>
      <div className="admin-readiness-list">
        {items.map((item) => {
          const complete = !blockers.has(item.blocker);
          return (
            <a className={complete ? "complete" : "missing"} href={item.href} key={item.blocker}>
              <span aria-hidden="true">{complete ? "✓" : "!"}</span>
              <strong>{item.label}</strong>
              <small>{complete ? "완료" : "확인 필요"}</small>
            </a>
          );
        })}
      </div>
      {!product.hasDetailContent ? <p className="field-help">상세 콘텐츠는 판매 차단 조건은 아니지만 공개 전 확인을 권장합니다.</p> : null}
    </section>
  );
}

function ProductThumbnailPanel({ product }: { product: ProductDetail }) {
  return (
    <section className="admin-panel" id="product-images">
      <div className="admin-panel-head">
        <h2>대표 이미지</h2>
        <span>{product.hasThumbnail ? "등록 완료" : "판매 전 등록 필요"}</span>
      </div>
      <form action={updateAdminProductThumbnail} className="admin-inline-form">
        <input name="productId" type="hidden" value={product.id} />
        <label>
          이미지 파일
          <input accept="image/jpeg,image/png,image/webp" name="thumbnailFile" required type="file" />
        </label>
        <label>
          변경 사유
          <input name="reason" required placeholder="예: 대표 이미지 검수 완료" />
        </label>
        <button className="button" type="submit">대표 이미지 저장</button>
      </form>
    </section>
  );
}

function ProductSummaryPanel({ product }: { product: ProductDetail }) {
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
              <dd>{product.supplierName ?? "공급처 정보 없음"}</dd>
            </div>
            <div>
              <dt>공급가</dt>
              <dd>{formatPrice(product.sourcePrice ?? product.basePrice)}</dd>
            </div>
            <div>
              <dt>판매가</dt>
              <dd>{formatPrice(product.basePrice)}</dd>
            </div>
            <div>
              <dt>공급처 원본</dt>
              <dd>
                {product.sourceUrl ? (
                  <a href={product.sourceUrl} rel="noopener noreferrer" target="_blank">원본 보기</a>
                ) : "등록되지 않음"}
              </dd>
            </div>
            <div>
              <dt>공급처 상태</dt>
              <dd>{product.sourceAvailable === true ? "판매 가능" : product.sourceAvailable === false ? "품절 또는 판매 중지" : "확인 전"}</dd>
            </div>
            <div>
              <dt>마지막 동기화</dt>
              <dd>{product.sourceSyncedAt ? formatDateTime(product.sourceSyncedAt) : "아직 실행되지 않음"}</dd>
            </div>
            {product.sourceSyncError ? (
              <div>
                <dt>동기화 오류</dt>
                <dd>{product.sourceSyncError}</dd>
              </div>
            ) : null}
            <div>
              <dt>인증 검수</dt>
              <dd>{complianceStatusLabel(product.complianceStatus ?? "PENDING")}</dd>
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

function ProductPricingPanel({
  pricingPolicy,
  product,
}: {
  pricingPolicy: PricingPolicy;
  product: ProductDetail;
}) {
  const sourcePrice = product.sourcePrice ?? product.basePrice;
  const calculatedPrice = Math.round(
    (sourcePrice * (1 + pricingPolicy.totalMarkupRate / 100)) / pricingPolicy.roundingUnit
  ) * pricingPolicy.roundingUnit;

  return (
    <section className="admin-panel" id="product-pricing">
      <div className="admin-panel-head">
        <h2>가격 및 판매 검수</h2>
        <span>계산 판매가 {formatPrice(calculatedPrice)} · {pricingPolicy.roundingUnit}원 단위 반올림</span>
      </div>
      <form action={updateAdminProductPrices} className="admin-form-grid">
        <input name="productId" type="hidden" value={product.id} />
        <input name="supplierId" type="hidden" value={product.supplierId ?? ""} />
        <input name="name" type="hidden" value={product.name} />
        <input name="summary" type="hidden" value={product.summary} />
        <input name="categoryCode" type="hidden" value={product.categoryCode} />
        <label>
          공급가
          <input name="sourcePrice" required min="0" type="number" defaultValue={sourcePrice} />
        </label>
        <label className="wide">
          공급처 원본 URL
          <input name="sourceUrl" type="url" placeholder="https://..." defaultValue={product.sourceUrl ?? ""} />
          <span className="field-help">운영자 검수용이며 고객 화면에는 노출하지 않습니다.</span>
        </label>
        <label>
          판매가
          <input name="basePrice" required min="0" type="number" defaultValue={product.basePrice} />
        </label>
        <label>
          인증 검수
          <select name="complianceStatus" required defaultValue={product.complianceStatus ?? "PENDING"}>
            {COMPLIANCE_STATUSES.map((status) => (
              <option key={status} value={status}>{complianceStatusLabel(status)}</option>
            ))}
          </select>
        </label>
        <label>
          변경 사유
          <input name="reason" required placeholder="예: 도매가 25% 마진 및 100원 반올림 적용" />
        </label>
        <div className="admin-form-actions wide">
          <button className="button" name="priceMode" value="manual" type="submit">
            입력 정보 저장
          </button>
          <button className="button primary" name="priceMode" value="apply" type="submit">
            계산가 적용
          </button>
        </div>
      </form>
    </section>
  );
}

function complianceStatusLabel(status: string) {
  return ({
    PENDING: "검수 전",
    NOT_REQUIRED: "인증 비대상",
    VERIFIED: "인증 확인 완료",
    REJECTED: "판매 불가",
  }[status] ?? status);
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
              <option
                disabled={status === "ACTIVE" && !product.saleReady && product.status !== "ACTIVE"}
                key={status}
                value={status}
              >
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

function ProductDetailBlocksPanel({ product }: { product: ProductDetail }) {
  const nextSortOrder = product.detailBlocks.length;

  return (
    <section className="admin-panel" id="product-details">
      <div className="admin-panel-head">
        <h2>상세 콘텐츠</h2>
        <span>현재 {product.detailBlocks.length}개</span>
      </div>
      <form action={updateAdminProductDetailBlocks} className="admin-detail-form">
        <input name="productId" type="hidden" value={product.id} />
        <input name="blockCount" type="hidden" value={product.detailBlocks.length} />
        <div className="admin-detail-block-list">
          {product.detailBlocks.map((block, index) => (
            <article className="admin-detail-block-card" key={block.id}>
              <input name={`blockType-${index}`} type="hidden" value={block.type} />
              <input name={`blockImageUrl-${index}`} type="hidden" value={block.imageUrl ?? ""} />
              <div className="admin-detail-block-head">
                <strong>{block.type === "IMAGE" ? "이미지 블록" : "HTML 블록"}</strong>
                <label className="admin-check-field">
                  <input name={`blockInclude-${index}`} type="checkbox" value="true" defaultChecked />
                  포함
                </label>
              </div>
              <div className="admin-form-grid">
                <label>
                  정렬값
                  <input name={`blockSortOrder-${index}`} type="number" defaultValue={block.sortOrder} />
                </label>
                {block.type === "IMAGE" ? (
                  <>
                    <label>
                      대체 텍스트
                      <input name={`blockAltText-${index}`} defaultValue={block.altText ?? ""} />
                    </label>
                    <div className="wide admin-detail-image-row">
                      <ProductImage
                        alt={block.altText ?? product.name}
                        className="admin-detail-block-image"
                        src={block.imageUrl}
                      />
                      <label>
                        이미지 교체
                        <input name={`blockImageFile-${index}`} type="file" accept="image/jpeg,image/png,image/webp" />
                      </label>
                    </div>
                  </>
                ) : (
                  <label className="wide">
                    HTML 내용
                    <textarea name={`blockHtmlContent-${index}`} required rows={7} defaultValue={block.htmlContent ?? ""} />
                  </label>
                )}
              </div>
            </article>
          ))}
          {product.detailBlocks.length === 0 ? (
            <div className="admin-empty compact">
              <strong>등록된 상세 콘텐츠가 없습니다</strong>
              <span>아래 입력칸으로 이미지 또는 HTML 블록을 추가하세요.</span>
            </div>
          ) : null}
        </div>
        <div className="admin-detail-add-grid">
          <section>
            <h3>새 이미지 블록</h3>
            <div className="admin-form-grid">
              <label>
                이미지 파일
                <input name="newImageFile" type="file" accept="image/jpeg,image/png,image/webp" />
                <span className="field-help">
                  상세 이미지 블록은 16:9, 1600x900px 또는 1920x1080px webp 권장.
                </span>
              </label>
              <label>
                정렬값
                <input name="newImageSortOrder" type="number" defaultValue={nextSortOrder} />
              </label>
              <label className="wide">
                대체 텍스트
                <input name="newImageAltText" placeholder={product.name} />
              </label>
            </div>
          </section>
          <section>
            <h3>새 HTML 블록</h3>
            <div className="admin-form-grid">
              <label>
                정렬값
                <input name="newHtmlSortOrder" type="number" defaultValue={nextSortOrder + 1} />
              </label>
              <label className="wide">
                HTML 내용
                <textarea name="newHtmlContent" rows={6} placeholder="<p>상세 설명을 입력하세요.</p>" />
              </label>
            </div>
          </section>
        </div>
        <label>
          변경 사유
          <input name="reason" required placeholder="예: 상품 상세 설명 보강" />
        </label>
        <div className="admin-form-actions">
          <button className="button primary" type="submit">
            상세 콘텐츠 저장
          </button>
        </div>
      </form>
    </section>
  );
}

function ProductNoticePanel({ product }: { product: ProductDetail }) {
  return (
    <section className="admin-panel" id="product-notice">
      <div className="admin-panel-head">
        <h2>상품 고시</h2>
        <span>{product.productNotice ? `v${product.productNotice.version}` : "미등록"}</span>
      </div>
      <form action={updateAdminProductNotice} className="admin-form">
        <input name="productId" type="hidden" value={product.id} />
        <label>
          상품 정보
          <textarea
            name="productInfoNotice"
            required
            rows={3}
            defaultValue={product.productNotice?.productInfoNotice ?? ""}
          />
        </label>
        <label>
          배송
          <textarea name="shippingInfo" required rows={3} defaultValue={product.productNotice?.shippingInfo ?? ""} />
        </label>
        <label>
          AS
          <textarea name="asInfo" required rows={3} defaultValue={product.productNotice?.asInfo ?? ""} />
        </label>
        <label>
          반품/교환
          <textarea
            name="returnExchangeInfo"
            required
            rows={3}
            defaultValue={product.productNotice?.returnExchangeInfo ?? ""}
          />
        </label>
        <label>
          변경 사유
          <input name="reason" required placeholder="예: 상품 고시 최신화" />
        </label>
        <div className="admin-form-actions">
          <button className="button primary" type="submit">
            상품 고시 저장
          </button>
        </div>
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
