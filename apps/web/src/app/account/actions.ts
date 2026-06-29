"use server";

import { revalidatePath } from "next/cache";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { apiSendWithCookie } from "@/lib/api";

function value(formData: FormData, name: string) {
  const raw = formData.get(name);
  return typeof raw === "string" ? raw.trim() : "";
}

function accountMessage(message: string) {
  return `/account?message=${encodeURIComponent(message)}`;
}

export async function updateProfile(formData: FormData) {
  try {
    await apiSendWithCookie("/api/me/profile", (await cookies()).toString(), {
      method: "PATCH",
      body: JSON.stringify({
        displayName: value(formData, "displayName"),
        email: value(formData, "email"),
      }),
    });
  } catch {
    redirect(accountMessage("기본 정보를 저장하지 못했습니다."));
  }

  revalidatePath("/account");
  redirect(accountMessage("기본 정보를 저장했습니다."));
}

export async function requestPhoneVerification(formData: FormData) {
  try {
    await apiSendWithCookie("/api/me/phone-verifications", (await cookies()).toString(), {
      method: "POST",
      body: JSON.stringify({
        phoneNumber: value(formData, "phoneNumber"),
      }),
    });
  } catch {
    redirect(accountMessage("인증번호를 발송하지 못했습니다. 번호 또는 재발송 시간을 확인해 주세요."));
  }

  revalidatePath("/account");
  redirect(accountMessage("인증번호를 발송했습니다."));
}

export async function confirmPhoneVerification(formData: FormData) {
  try {
    await apiSendWithCookie("/api/me/phone-verifications/confirm", (await cookies()).toString(), {
      method: "POST",
      body: JSON.stringify({
        phoneNumber: value(formData, "phoneNumber"),
        code: value(formData, "code"),
      }),
    });
  } catch {
    redirect(accountMessage("휴대폰 인증에 실패했습니다."));
  }

  revalidatePath("/account");
  redirect(accountMessage("휴대폰 인증을 완료했습니다."));
}
