# Linear Backlog

This backlog is intended for the `Dropship Shop` Linear organization.

Recommended setup:

- Team: Core
- Team key: DS
- Project: MVP

## Milestone 1: Product And Domain Design

### DS-1: Confirm MVP scope and non-goals

Description:

Finalize the first release scope for the single-operator dropshipping shop.

Acceptance criteria:

- Customer MVP scope is confirmed.
- Admin MVP scope is confirmed.
- Explicit non-goals are documented.
- Any launch-blocking policy gaps are listed.

Resolved decisions:

- Customer MVP scope is locked in `docs/product-brief.md` and `docs/requirements.md`.
- Admin MVP scope is locked in `docs/product-brief.md` and `docs/requirements.md`.
- Explicit MVP non-goals are documented in `README.md` and `docs/product-brief.md`.
- Launch-blocking checks are listed in `docs/product-brief.md`; these are launch readiness checks, not blockers for backend implementation.
- Backend implementation can start from DS-4 after DS-1 and DS-2 are complete.

References:

- `README.md`
- `docs/product-brief.md`
- `docs/requirements.md`

### DS-2: Finalize order, payment, fulfillment, and refund states

Description:

Lock down the state model before implementing checkout or admin workflows.

Acceptance criteria:

- Order statuses are finalized.
- Payment statuses are finalized.
- Fulfillment statuses are finalized.
- Shipment statuses are finalized.
- Refund statuses are finalized.
- Invalid state transitions are listed.

Resolved decisions:

- Order statuses are finalized: `PAYMENT_PENDING`, `EXPIRED`, `PAYMENT_EXCEPTION`, `SUPPLIER_ORDER_PENDING`, `SUPPLIER_ORDERED`, `OUT_OF_STOCK`, `SHIPPED`, `DELIVERED`, `CANCELLED`, `REFUND_REQUESTED`, `REFUNDED`.
- `PREPARING_SHIPMENT` and `CANCEL_REQUESTED` are not MVP order statuses.
- `CANCELLED` is used for PG approval before-order termination or payment exception cancel completion; paid order refund completion uses `REFUNDED`.
- Payment group statuses are finalized: `PAYMENT_PENDING`, `APPROVED`, `PARTIALLY_REFUNDED`, `REFUNDED`, `PAYMENT_EXCEPTION`, `EXPIRED`, `CANCELLED`, `CANCEL_FAILED`.
- Payment statuses are finalized: `READY`, `APPROVED`, `FAILED`, `CANCEL_REQUIRED`, `CANCEL_REQUESTED`, `CANCELLED`, `CANCEL_FAILED`, `REFUND_REQUESTED`, `PARTIALLY_REFUNDED`, `REFUNDED`, `REFUND_FAILED`, `REVIEW_REQUIRED`.
- Fulfillment statuses are finalized: `PENDING`, `ORDERED`, `OUT_OF_STOCK`, `CANCELLED`.
- Shipment statuses are finalized: `READY`, `SHIPPED`, `DELIVERED`.
- Refund statuses are finalized: `REQUESTED`, `APPROVED`, `PG_CANCEL_REQUESTED`, `PROCESSING`, `COMPLETED`, `FAILED`, `RETRY_REQUIRED`, `REJECTED`, `MANUAL_REVIEW_REQUIRED`.
- Invalid transitions are listed in order policy forbidden transitions.

References:

- `docs/order-flow.md`
- `docs/domain-model.md`

### DS-3: Choose payment provider for MVP

Description:

Select the PG provider for sandbox and production integration.

Acceptance criteria:

- Candidate PGs are compared.
- Sandbox integration path is confirmed.
- Refund/cancel API support is confirmed.
- Production approval requirements are listed.

Notes:

- Toss Payments is confirmed for MVP.
- DS-18 tracks the remaining payment method policy decisions.

Resolved decisions:

- Toss Payments is selected for MVP.
- PortOne is not used for MVP.
- Sandbox integration path is documented in `docs/policies/payment-policy.md`.
- Refund/cancel support is handled through Toss Payments payment cancel APIs and reflected in the payment/refund policies.
- Production readiness requirements are documented in `docs/policies/payment-policy.md`.
- DS-18 resolved the enabled Toss Payments method set: card, easy payment, and account transfer.

## Milestone 1.5: MVP Policy Completion

### DS-18: Finalize Toss Payments method policy

Description:

Finalize which Toss Payments methods are enabled in MVP and which are deferred.

Acceptance criteria:

- MVP payment methods are confirmed: card, easy payment, account transfer.
- Virtual account/bank-transfer-like async payment handling is explicitly deferred.
- Product/option/quantity-level partial cancel support is excluded from MVP; delivery-group order level partial refund is handled by DS-24.
- Failed/expired payment order visibility is decided: not shown in customer order history.
- `docs/policies/payment-policy.md` and `docs/decision-log.md` are updated.

References:

- `docs/policies/payment-policy.md`
- `docs/policies/order-policy.md`
- `docs/order-flow.md`

### DS-19: Finalize fulfillment and shipping policy

Description:

Finalize supplier order and shipping policy for MVP operations.

Acceptance criteria:

- Manual supplier ordering is confirmed: admin manual supplier order.
- Shipping fee policy is decided: shipping fee is included in product price and customer shipping fee is 0.
- Multiple-supplier order policy is decided: checkout splits orders by delivery group.
- Shipment tracking input and delivery completion handling are decided: admin enters carrier/tracking number, automatic tracking sync is included, admin manual correction is available.
- `docs/policies/fulfillment-shipping-policy.md` and `docs/decision-log.md` are updated.

References:

- `docs/policies/fulfillment-shipping-policy.md`
- `docs/order-flow.md`
- `docs/domain-model.md`

### DS-20: Finalize cancellation and refund policy

Description:

Finalize customer cancellation, supplier out-of-stock, refund, return, and exchange policy for MVP.

Acceptance criteria:

- Direct customer cancellation window is decided: through `SUPPLIER_ORDER_PENDING`.
- Admin approval rules after supplier order are decided: manual admin handling after `SUPPLIER_ORDERED`.
- Partial out-of-stock handling is decided: delivery-group order level cancellation/refund, with product/option/quantity-level partial refund excluded from MVP.
- Refund reason categories are decided.
- Post-shipment return/exchange MVP scope is decided: inquiry/admin manual handling.
- `docs/policies/cancellation-refund-policy.md` and `docs/decision-log.md` are updated.

References:

- `docs/policies/cancellation-refund-policy.md`
- `docs/policies/payment-policy.md`
- `docs/order-flow.md`

### DS-21: Finalize admin operations policy

Description:

Finalize admin role, audit, and manual operation policy for MVP.

Acceptance criteria:

- Admin role model is decided: `ADMIN` single role for MVP.
- Admin account creation flow is decided: DB seed or manual registration only.
- Admin order operation model is decided: defined action buttons, not arbitrary status dropdown changes.
- Admin action history scope is decided: order status changes, cancellation/refund, out-of-stock, shipment manual correction, admin correction actions.
- Product change history scope is decided: price, product/option sales status, supplier changes.
- Refund and correction reason requirements are decided: required for cancellation, refund, out-of-stock, shipment manual correction, and admin correction.
- State rollback support is decided: no automatic rollback button in MVP; corrections are explicit admin actions with history.
- `docs/policies/admin-operations-policy.md` and `docs/decision-log.md` are updated.

References:

- `docs/policies/admin-operations-policy.md`
- `docs/policies/account-policy.md`
- `docs/policies/order-policy.md`

### DS-22: Finalize legal and customer notice policy

Description:

Finalize where and how customer-facing notices and policy acknowledgements are shown in MVP.

Acceptance criteria:

- Supplier out-of-stock notice locations are decided: product detail and checkout.
- Policy page locations are decided: customer menu and footer.
- Signup/first-login agreements are decided: terms of service and privacy policy agreement.
- Checkout policy acknowledgement checkbox is decided: one integrated checkbox per payment group.
- Policy versioning scope is decided: policy pages have version/effective date, and payment group confirmation stores policy versions and confirmation time.
- Customer notification method baseline is decided: email and order detail status display for MVP.
- `docs/policies/legal-and-customer-notice-policy.md` and `docs/decision-log.md` are updated.

References:

- `docs/policies/legal-and-customer-notice-policy.md`
- `docs/policies/catalog-inventory-policy.md`
- `docs/policies/cancellation-refund-policy.md`

## Milestone 1.6: Policy Hardening From Operational Review

These issues come from the multi-perspective policy review for a supplier-based commerce operation. They should be resolved before DS-2 is finalized or backend implementation starts.

Recommended execution order:

1. DS-23
2. DS-24
3. DS-26
4. DS-25
5. DS-27
6. DS-28

### DS-23: Finalize payment exception and refund failure policy

Description:

Close payment and refund edge cases that can create approved payment without a fulfillable or visible order.

Acceptance criteria:

- Payment exception states are defined for amount mismatch, approval after expiration, unsellable products, duplicate/conflicting confirmation, and PG confirmation error.
- Automatic full PG cancel behavior is decided: attempt immediate full PG cancel for approved payment exceptions.
- Admin emergency queue behavior is defined: automatic cancel/refund failure enters admin emergency review and stays customer-visible.
- Refund lifecycle includes requested, approved, PG cancel requested, processing, completed, failed, retry required, rejected, and manual review states.
- Customer-facing statuses are defined: refund received, refund processing, refund completed, payment review/cancel processing.
- PG event, payment, and refund audit fields are defined for Toss Payments reconciliation and idempotency.
- Payment, cancellation/refund, order policy, order-flow, domain-model, and decision-log docs are updated.

References:

- `docs/policies/payment-policy.md`
- `docs/policies/cancellation-refund-policy.md`
- `docs/policies/order-policy.md`
- `docs/order-flow.md`
- `docs/domain-model.md`

### DS-24: Finalize delivery-group checkout payment unit policy

Description:

Decide how payment works when a cart contains multiple delivery groups.

Acceptance criteria:

- MVP payment unit rule is decided: one customer checkout can pay for multiple delivery-group orders through one payment group.
- Payment model is decided: PG payment 1 = payment group 1, and payment group 1 can contain multiple delivery-group orders.
- Multi-delivery-group checkout UX is decided: customer pays once for the whole cart while the server creates one order per delivery group.
- Partial cancellation/refund scope is decided: delivery-group order level is supported.
- Product, option, and quantity-level partial cancellation/refund remains excluded from MVP.
- Out-of-stock behavior is defined: if one delivery-group order is out of stock, refund only that delivery-group order amount and keep other delivery-group orders active.
- Domain model includes `PaymentGroup` or equivalent checkout payment aggregate.
- Order number and customer order history display rules are defined for split delivery-group orders under one payment group.
- Fulfillment/shipping, payment, order-flow, domain-model, requirements, and decision-log docs are updated.

References:

- `docs/policies/fulfillment-shipping-policy.md`
- `docs/policies/payment-policy.md`
- `docs/order-flow.md`
- `docs/domain-model.md`

### DS-25: Finalize cancellation, return, exchange, and claim policy

Description:

Separate self-service cancellation from customer cancellation, return, exchange, and claim handling rights.

Acceptance criteria:

- Customer self-service cancel button and cancellation/return/exchange claim rights are separated.
- After-supplier-order cancellation request flow before shipment is defined.
- Post-delivery return/exchange request windows and manual review flow are defined.
- Claim reason categories are defined: simple change of mind, defect, wrong delivery, different from product info, delivery issue.
- Shipping cost burden rules are defined for simple change of mind vs seller fault.
- Evidence requirements such as photos are defined for defect or wrong delivery.
- Claim entity/model requirements and admin handling statuses are defined.
- Cancellation/refund, legal/customer notice, order-flow, domain-model, requirements, and decision-log docs are updated.

Resolved decisions:

- Customer self-service cancel button is shown only when the order is `SUPPLIER_ORDER_PENDING` and supplier order work has not started.
- After supplier order work starts and before shipment, customer cancellation is handled as a cancellation claim with admin review.
- Post-delivery return/exchange is handled as a claim with admin manual review.
- Simple change-of-mind return/exchange request window is 7 days from delivery completion.
- Defect, wrong delivery, different-from-product-info, and delivery issue claims must be requested within 3 months from delivery completion and within 30 days from discovery.
- Claim reasons start with simple change of mind, defect, wrong delivery, different from product info, and delivery issue.
- Simple change-of-mind return/exchange shipping cost is borne by the customer by default.
- Seller-fault return/exchange shipping cost is borne by the seller/operator by default.
- Defect, wrong delivery, different-from-product-info, and delivery issue claims require photo evidence by default.
- Refund request starts within 3 business days from return receipt confirmation or cancellation approval.
- `Claim` model and claim handling statuses are added separately from `Refund`.

References:

- `docs/policies/cancellation-refund-policy.md`
- `docs/policies/legal-and-customer-notice-policy.md`
- `docs/order-flow.md`
- `docs/domain-model.md`

### DS-26: Finalize supplier fulfillment SLA, address lock, and shipment policy

Description:

Define operating rules for manual supplier ordering, delayed fulfillment, customer address lock, and shipment corrections.

Acceptance criteria:

- Supplier order SLA after payment confirmation is defined.
- Supplier response/follow-up SLA and delay notification threshold are defined.
- Address lock behavior is defined when admin starts supplier-order work.
- Supplier order evidence fields are defined: supplier order number, ordered address snapshot, orderedByAdminId, expected ship date, supplier response memo.
- MVP shipment model is decided: one shipment per order or multiple shipments.
- Tracking sync vs admin manual correction precedence is defined.
- Fulfillment/shipping, order, admin operations, order-flow, domain-model, requirements, and decision-log docs are updated.

Resolved decisions:

- Supplier order work starts same business day or next business day after payment confirmation.
- Orders paid before 15:00 target same-business-day supplier order work; later, weekend, and holiday orders target next-business-day work.
- Supplier response or expected ship date should be secured within 1 business day after supplier order.
- Customer delay notice is required when expected shipment remains unclear for 2 business days after supplier order.
- Address locks when admin starts supplier order work.
- Address lock uses `supplierOrderStartedAt` and `addressLockedAt`; no new order status is added for MVP.
- MVP shipment model is one shipment per order.
- Partial shipment and split shipment are excluded from MVP.
- Automatic tracking sync can move state forward but must not overwrite admin manual correction or move state backward.

References:

- `docs/policies/fulfillment-shipping-policy.md`
- `docs/policies/order-policy.md`
- `docs/policies/admin-operations-policy.md`
- `docs/order-flow.md`

### DS-27: Finalize privacy, business notice, and legal disclosure policy

Description:

Expand customer notice policy into concrete business disclosure, privacy processing, and retention requirements.

Acceptance criteria:

- Business/operator disclosure fields are defined for footer and customer pages.
- Commerce notice scope is defined: customer center, business registration, mail-order sales registration, product information notice, shipping/AS/return information.
- Privacy processing table is defined: collection item, purpose, retention period, processor/consignee, third-party sharing if any.
- Transactional notifications are separated from optional marketing consent.
- Account deletion, anonymization, legally retained order/payment/claim records, and rejoin behavior are defined.
- Checkout notice versioning includes out-of-stock and checkout notice text.
- Account, legal/customer notice, domain-model, requirements, and decision-log docs are updated.

Resolved decisions:

- Business/operator disclosure includes company name, representative, business registration number, mail-order sales registration number, mail-order sales registration authority, address, customer center phone/email/hours, privacy officer, and hosting provider.
- Commerce notice scope includes customer center, business registration, mail-order sales registration, product information notice, shipping, AS, return, exchange, and claim information.
- Privacy processing table includes collection item, purpose, retention period, processor/consignee, and third-party sharing fields.
- Social login stores provider, provider user id, email, and display name; phone is collected only when needed for order, shipping, or claim.
- Transactional notifications for order, shipping, payment, refund, and claim are separated from optional marketing consent.
- Optional marketing consent is stored per channel with agreement, withdrawal, and policy version.
- Account deletion anonymizes or deletes profile and social account linkage while legally retained records are separated.
- Rejoining with the same social account creates a new user account and does not automatically restore old order history to the customer screen.
- Legal retention starts with 6 months for display/advertising records, 5 years for contract/withdrawal records, 5 years for payment/supply records, and 3 years for complaint/dispute records.

References:

- `docs/policies/account-policy.md`
- `docs/policies/legal-and-customer-notice-policy.md`
- `docs/domain-model.md`

### DS-28: Harden order state transition table and operational audit models

Description:

Prepare the state transition and audit model needed before DS-2 is finalized.

Acceptance criteria:

- Transition table is added with fromStatus, actor, action, guard, side effect, and toStatus.
- Forbidden transitions are defined, including refund without PG success, shipped without tracking number, delivered without shipment, and out-of-stock after shipped except manual claim path.
- `PREPARING_SHIPMENT` is either removed from MVP or given a clear transition.
- Customer order history visibility is separated from checkout/retry screens.
- Notification log model and required notification triggers are defined.
- Order item/detail content version snapshot policy is defined.
- Order-flow, order policy, admin operations, domain-model, requirements, and decision-log docs are updated.

Resolved decisions:

- MVP removes `PREPARING_SHIPMENT`; `SUPPLIER_ORDERED` covers supplier-ordered and waiting-for-tracking state.
- Transition table is defined in order policy with fromStatus, actor, action, guard, side effect, and toStatus.
- Forbidden transitions include refund without PG success, shipped without tracking number, delivered without shipment evidence, out-of-stock after shipped except claim/manual correction path, supplier ordering from payment exception, and expired checkout confirmation.
- Customer order history excludes `PAYMENT_PENDING`, `EXPIRED`, and payment failure states; those belong to checkout/retry surfaces.
- `NotificationLog` is added for transaction notifications.
- Notification triggers include payment completed, payment exception, out of stock, shipment started, delivery completed, delay notice, claim status changed, and refund completed.
- Order item snapshots include product/option names, price, product summary, product detail snapshot reference, and product notice snapshot reference.

References:

- `docs/order-flow.md`
- `docs/policies/order-policy.md`
- `docs/policies/admin-operations-policy.md`
- `docs/domain-model.md`

## Milestone 2: Backend Foundation

### DS-4: Scaffold Spring Boot backend

Description:

Create the Spring Boot backend project and baseline local development setup.

Acceptance criteria:

- Spring Boot project exists.
- PostgreSQL configuration exists.
- Local profile is documented.
- Basic health endpoint exists.
- Test command is documented.

Resolved decisions:

- Spring Boot API scaffold lives in `apps/api`.
- Java target is 21 and the generated Gradle wrapper is committed with the API project.
- PostgreSQL local configuration lives in `apps/api/src/main/resources/application-local.yml`.
- Local PostgreSQL compose file lives in `infra/local/postgres/compose.yml`.
- Basic health endpoint is `GET /api/health`; Actuator health is also enabled at `/actuator/health`.
- Local profile and test command are documented in `apps/api/README.md`.
- API tests run with the `test` profile and an H2 in-memory database in PostgreSQL compatibility mode.

### DS-5: Implement user and admin authentication foundation

Description:

Add customer and admin authentication foundation.

Acceptance criteria:

- User entity exists.
- Role model supports customer and admin.
- Admin APIs are protected.
- Customer APIs are scoped to authenticated user.

Resolved decisions:

- User entity is `UserAccount` mapped to the `users` table.
- Social identity is represented by `SocialProvider` and `providerUserId`; supported providers start with Kakao, Google, and Naver.
- Role model starts with `CUSTOMER` and `ADMIN`.
- User status starts with `ACTIVE` and `DELETED`.
- `GET /api/admin/me` proves admin API protection with `ADMIN` role.
- `GET /api/me` proves customer API scoping through authenticated user id.
- Basic login and form login are disabled to preserve the social-login-only policy.
- Actual Kakao, Google, and Naver OAuth callback/token integration is implemented by DS-30.

### DS-29: Create MVP ERD and API specification docs

Description:

Create the documentation baseline that should exist before DS-6 catalog implementation starts.

Acceptance criteria:

- `docs/erd.md` exists.
- MVP entities and relationships are documented with Mermaid ERD.
- Current implemented `users` table is reflected.
- Planned catalog, cart, order, payment, fulfillment, shipment, refund, claim, policy, and audit entities are included at the right level of detail.
- `docs/api-spec.md` exists.
- MVP APIs are grouped by customer, admin, auth, catalog, cart, checkout/order, payment, fulfillment/shipment, refund/claim, and policy pages.
- Current implemented endpoints are marked as implemented.
- Future endpoints are marked as planned.
- DS-6 catalog API and table expectations are clearly identified.
- Documentation index links to the new ERD and API specification docs.

Resolved decisions:

- `docs/erd.md` is the ERD baseline for MVP implementation.
- `docs/api-spec.md` is the API baseline for MVP implementation.
- Current implemented endpoints and tables are marked separately from planned endpoints and tables.
- DS-6 catalog table and endpoint expectations are explicitly identified before catalog implementation starts.

References:

- `docs/domain-model.md`
- `docs/requirements.md`
- `docs/policies/README.md`
- `docs/linear-backlog.md`
- `apps/api/README.md`

Dependency:

Handle this before DS-6.

### DS-6: Implement catalog domain

Description:

Implement supplier, product, product option, and sales status management.

Acceptance criteria:

- Supplier model exists.
- Product model exists.
- Product option model exists.
- Product image metadata model exists.
- Product detail block model exists.
- Product notice/version source exists for product information notice, shipping, AS, return, and exchange information.
- Product change history writes exist for price, product status, option status, and supplier changes.
- Product and option support sales status instead of real stock quantity.
- Admin can create and update catalog data through APIs.
- Customer can read public product list and detail APIs.
- Public product APIs are permitted by security configuration while admin catalog APIs remain `ADMIN` only.

Resolved decisions:

- Catalog tables are created by `V2__create_catalog.sql`.
- Product and option sellability uses status fields and no stock quantity.
- Public catalog APIs are implemented at `GET /api/products` and `GET /api/products/{productId}`.
- Admin supplier and product APIs are implemented under `/api/admin`.
- Product image and detail block APIs manage URL/object-key metadata; binary upload remains future work.
- Product notice version source is implemented through `product_notices`.
- Product mutations write `product_change_histories`; read API for change history remains planned.
- `SecurityConfig` permits public product APIs and keeps `/api/admin/**` protected by `ADMIN`.

### DS-7: Implement cart domain

Description:

Implement customer cart operations.

Acceptance criteria:

- Customer can add product option to cart.
- Customer can update quantity.
- Customer can remove cart item.
- Cart validates product and option sellability before checkout.

Resolved decisions:

- Cart tables are created by `V3__create_cart.sql`.
- One customer has one current cart.
- Guest cart is excluded from MVP.
- Adding the same product option increases the existing cart item quantity.
- Cart item quantity is limited to 1 through 99.
- Product options can be added only when product status is `ACTIVE` and option status is `ACTIVE`.
- Items already in cart remain when product or option status later becomes unavailable.
- `POST /api/cart/validate` blocks checkout when cart is empty or contains unavailable items.
- Cart responses show current product/option prices; order creation will snapshot final prices.
- Customer cart APIs require `CUSTOMER`; admin access is forbidden.

## Milestone 3: Checkout MVP

### DS-8: Implement order creation

Description:

Create server-side order creation from cart or selected product option.

Acceptance criteria:

- Server calculates all order amounts.
- Order stores item name and price snapshots.
- Order starts in payment pending state.
- Client-submitted total amount is not trusted.

Resolved decisions:

- Checkout/order tables are created by `V4__create_checkout_order.sql`.
- DS-8 creates checkouts from cart only; direct-buy checkout is deferred.
- Checkout request includes shipping address fields directly.
- One `PaymentGroup` is created per checkout.
- Cart items are grouped by supplier as the MVP delivery-group boundary.
- One `PAYMENT_PENDING` order is created per supplier-backed delivery group.
- `OrderItem` stores product name, product summary, option name, unit price, quantity, line amount, product detail version, and product notice version snapshots.
- `PaymentGroup` and orders expire 30 minutes after checkout creation.
- Server calculates all totals and ignores `clientSubmittedTotalAmount`.
- Checkout creation empties the cart after successful order creation.
- Policy confirmation is implemented as `POST /api/checkouts/{checkoutNumber}/policy-confirmation`.
- Separate `delivery_groups` table and direct-buy checkout are deferred.

### DS-9: Integrate PG sandbox payment approval

Description:

Connect the selected PG sandbox and approve payments server-side.

Acceptance criteria:

- Client can request payment.
- Server verifies payment with PG.
- Amount mismatch is rejected.
- Duplicate payment confirmation is idempotent.
- Successful payment moves order to supplier order pending.

Resolved decisions:

- Payment tables are created by `V5__create_payment.sql`.
- Toss confirmation API is implemented at `POST /api/payments/toss/confirm`.
- Toss secret key is read from `payments.toss.secret-key`; secrets are not committed.
- Toss client is behind `TossPaymentsClient` so tests can use a fake client.
- Server verifies checkout ownership, `PAYMENT_PENDING` status, 30-minute expiration, policy confirmation, amount, PG payment key uniqueness, and product/option sellability.
- Successful confirmation creates `Payment(APPROVED)`, records a payment event, moves `PaymentGroup` to `APPROVED`, and moves orders to `SUPPLIER_ORDER_PENDING`.
- Duplicate confirmation with the same payment key and checkout returns the existing payment result.
- Same payment key on a different checkout is rejected as conflict.
- Toss-approved amount mismatch creates a payment exception path with `Payment(CANCEL_REQUIRED)`, `PaymentGroup(PAYMENT_EXCEPTION)`, and `Order(PAYMENT_EXCEPTION)`.
- Automatic PG cancel execution, Toss webhook handling, payment detail API, and admin payment exception retry APIs remain planned.

### DS-10: Implement customer order history

Description:

Expose customer order list and detail screens/API.

Acceptance criteria:

- Customer sees own orders only.
- Order detail includes payment, fulfillment, shipment, and refund summary.
- Customer-facing status does not expose confusing internal states.

Resolved decisions:

- Customer order APIs are implemented at `GET /api/orders` and `GET /api/orders/{orderId}`.
- Customer order APIs require `CUSTOMER`; anonymous access is rejected and admin access is forbidden.
- Customer order list excludes `PAYMENT_PENDING` and `EXPIRED`.
- Customer order detail also rejects non-customer-visible pending/expired orders.
- Customers can read only their own orders.
- Customer-facing responses expose `displayStatus` and do not expose raw internal order status.
- Detail response includes payment group summary, payment summary, shipping address snapshot, order items, and placeholder fulfillment/shipment/refund summaries.
- Same payment group orders include `paymentGroupId` and `checkoutNumber` so the client can group them.

## Milestone 4: Admin Fulfillment

### DS-11: Implement admin order queue

Status: Implemented

Description:

Create the core admin work queue for paid orders waiting for supplier handling.

Acceptance criteria:

- Admin can filter supplier order pending orders.
- Admin can open order detail.
- Admin sees supplier, product option, customer shipping info, and payment summary.
- `PAYMENT_PENDING` and `EXPIRED` orders are excluded from the supplier order queue.
- Implemented by `GET /api/admin/orders` and `GET /api/admin/orders/{orderId}`.

### DS-12: Implement supplier order actions

Status: Implemented

Description:

Allow admin to mark supplier order completion or supplier out-of-stock.

Acceptance criteria:

- Admin can mark supplier ordered.
- Admin can mark out of stock.
- Out-of-stock order enters refund handling flow.
- Status changes are recorded.
- Admin can start supplier order work and lock the shipping address.
- Supplier order completion requires supplier order number evidence and reason.
- Out-of-stock requires reason and moves the order to `OUT_OF_STOCK`.
- Implemented by `POST /api/admin/orders/{orderId}/supplier-work-start`, `POST /api/admin/orders/{orderId}/supplier-order-completed`, and `POST /api/admin/orders/{orderId}/out-of-stock`.

### DS-13: Implement shipment tracking input

Status: Implemented

Description:

Allow admin to enter carrier and tracking number.

Acceptance criteria:

- Admin can enter carrier and tracking number.
- Order enters shipped state.
- Customer can see shipment summary.
- Carrier and tracking number are required.
- Duplicate shipment creation is rejected for the same order.
- Implemented by `POST /api/admin/orders/{orderId}/shipments`; customer order detail now includes shipment summary.

Acceptance criteria:

- Admin can enter carrier and tracking number.
- Order moves to shipped state.
- Customer can view tracking data.

## Milestone 5: Cancellation And Refund

### DS-14: Implement customer cancellation request

Status: Implemented

Description:

Allow customers to request cancellation before shipment.

Acceptance criteria:

- Customer can request cancellation for eligible orders.
- Ineligible orders cannot be cancelled from customer UI.
- Admin can review cancellation requests.
- Self-service cancellation is implemented by `POST /api/orders/{orderId}/cancel`.
- Post-supplier-work cancellation claim is implemented by `POST /api/orders/{orderId}/claims`.
- Admin cancellation review is implemented by `GET /api/admin/claims`, `POST /api/admin/claims/{claimId}/approve`, and `POST /api/admin/claims/{claimId}/reject`.

### DS-15: Implement refund handling

Status: Implemented

Description:

Implement refund records and PG refund/cancel integration.

Acceptance criteria:

- Refund record is created for approved cancellation or out-of-stock.
- PG refund/cancel result is stored.
- Refund queue and PG cancel/retry APIs are implemented by `GET /api/admin/refunds`, `POST /api/admin/refunds/{refundId}/request-pg-cancel`, and `POST /api/admin/refunds/{refundId}/retry`.
- Delivery-group order level partial refund updates payment and payment group to `PARTIALLY_REFUNDED`.
- PG cancel failure keeps the order in `REFUND_REQUESTED` and refund in `RETRY_REQUIRED`.
- Order and payment status are updated consistently.
- Refund completion is visible to customer and admin.

## Milestone 6: Launch Preparation

### DS-16: Add policy pages

Status: Implemented

Description:

Add customer-facing policy pages for shipping, cancellation, refund, and supplier stock risk.

Acceptance criteria:

- Shipping policy page exists at `GET /api/policies/shipping`.
- Cancellation/refund policy page exists at `GET /api/policies/cancellation-refund`.
- Order-after-payment supplier stock risk is clearly disclosed at `GET /api/policies/stock-risk`.
- Policy links are visible from product detail and checkout through `policyLinks`.
- Public policy page access, customer-facing core policy text, product detail links, and checkout links are covered by `PolicyPageApiIntegrationTest`.

### DS-17: Add production readiness baseline

Status: Implemented

Description:

Prepare production deployment and operational baseline.

Acceptance criteria:

- Production environment variables are documented in `docs/production-readiness.md`.
- Logging baseline exists in `application-prod.yml` and `docs/production-readiness.md`.
- Error monitoring plan exists in `docs/production-readiness.md`.
- Backup plan for PostgreSQL is documented in `docs/production-readiness.md`.
- Admin operating checklist is documented in `docs/production-readiness.md`.
- Prod profile, readiness/liveness health probes, and env-based CORS baseline are covered by `ProductionReadinessIntegrationTest`.

## Milestone 7: Backend MVP Completion

### DS-55: Backend MVP completion track

Status: Backlog

Description:

Track backend API, policy, state transition, notification, and audit work remaining after DS-17.

Child issues:

- DS-31 through DS-44 remain tracked in Linear.
- DS-30 is implemented.
- DS-45 is implemented.

### DS-30: Implement social OAuth login and cookie JWT auth

Status: Implemented

Description:

Implement Kakao, Google, and Naver social OAuth login with stateless API authentication.

Acceptance criteria:

- OAuth authorize and callback endpoints exist for Kakao, Google, and Naver.
- Callback exchanges provider code for profile data and creates or finds `UserAccount` by provider and provider user id.
- Login issues a JWT access token in the `ACCESS_TOKEN` HttpOnly cookie.
- Cookie auth reloads the active user and current role from the database for protected APIs.
- Logout clears the access token cookie.
- Basic login, form login, guest checkout, and refresh token rotation remain excluded from DS-30.
- API spec, account policy, decision log, and production readiness docs describe the implemented auth model.

### DS-45: Standardize API error response

Status: Implemented

Description:

Standardize API error responses for frontend handling and operational diagnostics.

Acceptance criteria:

- Common error response schema is implemented with `timestamp`, `status`, `code`, `message`, `path`, and `fields`.
- Validation errors return `VALIDATION_FAILED` with field-level details.
- Domain policy and state guard failures return `BUSINESS_RULE_VIOLATION`.
- Authentication and authorization errors use the same JSON response shape.
- `docs/api-spec.md` and `docs/production-readiness.md` describe the implemented format.
- `ApiErrorResponseIntegrationTest` covers 401, 403, validation, malformed request, not found, and business rule errors.
