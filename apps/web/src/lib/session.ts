import { cookies } from "next/headers";
import { ApiError, apiGetWithCookie } from "./api";

export type UserSession = {
  userId: string;
};

async function getSession(path: string): Promise<UserSession | null> {
  const cookieHeader = (await cookies()).toString();
  try {
    return await apiGetWithCookie<UserSession>(path, cookieHeader);
  } catch (error) {
    if (error instanceof ApiError || error instanceof TypeError) {
      return null;
    }
    throw error;
  }
}

export function getCurrentUser() {
  return getSession("/api/me");
}

export function getAdminUser() {
  return getSession("/api/admin/me");
}
