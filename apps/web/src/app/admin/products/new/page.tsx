import { getAdminSuppliers } from "@/lib/admin";
import { createAdminProduct } from "./actions";

export default async function AdminProductNewPage() {
  const data = await loadSuppliers();
  const suppliers = data.suppliers;

  return (
    <div className="admin-page">
      <div className="admin-heading">
        <div>
          <h1>상품 등록 및 수정</h1>
          <p>기본 정보, 가격, 이미지, 상세 설명, 노출 설정을 관리하세요.</p>
        </div>
      </div>

      {data.error ? (
        <div className="notice">
          <strong>공급처 데이터를 불러오지 못했습니다</strong>
          <span>권한, API 서버, 네트워크 상태를 확인한 뒤 다시 시도하세요.</span>
        </div>
      ) : null}

      {!data.error ? (
        <form action={createAdminProduct} className="admin-form">
          <section className="admin-panel">
            <h2>기본 정보</h2>
            <div className="admin-form-grid">
              <label>
                상품명
                <input name="name" required placeholder="예: K2 안전모 K2-THINK 1" />
              </label>
              <label>
                공급처
                <select name="supplierId" required>
                  {suppliers.map((supplier) => (
                    <option key={supplier.id} value={supplier.id}>
                      {supplier.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="wide">
                요약 설명
                <input name="summary" required placeholder="상품 목록과 상세 상단에 노출되는 설명" />
              </label>
            </div>
          </section>

          <section className="admin-panel">
            <h2>가격 / 판매 상태</h2>
            <div className="admin-form-grid">
              <label>
                기본 가격
                <input min="0" name="basePrice" required type="number" />
              </label>
              <label>
                판매 상태
                <select name="status" defaultValue="ACTIVE">
                  <option value="ACTIVE">판매중</option>
                  <option value="SOLD_OUT">품절</option>
                  <option value="HIDDEN">숨김</option>
                  <option value="STOPPED">판매중지</option>
                </select>
              </label>
            </div>
          </section>

          <section className="admin-panel">
            <h2>배송 / 주문</h2>
            <div className="admin-form-grid">
              <label>
                배송그룹
                <input defaultValue="공급처 기본 배송그룹" readOnly />
              </label>
              <label>
                최소 주문수량
                <input defaultValue="1" min="1" type="number" />
              </label>
            </div>
          </section>

          <section className="admin-panel">
            <h2>이미지 / 상세 설명</h2>
            <div className="admin-form-grid">
              <label>
                대표 이미지 URL
                <input placeholder="이미지 업로드 후 URL 또는 object key" />
              </label>
              <label>
                상세 타입
                <select defaultValue="HTML">
                  <option>HTML</option>
                  <option>IMAGE</option>
                </select>
              </label>
              <label className="wide">
                상세 설명
                <textarea placeholder="<p>상품 상세 HTML 또는 이미지 설명</p>" />
              </label>
            </div>
          </section>

          <section className="admin-panel">
            <h2>노출 설정</h2>
            <div className="admin-form-grid">
              <label>
                검색 태그
                <input placeholder="안전모, 건설현장, K2" />
              </label>
              <label>
                전시 위치
                <select defaultValue="기본">
                  <option>기본</option>
                  <option>추천 상품</option>
                </select>
              </label>
            </div>
          </section>

          <div className="admin-form-actions">
            <button className="button primary" type="submit">
              저장
            </button>
          </div>
        </form>
      ) : null}
    </div>
  );
}

async function loadSuppliers() {
  try {
    return { error: false as const, suppliers: await getAdminSuppliers() };
  } catch {
    return { error: true as const, suppliers: [] };
  }
}
