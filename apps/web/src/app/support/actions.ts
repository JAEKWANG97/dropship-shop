"use server";

import { redirect } from "next/navigation";
import { apiUrl } from "@/lib/api";

type CreatedInquiry = {
  inquiryId: string;
  lookupToken: string;
};

function text(formData: FormData, name: string) {
  const value = formData.get(name);
  return typeof value === "string" ? value.trim() : "";
}

export async function createCustomerInquiry(formData: FormData) {
  let destination = "/support?message=" + encodeURIComponent("문의 접수에 실패했습니다. 입력값을 확인한 뒤 다시 시도해 주세요.");
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
        privacyConsent: formData.get("privacyConsent") === "true",
      }),
      cache: "no-store",
    });
    if (!response.ok) {
      if (response.status === 429) {
        destination = "/support?message=" + encodeURIComponent("문의가 연속으로 접수되었습니다. 10분 뒤 다시 시도해 주세요.");
      }
      throw new Error(`Inquiry request failed: ${response.status}`);
    }
    const inquiry = (await response.json()) as CreatedInquiry;
    destination = `/support/inquiries/${inquiry.inquiryId}#token=${encodeURIComponent(inquiry.lookupToken)}`;
  } catch {
    // destination already contains the customer-safe failure message.
  }

  redirect(destination);
}
