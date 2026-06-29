import Link from "next/link";
import { getProfileCompletion } from "@/lib/account";
import { getCurrentUser } from "@/lib/session";
import { confirmPhoneVerification, requestPhoneVerification, updateProfile } from "./actions";

type AccountPageProps = {
  searchParams: Promise<{ message?: string }>;
};

export default async function AccountPage({ searchParams }: AccountPageProps) {
  const params = await searchParams;
  const session = await getCurrentUser();

  if (!session) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">Customer area</p>
        <h1>로그인이 필요합니다</h1>
        <p>장바구니, 주문, 배송지 관리는 소셜 로그인 후 사용할 수 있습니다.</p>
        <Link className="button primary" href="/login">
          로그인
        </Link>
      </section>
    );
  }

  const profile = await loadProfileCompletion();

  return (
    <section className="narrow-page">
      <p className="eyebrow">Customer area</p>
      <h1>내 계정</h1>
      {params.message ? (
        <div className="notice">
          <strong>알림</strong>
          <span>{params.message}</span>
        </div>
      ) : null}
      <div className="notice success">
        <strong>세션 확인</strong>
        <span>{session.userId}</span>
      </div>
      {profile.error ? (
        <div className="notice">
          <strong>필수 정보를 불러오지 못했습니다</strong>
          <span>API 서버와 로그인 상태를 확인해 주세요.</span>
        </div>
      ) : (
        <>
          <div className={profile.data.requiredInfoComplete ? "notice success" : "notice"}>
            <strong>{profile.data.requiredInfoComplete ? "필수 정보 완료" : "필수 정보 필요"}</strong>
            <span>주문 전 이름, 연락 가능한 이메일, 인증된 휴대폰 번호가 필요합니다.</span>
          </div>
          <form action={updateProfile} className="account-form">
            <label>
              이름
              <input name="displayName" required defaultValue={profile.data.displayName} />
            </label>
            <label>
              이메일
              <input name="email" required type="email" defaultValue={profile.data.email} />
            </label>
            <button className="button" type="submit">
              기본 정보 저장
            </button>
          </form>
          <form action={requestPhoneVerification} className="account-form">
            <label>
              휴대폰 번호
              <input
                name="phoneNumber"
                required
                inputMode="tel"
                placeholder="01012345678"
                defaultValue={profile.data.phoneNumber ?? ""}
              />
            </label>
            <button className="button" type="submit">
              인증번호 받기
            </button>
          </form>
          <form action={confirmPhoneVerification} className="account-form">
            <label>
              인증할 휴대폰 번호
              <input
                name="phoneNumber"
                required
                inputMode="tel"
                placeholder="01012345678"
                defaultValue={profile.data.phoneNumber ?? ""}
              />
            </label>
            <label>
              인증번호
              <input name="code" required inputMode="numeric" minLength={6} maxLength={6} />
            </label>
            <button className="button primary" type="submit">
              휴대폰 인증 완료
            </button>
          </form>
        </>
      )}
      <div className="link-list">
        <Link href="/products">상품 보기</Link>
        <Link href="/orders">주문 내역</Link>
      </div>
      <form action="/auth/logout" method="post">
        <button className="button" type="submit">
          로그아웃
        </button>
      </form>
    </section>
  );
}

async function loadProfileCompletion() {
  try {
    return { error: false as const, data: await getProfileCompletion() };
  } catch {
    return { error: true as const, data: null };
  }
}
