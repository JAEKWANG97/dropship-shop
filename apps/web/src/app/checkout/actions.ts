"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import { ApiError, apiSendWithCookie } from "@/lib/api";
import type { Checkout } from "@/lib/checkout";

function value(formData: FormData, name: string) {
  const raw = formData.get(name);
  return typeof raw === "string" ? raw : "";
}

function checkoutMessage(message: string) {
  return `/checkout?message=${encodeURIComponent(message)}`;
}

function checkoutDetailMessage(checkoutNumber: string, message: string) {
  return `/checkout/${checkoutNumber}?message=${encodeURIComponent(message)}`;
}

function shippingAddress(formData: FormData) {
  return {
    recipientName: value(formData, "recipientName"),
    recipientPhone: value(formData, "recipientPhone"),
    postalCode: value(formData, "postalCode"),
    address1: value(formData, "address1"),
    address2: value(formData, "address2"),
  };
}

export async function agreeRequiredPolicies(formData: FormData) {
  try {
    await apiSendWithCookie(
      "/api/me/agreements",
      (await cookies()).toString(),
      {
        method: "POST",
        body: JSON.stringify({
          termsAgreed: value(formData, "termsAgreed") === "on",
          privacyAgreed: value(formData, "privacyAgreed") === "on",
          termsVersion: value(formData, "termsVersion"),
          privacyVersion: value(formData, "privacyVersion"),
        }),
      },
    );
  } catch {
    redirect(checkoutMessage("필수 약관 동의를 저장하지 못했습니다."));
  }

  revalidatePath("/checkout");
  redirect(checkoutMessage("필수 약관 동의를 저장했습니다."));
}

export async function createCheckout(formData: FormData) {
  let checkout: Checkout;

  try {
    checkout = await apiSendWithCookie<Checkout>(
      "/api/checkouts",
      (await cookies()).toString(),
      {
        method: "POST",
        body: JSON.stringify({
          ...shippingAddress(formData),
          depositorName: value(formData, "depositorName"),
          clientSubmittedTotalAmount: Number(value(formData, "clientSubmittedTotalAmount") || "0"),
        }),
      },
    );
  } catch (error) {
    if (error instanceof ApiError && error.responseMessage.includes("already submitted")) {
      redirect(checkoutMessage("이미 주문이 접수되었습니다. 주문서 또는 장바구니를 확인해 주세요."));
    }
    if (error instanceof ApiError && error.status === 409) {
      redirect(checkoutMessage(error.responseMessage));
    }
    redirect(checkoutMessage("주문서를 생성하지 못했습니다. 장바구니와 약관 동의를 확인해 주세요."));
  }

  revalidatePath("/cart");
  redirect(`/checkout/${checkout.checkoutNumber}`);
}

export async function updateCheckoutShippingAddress(formData: FormData) {
  const checkoutNumber = value(formData, "checkoutNumber");

  try {
    await apiSendWithCookie<Checkout>(
      `/api/checkouts/${checkoutNumber}/shipping-address`,
      (await cookies()).toString(),
      {
        method: "PATCH",
        body: JSON.stringify(shippingAddress(formData)),
      },
    );
  } catch {
    redirect(checkoutDetailMessage(checkoutNumber, "배송지를 변경하지 못했습니다."));
  }

  revalidatePath(`/checkout/${checkoutNumber}`);
  redirect(checkoutDetailMessage(checkoutNumber, "배송지를 변경했습니다."));
}

export async function confirmCheckoutPolicies(formData: FormData) {
  const checkoutNumber = value(formData, "checkoutNumber");

  try {
    await apiSendWithCookie(
      `/api/checkouts/${checkoutNumber}/policy-confirmation`,
      (await cookies()).toString(),
      {
        method: "POST",
        body: JSON.stringify({
          termsVersion: value(formData, "termsVersion"),
          privacyVersion: value(formData, "privacyVersion"),
          orderPolicyVersion: value(formData, "orderPolicyVersion"),
          cancellationRefundPolicyVersion: value(
            formData,
            "cancellationRefundPolicyVersion",
          ),
          outOfStockNoticeVersion: value(formData, "outOfStockNoticeVersion"),
        }),
      },
    );
  } catch {
    redirect(checkoutDetailMessage(checkoutNumber, "주문서 정책 확인을 저장하지 못했습니다."));
  }

  revalidatePath(`/checkout/${checkoutNumber}`);
  redirect(checkoutDetailMessage(checkoutNumber, "주문서 정책 확인을 저장했습니다."));
}
