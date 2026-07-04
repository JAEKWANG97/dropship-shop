import { redirect } from "next/navigation";
import { isTruthyQueryFlag, safeRedirectTo } from "@/lib/redirect";

type OAuthSuccessPageProps = {
  searchParams: Promise<{ onboarding?: string | string[]; redirectTo?: string | string[] }>;
};

export default async function OAuthSuccessPage({ searchParams }: OAuthSuccessPageProps) {
  const params = await searchParams;
  const redirectTo = safeRedirectTo(params.redirectTo);
  if (isTruthyQueryFlag(params.onboarding)) {
    const query = redirectTo ? `?redirectTo=${encodeURIComponent(redirectTo)}` : "";
    redirect(`/welcome${query}`);
  }
  redirect(redirectTo || "/account");
}
