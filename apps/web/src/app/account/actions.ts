"use server";

import { revalidatePath } from "next/cache";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { ApiError, apiSendWithCookie } from "@/lib/api";

function value(formData: FormData, name: string) {
  const raw = formData.get(name);
  return typeof raw === "string" ? raw.trim() : "";
}

function accountMessage(message: string) {
  return `/account?message=${encodeURIComponent(message)}`;
}

function addressRequest(formData: FormData) {
  return {
    recipientName: value(formData, "recipientName"),
    recipientPhone: value(formData, "recipientPhone"),
    postalCode: value(formData, "postalCode"),
    address1: value(formData, "address1"),
    address2: value(formData, "address2"),
    defaultAddress: formData.get("defaultAddress") === "true",
  };
}

function refreshAddresses() {
  revalidatePath("/account");
  revalidatePath("/checkout");
}

export async function updateProfile(formData: FormData) {
  try {
    await apiSendWithCookie("/api/me/profile", (await cookies()).toString(), {
      method: "PATCH",
      body: JSON.stringify({
        displayName: value(formData, "displayName"),
        email: value(formData, "email"),
        phoneNumber: value(formData, "phoneNumber"),
      }),
    });
  } catch {
    redirect(accountMessage("기본 정보를 저장하지 못했습니다."));
  }

  revalidatePath("/account");
  redirect(accountMessage("기본 정보를 저장했습니다."));
}

export async function createAddress(formData: FormData) {
  try {
    await apiSendWithCookie("/api/me/addresses", (await cookies()).toString(), {
      method: "POST",
      body: JSON.stringify(addressRequest(formData)),
    });
  } catch {
    redirect(accountMessage("배송지를 저장하지 못했습니다."));
  }

  refreshAddresses();
  redirect(accountMessage("배송지를 저장했습니다."));
}

export async function updateAddress(formData: FormData) {
  const addressId = value(formData, "addressId");
  try {
    await apiSendWithCookie(`/api/me/addresses/${addressId}`, (await cookies()).toString(), {
      method: "PATCH",
      body: JSON.stringify(addressRequest(formData)),
    });
  } catch {
    redirect(accountMessage("배송지를 수정하지 못했습니다."));
  }

  refreshAddresses();
  redirect(accountMessage("배송지를 수정했습니다."));
}

export async function deleteAddress(formData: FormData) {
  const addressId = value(formData, "addressId");
  try {
    await apiSendWithCookie<null>(`/api/me/addresses/${addressId}`, (await cookies()).toString(), {
      method: "DELETE",
    });
  } catch {
    redirect(accountMessage("배송지를 삭제하지 못했습니다."));
  }

  refreshAddresses();
  redirect(accountMessage("배송지를 삭제했습니다."));
}

export async function requestAccountDeletion(formData: FormData) {
  if (value(formData, "confirmDeletion") !== "yes") {
    redirect(accountMessage("회원 탈퇴 안내 확인이 필요합니다."));
  }

  const cookieStore = await cookies();
  try {
    await apiSendWithCookie<null>("/api/me/deletion-request", cookieStore.toString(), {
      method: "POST",
    });
  } catch (error) {
    const message =
      error instanceof ApiError && error.responseMessage
        ? error.responseMessage
        : "회원 탈퇴 요청을 처리하지 못했습니다.";
    redirect(accountMessage(message));
  }

  cookieStore.delete("ACCESS_TOKEN");
  revalidatePath("/account");
  redirect("/");
}
