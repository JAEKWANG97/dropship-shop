import { getAdminReferrals } from "@/lib/admin";

export default async function AdminReferralsPage() {
  const data = await loadReferrals();

  return (
    <div className="admin-page">
      <div className="admin-heading">
        <div>
          <h1>추천인 관리</h1>
          <p>첫 로그인 온보딩에서 등록된 추천 관계를 확인합니다.</p>
        </div>
      </div>

      {data.error ? (
        <div className="notice">
          <strong>추천 관계를 불러오지 못했습니다</strong>
          <span>API 서버 또는 관리자 권한을 확인하세요.</span>
        </div>
      ) : null}

      {!data.error ? (
        <section className="admin-panel">
          <div className="admin-panel-head">
            <h2>추천 관계</h2>
            <span>총 {data.referrals.length}건</span>
          </div>
          <div className="admin-table">
            <div className="admin-table-row admin-table-head">
              <span>추천인</span>
              <span>추천 코드</span>
              <span>가입 회원</span>
              <span>등록 시각</span>
            </div>
            {data.referrals.map((referral) => (
              <div className="admin-table-row" key={referral.referredUserId}>
                <span>{referral.referrerDisplayName}</span>
                <strong>{referral.referralCode}</strong>
                <span>{referral.referredDisplayName}</span>
                <time dateTime={referral.referredAt}>{new Date(referral.referredAt).toLocaleString("ko-KR")}</time>
              </div>
            ))}
            {data.referrals.length === 0 ? (
              <div className="admin-empty">
                <strong>등록된 추천 관계가 없습니다</strong>
                <span>신규 회원이 추천인 코드를 입력하면 이곳에 표시됩니다.</span>
              </div>
            ) : null}
          </div>
        </section>
      ) : null}
    </div>
  );
}

async function loadReferrals() {
  try {
    return { error: false as const, referrals: await getAdminReferrals() };
  } catch {
    return { error: true as const, referrals: [] };
  }
}
