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

type ApiErrorBody = {
  code?: unknown;
  message?: unknown;
};

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly responseMessage = "",
    public readonly responseCode = "",
  ) {
    super(responseMessage || `API request failed: ${status}`);
  }
}

async function apiError(response: Response) {
  let responseCode = "";
  let responseMessage = "";
  if (response.headers.get("content-type")?.includes("application/json")) {
    try {
      const body = (await response.json()) as ApiErrorBody;
      responseCode = typeof body.code === "string" ? body.code : "";
      responseMessage = typeof body.message === "string" ? body.message : "";
    } catch {
      responseMessage = "";
    }
  }
  return new ApiError(response.status, responseMessage, responseCode);
}

export async function apiGet<T>(path: string) {
  const response = await fetch(apiUrl(path), { cache: "no-store" });

  if (!response.ok) {
    throw await apiError(response);
  }

  return response.json() as Promise<T>;
}

export async function apiGetWithCookie<T>(path: string, cookieHeader: string) {
  const response = await fetch(apiUrl(path), {
    headers: cookieHeader ? { Cookie: cookieHeader } : {},
    cache: "no-store",
  });

  if (!response.ok) {
    throw await apiError(response);
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
    throw await apiError(response);
  }

  if (response.status === 204) {
    return null as T;
  }

  return response.json() as Promise<T>;
}
