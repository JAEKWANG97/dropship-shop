import { redirect } from "next/navigation";

type OAuthSuccessPageProps = {
  searchParams: Promise<{ redirectTo?: string | string[] }>;
};

export default async function OAuthSuccessPage({ searchParams }: OAuthSuccessPageProps) {
  const params = await searchParams;
  redirect(safeRedirectTo(params.redirectTo) || "/account");
}

function safeRedirectTo(value: string | string[] | undefined) {
  const redirectTo = Array.isArray(value) ? value[0] : value;
  if (!redirectTo || !redirectTo.startsWith("/") || redirectTo.startsWith("//")) {
    return "";
  }
  const normalized = redirectTo.toLowerCase();
  if (redirectTo.includes("\\") || normalized.includes("%5c") || /[\r\n]/.test(redirectTo)) {
    return "";
  }
  return redirectTo;
}
