import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { apiUrl } from "@/lib/api";

export async function POST(request: Request) {
  const cookieHeader = (await cookies()).toString();
  const response = await fetch(apiUrl("/api/auth/logout"), {
    method: "POST",
    headers: cookieHeader ? { Cookie: cookieHeader } : {},
    cache: "no-store",
  });

  const nextResponse = NextResponse.redirect(new URL("/login", request.url));
  const setCookie = response.headers.get("set-cookie");
  if (setCookie) {
    nextResponse.headers.set("set-cookie", setCookie);
  }
  return nextResponse;
}
