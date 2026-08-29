import { notFound } from "next/navigation";
import { supplierPortalEnabled } from "@/lib/supplier";
import { SupplierActivationClient } from "./activation-client";

export const dynamic = "force-dynamic";

export default function SupplierActivatePage() {
  if (!supplierPortalEnabled()) notFound();
  return <SupplierActivationClient />;
}
