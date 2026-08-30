# MVP API Specification

이 문서는 MVP API의 기준을 정리한다.

`Status` 값:

- `Implemented`: 현재 `apps/api`에 구현되어 있다.
- `Planned`: 아직 구현 전이다.

## API Rules

- Base path: `/api`
- Admin APIs use `/api/admin/**`.
- Customer APIs must use the authenticated user id from the security context.
- Admin APIs require `ADMIN`.
- Non-user-authenticated access is allowed for public product pages, public policy/business/legal pages, health checks, OAuth start/callback, and verified provider webhooks. Internal scheduler endpoints require their configured internal token.
- Basic login and form login are disabled. Social OAuth issues a stateless JWT access token in an HttpOnly cookie.
- Server calculates all order, payment, refund, and shipping amounts.
- Production storefront sales default to disabled. When `app.sales.enabled=false`, product detail and cart responses expose the closed state, while cart item creation and checkout creation return `409`.
- Client-submitted totals are never trusted.
- Mutating admin actions that affect order, refund, shipment, claim, or product status should record audit history.
- API errors use the standard response shape defined below.

## Error Response Format

Status: Implemented

All API errors return the correct HTTP status and the following JSON body:

```json
{
  "timestamp": "2026-06-28T00:00:00Z",
  "status": 400,
  "code": "BUSINESS_RULE_VIOLATION",
  "message": "Cart is empty",
  "path": "/api/checkouts",
  "fields": []
}
```

Validation errors include field-level details:

```json
{
  "timestamp": "2026-06-28T00:00:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/cart/items",
  "fields": [
    {
      "field": "productOptionId",
      "message": "must not be null"
    }
  ]
}
```

Planned supplier-portal endpoints may add an optional `details` object without removing any implemented fields. Existing errors omit it; clients must tolerate the additive member.

Initial error codes:

- `UNAUTHORIZED`: authentication is required.
- `FORBIDDEN`: authenticated user lacks permission.
- `VALIDATION_FAILED`: request validation failed.
- `MALFORMED_REQUEST`: request body cannot be parsed.
- `BUSINESS_RULE_VIOLATION`: domain policy or state transition guard rejected the request.
- `RESOURCE_NOT_FOUND`: requested resource is missing or hidden from the caller.
- `CONFLICT`: request conflicts with an existing resource or idempotency boundary.
- `UPSTREAM_SERVICE_ERROR`: external provider or upstream service failed.
- `INTERNAL_SERVER_ERROR`: unexpected server error.

Public product detail and cart responses include:

- `salesEnabled`: whether storefront purchasing is open.
- `salesNotice`: customer-facing closure notice when sales are disabled.
- Public product detail also includes `complianceStatus`; supplier-only source metadata remains admin-only.

Order and payment state conflicts caused by optimistic locking return `409 CONFLICT` with code `CONFLICT` and the message `Order state was just changed. Please refresh and try again.` The client should not retry automatically; the user or admin should reload the latest state before submitting another action.

## Grouping Index

- Customer: catalog browsing, cart, checkout, orders, shipment, claims, account profile.
- Admin: suppliers, products, order queue, fulfillment, shipment correction, refunds, claims, policies, audit, notifications.
- Auth: social OAuth start/callback, logout, current user.
- Catalog: public product APIs and admin supplier/product/option/detail management.
- Cart: current customer cart and cart item mutations.
- Checkout/Order: payment group creation, policy confirmation, customer order history, address changes, self-service cancel.
- Payment: admin bank-transfer confirmation and manual refund completion.
- Fulfillment/Shipment: admin supplier actions, shipment entry, tracking sync, shipment correction.
- Refund/Claim: customer claim submission and admin review/refund execution.
- Policy Pages: public policy, business disclosure, privacy processing table, admin policy management.
- Supplier Portal: B-100 public application, one-time Kakao invitation, lifecycle and tenant session, B-101 individual catalog/review, B-102 inventory/reservation/received-payment exception, B-103 paid fulfillment/minimum-PII, B-104 plural Shipment, and B-105 shortage/requested claim-fact APIs are implemented.

## Implemented Endpoints

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/health` | Public | Implemented | Application health check |
| `GET` | `/actuator/health` | Public | Implemented | Actuator health check |
| `GET` | `/actuator/health/readiness` | Public | Implemented | Readiness probe |
| `GET` | `/actuator/health/liveness` | Public | Implemented | Liveness probe |
| `GET` | `/actuator/info` | Public | Implemented | Actuator info endpoint |
| `GET` | `/api/me` | Authenticated user | Implemented | Return authenticated user id |
| `GET` | `/api/admin/me` | `ADMIN` | Implemented | Prove admin access and return admin user id |

## Auth And Account APIs

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/auth/oauth2/{provider}/authorize` | Public | Implemented | Start Kakao, Google, or Naver social login |
| `GET` | `/api/auth/oauth2/{provider}/callback` | Public | Implemented | Handle provider callback, create or find user, and set access token cookie |
| `POST` | `/api/auth/logout` | Authenticated user | Implemented | Clear current access token cookie |
| `GET` | `/api/me` | Authenticated user | Implemented | Current user identity |
| `GET` | `/api/me/profile-completion` | Authenticated user | Implemented | Current required customer info completion state |
| `PATCH` | `/api/me/profile` | Authenticated user | Implemented | Update display name, contact email, and delivery phone number |
| `POST` | `/api/me/phone-verifications` | Authenticated user | Implemented | Optional legacy SMS OTP request; not required for checkout |
| `POST` | `/api/me/phone-verifications/confirm` | Authenticated user | Implemented | Optional legacy SMS OTP confirmation; not required for checkout |
| `GET` | `/api/me/referral` | Authenticated user | Implemented | Return current user's referral code and whether a referrer is registered. Lazily creates a code if missing. |
| `POST` | `/api/me/referral` | Authenticated user | Implemented | Register a referrer by referral code once |
| `GET` | `/api/me/agreements` | Authenticated user | Implemented | Current user policy agreement state |
| `POST` | `/api/me/agreements` | Authenticated user | Implemented | Agree to required terms/privacy policies |
| `GET` | `/api/me/addresses` | `CUSTOMER` | Implemented | List saved shipping addresses |
| `POST` | `/api/me/addresses` | `CUSTOMER` | Implemented | Create shipping address |
| `PATCH` | `/api/me/addresses/{addressId}` | `CUSTOMER` | Implemented | Update shipping address |
| `DELETE` | `/api/me/addresses/{addressId}` | `CUSTOMER` | Implemented | Delete shipping address |
| `POST` | `/api/me/deletion-request` | `CUSTOMER` | Implemented | Delete/anonymize current customer account and clear access token cookie. Rejects while any order/refund/claim is still in progress. |

Notes:

- The customer login page exposes Kakao only. Google and Naver OAuth endpoints remain implemented for existing-account compatibility.
- Email/password signup and login are excluded.
- Guest checkout is excluded.
- Admin users use the same social login flow, but admin access comes only from DB role.
- OAuth login uses provider authorization-code callbacks, provider token/userinfo requests, and social identity lookup by provider plus provider user id.
- Kakao authorization requests `profile_nickname account_email`. A verified provider email replaces only an existing internal `@oauth.local` placeholder and never overwrites a customer-edited email.
- Successful login sets `ACCESS_TOKEN` as an HttpOnly cookie with `SameSite=Lax`; production must use `Secure`.
- Access tokens are stateless JWTs signed by the API. Refresh tokens are deferred from MVP auth foundation.
- Current terms, privacy, shipping/order, cancellation/refund, and stock-risk versions are `2026-08-02`.
- `POST /api/me/agreements` requires both `termsAgreed=true` and `privacyAgreed=true` with current required versions.
- Reposting the same current versions is idempotent and returns the existing agreement record.
- Required customer info is display name, reachable contact email, and a valid delivery phone number.
- Existing SMS OTP endpoints retain hashed code storage, expiration, resend cooldown, and attempt limits, but phone verification is not a checkout requirement.
- Referral code collection runs after first social-login account creation through web onboarding. `GET /api/me` remains unchanged; referral state is only exposed through `/api/me/referral`.
- Referrer registration rejects unknown codes, inactive referrer accounts, self referral, and duplicate registration.
- Customer referral responses never expose the referrer's name or email.
- `POST /api/checkouts` requires current account terms/privacy agreement and completed required customer info before order creation.
- Saved shipping addresses belong to the authenticated customer only.
- The first saved address becomes the default address automatically.
- Creating or updating an address with `defaultAddress=true` clears the previous default address.
- Deleting the current default address promotes the most recently created remaining address to default.

Implemented request bodies:

```json
POST /api/me/agreements
{
  "termsAgreed": true,
  "privacyAgreed": true,
  "termsVersion": "2026-08-02",
  "privacyVersion": "2026-08-04"
}

GET /api/me/profile-completion
{
  "displayName": "Customer",
  "displayNameComplete": true,
  "email": "customer@example.com",
  "emailRequired": false,
  "emailComplete": true,
  "phoneNumber": "01012345678",
  "phoneVerified": false,
  "phoneVerifiedAt": null,
  "requiredInfoComplete": true
}

PATCH /api/me/profile
{
  "displayName": "Customer",
  "email": "customer@example.com",
  "phoneNumber": "010-1234-5678"
}

POST /api/me/phone-verifications
{
  "phoneNumber": "010-1234-5678"
}

POST /api/me/phone-verifications/confirm
{
  "phoneNumber": "01012345678",
  "code": "123456"
}

GET /api/me/referral
{
  "myReferralCode": "2ABCD789",
  "referrerRegistered": false
}

POST /api/me/referral
{
  "code": "2ABCD789"
}

POST /api/me/addresses
PATCH /api/me/addresses/{addressId}
{
  "recipientName": "Receiver",
  "recipientPhone": "010-1111-2222",
  "postalCode": "12345",
  "address1": "Base address",
  "address2": "Detail address",
  "defaultAddress": true
}
```

## Supplier Portal APIs

Status: `B-100` onboarding, lifecycle, Kakao activation and tenant/session endpoints, `B-101` catalog/review endpoints, `B-102` inventory/reservation/received-payment exception endpoints, `B-103` fulfillment/minimum-PII endpoints, `B-104` plural Shipment endpoints, and `B-105` shortage/claim-task/fact endpoints are Implemented. The `B-098` contract-evidence endpoint remains Planned. Existing customer/admin legacy response shapes remain compatible.

### Application, Approval, Invitation, And Session (Implemented B-100)

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/policies/SUPPLIER_APPLICATION_PRIVACY/current` | Public | Implemented B-100 | Read the active supplier-application privacy notice and canonical version before consent |
| `POST` | `/api/supplier-applications` | Public + allowed origin | Implemented B-100 | Submit the minimum supplier/contact application with privacy consent evidence |
| `GET` | `/api/admin/supplier-applications` | `ADMIN` | Implemented B-100 | Paginated review queue by application status |
| `GET` | `/api/admin/supplier-applications/{applicationId}` | `ADMIN` | Implemented B-100 | Review one application and its consent/audit metadata |
| `POST` | `/api/admin/supplier-applications/{applicationId}/approve` | `ADMIN` | Implemented B-100 | Idempotently create one pending portal supplier and invitation |
| `POST` | `/api/admin/supplier-applications/{applicationId}/reject` | `ADMIN` | Implemented B-100 | Reject with required reason and set 90-day anonymization deadline |
| `POST` | `/api/admin/suppliers/{supplierId}/invite/reissue` | `ADMIN` | Implemented B-100 | Revoke the open invitation and send one replacement |
| `PATCH` | `/api/admin/suppliers/{supplierId}/portal-status` | `ADMIN` | Implemented B-100 | Suspend/reactivate portal; require an explicit independent sales action |
| `PATCH` | `/api/admin/suppliers/{supplierId}/sales-status` | `ADMIN` | Implemented B-100 | Explicitly pause or resume the independent supplier trade/catalog status |
| `POST` | `/api/admin/suppliers/{supplierId}/portal-contract-status` | `ADMIN` | Planned B-098 | Record verified/expired/revoked per-supplier portal contract evidence before sales activation |
| `POST` | `/api/admin/suppliers/{supplierId}/manager-disconnect` | `ADMIN` | Implemented B-100 | Remove the manager link, revoke open invite, and remove supplier authority |
| `PATCH` | `/api/admin/suppliers/{supplierId}/contact-email` | `ADMIN` | Implemented B-100 | Replace contact email, disconnect manager, revoke invite, and require reverification |
| `POST` | `/api/supplier-invites/session` | Public + allowed origin | Implemented B-100 | Validate fragment token and issue short-lived HttpOnly invite context; do not consume yet |
| `GET` | `/api/supplier/auth/kakao/authorize` | Valid invite context | Implemented B-100 | Start Kakao-only OAuth with state bound to the invite context |
| `GET` | `/api/supplier/auth/kakao/callback` | Valid invite context + OAuth state | Implemented B-100 | Atomically bind the Kakao user, activate portal, verify contact email, and consume invite |
| `GET` | `/api/supplier/me` | `ROLE_SUPPLIER` | Implemented B-100 | Return current supplier tenant and portal status without customer/admin data |

Application request:

```json
POST /api/supplier-applications
Idempotency-Key: supplier-application-example
{
  "supplierName": "Example Supplier",
  "contactName": "Manager",
  "contactEmail": "manager@example.com",
  "contactPhone": "010-1234-5678",
  "memo": "Application note",
  "privacyAgreed": true,
  "consentPolicyVersion": "supplier-application-privacy-v1"
}
```

Admin lifecycle request bodies:

```json
POST /api/admin/supplier-applications/{applicationId}/approve
Idempotency-Key: supplier-application-review-example
{
  "approvalMode": "CREATE_NEW",
  "existingSupplierId": null,
  "reviewReasonCode": "APPLICATION_APPROVED",
  "internalReason": "Application criteria verified"
}

POST /api/admin/supplier-applications/{applicationId}/reject
Idempotency-Key: supplier-application-reject-example
{
  "reviewReasonCode": "POLICY_NOT_MET",
  "internalReason": "Application criteria not met"
}

POST /api/admin/suppliers/{supplierId}/invite/reissue
Idempotency-Key: supplier-invite-reissue-example
{
  "reasonCode": "DELIVERY_FAILED"
}

PATCH /api/admin/suppliers/{supplierId}/portal-status
Idempotency-Key: supplier-portal-status-example
{
  "portalStatus": "SUSPENDED",
  "salesAction": "PAUSE",
  "reason": "Portal access review"
}

PATCH /api/admin/suppliers/{supplierId}/sales-status
Idempotency-Key: supplier-sales-status-example
{
  "status": "ACTIVE",
  "reason": "Sales resumption approved"
}

POST /api/admin/suppliers/{supplierId}/portal-contract-status
Idempotency-Key: supplier-contract-status-example
{
  "status": "VERIFIED",
  "expectedCurrentContractVersion": null,
  "contractVersion": "supplier-contract-2026-01",
  "effectiveAt": "2026-08-29T00:00:00Z",
  "expiresAt": null,
  "evidenceReference": "contract-register/entry-id",
  "reason": "Signed contract and privacy duties verified"
}

POST /api/admin/suppliers/{supplierId}/portal-contract-status
Idempotency-Key: supplier-contract-revoke-example
{
  "status": "REVOKED",
  "expectedCurrentContractVersion": "supplier-contract-2026-01",
  "contractVersion": "supplier-contract-2026-01",
  "reason": "Contract relationship ended"
}

POST /api/admin/suppliers/{supplierId}/manager-disconnect
Idempotency-Key: supplier-manager-disconnect-example
{
  "salesAction": "KEEP",
  "reason": "Manager replacement"
}

PATCH /api/admin/suppliers/{supplierId}/contact-email
Idempotency-Key: supplier-contact-email-example
{
  "contactEmail": "new-manager@example.com",
  "salesAction": "PAUSE",
  "reason": "Contact changed"
}
```

Approval uses `approvalMode=CREATE_NEW|LINK_EXISTING`. CREATE_NEW forbids `existingSupplierId`; LINK_EXISTING requires it and accepts only an explicitly selected legacy supplier with no manager, invitation, application link, or portal lifecycle history and `portalStatus=DISABLED`. A permanently disabled prior portal supplier is therefore not relinkable through a new public application. The command never matches a Supplier by name or email. LINK_EXISTING preserves the selected Supplier trade status but replaces its contact email with the approved application's normalized contact email, clears `contactEmailVerifiedAt`, and sends the invite to that same address; callback may mark it verified only after rechecking the locked invite recipient. Approve/reject both require `Idempotency-Key`; the application stores the terminal action, key, canonical keyed-HMAC, approval mode/requested existing Supplier, and immutable ADMIN-safe result under the application row lock.

Portal-status transitions:

| From | Command | Guard | To |
| --- | --- | --- | --- |
| `DISABLED` | Admin portal-status PATCH | No direct activation; permanent portal termination is terminal in B-100 | `DISABLED` |
| `PENDING_ACTIVATION` | Kakao invite callback only | Valid unconsumed invite context/state, manager vacant, active Kakao user | `ACTIVE` |
| `ACTIVE` | Admin portal-status PATCH | `portalStatus=SUSPENDED`, required salesAction/reason; atomically hand over open work | `SUSPENDED` |
| `SUSPENDED` | Admin portal-status PATCH | `portalStatus=ACTIVE`, retained manager is active, contact email remains verified, and contract is time-valid VERIFIED; required salesAction/reason | `ACTIVE` |
| `PENDING_ACTIVATION` / `ACTIVE` / `SUSPENDED` | Admin portal-status PATCH | `portalStatus=DISABLED`, required salesAction/reason; clear manager, revoke invite, hand over open work | `DISABLED` |

The generic PATCH never accepts PENDING_ACTIVATION as a target and cannot activate DISABLED or PENDING_ACTIVATION. The dedicated contact-email change and manager-disconnect commands are the only implemented commands that move a non-disabled supplier back to PENDING_ACTIVATION.

B-100 rules:

- Public responses are generic and do not reveal whether a supplier name, email, or Kakao account already exists.
- Public application requires a basic rate limit, allows only one non-expired SUBMITTED or APPROVED row per normalized contact email, and stores a server-keyed HMAC of the canonical request with the required `Idempotency-Key`, never a plain deterministic hash of low-entropy contact fields. An identical replay returns the same generic result; reuse with another payload returns a generic `409` without revealing whether the conflict came from an email, application, or key. Under the normalized-email concurrency boundary, a new submit locks a matching SUBMITTED row and applies the same EXPIRED cleanup when `now >= retentionExpiresAt` before the active duplicate check, so scheduler lag cannot block a legitimate application after 90 days.
- `supplierName`, `contactName`, `contactEmail` and consent are required; `contactPhone` and `memo` are optional.
- Before submit, the UI reads the active `SUPPLIER_APPLICATION_PRIVACY` document. The server requires `privacyAgreed=true` and an exact match between `consentPolicyVersion` and the active server-owned version, stores canonical `consentedAt`, and returns `409 POLICY_VERSION_MISMATCH` for stale/unknown versions or `503 POLICY_UNAVAILABLE` when no active notice exists.
- Public application stores canonical consent evidence and starts with `retentionExpiresAt=createdAt+90 days`. If still SUBMITTED, scheduler makes it EXPIRED and anonymizes it. Human review stores admin/time, allowlisted reason, temporary PII-free internal reason, review action/mode/target, review key/HMAC and immutable result. REJECTED resets cleanup to `reviewedAt+90 days`; APPROVED follows the B-098 relationship deadline. EXPIRED/REJECTED cleanup nulls contact fields, internal reason, submit/review keys/HMACs and review result while preserving consent, terminal action/mode/code and reviewer/time. APPROVED cleanup later clears Supplier and application duplicate PII/replay material together. Post-cleanup public replay is new; CREATE_NEW separately refuses current Supplier-contact collision.
- Human review allows only `SUBMITTED -> APPROVED|REJECTED`; the deadline scheduler may use `SUBMITTED -> EXPIRED`. Review locks the application and rechecks `now < retentionExpiresAt`; if the deadline has arrived, it first commits the same EXPIRED cleanup as the scheduler and rejects review with `409 APPLICATION_EXPIRED`, so scheduler lag cannot approve or reject expired PII. All terminal states reject later review. Only the same stored action/key/hash returns the immutable first result; a different key or payload, changed approval mode/target/reason, or opposite action returns `409` and never changes Supplier/retention data. Allowed review codes are `APPLICATION_APPROVED`, `INCOMPLETE_INFORMATION`, `OUT_OF_SCOPE`, `POLICY_NOT_MET`, and `DUPLICATE_OR_EXISTING_RELATIONSHIP`; approval requires the first and rejection forbids it.
- Repeating approval for an already approved application returns the same approved supplier/invite state and never creates a duplicate supplier. CREATE_NEW creates `Supplier.status=INACTIVE`, `portalContractStatus=UNVERIFIED`, and `portalStatus=PENDING_ACTIVATION`; approval grants onboarding access, not permission to sell. LINK_EXISTING preserves the selected supplier's trade status but portal-managed products still require separate verified contract evidence.
- Approval creates `portalStatus=PENDING_ACTIVATION`; existing suppliers backfill `DISABLED`. Existing `Supplier.status` remains the independent catalog/trade status.
- Invitation tokens contain at least 256 bits of randomness. Only a unique digest, unique per-supplier issuance idempotency key, seven-day expiry, consumed/revoked timestamps, recipient email, and actors are persisted.
- Email links put the raw token after `#`. The client immediately POSTs it to `/api/supplier-invites/session`, removes the fragment, and continues with a short-lived HttpOnly invite-context cookie.
- The session exchange validates but does not consume the invite. Kakao callback first performs a non-mutating digest lookup only to resolve Supplier id, then follows the common `Supplier -> Invite(id) -> User/manager` lock order and revalidates digest, binding/state, expiry/revocation/consumption, recipient and manager vacancy. Active Kakao user lookup/create, unique manager binding, `contactEmailVerifiedAt`, `portalStatus=ACTIVE`, and consume timestamp then commit together. Contact change, portal disable and reissue use the same Supplier-before-Invite order.
- Supplier activation never matches or merges by email and never requires Kakao email to equal the invitation email.
- Google/Naver cannot enter or complete supplier invitation flow. The existing generic OAuth endpoints remain available only under their current customer/admin compatibility contract.
- Stored `User.role` remains `CUSTOMER` / `ADMIN`. `ROLE_SUPPLIER` is dynamically added only for the current ACTIVE user linked as manager of a `portalStatus=ACTIVE` supplier. Initial `UNVERIFIED` onboarding may use non-PII catalog surfaces, but a terminal or already-overdue VERIFIED contract suppresses supplier authority immediately even before the scheduler persists suspension. Independent `Supplier.status` alone gates new catalog sales/checkouts and may be INACTIVE while already-paid work is finished only while the contract remains time-valid.
- Existing CUSTOMER/ADMIN authorities remain available to that same Kakao account. Active supplier managers cannot use customer self-service deletion until an ADMIN disconnects the manager.
- Contact-email, portal-status, and manager-disconnect commands require `salesAction=KEEP|PAUSE`. Contact-email change clears the manager and `contactEmailVerifiedAt`, revokes open invitations, changes portal status to `PENDING_ACTIVATION`, and requires a replacement invitation.
- The admin UI defaults to PAUSE, but the server never silently mutates `Supplier.status`. Suspension sets `portalStatus=SUSPENDED`; disconnect clears manager and sets `PENDING_ACTIVATION`. Existing paid portal work enters a Coreable takeover queue and is not silently reassigned on reactivation. With `PAUSE`, new checkout is blocked. Implemented B-103 makes new deposit-confirmed work use `COREABLE_MANUAL` under `KEEP` until portal access is active again; B-102 itself creates or routes no Fulfillment. Reactivation never restores sales automatically.
- Portal-status, contact-email, manager-disconnect, and sales-status commands require `Idempotency-Key`, lock the Supplier, and append actor/action, before/after states, salesAction, PII-free reason, request HMAC, result and time. Reason rejects contact/customer identifiers. Identical replay returns the original result; changed payload returns `409`. At B-098 relationship cleanup, reason/key/HMAC/result are nulled while non-PII action/state/time remains.
- Deposit confirmation and late-deposit processing serialize with lifecycle and catalog writers. The shared lock order is PaymentGroup, affected Supplier rows by id, Product rows by id, every affected ProductOption row by id including UNTRACKED, then Orders/Fulfillments by id. Lifecycle-only commands lock their Supplier before Fulfillments and never acquire Product/Option; catalog/inventory saleability writers use Supplier when needed, then Product -> ProductOption, and never acquire a Supplier after Product. The payment command rechecks each locked Supplier/product/option immediately before routing or exception, so suspension, product review/status, and order-stop changes cannot leave stale work.
- Invitation is the only email allowed before contact verification and contains only the token/link plus generic connection instructions. Approval and invitation persistence do not roll back when email delivery fails. Stored notification subject/body/payload contain only token-free invite metadata; the raw fragment link exists only in ephemeral after-commit sending context. A failed/lost invitation is not generically retried because the token cannot be reconstructed and instead requires a new-key revoke/reissue; later token-free operational emails remain retryable.
- Invite recipient email and its linked notification recipient are nulled 30 days after consumed, revoked, or expired state; audit keeps only invite id, digest, terminal timestamps, template/delivery state, and actor evidence. Supplier operational contact PII lives only in the Supplier record while needed. Before production activation, B-098 and the managed privacy notice must supply a concrete post-relationship retention duration; permanent portal disable plus inactive trade and no open fulfillment/claim/refund sets that cleanup deadline. At the deadline the scheduler locks the Supplier and rechecks every lifecycle/open-work predicate; new open work clears or defers the deadline, and only continuing eligibility permits contact cleanup.
- Invite `consumedByUserId` plus catalog/inventory/lifecycle supplier actor FKs are nulled at that relationship deadline. Shipment/shortage/claim supplier actor FKs follow their parent Order/Claim legal-retention deadline, then are nulled or removed with the parent. Non-PII event evidence may remain; supplier PII access logs instead delete after one year.
- Invite reissue requires `Idempotency-Key` and one allowlisted `reasonCode`: `DELIVERY_FAILED`, `INVITE_EXPIRED`, `RECIPIENT_CHANGED`, or `ADMIN_REISSUE`. It accepts no free text. The command key/result remains the external idempotency contract, while the created invite uses a separate `reissue:` server-HMAC issuance namespace so it cannot collide with an `application:` approval invite. After scoped key/hash replay lookup, a new command locks Supplier and requires `portalStatus=PENDING_ACTIVATION`, `managerUserId=null`, a non-null current contact email, and `contactEmailVerifiedAt=null`; ACTIVE, SUSPENDED, terminal DISABLED, or manager-bound suppliers return `409 INVITE_REISSUE_NOT_ALLOWED`. Contact replacement first establishes that pending/unverified state in its lifecycle transaction. An identical retry returns the existing invite metadata without revoking it or sending another message, while a reused key with a different request returns `409`. A confirmed delivery failure is recovered with an explicitly new reissue key, which revokes the failed invite and creates one replacement rather than trying to recover the raw token.
- Invite activation failures use a safe allowlist: `INVITE_INVALID`, `INVITE_EXPIRED`, `INVITE_ALREADY_USED`, `INVITE_REVOKED`, `MANAGER_ALREADY_LINKED`, and `ACCOUNT_ALREADY_LINKED`. `/supplier/activate` shows no supplier/account existence detail and offers only `초대 링크를 다시 확인해 주세요` or `Coreable에 새 초대를 요청해 주세요`; retry is shown only for transient OAuth failures.
- `sales-status` locks the Supplier, requires `Idempotency-Key` and a PII-free reason, and accepts only `ACTIVE|INACTIVE`. Activating a portal-enrolled supplier requires time-valid VERIFIED evidence (`effectiveAt <= now`, no expiry or `now < expiresAt`) and first lazily expires overdue evidence; otherwise it returns `409 CONTRACT_NOT_VERIFIED`. It never changes portal status, manager, invite, or handed-over ownership. Implemented B-103 routes later paid work to `COREABLE_MANUAL` when sales stay ACTIVE but portal access is unavailable; resumption never hands earlier work back.
- B-100 owns the denormalized contract-status columns/default UNVERIFIED and makes sales activation fail closed; B-098 owns immutable command/history, expiry index, evidence, and scheduler. Every command carries `expectedCurrentContractVersion` and locks Supplier. VERIFIED requires the expected value (including null initially) to match, a unique new `contractVersion`, valid effective/expiry times, evidence, reason, and key. EXPIRED/REVOKED require current status VERIFIED and the matching non-null target version; only one terminal event is allowed per version. Scheduler rechecks candidate status/version/expiry after locking and no-ops if terminal processing or re-verification won. Current means VERIFIED with `effectiveAt <= now` and no expiry or `now < expiresAt`. A terminal command or lazy expiry atomically makes sales INACTIVE, changes an ACTIVE portal to SUSPENDED while retaining the manager, revokes any open invite, and hands every still-SUPPLIER-owned open portal Fulfillment to COREABLE with `CONTRACT_EXPIRED|CONTRACT_REVOKED` evidence. PENDING_ACTIVATION remains non-authorized but loses its open invite. The same time-valid check directly guards paid-work list/detail/mutations and Claim-grant access so scheduler lag cannot expose PII. Re-verification uses a new version/key but never reactivates portal/sales or restores handed-over ownership; explicit guarded admin commands are required. Documents/secrets are never exposed.
- For VERIFIED, `contractVersion` is the new version and `effectiveAt`, optional `expiresAt`, and `evidenceReference` are required as documented. For EXPIRED/REVOKED, `contractVersion` must equal the non-null `expectedCurrentContractVersion`; effective/expiry/evidence fields are forbidden because the terminal event refers to the current verified evidence rather than replacing it.
- After B-100, legacy `PATCH /api/admin/suppliers/{supplierId}` rejects contact-email, manager, portal lifecycle, and trade-status mutations for any supplier whose portal status is not DISABLED or that has portal manager/invite history. Admins must use contact-email/portal-status/sales-status lifecycle routes so invite revocation, salesAction and handover cannot be bypassed; unrelated legacy supplier fields remain compatible.

### Supplier Catalog And Coreable Review (Implemented B-101)

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/supplier/products` | `ROLE_SUPPLIER` | Implemented B-101 | List only current supplier products |
| `POST` | `/api/supplier/products` | `ROLE_SUPPLIER` | Implemented B-101 | Create one internal product draft used by the single registration flow; CSV is not supported in this slice |
| `GET` | `/api/supplier/products/{productId}` | `ROLE_SUPPLIER` | Implemented B-101 | Read one owned product and supplier-editable fields |
| `PATCH` | `/api/supplier/products/{productId}` | `ROLE_SUPPLIER` | Implemented B-101 | Update supplier-editable product fields only |
| `DELETE` | `/api/supplier/products/{productId}` | `ROLE_SUPPLIER` | Implemented B-101 | Hard-delete an unreferenced, never-submitted portal draft and queue owned binary cleanup |
| `POST` | `/api/supplier/products/{productId}/submit` | `ROLE_SUPPLIER` | Implemented B-101 | Final call behind the same visible registration action; classify and auto-publish or queue review |
| `POST` | `/api/supplier/products/{productId}/options` | `ROLE_SUPPLIER` | Implemented B-101 | Add an owned product option |
| `PATCH` | `/api/supplier/products/{productId}/options/{optionId}` | `ROLE_SUPPLIER` | Implemented B-101 | Update an option after product+option tenant check |
| `DELETE` | `/api/supplier/products/{productId}/options/{optionId}` | `ROLE_SUPPLIER` | Implemented B-101 | Hard-delete an unreferenced option under a never-submitted draft while preserving one option |
| `POST` | `/api/supplier/products/{productId}/images` | `ROLE_SUPPLIER` | Implemented B-101 | Upload validated owned-product image |
| `DELETE` | `/api/supplier/products/{productId}/images/{imageId}` | `ROLE_SUPPLIER` | Implemented B-101 | Remove an owned draft image and schedule binary cleanup after commit |
| `PUT` | `/api/supplier/products/{productId}/images/order` | `ROLE_SUPPLIER` | Implemented B-101 | Reorder owned images and select exactly one thumbnail |
| `PUT` | `/api/supplier/products/{productId}/detail-blocks` | `ROLE_SUPPLIER` | Implemented B-101 | Replace sanitized owned-product detail blocks |
| `PUT` | `/api/supplier/products/{productId}/notice` | `ROLE_SUPPLIER` | Implemented B-101 | Create/update structured product information notice |
| `GET` | `/api/admin/product-reviews` | `ADMIN` | Implemented B-101 | Review queue for certification/category/required-info flags |
| `GET` | `/api/admin/product-reviews/{productId}` | `ADMIN` | Implemented B-101 | Read the exact version and structured category/notice/product fields for review |
| `POST` | `/api/admin/product-reviews/{productId}/approve` | `ADMIN` | Implemented B-101 | Approve with audit reason |
| `POST` | `/api/admin/product-reviews/{productId}/supplement` | `ADMIN` | Implemented B-101 | Request supplier supplementation with reason |
| `POST` | `/api/admin/product-reviews/{productId}/reject` | `ADMIN` | Implemented B-101 | Reject and keep product hidden with reason |

Review action request bodies:

```json
POST /api/admin/product-reviews/{productId}/approve
{
  "expectedVersion": 7,
  "internalReason": "Required evidence verified"
}

POST /api/admin/product-reviews/{productId}/supplement
{
  "expectedVersion": 7,
  "reviewReasonCode": "SUPPLEMENT_REQUIRED",
  "supplierReviewMessage": "필수 인증번호와 고시 항목을 추가해 주세요.",
  "internalReason": "Structured certification information is missing"
}

POST /api/admin/product-reviews/{productId}/reject
{
  "expectedVersion": 7,
  "reviewReasonCode": "REJECTED_POLICY",
  "supplierReviewMessage": "현재 판매 정책상 등록할 수 없는 상품입니다.",
  "internalReason": "Prohibited catalog category"
}
```

B-101 request and response boundaries:

- B-101 supplier create/update may accept product name, summary, supplier cost, option supplier cost/code, MOQ/order step, category selection, public product images, detail blocks and structured notice rows owned by that supplier. The classifier may read the existing admin-managed compliance status, but supplier payloads cannot set it. Supplier IMAGE detail blocks accept an owned same-Product `productImageId` of type `DETAIL`, never an arbitrary URL/key. Inventory mode/on-hand is owned only by the B-102 inventory endpoint.
- Supplier payload must not accept `supplierId`, `basePrice`, customer sale status, `complianceStatus`, `reviewStatus`, sale-readiness result, another supplier source identifier, or an arbitrary image owner/storage key.
- Coreable computes prices deterministically from the active pricing policy whenever an approved supplier cost change takes effect. `basePrice=price(sourcePrice)`; each option's customer total is `price(sourcePrice + sourceAdditionalPrice)` and `additionalPrice=optionCustomerTotal-basePrice`, where `price` applies the same markup, resale-minimum floor, and rounding rules. `sourcePrice` and `sourceAdditionalPrice` are each integer KRW in `0..100,000,000`; a calculated customer unit price is at most `1,000,000,000`. Exact add/multiply rejects overflow before any cart, order, or payment snapshot is written. Price values and policy version change atomically and append audit history.
- B-101 additively returns monotonic `version` from the existing admin pricing-policy API. Each price-application history after state stores applied policy id/version, full rates/rounding/resale-minimum calculator snapshot and resulting product/option prices; its before state stores the prior applied policy id/version and prices. The prior full snapshot remains in the earlier application row, so a later in-place policy edit cannot fabricate a mixed-version audit value.
- Portal checkout reuses the existing ADMIN-only `sourceUnitPrice` OrderItem snapshot for the then-current supplier option cost instead of adding a second cost column. It preserves customer `unitPrice` and `lineAmount`; supplier order responses and settlement UI never expose `sourceUnitPrice`, and supplier settlement remains a non-goal.
- A no-option request creates one internal `기본` option so existing required order-item option references remain valid.
- The web presents one `상품 등록` action. Internal draft creation and asset calls are implementation details; the supplier does not perform a second approval-request step. A successful submit must make a structurally sale-ready unflagged product `AUTO_APPROVED`; certification/category/legal-information flags or classifier uncertainty produce `REVIEW_REQUIRED` and keep it hidden.
- Supplier product GET/submit responses expose only `supplierDisplayStatus`, allowlisted `reviewReasonCode`, supplier-safe `reviewMessage`, and `nextAction` for review feedback. Allowed reason codes are `CERTIFICATION_REVIEW`, `CATEGORY_REVIEW`, `REQUIRED_INFO_MISSING`, `SAFETY_REVIEW`, `SUPPLEMENT_REQUIRED`, and `REJECTED_POLICY`; allowed next actions are `WAIT`, `EDIT_AND_RESUBMIT`, `CONTACT_COREABLE`, and `NONE`.
- `reviewMessage` is a separately validated supplier-facing single-line plain-text message of at most 500 characters, not an admin note. It rejects email, phone, address, customer identifiers, and links so a durable product decision never becomes a contact/customer PII channel. Internal reviewer identity, notes, rule traces, and evidence not explicitly supplied for disclosure are never serialized. Supplementation uses the same edit screen and visible `상품 등록` action to resubmit.
- Display mapping is deterministic: DRAFT -> `EDITING`/`EDIT_AND_RESUBMIT`; AUTO_APPROVED or APPROVED -> `APPROVED`/`NONE`; REVIEW_REQUIRED -> `UNDER_REVIEW`/`WAIT`; SUPPLEMENT_REQUESTED -> `CHANGES_REQUESTED`/`EDIT_AND_RESUBMIT`; REJECTED -> `REJECTED`/`CONTACT_COREABLE`. A separate Coreable sale hold displays `PAUSED_BY_COREABLE`/`CONTACT_COREABLE` without exposing its internal note. `reviewReasonCode` and `reviewMessage` may be null only when no supplier action or explanation is needed.
- Supplier/admin product detail and every non-delete mutation response include the aggregate `version`. PATCH/submit and every option/image/detail/notice mutation that can affect review accepts the current `expectedVersion`; DELETE uses `If-Match`. An accepted mutation increments the aggregate unless the Product itself is deleted. A stale supplier or reviewer request returns `409 PRODUCT_VERSION_CONFLICT` with no partial write.
- Review transitions are explicit: initial `DRAFT` submit classifies to `AUTO_APPROVED` or `REVIEW_REQUIRED`; `REVIEW_REQUIRED` admin review moves only to `APPROVED`, `SUPPLEMENT_REQUESTED`, or `REJECTED`; `SUPPLEMENT_REQUESTED` remains hidden while supplier edits and its resubmit always returns to `REVIEW_REQUIRED` rather than auto-publication; `REJECTED` has no direct supplier resubmit and maps to Coreable contact. Review-relevant edits of `AUTO_APPROVED`, `APPROVED`, or `REVIEW_REQUIRED` hide the product and move it to `DRAFT` before a new submit/classification.
- Admin approve/supplement/reject is valid only for the exact `REVIEW_REQUIRED` version the reviewer loaded. Supplement/reject require an allowlisted supplier reason code and separately validated `supplierReviewMessage`; `internalReason` stays ADMIN-only but is also single-line, at most 500 characters, and rejects contact/customer PII. All review decisions and supplier edits append actor/version before-and-after history. History snapshots canonicalize only allowlisted product/option/image/detail/notice/pricing/review business fields and never copy request bodies, actor contact fields, customer/order data, or arbitrary admin notes.
- A Coreable `APPROVED` decision for `CERTIFICATION_REVIEW` completes only the portal human-review state. It does not write `complianceStatus`; the legacy compatibility rule remains that `PENDING` can sell and only `REJECTED` blocks sale readiness.
- B-101 uses an expand-contract rollout for existing admin/source writers: backfill Product version, make every legacy admin, review, cart, checkout, and source-sync writer discover only scalar supplier/ownership first, then take `Supplier -> fresh Product -> ProductOption(id)` pessimistic locks and increment the aggregate version when it mutates the Product. After any lock wait, the fresh Product supplier must still match the discovered/requested supplier; stale ownership returns the applicable conflict or tenant-safe `404`. Product history distinguishes `ADMIN|SUPPLIER|SYSTEM`; the legacy zero-UUID Domeggook sentinel backfills to SYSTEM with null actor user rather than becoming a fake ADMIN/User FK. During compatibility, an omitted admin version uses the locked current row and returns the new version. Any review-relevant legacy/source mutation of a portal product also hides/invalidates its pending approval. For a portal product, legacy admin product/option mutations treat customer-price fields as compatibility input only and overwrite them with an active-policy recalculation of the product and every option, recording the same applied version/full snapshot. Only a later contract release may require the admin precondition, so existing request bodies are not broken at B-101.
- Supplier responses expose supplier cost and source option metadata only for the current tenant. Public responses continue to omit all supplier cost, supplier identity, inventory mode/quantity, review internals, and source metadata.
- Supplier product routes require both current supplier ownership and `managementChannel=SUPPLIER_PORTAL`; legacy COREABLE/Domeggook products remain Coreable-managed even after LINK_EXISTING and return tenant-safe `404` from supplier routes. No automatic ownership migration is included.
- Supplier product lookup and mutation first discover scalar ownership scoped by resource id, current supplier id and portal management channel. Mutations then lock the Supplier, reload a fresh Product, lock its Options in id order, and recheck tenant/channel/version; a post-wait owner mismatch is tenant-safe `404`. Image ownership and multipart metadata, including the 200-character alt-text limit, are validated before binary write.
- Supplier product, option, and image rows return server-derived `deletable`. An image is not deletable while a detail block references it, and a current thumbnail with other presentation images must first be replaced through reorder. The UI shows destructive deletion only when true and can remove a DETAIL block reference before deleting its image. The server still rechecks every guard at mutation time. Reorder requires exactly one thumbnail; deleting the sole presentation thumbnail may temporarily leave a draft without one, and submit then routes it to required-information review.
- B-101 does not collect private certification-document files. Review uses the structured category/notice fields, existing admin-managed compliance status and validated public product images; if a private document is operationally required, Coreable handles it outside this initial portal slice until a retention/access policy is separately approved.
- Product/image/detail/notice HTML and files keep the existing sanitization, extension, signature, size, and count rules.
- The production supplier-portal feature gate remains closed after B-105. B-100~B-105 are necessary release foundations, but customer purchase and external supplier routes remain closed until privacy, live-email, B-098 contract, and feature-flag release gates are all ready.

B-101 draft deletion contract:

```http
DELETE /api/supplier/products/{productId}
If-Match: "7"

DELETE /api/supplier/products/{productId}/options/{optionId}
If-Match: "7"
```

- Both requests have no body. A successful Product delete returns `204 No Content`; a successful Option delete returns `204 No Content` and the surviving Product version in `ETag`. Product/option detail and list queries return no tombstone after hard delete.
- Both routes first require the current supplier tenant and `managementChannel=SUPPLIER_PORTAL`; absent, other-tenant, or Coreable-managed resources return tenant-safe `404`.
- Product delete requires `reviewStatus=DRAFT`, immutable `firstSubmittedAt=null`, the matching Product version, and no CartItem/OrderItem reference to the Product or any of its Options. Option delete requires the same parent state/version, no reference to that Option, and at least one remaining Option. A row that was submitted, reviewed, published, or referenced is preserved through Coreable `HIDDEN`/`STOPPED`, not soft- or hard-deleted.
- Missing `If-Match` returns `428 PRODUCT_VERSION_REQUIRED`. A stale value returns `409 PRODUCT_VERSION_CONFLICT`; invalid parent state returns `409 PRODUCT_NOT_DRAFT` or `409 PRODUCT_ALREADY_SUBMITTED`; references return `409 PRODUCT_REFERENCED`, `409 OPTION_REFERENCED`, or `409 DETAIL_IMAGE_REFERENCED`; deleting the last Option returns `409 LAST_OPTION_REQUIRED`. No path returns a raw FK error.
- A new delete performs scalar ownership discovery, then locks `Supplier -> fresh Product -> all ProductOptions(id)` and rechecks tenant, version, state, submission marker and references. Cart add and checkout use the same lock contract plus fresh ownership/saleability and reference guards before inserting CartItem/OrderItem. A stale owner or deleted/not-sellable resource therefore returns the applicable conflict/`404` instead of a dangling row or raw FK error.
- A valid deletable Product is a never-submitted `DRAFT/HIDDEN`, so normal cart and checkout APIs cannot create a new reference to it. B-101 verifies the shared lock order as a service contract and verifies existing CartItem/OrderItem reference rejection through integration tests; it does not fabricate a saleable-and-deletable state solely to claim a concurrent purchase-success E2E.
- Before row removal, Product deletion appends `PRODUCT_DELETED` with `beforeVersion=current`, `afterVersion=null`, allowlisted before snapshot and immutable subject ids. Option deletion appends `OPTION_DELETED`, increments the surviving Product and records `v -> v+1`. Live history FKs use `ON DELETE SET NULL`; `/api/admin/products/{productId}/changes` queries the immutable subject id and returns deleted-subject history, while a random id with neither live Product nor history remains `404`.
- DELETE takes no supplier free-text reason. Product deletion uses server reason `DRAFT_ABANDONED`; Option deletion uses `DRAFT_OPTION_REMOVED`.
- Product deletion removes DetailBlock references before owned Image metadata, then Option and Notice metadata after checking restrictive CartItem/OrderItem FKs. Supplier uploads create `THUMBNAIL`, `GALLERY`, or `DETAIL` ProductImage rows with a server-generated, non-client-writable, single-use unique `storageObjectKey`; a referenced DETAIL image cannot be deleted alone. Any key with a cleanup job is a tombstone and cannot be attached again through admin metadata, whether the job is pending or terminal. Metadata removal and durable cleanup enqueue commit together, and repeated enqueue of the same key returns the same job. Immediately before binary deletion the worker checks for a live `ProductImage.storageObjectKey` reference; if one exists, it skips deletion and completes the job with `LIVE_REFERENCE`. A later real metadata removal reopens that same job as pending. Missing objects are success, other failures retry without restoring metadata, and external/legacy URLs with no owned key are never deleted.

### Option Inventory And Reservation (Implemented B-102)

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `PUT` | `/api/supplier/products/{productId}/options/{optionId}/inventory` | `ROLE_SUPPLIER` | Implemented B-102 | Idempotently set supplier availability and absolute inventory mode/on-hand for an owned option |
| `POST` | `/api/admin/orders/{orderId}/deposit-mismatch` | `ADMIN` | Implemented B-102 | Record an identified amount-mismatched receipt and create one actual-amount PaymentGroup refund exactly once |
| `POST` | `/api/admin/orders/{orderId}/late-deposit` | `ADMIN` | Implemented B-102 | Record an expired or qualifying unpaid-cancelled checkout receipt exactly once; only expired portal groups may reacquire, while cancelled groups always refund |

Inventory request:

```json
PUT /api/supplier/products/{productId}/options/{optionId}/inventory
Idempotency-Key: supplier-inventory-update-example
{
  "expectedInventoryVersion": 0,
  "supplierAvailability": "AVAILABLE",
  "inventoryMode": "TRACKED",
  "onHandQuantity": 120
}
```

Success returns the canonical current option inventory projection used to refresh the editor:

```json
{
  "optionId": "00000000-0000-0000-0000-000000000001",
  "inventoryVersion": 1,
  "supplierAvailability": "AVAILABLE",
  "inventoryMode": "TRACKED",
  "onHandQuantity": 120,
  "reservedQuantity": 4,
  "availableQuantity": 116
}
```

B-102 rules:

- The request never accepts `reservedQuantity` or `availableQuantity`; both are server-controlled/derived.
- Inventory uses an option-local `inventoryVersion`, separate from the Product review version. The supplier sends the last canonical value as `expectedInventoryVersion`; every accepted supplier update and every reservation, release, consumption, or reacquisition increments it. A stale value returns `409 INVENTORY_CONFLICT` with the current canonical projection, while inventory writes never increment Product version or change review state.
- Existing COREABLE options migrate as `UNTRACKED`; SUPPLIER_PORTAL options created during B-101 before B-102 migrate as `TRACKED/onHandQuantity=0`; later portal options default to `TRACKED` but the supplier may explicitly submit `UNTRACKED`. `sourceStockQuantity` stays reference metadata and is never checkout inventory.
- `supplierAvailability=AVAILABLE|UNAVAILABLE` is the supplier's emergency new-order switch, distinct from Coreable-owned Product/Option sale status. UNAVAILABLE blocks new checkout; AVAILABLE cannot override a Coreable sale stop, hidden/compliance state, or inactive Supplier.
- The supplier UI labels TRACKED as `수량 관리 (권장)`, requires an integer `onHandQuantity >= 0`, and explains that checkout temporarily reserves quantity. It labels UNTRACKED as `재고 수량 관리 안 함`, omits on-hand input, and uses an `주문 받기`/`주문 중지` control backed by supplierAvailability. Stopping warns that a later deposit for an existing unpaid checkout can require a refund. Customer DTOs expose neither mode nor an `unlimited` label.
- TRACKED inventory enforces `0 <= reserved <= onHand`; setting on-hand below reserved returns `409 CONFLICT`.
- Changing `inventoryMode` in either direction is rejected while the option is referenced by any open `PAYMENT_PENDING` OrderItem; the supplier can stop new orders and wait for those 24-hour checkouts to finish. If the mode changed only after an Order expired, a later-deposit command detects `inventoryModeSnapshot != current mode` and uses `SALE_UNAVAILABLE_AT_DEPOSIT` rather than approving an untracked quantity against the new ledger.
- Inventory conflict responses include `code=INVENTORY_CONFLICT` and the same canonical current projection, so the UI replaces stale local values and asks the supplier to retry. No requested values are partially applied.

```json
{
  "timestamp": "2026-08-29T00:00:00Z",
  "status": 409,
  "code": "INVENTORY_CONFLICT",
  "message": "Inventory changed; refresh and retry",
  "path": "/api/supplier/products/{productId}/options/{optionId}/inventory",
  "fields": [],
  "details": {
    "currentInventory": {
      "optionId": "00000000-0000-0000-0000-000000000001",
      "inventoryVersion": 1,
      "supplierAvailability": "AVAILABLE",
      "inventoryMode": "TRACKED",
      "onHandQuantity": 120,
      "reservedQuantity": 4,
      "availableQuantity": 116
    }
  }
}
```
- An accepted inventory mutation appends current supplier actor, immutable subject option id, nullable live Option FK, before/after availability/mode/on-hand plus reserved snapshots, request hash, idempotency key, and time. After resolving the current supplier principal, `(supplierId,subjectOptionId,idempotencyKey)` history lookup precedes the live Option guard; the canonical hash includes both path ids and the body. Unique `(subjectOptionId,idempotencyKey)` therefore returns the first canonical result for an identical retry even after an allowed draft Option deletion and rejects a changed path/payload, while another tenant still receives `404`. Checkout reservation lifecycle remains audited through OrderItem reservation fields rather than this manual-change history.
- Checkout creation locks affected Supplier rows by id, Product rows by id, and every affected ProductOption row by id including UNTRACKED. It rechecks trade status, product/option/compliance and supplier availability under those locks, plus time-valid contract only for portal-origin items, then reserves TRACKED quantities for the full multi-supplier checkout atomically for the existing 24-hour deadline. UNTRACKED-only checkout still locks Supplier/Product/Option, so concurrent PAUSE, product review/status, or `주문 중지` cannot commit a stale new `PAYMENT_PENDING` checkout.
- Deposit confirmation consumes HELD reservations exactly once. For every portal-origin item it also invokes the shared terminal-contract routine for overdue evidence under the Supplier lock before saleability evaluation: EXPIRED, sales INACTIVE, portal suspension/invite revocation as applicable, and open-work Coreable handover. Unpaid cancellation or scheduler expiry releases reservations exactly once.
- Normal confirm checks locked current saleability/mode before deadline reason classification. Any saleability, time-valid-contract, or immutable/current mode failure takes `SALE_UNAVAILABLE_AT_DEPOSIT` even if the timestamp is also late. Only when those guards pass does `depositedAt` after the original deadline take `LATE_DEPOSIT_EXCEPTION`; scheduler lag cannot make it timely, and HELD is released once.
- A deposit found after expiry is approved only when its actual received time is within the original deadline, every sale/compliance/supplier-availability guard and portal-origin time-valid contract still passes after lazy expiry, and every TRACKED item is reacquired atomically. Success preserves `releasedAt`, records `reacquiredAt` plus `consumedAt`, and returns CONSUMED in ADMIN audit data. A saleability/contract/mode failure rolls back reacquisition and uses `SALE_UNAVAILABLE_AT_DEPOSIT`; a late timestamp or stock failure uses `LATE_DEPOSIT_EXCEPTION`. Both keep the supplier hidden and finish Orders at `REFUND_REQUESTED`.
- Effective purchase availability requires Coreable product/option sale eligibility, active Supplier, `supplierAvailability=AVAILABLE`, and positive derived available quantity for TRACKED. CUSTOMER/public responses expose only purchasable/sold-out, never TRACKED/UNTRACKED, on-hand, reserved, available, supplierAvailability, or `무제한` text.

Amount-mismatch request:

```json
POST /api/admin/orders/{orderId}/deposit-mismatch
Idempotency-Key: deposit-mismatch-example
{
  "actualDepositorName": "Depositor",
  "actualAmount": 31000,
  "depositedAt": "2026-08-29T01:00:00Z",
  "transactionReference": "bank-reference",
  "reason": "Checkout total was 30000"
}
```

Admin-only response:

```json
{
  "outcome": "PAYMENT_EXCEPTION",
  "exceptionReason": "AMOUNT_MISMATCH",
  "expectedAmount": 30000,
  "actualAmount": 31000,
  "paymentGroupStatus": "PAYMENT_EXCEPTION",
  "orderStatuses": ["REFUND_REQUESTED"],
  "refund": {
    "refundScope": "PAYMENT_GROUP",
    "reason": "PAYMENT_AMOUNT_MISMATCH",
    "status": "REQUESTED",
    "refundAmount": 31000
  },
  "supplierVisible": false,
  "customerDisplayStatus": "REFUND_PROCESSING",
  "nextAction": "COREABLE_APPROVE_AND_COMPLETE_BANK_REFUND"
}
```

Amount-mismatch contract:

- B-102 replaces the current memo-only action contract with the terminal command for an identified positive bank receipt whose amount differs from the immutable PaymentGroup total. It accepts `PAYMENT_PENDING`, `EXPIRED`, or a qualifying unpaid `CANCELLED` group, applies to portal and legacy groups, and requires the full receipt evidence plus `Idempotency-Key`. A cancelled group qualifies only when unpaid cancellation is its sole terminal outcome, every included Order is cancelled for non-payment, and no received Payment, Refund or Fulfillment exists. An exact amount returns `409 DEPOSIT_AMOUNT_NOT_MISMATCHED`; pending/expired groups use normal or late confirmation, while a qualifying cancelled group uses the terminal `late-deposit` path below. Expand first accepts both server shapes while the admin web switches, then rejects new memo-only writes; historical memo fields and rows remain readable. A bank transaction not yet attributable to any PaymentGroup remains outside Order/Payment mutation until Coreable matches it.
- The path `orderId` only locates the PaymentGroup. Replay identity is `(paymentGroupId, Idempotency-Key)` and the request hash includes action, admin actor, depositor, actual amount, deposited time, transaction reference and reason. Identical replay returns the stored ADMIN-safe result before current-state guards; changed payload/action returns `409` without mutation.
- Amount mismatch has reason priority over saleability, contract, inventory mode, receipt deadline and stock. Under `PaymentGroup -> Supplier -> Product -> all Option -> all Order` locks, one transaction stores actual receipt evidence, a `BANK_TRANSFER` Payment with `status=PAYMENT_EXCEPTION` and `exceptionReason=AMOUNT_MISMATCH`, `PaymentGroup.status=PAYMENT_EXCEPTION`, and exception history for every included Order. Expected total is unchanged, approved amount/time stay null, and refundable amount becomes the positive actual receipt.
- The command releases every remaining portal TRACKED `HELD` reservation exactly once and never reacquires or consumes stock. It creates exactly one `Refund(status=REQUESTED, refundScope=PAYMENT_GROUP, orderId=null, reason=PAYMENT_AMOUNT_MISMATCH, refundAmount=actualAmount)` and commits every included Order directly to `REFUND_REQUESTED`. PaymentGroup-scope uniqueness and provider payment key are secondary duplicate guards.
- It creates no `SUPPLIER_ORDER_PENDING`, Fulfillment, address lock, supplier notification, PII window or supplier-visible row. There is no action that resumes the checkout; a customer who still wants the products creates a new checkout.
- Admin refund approval for `PAYMENT_AMOUNT_MISMATCH` is an operational acknowledgement and cannot reject or alter the exact refund amount. Manual completion requires its own `Idempotency-Key`, full bank-transfer evidence and an exact outstanding actual amount. It atomically completes the single Refund, Payment, PaymentGroup and every included Order as `REFUNDED`; this path never uses `PARTIALLY_REFUNDED`.
- Customer checkout/order projections expose `REFUND_PROCESSING`, `입금 확인 및 환불 처리 중`, and the actual refund amount, but not depositor, transaction reference, admin reason or account evidence. Supplier list/detail/mutation returns no row/`404`, creates no PII access log and sends no email.

Late-deposit request:

```json
POST /api/admin/orders/{orderId}/late-deposit
Idempotency-Key: late-deposit-example
{
  "actualDepositorName": "Depositor",
  "actualAmount": 45000,
  "depositedAt": "2026-08-28T12:00:00Z",
  "transactionReference": "bank-reference",
  "reason": "Deposit found after scheduler expiry"
}
```

Admin-only exception response:

```json
{
  "outcome": "PAYMENT_EXCEPTION",
  "paymentGroupStatus": "PAYMENT_EXCEPTION",
  "orderStatuses": ["REFUND_REQUESTED"],
  "payment": {
    "provider": "BANK_TRANSFER",
    "status": "PAYMENT_EXCEPTION",
    "actualAmount": 45000,
    "depositedAt": "2026-08-28T12:00:00Z",
    "transactionReference": "bank-reference"
  },
  "supplierVisible": false,
  "customerDisplayStatus": "REFUND_PROCESSING",
  "nextAction": "COREABLE_COMPLETE_BANK_REFUND"
}
```

Late-deposit contract:

- This is a separate Implemented B-102 action; legacy-only use of the existing `confirm-deposit` endpoint keeps its implemented `PAYMENT_PENDING` precondition and response. B-102 additively requires `Idempotency-Key` for any PaymentGroup containing a portal-origin snapshot item and returns the stored first approved-or-exception result on identical replay.
- For `EXPIRED`, `late-deposit` requires at least one OrderItem with `managementChannelSnapshot=SUPPLIER_PORTAL`; a legacy-only expired PaymentGroup returns `409 PORTAL_LATE_DEPOSIT_UNSUPPORTED` without mutation and remains on the existing manual reconciliation policy. A qualifying unpaid `CANCELLED` PaymentGroup is accepted for portal and legacy groups only when unpaid cancellation is its sole terminal outcome, every included Order is `CANCELLED` for non-payment, and no received Payment, Refund or Fulfillment exists.
- The endpoint acts on the entire PaymentGroup even though an order id locates it. It requires the exact group amount, an actual received time, bank transaction reference, reason, and `Idempotency-Key`; an amount mismatch uses the dedicated group-refund command above and never enters late saleability/reacquisition classification.
- For a qualifying `CANCELLED` exact receipt, the same transaction keeps immutable `totalAmount`, leaves approved amount/time null, restores `refundableAmount=totalAmount=actualAmount` from the unpaid-cancelled zero, and stores the received Payment as `PAYMENT_EXCEPTION`. This restoration happens before the Order-scoped Refunds can be completed.
- The shared B-102 portal payment-command PaymentEvent row stores command type, request hash, and ADMIN-safe result snapshot under unique `(paymentGroupId, Idempotency-Key)`. Identical confirmation/late-deposit replay returns the stored response; reuse with another command, amount, time, reference, depositor, or reason returns `409` before mutation.
- PaymentGroup, affected Suppliers, Products, every affected Option including UNTRACKED, and Orders/Fulfillments are locked in the shared stable order above. For `EXPIRED`, the locks protect every supplier/product/option/compliance/supplier-availability/contract recheck; when `depositedAt` is within the original deadline and every TRACKED quantity is reacquired, one approved Payment is created and all Orders move to `SUPPLIER_ORDER_PENDING`. B-102 creates or routes no Fulfillment and does not choose the `salesAction=KEEP` `COREABLE_MANUAL` fallback; B-103 owns that decision from the paid state. A qualifying `CANCELLED` group never enters the approval/reacquisition branches: any stray HELD evidence is released once, and no stock is reacquired or consumed.
- When reacquisition fails or `depositedAt` is after the deadline, exactly one received `BANK_TRANSFER` Payment with `status=PAYMENT_EXCEPTION`, one PaymentEvent, `PaymentGroup.status=PAYMENT_EXCEPTION`, an exception history for every delivery-group Order, and the Refunds below are committed in one transaction. `PAYMENT_EXCEPTION` is not a separately committed final Order status for this portal command.
- After exact amount is established, qualifying unpaid `CANCELLED` has terminal priority and uses `LATE_DEPOSIT_EXCEPTION` without saleability or reacquisition. For remaining pending/expired portal paths, a current saleability/compliance/contract/mode failure uses `SALE_UNAVAILABLE_AT_DEPOSIT`, including when timestamp is also late; otherwise a late timestamp or stock-reacquisition failure uses `LATE_DEPOSIT_EXCEPTION`. Normal failure releases HELD once; late-path tentative reacquisition rollback preserves RELEASED.
- The same command creates exactly one `Refund(status=REQUESTED, refundScope=DELIVERY_GROUP_ORDER, reason=LATE_DEPOSIT_EXCEPTION|SALE_UNAVAILABLE_AT_DEPOSIT)` for each immutable delivery-group Order amount and commits every affected Order directly from `EXPIRED` or qualifying unpaid `CANCELLED` to final `REFUND_REQUESTED`. Existing unique `refunds.order_id` plus the command `Idempotency-Key` prevent duplicate Refunds; their amounts sum to the exact received group total.
- A `PAYMENT_EXCEPTION` result never creates or resumes `SUPPLIER_ORDER_PENDING`, Fulfillment, address/PII exposure, supplier notification, or tracking work. There is no retry-to-normal endpoint. Coreable uses the admin refund/customer-contact queue and existing delivery-group refund actions.
- Admin exception/refund queues and order detail include PaymentGroup exception status, actual deposit evidence, reason, created Refunds and next action. Customer checkout and order-history APIs expose only `REFUND_PROCESSING`, `입금 확인 및 환불 처리 중`, and the applicable refund amount; depositor, bank reference, admin reason, account and transfer evidence are ADMIN-only.
- Implemented B-103 `/api/supplier/orders` predicates require `fulfillment.channel=SUPPLIER_PORTAL`, `operationalOwner=SUPPLIER`, and exclude a `PaymentGroup=PAYMENT_EXCEPTION` or late-deposit Refund unconditionally. A guessed exception order number on supplier list or detail returns `404` without writing a PII access log; Implemented B-104 Shipment and B-105 shortage/claim-task mutations preserve the same boundary.

### Supplier Fulfillment, Minimum PII, And Audit (Implemented B-103)

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/supplier/orders` | `ROLE_SUPPLIER` | Implemented B-103 | List only paid supplier-owned `SUPPLIER_PORTAL` fulfillment requests for current supplier, with no customer PII |
| `GET` | `/api/supplier/orders/{orderNumber}` | `ROLE_SUPPLIER` | Implemented B-103 | Read minimum fulfillment detail and write one PII access log |
| `GET` | `/api/admin/supplier-pii-access-logs` | `ADMIN` | Implemented B-103 | List minimal actor/order/reason/time audit rows without PII; filtering is outside the B-103 MVP |
| `POST` | `/api/admin/orders/{orderId}/portal-takeover` | `ADMIN` | Implemented B-103 | Idempotently take one open portal fulfillment into Coreable ownership with reason |
| `POST` | `/api/admin/claims/{claimId}/supplier-pii-access-grants` | `ADMIN` | Implemented B-103 | Append a bounded PII grant/extension history row with deadline, reason, and idempotency key |
| `POST` | `/api/admin/claims/{claimId}/supplier-pii-access-grants/revoke` | `ADMIN` | Implemented B-103 | Append a revocation history row with required reason and idempotency key |

Admin takeover request:

```json
POST /api/admin/orders/{orderId}/portal-takeover
Idempotency-Key: portal-takeover-example
{
  "reason": "COREABLE_FULFILLMENT_TAKEOVER"
}
```

The command accepts a required ADMIN-only `reason` code: `COREABLE_FULFILLMENT_TAKEOVER`, `SUPPLIER_SUPPORT_REQUIRED`, or `OPERATIONAL_RISK`. Free text is not accepted, so an address or other customer data cannot be preserved in the append-only command row. It locks the portal Fulfillment and appends an immutable handover command row containing that allowlisted code, request hash, and an ADMIN-safe result snapshot; the denormalized Fulfillment reason uses the same validated value. Unique `(fulfillmentId, Idempotency-Key)` returns the stored result for an identical replay and returns `409` for a different payload. It changes only an open `SUPPLIER_PORTAL/owner=SUPPLIER` row to COREABLE; an already-Coreable row is successful only through matching replay, and ownership is never auto-restored.

Grant/extension accepts `action=GRANTED|EXTENDED`, `expectedLatestGrantId` (null only for the first grant), a future `accessUntil` no later than `now + 30 days`, and one required ADMIN-only `reason` code: `RETURN_COORDINATION_REQUIRED`, `EXCHANGE_COORDINATION_REQUIRED`, or `REFUND_COORDINATION_REQUIRED`. Revoke accepts `expectedLatestGrantId` and only `CLAIM_ACCESS_NO_LONGER_REQUIRED`. Both require `Idempotency-Key`; arbitrary text is not accepted or copied from Claim/customer content. After ADMIN and Order/Claim scoping, the command checks `(claimId,key)` and request hash first, then locks Order -> Claim -> latest grant stream, verifies the expected id and allowed Claim status, and appends the next unique sequence with an immutable ADMIN-safe result. An identical replay returns that result and changed payload or stale expected id returns `409`. EXTENDED requires the latest row to be an active GRANTED/EXTENDED; after REVOKED, only a deliberate fresh GRANTED may reopen access. The server derives supplier from the Claim Order and never updates prior rows. An extension is independently bounded rather than added to the old deadline. Effective access is determined by highest sequence, not timestamp ties, and exists only while its action is GRANTED/EXTENDED, `accessUntil > now`, and Claim is `APPROVED`, `RETURN_WAITING`, `RETURN_RECEIVED`, `REFUND_PROCESSING`, or `EXCHANGE_SHIPPING`; every other/terminal state masks immediately. Grant reason/history stays ADMIN-only.

Implemented B-103 checkout address extension (`deliveryMemo` is optional, max 300 characters, trimmed, and blank becomes `null`):

```json
POST /api/checkouts
PATCH /api/checkouts/{checkoutNumber}/shipping-address
{
  "recipientName": "Receiver",
  "recipientPhone": "010-1111-2222",
  "postalCode": "12345",
  "address1": "Base address",
  "address2": "Detail address",
  "deliveryMemo": "Leave at the door"
}
```

Supplier order list shape:

```json
{
  "orders": [
    {
      "orderNumber": "ORD-EXAMPLE",
      "status": "FULFILLMENT_REQUESTED",
      "requestedAt": "2026-08-29T00:00:00Z",
      "items": [
        {
          "productName": "Product",
          "optionName": "기본",
          "quantity": 2
        }
      ]
    }
  ]
}
```

Supplier order detail shape:

```json
{
  "orderNumber": "ORD-EXAMPLE",
  "status": "FULFILLMENT_REQUESTED",
  "requestedAt": "2026-08-29T00:00:00Z",
  "piiAccessLevel": "FULL",
  "piiBasis": "NORMAL_WINDOW",
  "piiAccessUntil": "2026-10-28T00:00:00Z",
  "recipient": {
    "name": "Receiver",
    "phone": "01011112222",
    "postalCode": "12345",
    "address1": "Base address",
    "address2": "Detail address",
    "deliveryMemo": "Leave at the door"
  },
  "items": [
    {
      "orderItemId": "00000000-0000-0000-0000-000000000001",
      "productName": "Product",
      "optionName": "기본",
      "quantity": 2,
      "allocatedQuantity": 0,
      "remainingQuantity": 2
    }
  ]
}
```

B-103 privacy contract:

- The list contains only order number, supplier-facing processing status, current-supplier item/option names and quantities, and request time. It contains no recipient/customer PII.
- Supplier paid-work list/detail and every shipment/shortage mutation require a time-valid VERIFIED supplier contract in addition to `Fulfillment.channel=SUPPLIER_PORTAL`, `operationalOwner=SUPPLIER`, current supplier ownership, and an action-eligible Order state. Detail additionally requires the original supplier's current ACTIVE portal and manager binding. Owner-SUPPLIER work returns normal FULL detail only before its cutoff. Original-supplier work handed to COREABLE because the cutoff or terminal mask boundary was reached remains readable as the dedicated MASKED projection; an active allowed-status Claim grant may instead return read-only FULL detail only while the contract remains time-valid. Contract expiry/revocation is a lifecycle authorization failure and returns `403`, including when an older grant exists. Other non-readable handover reasons, including admin takeover and shortage, return `404` regardless of a grant and use their separate safe queues. None of these read exceptions grants shipment/shortage mutation. Suspension/disconnect/contract termination changes open work to COREABLE with handover evidence; reactivation or re-verification does not silently return it. KEEP creates new paid work as `COREABLE_MANUAL` until portal access is active. Missing authority or current lifecycle/contract failure returns `403`; cross-tenant, wrong-channel, or non-readable handover returns `404`; an owned mutation with disallowed owner/state returns `409`. No failure writes a PII log or partial mutation.
- Supplier status values are a dedicated projection: `SUPPLIER_ORDER_PENDING -> FULFILLMENT_REQUESTED`, first tracking -> `TRACKING_REGISTERED`, delivery -> `DELIVERED`, and cancelled/refund handling -> `CLOSED`. The shortage POST/report projection returns `SHORTAGE_REPORTED` while its report is REPORTED, but the order leaves the supplier fulfillment list when ownership is handed to Coreable. Raw Order status is never serialized, so `FULFILLMENT_REQUESTED` is an action queue rather than an accept-wait state. No live-carrier signal means no separate in-transit portal value in this slice.
- The detail allowlist is order number; recipient name/phone/postcode/address1/address2/delivery memo; current-supplier stable order-item id, item/option names, ordered/allocated/remaining quantities; and non-sensitive portal processing/access metadata. B-103 initially returned allocated 0/remaining ordered quantity; Implemented B-104 keeps the same fields current after each Shipment mutation.
- Forbidden fields include customer/member id, customer email/display name, checkout/payment/depositor/bank/amount fields, refund account/execution, claim content, admin memo/audit content, another supplier identity/items, and the existing `sourceUnitPrice` cost snapshot. The owned-product catalog contract may show the supplier its current editable cost, but supplier order/settlement DTOs never expose the historical order cost snapshot.
- The supplier DTO is a dedicated projection. It must not serialize or filter the existing AdminOrder response after loading its broader customer/payment/refund graph.
- Fulfillment stores `piiAccessCutoffAt`, initialized by B-103 to `requestedAt + 60 days`; B-103 reads it and enforces scheduler/lazy takeover at the exact boundary. Implemented B-104 makes each tracking registration atomically shorten it to `min(current cutoff, registeredAt + 30 days)` and proves void/replacement never extends it. Normal FULL access is only while `now < piiAccessCutoffAt`.
- An idempotent scheduler takes any still-SUPPLIER-owned open portal Fulfillment over to COREABLE at `now >= piiAccessCutoffAt`, recording reason `PII_CUTOFF_REACHED` in immutable handover history under an owner compare-and-set. Supplier mutation guards also enforce the cutoff lazily under the Fulfillment lock: at/after cutoff they perform the same takeover and reject the requested mutation, so scheduler lag never extends write access. The original supplier's active manager may still read the MASKED detail with `EXPIRED_MASKED`; an active Claim grant may return read-only FULL detail only with a time-valid contract and never restores shipment/shortage mutation. ADMIN may use `portal-takeover` earlier with `Idempotency-Key`, request hash, stored result, and reason. Neither path auto-returns ownership; Coreable can then use the B-104 admin portal-shipment action.
- An order masks immediately when it reaches OUT_OF_STOCK, CANCELLED, REFUND_REQUESTED, or REFUNDED, regardless of whether a non-voided Shipment exists. Those transitions atomically set an open portal Fulfillment's operational owner to COREABLE with handover evidence; the original supplier's active manager receives only `TERMINAL_MASKED`. The latest effective append-only Claim grant may reopen FULL access only before its explicit admin-owned deadline, while no later revocation exists, while the Claim remains in an allowed ongoing status, and while the supplier contract remains time-valid VERIFIED.
- At cutoff, a one-character name becomes `*`; a name with two or more Unicode code points becomes its first code point plus fixed `**`. Phone is normalized to digits: four digits or fewer are fully masked, otherwise all preceding digits are `*` and only the final four remain. Postcode, both address fields, and delivery memo are `null`.
- Every detail response, including MASKED, writes only `actorUserId`, `orderId`, `accessReason` (`NORMAL_FULL`, `CLAIM_FULL`, `TERMINAL_MASKED`, `EXPIRED_MASKED`) and `accessedAt`. Supplier/grant validity is checked from the Order and grant history but not duplicated in this minimal log, which never copies PII.
- Detail responses set `Cache-Control: no-store`; Next/server data fetches for this route also disable cache persistence.
- Supplier operational email goes only to the current verified Supplier contact email. Subject/body/payload may contain order number, product identifier, event type, and portal link, but no recipient/customer PII, delivery memo, payment, or refund data. Dispatch and every retry re-read the current Supplier, active portal/manager binding, time-valid VERIFIED contract, verified email, and stored recipient; a mismatch or lifecycle/contract revocation marks the pending notification `SKIPPED` instead of sending to an old contact. Supplier-linked writers map provider exceptions to an allowlisted redacted failure code and never persist raw exception messages. Operational retry ends seven days after creation; SENT/SKIPPED or retry-ended FAILED recipient and any legacy/free-form `failureReason` are nulled after another 30 days, while an allowlisted non-PII failure code and other event/template/delivery evidence may remain. B-103 produces fulfillment-request and admin product-review-result notifications; B-105 claim-task creation produces `SUPPLIER_CLAIM_WORK_REQUESTED` using the B-103 type/template.

### Tracking And Multiple Shipments (Implemented B-104)

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/orders/{orderId}/shipments` | `CUSTOMER` | Implemented B-104 | List non-voided shipments for an owned order with server-generated official links |
| `GET` | `/api/supplier/carriers` | `ROLE_SUPPLIER` | Implemented B-104 | Return supported carrier codes, display names and official-link capability |
| `GET` | `/api/supplier/orders/{orderNumber}/shipments` | `ROLE_SUPPLIER` | Implemented B-104 | List current supplier tracking registrations and allocations |
| `POST` | `/api/supplier/orders/{orderNumber}/shipments` | `ROLE_SUPPLIER` | Implemented B-104 | Register carrier/tracking and allocate owned order-item quantities |
| `PATCH` | `/api/supplier/orders/{orderNumber}/shipments/{shipmentId}` | `ROLE_SUPPLIER` | Implemented B-104 | Correct owned non-delivered carrier/tracking with idempotency, version, and reason guards |
| `GET` | `/api/admin/carriers` | `ADMIN` | Implemented B-104 | Return the same server-owned carrier registry used by admin portal Shipment actions |
| `GET` | `/api/admin/orders/{orderId}/portal-shipments` | `ADMIN` | Implemented B-104 | List current and VOIDED portal Shipments, allocations, versions and history |
| `POST` | `/api/admin/orders/{orderId}/portal-shipments` | `ADMIN` | Implemented B-104 | Register an allocated shipment for handed-over `SUPPLIER_PORTAL` work owned by Coreable |
| `PATCH` | `/api/admin/shipments/{shipmentId}/tracking-correction` | `ADMIN` | Implemented B-104 | Correct non-delivered portal carrier/tracking and preserve before/after evidence |
| `POST` | `/api/admin/shipments/{shipmentId}/void` | `ADMIN` | Implemented B-104 | Void a non-delivered duplicate/error and release its active allocations |
| `POST` | `/api/admin/shipments/{shipmentId}/delivery-complete` | `ADMIN` | Implemented B-104 | Record observed delivery evidence and recalculate the Order aggregate |
| `POST` | `/api/admin/shipments/{shipmentId}/delivery-correction` | `ADMIN` | Implemented B-104 | Reopen an erroneous manual delivery or correct its delivered time under strict guards |

`GET /api/supplier/carriers` and `GET /api/admin/carriers` return exactly these four registry entries: `CJ_LOGISTICS`/`CJ대한통운`, `LOTTE`/`롯데택배`, `HANJIN`/`한진택배`, and `KOREA_POST`/`우체국택배`. `officialTrackingSupported` is true for each entry. The registry only generates official links; B-104 has no live carrier-status API.

Shipment request:

```json
POST /api/supplier/orders/{orderNumber}/shipments
Idempotency-Key: shipment-registration-example
{
  "carrierCode": "CJ_LOGISTICS",
  "trackingNumber": "1234567890",
  "allocations": [
    {
      "orderItemId": "00000000-0000-0000-0000-000000000000",
      "quantity": 2
    }
  ]
}
```

Correction and delivery request bodies:

```json
PATCH /api/supplier/orders/{orderNumber}/shipments/{shipmentId}
Idempotency-Key: supplier-tracking-correction-example
{
  "expectedVersion": 1,
  "carrierCode": "CJ_LOGISTICS",
  "trackingNumber": "1234567891",
  "reason": "Tracking number typo"
}

PATCH /api/admin/shipments/{shipmentId}/tracking-correction
Idempotency-Key: admin-tracking-correction-example
{
  "expectedVersion": 2,
  "carrierCode": "CJ_LOGISTICS",
  "trackingNumber": "1234567892",
  "reason": "Verified corrected number"
}

POST /api/admin/shipments/{shipmentId}/void
Idempotency-Key: shipment-void-example
{
  "expectedVersion": 2,
  "reason": "Duplicate registration"
}

POST /api/admin/shipments/{shipmentId}/delivery-complete
Idempotency-Key: delivery-complete-example
{
  "expectedVersion": 2,
  "deliveredAt": "2026-08-29T02:00:00Z",
  "evidenceObservedAt": "2026-08-29T03:00:00Z",
  "reason": "Verified on official carrier page"
}
```

Delivery correction request:

```json
POST /api/admin/shipments/{shipmentId}/delivery-correction
Idempotency-Key: delivery-correction-example
{
  "expectedVersion": 3,
  "correctionType": "CORRECT_DELIVERED_AT",
  "correctedDeliveredAt": "2026-08-29T02:30:00Z",
  "evidenceObservedAt": "2026-08-29T03:00:00Z",
  "reason": "Delivery completion was entered for the wrong shipment"
}
```

For `CORRECT_DELIVERED_AT`, both `correctedDeliveredAt` and `evidenceObservedAt` are required. For `REOPEN_TRACKING`, both are forbidden.

Customer shipment response:

```json
{
  "shipments": [
    {
      "shipmentId": "00000000-0000-0000-0000-000000000000",
      "carrierCode": "CJ_LOGISTICS",
      "carrierName": "CJ대한통운",
      "trackingNumber": "1234567890",
      "officialTrackingUrl": "https://official-carrier.example/track/1234567890",
      "displayStatus": "TRACKING_REGISTERED",
      "registeredAt": "2026-08-29T01:00:00Z",
      "deliveredAt": null,
      "allocations": [
        {
          "orderItemId": "00000000-0000-0000-0000-000000000001",
          "quantity": 2
        }
      ]
    }
  ],
  "allocationComplete": true
}
```

The example domain is illustrative; production URLs come only from the server carrier registry.

Supplier shipment response:

```json
{
  "shipments": [
    {
      "shipmentId": "00000000-0000-0000-0000-000000000000",
      "version": 2,
      "status": "TRACKING_REGISTERED",
      "carrierCode": "CJ_LOGISTICS",
      "carrierName": "CJ대한통운",
      "trackingNumber": "1234567890",
      "officialTrackingUrl": "https://official-carrier.example/track/1234567890",
      "editable": true,
      "countsTowardAllocation": true,
      "registeredAt": "2026-08-29T01:00:00Z",
      "deliveredAt": null,
      "allocations": [
        {
          "orderItemId": "00000000-0000-0000-0000-000000000001",
          "quantity": 2
        }
      ]
    }
  ],
  "unallocatedItems": [],
  "allocationComplete": true,
  "canReportShortage": false,
  "canRegisterShipment": false,
  "nextAction": "NONE"
}
```

Supplier shipment GET includes current and VOIDED rows for its own Order so the UI can explain a required re-registration; VOIDED rows set `editable=false` and `countsTowardAllocation=false` without exposing an admin-only reason. Implemented B-105 adds `canReportShortage` only to this `SupplierShipmentListResponse`; it is true exactly while the same order is eligible for a first whole-order shortage report. It is not added to the supplier order-detail DTO or shortage-report responses.

B-104 rules:

- A supplier may register or correct tracking only when the owned paid Order has `channel=SUPPLIER_PORTAL`, `operationalOwner=SUPPLIER`, and status `SUPPLIER_ORDER_PENDING|TRACKING_REGISTERED`. Carrier/tracking creation does not prove pickup and cannot set `SHIPPED` or `DELIVERED` directly.
- B-104 establishes Order -> Fulfillment -> all Shipment rows -> OrderItems for every portal Shipment create/correct/void/delivery mutation and admin portal-shipment command. Owner/state/cutoff/allocation predicates are rechecked and the full aggregate is recalculated. Implemented B-105 inserts the optional report row between Fulfillment and Shipment locks for those commands and for shortage submit/review and adds the open-REPORTED guard. Every Claim/Refund writer locks its parent Order before the new Claim/Refund row; payment-origin Refund keeps the broader `PaymentGroup -> Supplier -> Product -> Option -> Order -> Refund` order rather than jumping to Order first. Delivery correction checks dependent Claim/Refund absence under that same Order lock.
- Coreable uses `POST /api/admin/orders/{orderId}/portal-shipments` only for `channel=SUPPLIER_PORTAL`, `operationalOwner=COREABLE`, action-eligible handed-over work. It reuses the same body, idempotency, carrier registry, allocation locks, response, and aggregate rules as supplier creation while recording ADMIN as actor. From B-105 onward it rejects an open REPORTED shortage; after REJECTED it may continue manually, while APPROVED follows the out-of-stock/refund path. A B-103 KEEP fallback with `channel=COREABLE_MANUAL` stays on the legacy Coreable path.
- While production `APP_SUPPLIER_PORTAL_ENABLED=false`, a new admin portal-Shipment creation fails `409 SUPPLIER_PORTAL_NOT_RELEASED`. An already stored identical `(orderId, Idempotency-Key)` replay is resolved before this gate and remains replayable without another write; changed payload or actor/action still conflicts. Read, correction, void and delivery-evidence cleanup paths remain available for already stored portal evidence.
- The request accepts a carrier code, tracking number, and optional first-shipment allocations. It never accepts arbitrary tracking URL, shipping/delivery timestamps, status, another order item, or more than the remaining quantity.
- The server builds the official carrier URL from a carrier registry. The registry maps `carrierCode` to a canonical legacy carrier name; new portal writes persist both `carrierCode` and the existing `carrier` value during compatibility. The first shipment defaults an omitted allocation to every remaining item quantity; later shipments require explicit positive allocations.
- After authenticating the tenant/order relationship, Shipment create checks `(orderId, Idempotency-Key)` and immutable request hash before mutable owner/state/cutoff guards. The canonical hash includes the exact creation action (`SUPPLIER_CREATE` or `ADMIN_CREATE`), `actorType`, and canonical body because the unique key space is shared by supplier/admin routes. It therefore returns the stored first response after a later takeover or status change; the same key through another actor/route or with changed payload returns `409` and never replays another actor's result. Only a new command then takes the common Order/Fulfillment/item locks to prevent duplicate/over-allocation.
- First registration moves a portal order/shipment to `TRACKING_REGISTERED`; it is not a customer `SHIPPED` event.
- Supplier PATCH requires `Idempotency-Key`, expected `version`, replacement carrier/tracking values, and a 200-character-or-shorter single-line PII-free reason. It may not change allocation; an allocation mistake requires Coreable void followed by a new registration so ShipmentItem rows remain immutable.
- Supplier/admin correction, void, delivery-complete, and delivery-correction authenticate resource scope, then check their action-history key/hash before current version/state guards. The shared `(shipmentId, Idempotency-Key)` hash includes the exact action, `actorType`, and canonical body, so only the same actor/action/payload replays the stored result even after later changes; another action/actor or changed payload conflicts and never receives another actor's result. New commands require `Idempotency-Key`, expected version, and a 200-character-or-shorter single-line PII-free reason. Tracking correction changes carrier/tracking only; void is allowed only before delivery. Delivery-complete requires `registeredAt <= deliveredAt <= evidenceObservedAt <= now`. `CORRECT_DELIVERED_AT` requires `registeredAt <= correctedDeliveredAt <= evidenceObservedAt <= now`.
- A VOIDED Shipment and its rows stay as audit evidence but no longer count toward active allocation. If void leaves no non-voided Shipment, Order returns to `SUPPLIER_ORDER_PENDING` and supplier display `FULFILLMENT_REQUESTED`; otherwise it remains or recalculates to `TRACKING_REGISTERED`, unless the complete-delivery rule makes it `DELIVERED`.
- Delivery correction is allowed only for a portal Shipment whose delivery was set by the B-104 admin delivery-complete action and only when no Claim or Refund was created after that delivery. `REOPEN_TRACKING` clears the effective delivered timestamp, sets Shipment to `TRACKING_REGISTERED`, recalculates Order, appends `ADMIN_DELIVERY_REOPENED`, and creates a customer notification for the visible rollback. `CORRECT_DELIVERED_AT` updates only the effective timestamp and appends `ADMIN_DELIVERED_AT_CORRECTED`. Original delivery evidence remains in before/after history; a dependent Claim/Refund returns `409` and requires incident/claim handling.
- Order becomes DELIVERED only after all item quantities are allocated to non-voided Shipments and each has Coreable delivery evidence. Suppliers cannot mark delivery complete.
- Claim eligibility and statutory delivery windows for a plural-shipment Order use `max(deliveredAt)` across non-voided Shipments, not the legacy singular projection. B-104 migrates every Claim/cancellation/refund status guard and customer/admin status allowlist to recognize `TRACKING_REGISTERED`; customer direct cancel remains blocked, while a Coreable-reviewed cancellation Claim may proceed only after active tracking is voided/stopped or is routed into the appropriate return flow.
- `GET /api/orders/{orderId}/shipments` returns `{shipments, allocationComplete}`. The implemented owned customer order detail adds top-level `shipments`, `shipmentAllocationComplete`, and `shipmentCompatibilityTruncated`; its canonical `shipments[]` returns only non-voided rows with `shipmentId`, `carrierCode`, `carrierName`, `trackingNumber`, server-generated `officialTrackingUrl`, `displayStatus`, `registeredAt`, and item allocations. `TRACKING_REGISTERED` maps exactly to `송장 등록 · 배송조회 가능` and never implies pickup or in-transit. Admin order detail uses the same three top-level compatibility fields and includes current plus VOIDED rows, versions, and correction history. `GET /api/admin/orders/{orderId}/portal-shipments` returns `{shipments, unallocatedItems, allocationComplete}`.
- V43 implements the plural Shipment/allocation/history schema. Under the existing expand-contract principle, repository callers of singular `findByOrder...` semantics move to plural/aggregate reads and legacy carrier codes are backfilled only where the mapping is deterministic before the singular constraint is removed. Legacy rows with an unsupported carrier keep `carrierCode=null` and produce no official URL.
- Existing one-shipment customer/admin fields and Domeggook tracking behavior remain during an expand-contract compatibility release while `shipments[]` is added. With a row, legacy singular selects earliest non-voided `(registeredAt,id)` and sets `shipmentCompatibilityTruncated=true` when plural was reduced. With no row, customer detail preserves its current non-null `{status: "READY", carrier: null, trackingNumber: null}` placeholder because the current web reads `shipment.status` without a null guard, while admin detail preserves its current null. Canonical `shipments[]` is empty for both.

### Shortage, Claim Tasks, And Facts (Implemented B-105)

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `POST` | `/api/supplier/orders/{orderNumber}/shortage-reports` | `ROLE_SUPPLIER` | Implemented B-105 | Report whole delivery-group shortage before any tracking registration |
| `GET` | `/api/supplier/shortage-reports` | `ROLE_SUPPLIER` | Implemented B-105 | List the current supplier's PII-free report status and next action |
| `GET` | `/api/supplier/shortage-reports/{reportId}` | `ROLE_SUPPLIER` | Implemented B-105 | Read one owned PII-free shortage report result |
| `GET` | `/api/supplier/claim-tasks` | `ROLE_SUPPLIER` | Implemented B-105 | List only Coreable-requested task summaries for the current supplier |
| `GET` | `/api/supplier/claim-tasks/{taskId}` | `ROLE_SUPPLIER` | Implemented B-105 | Read one safe task projection and its fact history without Claim/customer content |
| `POST` | `/api/supplier/claim-tasks/{taskId}/facts` | `ROLE_SUPPLIER` | Implemented B-105 | Append one type-matched validated answer or correction |
| `GET` | `/api/admin/supplier-shortage-reports` | `ADMIN` | Implemented B-105 | Review/filter reported supplier shortages |
| `GET` | `/api/admin/supplier-shortage-reports/{reportId}` | `ADMIN` | Implemented B-105 | Read one report with order linkage and review evidence |
| `POST` | `/api/admin/supplier-shortage-reports/{reportId}/approve` | `ADMIN` | Implemented B-105 | Approve a reported shortage through the existing Coreable out-of-stock service |
| `POST` | `/api/admin/supplier-shortage-reports/{reportId}/reject` | `ADMIN` | Implemented B-105 | Reject a report while retaining Coreable operational ownership |
| `GET` | `/api/admin/supplier-claim-tasks` | `ADMIN` | Implemented B-105 | Read/filter Coreable-created supplier task summaries with internal claim context |
| `GET` | `/api/admin/supplier-claim-tasks/{taskId}` | `ADMIN` | Implemented B-105 | Read one task and its append-only safe fact history for Coreable review |
| `POST` | `/api/admin/claims/{claimId}/supplier-tasks` | `ADMIN` | Implemented B-105 | Create a non-PII supplier task for the Claim Order supplier |
| `POST` | `/api/admin/supplier-claim-tasks/{taskId}/close` | `ADMIN` | Implemented B-105 | Close supplier input with an allowlisted reason code |

초기 운영량에서는 pagination을 추가하지 않는다. Shortage list는 stable `createdAt DESC, reportId DESC`의 `{ "reports": [...] }`, claim-task list는 stable `requestedAt DESC, taskId DESC`의 facts 없는 `{ "tasks": [...] }` summary wrapper를 사용한다. Fact history는 supplier/admin detail에서만 반환한다. Supplier shortage/task list는 각각 optional `status`만, ADMIN shortage list는 optional `status`와 `orderId`, ADMIN task list는 optional `status`, `claimId`, `orderId`만 받는다. 이 지원 filter에 유효하지 않은 enum 또는 identifier 값은 `400`이며 실제 운영량이 측정되어 필요해질 때만 pagination을 추가한다.

Shortage request:

```json
POST /api/supplier/orders/{orderNumber}/shortage-reports
Idempotency-Key: shortage-example
{
  "reasonCode": "OUT_OF_STOCK"
}
```

Supplier shortage responses contain only `reportId`, `orderNumber`, `reasonCode`, `status=REPORTED|APPROVED|REJECTED`, `reportedAt`, `reviewedAt`, allowlisted `reviewReasonCode`, and derived `nextAction=WAIT|NONE|CONTACT_COREABLE`. They contain no recipient, customer, payment, refund, Claim, internal review value, or `orderDetailAvailable`; shortage report Web provides no supplier-order link.

Admin review requests require `Idempotency-Key`:

```json
POST /api/admin/supplier-shortage-reports/{reportId}/approve
Idempotency-Key: shortage-review-example
{
  "expectedStatus": "REPORTED",
  "reviewReasonCode": "SHORTAGE_CONFIRMED"
}
```

Reject uses the same shape with `reviewReasonCode=INSUFFICIENT_EVIDENCE|FULFILLMENT_CAN_CONTINUE`. The review command accepts no free-text reason.

Admin task creation request:

```json
POST /api/admin/claims/{claimId}/supplier-tasks
Idempotency-Key: supplier-claim-task-example
{
  "requestedType": "INSPECTION_RESULT",
  "instructionCode": "INSPECT_RETURNED_ITEM",
  "dueAt": "2026-09-01T00:00:00Z"
}
```

The server derives the instruction text from `instructionCode`; arbitrary `instructions`, supplier id, order id, Claim text, or customer data are rejected.

Instruction mapping is exact and one-to-one:

| requestedType | instructionCode | Supplier text |
| --- | --- | --- |
| `SHIPMENT_STOP_RESULT` | `CHECK_SHIPMENT_STOP` | `상품 발송을 멈출 수 있는지 확인해 주세요.` |
| `RETURN_INSTRUCTIONS` | `PROVIDE_RETURN_METHOD` | `반품 수거 방법을 선택해 주세요.` |
| `RETURN_RECEIVED` | `CONFIRM_RETURN_RECEIPT` | `반품 상품 수령 여부를 확인해 주세요.` |
| `INSPECTION_RESULT` | `INSPECT_RETURNED_ITEM` | `반품 상품의 상태를 확인해 주세요.` |

`dueAt` must satisfy `requestedAt < dueAt <= requestedAt + 30 days`, where `requestedAt` is the canonical server creation time. A mismatched requestedType/instructionCode, a non-future deadline, or a deadline beyond 30 days returns `400` before mutation.

Close request:

```json
POST /api/admin/supplier-claim-tasks/{taskId}/close
Idempotency-Key: supplier-claim-task-close-example
{
  "expectedStatus": "ANSWERED",
  "closeReasonCode": "RESPONSE_ACCEPTED"
}
```

ADMIN may close either `OPEN` or `ANSWERED` and allows only `RESPONSE_ACCEPTED`, `SUPERSEDED`, and `NO_LONGER_NEEDED`. `DUE_AT_EXPIRED` and `CLAIM_TERMINAL` are server-derived only when the deadline/status guard actually holds. Supplier create/fact replay results and detail use the canonical supplier-safe task fields. Supplier/admin list responses are facts-free summaries; detail alone adds fact history. ADMIN projections add internal linkage and actor evidence but never the supplier-only `orderDetailAvailable` field.

Claim-task fact request example:

```json
POST /api/supplier/claim-tasks/{taskId}/facts
Idempotency-Key: supplier-claim-fact-example
{
  "type": "INSPECTION_RESULT",
  "payload": {
    "resultCode": "DEFECT_CONFIRMED",
    "inspectedAt": "2026-08-29T01:00:00Z"
  },
  "correctsFactId": null
}
```

Claim-task detail response includes only the supplier's safe task and its own fact history:

```json
{
  "taskId": "00000000-0000-0000-0000-000000000010",
  "orderNumber": "ORD-EXAMPLE",
  "orderDetailAvailable": true,
  "items": [
    {
      "productName": "Product",
      "optionName": "기본",
      "quantity": 1
    }
  ],
  "requestedType": "INSPECTION_RESULT",
  "status": "ANSWERED",
  "instructionCode": "INSPECT_RETURNED_ITEM",
  "instructions": "반품 상품의 상태를 확인해 주세요.",
  "dueAt": "2026-09-01T00:00:00Z",
  "facts": [
    {
      "factId": "00000000-0000-0000-0000-000000000011",
      "type": "INSPECTION_RESULT",
      "payload": {
        "resultCode": "UNDETERMINED",
        "inspectedAt": "2026-08-29T01:00:00Z"
      },
      "correctsFactId": null,
      "createdAt": "2026-08-29T01:01:00Z"
    }
  ]
}
```

B-105 rules:

- A new shortage is allowed only for the supplier's own paid `SUPPLIER_PORTAL/owner=SUPPLIER` order before any Shipment has ever been registered, including one later voided. It accepts only an allowlisted reason code and applies to the whole delivery-group order.
- V44 adds unique `orders(id,supplier_id)` and a report composite FK `(order_id,supplier_id)` so persisted shortage ownership cannot cross tenants. It also adds unique `claims(id,order_id)`, immutable `supplier_claim_tasks.order_id`, and task composite FKs `(claim_id,order_id)` and `(order_id,supplier_id)` so Claim, Order, and Supplier remain one structural scope.
- The existing `GET /api/supplier/orders/{orderNumber}/shipments` response exposes that current action eligibility as `canReportShortage`; the supplier order detail renders the whole-shortage action only when it is true. The boolean grants no authority, and POST rechecks every eligibility predicate under the common write locks.
- The POST first resolves `(supplierId, Idempotency-Key)` and compares its request hash before checking the current owner/state. The report stores an immutable supplier-safe canonical submit result, so an identical retry returns that first response even after creation handed the Fulfillment to Coreable or review changed the report; the same key with another payload returns `409`. Because the report row durably binds only its original submit key, every second key for the same order returns `409 SHORTAGE_ALREADY_REPORTED` even when the reason code matches.
- Report creation stores `status=REPORTED` and atomically changes only the Fulfillment operational owner to COREABLE with reason `SUPPLIER_SHORTAGE_REPORTED`. Order, Claim, Payment, and Refund state remain unchanged until Coreable review; no supplier queue/email is recreated.
- Submit, admin approve/reject, and portal-shipment commands share the Order -> Fulfillment -> report/Shipment -> OrderItems lock order. Submit and new review commands recheck that no Shipment ever exists, and an open REPORTED report blocks Coreable portal shipment creation. Approval/rejection therefore cannot race a tracking row into the reviewed order.
- After ADMIN authorization and report scoping, approve/reject checks the report-scoped key/hash/result before mutable expected-status, REPORTED, Shipment, or owner guards. An identical replay therefore returns its stored terminal result after APPROVED/REJECTED; changed payload conflicts. Only a new command locks the aggregate and requires `REPORTED`, expected status, an allowlisted `reviewReasonCode`, and `Idempotency-Key`. `SHORTAGE_CONFIRMED` is approve-only; `INSUFFICIENT_EVIDENCE` and `FULFILLMENT_CAN_CONTINUE` are reject-only. Approval invokes the existing Coreable out-of-stock/refund service in the same transaction, moves the whole Order through its existing `OUT_OF_STOCK` and Refund boundary, and marks the report `APPROVED`. Rejection marks it `REJECTED`, creates no Refund, and keeps operational ownership with COREABLE for manual continuation; it never silently returns the work to the supplier.
- Shortage submit and review store no free text, partial quantity, customer PII, Claim, payment, or refund data. Supplier list/detail remains available after handover as the safe status channel; a rejected report returns `nextAction=CONTACT_COREABLE`.
- Supplier cannot submit a fact directly by Claim id. Coreable must first create an OPEN task with one requested type, allowlisted non-PII instruction template, due time, requesting admin, and the Claim Order's supplier.
- Admin task creation requires `Idempotency-Key`. Requested type/instruction code uses the exact four-entry mapping above and `dueAt` is strictly after canonical `requestedAt` and at most 30 days later. The task stores a hash of claim id, requested type, instruction code, and due time plus an immutable ADMIN-safe canonical creation result under unique `(claimId,idempotencyKey)`, so an identical network retry returns the first task response even after answer/close and a changed payload returns `409`; a deliberately new follow-up round uses a new key.
- Task create, supplier fact/correction, and admin close first authenticate and scope the Order/Claim/task/supplier, then resolve their scoped idempotency key, request hash, and stored result before any mutable Claim status, task status, due-time, or correction-target guard. Identical replay returns the first stored result even after terminal transition, close, or deadline; changed payload conflicts. The supplier fact replay has one deliberate derived-field exception: it recomputes `orderDetailAvailable` against the current B-103 authorization before responding, so an old stored `true` can never preserve a stale order-detail link. All other replay fields come from the immutable first result. Only a new command uses the common lock order Order -> Claim -> SupplierClaimTask rows by id -> SupplierClaimFact rows and rechecks task status, current Claim status, due time, tenant, and correction target, preventing two first facts or a fact after close. Every terminal Claim transition uses the same Order -> Claim prefix before atomically closing tasks. Allowed Claim states are `REQUESTED`, `UNDER_REVIEW`, `EVIDENCE_REQUESTED`, `APPROVED`, `RETURN_WAITING`, `RETURN_RECEIVED`, `REFUND_PROCESSING`, and `EXCHANGE_SHIPPING`. `REJECTED`, `COMPLETED`, and `WITHDRAWN` are terminal: their transition atomically closes every OPEN/ANSWERED supplier task with `CLAIM_TERMINAL`. At `now >= dueAt`, new input lazily closes/rejects and an idempotent scheduler also closes the task with `DUE_AT_EXPIRED`; Coreable creates a new-key task if more evidence is needed.
- Supplier task list exposes only task id, order number, derived `orderDetailAvailable`, own item/option names and quantities, requested type, status, safe instruction code/text, and due/requested/answered timestamps. `orderDetailAvailable` is true only when the existing B-103 supplier order-detail authorization currently permits FULL or MASKED read for that same supplier/order; it grants no new access. Its detail adds the same safe correlation fields and same-task `factId`, type, allowlisted payload, `correctsFactId`, and created time so an ANSWERED task can be identified and corrected; it hides Claim/customer/member/payment/refund/admin memo, actor identity, another supplier's work, and any PII.
- Facts are limited to `SHIPMENT_STOP_RESULT`, `RETURN_INSTRUCTIONS`, `RETURN_RECEIVED`, and `INSPECTION_RESULT`; fact type must equal its OPEN/ANSWERED task requested type.
- Each type has a closed payload schema: `SHIPMENT_STOP_RESULT` accepts `resultCode=STOPPED|ALREADY_SHIPPED|UNCONFIRMED` and `checkedAt`; `RETURN_INSTRUCTIONS` accepts `methodCode=COURIER_PICKUP|CUSTOMER_PREPAID|CUSTOMER_COD` plus optional allowlisted `carrierCode`; `RETURN_RECEIVED` accepts `resultCode=RECEIVED|NOT_RECEIVED` and `checkedAt`; `INSPECTION_RESULT` accepts `resultCode=DEFECT_CONFIRMED|NO_DEFECT|DAMAGED_IN_TRANSIT|UNDETERMINED` and `inspectedAt`. Every `checkedAt`/`inspectedAt` must satisfy `task.requestedAt <= fact time <= current server time`.
- Unknown fields, arbitrary text, customer PII, or another task/supplier reference return `400` or tenant-safe `404` as appropriate.
- Facts require `Idempotency-Key` and are append-only. Each fact stores the canonical request hash and immutable supplier-safe result; unique `(taskId, idempotencyKey)` returns that first response for an identical retry after later close/deadline, except that the current authorization-derived `orderDetailAvailable` boolean is recomputed fail-closed, and rejects a reused key with a different payload. The first fact requires OPEN and marks the task ANSWERED once. While ANSWERED, only a correction whose `correctsFactId` equals the current latest effective fact for the same task/type is accepted; the current head is the unique same-task fact not referenced by another row's `correctsFactId`. Task/Fact locks recheck that head so concurrent corrections cannot branch. Supplier/admin detail returns each append-only chain root→current head, never timestamp/id order. It never updates or deletes an earlier row. ADMIN close from OPEN or ANSWERED prevents all further new input.
- V44 backs that linear history with partial unique constraints: only one `correctsFactId=null` root per task and only one child for each non-null predecessor. A uniqueness race is translated to the same stale-head conflict rather than creating another branch.
- Admin task list may include Claim/order links, requesting/closing admin identities and internal task context but no facts; admin detail adds the complete same-task fact history needed for a decision. Neither admin projection includes supplier-only `orderDetailAvailable`. A supplier fact is never treated as a Claim/Refund decision; Coreable must use the existing explicit admin Claim actions afterward.
- Supplier cannot approve/reject a claim, read customer claim content, change Order/Claim/Refund status, or execute/refund payment.
- Coreable ADMIN keeps all CS, cancellation, return/exchange decision, refund approval, and manual bank-transfer completion authority through existing admin flows.
- While `APP_SUPPLIER_PORTAL_ENABLED=false`, ADMIN task creation authenticates and scopes the Claim, resolves stored `(claimId, Idempotency-Key)` hash/result first, returns an identical stored result, rejects a changed replay, and then returns `409 SUPPLIER_PORTAL_NOT_RELEASED` for a new command without mutation or notification. Existing ADMIN shortage read/review, task read/close and system expiry/terminal closure remain available. Supplier routes remain `404`.

### Supplier Tenant And Browser Security Contract (Implemented B-100)

- Production defaults `APP_SUPPLIER_PORTAL_ENABLED=false`. While false, public application, invite exchange/auth callback and `/api/supplier/**` return `404`. After ADMIN/resource scope and stored idempotency replay lookup, a new application approval, invite reissue, contact-email workflow step that would issue a replacement invite, portal-Shipment creation, or B-105 claim-task creation fails before mutation with `409 SUPPLIER_PORTAL_NOT_RELEASED`; an identical completed-command replay may return its token-free stored result but never redispatches. Application rejection, supplier suspension/disable, retention cleanup, existing shortage read/review, task read/close, system close, existing admin order takeover, and legacy customer/admin APIs remain available; the Planned B-098 contract-evidence administration will also remain outside this release gate when implemented. Invitation dispatch rechecks the flag immediately before sending and marks the attempt `SKIPPED/PORTAL_NOT_RELEASED`, so a stale after-commit job cannot leak an activation link while the gate is closed; recovery after reopening requires a new-key reissue. Implemented B-100~B-105 are necessary prerequisites only: the flag remains false until privacy/live-email/B-098 contract gates and their required tests are all ready. After the global flag opens, every portal-product public/read/checkout guard still requires that specific supplier's `Supplier.status=ACTIVE` and time-valid VERIFIED evidence (`effectiveAt <= now` and no expiry or `now < expiresAt`); a newly approved or expired/revoked supplier cannot sell merely because the global gate is on.
- `/api/supplier/**` requires dynamic `ROLE_SUPPLIER` from an active user, active portal status and manager link, and rejects terminal or already-overdue VERIFIED contract state before resolving one current supplier. Initial UNVERIFIED onboarding remains limited to non-PII catalog work. Independent `Supplier.status` gates new sales/checkouts but not already-paid fulfillment while the contract is time-valid; paid-work services recheck time-valid VERIFIED explicitly. Every repository lookup contains both resource identifier and resolved supplier id.
- A missing supplier role returns `403`. An id/order number belonging to another supplier returns `404` to avoid existence disclosure.
- UUID/order-number unguessability is not authorization. Payload `supplierId`, product ownership, image storage owner, order supplier, shipment item order, and claim order are always server-verified.
- Unsafe methods are `POST`, `PUT`, `PATCH`, and `DELETE`. For supplier application, invitation exchange, admin supplier actions, `/api/supplier/**`, and B-105 `POST /api/admin/supplier-shortage-reports/{reportId}/approve`, `POST /api/admin/supplier-shortage-reports/{reportId}/reject`, `POST /api/admin/claims/{claimId}/supplier-tasks`, and `POST /api/admin/supplier-claim-tasks/{taskId}/close`, an `Origin` header must exactly match an allowed scheme/host/port. Only when Origin is absent may the server accept an exact same-origin `Referer`; missing or mismatched Origin+Referer returns `403`.
- CORS success is not treated as CSRF defense. Internal token-authenticated scheduler endpoints stay under their existing separate contract.
- Access and invite-context cookies are `HttpOnly` and `SameSite=Lax`; production cookies require `Secure` and HTTPS. OAuth callback additionally requires the existing one-time state check bound to invite context.
- Raw invite tokens, applicant/supplier contact PII, customer PII, PII-bearing idempotency keys/HMACs, email bodies, supplier costs, and internal/admin memo must not be written to request/application logs.

### Web Client Surface (`B-100`~`B-105` Implemented)

- Public, Implemented B-100: `/supplier/apply`, `/supplier/activate`
- Supplier, Implemented B-100~B-105: `/supplier`, `/supplier/products`, `/supplier/products/new`, `/supplier/products/{productId}` including the B-102 option inventory editor, `/supplier/orders`, `/supplier/orders/{orderNumber}` including B-104 Shipment registration/correction and B-105 whole-shortage action driven only by `SupplierShipmentListResponse.canReportShortage`, plus `/supplier/shortage-reports`, `/supplier/shortage-reports/{reportId}`, `/supplier/claim-tasks`, `/supplier/claim-tasks/{taskId}`
- Admin, Implemented B-100~B-105: `/admin/supplier-applications`, `/admin/supplier-applications/{applicationId}`, `/admin/suppliers`, `/admin/suppliers/{supplierId}`, `/admin/product-reviews`, B-102 payment-exception actions, B-103 portal takeover, B-104 portal Shipment actions in existing `/admin/orders`, B-105 master/detail `/admin/shortage-reports?reportId=...`, `/admin/supplier-claim-tasks`, `/admin/supplier-claim-tasks/{taskId}` and supplier task create/read/close inside the existing admin Claim detail surface. B-103 Claim PII grant/access-log APIs have no dedicated Web screen. Web paths are distinct from the `/api/admin/supplier-shortage-reports/**` API paths.
- Supplier routes use `/api/supplier/me` as their session/tenant gate and never accept supplier id from URL/query/client state.
- Supplier order detail must use uncached server/client fetching and avoid embedding PII in static HTML, page metadata, analytics, email, or browser-persistent storage.

## Catalog APIs

### Customer Catalog

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/products` | Public | Implemented | Page customer-visible active products with search, category, price, and sort filters |
| `GET` | `/api/products/{productId}` | Public | Implemented | Product detail with options, images, detail blocks, and customer policy links |

Customer visibility rules:

- Show only products customer can view.
- Purchase requires product and option status `ACTIVE`, sale-allowing compliance, valid customer price, `Supplier.status=ACTIVE`, and `supplierAvailability=AVAILABLE`. A `TRACKED` option additionally requires derived available quantity at least equal to the requested quantity. A `SUPPLIER_PORTAL` product also requires the global portal feature flag, `AUTO_APPROVED|APPROVED` review status, and a time-valid VERIFIED supplier contract.
- Public product summaries, details, and option responses expose only the derived boolean `purchasable`, evaluated with the current global sales gate and the product's `minimumOrderQuantity`; they never expose inventory mode, on-hand, reserved, available quantity, supplier availability, or an `unlimited` label.

`GET /api/products` query:

- `q`: product name and summary keyword.
- `category`: one leaf category. Takes precedence over `categories`.
- `categories`: repeated leaf categories used for a category group.
- `minPrice`, `maxPrice`: inclusive customer sale price range.
- `sort`: `latest` (default), `price-asc`, or `price-desc`.
- `page`: zero-based page, default `0`.
- `size`: default `24`, range `1..100`.

The response is `{ products, page, size, totalElements, totalPages, categoryCounts }`. `categoryCounts` contains active product counts for each leaf category after applying `q`, `minPrice`, and `maxPrice`, but before a selected `category` or `categories` filter. It is used to show related categories within the current search result.
- Do not expose raw supplier information to customers.
- Product detail responses include `policyLinks` for shipping, cancellation/refund, and payment-after-stockout notices so operational policy is not embedded only in arbitrary product HTML/images.
- Product detail `productNotice.noticeRows` contains structured `{ label, value }` rows from the supplier product information notice. Supplier trade terms and supplier identity are not public fields.

### Admin Catalog

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/admin/suppliers` | `ADMIN` | Implemented | List suppliers |
| `POST` | `/api/admin/suppliers` | `ADMIN` | Implemented | Create supplier |
| `GET` | `/api/admin/suppliers/{supplierId}` | `ADMIN` | Implemented | Supplier detail |
| `PATCH` | `/api/admin/suppliers/{supplierId}` | `ADMIN` | Implemented | Update supplier |
| `GET` | `/api/admin/products` | `ADMIN` | Implemented | Page products with keyword, status, category, supplier, and sale-readiness filters |
| `POST` | `/api/admin/products` | `ADMIN` | Implemented | Create product |
| `GET` | `/api/admin/products/{productId}` | `ADMIN` | Implemented | Product detail for admin editing |
| `PATCH` | `/api/admin/products/{productId}` | `ADMIN` | Implemented | Update product base fields |
| `PATCH` | `/api/admin/products/{productId}/status` | `ADMIN` | Implemented | Change product sales status |
| `POST` | `/api/admin/products/{productId}/options` | `ADMIN` | Implemented | Create product option |
| `PATCH` | `/api/admin/products/{productId}/options/{optionId}` | `ADMIN` | Implemented | Update product option |
| `PATCH` | `/api/admin/products/{productId}/options/{optionId}/status` | `ADMIN` | Implemented | Change option sales status |
| `PUT` | `/api/admin/products/{productId}/images` | `ADMIN` | Implemented | Replace thumbnail/gallery image metadata |
| `POST` | `/api/admin/products/{productId}/images/upload` | `ADMIN` | Implemented | Upload product image file to local storage |
| `PUT` | `/api/admin/products/{productId}/detail-blocks` | `ADMIN` | Implemented | Replace ordered IMAGE/HTML detail blocks |
| `PUT` | `/api/admin/products/{productId}/notice` | `ADMIN` | Implemented | Create next active product notice version |
| `GET` | `/api/admin/products/{productId}/changes` | `ADMIN` | Implemented | Product change audit history |
| `GET` | `/api/admin/pricing-policy` | `ADMIN` | Implemented | Read active product pricing policy |
| `PUT` | `/api/admin/pricing-policy` | `ADMIN` | Implemented | Update active product pricing policy |

DS-6 minimum:

- Supplier model and admin create/update API.
- Product model and admin create/update API.
- Product option model and admin create/update API.
- Product image metadata API with one thumbnail and up to ten gallery images.
- Product image upload stores files under local product image storage and returns `imageUrl` and `objectKey`.
- Admin thumbnail/gallery metadata may attach only the exact `objectKey` and URL returned for that Product by the upload endpoint. A key that already has any cleanup job is a non-reusable tombstone. Retained owned keys survive metadata reorder/replacement; removed keys enqueue the unique durable job idempotently, including reopening a prior `COMPLETED/LIVE_REFERENCE` job after the live metadata is actually removed. Omitted keys keep external/legacy URLs non-owned.
- Products carry one fixed `categoryCode`; category administration and multi-category assignment are future scope.
- Product detail block API with ordered `IMAGE` and sanitized `HTML` blocks.
- Product notice/version source for structured product information notice rows and legacy shipping, AS, return, and exchange information.
- Product change history writes for product, option, image, detail, notice, and supplier changes.
- Product and option status handling without stock quantity.
- Product create/update accepts optional `minimumOrderQuantity` and `orderQuantityStep` values from 1 to 99. Create defaults omitted values to `1`; update preserves the current values.
- Admin and public product responses expose both quantity-rule fields.
- Admin product responses include `sourcePrice`, optional `sourceItemNo`, `sourceUrl`, `sourceAvailable`, `sourceSyncedAt`, and `sourceSyncError`; public product responses expose none of them.
- Source-backed `ACTIVE` products are refreshed in bounded batches. Both success apply and failure recording require the `sourceItemNo` used for the fetch to equal the fresh Product's current `sourceItemNo` after `Supplier -> Product -> Option` locking; a stale fetch writes nothing. V40 adds durable `sourceAutoSoldOut=false` for new and backfilled Products. Confirmed unavailability sets it to `true` only when sync actually changes `ACTIVE -> SOLD_OUT`; sync targeting/recovery includes only marker-backed `SOLD_OUT`. Recovery requires source MOQ at most 10, a positive price within the customer-price cap, compliance other than `REJECTED`, an active option, thumbnail, and active notice, then changes the marker to `false`. Any successful admin status command clears the marker, including `SOLD_OUT -> SOLD_OUT`, so a manual sold-out decision is never auto-recovered. A supplier-side outage otherwise keeps the existing price and options and records `sourceSyncError`.
- `sourceUrl` is limited to 2,000 characters and accepts only `http` or `https`. Domeggook URLs must contain a product number, which the server stores as the unique `sourceItemNo`. Duplicate creation returns `409 Conflict`.
- Admin responses and the legacy public product detail response include `complianceStatus` for compatibility. Supplier product responses cannot set it and expose only their allowlisted review projection; public list responses do not add review internals.
- Admin product list/detail responses include derived `saleReady`, stable `saleBlockers`, `optionCount`, `hasThumbnail`, `hasProductNotice`, and `hasDetailContent`. `saleBlockers` uses `BASE_PRICE`, `THUMBNAIL`, `ACTIVE_OPTION`, `PRODUCT_NOTICE`, and `COMPLIANCE` codes.
- Admin product detail includes `supplierId` and `supplierName`; public product detail omits supplier information.
- Admin product list accepts optional `q`, `status`, `category`, `supplierId`, `readiness=READY|BLOCKED`, `page`, and `size`. `page` is zero-based, `size` defaults to 20 and is limited to 1-100.
- Admin product list returns `{ products, page, size, totalElements, totalPages }` ordered by `createdAt DESC, id DESC`.
- Products must be created as non-active. `ACTIVE` requires a positive sale price, thumbnail, active option, active product notice, and a compliance status other than `REJECTED`.
- Sale readiness is derived from current product data and is not persisted as a separate review-status column. Individual activation remains protected by the same service validation used for readiness display.
- Price, image, option, and compliance updates cannot leave an `ACTIVE` product without those requirements.
- Active pricing policy stores the default margin rates used to calculate customer sale prices from supplier cost.
- Customer product list/detail read APIs.

DS-6 implementation notes:

- Public `/api/products/**` must be permitted by `SecurityConfig`.
- `/api/admin/**` remains `ADMIN` only.
- DS-43 implements the admin product change history read API at `GET /api/admin/products/{productId}/changes`.
- Product image binary upload is implemented for local product image storage and returns URL/object key metadata.
- Product detail and notice version sources must exist before DS-8 order creation can safely snapshot order items.
- `PUT /api/admin/products/{productId}/notice` accepts optional `noticeRows`. Omitting it preserves the current structured rows.

## Cart APIs

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/cart` | `CUSTOMER` | Implemented | Get current user cart |
| `POST` | `/api/cart/items` | `CUSTOMER` | Implemented | Add product option to cart |
| `PATCH` | `/api/cart/items/{cartItemId}` | `CUSTOMER` | Implemented | Update quantity |
| `DELETE` | `/api/cart/items/{cartItemId}` | `CUSTOMER` | Implemented | Remove cart item |
| `POST` | `/api/cart/validate` | `CUSTOMER` | Implemented | Revalidate sellability before checkout |

Rules:

- Cart belongs to authenticated customer.
- Guest cart is excluded from MVP.
- One customer has one current cart.
- Adding the same product option increases the existing cart item quantity and validates the combined result.
- Cart item quantity is at most 99 and must be at least the current product `minimumOrderQuantity` and divisible by `orderQuantityStep`.
- Product option can be added only when the complete current saleability guard passes for the combined quantity: product/option/compliance/customer price, active Supplier, portal feature/review/contract when applicable, `supplierAvailability=AVAILABLE`, and sufficient derived available quantity for TRACKED.
- If product/option/supplier/contract/availability/inventory or MOQ rules change after an item is added, the saved item and quantity remain unchanged but its `sellable` and the cart-level `checkoutAvailable` become false.
- Cart item responses include current `minimumOrderQuantity`, `orderQuantityStep`, `sellable`, and `unavailableReason`; raw inventory fields remain absent.
- Cart response shows current product/option price. Final price is snapshotted by order creation, not cart.
- Cart items can span multiple delivery groups.
- Checkout splits cart into delivery-group orders.
- Cart viewing and editing are allowed before account agreement, but checkout creation requires current account agreement.

Implemented request bodies:

```json
POST /api/cart/items
{
  "productOptionId": "uuid",
  "quantity": 1
}

PATCH /api/cart/items/{cartItemId}
{
  "quantity": 1
}
```

## Checkout And Order APIs

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `POST` | `/api/checkouts` | `CUSTOMER` | Implemented | Create payment group and delivery-group orders from cart |
| `GET` | `/api/checkouts/{checkoutNumber}` | `CUSTOMER` | Implemented | Read checkout/payment group state |
| `PATCH` | `/api/checkouts/{checkoutNumber}/shipping-address` | `CUSTOMER` | Implemented | Update checkout shipping address before payment confirmation and before checkout policy confirmation |
| `POST` | `/api/checkouts/{checkoutNumber}/policy-confirmation` | `CUSTOMER` | Implemented | Store order policy confirmation |
| `GET` | `/api/orders` | `CUSTOMER` | Implemented | Customer order history |
| `GET` | `/api/orders/{orderId}` | `CUSTOMER` | Implemented | Customer order detail |
| `PATCH` | `/api/orders/{orderId}/shipping-address` | `CUSTOMER` | Implemented | Legacy compatibility route; rejects orders whose checkout policy is confirmed |
| `POST` | `/api/orders/{orderId}/cancel` | `CUSTOMER` | Implemented | Self-service cancel when allowed |

Rules:

- Order creation starts as `PAYMENT_PENDING`.
- `PAYMENT_PENDING` means bank-transfer deposit waiting in the current MVP flow.
- Bank-transfer deposit deadline defaults to 24 hours.
- Checkout creation requires current account terms/privacy agreement and completed required customer info.
- Checkout creation revalidates every cart item against the current product MOQ immediately before snapshot creation.
- An invalid saved quantity leaves the cart unchanged and returns the customer to the cart correction flow.
- DS-8 creates checkouts from cart only; direct-buy checkout is deferred.
- Checkout request includes shipping address fields directly.
- Server calculates all totals and ignores client-submitted totals.
- Checkout creation groups cart items by supplier as the MVP delivery-group boundary.
- Checkout creation pessimistically locks the customer's cart row before reading cart items to prevent duplicate submit from creating two payment groups.
- Checkout creation empties the cart after payment group and orders are created.
- A duplicate checkout submit after the first transaction commits returns `400 BUSINESS_RULE_VIOLATION` with `Checkout was already submitted for this cart. Please check your checkout or cart.`
- Checkout create/read responses include the current `shippingAddress`, server-owned `policyEvidence`, and `policyLinks` for shipping, cancellation/refund, and payment-after-stockout notice.
- Checkout create/read responses include `bankTransferDeposit` with bank name, account number, account holder, depositor name, amount, deadline, and cash receipt notice.
- Policy confirmation accepts only the versions returned in `policyEvidence`. The server validates those versions and stores its own canonical notice text before admin deposit confirmation.
- Customer order history excludes normal `PAYMENT_PENDING`, `EXPIRED`, and failed payment attempts.
- Customer order list and detail are scoped to the authenticated customer.
- Customer order APIs expose stable status codes. Customer-facing display labels are owned by the frontend.
- Customer order detail includes payment group summary, payment summary, shipping address, order items, and fulfillment/shipment/refund summaries.
- Checkout shipping address changes are allowed only while the payment group and its orders are still `PAYMENT_PENDING`.
- Checkout shipping address changes are rejected after checkout policy confirmation because the confirmation text includes shipping address.
- Customer shipping-address changes are rejected after checkout policy confirmation. A required correction is handled through customer support before supplier work starts.
- `address_locked_at` still records the stronger operational lock applied when supplier work starts.

Implemented request bodies:

```json
POST /api/checkouts
{
  "recipientName": "Receiver",
  "recipientPhone": "010-1111-2222",
  "postalCode": "12345",
  "address1": "Base address",
  "address2": "Detail address",
  "depositorName": "Receiver",
  "clientSubmittedTotalAmount": 1
}

PATCH /api/checkouts/{checkoutNumber}/shipping-address
PATCH /api/orders/{orderId}/shipping-address
{
  "recipientName": "Receiver",
  "recipientPhone": "010-1111-2222",
  "postalCode": "12345",
  "address1": "Base address",
  "address2": "Detail address"
}

POST /api/checkouts/{checkoutNumber}/policy-confirmation
{
  "termsVersion": "2026-08-02",
  "privacyVersion": "2026-08-04",
  "orderPolicyVersion": "2026-08-02",
  "cancellationRefundPolicyVersion": "2026-08-02",
  "outOfStockNoticeVersion": "2026-08-02"
}
```

## Payment APIs

Rules:

- Customer payment uses direct bank transfer only.
- Bank-transfer payment records use `PaymentProvider.BANK_TRANSFER`, `PaymentMethod.BANK_TRANSFER`, and `providerPaymentKey = BANK-{checkoutNumber}`.
- Deposit confirmation is an administrator action after the customer has transferred the exact checkout amount.
- Card, easy payment, PG account transfer, virtual account, mobile phone payment, and gift certificate payment are excluded.

## Admin Order And Fulfillment APIs

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/admin/orders` | `ADMIN` | Implemented | Paginated admin queue; supports `status`, `q`, `from`, `to`, `page`, and `size` filters |
| `GET` | `/api/admin/orders/{orderId}` | `ADMIN` | Implemented | Admin order detail |
| `POST` | `/api/admin/orders/{orderId}/confirm-deposit` | `ADMIN` | Implemented | Confirm exact direct bank-transfer deposit evidence and move checkout orders to supplier order pending |
| `POST` | `/api/admin/orders/{orderId}/unpaid-cancel` | `ADMIN` | Implemented | Cancel unpaid bank-transfer checkout |
| `POST` | `/api/admin/orders/{orderId}/deposit-mismatch` | `ADMIN` | Implemented B-102 | Record identified amount-mismatch evidence and the PaymentGroup refund command |
| `POST` | `/api/admin/orders/{orderId}/late-deposit` | `ADMIN` | Implemented B-102 | Process portal expiry or a portal/legacy qualifying unpaid-cancelled receipt; cancelled groups always refund and never resume |
| `POST` | `/api/admin/orders/{orderId}/supplier-work-start` | `ADMIN` | Implemented | Lock address and mark supplier work started |
| `POST` | `/api/admin/orders/{orderId}/supplier-order-completed` | `ADMIN` | Implemented | Mark manual supplier order completed |
| `POST` | `/api/admin/orders/{orderId}/supplier-order/validate` | `ADMIN` | Implemented | Revalidate source item, option, price, and shipping before automated purchase |
| `POST` | `/api/admin/orders/{orderId}/supplier-order/retry` | `ADMIN` | Implemented | Queue a failed, known-safe automated purchase for retry |
| `POST` | `/api/admin/orders/{orderId}/supplier-order/reconcile` | `ADMIN` | Implemented | Reconcile an uncertain purchase against Domeggook orders without blind retry |
| `POST` | `/api/admin/orders/{orderId}/supplier-order/cancel` | `ADMIN` | Implemented | Request supplier purchase cancellation with a required reason |
| `POST` | `/api/admin/orders/{orderId}/out-of-stock` | `ADMIN` | Implemented | Mark supplier out-of-stock and prepare refund flow |
| `POST` | `/api/admin/orders/{orderId}/shipments` | `ADMIN` | Implemented | Enter carrier and tracking number |
| `PATCH` | `/api/admin/orders/{orderId}/shipment-correction` | `ADMIN` | Planned | Manually correct shipment state with reason |
| `POST` | `/api/admin/orders/{orderId}/corrections` | `ADMIN` | Planned | Admin correction action with reason |

Rules:

- Admin cannot write arbitrary order status values.
- Admin actions must map to valid transition table actions.
- Admin order queue defaults to `SUPPLIER_ORDER_PENDING` orders.
- `GET /api/admin/orders?status=PAYMENT_PENDING` returns the bank-transfer deposit waiting queue.
- Admin order search and order-date filters run on the server. Dates use the Asia/Seoul day boundary.
- Admin order pages default to 20 rows, allow at most 100 rows, and return `page`, `size`, `totalElements`, and `totalPages`.
- Admin order summaries include `itemCount` so the list does not depend on detail API data.
- `PAYMENT_PENDING` and `EXPIRED` orders are excluded from the supplier order queue.
- Admin deposit confirmation requires `actualDepositorName`, positive `actualAmount`, past-or-present `depositedAt`, `transactionReference`, reason, confirmed checkout policies, `PAYMENT_PENDING` checkout orders, and currently sellable products/options. `actualAmount` must exactly equal the payment group total; a non-equal amount on this exact-confirm route returns conflict and directs the operator to the idempotent `deposit-mismatch` group-refund action, which preserves the receipt evidence. Portal confirmation also uses the shared locks, lazy-expires its Supplier contract, and requires time-valid VERIFIED before normal approval; the admin UI sends an idempotency key for every payment action.
- Admin deposit confirmation creates a `BANK_TRANSFER` payment row, marks the payment group `APPROVED`, and moves all checkout orders to `SUPPLIER_ORDER_PENDING`.
- Implemented B-102: before the current `PAYMENT_PENDING` precondition, a portal-origin confirmation looks up unique `(paymentGroupId, Idempotency-Key)` and compares a hash containing command type, amount, depositor, deposited time, transaction reference, and reason. Identical replay returns the immutable first result after either success or exception; changed payload returns `409`. After exact money receipt is established, a current Supplier/product/option/compliance/supplier-availability or immutable supplier/mode mismatch must not discard the receipt as a plain `400`. The whole group atomically records one received `BANK_TRANSFER` Payment and PaymentGroup as `PAYMENT_EXCEPTION`, creates one `Refund(status=REQUESTED, reason=SALE_UNAVAILABLE_AT_DEPOSIT)` per delivery-group Order, leaves all final Orders at `REFUND_REQUESTED`, and creates no Fulfillment/PII/supplier work. Non-portal-only PaymentGroups keep the implemented `400` behavior.
- In that normal pre-expiry exception transaction, all portal TRACKED `HELD` reservations move to `RELEASED`, decrement reserved quantity, and set `releasedAt` exactly once. This prevents a terminal payment exception from leaving inventory reserved after the expiry scheduler no longer owns the PaymentGroup.
- B-102 applies the same saleability revalidation to late-deposit success. Implemented B-103 decides the `COREABLE_MANUAL` KEEP fallback from portal/manager availability; a real B-102 saleability failure rolls back reservation consumption/reacquisition and uses the supplier-hidden refund-processing outcome.
- Admin unpaid cancellation requires a reason and moves all checkout orders to `CANCELLED`.
- B-102 ends new memo-only order mutations after admin-web cutover; historical notes remain readable, while an identified positive amount mismatch uses full evidence, `PAYMENT_EXCEPTION`, one actual-amount PaymentGroup Refund and `REFUND_REQUESTED` for every included Order as specified above. An unattributed bank transaction does not mutate a guessed Order.
- B-102 `late-deposit` also accepts an exact receipt for a portal/legacy group whose sole terminal outcome is unpaid cancellation and which has no received Payment, Refund or Fulfillment. It always creates Order-scoped `LATE_DEPOSIT_EXCEPTION` refunds totaling the immutable checkout amount and never routes that `CANCELLED` group back to approval or fulfillment.
- Stale customer/admin order or payment group updates are rejected with `409 CONFLICT` instead of overwriting the latest state.
- Admin order detail exposes internal order/payment/fulfillment statuses plus supplier, product option, customer shipping, and payment summary fields.
- Supplier work start requires a reason and records `supplierOrderStartedAt`, `addressLockedAt`, and `addressLockedByAdminId`.
- Supplier order completion requires `supplierOrderNumber` and reason. `expectedShipDate` and `supplierResponseMemo` are optional evidence fields.
- Deposit-confirmed orders whose items all contain Domeggook source snapshots are queued for automated purchase.
- Automated purchase checks live sale/option state, source price, fixed shipping, and e-money balance before `setOrder`.
- A transport failure after an order request becomes `RECONCILIATION_REQUIRED`; it cannot use the retry endpoint until order-list reconciliation proves no duplicate purchase.
- Admin order detail includes purchase status, expected/actual supplier amount, supplier order number, last error, sync time, and cancellation status.
- Supplier out-of-stock requires a reason and moves the order to `OUT_OF_STOCK`.
- Shipment creation requires `carrier` and `trackingNumber`, creates one shipment for the order, and moves the order to `SHIPPED`.
- MVP allows only one shipment per order; duplicate shipment creation is rejected.
- Reason is required for cancellation, refund, out-of-stock, shipment correction, and admin correction.
- `PREPARING_SHIPMENT` is not an MVP order status.
- Implemented B-103/B-104 channel guards preserve the legacy endpoints without allowing them to bypass portal contracts: `supplier-work-start`, `supplier-order-completed`, legacy `POST /api/admin/orders/{orderId}/shipments`, legacy shipment-correction, scheduler/admin tracking-sync, and legacy manual-correction reject `Fulfillment.channel=SUPPLIER_PORTAL`. B-103 provides portal takeover; B-104 adds the portal shipment actions. The existing Coreable `out-of-stock` service remains available for legacy/manual use; B-105's dedicated shortage-report approval locks the REPORTED row and delegates to that same service so it cannot be bypassed or invoked twice through the supplier report.

## Shipment Tracking APIs

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/orders/{orderId}/shipment` | Authenticated user | Not exposed | Singular compatibility remains embedded in the implemented customer order-detail response; canonical standalone reads use plural `/shipments` |
| `POST` | `/api/internal/shipments/tracking-sync` | Internal scheduler token | Implemented | Sync tracking status batch by carrier/tracking number |
| `POST` | `/api/admin/shipments/{shipmentId}/tracking-sync` | `ADMIN` | Implemented | Manual retry tracking sync |
| `POST` | `/api/admin/shipments/{shipmentId}/manual-correction` | `ADMIN` | Implemented | Manually correct shipment status to delivered |

Rules:

- Customer order detail includes shipment summary when an admin-entered shipment exists.
- Internal scheduler calls must include `X-Internal-Sync-Token`; the token is configured only on the API server and scheduler.
- Shipment creation requires carrier and tracking number.
- Current legacy Coreable/Domeggook flow supports one shipment per order. Implemented B-104 portal orders use the plural contract above.
- Tracking-sync and legacy manual-correction apply only to non-portal channels. A portal Shipment, including `TRACKING_REGISTERED`, is changed only through the implemented versioned/idempotent portal admin actions with evidence guards.
- Automatic tracking moves shipment forward only.
- `DELIVERED` tracking status moves shipment and order to `DELIVERED`; other tracking statuses keep the current state.
- Sync failure stores `trackingSyncFailureReason` and keeps current shipment/order state.
- Tracking failure must not block order, payment, or refund operations.
- Manual correction supports `DELIVERED` only, requires reason, records admin action history, and records order status history when the order state changes.

## Refund And Claim APIs

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `POST` | `/api/orders/{orderId}/cancel` | `CUSTOMER` | Implemented | Self-service cancel when eligible |
| `POST` | `/api/orders/{orderId}/claims` | `CUSTOMER` | Implemented | Create cancellation, return, or exchange claim. Supports JSON for simple claims and multipart `evidenceFiles` for claims with photo evidence. |
| `GET` | `/api/orders/{orderId}/claims` | `CUSTOMER` | Implemented | Customer claim list for an order |
| `GET` | `/api/orders/{orderId}/claims/{claimId}` | `CUSTOMER` | Implemented | Customer claim detail |
| `POST` | `/api/orders/{orderId}/claims/{claimId}/evidence` | `CUSTOMER` | Implemented | Add evidence image files to an existing customer claim |
| `GET` | `/api/admin/claims` | `ADMIN` | Implemented | Admin claim queue |
| `POST` | `/api/admin/claims/{claimId}/approve` | `ADMIN` | Implemented | Approve claim |
| `POST` | `/api/admin/claims/{claimId}/reject` | `ADMIN` | Implemented | Reject claim |
| `POST` | `/api/admin/claims/{claimId}/request-evidence` | `ADMIN` | Planned | Request evidence |
| `POST` | `/api/admin/claims/{claimId}/return-received` | `ADMIN` | Implemented | Mark return received |
| `POST` | `/api/admin/claims/{claimId}/return-refund` | `ADMIN` | Implemented | Start return refund after return received |
| `POST` | `/api/admin/claims/{claimId}/exchange-shipped` | `ADMIN` | Planned | Mark exchange shipment |
| `GET` | `/api/admin/refunds` | `ADMIN` | Implemented | Refund queue |
| `POST` | `/api/admin/refunds/{refundId}/approve` | `ADMIN` | Implemented | Approve refund execution |
| `POST` | `/api/admin/refunds/{refundId}/manual-review` | `ADMIN` | Implemented | Mark manual review result |
| `POST` | `/api/admin/refunds/{refundId}/manual-complete` | `ADMIN` | Implemented | Complete actual manual bank-transfer refund with transfer evidence |

Rules:

- Customer self-service cancel is allowed only while `SUPPLIER_ORDER_PENDING` and supplier work has not started.
- Self-service cancellation creates an approved cancellation claim, refund record, and moves the order to `REFUND_REQUESTED`.
- After supplier work starts, cancellation becomes a `CANCEL` claim that admin can approve or reject.
- After delivery, customers can submit `RETURN` or `EXCHANGE` claims.
- Simple change-of-mind return/exchange claims require delivery within 7 days.
- Seller-fault return/exchange claims require delivery within 90 days in the current implementation. The policy still requires 30 days from discovery, but discovery-date input remains planned.
- Seller-fault claim reasons (`DEFECT`, `WRONG_DELIVERY`, `DIFFERENT_FROM_PRODUCT_INFO`, `DELIVERY_ISSUE`) require at least one image evidence file at customer claim creation.
- Evidence upload accepts `jpg/jpeg`, `png`, and `webp` images using the shared upload extension and magic-byte validation, and stores metadata in `claim_evidences`.
- Return approval moves the claim to `RETURN_WAITING`; exchange approval keeps the claim approved until exchange shipment handling is implemented.
- `return-received` requires a `RETURN_WAITING` return claim and records return received memo/time.
- Manual bank-transfer refund completion requires `bankName`, `accountNumber`, `accountHolder`, past-or-present `transferredAt`, `transactionReference`, and reason. Account evidence is returned only from the selected admin order detail; it is excluded from the refund queue, customer APIs, notifications, and action histories.
- `return-refund` requires a `RETURN_RECEIVED` return claim, creates a `RETURN_REQUESTED` refund, links it to the claim, moves the order to `REFUND_REQUESTED`, and moves the claim to `REFUND_PROCESSING`.
- Bank-transfer refund completion moves the linked return claim to `COMPLETED`.
- Refund execution requires admin approval before manual bank-transfer refund completion.
- Manual review can approve a normal refund again or reject it with reason. B-102 received-payment exception Refunds are the exception below and cannot be rejected or repriced.
- Bank-transfer refund completion requires actual manual refund completion by an admin.
- B-102 `PAYMENT_GROUP` amount-mismatch Refund responses add `paymentGroupId` and `appliedOrderIds`; their legacy scalar `orderId`/`orderNumber` fields are null. List/detail rendering targets the checkout number and actual refund amount rather than selecting an arbitrary anchor Order.
- `PAYMENT_AMOUNT_MISMATCH` Refund approval cannot reject, change amount or resume an Order. Its manual-complete action locks the PaymentGroup, received Payment, all included Orders and the Refund; exact actual-amount completion stores transfer evidence once and changes all of them to `REFUNDED` atomically.
- `LATE_DEPOSIT_EXCEPTION` and `SALE_UNAVAILABLE_AT_DEPOSIT` received-payment Refunds likewise cannot be rejected, repriced or used to resume an Order. Each manual completion locks the PaymentGroup, received Payment, target Order and Refund, completes that immutable Order amount, and atomically recomputes Payment/PaymentGroup as `PARTIALLY_REFUNDED` or `REFUNDED`.
- The PaymentGroup refund transition accepts `PAYMENT_EXCEPTION` only for a locked approved B-102 received-payment exception Refund whose immutable positive amount is within the outstanding refundable balance. Other payment exceptions remain non-refundable and return `409` without mutation.
- Creation and completion reject `409 REFUND_PAYMENT_GROUP_MISMATCH` before mutation unless the Refund, linked received Payment, and any linked delivery-group Order all belong to the same locked PaymentGroup. V41 composite foreign keys are the final database guard against cross-group links.
- Manual-complete requests for every B-102 received-payment exception require `Idempotency-Key`. `MANUAL_REFUND_COMPLETED` is written to the same PaymentEvent command stream under the payment-group unique key; its server-keyed HMAC covers Refund id, admin actor, exact transfer amount and transfer evidence without copying account data into the event. The immutable result covers the target Refund/Order plus Payment/PaymentGroup aggregate, or all applied Orders for `PAYMENT_AMOUNT_MISMATCH`, and omits account/transfer evidence. An order-scoped event retains its `orderId`; a group-scope event keeps it null. Identical key/hash returns the original result before state validation; changed Refund/body/action returns `409`. If an operator loses the response after a real bank transfer, the UI instructs them to retry the same key or reconcile the recorded transfer, never send another transfer blindly.
- `GET /api/orders/{orderId}` includes `claims` plus the latest `claim` summary for compatibility. `GET /api/admin/orders/{orderId}` includes the latest claim summary and claim evidence metadata.
- Refund records are created for approved customer cancellation and supplier out-of-stock.
- Manual bank-transfer refund completion moves the delivery-group order to `REFUNDED`, the payment to `REFUNDED` or `PARTIALLY_REFUNDED`, and the payment group to `REFUNDED` or `PARTIALLY_REFUNDED`.
- Delivery-group order level partial refund is supported.
- Product, option, and quantity-level partial refund inside one delivery-group order is excluded.

## Policy And Legal APIs

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/policies` | Public | Implemented | List customer-facing policy pages |
| `GET` | `/api/policies/{slug}` | Public | Implemented | Customer-facing policy page by slug |
| `GET` | `/api/policies/{type}/current` | Public | Implemented | Active managed policy document by type |
| `GET` | `/api/policies/{type}/versions/{version}` | Public | Implemented | Specific policy version |
| `GET` | `/api/business-profile` | Public | Implemented | Active business disclosure |
| `GET` | `/api/privacy-processing-items` | Public | Implemented | Active privacy processing table |
| `POST` | `/api/customer-inquiries` | Public | Implemented | Create customer support inquiry |
| `POST` | `/api/customer-inquiries/{inquiryId}/lookup` | Public lookup token | Implemented | Read customer-safe inquiry status and latest answer |
| `GET` | `/api/admin/policies` | `ADMIN` | Implemented | Admin policy document list |
| `POST` | `/api/admin/policies` | `ADMIN` | Implemented | Create policy draft |
| `PATCH` | `/api/admin/policies/{policyId}` | `ADMIN` | Implemented | Update policy draft |
| `POST` | `/api/admin/policies/{policyId}/activate` | `ADMIN` | Implemented | Activate policy version |
| `PATCH` | `/api/admin/business-profile` | `ADMIN` | Planned | Update business disclosure |
| `PUT` | `/api/admin/privacy-processing-items` | `ADMIN` | Planned | Replace privacy processing table |
| `GET` | `/api/admin/customer-inquiries?status=...` | `ADMIN` | Implemented | List and filter customer support inquiries |
| `GET` | `/api/admin/customer-inquiries/{inquiryId}` | `ADMIN` | Implemented | Customer support inquiry detail |
| `PATCH` | `/api/admin/customer-inquiries/{inquiryId}/status` | `ADMIN` | Implemented | Change inquiry processing status and memo |
| `POST` | `/api/admin/customer-inquiries/{inquiryId}/answer` | `ADMIN` | Implemented | Save latest answer and queue customer email |
| `GET` | `/api/admin/referrals` | `ADMIN` | Implemented | List registered referral relationships |

Rules:

- Implemented policy slugs are `shipping`, `cancellation-refund`, and `stock-risk`.
- Implemented policy pages are backed by active `policy_documents` rows.
- Business profile and privacy processing item APIs are backed by DB tables; admin management remains planned.
- Managed policy documents support draft creation, draft update, activation, current public lookup, and version public lookup in DS-41.
- Public policy document types include `SHIPPING_POLICY`, `CANCELLATION_REFUND_POLICY`, and `OUT_OF_STOCK_NOTICE`.
- Product detail and checkout responses include links to the implemented policy page endpoints; link labels use active policy document titles when configured.
- Policy pages are available from customer menu and footer.
- Policy documents have version and effective date.
- Checkout stores policy versions per payment group.
- Customer inquiry creation requires explicit privacy consent and stores the disclosed policy version, consent time, and three-year retention expiry.
- Inquiry status is `RECEIVED`, `IN_PROGRESS`, `ANSWERED`, or `CLOSED`; a closed inquiry must be reopened before answering.
- Public lookup requires an HMAC token and never exposes customer contact, consent evidence, admin memo, or handler id.
- The same normalized email can create at most three inquiries in ten minutes. Further requests return `429 RATE_LIMITED`.
- Answer email delivery is logged separately and does not roll back the stored answer when delivery fails.
- Actual legal wording requires launch review.

## Notification And Audit APIs

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/admin/orders/{orderId}/status-history` | `ADMIN` | Implemented | Order status transition history |
| `GET` | `/api/admin/actions?orderId={orderId}` | `ADMIN` | Implemented | Admin order action history, optionally filtered to one order |
| `GET` | `/api/admin/notifications?status=FAILED` | `ADMIN` | Implemented | Notification log search, optionally filtered by status |
| `POST` | `/api/admin/notifications/{notificationId}/retry` | `ADMIN` | Implemented; invite guard B-100, operational guard B-103 | Retry an eligible failed/skipped legacy notification; supplier rows use stricter rules below |
| `POST` | `/api/admin/orders/{orderId}/delay-notice` | `ADMIN` | Implemented | Send manual supplier delay notice before shipment |

Rules:

- Transactional notifications are separate from marketing consent.
- Payment pending, payment completed, out-of-stock, shipment started, delivered, delay notice, claim changed, and refund completed should create notification logs.
- B-011 sends transactional notifications through SMS first. Logs start as `PENDING` and become `SENT`, `FAILED`, or `SKIPPED`.
- `sms.sens.enabled=false` is the default safe fallback and records logs as `SKIPPED`.
- DS-44 exposes order status history and admin order action history read APIs.
- B-100 preserves this route for legacy notifications and implements the fail-closed invite guard: any `supplierInviteId` row rejects generic retry because its raw token/link is not stored, so recovery uses a new-key invite reissue. Implemented B-103 permits an operational supplier row only from `FAILED`, before `createdAt + 7 days`, with non-null recipient and after the current ACTIVE portal/manager/time-valid-contract/verified-email/recipient recheck. `SKIPPED`, `SENT`, recipient-null, expired-retry-window and lifecycle/contract-mismatched supplier rows remain terminal.

## DS-6 Catalog Request And Response Expectations

Allowed product statuses:

- `ACTIVE`
- `SOLD_OUT`
- `HIDDEN`
- `STOPPED`

Allowed product option statuses:

- `ACTIVE`
- `SOLD_OUT`
- `STOPPED`

Customer visibility:

- `ACTIVE` products can appear in customer product lists.
- `HIDDEN` products are hidden from customer product lists.
- `STOPPED` products are not purchasable.
- `SOLD_OUT` products may be displayed as sold out but are not purchasable.
- Options are purchasable only when the complete customer saleability guard above passes, including supplier availability and sufficient derived TRACKED inventory for the purchase quantity. Public `purchasable` evaluates that guard at `minimumOrderQuantity` and exposes no raw inventory state.

### Supplier Create Request

```json
{
  "name": "Supplier name",
  "contactName": "Manager",
  "phone": "010-0000-0000",
  "email": "supplier@example.com",
  "memo": "Internal memo"
}
```

### Product Create Request

```json
{
  "supplierId": "00000000-0000-0000-0000-000000000000",
  "name": "Product name",
  "summary": "Short customer-facing summary",
  "sourcePrice": 31200,
  "basePrice": 39000,
  "minimumOrderQuantity": 6,
  "orderQuantityStep": 6,
  "categoryCode": "PPE_SAFETY_HELMET",
  "status": "ACTIVE"
}
```

### Product Option Create Request

```json
{
  "name": "Black / Large",
  "additionalPrice": 0,
  "status": "ACTIVE"
}
```

### Product Image Metadata Request

```json
{
  "images": [
    {
      "type": "THUMBNAIL",
      "imageUrl": "https://example.com/thumbnail.jpg",
      "sortOrder": 0,
      "altText": "Product thumbnail"
    }
  ]
}
```

Validation:

- One `THUMBNAIL` image per product.
- Up to ten `GALLERY` images per product.
- Detail block image count follows the detail image policy limit of fifty.
- Allowed image extensions: `jpg`, `jpeg`, `png`, `webp`.
- Image size limit: 10MB per image.
- Upload validates both filename extension and actual image file signature.

### Product Detail Blocks Request

```json
{
  "detailBlocks": [
    {
      "type": "HTML",
      "htmlContent": "<p>Sanitized detail content</p>",
      "sortOrder": 1
    }
  ]
}
```

Validation:

- `HTML` blocks are sanitized by a server-side safelist before storage.
- `IMAGE` blocks store an image URL or object key.
- Shipping, cancellation/refund, AS, return/exchange, and out-of-stock notices must not exist only inside arbitrary detail HTML/images.

### Product Notice Request

```json
{
  "productInfoNotice": "Product information notice",
  "shippingInfo": "Shipping information",
  "asInfo": "AS information",
  "returnExchangeInfo": "Return and exchange information"
}
```

Rule:

- The active product notice version or equivalent snapshot source must be available to order creation.
- Public product detail responses omit `sourcePrice`; admin product detail responses include it.

### Pricing Policy Request

```json
{
  "name": "기본 가격 정책",
  "commissionRate": 5.0,
  "taxBufferRate": 10.0,
  "overheadRate": 5.0,
  "safetyMarginRate": 5.0,
  "roundingUnit": 100
}
```

Rule:

- Default customer sale price is supplier cost plus the total markup rate, rounded to the nearest `roundingUnit`.
- Admin product option create/update/detail may include source metadata fields for import traceability: `sourceOptionCode`, `sourceAdditionalPrice`, `sourceStockQuantity`, and `sortOrder`.
- Public product detail omits source option metadata. Customers see only option `id`, `name`, customer-facing `additionalPrice`, and `status`.

### Product Detail Response Shape

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "name": "Product name",
  "summary": "Short customer-facing summary",
  "basePrice": 39000,
  "minimumOrderQuantity": 6,
  "orderQuantityStep": 6,
  "categoryCode": "PPE_SAFETY_HELMET",
  "status": "ACTIVE",
  "detailVersion": 3,
  "productNoticeVersion": 2,
  "images": [],
  "options": [
    {
      "id": "00000000-0000-0000-0000-000000000000",
      "name": "Option name",
      "additionalPrice": 0,
      "status": "ACTIVE"
    }
  ],
  "detailBlocks": [],
  "productNotice": {
    "productInfoNotice": "Product information notice",
    "shippingInfo": "Shipping information",
    "asInfo": "AS information",
    "returnExchangeInfo": "Return and exchange information"
  },
  "policyLinks": [
    {
      "label": "배송 정책",
      "href": "/api/policies/shipping",
      "policyType": "SHIPPING_POLICY"
    },
    {
      "label": "취소/환불 정책",
      "href": "/api/policies/cancellation-refund",
      "policyType": "CANCELLATION_REFUND_POLICY"
    },
    {
      "label": "결제 후 품절 안내",
      "href": "/api/policies/stock-risk",
      "policyType": "OUT_OF_STOCK_NOTICE"
    }
  ]
}
```

DS-6 should keep request/response DTOs separate from JPA entities.

## Open API Notes

- Pagination format is not defined.
- Binary image upload is implemented by DS-42 with local storage. External object storage can replace it later without changing image metadata rules.
- OAuth token/session format is implemented as a stateless JWT access token stored in an HttpOnly cookie.
- Public product APIs are public, but checkout/cart/order APIs require authentication.
