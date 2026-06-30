"use server";

import { redirect } from "next/navigation";
import { apiUrl } from "@/lib/api";

function text(formData: FormData, name: string) {
  const value = formData.get(name);
  return typeof value === "string" ? value.trim() : "";
}

export async function createCustomerInquiry(formData: FormData) {
  let message = "문의가 접수되었습니다. 운영자가 확인 후 답변합니다.";
  try {
    const response = await fetch(apiUrl("/api/customer-inquiries"), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        customerName: text(formData, "customerName"),
        email: text(formData, "email"),
        phone: text(formData, "phone"),
        subject: text(formData, "subject"),
        message: text(formData, "message"),
      }),
      cache: "no-store",
    });
    if (!response.ok) {
      throw new Error(`Inquiry request failed: ${response.status}`);
    }
  } catch {
    message = "문의 접수에 실패했습니다. 입력값을 확인한 뒤 다시 시도해 주세요.";
  }

  redirect(`/support?message=${encodeURIComponent(message)}`);
}
