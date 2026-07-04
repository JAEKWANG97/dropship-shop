import { redirect } from "next/navigation";
import Link from "next/link";
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
    <section className="auth-page">
      <div className="auth-card">
        <Link href="/" className="auth-brand" aria-label="코어블SAF home">
          <strong>
            <span>코어블</span>
            <em>SAF</em>
          </strong>
          <small>건설 안전용품 쇼핑몰</small>
        </Link>
        <div className="auth-copy">
          <span className="auth-context">소셜 로그인</span>
          <h1>현장에 필요한 안전용품을 바로 주문하세요</h1>
          <p>비밀번호 없이 소셜 계정으로 로그인하고 장바구니, 주문조회, 배송 정보를 이어서 확인할 수 있습니다.</p>
        </div>
        {session ? (
          <div className="auth-signed-in">
            <strong>이미 로그인되어 있습니다</strong>
            <div className="auth-actions">
              <Link className="button primary" href={redirectTo ?? "/account"}>
                계속하기
              </Link>
              <Link className="button" href="/products">
                상품 보기
              </Link>
            </div>
          </div>
        ) : (
          <>
            <div className="auth-tabs" aria-hidden="true">
              <span className="active">소셜 로그인</span>
              <span>주문·배송 조회</span>
            </div>
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
            <p className="auth-helper">주문과 배송 안내를 위해 로그인 후 이름, 이메일, 휴대폰 인증이 필요할 수 있습니다.</p>
          </>
        )}
      </div>
    </section>
  );
}
