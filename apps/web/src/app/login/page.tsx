import { redirect } from "next/navigation";
import { publicApiUrl } from "@/lib/api";
import { safeRedirectTo } from "@/lib/redirect";
import { getCurrentUser } from "@/lib/session";

const providers = [
  { id: "kakao", label: "카카오로 계속하기" },
  { id: "google", label: "구글로 계속하기" },
  { id: "naver", label: "네이버로 계속하기" },
];

type LoginPageProps = {
  searchParams: Promise<{ redirectTo?: string | string[] }>;
};

export default async function LoginPage({ searchParams }: LoginPageProps) {
  const [session, params] = await Promise.all([getCurrentUser(), searchParams]);
  const redirectTo = safeRedirectTo(params.redirectTo);

  if (session && redirectTo) {
    redirect(redirectTo);
  }

  const redirectQuery = redirectTo ? `?redirectTo=${encodeURIComponent(redirectTo)}` : "";

  return (
    <section className="login-page">
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
                href={publicApiUrl(`/api/auth/oauth2/${provider.id}/authorize${redirectQuery}`)}
                key={provider.id}
              >
                {provider.label}
              </a>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
