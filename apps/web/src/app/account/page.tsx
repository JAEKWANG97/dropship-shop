import Link from "next/link";
import { getCurrentUser } from "@/lib/session";

export default async function AccountPage() {
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

  return (
    <section className="narrow-page">
      <p className="eyebrow">Customer area</p>
      <h1>내 계정</h1>
      <div className="notice success">
        <strong>세션 확인</strong>
        <span>{session.userId}</span>
      </div>
      <div className="link-list">
        <Link href="/products">상품 보기</Link>
      </div>
      <form action="/auth/logout" method="post">
        <button className="button" type="submit">
          로그아웃
        </button>
      </form>
    </section>
  );
}
