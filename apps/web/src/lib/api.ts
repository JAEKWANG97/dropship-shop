export const API_BASE_URL =
  process.env.DROPSHIP_API_BASE_URL ?? "http://localhost:8080";

export const PUBLIC_API_BASE_URL =
  process.env.NEXT_PUBLIC_DROPSHIP_API_BASE_URL ?? "";

export function apiUrl(path: string) {
  return `${API_BASE_URL}${path.startsWith("/") ? path : `/${path}`}`;
}

export function publicApiUrl(path: string) {
  return `${PUBLIC_API_BASE_URL}${path.startsWith("/") ? path : `/${path}`}`;
}

export class ApiError extends Error {
  constructor(public readonly status: number) {
    super(`API request failed: ${status}`);
  }
}

export async function apiGet<T>(path: string) {
  const response = await fetch(apiUrl(path), { cache: "no-store" });

  if (!response.ok) {
    throw new ApiError(response.status);
  }

  return response.json() as Promise<T>;
}

export async function apiGetWithCookie<T>(path: string, cookieHeader: string) {
  const response = await fetch(apiUrl(path), {
    headers: cookieHeader ? { Cookie: cookieHeader } : {},
    cache: "no-store",
  });

  if (!response.ok) {
    throw new ApiError(response.status);
  }

  return response.json() as Promise<T>;
}

export async function apiSendWithCookie<T>(
  path: string,
  cookieHeader: string,
  init: RequestInit,
) {
  const response = await fetch(apiUrl(path), {
    ...init,
    headers: {
      ...(init.body ? { "Content-Type": "application/json" } : {}),
      ...(cookieHeader ? { Cookie: cookieHeader } : {}),
      ...init.headers,
    },
    cache: "no-store",
  });

  if (!response.ok) {
    throw new ApiError(response.status);
  }

  if (response.status === 204) {
    return null as T;
  }

  return response.json() as Promise<T>;
}
