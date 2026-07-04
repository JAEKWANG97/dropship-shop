"use server";

import { revalidatePath } from "next/cache";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { ApiError, apiSendWithCookie } from "@/lib/api";
import { safeRedirectTo } from "@/lib/redirect";
import { getCurrentUser } from "@/lib/session";

function value(formData: FormData, name: string) {
  const raw = formData.get(name);
  return typeof raw === "string" ? raw.trim() : "";
}

function destination(formData: FormData) {
  return safeRedirectTo(value(formData, "redirectTo")) || "/account";
}

function welcomeMessage(redirectTo: string, message: string) {
  return `/welcome?redirectTo=${encodeURIComponent(redirectTo)}&message=${encodeURIComponent(message)}`;
}

export async function registerReferral(formData: FormData) {
  const session = await getCurrentUser();
  if (!session) {
    redirect("/login");
  }

  const redirectTo = destination(formData);
  try {
    await apiSendWithCookie("/api/me/referral", (await cookies()).toString(), {
      method: "POST",
      body: JSON.stringify({
        code: value(formData, "code"),
      }),
    });
  } catch (error) {
    const message =
      error instanceof ApiError && error.responseMessage
        ? error.responseMessage
        : "추천인 코드를 등록하지 못했습니다.";
    redirect(welcomeMessage(redirectTo, message));
  }

  revalidatePath("/account");
  redirect(redirectTo);
}

export async function skipReferral(formData: FormData) {
  const session = await getCurrentUser();
  if (!session) {
    redirect("/login");
  }

  redirect(destination(formData));
}
