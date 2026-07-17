# Decision Log

## 2026-07-14: Product Activation Requires Explicit Sale Readiness

Decision:

Allow `ACTIVE` only when a product has a positive sale price, canonical thumbnail, active option, active product notice, and compliance review state `NOT_REQUIRED` or `VERIFIED`. New products start non-active. Do not infer certification requirements from category alone.

Context:

Collected products could be activated before required sales information and certification review were complete. The existing model had no machine-readable record that an administrator had determined whether certification was required or verified.

Consequences:

- `products.compliance_status` stores `PENDING`, `NOT_REQUIRED`, `VERIFIED`, or `REJECTED`; changes reuse product change history.
- Existing products start `PENDING`, and existing `ACTIVE` products move to `HIDDEN` because no prior compliance evidence exists.
- Activation and mutations that could invalidate an active product use the same backend readiness validation.
- B-064 may display richer review reasons, but it must reuse this backend rule rather than define a second activation policy.

## 2026-07-13: Customer Inquiry Consent, Retention, And Reply Channel

Decision:

Public customer inquiries require explicit consent for the disclosed inquiry-processing items. Store consent version/time and delete inquiry records three years after receipt. Admins process one latest answer through `RECEIVED`, `IN_PROGRESS`, `ANSWERED`, and `CLOSED`; customers receive the answer through AWS SES email and an HMAC-token-protected lookup page.

Context:

The existing public form stored contact and message data without consent evidence, while the admin page only listed raw inquiries. This could not complete customer support operations or prove what an anonymous customer accepted.

Consequences:

- The inquiry disclosure version is `support-inquiry-privacy-2026-07-13`; existing rows keep null consent evidence.
- The same normalized email is limited to three submissions per ten minutes. CAPTCHA remains deferred until actual abuse requires it.
- Lookup tokens are derived with `APP_INQUIRY_LOOKUP_SECRET`, placed in URL fragments, and never stored in notification payloads.
- Answer storage commits before email dispatch. Failed or disabled email remains retryable while the customer lookup page still shows the answer.
- B-061 remains in progress until SES domain/DKIM verification, production access, and a real delivery test are complete.

## 2026-07-13: Bank Transfer Legal Disclosure Baseline

Decision:

Public order and refund notices must describe the current bank-transfer flow first and distinguish deferred PG behavior. Bank-transfer orders use a 24-hour deposit deadline, and a refund is complete only after the actual transfer is made and an admin records completion. Cash receipts are handled manually through Hometax by the representative/admin; customer requests are accepted through support, phone, or email, and mandatory-issuance transactions are issued even without a request. Do not accept real sales orders until a purchase-safety service and customer selection path are available.

Context:

The public cancellation/refund page still described only PG cancellation even though direct bank transfer is the current checkout path. Official KFTC guidance also requires a purchase-safety option for applicable non-credit-card prepayment transactions, and NTS guidance requires automatic cash-receipt issuance for qualifying mandatory-issuance transactions of KRW 100,000 or more.

Consequences:

- Public terms and refund text now describe bank-transfer deposit confirmation, manual refund completion, and the deferred Toss path separately.
- The default checkout cash-receipt notice covers both customer requests and mandatory-issuance transactions.
- B-030 remains in progress until the purchase-safety provider, Hometax account/industry applicability, actual privacy processors, policy effective version, and initial product certifications are confirmed.

## 2026-07-13: Launch Documentation Baseline

Decision:

Treat direct bank transfer as the current customer payment path and Toss Payments as a deferred integration in every active product, policy, architecture, and operations document. Treat the mail-order sales exemption, customer center values, and AWS hosting provider as confirmed public values. Keep purchase-safety service, cash-receipt operations, inquiry consent evidence, product activation checks, and monitoring as launch blockers.

Context:

Implementation and recent policy decisions had moved to bank transfer and a low-cost single-EC2 deployment, while several top-level documents still described Toss Payments and managed PostgreSQL as the current baseline. The legal checklist also left already-configured public values marked unresolved.

Consequences:

- The 2026-07-03 bank-transfer decision supersedes the 2026-06-27 Toss-first decision for the current default checkout path; the older entry remains as historical context for B-001.
- `docs/BACKLOG.md` is ordered by launch risk, and completed work is stored only in `docs/BACKLOG_DONE.md`.
- B-030 owns unresolved public legal/payment wording, B-056 owns product activation guards, B-061 owns inquiry operations and consent evidence, B-059 owns security closure, and B-062 owns minimum monitoring.
- The paused EC2 environment is a production-style deployment baseline, not evidence that the service is ready to accept orders.

## 2026-07-04: Local Seed Dev Login Guard

Decision:

Expose `/api/dev/login` only for local/dev development sessions, and guard it with both `@Profile({"local","dev"})` and `app.dev-login.enabled=true`. The endpoint may issue a normal access-token cookie only for existing seed users; it must not create users or change production authentication policy.

Context:

Manual QA and Playwright smoke checks repeatedly need the local seed customer and admin accounts. The previous workflow required generating JWTs by hand from database user ids and injecting browser cookies, which was slow and easy to do incorrectly.

Consequences:

- `application-local.yml` enables `app.dev-login.enabled`; common, test, and prod configuration files do not define it.
- The endpoint reuses `JwtAccessTokenService` and the same `ACCESS_TOKEN` HttpOnly, `SameSite=Lax`, path `/` cookie attributes as OAuth login.
- `DevLoginProdProfileIntegrationTest` intentionally sets `app.dev-login.enabled=true` under the prod profile and expects `/api/dev/login` to remain unmapped with 404.
- `DevLoginApiIntegrationTest` verifies local seed customer/admin cookie issuance and authenticated access to `/api/me` and `/api/admin/me`.
- Playwright local auth smoke may call the dev login endpoint when explicit `E2E_CUSTOMER_COOKIE` or `E2E_ADMIN_COOKIE` values are not supplied.

## 2026-07-03: Customer Claim Evidence MVP Scope

Decision:

Store customer claim evidence as separate `ClaimEvidence` rows connected to `Claim`, and require at least one image evidence file when the claim reason is seller-fault: defect, wrong delivery, different from product information, or delivery issue. Keep discovery-date input and exchange shipment handling as policy/planned work, not B-015 implementation work.

Context:

The refund/claim policy already requires photo evidence for seller-fault return/exchange claims, but the previous implementation only stored a latest claim summary and had no evidence persistence. The customer also needs to see submitted claim status/evidence, while admins need evidence inside the order operation screen before approving or rejecting a claim.

Consequences:

- Customer claim creation supports multipart image evidence and rejects seller-fault claims without evidence.
- Evidence files reuse the same extension and magic-byte validation as product images and are served through the existing upload URL path.
- Customer order detail now exposes both `claims` and a latest `claim` field for compatibility.
- Admin order detail exposes latest claim evidence for review.
- The 30-day discovery-date policy remains documented but is not yet enforced because no discovery-date input exists.
- Exchange shipment completion remains deferred; exchange claim approval is still review-only until that flow is explicitly built.

## 2026-07-03: Checkout And Order State Concurrency Guard

Decision:

Use a pessimistic write lock on the customer's cart row during checkout creation, and use optimistic locking on `orders` and `payment_groups` for order/payment state changes. Do not auto-retry optimistic lock conflicts; return `409 CONFLICT` and require the user or admin to refresh and retry the action.

Context:

The MVP payment flow is direct bank transfer. If a checkout submit is duplicated, two payment-pending order sets can be created for the same cart and one becomes a ghost order. Separately, order and payment state transitions are read-check-write operations, so concurrent customer cancellation and admin deposit/supplier actions can otherwise overwrite each other.

Consequences:

- Checkout creation locks the cart row first, then reads cart items. A duplicate request waits for the first transaction and then sees an empty cart.
- Duplicate checkout submission returns a clear business-rule error instead of creating a second payment group.
- `orders.version` and `payment_groups.version` reject stale commits.
- A stale order/payment action returns the standard API error body with HTTP `409` and code `CONFLICT`.
- Automatic retry is intentionally excluded because these conflicts represent state decisions that should be rechecked by a customer or operator.

## 2026-07-03: MVP Payment Flow Changes To Direct Bank Transfer

Decision:

Use direct customer bank transfer and manual admin deposit confirmation as the current MVP payment flow. Keep existing Toss Payments code for a deferred PG reintroduction path, but remove Toss from the customer primary checkout path.

Context:

Toss Payments live review and PG activation add operational and contract work before the shop can accept initial test orders. The operator decided to start with direct bank transfer, where the customer deposits the checkout amount into the shop account and an admin confirms the deposit before supplier ordering.

Consequences:

- `PAYMENT_PENDING` is reused as deposit waiting state. Admin deposit confirmation moves orders to `SUPPLIER_ORDER_PENDING`.
- Direct bank transfer payment records use `PaymentProvider.BANK_TRANSFER`, `PaymentMethod.BANK_TRANSFER`, and a server-generated `providerPaymentKey` such as `BANK-{checkoutNumber}` so the existing unique payment key invariant remains intact.
- The checkout deposit deadline is 24 hours by default, not the old 30 minute Toss payment window.
- Admin actions for deposit confirmation, unpaid cancellation, deposit mismatch memo, and manual refund completion must record actor, time, from/to state where applicable, and reason in order status/action history.
- Purchase safety service for cash payment is not decided by code. The launch checklist must keep bank escrow, consumer damage compensation insurance, or PG/virtual-account reintroduction as unresolved operating choices before real sales.
- Cash receipt issuance is required for cash-like payment when requested. The first operating method is manual issuance through Hometax; automatic API integration is deferred.

## 2026-07-03: Delivered Return Refund MVP Scope

Decision:

Complete delivered return refunds through admin-managed return received and manual bank-transfer refund actions. Do not implement automatic return-shipping-fee deduction in B-044.

Context:

The MVP payment flow is bank transfer. Delivered return claims need a complete operational path from customer request to admin return receipt, refund request, and actual refund completion, but the shipping-cost deduction policy still needs more operational detail.

Consequences:

- A `RETURN_RECEIVED` return claim is required before a delivered order can enter `REFUND_REQUESTED` for a return refund.
- The created refund uses `RefundReason.RETURN_REQUESTED` and is linked back to the claim.
- Manual bank-transfer refund completion moves the order to `REFUNDED`, refund to `COMPLETED`, and linked claim to `COMPLETED`.
- Simple change-of-mind return shipping cost remains customer-burden by policy, but automatic refund deduction is deferred; admins must handle any deduction/customer notice manually for now.

## 2026-06-27: Business Model

Decision:

Build a single-operator dropshipping shop.

Context:

The operator sells products directly, but the supplier handles actual shipment.

Consequences:

- No seller marketplace in MVP.
- Supplier and fulfillment are core domains.
- Admin workflows are essential.

## 2026-06-27: MVP Scope Lock

Decision:

Lock the MVP as a single-operator supplier-fulfillment commerce product with authenticated customer checkout, Toss Payments payment, manual supplier ordering, delivery-group order handling, admin claim/refund processing, and customer-facing policy pages.

Context:

The project has enough product, policy, and state-model decisions to move from planning into backend implementation. Remaining launch checks are legal, business registration, PG production, privacy disclosure, and delivery tracking readiness items rather than blockers for backend scaffolding.

Consequences:

- DS-4 can start after DS-1 and DS-2 are complete.
- Customer scope, admin scope, non-goals, and launch-blocking checks are documented in `docs/product-brief.md`.
- Implementation should not add excluded MVP features unless a new decision updates the scope.
- Launch readiness still requires final policy text, business disclosure values, Toss Payments production readiness, privacy disclosure confirmation, and delivery tracking integration confirmation.

## 2026-06-27: Inventory Model

Decision:

Do not manage real stock quantity in MVP. Use product and option sales status instead. Product status and product option status are separate.

Context:

The site assumes products are available and discovers supplier stock issues after payment/order processing.

Consequences:

- Product status must support active, sold out, hidden, and stopped.
- Product option status must support active, sold out, and stopped.
- A product option can be sold out while the product remains active.
- Customer purchase is allowed only when both product and option are active.
- Supplier out-of-stock flow must be designed.
- Customer-facing policy must explain possible post-order stock issues.

## 2026-06-27: Product Detail Content

Decision:

Use `IMAGE` and `HTML` blocks for product detail content in MVP. Operational policy notices must be managed separately from product detail content.

Context:

Dropshipping products often come with supplier-provided detail images, but some content such as size tables or additional explanations may need HTML. At the same time, shipping, exchange, refund, and post-order out-of-stock notices should not exist only inside images or arbitrary HTML.

Consequences:

- Product detail content needs ordered blocks.
- HTML blocks are admin-only and must be sanitized.
- Detail images can be uploaded and ordered.
- Policy notices should be managed as structured text or reusable policy sections.
- Product detail content and customer-facing policy notices are separate concerns.

## 2026-06-27: Product Image Limits

Decision:

Use fixed MVP image limits: one thumbnail image, up to ten gallery images, up to fifty detail block images, max 10MB per image, and allowed extensions `jpg`, `jpeg`, `png`, and `webp`. Upload validation checks both filename extension and actual image file signature.

Context:

Product detail pages may use many supplier-provided images. A strict low detail-image count would make operations difficult, but file size and extension limits are still needed for performance and abuse control.

Consequences:

- Product image upload requires count validation.
- Image upload requires extension and file size validation.
- Thumbnail, gallery, and detail images are separate concepts.
- Uploaded image binaries should live in object storage, while the database stores URLs or storage keys.

## 2026-06-27: Price Change After Payment

Decision:

Keep the paid order price fixed at the price captured when the order was created and paid. Product price changes apply only to new orders created after the change.

Context:

Supplier prices can change after a customer pays. Changing an already-paid customer order would break customer trust and make payment/order reconciliation unsafe.

Consequences:

- Order items must store product name, option name, unit price, quantity, and line amount snapshots.
- Product price updates must not mutate existing order item prices.
- Customers are not charged extra if supplier price increases after payment.
- If the supplier cannot fulfill at the paid price, the operational fallback is cancellation/refund, not additional billing.

## 2026-06-27: Order Creation Before Payment

Decision:

Create an order before requesting PG payment. The initial order status is `PAYMENT_PENDING`.

Context:

Payment needs an internal payment group anchor so the server can calculate the amount, pass a stable identifier to the PG flow, and verify the PG-approved amount against the server-side payment group amount. The payment group can contain one or more delivery-group orders.

Consequences:

- Checkout creates a `PAYMENT_PENDING` order before redirecting or invoking PG payment.
- `PAYMENT_PENDING` orders are not confirmed orders.
- `PAYMENT_PENDING` orders do not appear in the admin supplier order queue.
- Server-side payment verification is required before the order moves to `SUPPLIER_ORDER_PENDING`.
- The payment and order records must be linked by order id or order number.

## 2026-06-27: Payment Pending Expiration

Decision:

Expire `PAYMENT_PENDING` orders 30 minutes after creation.

Context:

Payment-pending orders should not remain valid forever. They are not confirmed orders, but they reserve a server-side checkout record for PG payment verification. A 30-minute window gives customers enough time to complete payment while keeping abandoned checkout data bounded.

Consequences:

- Orders need an `expiresAt` field or equivalent expiration calculation.
- Expired payment-pending orders should transition to `EXPIRED`.
- Expired orders are not supplier-order candidates.
- Payment verification arriving after expiration must not confirm the order.
- Customers must create a new order after expiration.

## 2026-06-27: Shipping Address Change Window

Decision:

Allow customers to directly change the shipping address only until the order is in `SUPPLIER_ORDER_PENDING`. After `SUPPLIER_ORDERED`, shipping address changes require customer support/admin manual handling.

Context:

Before supplier ordering, the operator has not sent the fulfillment request to the supplier. After supplier ordering, the supplier may already have received or started processing the shipping information, so customer-side edits can desynchronize the shop order and supplier shipment.

Consequences:

- `PAYMENT_PENDING` address edits are treated as checkout edits before payment.
- `SUPPLIER_ORDER_PENDING` orders can expose customer self-service address editing.
- `SUPPLIER_ORDERED` and later states must reject customer direct address changes.
- Admin/customer support may still handle exceptional address changes manually.

## 2026-06-27: Customer Order Status Display

Decision:

Do not expose internal order statuses directly to customers. Map internal statuses to customer-facing display statuses.

Context:

Internal statuses such as `SUPPLIER_ORDER_PENDING` and `SUPPLIER_ORDERED` are operational states for payment verification and supplier fulfillment. They are useful for the admin system but too implementation-specific for customer order tracking.

Consequences:

- Customer order list/detail APIs need a customer display status.
- Admin APIs can expose internal status.
- Status mapping must be maintained as part of order policy.
- Internal state changes should not automatically leak implementation terminology into the customer UI.

## 2026-06-27: Monorepo For MVP

Decision:

Use a single GitHub repository as a monorepo for the MVP.

Context:

The project will be developed by one developer. During MVP development, frontend, backend, documentation, and infrastructure changes are tightly coupled and should be reviewed in one issue/PR flow.

Consequences:

- Frontend code will live under `apps/web`.
- Backend code will live under `apps/api`.
- Infrastructure files will live under `infra`.
- Documentation remains under `docs`.
- One Linear issue can map to one PR even if both frontend and backend are touched.
- Repository split can be revisited later if team ownership, release cadence, CI cost, or security boundaries require it.

## 2026-06-27: Payment Gateway Provider

Decision:

Use Toss Payments as the MVP payment gateway provider.

Context:

The product targets a Korean commerce flow and needs a domestic PG that supports card, easy payment, account transfer, and optional virtual account flows. Toss Payments is the selected provider for the first implementation path.

Consequences:

- Payment integration issues should target Toss Payments APIs and SDKs.
- Spring Boot must verify payment approvals server-side with Toss Payments.
- Payment method policy enables card, easy payment, and account transfer for MVP.
- Virtual account/bank-transfer-like flows remain deferred because they require separate async deposit state handling.
- PortOne is not used for MVP because the first implementation benefits from a narrower direct PG integration.
- Live operation requires Toss Payments merchant readiness, live keys, enabled payment methods, cancel/partial-cancel readiness, and customer-facing policy pages.

## 2026-06-27: MVP Payment Methods

Decision:

Enable Toss Payments card, easy payment, and account transfer for MVP. Exclude virtual account/bank-transfer-like async payment, mobile phone payment, and gift certificate payment from MVP. Do not show failed, pending, or expired payment orders in customer order history.

## 2026-06-29: Toss Payments Test Key First

Decision:

Use Toss Payments test keys for current development and sandbox verification. Defer live PG review, live key switching, and merchant production activation until a deployed homepage URL is available.

Context:

Toss Payments live operation needs merchant review inputs such as service URL, business information, customer-facing policy pages, and payment method approval. The project can still finish the technical payment flow with test keys before that review.

Consequences:

- Local and staging payment verification uses test client/secret keys only.
- Live keys are not required for current development.
- Production PG activation is a launch-readiness task after deployment URL, business registration, customer service information, and policy pages are ready.
- Test keys and live keys must never be committed.

## 2026-06-29: Service Name

Decision:

The service name is `코어블SAF`.

Context:

The frontend previously used the temporary brand name `SafeHub Pro`. The product now needs a stable customer-facing service name before design and deployment work continues.

Consequences:

- Customer-facing frontend brand text uses `코어블SAF`.
- Documentation should use `코어블SAF` for the service name.
- Repository/package identifiers can remain `dropship-shop` unless a separate rename task is needed.

Superseded note:

The original decision excluded all partial cancellation/refund. That part is superseded by `2026-06-27: Payment Group And Delivery Group Refund Unit`, which allows delivery-group order level partial cancellation/refund while still excluding product, option, and quantity-level partial cancellation/refund.

Context:

Card, easy payment, and account transfer fit the current synchronous payment confirmation model: `PAYMENT_PENDING` -> server verification -> `SUPPLIER_ORDER_PENDING`. Virtual account style payment requires account-issued, waiting-for-deposit, deposit-completed, and deposit-expired states, which would complicate the MVP order/payment model.

Consequences:

- MVP payment method enum can start with card, easy pay, and transfer.
- Virtual account state handling is deferred.
- Product, option, and quantity-level partial cancel/refund complexity is deferred.
- Refund policy starts with payment-group level refund and delivery-group order level partial refund.
- Customer order history starts from confirmed orders, not failed/pending/expired checkout attempts.

## 2026-06-27: Automatic Shipment Tracking In MVP

Decision:

Include automatic carrier tracking sync in MVP after an admin enters carrier and tracking number.

Context:

The product should show reliable shipment progress without requiring the admin to manually update every delivered order. However, supplier fulfillment is still manual and tracking providers can fail, so the system needs manual correction as a fallback.

Consequences:

- Admin still manually enters carrier and tracking number.
- The system syncs carrier tracking status after shipment starts.
- Delivered tracking status can automatically move shipment/order to `DELIVERED`.
- Tracking sync failures must be recorded and retried or manually corrected.
- Tracking integration failure must not block order, payment, or refund operations.
- A later technical decision is needed: direct carrier integrations vs a tracking aggregation service. This implementation choice does not change the MVP policy that automatic tracking sync is included.

## 2026-06-27: Shipping Fee Included In Product Price

Decision:

Do not charge customers a separate shipping fee in MVP. Product sale prices include expected shipping cost, and order shipping fee is displayed as `0`.

Context:

Charging shipping per supplier or delivery group can create customer friction, especially when a cart contains products from different suppliers. Including shipping cost in product pricing simplifies checkout and avoids exposing supplier-level shipping complexity to customers.

Consequences:

- `shippingFee` starts as `0` in MVP orders.
- Product pricing must account for expected supplier shipping cost and margin.
- There is no free-shipping threshold policy in MVP.
- Supplier-specific shipping cost differences affect product margin instead of checkout shipping fee.
- Future paid-shipping or free-shipping-threshold campaigns can be added later as a pricing/promotion policy.

## 2026-06-27: Delivery Group Based Orders

Decision:

In MVP, one order contains exactly one delivery group. Delivery groups are based on supplier, but the customer UI should use delivery group wording instead of supplier wording. Cart items from multiple suppliers are split into separate delivery-group orders at checkout.

Context:

Multi-supplier orders introduce partial stock-out, partial shipment, multiple tracking numbers, and partial refund complexity. Order splitting by delivery group keeps fulfillment and refund rules aligned to the supplier-backed delivery boundary.

Consequences:

- Cart can contain multiple delivery groups.
- Checkout must group items by supplier-backed delivery group.
- Each delivery group creates a separate order.
- Shipping fee remains `0` for all delivery groups in MVP.
- Customer order history may show multiple delivery-group orders from one cart checkout.
- One cart checkout can be connected by a payment group and paid through one PG payment.

## 2026-06-27: Cancellation And Refund Scope

Decision:

Allow customer direct cancellation only until `SUPPLIER_ORDER_PENDING`. After `SUPPLIER_ORDERED`, cancellation, return, and exchange requests are handled manually by admin. MVP supports delivery-group order level cancellation/refund inside a payment group, while product, option, and quantity-level partial cancellation/refund is excluded.

Context:

After supplier ordering, the supplier may have started fulfillment or shipment preparation. Post-supplier-order changes need manual review to avoid mismatches between customer order, supplier fulfillment, payment, and shipment state.

Consequences:

- Customer cancel button is shown only through `SUPPLIER_ORDER_PENDING`.
- `SUPPLIER_ORDERED` and later states reject direct customer cancellation.
- Supplier out-of-stock leads to delivery-group order cancellation/refund.
- If only part of a delivery-group order is out of stock, MVP cancels/refunds that whole delivery-group order.
- Return/exchange after delivery starts as inquiry/admin manual handling.
- Refund reason enum starts with customer cancel, supplier out of stock, admin cancel, payment amount mismatch, return requested, and exchange requested.

## 2026-06-27: Admin Operations And Audit Scope

Decision:

Use a single `ADMIN` role for MVP. Admin accounts are granted by DB seed or manual registration only. Admins cannot freely change order status through arbitrary dropdown values; they must use defined action buttons, and an order can only progress when the next operational step is confirmed. MVP excludes automatic state rollback. Wrong state changes are handled through explicit admin correction actions with required reason and history.

Context:

Order status is connected to payment, fulfillment, shipment tracking, customer display status, notification, cancellation, and refund handling. A generic rollback button can create inconsistent side effects after customers have already seen shipment or refund state changes. The safer MVP model is to restrict state transitions to valid operational actions and keep an audit trail for corrections.

Consequences:

- Admin UI needs action buttons instead of arbitrary status dropdown editing.
- Order state transition APIs must validate the current state and requested action.
- Order status history is required from MVP.
- Admin action history is required for cancellation, refund, out-of-stock, shipment manual correction, and admin correction.
- Cancellation, refund, out-of-stock, shipment manual correction, and admin correction actions require a reason.
- Product change history starts with price, product/option sales status, and supplier changes.
- Full product content diff history for HTML, images, names, and summaries is deferred.
- Automatic rollback is excluded from MVP; correction actions are the recovery mechanism.

## 2026-06-27: Legal Notice And Checkout Confirmation

Decision:

Expose terms of service, privacy policy, shipping policy, and cancellation/refund policy from the customer menu and footer. At first signup or first social login completion, collect terms of service and privacy policy agreement. At checkout, require one integrated confirmation checkbox per payment group before payment can start. The checkout confirmation covers order items, payment amount, shipping address, shipping policy, cancellation/refund policy, post-payment supplier out-of-stock possibility, and refund of the affected delivery-group order amount on out-of-stock. Store policy versions and confirmation time with the payment group.

Context:

Account-level agreements and order-level confirmations solve different problems. Signup agreements cover service use and personal data processing. Checkout confirmation records that the customer reviewed the conditions of this specific order before payment. This is especially important because this product model allows supplier out-of-stock after payment.

Consequences:

- Customer menu and footer need policy page links.
- Policy pages need version and effective date.
- First-login flow needs required terms/privacy agreement.
- Checkout needs one integrated confirmation checkbox per payment group.
- Payment request must be blocked until checkout confirmation is complete.
- Payment group records need policy version and confirmation timestamp fields.
- Product detail and checkout must both mention post-payment supplier out-of-stock possibility and affected delivery-group order refund policy.
- MVP customer notifications start with email and order detail status display.
- SMS, Kakao Alimtalk, and app push notifications are deferred.
- Final legal wording remains subject to separate pre-launch legal review.

## 2026-06-27: Payment Exception And Refund Failure Handling

Decision:

If PG payment is approved but the order cannot be confirmed, treat it as a payment exception, block supplier ordering, record the exception reason, and immediately attempt a full PG cancel. Payment exception reasons start with amount mismatch, approval after expiration, sellability check failure, duplicate or conflicting confirmation, and PG confirmation error. If automatic cancel fails, move the case to an admin emergency review queue and keep the processing status visible to the customer.

Refund completion is only allowed after PG cancel/refund success. Paid orders must not move to final `REFUNDED` state until the PG result is confirmed. PG cancel/refund failures stay in failed, retry required, or manual review states and must not be shown as completed to customers.

Context:

A supplier-based shop expects out-of-stock and cancellation/refund operations. The highest-risk failure is collecting money while hiding or failing to fulfill the order. The previous policy said to not confirm mismatched or invalid payments, but it did not fully define what happens when the PG has already approved the payment.

Consequences:

- Payment approval verification requires unexpired `PAYMENT_PENDING` order, checkout confirmation, amount match, conflict-free PG payment key, and sellable product/option status.
- `PAYMENT_EXCEPTION` is introduced for approved payments that cannot become confirmed orders.
- Payment exceptions never enter supplier ordering.
- Approved payment exceptions attempt immediate full PG cancel with idempotency.
- Automatic cancel/refund failure creates an admin emergency review item.
- Refund lifecycle includes PG cancel requested, processing, completed, failed, retry required, and manual review states.
- Payment/refund events need event history for idempotency and PG reconciliation.
- Customer must see processing or review status for PG-approved exception cases instead of the order disappearing.

## 2026-06-27: Payment Group And Delivery Group Refund Unit

Decision:

Support one customer checkout payment for multiple delivery groups. The server creates a payment group for the cart checkout, creates one order per delivery group, and connects all delivery-group orders to the same payment group. One PG payment belongs to one payment group. The PG approved amount must match the payment group total.

MVP supports partial cancellation/refund at the delivery-group order level inside a payment group. Product, option, or quantity-level partial cancellation/refund inside a delivery-group order remains excluded from MVP. If one delivery-group order is out of stock, only that delivery-group order amount is cancelled/refunded, while the other delivery-group orders continue.

Context:

The earlier policy excluded partial cancellation/refund to reduce MVP complexity. After confirming that carts may contain multiple supplier-backed delivery groups, full exclusion became inconsistent with a natural commerce checkout. Cancelling the entire cart because one supplier group is out of stock would be poor customer experience. The compromise is to allow partial refund only at the delivery-group order boundary, which matches the fulfillment boundary.

Consequences:

- Add `PaymentGroup` or equivalent checkout payment aggregate.
- `Payment` points to `PaymentGroup`; `Order` points to `PaymentGroup`.
- One payment group can contain multiple delivery-group orders.
- One order still contains exactly one delivery group.
- Payment approval verifies payment group total, not only a single order total.
- Refund can target one delivery-group order inside a payment group.
- Payment group status can become `PARTIALLY_REFUNDED`.
- Product/option/quantity-level partial refund remains deferred.
- Policies that said partial refund is fully excluded are superseded by this decision.

## 2026-06-27: Cancellation, Return, Exchange, And Claim Policy

Decision:

Customer self-service cancellation is allowed only while an order is `SUPPLIER_ORDER_PENDING` and supplier order work has not started. If `supplierOrderStartedAt` or `addressLockedAt` is already set, the customer cannot directly cancel and must submit a cancellation claim. After `SUPPLIER_ORDERED`, cancellation, return, and exchange are handled through claim submission and admin manual review.

Post-delivery return/exchange is handled as a `Claim`, separate from `Refund`. Claim types start with cancellation, return, and exchange. Claim reasons start with simple change of mind, defect, wrong delivery, different from product information, and delivery issue.

Simple change-of-mind return/exchange requests are accepted for review within 7 days from delivery completion. Defect, wrong delivery, different-from-product-information, and delivery issue claims are accepted for review within 3 months from delivery completion and within 30 days from the customer discovering or being able to discover the issue. Defect, wrong delivery, product-information mismatch, and delivery issue claims require photo evidence by default.

Simple change-of-mind return/exchange shipping cost is borne by the customer by default. Seller-fault return/exchange shipping cost is borne by the seller/operator by default. Claim approval does not itself complete a refund; PG cancel/refund still follows the `Refund` lifecycle and must succeed before the customer sees refund completed.

For refunds that require returned goods, PG cancel/refund request should start within 3 business days from return receipt confirmation. For cancellation refunds that do not require returned goods, PG cancel/refund request should start within 3 business days from cancellation approval.

Context:

The earlier policy only said that post-supplier-order cancellation and post-delivery return/exchange would be handled manually. That was not enough for implementation because self-service cancellation, cancellation claims, return claims, exchange claims, evidence requirements, and shipping cost burden need different UI, API, state, and admin handling rules.

Consequences:

- Customer cancel button visibility must check order status and supplier order work start.
- Add `Claim` model and claim statuses separate from `Refund`.
- Admin needs actions for evidence request, claim approval, claim rejection, return received, and exchange shipping.
- Claim reason and evidence rules must be shown on customer claim screens.
- Refund execution timing needs a 3-business-day operational target after return receipt confirmation or cancellation approval.
- Legal/customer notice policy must include claim windows and shipping cost burden rules.
- Refund processing remains delivery-group order level and PG-success based.

## 2026-06-27: Supplier Fulfillment SLA, Address Lock, And Shipment Policy

Decision:

Supplier ordering stays manual in MVP, but the operation must have explicit timing and locking rules. After payment confirmation, admin should start supplier order work on the same business day or next business day. Orders paid before 15:00 are targeted for same-business-day supplier order work; orders paid after 15:00, on weekends, or on holidays are targeted for next-business-day work.

When admin starts supplier order work, the system records `supplierOrderStartedAt` and locks the shipping address with `addressLockedAt`. MVP does not add a new order status for this working state. Customer direct address changes are allowed in `SUPPLIER_ORDER_PENDING` only while `addressLockedAt` is empty. After address lock, address changes require customer support or admin manual handling.

Supplier response or expected shipment date should be secured within 1 business day after supplier order. If expected shipment remains unclear for 2 business days after supplier order, the customer must receive a delay notice. If supplier out-of-stock is confirmed, the order moves to out-of-stock notice and delivery-group order level refund handling.

MVP shipment model is one shipment per order. Partial shipment and split shipment are excluded from MVP. Automatic tracking sync can move shipment state forward, but must not move shipment backward or overwrite admin manual correction without a valid forward transition and recorded reason.

Context:

The shop relies on manual supplier ordering. Without a work-start lock, customer address edits can race with supplier ordering and create a mismatch between the site order and the supplier order. Adding a separate order status only for work-in-progress would increase state complexity before the full transition table is finalized. Field-based locking keeps the order state simpler while preserving auditability.

Consequences:

- Add supplier order work start fields to order or fulfillment models.
- Add address lock fields: `addressLockedAt` and `addressLockedByAdminId`.
- Supplier order evidence includes supplier order number, ordered address snapshot, ordered admin, expected ship date, and supplier response memo.
- Delay notification tracking is required for orders with unclear expected shipment after 2 business days.
- Customer address change API must check both order status and `addressLockedAt`.
- Admin order actions include supplier order work start.
- Shipment model is one shipment per order in MVP.
- Tracking sync must respect admin manual corrections and only apply valid forward transitions.

## 2026-06-27: Privacy, Business Notice, And Legal Disclosure Policy

Decision:

Customer-facing legal disclosure starts with business/operator information in the footer and customer center/company information pages. The displayed fields are company name, representative name, business registration number, mail-order sales registration number, mail-order sales registration authority, business address, customer center phone, customer center email, customer center hours, privacy officer contact, and hosting provider.

Product detail pages must include product information notice fields, shipping information, AS information, return/exchange information, and claim guidance. Policy pages must include terms, privacy policy, shipping policy, cancellation/refund policy, and return/exchange/claim policy.

The privacy policy must include processing purpose, collected items, retention period, third-party provision, processing consignment, destruction procedure, data subject rights, and privacy officer. A privacy processing table stores collection item, purpose, retention period, processor/consignee, and third-party sharing fields.

Social login stores provider, provider user id, and display name as the baseline. Provider email is stored only when the provider supplies it, and it is not required for login. Customer contact email or phone number is collected separately when needed for order, shipping, or claim handling.

Transactional notifications for order, shipping, payment, refund, and claim handling are separated from optional marketing consent. Marketing notifications require separate channel-level opt-in and store agreement time, withdrawal time, and policy version.

On account deletion, customer profile and social account linkage are deleted or anonymized. Legal-retention order, payment, shipment, refund, claim, and policy agreement records are separated from normal service lookup and retained until their retention period expires. Rejoining with the same social account creates a new user account and does not automatically restore old order history to the customer screen.

Legal retention starts with 6 months for display/advertising records, 5 years for contract or withdrawal records, 5 years for payment and goods supply records, and 3 years for consumer complaint or dispute records.

Context:

The project is moving from high-level policy pages to a product that can be launched as a commerce site. The implementation needs concrete fields for footer disclosure, privacy processing, legal retention, account deletion, and marketing consent separation before auth, order, notification, and policy-page models are finalized.

Consequences:

- Add `BusinessProfile` for footer/customer center disclosure.
- Add `PrivacyProcessingItem` for privacy policy processing table.
- Add `MarketingConsent` separate from transactional notifications.
- Add `LegalRetentionRecord` for separated legal-retention records after withdrawal.
- Account deletion must anonymize or remove profile/social account linkage while preserving legally required records.
- Rejoin behavior does not merge deleted account history into the new customer account.
- Product detail and policy pages need structured legal notice sections.

## 2026-06-27: Order State Transition Table And Operational Audit Policy

Decision:

MVP removes `PREPARING_SHIPMENT` as an order status. The period after supplier order completion and before carrier/tracking input is represented by `SUPPLIER_ORDERED`. This keeps the state model smaller before implementation and avoids another customer-facing status that maps to the same "상품 준비 중" display.

Order state transitions are defined by from status, actor, action, guard, side effect, and target status. Admins cannot change arbitrary status values through a dropdown. They must execute defined actions, and each action validates its guard before changing state or recording side effects.

Customer order history is separated from checkout/retry screens. `PAYMENT_PENDING`, `EXPIRED`, and payment failure states are not normal order-history rows. They belong to the current checkout, retry, or payment-result surface. Customer order history includes confirmed orders and customer-visible payment exceptions.

Forbidden transitions include refund completion without PG cancel/refund success, shipment without carrier and tracking number, delivery completion without shipment evidence, out-of-stock after shipment except through claim/manual correction handling, supplier ordering from payment exception, and confirming an expired checkout.

Transaction notifications are recorded in `NotificationLog`. Initial triggers are payment completed, payment exception/cancel processing, supplier out of stock, shipment started, delivery completed, delay notice, claim status changed, and refund completed. Marketing notifications remain separate through `MarketingConsent`.

Order item snapshots include product/option names, price, product summary, product detail snapshot reference, and product information notice snapshot reference. Later product content changes do not mutate completed order snapshots.

Context:

The project is ready to move toward DS-2 and backend implementation. Without a transition table, implementation can accidentally allow invalid state jumps or hide important side effects such as notifications, refund events, and shipment evidence. The audit model also needs to capture why an action was allowed, not only the before/after status.

Consequences:

- Add transition table to order policy.
- Remove `PREPARING_SHIPMENT` from MVP status lists and customer display mapping.
- Add `NotificationLog`.
- Extend `OrderItem` snapshot fields.
- Extend order status history with guard result and side effect summary.
- Separate customer order history from checkout/retry surfaces.
- Implement forbidden transition checks before backend order state code.

## 2026-06-27: Final MVP State Sets

Decision:

Finalize MVP state sets before backend implementation. Order statuses are `PAYMENT_PENDING`, `EXPIRED`, `PAYMENT_EXCEPTION`, `SUPPLIER_ORDER_PENDING`, `SUPPLIER_ORDERED`, `OUT_OF_STOCK`, `SHIPPED`, `DELIVERED`, `CANCELLED`, `REFUND_REQUESTED`, and `REFUNDED`.

`PREPARING_SHIPMENT` is not an MVP order status, and `CANCEL_REQUESTED` is not an MVP order status. `CANCELLED` is reserved for PG approval before-order termination or payment exception cancel completion. Paid order refund completion uses `REFUNDED` and requires PG cancel/refund success.

Payment group statuses are `PAYMENT_PENDING`, `APPROVED`, `PARTIALLY_REFUNDED`, `REFUNDED`, `PAYMENT_EXCEPTION`, `EXPIRED`, `CANCELLED`, and `CANCEL_FAILED`. Payment statuses are `READY`, `APPROVED`, `FAILED`, `CANCEL_REQUIRED`, `CANCEL_REQUESTED`, `CANCELLED`, `CANCEL_FAILED`, `REFUND_REQUESTED`, `PARTIALLY_REFUNDED`, `REFUNDED`, `REFUND_FAILED`, and `REVIEW_REQUIRED`.

Fulfillment statuses are `PENDING`, `ORDERED`, `OUT_OF_STOCK`, and `CANCELLED`. Shipment statuses are `READY`, `SHIPPED`, and `DELIVERED`. Refund statuses are `REQUESTED`, `APPROVED`, `PG_CANCEL_REQUESTED`, `PROCESSING`, `COMPLETED`, `FAILED`, `RETRY_REQUIRED`, `REJECTED`, and `MANUAL_REVIEW_REQUIRED`.

Context:

DS-23 through DS-28 hardened edge cases, but the state lists still had a few transitional leftovers. DS-2 locks the final state vocabulary so backend enum definitions, transition guards, customer status mapping, and admin actions can be implemented consistently.

Consequences:

- Backend enums should follow the finalized state sets in `docs/domain-model.md`.
- `PREPARING_SHIPMENT` should not be generated in code or UI for MVP.
- `CANCEL_REQUESTED` should not be generated as an order status for MVP; in-progress cancellation uses `REFUND_REQUESTED` plus `Refund.status`.
- Invalid state transitions remain governed by the order policy transition table and forbidden transitions.

## 2026-06-27: Supplier Order Model

Decision:

Supplier ordering is manual in MVP.

Context:

Supplier API integration is unnecessary before validating operations.

Consequences:

- Admin order queue is required.
- Admin must be able to mark supplier order completed, out of stock, and shipment started.
- Later automation can replace manual steps without changing the core order states.

## 2026-06-27: Backend Direction

Decision:

Use Spring Boot as the backend foundation.

Context:

The project owner is already comfortable with Spring Boot.

Consequences:

- Start with a modular monolith.
- Prefer PostgreSQL and JPA.
- Avoid microservices in MVP.

## 2026-06-27: Guest Checkout

Decision:

Do not allow guest checkout in MVP.

Context:

The first version should minimize order, payment, refund, and shipment ownership complexity. Every order should belong to an authenticated customer.

Consequences:

- All orders require `userId`.
- Guest cart and guest order lookup are out of MVP scope.
- Checkout requires login.
- Customer order history, refund requests, and shipment lookup can rely on authenticated user ownership checks.

## 2026-06-27: Social Login And Admin Access

Decision:

Support only Kakao, Google, and Naver social login in MVP. Admin users also use social login, but only DB-registered accounts can access admin features.

Context:

The product owner does not want to operate a separate email/password login flow. Social-only login removes password storage, email verification, and password reset scope from MVP. Admin access should rely on internal authorization, not a separate admin password login.

Consequences:

- Customer email/password login is out of MVP scope.
- Password hash storage is not needed for customer or admin accounts.
- User identity must store provider and provider user id.
- Kakao, Google, and Naver OAuth flows are required.
- Same email across different providers is treated as separate accounts in MVP.
- Account merge is deferred.
- Admin authorization is controlled by DB role or an admin allowlist.
- A social account without DB admin permission cannot access admin features.

## 2026-06-28: Cookie-Based JWT Authentication

Decision:

Use provider OAuth login with a stateless JWT access token stored in an HttpOnly cookie for MVP backend authentication.

Context:

The MVP needs browser-friendly authentication for Kakao, Google, and Naver social login without adding email/password accounts or server-side sessions. The frontend should not need to manually store bearer tokens.

Consequences:

- OAuth start/callback endpoints are public.
- Successful OAuth callback creates or finds the user by provider and provider user id.
- The API sets `ACCESS_TOKEN` as an HttpOnly cookie with `SameSite=Lax`.
- Production must set the access token cookie as `Secure` and run behind HTTPS.
- API authorization remains stateless; each request verifies the JWT and reloads the current active user role from the database.
- Logout clears the access token cookie.
- Refresh token rotation, long-lived sessions, and account linking are deferred from DS-30.

## 2026-06-28: Account Agreement Gate

Decision:

Store required terms/privacy agreement per user and require current agreement before checkout creation.

Context:

The product has no separate signup form because customers enter through social login. Required legal agreement therefore happens after login and before the first order creation.

Consequences:

- `user_policy_agreements` stores terms version, privacy version, and agreement time.
- `GET /api/me/agreements` exposes whether the user has accepted the current required versions.
- `POST /api/me/agreements` records required agreement and is idempotent for the same version pair.
- Product browsing and cart management can happen before agreement.
- `POST /api/checkouts` rejects users without current required terms/privacy agreement.
- Marketing consent remains separate and is not included in DS-31.

## 2026-06-28: Address Book And Address Change Window

Decision:

Implement a customer address book separately from order shipping address snapshots, and allow direct customer address changes only before operational lock points.

Context:

Customers need reusable shipping addresses, but orders must preserve the shipping address used for fulfillment and disputes. Checkout policy confirmation includes shipping address, so changing checkout address after confirmation would make the confirmation stale.

Consequences:

- `user_addresses` stores reusable customer-owned addresses.
- Orders keep independent shipping address snapshots.
- The first saved address becomes default, and default address changes are managed server-side.
- Checkout shipping address can change only while payment is pending and before checkout policy confirmation.
- Paid order shipping address can change only while the order is `SUPPLIER_ORDER_PENDING` and supplier work/address lock has not started.
- After `addressLockedAt` or `SUPPLIER_ORDERED`, customer direct address change is rejected.

## 2026-06-28: DB State-Based Payment Exception Queue

Decision:

Use database payment/order states as the MVP admin queue for payment exceptions instead of introducing a separate queue broker.

Context:

Payment exception handling needs an operator-visible follow-up queue when an approved PG payment cannot become a confirmed order and automatic PG cancel fails. The MVP does not need Kafka, RabbitMQ, or a background worker queue for this. What the admin needs is a durable list of records whose current state requires action.

Consequences:

- `payments.status` is the queue source for payment exceptions.
- `CANCEL_REQUIRED`, `CANCEL_REQUESTED`, `CANCEL_FAILED`, and `REVIEW_REQUIRED` are admin payment exception queue candidates.
- Automatic payment exception cancel uses a stable idempotency key derived from the payment id.
- Successful automatic cancel moves `Payment` and `PaymentGroup` to `CANCELLED` and removes the item from the admin queue.
- Failed automatic cancel moves `Payment` and `PaymentGroup` to `CANCEL_FAILED`, keeps failure code/message, and exposes the item through the admin queue.
- Admin retry reuses the same idempotency key so duplicate cancel requests do not double-cancel at the PG.
- A separate broker-based queue can be added later for scheduled retries or alerting, but the state table remains the source of truth.

## 2026-06-28: Toss Webhook Verification And Reconciliation

Decision:

Verify Toss payment webhooks by re-fetching the payment from Toss Payments with `paymentKey`, then reconcile the verified PG status with the local payment state.

Context:

The MVP already treats the server-side Toss confirmation result as the primary order confirmation path. Webhooks should improve reconciliation and operational visibility without becoming a second independent order-confirmation path.

Consequences:

- `POST /api/payments/toss/webhook` is public but must verify the payment through the Toss secret-key-backed payment lookup API.
- Webhook payload status must match the verified Toss payment lookup status.
- Webhook idempotency uses `TossPayments-Webhook-Transmission-Id` when present.
- Duplicate webhook deliveries are ignored after the first saved event.
- Unknown local `paymentKey` webhooks are accepted after Toss lookup verification but do not create local payment events.
- If webhook status conflicts with the local server-confirmed state, the payment moves to `REVIEW_REQUIRED` instead of auto-mutating orders or refunds.
- `REVIEW_REQUIRED` appears in the admin payment exception queue.

## 2026-06-29: Markdown Backlog And Git Commit Workflow

Decision:

Use `docs/BACKLOG.md` and git commits as the default solo-development workflow. Linear, GitHub Issues, and PRs are no longer the default work unit.

Context:

The project is currently developed by one person with frequent AI pair-programming. Linear issue updates, GitHub Issue management, PR creation, and cross-tool status sync add more overhead than value for small and medium changes.

Consequences:

- `docs/BACKLOG.md` is the current work queue.
- A normal work unit is one backlog item and one git commit.
- Small bugs, copy changes, and style fixes can be handled without backlog entries.
- PRs are reserved for team review, deployment review, or high-risk changes.
- Linear and GitHub Issues remain historical records and can be reintroduced when team collaboration requires them.

## 2026-06-29: Customer Required Info And Phone Verification

Decision:

Use one customer account flow with no business-member type. For production readiness, collect only minimal customer required info after social login and verify phone number ownership with SMS OTP.

Context:

The shop does not need a separate business-member profile. It does need reliable customer contact information before checkout and shipment. Full CI/DI identity verification through NICE, KCB, or Toss 인증 is heavier than the current requirement and should not be added until real-name verification, adult verification, or duplicate identity control is required.

Consequences:

- Customer account type remains simple: social-login customer or admin role.
- Required customer info starts with display name, reachable email when social email is missing or placeholder, and verified phone number.
- MVP phone verification uses SMS OTP, not CI/DI identity verification.
- OTP codes must be hashed at rest and bounded by expiration, resend cooldown, and attempt limits.
- Phone number changes require re-verification.
- Checkout gating for incomplete required info is tracked as backlog item B-017.

## 2026-06-30: B-012 Legal Footer And Customer Inquiry MVP

Decision:

Use the confirmed 가라사니 business information in the customer-facing footer and company page, and receive customer inquiries through a site form backed by `customer_inquiries`.

Context:

The shop needs visible legal/customer notice paths before real operation and Toss live review. Terms, privacy, shipping, cancellation/refund, and post-payment stock risk pages can start as MVP drafts based on existing policy documents. The owner accepts public display of the business address.

Consequences:

- Footer and company page display 상호 `가라사니`, 대표자명 `김문교`, 사업자등록번호 `611-05-94564`, and the 송파구 business address.
- 통신판매업 신고번호, hosting provider, customer center phone/email, and payment/purchase safety details remain marked as 준비중 until live payment opening.
- Customer inquiries are accepted without login through `/support` and listed for admins at `/admin/inquiries`.
- Reply workflow, inquiry assignment, and customer center SLA are deferred until inquiry volume requires them.
- Legal text remains an MVP draft and requires final launch review.

## 2026-07-03: B-014 Account Deletion And Legal Retention Boundary

Decision:

회원 탈퇴는 즉시 물리 삭제가 아니라 `users.status=DELETED`, `deleted_at`, `anonymized_at` 기록과 개인식별정보 비식별화로 처리한다. 이메일은 `deleted-{userId}@deleted.local`, 표시 이름은 `탈퇴회원`, 휴대폰 번호와 인증 시각은 null로 바꾸고, 현재 MVP 소셜 연결 값인 `provider_user_id`는 `deleted-{userId}`로 바꾼다.

진행 중 주문, 환불, 클레임이 있으면 탈퇴를 400으로 거부한다. 종결 주문은 `DELIVERED`, `CANCELLED`, `REFUNDED`, `EXPIRED`이며, 종결 환불은 `COMPLETED`, `REJECTED`, 종결 클레임은 `COMPLETED`, `REJECTED`, `WITHDRAWN`이다.

별도 `LegalRetentionRecord` 색인 테이블은 이번 범위에서 만들지 않는다. 주문, 결제, 배송, 환불, 클레임, 약관 동의 기록은 비식별화된 유저 row를 참조한 채 보존한다. 법정 보존 기간 만료 후 자동 완전 삭제와 보존 색인은 후속 운영 이슈로 둔다.

Context:

탈퇴 후 같은 소셜 계정 재가입은 새 고객 계정으로 시작해야 하지만, 현재 구현은 별도 `social_accounts` 테이블 없이 `users.provider/provider_user_id` unique 제약으로 소셜 식별자를 저장한다. 또한 주문/환불/클레임 기록은 참조 무결성과 법정 보존이 필요하다.

Consequences:

- `users.deleted_at`, `users.anonymized_at` 컬럼을 추가한다.
- OAuth 로그인은 `ACTIVE` 유저의 provider identity만 재사용한다.
- 탈퇴 성공 후 기존 JWT는 DB status 검사에서 인증되지 않는다.
- 고객 화면에는 되돌릴 수 없는 탈퇴 안내와 확인 체크박스를 둔다.
- 별도 법정 보존 색인과 보존 기간 만료 자동 삭제는 후속 범위로 남긴다.

## 2026-07-03: B-011 Transactional Notifications Use SMS First

Decision:

거래 알림의 1차 실제 발송 채널은 SMS로 한다. 이메일 SMTP와 카카오 알림톡은 구조와 enum은 유지하되 이번 범위에서는 실제 연동하지 않는다.

Context:

코어블SAF는 앱이 없는 웹 쇼핑몰이고, 주문/입금/배송/환불 안내는 고객 도달률이 중요하다. 이미 휴대폰 인증용 Naver SENS SMS 설정과 fallback sender가 있으므로, 별도 SMTP/알림톡 계약보다 SMS를 먼저 운영 채널로 확장하는 편이 작고 실질적이다. 기존 `NotificationLog`는 실제 발송 없이 `EMAIL/SENT`를 남겨 운영 이력으로 신뢰할 수 없었다.

Consequences:

- `NotificationLog`는 `PENDING`으로 생성되고 발송 결과에 따라 `SENT`, `FAILED`, `SKIPPED`로 바뀐다.
- `sms.sens.enabled=false` 또는 자격증명 없는 기본 환경은 거래 SMS를 발송하지 않고 `SKIPPED`로 기록한다.
- 주문 관련 알림 수신자는 계정 휴대폰 번호가 아니라 해당 주문의 `recipientPhone`을 사용한다.
- SMS 발송은 주문/결제 트랜잭션 커밋 이후 실행해 SENS 장애가 입금확인, 배송, 환불 처리를 롤백하지 않게 한다.
- 이메일 SMTP와 카카오 알림톡은 도달률, 비용, 계약 상태를 다시 판단한 뒤 후속 이슈로 붙인다.

## 2026-07-17: Direct Bank Transfer Only, No Toss Payments

Decision:

고객 결제 수단은 직접 계좌입금과 관리자 입금확인만 제공한다. Toss Payments를 포함한 PG 결제는 재도입 후보로 남기지 않는다.

Context:

초기 운영 규모에서는 계좌입금 흐름이 이미 구현되어 있고, PG 계약·심사·키·웹훅·환불 예외 운영을 함께 유지하는 비용이 실제 필요보다 크다. 결제 수단을 하나로 고정해 주문·환불 운영과 법적 준비 범위를 줄인다.

Consequences:

- B-001은 Deferred가 아니라 취소로 종료한다.
- 카드, 간편결제, PG 계좌이체·가상계좌, 휴대폰 결제, 상품권 결제는 제공하지 않는다.
- 구매안전서비스는 PG가 아니라 은행 에스크로 또는 소비자피해보상보험으로 확보한다.
- 환불은 실제 계좌이체 후 관리자가 완료를 기록하는 기존 흐름만 사용한다.
- 미사용 Toss endpoint, client, env, 고객 화면과 문서는 B-067에서 제거한다.
- 과거 데이터와 migration 호환에 필요한 결제 provider/method 값은 기존 데이터 존재 여부를 확인한 뒤 보존한다.
