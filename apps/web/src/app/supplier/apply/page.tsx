import { randomUUID } from "node:crypto";
import { notFound } from "next/navigation";
import { SubmitButton } from "@/app/submit-button";
import {
  getSupplierApplicationPolicy,
  supplierPortalEnabled,
  type SupplierApplicationPolicy,
} from "@/lib/supplier";
import { submitSupplierApplication } from "./actions";

export const dynamic = "force-dynamic";

type PageProps = {
  searchParams: Promise<{ result?: string | string[] }>;
};

export default async function SupplierApplyPage({ searchParams }: PageProps) {
  if (!supplierPortalEnabled()) notFound();

  const [policy, query] = await Promise.all([loadPolicy(), searchParams]);
  const result = Array.isArray(query.result) ? query.result[0] : query.result;

  return (
    <section className="narrow-page supplier-apply-page">
      <div className="account-heading">
        <p className="eyebrow">공급처 포털</p>
        <h1>Coreable 공급처 신청</h1>
        <p>담당자 한 분의 연락처만 남겨 주세요. 검토 후 입력한 이메일로 연결 안내를 보내드립니다.</p>
      </div>

      {result === "received" ? (
        <div className="notice success" role="status">
          <strong>신청을 확인했습니다</strong>
          <span>담당자가 검토한 뒤 입력한 이메일로 안내드리겠습니다.</span>
        </div>
      ) : result === "policy-changed" ? (
        <div className="notice" role="status">
          <strong>개인정보 안내가 변경되었습니다</strong>
          <span>아래 최신 안내를 확인하고 다시 동의해 주세요.</span>
        </div>
      ) : result === "failed" ? (
        <div className="notice danger" role="alert">
          <strong>신청을 접수하지 못했습니다</strong>
          <span>입력 내용을 확인한 뒤 잠시 후 다시 시도해 주세요.</span>
        </div>
      ) : null}

      {!policy ? (
        <div className="notice danger" role="alert">
          <strong>개인정보 안내를 불러오지 못했습니다</strong>
          <span>안내가 준비된 뒤 신청할 수 있습니다. 잠시 후 다시 시도해 주세요.</span>
        </div>
      ) : (
        <>
          <article className="supplier-policy-card" aria-labelledby="supplier-privacy-title">
            <div>
              <h2 id="supplier-privacy-title">{policy.title}</h2>
              <span>버전 {policy.version}{policy.effectiveFrom ? ` · 시행 ${date(policy.effectiveFrom)}` : ""}</span>
            </div>
            <p>{policy.content}</p>
          </article>

          <form action={submitSupplierApplication} className="account-form supplier-apply-form">
            <input name="idempotencyKey" type="hidden" value={randomUUID()} />
            <input name="consentPolicyVersion" type="hidden" value={policy.version} />
            <label>
              공급처명
              <input autoComplete="organization" maxLength={100} name="supplierName" required />
            </label>
            <label>
              담당자명
              <input autoComplete="name" maxLength={100} name="contactName" required />
            </label>
            <label>
              연락 이메일
              <input autoComplete="email" maxLength={320} name="contactEmail" required type="email" />
            </label>
            <label>
              연락처 <span className="field-help">선택</span>
              <input autoComplete="tel" maxLength={30} name="contactPhone" type="tel" />
            </label>
            <label>
              문의 메모 <span className="field-help">선택</span>
              <textarea maxLength={1000} name="memo" rows={4} />
            </label>
            <label className="checkbox-label">
              <input name="privacyAgreed" required type="checkbox" />
              <span>위 개인정보 수집·이용 안내를 확인했으며 공급처 신청을 위해 동의합니다.</span>
            </label>
            <SubmitButton className="button primary" pendingLabel="신청 중...">
              공급처 신청
            </SubmitButton>
          </form>
        </>
      )}
    </section>
  );
}

async function loadPolicy(): Promise<SupplierApplicationPolicy | null> {
  try {
    const policy = await getSupplierApplicationPolicy();
    return policy.version && policy.content ? policy : null;
  } catch {
    return null;
  }
}

function date(value: string) {
  return new Date(value).toLocaleDateString("ko-KR");
}
