"use server";

import { revalidatePath } from "next/cache";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { apiSendWithCookie, apiUrl } from "@/lib/api";

function value(formData: FormData, name: string) {
  const raw = formData.get(name);
  return typeof raw === "string" ? raw : "";
}

function orderMessage(orderId: string, message: string) {
  return `/orders/${orderId}?message=${encodeURIComponent(message)}`;
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
  const cookieHeader = (await cookies()).toString();
  const request = new FormData();
  request.set("claimType", value(formData, "claimType"));
  request.set("claimReason", value(formData, "claimReason"));
  request.set("customerMemo", value(formData, "customerMemo"));

  formData.getAll("evidenceFiles").forEach((file) => {
    if (file instanceof File && file.size > 0) {
      request.append("evidenceFiles", file);
    }
  });

  try {
    const response = await fetch(apiUrl(`/api/orders/${orderId}/claims`), {
      method: "POST",
      headers: cookieHeader ? { Cookie: cookieHeader } : {},
      body: request,
      cache: "no-store",
    });
    if (!response.ok) {
      throw new Error(await responseMessage(response));
    }
  } catch (error) {
    const message = error instanceof Error && error.message ? error.message : "클레임을 접수하지 못했습니다.";
    redirect(orderMessage(orderId, message));
  }

  revalidatePath(`/orders/${orderId}`);
  redirect(orderMessage(orderId, "클레임을 접수했습니다."));
}

async function responseMessage(response: Response) {
  if (response.headers.get("content-type")?.includes("application/json")) {
    try {
      const body = (await response.json()) as { message?: unknown };
      if (typeof body.message === "string" && body.message) {
        return body.message;
      }
    } catch {
      return "클레임을 접수하지 못했습니다.";
    }
  }
  return "클레임을 접수하지 못했습니다.";
}
