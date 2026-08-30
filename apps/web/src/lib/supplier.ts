import "server-only";

import { cookies } from "next/headers";
import { apiGet, apiGetWithCookie } from "./api";
export { supplierMutationHeaders } from "./supplier-mutation";

export type SupplierApplicationPolicy = {
  title: string;
  content: string;
  version: string;
  effectiveFrom: string | null;
};

export type SupplierApplication = {
  applicationId: string;
  supplierName: string | null;
  contactName: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  memo: string | null;
  status: string;
  consentPolicyVersion: string;
  consentedAt: string | null;
  retentionExpiresAt: string | null;
  anonymizedAt: string | null;
  reviewedByAdminId: string | null;
  reviewedAt: string | null;
  reviewReasonCode: string | null;
  reviewReason: string | null;
  approvalMode: string | null;
  requestedExistingSupplierId: string | null;
  approvedSupplierId: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type SupplierApplicationPage = {
  applications: SupplierApplication[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type SupplierPortalSummary = {
  supplierId: string;
  name: string;
  portalStatus: string;
  salesStatus: string;
  contractStatus: string;
  managerUserId: string | null;
  contactEmail: string | null;
  contactEmailVerifiedAt: string | null;
};

type JsonRecord = Record<string, unknown>;

export function supplierPortalEnabled() {
  const configured = process.env.APP_SUPPLIER_PORTAL_ENABLED;
  if (configured !== undefined) {
    return ["1", "true", "yes", "on"].includes(configured.trim().toLowerCase());
  }
  return process.env.NODE_ENV !== "production";
}

export async function getSupplierApplicationPolicy() {
  const value = await apiGet<unknown>("/api/policies/SUPPLIER_APPLICATION_PRIVACY/current");
  const policy = record(value);
  return {
    title: text(policy.title) || "공급처 신청 개인정보 수집·이용 안내",
    content: text(policy.content),
    version: text(policy.version),
    effectiveFrom: nullableText(policy.effectiveFrom),
  } satisfies SupplierApplicationPolicy;
}

export async function getSupplierSession() {
  const value = await readWithCookie<unknown>("/api/supplier/me");
  return portalSummary(value);
}

export async function getAdminSupplierApplications(params: {
  status?: string;
  page?: number;
  size?: number;
} = {}) {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? 20));
  const value = await readWithCookie<unknown>(`/api/admin/supplier-applications?${query}`);
  const wrapper = record(value);
  const rawApplications = Array.isArray(value)
    ? value
    : Array.isArray(wrapper.applications)
      ? wrapper.applications
      : [];
  const applications = rawApplications.map(application);
  const size = number(wrapper.size, params.size ?? applications.length);
  return {
    applications,
    page: number(wrapper.page, params.page ?? 0),
    size,
    totalElements: number(wrapper.totalElements, applications.length),
    totalPages: number(wrapper.totalPages, applications.length === 0 ? 0 : Math.max(1, Math.ceil(applications.length / Math.max(size, 1)))),
  } satisfies SupplierApplicationPage;
}

export async function getAdminSupplierApplication(applicationId: string) {
  const value = await readWithCookie<unknown>(
    `/api/admin/supplier-applications/${encodeURIComponent(applicationId)}`,
  );
  return application(value);
}

export async function getAdminPortalSuppliers() {
  const value = await readWithCookie<unknown>("/api/admin/suppliers");
  const wrapper = record(value);
  const rawSuppliers = Array.isArray(value)
    ? value
    : Array.isArray(wrapper.suppliers)
      ? wrapper.suppliers
      : [];
  return rawSuppliers.map(portalSummary);
}

export async function getAdminPortalSupplier(supplierId: string) {
  const value = await readWithCookie<unknown>(`/api/admin/suppliers/${encodeURIComponent(supplierId)}`);
  return portalSummary(value);
}

export function supplierStatusLabel(status: string) {
  return ({
    ACTIVE: "활성",
    INACTIVE: "판매 중지",
    PENDING_ACTIVATION: "담당자 연결 대기",
    SUSPENDED: "접근 정지",
    DISABLED: "영구 종료",
    UNVERIFIED: "계약 확인 전",
    VERIFIED: "계약 확인됨",
    EXPIRED: "만료",
    REVOKED: "계약 해지",
    SUBMITTED: "검토 대기",
    APPROVED: "승인",
    REJECTED: "거절",
  } as Record<string, string>)[status] ?? status;
}

async function readWithCookie<T>(path: string) {
  return apiGetWithCookie<T>(path, (await cookies()).toString());
}

function application(value: unknown): SupplierApplication {
  const item = record(value);
  return {
    applicationId: text(item.applicationId) || text(item.id),
    supplierName: nullableText(item.supplierName),
    contactName: nullableText(item.contactName),
    contactEmail: nullableText(item.contactEmail),
    contactPhone: nullableText(item.contactPhone),
    memo: nullableText(item.memo),
    status: text(item.status) || "SUBMITTED",
    consentPolicyVersion: text(item.consentPolicyVersion),
    consentedAt: nullableText(item.consentedAt),
    retentionExpiresAt: nullableText(item.retentionExpiresAt),
    anonymizedAt: nullableText(item.anonymizedAt),
    reviewedByAdminId: nullableText(item.reviewedByAdminId),
    reviewedAt: nullableText(item.reviewedAt),
    reviewReasonCode: nullableText(item.reviewReasonCode),
    reviewReason: nullableText(item.reviewReason) || nullableText(item.internalReason),
    approvalMode: nullableText(item.approvalMode),
    requestedExistingSupplierId: nullableText(item.requestedExistingSupplierId) || nullableText(item.existingSupplierId),
    approvedSupplierId: nullableText(item.approvedSupplierId) || nullableText(item.supplierId),
    createdAt: nullableText(item.createdAt),
    updatedAt: nullableText(item.updatedAt),
  };
}

function portalSummary(value: unknown): SupplierPortalSummary {
  const item = record(value);
  return {
    supplierId: text(item.supplierId) || text(item.id),
    name: text(item.name) || text(item.supplierName) || "공급처",
    portalStatus: text(item.portalStatus) || "DISABLED",
    salesStatus: text(item.salesStatus) || text(item.status) || "INACTIVE",
    contractStatus: text(item.contractStatus) || text(item.portalContractStatus) || "UNVERIFIED",
    managerUserId: nullableText(item.managerUserId),
    contactEmail: nullableText(item.contactEmail) || nullableText(item.email),
    contactEmailVerifiedAt: nullableText(item.contactEmailVerifiedAt),
  };
}

function record(value: unknown): JsonRecord {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as JsonRecord)
    : {};
}

function text(value: unknown) {
  return typeof value === "string" ? value : "";
}

function nullableText(value: unknown) {
  const valueText = text(value);
  return valueText || null;
}

function number(value: unknown, fallback: number) {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}
