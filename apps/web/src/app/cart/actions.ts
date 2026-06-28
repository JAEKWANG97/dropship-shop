"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import { apiSendWithCookie } from "@/lib/api";
import type { Cart, CartValidation } from "@/lib/cart";

function value(formData: FormData, name: string) {
  const raw = formData.get(name);
  return typeof raw === "string" ? raw : "";
}

function messagePath(message: string) {
  return `/cart?message=${encodeURIComponent(message)}`;
}

export async function addCartItem(formData: FormData) {
  const productOptionId = value(formData, "productOptionId");
  const quantity = Number(value(formData, "quantity") || "1");

  try {
    await apiSendWithCookie<Cart>("/api/cart/items", (await cookies()).toString(), {
      method: "POST",
      body: JSON.stringify({ productOptionId, quantity }),
    });
  } catch {
    redirect(messagePath("장바구니에 담지 못했습니다. 로그인과 상품 상태를 확인해 주세요."));
  }

  revalidatePath("/cart");
  redirect("/cart?message=" + encodeURIComponent("장바구니에 담았습니다."));
}

export async function updateCartItem(formData: FormData) {
  const cartItemId = value(formData, "cartItemId");
  const quantity = Number(value(formData, "quantity") || "1");

  try {
    await apiSendWithCookie<Cart>(
      `/api/cart/items/${cartItemId}`,
      (await cookies()).toString(),
      {
        method: "PATCH",
        body: JSON.stringify({ quantity }),
      },
    );
  } catch {
    redirect(messagePath("수량을 변경하지 못했습니다."));
  }

  revalidatePath("/cart");
  redirect(messagePath("수량을 변경했습니다."));
}

export async function removeCartItem(formData: FormData) {
  const cartItemId = value(formData, "cartItemId");

  try {
    await apiSendWithCookie<null>(
      `/api/cart/items/${cartItemId}`,
      (await cookies()).toString(),
      { method: "DELETE" },
    );
  } catch {
    redirect(messagePath("상품을 삭제하지 못했습니다."));
  }

  revalidatePath("/cart");
  redirect(messagePath("상품을 삭제했습니다."));
}

export async function validateCart() {
  let validation: CartValidation;

  try {
    validation = await apiSendWithCookie<CartValidation>(
      "/api/cart/validate",
      (await cookies()).toString(),
      { method: "POST" },
    );
  } catch {
    redirect(messagePath("장바구니를 검증하지 못했습니다."));
  }

  const message = validation.checkoutAvailable
    ? "주문 가능한 장바구니입니다."
    : validation.issues[0]?.message ?? "주문할 수 없는 항목이 있습니다.";

  revalidatePath("/cart");
  redirect(messagePath(message));
}
