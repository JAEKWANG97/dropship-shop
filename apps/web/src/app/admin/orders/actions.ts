"use server";

import { revalidatePath } from "next/cache";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { apiSendWithCookie } from "@/lib/api";

function value(formData: FormData, name: string) {
  const raw = formData.get(name);
  return typeof raw === "string" ? raw.trim() : "";
}

function done(orderId: string, message: string) {
  redirect(`/admin/orders?orderId=${encodeURIComponent(orderId)}&message=${encodeURIComponent(message)}`);
}

async function postOrderAction(orderId: string, path: string, body: Record<string, string>) {
  await apiSendWithCookie(`/api/admin/orders/${orderId}${path}`, (await cookies()).toString(), {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function startSupplierWork(formData: FormData) {
  const orderId = value(formData, "orderId");

  try {
    await postOrderAction(orderId, "/supplier-work-start", { reason: value(formData, "reason") });
  } catch {
    done(orderId, "발주 시작 처리에 실패했습니다.");
  }

  revalidatePath("/admin/orders");
  done(orderId, "발주 시작 처리했습니다.");
}

export async function completeSupplierOrder(formData: FormData) {
  const orderId = value(formData, "orderId");

  try {
    await postOrderAction(orderId, "/supplier-order-completed", {
      supplierOrderNumber: value(formData, "supplierOrderNumber"),
      expectedShipDate: value(formData, "expectedShipDate"),
      supplierResponseMemo: value(formData, "supplierResponseMemo"),
      reason: value(formData, "reason"),
    });
  } catch {
    done(orderId, "공급처 발주 완료 처리에 실패했습니다.");
  }

  revalidatePath("/admin/orders");
  done(orderId, "공급처 발주 완료 처리했습니다.");
}

export async function markOrderOutOfStock(formData: FormData) {
  const orderId = value(formData, "orderId");

  try {
    await postOrderAction(orderId, "/out-of-stock", { reason: value(formData, "reason") });
  } catch {
    done(orderId, "공급처 품절 처리에 실패했습니다.");
  }

  revalidatePath("/admin/orders");
  done(orderId, "공급처 품절 처리했습니다.");
}

export async function createOrderShipment(formData: FormData) {
  const orderId = value(formData, "orderId");

  try {
    await postOrderAction(orderId, "/shipments", {
      carrier: value(formData, "carrier"),
      trackingNumber: value(formData, "trackingNumber"),
    });
  } catch {
    done(orderId, "송장 입력에 실패했습니다.");
  }

  revalidatePath("/admin/orders");
  done(orderId, "송장을 입력했습니다.");
}
