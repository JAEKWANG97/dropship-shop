import { cookies } from "next/headers";
import { apiUrl } from "./api";

export type UserSession = {
  userId: string;
};

async function getSession(path: string): Promise<UserSession | null> {
  const cookieHeader = (await cookies()).toString();
  const response = await fetch(apiUrl(path), {
    headers: cookieHeader ? { Cookie: cookieHeader } : {},
    cache: "no-store",
  });

  if (!response.ok) {
    return null;
  }

  return response.json() as Promise<UserSession>;
}

export function getCurrentUser() {
  return getSession("/api/me");
}

export function getAdminUser() {
  return getSession("/api/admin/me");
}
