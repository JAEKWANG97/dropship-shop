export default function SupplierHomePage() {
  return (
    <div className="supplier-page">
      <div className="admin-heading">
        <div>
          <p className="eyebrow">공급처 홈</p>
          <h1>담당자 연결이 완료되었습니다</h1>
          <p>상품과 출고 업무는 준비된 메뉴부터 순서대로 열립니다.</p>
        </div>
      </div>

      <section className="admin-panel">
        <div className="admin-panel-head">
          <h2>현재 이용 안내</h2>
          <span>최소 포털</span>
        </div>
        <div className="supplier-home-list">
          <div><strong>로그인</strong><span>초대에 연결한 카카오 계정을 사용합니다.</span></div>
          <div><strong>상품</strong><span>개별 상품 등록 기능이 준비되면 이 포털에서 바로 등록합니다.</span></div>
          <div><strong>출고</strong><span>입금 확인이 끝난 주문만 최소 배송정보와 함께 표시됩니다.</span></div>
        </div>
      </section>
    </div>
  );
}
