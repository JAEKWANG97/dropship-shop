import Link from "next/link";
import { redirect } from "next/navigation";
import { getReferralState } from "@/lib/account";
import { safeRedirectTo } from "@/lib/redirect";
import { getCurrentUser } from "@/lib/session";
import { SubmitButton } from "../submit-button";
import { registerReferral, skipReferral } from "./actions";

type WelcomePageProps = {
  searchParams: Promise<{ message?: string | string[]; redirectTo?: string | string[] }>;
};

export default async function WelcomePage({ searchParams }: WelcomePageProps) {
  const [session, params] = await Promise.all([getCurrentUser(), searchParams]);
  const redirectTo = safeRedirectTo(params.redirectTo) || "/account";
  const message = Array.isArray(params.message) ? params.message[0] : params.message;

  if (!session) {
    redirect(`/login?redirectTo=${encodeURIComponent("/welcome")}`);
  }

  const referral = await loadReferralState();

  return (
    <section className="narrow-page">
      <p className="eyebrow">첫 로그인</p>
      <h1>추천인 코드</h1>
      <p>추천인 코드가 있으면 한 번만 등록할 수 있습니다. 없으면 건너뛰어도 됩니다.</p>

      {message ? (
        <div className="notice">
          <strong>알림</strong>
          <span>{message}</span>
        </div>
      ) : null}

      {referral.error ? (
        <div className="notice danger">
          <strong>추천 정보를 불러오지 못했습니다</strong>
          <span>잠시 후 다시 시도하거나 계정 페이지로 이동해 주세요.</span>
        </div>
      ) : referral.data.referrerRegistered ? (
        <div className="notice success">
          <strong>이미 등록되어 있습니다</strong>
          <span>추천인 코드는 계정당 한 번만 등록할 수 있습니다.</span>
        </div>
      ) : (
        <form action={registerReferral} className="account-form">
          <input name="redirectTo" type="hidden" value={redirectTo} />
          <label>
            추천인 코드
            <input
              autoCapitalize="characters"
              autoComplete="off"
              maxLength={20}
              name="code"
              placeholder="예: 2ABCD789"
              required
            />
          </label>
          <SubmitButton className="button primary" pendingLabel="등록 중...">
            등록
          </SubmitButton>
        </form>
      )}

      <form action={skipReferral}>
        <input name="redirectTo" type="hidden" value={redirectTo} />
        <SubmitButton className="button" pendingLabel="이동 중...">
          건너뛰기
        </SubmitButton>
      </form>
      <Link className="admin-text-link" href={redirectTo}>
        계속하기
      </Link>
    </section>
  );
}

async function loadReferralState() {
  try {
    return { error: false as const, data: await getReferralState() };
  } catch {
    return { error: true as const, data: null };
  }
}
