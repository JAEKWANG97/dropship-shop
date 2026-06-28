export const API_BASE_URL =
  process.env.DROPSHIP_API_BASE_URL ?? "http://localhost:8080";

export function apiUrl(path: string) {
  return `${API_BASE_URL}${path.startsWith("/") ? path : `/${path}`}`;
}
