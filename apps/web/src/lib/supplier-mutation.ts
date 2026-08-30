export function supplierMutationHeaders(idempotencyKey?: string, configuredOrigin?: string) {
  const origin = configuredOrigin?.trim()
    || process.env.DROPSHIP_WEB_ORIGIN?.trim()
    || process.env.APP_PUBLIC_BASE_URL?.trim()
    || "http://localhost:3000";
  const headers: Record<string, string> = { Origin: new URL(origin).origin };
  if (idempotencyKey) headers["Idempotency-Key"] = idempotencyKey;
  return headers;
}
