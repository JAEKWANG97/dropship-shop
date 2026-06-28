import Link from "next/link";

export default function Home() {
  return (
    <div className="home-page">
      <section className="home-hero">
        <div className="home-copy">
          <p className="eyebrow">B2B Safety Gear</p>
          <h1>건설 안전장비 도매 주문</h1>
          <p>
            현장에서 필요한 안전모, 안전화, 형광조끼, 장갑을 한 번에 담고
            빠르게 주문하세요.
          </p>
          <div className="action-row">
            <Link className="button primary" href="/products">
              상품 보러가기
            </Link>
            <Link className="button" href="/cart">
              장바구니 확인
            </Link>
          </div>
        </div>
        <div className="hero-visual" aria-label="안전장비 상품 예시" />
      </section>

      <section className="category-strip" aria-label="대표 카테고리">
        <span>안전모</span>
        <span>안전화</span>
        <span>형광조끼</span>
        <span>안전장갑</span>
        <span>추락방지</span>
      </section>

      <section className="status-panel" aria-label="service benefits">
        <div>
          <span className="panel-label">사업자 전용가</span>
          <strong>배송비 포함 가격으로 바로 주문</strong>
        </div>
        <div>
          <span className="panel-label">공급처 출고</span>
          <strong>결제 후 운영자가 발주와 송장을 관리</strong>
        </div>
        <div>
          <span className="panel-label">품절 대응</span>
          <strong>공급처 품절 시 배송 그룹 단위 환불</strong>
        </div>
      </section>
    </div>
  );
}
