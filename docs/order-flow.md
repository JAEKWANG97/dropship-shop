# Order Flow

## Happy Path

```text
Customer selects product option
-> Cart items are grouped by delivery group
-> Customer creates order
-> Order status: PAYMENT_PENDING
-> Customer pays through PG
-> Server verifies payment
-> Payment status: APPROVED
-> Order status: SUPPLIER_ORDER_PENDING
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
-> Customer sees delivery groups instead of supplier names
-> Each delivery group has shipping fee 0
```

## Supplier Out Of Stock

```text
Order status: SUPPLIER_ORDER_PENDING
-> Admin checks supplier
-> Supplier says out of stock
-> Fulfillment status: OUT_OF_STOCK
-> Order status: OUT_OF_STOCK
-> Customer is notified
-> Refund is requested
-> Refund status: COMPLETED
-> Payment status: REFUNDED
-> Order status: REFUNDED
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

## Payment Amount Mismatch

```text
Order status: PAYMENT_PENDING
-> PG says payment succeeded
-> Server compares expected order amount and approved amount
-> Amount mismatch detected
-> Order is not confirmed
-> Payment is cancelled or manually reviewed
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
-> Order status: CANCEL_REQUESTED
-> Admin approves cancellation
-> PG refund/cancel is executed
-> Payment status: CANCELLED or REFUNDED
-> Order status: CANCELLED
```

## Shipping Address Change

```text
Order status: PAYMENT_PENDING
-> Customer can edit checkout address before payment

Order status: SUPPLIER_ORDER_PENDING
-> Customer can directly change shipping address

Order status: SUPPLIER_ORDERED or later
-> Customer cannot directly change shipping address
-> Address change requires customer support/admin manual handling
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
```

## State Rules

- `PAYMENT_PENDING` orders are not real confirmed orders.
- `PAYMENT_PENDING` orders expire 30 minutes after creation.
- Payment approval must be verified by the server before an order leaves `PAYMENT_PENDING`.
- MVP enabled payment methods are card, easy payment, and account transfer through Toss Payments.
- Virtual account, mobile phone payment, and gift certificate payment are excluded from MVP.
- Failed, pending, and expired payment orders are not shown in customer order history.
- `SUPPLIER_ORDER_PENDING` is the main admin work queue.
- One MVP order contains exactly one delivery group.
- Delivery groups are based on supplier, but customer UI should use delivery group wording instead of supplier wording.
- Customers can directly change shipping address only until `SUPPLIER_ORDER_PENDING`.
- `SUPPLIER_ORDERED` means the operator has placed the order with the supplier.
- `OUT_OF_STOCK` must lead to customer notification and refund handling.
- `SHIPPED` requires carrier and tracking number.
- MVP includes automatic carrier tracking sync after carrier and tracking number are entered.
- Automatic tracking sync failure must not block order, payment, or refund operations.
- `REFUNDED` requires a completed refund record.
- Customer-facing order status must be mapped from internal order status instead of exposing internal status directly.

## Risk Points

### Duplicate Payment Callback

PG callbacks or client confirmations can arrive multiple times. Payment approval must be idempotent.

### Client-Side Price Tampering

The server must calculate order amount from product and option data. Never trust client-submitted total price.

### Product Status Changed During Checkout

Before creating the order or confirming payment, the server must re-check that product and option are still sellable.

### Supplier Out Of Stock After Payment

This is expected in this business model. The service must support refund and customer notification instead of treating it as a system error.
