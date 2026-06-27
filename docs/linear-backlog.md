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

## Milestone 1.5: MVP Policy Completion

### DS-18: Finalize Toss Payments method policy

Description:

Finalize which Toss Payments methods are enabled in MVP and which are deferred.

Acceptance criteria:

- MVP payment methods are confirmed: card, easy payment, account transfer.
- Virtual account/bank-transfer-like async payment handling is explicitly deferred.
- Partial cancel support is excluded from MVP.
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
- Partial out-of-stock handling is decided: full-order cancellation/refund in MVP.
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
- Checkout policy acknowledgement checkbox is decided: one integrated checkbox per order.
- Policy versioning scope is decided: policy pages have version/effective date, and order confirmation stores policy versions and confirmation time.
- Customer notification method baseline is decided: email and order detail status display for MVP.
- `docs/policies/legal-and-customer-notice-policy.md` and `docs/decision-log.md` are updated.

References:

- `docs/policies/legal-and-customer-notice-policy.md`
- `docs/policies/catalog-inventory-policy.md`
- `docs/policies/cancellation-refund-policy.md`

## Milestone 1.6: Policy Hardening From Operational Review

These issues come from the multi-perspective policy review for a supplier-based commerce operation. They should be resolved before DS-2 is finalized or backend implementation starts.

### DS-23: Finalize payment exception and refund failure policy

Description:

Close payment and refund edge cases that can create approved payment without a fulfillable or visible order.

Acceptance criteria:

- Payment exception states are defined for amount mismatch, approval after expiration, and approval for unsellable products.
- Automatic full PG cancel behavior is decided for each exception.
- Admin emergency queue behavior is defined when automatic cancel/refund fails.
- Refund lifecycle includes requested, PG cancel requested, processing, completed, failed, retry required, or manual review states.
- Customer-facing statuses are defined for refund processing and refund failure/manual review.
- PG event, payment, and refund audit fields are defined for Toss Payments reconciliation and idempotency.
- Payment, cancellation/refund, order-flow, domain-model, and decision-log docs are updated.

References:

- `docs/policies/payment-policy.md`
- `docs/policies/cancellation-refund-policy.md`
- `docs/order-flow.md`
- `docs/domain-model.md`

### DS-24: Finalize delivery-group checkout payment unit policy

Description:

Decide how payment works when a cart contains multiple delivery groups.

Acceptance criteria:

- MVP payment unit rule is decided. Recommended: PG payment 1 = order 1 = delivery group 1.
- Multi-delivery-group checkout UX is decided: separate payment per order or block combined checkout.
- Failure behavior is defined when one delivery-group payment succeeds and another fails.
- Order number and customer order history display rules are defined for split delivery-group checkout.
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

### DS-5: Implement user and admin authentication foundation

Description:

Add customer and admin authentication foundation.

Acceptance criteria:

- User entity exists.
- Role model supports customer and admin.
- Admin APIs are protected.
- Customer APIs are scoped to authenticated user.

### DS-6: Implement catalog domain

Description:

Implement supplier, product, product option, and sales status management.

Acceptance criteria:

- Supplier model exists.
- Product model exists.
- Product option model exists.
- Product and option support sales status instead of real stock quantity.
- Admin can create and update catalog data through APIs.

### DS-7: Implement cart domain

Description:

Implement customer cart operations.

Acceptance criteria:

- Customer can add product option to cart.
- Customer can update quantity.
- Customer can remove cart item.
- Cart validates product and option sellability before checkout.

## Milestone 3: Checkout MVP

### DS-8: Implement order creation

Description:

Create server-side order creation from cart or selected product option.

Acceptance criteria:

- Server calculates all order amounts.
- Order stores item name and price snapshots.
- Order starts in payment pending state.
- Client-submitted total amount is not trusted.

### DS-9: Integrate PG sandbox payment approval

Description:

Connect the selected PG sandbox and approve payments server-side.

Acceptance criteria:

- Client can request payment.
- Server verifies payment with PG.
- Amount mismatch is rejected.
- Duplicate payment confirmation is idempotent.
- Successful payment moves order to supplier order pending.

### DS-10: Implement customer order history

Description:

Expose customer order list and detail screens/API.

Acceptance criteria:

- Customer sees own orders only.
- Order detail includes payment, fulfillment, shipment, and refund summary.
- Customer-facing status does not expose confusing internal states.

## Milestone 4: Admin Fulfillment

### DS-11: Implement admin order queue

Description:

Create the core admin work queue for paid orders waiting for supplier handling.

Acceptance criteria:

- Admin can filter supplier order pending orders.
- Admin can open order detail.
- Admin sees supplier, product option, customer shipping info, and payment summary.

### DS-12: Implement supplier order actions

Description:

Allow admin to mark supplier order completion or supplier out-of-stock.

Acceptance criteria:

- Admin can mark supplier ordered.
- Admin can mark out of stock.
- Out-of-stock order enters refund handling flow.
- Status changes are recorded.

### DS-13: Implement shipment tracking input

Description:

Allow admin to enter carrier and tracking number.

Acceptance criteria:

- Admin can enter carrier and tracking number.
- Order moves to shipped state.
- Customer can view tracking data.

## Milestone 5: Cancellation And Refund

### DS-14: Implement customer cancellation request

Description:

Allow customers to request cancellation before shipment.

Acceptance criteria:

- Customer can request cancellation for eligible orders.
- Ineligible orders cannot be cancelled from customer UI.
- Admin can review cancellation requests.

### DS-15: Implement refund handling

Description:

Implement refund records and PG refund/cancel integration.

Acceptance criteria:

- Refund record is created for approved cancellation or out-of-stock.
- PG refund/cancel result is stored.
- Order and payment status are updated consistently.
- Refund completion is visible to customer and admin.

## Milestone 6: Launch Preparation

### DS-16: Add policy pages

Description:

Add customer-facing policy pages for shipping, cancellation, refund, and supplier stock risk.

Acceptance criteria:

- Shipping policy page exists.
- Cancellation/refund policy page exists.
- Order-after-payment supplier stock risk is clearly disclosed.
- Policy links are visible from product detail and checkout.

### DS-17: Add production readiness baseline

Description:

Prepare production deployment and operational baseline.

Acceptance criteria:

- Production environment variables are documented.
- Logging baseline exists.
- Error monitoring plan exists.
- Backup plan for PostgreSQL is documented.
- Admin operating checklist is documented.
