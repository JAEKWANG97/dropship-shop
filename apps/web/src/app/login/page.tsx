import { apiUrl } from "@/lib/api";
import { getCurrentUser } from "@/lib/session";

const providers = [
  { id: "kakao", label: "카카오로 계속하기" },
  { id: "google", label: "구글로 계속하기" },
  { id: "naver", label: "네이버로 계속하기" },
];

export default async function LoginPage() {
  const session = await getCurrentUser();

  return (
    <section className="login-page">
      <div className="login-copy">
        <p className="eyebrow">Social login</p>
        <h1>사업자 구매를 위한 간편 로그인</h1>
        <p>
          SafeHub Pro는 비회원 주문과 이메일/비밀번호 로그인을 제공하지 않습니다.
          카카오, 구글, 네이버 계정으로 로그인한 뒤 장바구니와 주문 결제를 진행합니다.
        </p>
        <div className="login-policy-list">
          <span>비회원 주문 없음</span>
          <span>소셜 로그인 전용</span>
          <span>관리자는 DB 권한 기준</span>
        </div>
      </div>

      <div className="login-panel">
        <h2>로그인</h2>
        {session ? (
          <div className="notice success">
            <strong>로그인됨</strong>
            <span>{session.userId}</span>
          </div>
        ) : (
          <div className="button-stack">
            {providers.map((provider) => (
              <a
                className={`oauth-button ${provider.id}`}
                href={apiUrl(`/api/auth/oauth2/${provider.id}/authorize`)}
                key={provider.id}
              >
                {provider.label}
              </a>
            ))}
          </div>
        )}
        <p>관리자도 같은 소셜 로그인 후 등록된 ADMIN 권한으로만 접근합니다.</p>
      </div>
    </section>
  );
}
