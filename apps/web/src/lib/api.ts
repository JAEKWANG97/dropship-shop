export const API_BASE_URL =
  process.env.DROPSHIP_API_BASE_URL ?? "http://localhost:8080";

export function apiUrl(path: string) {
  return `${API_BASE_URL}${path.startsWith("/") ? path : `/${path}`}`;
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
