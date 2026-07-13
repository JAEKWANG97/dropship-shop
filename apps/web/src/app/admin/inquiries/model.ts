export type InquiryStatus = "RECEIVED" | "IN_PROGRESS" | "ANSWERED" | "CLOSED";

export type AdminInquiry = {
  inquiryId: string;
  customerName: string;
  email: string;
  phone: string | null;
  subject: string;
  message: string;
  status: InquiryStatus;
  consentPolicyVersion: string | null;
  consentedAt: string | null;
  retentionExpiresAt: string;
  adminMemo: string | null;
  answer: string | null;
  handledByAdminId: string | null;
  answeredAt: string | null;
  closedAt: string | null;
  createdAt: string;
  updatedAt: string;
  latestAnswerNotification: {
    notificationId: string;
    status: "PENDING" | "SENT" | "FAILED" | "SKIPPED";
    failureReason: string | null;
    sentAt: string | null;
    createdAt: string;
  } | null;
};

export const inquiryStatusLabel: Record<InquiryStatus, string> = {
  RECEIVED: "접수",
  IN_PROGRESS: "처리 중",
  ANSWERED: "답변 완료",
  CLOSED: "종료",
};
