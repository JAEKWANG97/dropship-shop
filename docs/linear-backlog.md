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

- Admin role model is decided.
- Admin account creation flow is decided.
- Admin action history scope is decided.
- Refund action reason requirement is decided.
- State rollback support is decided.
- `docs/policies/admin-operations-policy.md` and `docs/decision-log.md` are updated.

References:

- `docs/policies/admin-operations-policy.md`
- `docs/policies/account-policy.md`
- `docs/policies/order-policy.md`

### DS-22: Finalize legal and customer notice policy

Description:

Finalize where and how customer-facing notices and policy acknowledgements are shown in MVP.

Acceptance criteria:

- Supplier out-of-stock notice locations are decided.
- Checkout policy acknowledgement checkbox is decided.
- Policy versioning scope is decided.
- Customer notification method baseline is decided.
- `docs/policies/legal-and-customer-notice-policy.md` and `docs/decision-log.md` are updated.

References:

- `docs/policies/legal-and-customer-notice-policy.md`
- `docs/policies/catalog-inventory-policy.md`
- `docs/policies/cancellation-refund-policy.md`

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
