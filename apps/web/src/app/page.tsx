import Link from "next/link";
import {
  FEATURED_CATEGORIES,
  PRODUCT_CATEGORIES,
  categoryLabel,
  type ProductCategoryCode,
} from "@/lib/categories";
import { formatPrice, getProducts, type ProductSummary } from "@/lib/catalog";
import { ProductImage } from "./products/product-image";

const SITE_BUNDLES = [
  {
    title: "기본 보호구 준비",
    summary: "현장 출입 전 기본으로 확인하는 보호구",
    categories: ["PPE_SAFETY_HELMET", "PPE_SAFETY_SHOES", "PPE_HIGH_VISIBILITY_VEST", "PPE_SAFETY_GLASSES"],
  },
  {
    title: "추락 작업 준비",
    summary: "고소작업과 개구부 주변 작업 전 점검 품목",
    categories: ["PPE_FALL_ARREST_HARNESS", "FALL_PREVENTION_NET", "LIFELINE", "SAFETY_BLOCK"],
  },
  {
    title: "안전 통제 구역 설치",
    summary: "출입 제한과 위험구역 표시를 위한 통제 시설",
    categories: ["SAFETY_SIGN", "TRAFFIC_CONE", "SAFETY_FENCE", "BARRICADE"],
  },
  {
    title: "화기·용접 작업 준비",
    summary: "불꽃, 비산물, 연기 노출 전 확인 품목",
    categories: ["PPE_WELDING_GLOVES", "PPE_SAFETY_GLASSES", "PPE_RESPIRATOR", "PPE_PROTECTIVE_CLOTHING"],
  },
  {
    title: "전기 작업 준비",
    summary: "감전 위험과 작업 구역 통제를 위한 준비",
    categories: ["PPE_INSULATED_GLOVES", "PPE_SAFETY_HELMET", "WARNING_SIGN", "DANGER_AREA_BARRIER"],
  },
  {
    title: "철거·분진 작업 준비",
    summary: "분진, 소음, 비산물 노출 작업 전 점검",
    categories: ["PPE_RESPIRATOR", "PPE_SAFETY_GLASSES", "DUST_METER", "VENTILATION_EQUIPMENT"],
  },
  {
    title: "야간·우천 작업 준비",
    summary: "시야 확보와 차량·작업자 식별이 필요한 현장",
    categories: ["PPE_HIGH_VISIBILITY_VEST", "WARNING_LIGHT", "SIGNAL_BATON", "TRAFFIC_CONE"],
  },
  {
    title: "중장비 작업 구역 준비",
    summary: "장비 접근, 후방, 협착 위험 구역 관리",
    categories: ["HEAVY_EQUIPMENT_PROXIMITY_ALARM", "HEAVY_EQUIPMENT_COLLISION_PREVENTION", "HEAVY_EQUIPMENT_REAR_DETECTOR", "TRAFFIC_CONE"],
  },
  {
    title: "밀폐·협소 공간 작업 준비",
    summary: "산소, 가스, 환기와 비상 호출을 확인하는 작업",
    categories: ["OXYGEN_METER", "GAS_DETECTOR", "VENTILATION_EQUIPMENT", "WORKER_SOS_EMERGENCY_CALL"],
  },
  {
    title: "응급 대응 준비",
    summary: "응급처치와 현장 초기 대응을 위한 품목",
    categories: ["FIRST_AID_KIT", "FIRST_AID_SUPPLIES", "AED", "EYEWASH_STATION"],
  },
] satisfies {
  title: string;
  summary: string;
  categories: ProductCategoryCode[];
}[];

async function loadProducts() {
  try {
    return (await getProducts()).slice(0, 6);
  } catch {
    return [] as ProductSummary[];
  }
}

export default async function Home() {
  const products = await loadProducts();
  const groups = [...new Set(PRODUCT_CATEGORIES.map((category) => category[0]))];

  return (
    <div className="home-page">
      <section className="home-hero">
        <div className="home-copy">
          <p className="eyebrow">건설 안전용품 쇼핑몰</p>
          <h1>
            필요한 안전용품을
            <span>바로 찾고 주문</span>
          </h1>
          <p>
            안전모, 안전화, 보호구, 추락방지 장비까지 현장에서 자주 쓰는 품목을
            빠르게 확인하세요.
          </p>
          <div className="action-row">
            <Link className="button primary" href="/products">
              상품 보러가기
            </Link>
          </div>
        </div>
        <form action="/products" className="home-category-form">
          <label htmlFor="home-category-group">필요한 품목 찾기</label>
          <div>
            <select id="home-category-group" name="group" defaultValue={groups[0]}>
              {groups.map((group) => (
                <option key={group} value={group}>
                  {group}
                </option>
              ))}
            </select>
            <button className="button" type="submit">
              상품 보기
            </button>
          </div>
          <nav className="home-category-chips" aria-label="자주 찾는 품목">
            <span>자주 찾는 품목</span>
            {FEATURED_CATEGORIES.map((category) => (
              <Link href={`/products?category=${encodeURIComponent(category)}`} key={category}>
                {categoryLabel(category)}
              </Link>
            ))}
            <Link href="/products">전체 보기</Link>
          </nav>
        </form>
      </section>

      <section className="home-products">
        <div className="catalog-heading">
          <div className="section-heading">
            <h2>현장에서 자주 찾는 상품</h2>
          </div>
          <Link href="/products">더보기</Link>
        </div>
        {products.length > 0 ? (
          <div className="product-grid featured">
            {products.map((product) => (
              <Link className="product-card" href={`/products/${product.id}`} key={product.id}>
                <ProductImage
                  alt={product.name}
                  className="product-card-image"
                  src={product.thumbnailImageUrl}
                />
                <div className="product-card-copy">
                  <span className="product-card-name">{product.name}</span>
                  <strong className="product-card-price">{formatPrice(product.basePrice)}</strong>
                </div>
                <span className="product-card-cta">상세 보기</span>
              </Link>
            ))}
          </div>
        ) : (
          <div className="notice">
            <strong>추천 상품을 불러오지 못했습니다</strong>
            <span>백엔드 API 연결 상태를 확인해 주세요.</span>
          </div>
        )}
      </section>

      <section className="home-bundles">
        <div className="section-heading">
          <h2>현장별 구매 묶음</h2>
          <p>작업 상황별로 필요한 품목을 빠르게 확인하세요.</p>
        </div>
        <div className="home-bundle-grid">
          {SITE_BUNDLES.map((bundle) => (
            <article className="home-bundle-card" key={bundle.title}>
              <h3>{bundle.title}</h3>
              <p>{bundle.summary}</p>
              <div>
                {bundle.categories.map((category) => (
                  <Link href={`/products?category=${encodeURIComponent(category)}`} key={category}>
                    {categoryLabel(category)}
                  </Link>
                ))}
              </div>
            </article>
          ))}
        </div>
      </section>

    </div>
  );
}
