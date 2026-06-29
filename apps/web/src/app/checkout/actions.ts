"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import { apiSendWithCookie } from "@/lib/api";
import type { AgreementState } from "@/lib/account";
import type { Checkout } from "@/lib/checkout";
import { confirmTossPaymentRequest } from "@/lib/payments";

const ORDER_POLICY_VERSION = "order-2026-06-01";
const REFUND_POLICY_VERSION = "refund-2026-06-01";
const OUT_OF_STOCK_NOTICE_VERSION = "out-of-stock-2026-06-01";
const CONFIRMED_NOTICE_TEXT =
  "주문 상품, 결제 금액, 배송지, 배송/취소/환불 정책, 결제 후 품절 가능성과 품절 시 해당 배송 그룹 주문 금액 환불 안내를 확인했습니다.";

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
    await apiSendWithCookie<AgreementState>(
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
          clientSubmittedTotalAmount: Number(value(formData, "clientSubmittedTotalAmount") || "0"),
        }),
      },
    );
  } catch {
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
          orderPolicyVersion: ORDER_POLICY_VERSION,
          cancellationRefundPolicyVersion: REFUND_POLICY_VERSION,
          outOfStockNoticeVersion: OUT_OF_STOCK_NOTICE_VERSION,
          confirmedNoticeText: CONFIRMED_NOTICE_TEXT,
        }),
      },
    );
  } catch {
    redirect(checkoutDetailMessage(checkoutNumber, "주문서 정책 확인을 저장하지 못했습니다."));
  }

  revalidatePath(`/checkout/${checkoutNumber}`);
  redirect(checkoutDetailMessage(checkoutNumber, "주문서 정책 확인을 저장했습니다."));
}

export async function confirmTossPayment(formData: FormData) {
  const checkoutNumber = value(formData, "checkoutNumber");

  try {
    await confirmTossPaymentRequest(
      checkoutNumber,
      value(formData, "paymentKey"),
      Number(value(formData, "amount") || "0"),
    );
  } catch {
    redirect(`/checkout/payment/exception?checkoutNumber=${encodeURIComponent(checkoutNumber)}`);
  }

  revalidatePath(`/checkout/${checkoutNumber}`);
  redirect(`/checkout/payment/success?checkoutNumber=${encodeURIComponent(checkoutNumber)}`);
}
