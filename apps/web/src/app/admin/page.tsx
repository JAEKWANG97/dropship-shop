import Link from "next/link";
import { getAdminUser, getCurrentUser } from "@/lib/session";

export default async function AdminPage() {
  const [session, admin] = await Promise.all([getCurrentUser(), getAdminUser()]);

  if (!session) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">Admin</p>
        <h1>관리자 로그인이 필요합니다</h1>
        <p>관리자도 소셜 로그인 후 DB에 등록된 ADMIN 권한으로만 접근합니다.</p>
        <Link className="button primary" href="/login">
          로그인
        </Link>
      </section>
    );
  }

  if (!admin) {
    return (
      <section className="narrow-page">
        <p className="eyebrow">Admin</p>
        <h1>관리자 권한이 없습니다</h1>
        <p>현재 계정은 로그인되어 있지만 관리자 권한이 등록되어 있지 않습니다.</p>
      </section>
    );
  }

  return (
    <section className="narrow-page">
      <p className="eyebrow">Admin</p>
      <h1>관리자 콘솔</h1>
      <div className="notice success">
        <strong>관리자 세션 확인</strong>
        <span>{admin.userId}</span>
      </div>
      <Link className="button" href="/">
        홈으로
      </Link>
    </section>
  );
}
