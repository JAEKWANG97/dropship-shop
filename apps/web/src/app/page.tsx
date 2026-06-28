import Link from "next/link";

export default function Home() {
  return (
    <section className="home-grid">
      <div className="home-copy">
        <p className="eyebrow">Supplier fulfillment commerce</p>
        <h1>공급처 출고형 자사몰 MVP</h1>
        <p>
          고객은 상품을 보고 결제하고, 운영자는 결제 완료 주문을 공급처 발주와
          배송 처리로 이어갑니다.
        </p>
        <div className="action-row">
          <Link className="button primary" href="/products">
            상품 보기
          </Link>
          <Link className="button" href="/policies">
            정책 확인
          </Link>
        </div>
      </div>
      <div className="status-panel" aria-label="MVP service status">
        <div>
          <span className="panel-label">고객</span>
          <strong>상품 탐색, 장바구니, 주문, 배송 조회</strong>
        </div>
        <div>
          <span className="panel-label">운영자</span>
          <strong>상품 관리, 공급처 발주, 송장 입력, 클레임 처리</strong>
        </div>
        <div>
          <span className="panel-label">정책</span>
          <strong>비회원 주문 없음, 소셜 로그인만 허용</strong>
        </div>
      </div>
    </section>
  );
}
