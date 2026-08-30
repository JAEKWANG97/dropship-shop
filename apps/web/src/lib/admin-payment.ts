export type AdminDepositCommand = {
  actualDepositorName: string;
  actualAmount: number;
  depositedAt: string;
  transactionReference: string;
  reason: string;
};

export type RetryCommand = { action?: string; key?: string };

export const ADMIN_DEPOSIT_PATHS = {
  confirm: "/confirm-deposit",
  mismatch: "/deposit-mismatch",
  late: "/late-deposit",
} as const;

export const ADMIN_REFUND_PATHS = {
  approve: "/approve",
  manualComplete: "/manual-complete",
} as const;

export function adminDepositCommand(formData: FormData): AdminDepositCommand {
  return {
    actualDepositorName: field(formData, "actualDepositorName"),
    actualAmount: Number(field(formData, "actualAmount")),
    depositedAt: koreanLocalDateTime(field(formData, "depositedAt")),
    transactionReference: field(formData, "transactionReference"),
    reason: field(formData, "reason"),
  };
}

export function refundApprovalCommand(formData: FormData) {
  return { reason: field(formData, "reason") };
}

export function adminRefundNextAction(status: string | null | undefined) {
  if (status === "REQUESTED") return "APPROVE";
  if (status === "APPROVED") return "MANUAL_COMPLETE";
  return null;
}

export function idempotencyHeaders(key: string) {
  return key ? { "Idempotency-Key": key } : undefined;
}

export function retryCommandKey(retry: RetryCommand, action: string) {
  return retry.action === action && isUuid(retry.key) ? retry.key : null;
}

export function koreanLocalDateTime(value: string) {
  return value ? `${value}:00+09:00` : "";
}

function field(formData: FormData, name: string) {
  const raw = formData.get(name);
  return typeof raw === "string" ? raw.trim() : "";
}

function isUuid(value: string | undefined): value is string {
  return Boolean(value && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value));
}
