import { apiUrl } from "@/lib/api";
import { getCurrentUser } from "@/lib/session";

const providers = [
  { id: "kakao", label: "카카오로 로그인" },
  { id: "google", label: "구글로 로그인" },
  { id: "naver", label: "네이버로 로그인" },
];

export default async function LoginPage() {
  const session = await getCurrentUser();

  return (
    <section className="narrow-page">
      <p className="eyebrow">Social login</p>
      <h1>로그인</h1>
      <p>
        비회원 주문과 이메일/비밀번호 로그인은 제공하지 않습니다. 카카오,
        구글, 네이버 계정으로만 이용할 수 있습니다.
      </p>

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
    </section>
  );
}
