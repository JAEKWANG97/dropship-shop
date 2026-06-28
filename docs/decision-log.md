# Decision Log

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

Use fixed MVP image limits: one thumbnail image, up to ten gallery images, up to fifty detail block images, max 5MB per image, and allowed extensions `jpg`, `jpeg`, `png`, and `webp`.

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

Social login stores provider, provider user id, email, and display name as the baseline. Phone number is not required for social login and is collected only when needed for order, shipping, or claim handling.

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
