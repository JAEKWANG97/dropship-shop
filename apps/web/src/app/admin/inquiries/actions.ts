"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { ApiError, apiSendWithCookie } from "@/lib/api";

function text(formData: FormData, name: string) {
  const value = formData.get(name);
  return typeof value === "string" ? value.trim() : "";
}

function id(formData: FormData) {
  return text(formData, "inquiryId");
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError && error.responseMessage ? error.responseMessage : fallback;
}

export async function changeInquiryStatus(formData: FormData) {
  const inquiryId = id(formData);
  let message = "문의 상태를 변경했습니다.";
  try {
    await apiSendWithCookie(`/api/admin/customer-inquiries/${inquiryId}/status`, (await cookies()).toString(), {
      method: "PATCH",
      body: JSON.stringify({ status: text(formData, "status"), adminMemo: text(formData, "adminMemo") }),
    });
  } catch (error) {
    message = errorMessage(error, "문의 상태 변경에 실패했습니다.");
  }
  redirect(`/admin/inquiries/${inquiryId}?message=${encodeURIComponent(message)}`);
}

export async function answerInquiry(formData: FormData) {
  const inquiryId = id(formData);
  let message = "답변을 저장했습니다. 이메일 발송 상태를 확인하세요.";
  try {
    await apiSendWithCookie(`/api/admin/customer-inquiries/${inquiryId}/answer`, (await cookies()).toString(), {
      method: "POST",
      body: JSON.stringify({ answer: text(formData, "answer"), adminMemo: text(formData, "adminMemo") }),
    });
  } catch (error) {
    message = errorMessage(error, "문의 답변 저장에 실패했습니다.");
  }
  redirect(`/admin/inquiries/${inquiryId}?message=${encodeURIComponent(message)}`);
}

export async function retryInquiryEmail(formData: FormData) {
  const inquiryId = id(formData);
  const notificationId = text(formData, "notificationId");
  let message = "답변 이메일을 다시 발송했습니다.";
  try {
    await apiSendWithCookie(`/api/admin/notifications/${notificationId}/retry`, (await cookies()).toString(), {
      method: "POST",
    });
  } catch (error) {
    message = errorMessage(error, "답변 이메일 재시도에 실패했습니다.");
  }
  redirect(`/admin/inquiries/${inquiryId}?message=${encodeURIComponent(message)}`);
}
