export function safeRedirectTo(value: string | string[] | undefined) {
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

export function isTruthyQueryFlag(value: string | string[] | undefined) {
  const flag = Array.isArray(value) ? value[0] : value;
  return flag === "1" || flag === "true";
}
