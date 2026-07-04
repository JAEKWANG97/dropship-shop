import { getAdminPricingPolicy } from "@/lib/admin";
import { updatePricingPolicy } from "./actions";

type AdminPricingPageProps = {
  searchParams: Promise<{ message?: string }>;
};

export default async function AdminPricingPage({ searchParams }: AdminPricingPageProps) {
  const [policy, query] = await Promise.all([getAdminPricingPolicy(), searchParams]);

  return (
    <div className="admin-page">
      <div className="admin-heading">
        <div>
          <h1>가격 정책</h1>
          <p>공급가에서 고객 판매가를 계산하는 기본 마진율을 관리하세요.</p>
        </div>
      </div>

      {query.message ? (
        <div className="notice">
          <strong>알림</strong>
          <span>{query.message}</span>
        </div>
      ) : null}

      <section className="admin-panel">
        <div className="admin-panel-head">
          <h2>기본 정책</h2>
          <span>총 {policy.totalMarkupRate}%</span>
        </div>
        <form action={updatePricingPolicy} className="admin-form-grid">
          <label>
            정책명
            <input name="name" required defaultValue={policy.name} />
          </label>
          <label>
            커미션 %
            <input name="commissionRate" required min="0" step="0.01" type="number" defaultValue={policy.commissionRate} />
          </label>
          <label>
            세금/부가비 버퍼 %
            <input name="taxBufferRate" required min="0" step="0.01" type="number" defaultValue={policy.taxBufferRate} />
          </label>
          <label>
            운영비 %
            <input name="overheadRate" required min="0" step="0.01" type="number" defaultValue={policy.overheadRate} />
          </label>
          <label>
            안전마진 %
            <input name="safetyMarginRate" required min="0" step="0.01" type="number" defaultValue={policy.safetyMarginRate} />
          </label>
          <label>
            반올림 단위
            <input name="roundingUnit" required min="1" step="1" type="number" defaultValue={policy.roundingUnit} />
          </label>
          <div className="admin-form-actions wide">
            <button className="button primary" type="submit">
              가격 정책 저장
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
