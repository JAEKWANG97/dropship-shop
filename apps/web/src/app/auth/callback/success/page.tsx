import Link from "next/link";
import { getCurrentUser } from "@/lib/session";

export default async function OAuthSuccessPage() {
  const session = await getCurrentUser();

  return (
    <section className="narrow-page">
      <p className="eyebrow">Login complete</p>
      <h1>로그인 완료</h1>
      {session ? (
        <p>세션이 확인되었습니다. 이제 상품을 담고 주문을 진행할 수 있습니다.</p>
      ) : (
        <p>로그인 쿠키를 확인하지 못했습니다. 다시 로그인해 주세요.</p>
      )}
      <div className="action-row">
        <Link className="button primary" href="/products">
          상품 보기
        </Link>
        <Link className="button" href="/account">
          내 계정
        </Link>
      </div>
    </section>
  );
}
