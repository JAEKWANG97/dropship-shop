"use server";

import { cookies } from "next/headers";
import { notFound, redirect } from "next/navigation";
import { ApiError, apiSendWithCookie } from "@/lib/api";
import { supplierMutationHeaders, supplierPortalEnabled } from "@/lib/supplier";

function value(formData: FormData, name: string) {
  const field = formData.get(name);
  return typeof field === "string" ? field.trim() : "";
}

export async function submitSupplierApplication(formData: FormData) {
  if (!supplierPortalEnabled()) notFound();

  let result = "received";
  try {
    await apiSendWithCookie("/api/supplier-applications", (await cookies()).toString(), {
      method: "POST",
      headers: supplierMutationHeaders(value(formData, "idempotencyKey")),
      body: JSON.stringify({
        supplierName: value(formData, "supplierName"),
        contactName: value(formData, "contactName"),
        contactEmail: value(formData, "contactEmail"),
        contactPhone: value(formData, "contactPhone") || null,
        memo: value(formData, "memo") || null,
        privacyAgreed: formData.get("privacyAgreed") === "on",
        consentPolicyVersion: value(formData, "consentPolicyVersion"),
      }),
    });
  } catch (error) {
    if (error instanceof ApiError && error.responseCode === "POLICY_VERSION_MISMATCH") {
      result = "policy-changed";
    } else if (!(error instanceof ApiError && ["APPLICATION_CONFLICT", "IDEMPOTENCY_CONFLICT"].includes(error.responseCode))) {
      result = "failed";
    }
  }

  redirect(`/supplier/apply?result=${result}`);
}
