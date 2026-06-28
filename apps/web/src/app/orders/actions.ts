"use server";

import { revalidatePath } from "next/cache";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { apiSendWithCookie } from "@/lib/api";
import type { OrderDetail } from "@/lib/orders";

function value(formData: FormData, name: string) {
  const raw = formData.get(name);
  return typeof raw === "string" ? raw : "";
}

function orderMessage(orderId: string, message: string) {
  return `/orders/${orderId}?message=${encodeURIComponent(message)}`;
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

export async function updateOrderShippingAddress(formData: FormData) {
  const orderId = value(formData, "orderId");

  try {
    await apiSendWithCookie<OrderDetail>(
      `/api/orders/${orderId}/shipping-address`,
      (await cookies()).toString(),
      {
        method: "PATCH",
        body: JSON.stringify(shippingAddress(formData)),
      },
    );
  } catch {
    redirect(orderMessage(orderId, "배송지를 변경하지 못했습니다."));
  }

  revalidatePath(`/orders/${orderId}`);
  redirect(orderMessage(orderId, "배송지를 변경했습니다."));
}

export async function cancelOrder(formData: FormData) {
  const orderId = value(formData, "orderId");

  try {
    await apiSendWithCookie(`/api/orders/${orderId}/cancel`, (await cookies()).toString(), {
      method: "POST",
      body: JSON.stringify({ reason: value(formData, "reason") }),
    });
  } catch {
    redirect(orderMessage(orderId, "취소 요청을 접수하지 못했습니다."));
  }

  revalidatePath(`/orders/${orderId}`);
  redirect(orderMessage(orderId, "취소 요청을 접수했습니다."));
}

export async function createClaim(formData: FormData) {
  const orderId = value(formData, "orderId");

  try {
    await apiSendWithCookie(`/api/orders/${orderId}/claims`, (await cookies()).toString(), {
      method: "POST",
      body: JSON.stringify({
        claimType: value(formData, "claimType"),
        claimReason: value(formData, "claimReason"),
        customerMemo: value(formData, "customerMemo"),
      }),
    });
  } catch {
    redirect(orderMessage(orderId, "클레임을 접수하지 못했습니다."));
  }

  revalidatePath(`/orders/${orderId}`);
  redirect(orderMessage(orderId, "클레임을 접수했습니다."));
}
