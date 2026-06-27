# Order Flow

## Happy Path

```text
Customer selects product option
-> Cart items are grouped by delivery group
-> Customer creates payment group
-> System creates one PAYMENT_PENDING order per delivery group
-> Customer confirms order notice checkbox
-> Customer pays through PG
-> Server verifies payment
-> Payment status: APPROVED
-> All delivery-group orders in the payment group move to SUPPLIER_ORDER_PENDING
-> Admin starts supplier order work
-> Supplier order started time is recorded
-> Shipping address is locked
-> Admin places supplier order manually
-> Fulfillment status: ORDERED
-> Order status: SUPPLIER_ORDERED
-> Admin enters carrier and tracking number
-> Shipment status: SHIPPED
-> Order status: SHIPPED
-> System syncs carrier tracking status
-> Shipment delivered
-> Order status: DELIVERED
```

## Multi Delivery Group Checkout

```text
Cart contains products from multiple suppliers
-> System groups cart items by delivery group
-> Each delivery group creates a separate order
-> One payment group contains all delivery-group orders in the checkout
-> Customer pays once for the whole cart
-> Customer sees delivery groups instead of supplier names
-> Each delivery group has shipping fee 0
```

## Supplier Out Of Stock For Single-Order Payment Group

```text
Order status: SUPPLIER_ORDER_PENDING
-> Admin checks supplier
-> Supplier says out of stock
-> Fulfillment status: OUT_OF_STOCK
-> Order status: OUT_OF_STOCK
-> Customer is notified
-> Refund is requested for this order amount
-> Refund status: PG_CANCEL_REQUESTED
-> PG full cancel/refund succeeds because the payment group contains only this order
-> Refund status: COMPLETED
-> Payment status: REFUNDED
-> Order status: REFUNDED
```

## Supplier Out Of Stock In Multi Delivery Group Payment

```text
Payment group contains:
  - Order A: delivery group A, 60,000
  - Order B: delivery group B, 40,000
PG payment total: 100,000

Order A supplier order succeeds
-> Order A continues fulfillment

Order B supplier says out of stock
-> Order B status: OUT_OF_STOCK
-> Refund is requested for Order B amount only
-> Refund status: PG_CANCEL_REQUESTED
-> PG partial cancel/refund succeeds for 40,000
-> Order B status: REFUNDED
-> Order A remains active
```

## Payment Failure

```text
Order status: PAYMENT_PENDING
-> PG payment fails
-> Payment status: FAILED
-> Order remains unconfirmed
-> Customer can retry before order expiration or create a new order
-> Order is not shown in customer order history
```

## Payment Pending Expiration

```text
Order status: PAYMENT_PENDING
-> 30 minutes pass without verified payment approval
-> Order expires
-> Customer must create a new order to retry checkout
-> Order is not shown in customer order history
```

## Checkout Policy Confirmation

```text
Order status: PAYMENT_PENDING
-> Customer reviews order items, amount, shipping address, shipping policy, cancellation/refund policy, out-of-stock notice
-> Customer checks one integrated confirmation checkbox
-> System records confirmed policy versions and confirmed time on the payment group
-> Payment request can start
```

## Payment Amount Mismatch

```text
Order status: PAYMENT_PENDING
-> PG says payment succeeded
-> Server compares expected payment group amount and approved amount
-> Amount mismatch detected
-> Order status: PAYMENT_EXCEPTION
-> Payment status: CANCEL_REQUIRED
-> System attempts immediate full PG cancel
-> If PG cancel succeeds, payment status becomes CANCELLED and customer sees payment cancel completed
-> If PG cancel fails, payment status becomes CANCEL_FAILED and admin emergency review is required
```

## Payment Exception

```text
PG payment is approved
-> Server cannot confirm order because one validation failed:
   - order expired
   - amount mismatch
   - product or option is no longer sellable
   - duplicate or conflicting PG payment key
   - PG confirmation error
-> Order status: PAYMENT_EXCEPTION
-> Payment exception reason is recorded
-> Supplier order is blocked
-> System attempts immediate full PG cancel with idempotency key

PG cancel succeeds
-> Payment status: CANCELLED
-> Customer sees payment cancel completed

PG cancel fails
-> Payment status: CANCEL_FAILED
-> Admin emergency payment queue item is created
-> Customer sees payment review or cancel processing status
```

## Deferred Virtual Account Flow

```text
Virtual account / bank-transfer-like async payment is not included in MVP.
If added later, the order flow needs separate states for account issued, waiting for deposit, deposit completed, and deposit expired.
```

## Customer Cancellation Before Supplier Order

```text
Order status: SUPPLIER_ORDER_PENDING
-> Customer requests cancellation
-> Order status: REFUND_REQUESTED
-> Refund status: REQUESTED
-> Refund is requested for the cancelled order amount
-> Refund status: PG_CANCEL_REQUESTED
-> If the payment group has no other active orders, PG full cancel/refund succeeds
-> If the payment group has other active orders, PG partial cancel/refund succeeds
-> Refund status: COMPLETED
-> Payment status: REFUNDED or PARTIALLY_REFUNDED
-> Order status: REFUNDED

PG cancel/refund fails
-> Refund status: FAILED or RETRY_REQUIRED
-> Order remains REFUND_REQUESTED
-> Admin retry or manual review is required
```

## Customer Cancellation After Supplier Order

```text
Order status: SUPPLIER_ORDERED or later
-> Customer cannot directly cancel order
-> Customer can submit cancellation/return/exchange inquiry
-> Admin checks supplier/shipment situation
-> Admin manually approves or rejects follow-up action
```

## Shipping Address Change

```text
Order status: PAYMENT_PENDING
-> Customer can edit checkout address before payment

Order status: SUPPLIER_ORDER_PENDING
-> If addressLockedAt is empty, customer can directly change shipping address
-> If admin started supplier order work and addressLockedAt is set, customer cannot directly change shipping address
-> Address change requires customer support/admin manual handling

Order status: SUPPLIER_ORDERED or later
-> Customer cannot directly change shipping address
-> Address change requires customer support/admin manual handling
```

## Supplier Order SLA And Delay Notice

```text
Order status: SUPPLIER_ORDER_PENDING
-> Admin starts supplier order work on the same business day or next business day
-> supplierOrderStartedAt and addressLockedAt are recorded
-> Admin places supplier order manually
-> Supplier order number, ordered address snapshot, expected ship date, and supplier memo are recorded

Supplier response or expected ship date is not secured within 1 business day
-> Order remains in admin follow-up queue

Expected shipment remains unclear for 2 business days after supplier order
-> Customer delay notice is sent
-> Admin continues supplier follow-up or handles out-of-stock if confirmed
```

## Shipment Tracking Sync

```text
Order status: SHIPPED
-> System periodically syncs carrier tracking status
-> Tracking says delivered
-> Shipment status: DELIVERED
-> Order status: DELIVERED

Tracking sync failure
-> Keep current shipment/order status
-> Record sync failure
-> Retry later or allow admin manual correction

Admin manual correction
-> Admin enters correction reason
-> Manual correction is recorded
-> Later automatic tracking sync cannot move the shipment backward or overwrite the admin correction without a valid forward transition
```

## Return Or Exchange After Delivery

```text
Order status: DELIVERED
-> Customer submits return/exchange inquiry
-> Admin reviews manually
-> If refund is approved, delivery-group order level refund is processed in MVP
```

## Admin Manual Correction

```text
Admin detects wrong operational state or shipment information
-> Admin selects a defined correction action
-> Admin enters correction reason
-> System validates that the correction action is allowed
-> System records admin action history and order status history
-> Customer-facing status is recalculated from the corrected internal state
```

## State Rules

- `PAYMENT_PENDING` orders are not real confirmed orders.
- `PAYMENT_PENDING` orders expire 30 minutes after creation.
- Payment request requires checkout policy confirmation.
- Checkout policy confirmation is recorded per payment group with policy versions and confirmation time.
- Payment approval must be verified by the server before an order leaves `PAYMENT_PENDING`.
- Payment approval verification requires order status `PAYMENT_PENDING`, unexpired order, completed checkout policy confirmation, amount match, unused/conflict-free PG payment key, and sellable product/option status.
- If PG approves payment but order confirmation fails, the order moves to `PAYMENT_EXCEPTION` and supplier ordering is blocked.
- Payment exceptions attempt immediate full PG cancel.
- Immediate full PG cancel applies to payment exceptions before order confirmation, not to normal delivery-group order level refund after a payment group is confirmed.
- Failed automatic PG cancel creates an admin emergency review item and must not be hidden from the customer.
- MVP enabled payment methods are card, easy payment, and account transfer through Toss Payments.
- Virtual account, mobile phone payment, and gift certificate payment are excluded from MVP.
- Failed, pending, and expired payment orders are not shown in customer order history.
- `SUPPLIER_ORDER_PENDING` is the main admin work queue.
- One MVP order contains exactly one delivery group.
- One MVP payment group can contain multiple delivery-group orders.
- Customer can pay once for all delivery groups in the checkout.
- Delivery groups are based on supplier, but customer UI should use delivery group wording instead of supplier wording.
- Customers can directly change shipping address only until `SUPPLIER_ORDER_PENDING`.
- Customer direct shipping address change is blocked once `addressLockedAt` is set by supplier order work start.
- `SUPPLIER_ORDERED` means the operator has placed the order with the supplier.
- Supplier order work start does not add a new order status in MVP; it is tracked with `supplierOrderStartedAt` and `addressLockedAt`.
- Supplier order work should start on the same business day or next business day after payment confirmation.
- Supplier response or expected ship date should be secured within 1 business day after supplier order.
- Customer delay notice is required when expected shipment remains unclear for 2 business days after supplier order.
- Customer direct cancellation is allowed only until `SUPPLIER_ORDER_PENDING`.
- After `SUPPLIER_ORDERED`, cancellation/return/exchange is handled manually by admin.
- `OUT_OF_STOCK` must lead to customer notification and refund handling.
- `SHIPPED` requires carrier and tracking number.
- MVP includes automatic carrier tracking sync after carrier and tracking number are entered.
- Automatic tracking sync failure must not block order, payment, or refund operations.
- MVP uses one shipment per order and excludes partial shipment or split shipment.
- Automatic tracking sync can move shipment state forward, but must not overwrite admin manual correction or move shipment state backward.
- `REFUNDED` requires a completed refund record.
- Paid orders can move to `REFUNDED` only after PG cancel/refund succeeds.
- PG cancel/refund failure must keep the order in a processing or review-required state, not a completed state.
- MVP supports partial cancellation/refund only at delivery-group order level within a payment group.
- Product, option, or quantity-level partial cancellation/refund inside one delivery-group order is excluded from MVP.
- Customer-facing order status must be mapped from internal order status instead of exposing internal status directly.
- Admin order state changes must use defined actions, not arbitrary status dropdown changes.
- Admin can only progress an order when the next operational step is confirmed.
- Automatic state rollback is excluded from MVP.
- Wrong state changes are handled by admin correction actions with required reason and history.

## Risk Points

### Duplicate Payment Callback

PG callbacks or client confirmations can arrive multiple times. Payment approval must be idempotent.

Payment and refund events must be recorded with idempotency keys and provider identifiers so duplicate confirmations or retries do not create multiple state transitions.

### Client-Side Price Tampering

The server must calculate order amount from product and option data. Never trust client-submitted total price.

### Product Status Changed During Checkout

Before creating the order or confirming payment, the server must re-check that product and option are still sellable.

### Supplier Out Of Stock After Payment

This is expected in this business model. The service must support refund and customer notification instead of treating it as a system error.
