# Order Flow

Current payment path: direct bank transfer with manual admin deposit confirmation.

현재 구현 흐름은 기존 Coreable 수동 발주와 Domeggook 자동 발주를 설명한다. 공급처 포털 흐름은 `B-100`~`B-105`의 Planned 기준이며, 기존 주문의 상태·발주·단일 Shipment 의미를 바꾸지 않고 별도 channel과 expand-contract 방식으로 추가한다.

## Current Legacy/Domeggook Happy Path — Implemented

```text
Customer selects product option
-> Product quantity must meet the current minimum, order-unit multiple, and maximum 99 rules
-> Cart items are grouped by delivery group
-> Checkout revalidates every saved quantity without automatically changing it
-> Customer creates payment group
-> System creates one PAYMENT_PENDING order per delivery group
-> Customer confirms order notice checkbox
-> Customer sees bank transfer account, amount, depositor name, and deposit deadline
-> Customer deposits the checkout amount
-> Admin confirms actual deposit
-> Payment status: APPROVED
-> All delivery-group orders in the payment group move to SUPPLIER_ORDER_PENDING
-> Domeggook source snapshot order is queued for automated purchase
-> Supplier item, option, source price, current MOQ/order step, shipping, and e-money are revalidated
-> System places the supplier order with prefunded e-money
-> Non-Domeggook order stays on the existing manual supplier-order path
-> Fulfillment status: ORDERED
-> Order status: SUPPLIER_ORDERED
-> Domeggook sync supplies carrier and tracking number, or admin enters them for a manual order
-> Shipment status: SHIPPED
-> Order status: SHIPPED
-> System syncs carrier tracking status
-> Shipment delivered
-> Order status: DELIVERED
```

DS-8 backend implementation notes:

- Checkout creation is cart-based.
- Cart add, combined add, quantity update, and checkout creation enforce the current product MOQ and order unit.
- If MOQ changes after an item was saved, the cart keeps the customer's quantity, explains the mismatch, and blocks checkout until the customer edits or removes it.
- The server creates one payment group and one `PAYMENT_PENDING` order per supplier-backed delivery group.
- The checkout request includes the shipping address directly.
- Order items snapshot product name, summary, option name, price, product detail version, and product notice version.
- The cart is emptied after successful checkout creation.
- Direct-buy checkout is deferred.

B-041 bank-transfer implementation notes:

- Checkout creation still creates one payment group and one `PAYMENT_PENDING` order per supplier-backed delivery group.
- `PAYMENT_PENDING` means deposit waiting.
- Checkout detail shows bank name, account number, account holder, total amount, depositor name, deposit deadline, and cash receipt notice.
- Admin deposit confirmation requires the actual depositor name, actual amount, received time, transaction reference, and reason. It creates a `BANK_TRANSFER` payment with `providerPaymentKey = BANK-{checkoutNumber}`, marks the payment group `APPROVED`, and moves included orders to `SUPPLIER_ORDER_PENDING` only when the actual amount exactly equals the checkout total.
- Admin unpaid cancellation moves pending orders to `CANCELLED`.
- The current admin deposit-mismatch action keeps the order pending and records a memo for operations. Planned B-102 replaces this disposition for an identified non-equal receipt with a terminal payment-group refund flow while preserving historical rows.
- Bank-transfer refunds are completed only after an admin records the actual manual refund completion with the recipient bank/account/holder, transferred time, transaction reference, and reason.

B-072 supplier purchase notes:

- Only deposit-confirmed orders with complete supplier source snapshots enter the automated queue.
- The customer payment and Domeggook e-money purchase are separate money records.
- A known failure may be retried. A timeout or lost response must be reconciled against recent supplier orders first.
- Supplier cancellation does not complete the customer refund; the admin records the bank refund separately.
- Domeggook carrier/tracking data creates the existing shipment record when one unambiguous shipment is returned.

DS-10 backend implementation notes:

- Customer order history starts after bank-transfer deposit confirmation.
- Normal `PAYMENT_PENDING` and `EXPIRED` orders are excluded from customer order history.
- Customer APIs return display statuses instead of raw internal order statuses.
- Order detail includes implemented payment, shipment, fulfillment, and refund summaries.

## Supplier Portal Slice Map — Planned

| Slice | Order-flow impact |
| --- | --- |
| `B-100` | 공급처 신청, Coreable 승인, 1회용 이메일 초대, Kakao-only 담당자 연결. 주문 상태 변경 없음 |
| `B-101` | 개별 상품 등록, 기본 옵션, 자동 공개/위험상품 검토, Coreable 고객가 계산. 주문 상태 변경 없음 |
| `B-102` | `TRACKED` 재고, checkout 24시간 예약·만료, 늦은 입금 재확보 |
| `B-103` | 입금확인 즉시 수락 단계 없는 출고 요청, 주소 잠금, 최소 PII, 이메일 알림 |
| `B-104` | 복수 Shipment와 수량 allocation, `TRACKING_REGISTERED`, 공식 택배사 링크 |
| `B-105` | 송장 전 배송 그룹 전체 품절 보고, supplier facts, Coreable 환불 경계 |

`B-102` inventory migration과 checkout guard는 supplier portal release의 필요조건이지만 충분조건은 아니다. B-100~B-105와 privacy, operational email, 외부 공급처 계약, production feature-flag gate가 모두 준비될 때까지 외부 supplier route와 portal 상품 구매를 열지 않는다.

### Tracked Checkout And Late Deposit — Planned (`B-102`)

```text
Customer creates checkout
-> Existing COREABLE option: inventory mode UNTRACKED, current sellability validation remains
-> Portal option created before B-102: migrate to TRACKED/onHand=0; later portal options default TRACKED and may explicitly select UNTRACKED
-> System locks every affected Supplier, Product and ProductOption by id in that order, including UNTRACKED options
-> Recheck Supplier ACTIVE, product/option/compliance and supplier availability under those locks; require a time-valid portal contract only for Suppliers represented by portal-origin items
-> All delivery-group quantities are reserved in one transaction
-> Any shortage rolls back the whole checkout
-> Reservation status: HELD
-> Deposit deadline: checkout creation + 24 hours

Deposit confirmed before expiry
-> Admin records and verifies the actual deposit time and exact amount
-> Lock PaymentGroup -> Suppliers -> Products -> all ProductOptions -> Orders/Fulfillments and recheck current saleability and immutable inventory-mode snapshots
-> Actual amount differs from PaymentGroup total: record PAYMENT_AMOUNT_MISMATCH first, release HELD once, create one actual-amount PaymentGroup Refund, move every Order to REFUND_REQUESTED, stop
-> If actual deposit time is within the deadline and every guard passes, consume every HELD reservation exactly once
-> onHandQuantity and reservedQuantity decrease
-> Continue to the portal fulfillment request
-> First reason priority: if current saleability/contract or inventory mode changed, release HELD once and use SALE_UNAVAILABLE_AT_DEPOSIT even when the timestamp is also late
-> Otherwise, if actual deposit time is after the deadline even though scheduler has not expired the group, release HELD once and use LATE_DEPOSIT_EXCEPTION

No confirmed deposit at expiry
-> Scheduler locks the PaymentGroup and rechecks status
-> Release every HELD reservation exactly once
-> Reservation status: RELEASED
-> Payment group and orders: EXPIRED

Admin finds a deposit after expiry
-> Record the actual deposit time
-> Actual amount differs from PaymentGroup total: do not reacquire stock; create the same single PaymentGroup Refund and stop
-> If the actual deposit time was within the original deadline, recheck current saleability and immutable mode snapshots, then reacquire every TRACKED quantity atomically
-> Current saleability or mode mismatch: rollback tentative stock and use SALE_UNAVAILABLE_AT_DEPOSIT
-> Reacquisition succeeds: approve deposit and continue
-> Reacquisition fails after saleability passes: record received BANK_TRANSFER Payment once and LATE_DEPOSIT_EXCEPTION evidence, no supplier exposure
-> Actual deposit time was after the deadline: record received Payment and PAYMENT_EXCEPTION evidence once without reacquisition
-> The same command creates one reason-matched SALE_UNAVAILABLE_AT_DEPOSIT or LATE_DEPOSIT_EXCEPTION Refund per delivery-group Order
-> Orders move to REFUND_REQUESTED; customer sees "입금 확인 및 환불 처리 중"
-> Actual bank refund evidence completes orders and PaymentGroup as REFUNDED
```

`PAYMENT_EXCEPTION` has no action that resumes the normal order. The PaymentGroup can move from exception through partial/full refund accounting, while the supplier queue never sees these orders.

두 조건이 동시에 실패하면 reason 우선순위는 `SALE_UNAVAILABLE_AT_DEPOSIT`이 먼저다. 현재 saleability/time-valid contract/mode가 모두 통과한 경우에만 늦은 실제 시각 또는 재고 재확보 실패를 `LATE_DEPOSIT_EXCEPTION`으로 분류한다.

Supplier inventory PUT은 immutable subject option id, nullable live Option FK, unique `(subjectOptionId, idempotencyKey)`와 product/option path를 묶은 request hash를 사용한다. 동일 retry는 허용된 미제출 DRAFT Option 삭제 뒤에도 최초 canonical inventory projection을 반환하고 다른 path/payload의 key 재사용은 거절한다. 성공은 before/after 수량·모드·availability와 reserved snapshot을 append-only history에 남기며, checkout reservation 변화는 OrderItem 증적을 canonical로 유지한다.

Checkout은 영향받는 Supplier, Product, 모든 ProductOption을 각 id 순서로 잠그며 UNTRACKED-only checkout도 생략하지 않는다. 만료와 정상·늦은 입금은 공유 전역 순서 `PaymentGroup -> Supplier(id) -> Product(id) -> ProductOption(id, UNTRACKED 포함) -> Order/Fulfillment(id)`를 따른다. lifecycle-only 명령은 Supplier 뒤 Fulfillment만 잠그고 Product/Option을 잡지 않는다. Catalog/inventory saleability writer는 필요한 Supplier 뒤 Product -> Option 순서를 사용하고 Product를 잡은 뒤 Supplier를 역순으로 획득하지 않는다. 입금 처리는 잠금 아래 거래 상태, portal-origin item에 한정한 time-valid contract, portal/manager, 상품·옵션·compliance·availability와 immutable mode snapshot을 다시 검사한 뒤 routing 또는 reason별 exception을 결정한다. Duplicate scheduler/admin 요청은 inventory를 두 번 소비·해제하지 않는다.

### Immediate Portal Fulfillment — Planned (`B-103`)

```text
Admin confirms the deposit and tracked inventory consumption succeeds
-> For each delivery-group Order, read immutable OrderItem management-channel snapshots
-> All items are portal-origin and Supplier trade/portal/manager plus time-valid VERIFIED contract are valid: create SUPPLIER_PORTAL, owner SUPPLIER
-> Store requestedAt, piiAccessCutoffAt=requestedAt+60 days and addressLockedAt in the same transaction
-> Show immediately in that supplier queue; no accept/reject step
-> Dispatch an email containing only order number and portal link after revalidating current verified contact, active portal/manager and time-valid VERIFIED contract; mismatch becomes SKIPPED
-> All items are portal-origin but KEEP leaves sales active while portal/manager is unavailable and contract remains time-valid: create COREABLE_MANUAL, owner COREABLE
-> COREABLE_MANUAL fallback never enters a supplier queue and creates no supplier email
-> Mixed or legacy delivery group: preserve existing snapshot-based COREABLE_MANUAL/DOMEGGOOK_API routing

Supplier opens order list
-> List contains order number, supplier-facing status, own item summary, quantity, requested time
-> Raw SUPPLIER_ORDER_PENDING is mapped to FULFILLMENT_REQUESTED; there is no accept action
-> List contains no customer PII
-> Require a time-valid VERIFIED supplier contract; terminal/overdue contract closes paid-work access

Supplier opens own order detail
-> Show only recipient name/phone, postal code/address and delivery memo required for this delivery
-> Hide customer account, payment, bank, refund, other supplier and internal admin data
-> Read stored monotonic piiAccessCutoffAt initialized to requestedAt +60 days
-> Every tracking registration stores min(current cutoff, registeredAt +30 days); void/replacement never extends it
-> An OUT_OF_STOCK/CANCELLED/REFUND_REQUESTED/REFUNDED order is TERMINAL_MASKED immediately regardless of non-voided Shipment presence
-> Contract EXPIRED/REVOKED hands open work to COREABLE and closes detail regardless of an older Claim grant
-> At cutoff and after (now >= cutoff), one-character name becomes *; longer names become first Unicode code point + fixed **
-> Normalize phone to digits; mask all when length <=4, otherwise keep only last four digits
-> Return postal code, address and memo as null with piiAccessLevel=MASKED
-> Apply Cache-Control: no-store
-> Record actor, order, access basis and time without copying PII or the response body into the log
-> At cutoff, an idempotent scheduler takes open SUPPLIER-owned work over to COREABLE with PII_CUTOFF_REACHED evidence
```

After the portal request is visible, customer self-service cancellation and address change are blocked. Coreable handles cancellation as a claim and remains responsible for payment, refund and customer communication.

An earlier admin `portal-takeover` requires idempotency key, request hash and reason. Identical replay returns the first result, different payload reuse is rejected, and actor, owner before/after and time remain in append-only command history. Neither scheduler nor admin takeover auto-returns ownership after portal reactivation.

### Portal Tracking And Multiple Shipments — Planned (`B-104`)

```text
Supplier registers carrier code, tracking number and positive item allocations
-> Server locks the order and order items
-> Cumulative allocation for each item must not exceed ordered quantity
-> First tracking registration may omit allocation and receive all currently unallocated quantities
-> Additional tracking registrations require explicit allocation
-> Server generates the official carrier tracking URL
-> Atomically store piiAccessCutoffAt=min(current cutoff, registeredAt+30 days)
-> Shipment/order display state: TRACKING_REGISTERED
-> Tracking registration is not proof of pickup or in-transit delivery

Supplier corrects its own non-delivered Shipment
-> Replace carrier/tracking only with idempotency key, optimistic version guard and required reason
-> Preserve before/after history; do not delete evidence
-> Allocation error requires Coreable void and a new Shipment registration

Coreable finds a duplicate or invalid Shipment before delivery
-> Mark Shipment VOIDED with reason and actor
-> Release its allocations for replacement registration
-> Do not extend or recompute the stored PII cutoff; replacement registration may only shorten it again
-> If no non-voided Shipment remains, return to SUPPLIER_ORDER_PENDING/FULFILLMENT_REQUESTED; otherwise recalculate TRACKING_REGISTERED

Coreable continues handed-over SUPPLIER_PORTAL work
-> Use the admin plural portal-shipment route backed by the same allocation/idempotency service
-> Keep channel SUPPLIER_PORTAL and owner COREABLE; COREABLE_MANUAL fallback remains on the legacy admin path

Coreable verifies delivery on the official carrier page
-> Record evidenceObservedAt, reason and ADMIN actor for that Shipment
-> Recalculate the Order aggregate

Coreable discovers its planned manual delivery completion was wrong
-> Require idempotency key, expected version and reason
-> Allow REOPEN_TRACKING or CORRECT_DELIVERED_AT only before any later Claim/Refund exists
-> Preserve original delivery evidence; return 409 and use incident/claim handling when a dependency exists
-> Notify customer when the visible state rolls back

Customer opens official tracking link
-> Customer shipment projection shows carrier, tracking number, server-generated officialTrackingUrl and `송장 등록 · 배송조회 가능`
-> No live carrier-status API is called by the portal MVP

All quantities are allocated to non-voided Shipments and every one has Coreable delivery evidence
-> Order status: DELIVERED
-> Claim delivery basis: max(non-voided Shipment.deliveredAt)
```

Existing Domeggook tracking sync and existing single-Shipment responses remain compatible. New responses may add `shipments[]` and allocation completion without deleting the legacy `shipment` field during the compatibility window.

### Portal Shortage And Supplier Facts — Planned (`B-105`)

```text
No Shipment has ever existed for the supplier-owned portal fulfillment request
-> Supplier submits a whole delivery-group shortage report with idempotency key/request hash
-> Service looks up duplicate key/hash before checking operationalOwner
-> First request creates ShortageReport REPORTED and immediately hands ownership to COREABLE
-> Preserve Order, Claim and Refund exactly; do not notify the customer or create a refund queue yet
-> Identical retry after handover returns the first report; changed payload with the same key returns 409

Any Shipment, including one later VOIDED, already exists
-> Supplier shortage report is rejected

Coreable approves the REPORTED shortage
-> Call the existing admin out-of-stock/refund service
-> On successful service completion, mark the report APPROVED
-> Only that service moves the Order to OUT_OF_STOCK and creates customer notice/refund handling

Coreable rejects the REPORTED shortage
-> Report becomes REJECTED but operationalOwner remains COREABLE
-> Supplier sees nextAction CONTACT_COREABLE and cannot resume shipment/shortage mutations

Coreable creates a safe claim task for the owning supplier
-> Supplier list/detail correlates it only with orderNumber, own item/option summary and a direct supplier-order link; no PII
-> Supplier appends only the requested type of shipment-stop result, return instructions, return receipt or inspection result
-> Supplier fact does not directly change Claim, Order or Refund state
-> Coreable alone approves/rejects claims and completes the actual bank-transfer refund
```

Product-, option- or quantity-level shortage refund inside one delivery-group order remains unsupported.

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
-> Admin approves refund
-> Admin transfers the refund to the customer account
-> Admin records actual refund completion
-> Refund status: COMPLETED
-> Payment status: REFUNDED
-> Order status: REFUNDED
```

## Supplier Out Of Stock In Multi Delivery Group Payment

```text
Payment group contains:
  - Order A: delivery group A, 60,000
  - Order B: delivery group B, 40,000
Payment group total: 100,000

Order A supplier order succeeds
-> Order A continues fulfillment

Order B supplier says out of stock
-> Order B status: OUT_OF_STOCK
-> Refund is requested for Order B amount only
-> Admin approves refund
-> Admin transfers 40,000 to the customer account
-> Admin records actual refund completion
-> Order B status: REFUNDED
-> Order A remains active
```

## Payment Failure

```text
Order status: PAYMENT_PENDING
-> Customer does not deposit, or deposit cannot be matched
-> Admin records deposit mismatch memo or unpaid cancellation
-> Order remains unconfirmed or becomes CANCELLED
-> Customer creates a new order if purchase is still wanted
-> Order is not shown in customer order history
```

The block above is the current B-068 behavior. After the B-102 admin-web cutover, memo-only order mutations end: no receipt uses unpaid cancellation, an unattributed bank transaction stays outside Order mutation until matched, and an identified positive amount mismatch follows this flow:

```text
PaymentGroup status: PAYMENT_PENDING, EXPIRED, or qualifying unpaid CANCELLED
-> CANCELLED is allowed only when every included Order was cancelled for non-payment and no received Payment, Refund or Fulfillment exists
-> Admin records actual depositor, non-equal amount, received time, transaction reference and reason
-> Persist one BANK_TRANSFER Payment and PaymentGroup as PAYMENT_EXCEPTION exactly once
-> Keep expected checkout total unchanged; set refundable amount to the actual receipt
-> Release any remaining HELD reservation once; never reacquire or consume
-> Create one PAYMENT_GROUP / PAYMENT_AMOUNT_MISMATCH Refund for the actual receipt
-> Every included Order moves to REFUND_REQUESTED
-> No Fulfillment, supplier notification, address lock or supplier PII access is created
-> Coreable approves and completes the actual bank refund
-> Refund, Payment, PaymentGroup and all Orders become REFUNDED
-> Customer creates a new checkout if purchase is still wanted
```

An exact receipt discovered only after unpaid cancellation is also terminal in B-102:

```text
PaymentGroup and every included Order: qualifying unpaid CANCELLED
-> No received Payment, Refund or Fulfillment exists; actual receipt equals the immutable checkout total
-> Record the receipt and Payment/PaymentGroup PAYMENT_EXCEPTION exactly once
-> Never reacquire stock or revive fulfillment, even when depositedAt was within the original deadline
-> Create one DELIVERY_GROUP_ORDER / LATE_DEPOSIT_EXCEPTION Refund for each immutable Order amount
-> Every included Order moves to REFUND_REQUESTED
-> Coreable returns the full checkout total; customer creates a new checkout
```

## Current Legacy Bank Transfer Pending Deadline

```text
Order status: PAYMENT_PENDING
-> 24 hours pass without confirmed deposit
-> Admin cancels unpaid checkout with reason
-> Order status: CANCELLED
-> Customer must create a new order to purchase
-> Order is not shown in customer order history
```

## Checkout Policy Confirmation

```text
Order status: PAYMENT_PENDING
-> Customer reviews order items, amount, shipping address, shipping policy, cancellation/refund policy, out-of-stock notice, bank-transfer notice
-> Customer checks one integrated confirmation checkbox
-> System records confirmed policy versions and confirmed time on the payment group
-> Bank-transfer account information can be used for deposit
```

## Customer Cancellation Before Supplier Order

```text
Order status: SUPPLIER_ORDER_PENDING
supplierOrderStartedAt is empty
-> Customer requests cancellation
-> Order status: REFUND_REQUESTED
-> Claim type: CANCEL
-> Claim status: APPROVED
-> Refund status: REQUESTED
-> Refund is requested for the cancelled order amount
-> Admin approves refund
-> Refund status: APPROVED
-> Admin manually transfers refund to the customer
-> Admin records manual refund completion
-> Refund status: COMPLETED
-> Payment status: REFUNDED or PARTIALLY_REFUNDED
-> Order status: REFUNDED

Manual refund cannot be confirmed
-> Refund status stays APPROVED or requires manual review
-> Order remains REFUND_REQUESTED
```

## Customer Cancellation Claim After Supplier Order Work Starts

```text
Order status: SUPPLIER_ORDER_PENDING with supplierOrderStartedAt set, or SUPPLIER_ORDERED before shipment
-> Customer cannot directly cancel order
-> Customer submits cancellation claim
-> Claim type: CANCEL
-> Claim status: REQUESTED
-> Admin checks supplier cancellation possibility and shipment situation

Supplier cancellation is possible
-> Claim status: APPROVED
-> Order status: REFUND_REQUESTED
-> Refund is processed at delivery-group order level

Supplier cancellation is not possible or order already shipped
-> Claim status: REJECTED
-> Order status stays unchanged
-> Customer is guided to post-delivery return claim if applicable
```

## Shipping Address Change

```text
Order status: PAYMENT_PENDING
-> Customer can edit checkout address before payment

Order status: SUPPLIER_ORDER_PENDING
-> Customer cannot directly change the confirmed checkout address
-> Address correction requires customer support before supplier work starts
-> If admin started supplier order work and addressLockedAt is set, correction is no longer accepted

Planned SUPPLIER_PORTAL order after deposit confirmation
-> Fulfillment request and addressLockedAt are created together
-> Supplier can immediately read the minimum delivery address
-> Customer self-service address change is not allowed

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

## Current Domeggook/Legacy Shipment Tracking Sync

```text
Order status: SHIPPED
-> System periodically syncs carrier tracking status
-> Tracking says delivered
-> Shipment status: DELIVERED
-> Order status: DELIVERED

Tracking sync failure
-> Keep current shipment/order status
-> Record `trackingSyncFailureReason`
-> Retry later or allow admin manual correction

Admin manual correction
-> Admin enters correction reason
-> Manual correction is recorded
-> Later automatic tracking sync cannot move the shipment backward or overwrite the admin correction without a valid forward transition
-> DS-36 records admin action history and order status history
```

## Return Or Exchange Claim After Delivery

```text
Order status: DELIVERED
-> Customer submits return or exchange claim
-> Claim type: RETURN or EXCHANGE
-> deliveryCompletedAt is existing deliveredAt for legacy orders and max(non-voided Shipment.deliveredAt) for portal orders
-> Customer selects claim reason:
   - simple change of mind
   - defect
   - wrong delivery
   - different from product info
   - delivery issue

Simple change of mind
-> Request must be within 7 days from deliveryCompletedAt
-> Return or exchange shipping cost bearer: CUSTOMER

Seller fault claim
-> Request must be within 3 months from deliveryCompletedAt in the current implementation
-> Policy also requires 30 days from discovery; discovery-date input remains planned
-> Photo evidence is required and stored as claim evidence
-> Return or exchange shipping cost bearer: SELLER

Admin approves claim
-> If return is needed, customer sends product back
-> Claim status: RETURN_WAITING (implemented by DS-37 for return approval)
-> Admin receives and inspects returned product
-> Claim status: RETURN_RECEIVED (implemented by B-044)
-> Admin starts return refund after inspection
-> Refund status: REQUESTED
-> Order status: REFUND_REQUESTED
-> Claim status: REFUND_PROCESSING
-> Admin approves refund execution
-> Admin completes the actual bank-transfer refund
-> Refund status: COMPLETED
-> Order status: REFUNDED
-> Claim status: COMPLETED
-> Exchange proceeds if exchange is approved (approval implemented by DS-37; exchange shipment remains planned)

Admin rejects claim
-> Claim status: REJECTED
-> Order status remains DELIVERED for rejected return claims
-> Customer sees rejection reason
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
- Current legacy bank-transfer `PAYMENT_PENDING` orders have a 24-hour deposit deadline and require admin unpaid cancellation if not paid.
- Planned portal `TRACKED` checkout reservations expire automatically after 24 hours and release `HELD` quantities exactly once.
- Planned B-102 amount mismatch is a portal/legacy PaymentGroup exception: it takes priority over saleability, deadline and stock, returns the exact actual receipt through one group Refund, and never resumes fulfillment.
- A qualifying unpaid-cancelled exact receipt is also terminal for portal and legacy groups: it uses one `LATE_DEPOSIT_EXCEPTION` Refund per immutable delivery-group Order amount, never reacquires or revives fulfillment, and requires a new checkout after the full receipt is returned.
- A deposit found after portal expiry is approved only when its actual time was inside the original deadline, every current saleability/contract/mode guard passes, and every tracked quantity is reacquired atomically. If saleability/contract/mode fails it records `SALE_UNAVAILABLE_AT_DEPOSIT` even when another condition also fails; only after those guards pass does a late timestamp or stock failure use `LATE_DEPOSIT_EXCEPTION`. The command records `PAYMENT_EXCEPTION` evidence and one reason-matched Refund per delivery-group Order, which becomes `REFUND_REQUESTED` without supplier exposure or normal-flow resume.
- Checkout policy confirmation is recorded per payment group with policy versions and confirmation time.
- Bank-transfer deposit must be confirmed by an admin before an order leaves `PAYMENT_PENDING`.
- The current payment method is direct bank transfer with admin deposit confirmation.
- Failed, pending, and expired payment orders are not shown in customer order history.
- `PAYMENT_PENDING`, `EXPIRED`, and payment failure states belong to checkout/retry surfaces, not normal customer order history.
- `SUPPLIER_ORDER_PENDING` is the main admin work queue for current legacy orders.
- A planned delivery-group Order creates `SUPPLIER_PORTAL` with owner SUPPLIER only when every item snapshot is portal-origin and its Supplier trade/portal/manager state is ACTIVE under the deposit lock. All-portal KEEP fallback is COREABLE_MANUAL with no supplier queue/email; mixed/legacy Orders preserve existing routing.
- DS-11 implements the admin supplier order queue with `GET /api/admin/orders`; it shows only `SUPPLIER_ORDER_PENDING` orders.
- Admin order detail shows internal statuses and fulfillment inputs, while customer order detail keeps customer-facing display statuses.
- One MVP order contains exactly one delivery group.
- One MVP payment group can contain multiple delivery-group orders.
- Customer can pay once for all delivery groups in the checkout.
- Delivery groups are based on supplier, but customer UI should use delivery group wording instead of supplier wording.
- Customers can directly change shipping address only until `SUPPLIER_ORDER_PENDING` in the current legacy flow.
- Customer direct shipping address change is blocked after checkout policy confirmation. `addressLockedAt` additionally records that supplier work has started.
- Planned portal orders record `addressLockedAt` when the deposit is confirmed and the request becomes visible to the supplier, so customer self-service address changes and cancellations are blocked immediately.
- `SUPPLIER_ORDERED` means the operator has placed the order with the supplier.
- DS-12 implements admin supplier actions: supplier work start, supplier order completed, and supplier out-of-stock.
- Supplier work start records `supplierOrderStartedAt`, `addressLockedAt`, and `addressLockedByAdminId` without changing order status.
- Supplier order completion is allowed only after supplier work start and records supplier order evidence before moving to `SUPPLIER_ORDERED`.
- Supplier out-of-stock before shipment moves the order to `OUT_OF_STOCK` and records the reason for refund handling.
- DS-13 implements admin shipment entry with carrier and tracking number; shipment entry is allowed only from `SUPPLIER_ORDERED`.
- Shipment entry creates one shipment record, moves the order to `SHIPPED`, and exposes the shipment summary on customer order detail.
- `PREPARING_SHIPMENT` is not used as an MVP order status; `SUPPLIER_ORDERED` covers supplier-ordered and waiting-for-tracking state.
- Supplier order work start does not add a new order status in MVP; it is tracked with `supplierOrderStartedAt` and `addressLockedAt`.
- Supplier order work should start on the same business day or next business day after payment confirmation.
- Supplier response or expected ship date should be secured within 1 business day after supplier order.
- Customer delay notice is required when expected shipment remains unclear for 2 business days after supplier order.
- Customer direct cancellation is allowed only for `COREABLE_MANUAL`/`DOMEGGOOK_API` orders whose status is `SUPPLIER_ORDER_PENDING` and whose supplier work/address lock has not started. `SUPPLIER_PORTAL` orders are address-locked at deposit confirmation and always use the Claim path.
- After supplier order work starts, cancellation is handled as a claim with admin manual review.
- After delivery, return and exchange are handled as claims with admin manual review.
- Simple change-of-mind return/exchange request window is 7 days from delivery completion; portal delivery completion is `max(non-voided Shipment.deliveredAt)`.
- Seller-fault claim request window is within 3 months from that delivery completion and within 30 days from discovery.
- Claim reasons start with simple change of mind, defect, wrong delivery, different from product info, and delivery issue.
- Defect, wrong delivery, different-from-product-info, and delivery issue claims require photo evidence by default.
- Simple change-of-mind return/exchange shipping cost is borne by the customer by default.
- Seller-fault return/exchange shipping cost is borne by the seller/operator by default.
- `OUT_OF_STOCK` must lead to customer notification and refund handling.
- Existing legacy `SHIPPED` requires carrier and tracking number.
- Planned portal tracking registration creates one or more allocated Shipments and uses `TRACKING_REGISTERED`; it is not evidence that the carrier has picked up the parcel.
- `DELIVERED` requires a shipment record plus delivered tracking or admin correction evidence. A portal claim's delivery basis is the maximum deliveredAt among non-voided Shipments.
- Current Domeggook flow includes automatic carrier tracking sync after carrier and tracking number are entered.
- Automatic tracking sync failure must not block order, payment, or refund operations.
- Current legacy/Domeggook flow uses one shipment per order and excludes partial shipment or split shipment.
- Planned portal orders allow multiple Shipments with positive item allocations; cumulative allocation cannot exceed each ordered quantity.
- Planned portal MVP does not call a live carrier-status API. The server generates an official carrier tracking link; suppliers may correct non-delivered records, while Coreable alone may void a pre-delivery record or record per-Shipment delivery evidence and recalculate the Order aggregate.
- Automatic tracking sync can move shipment state forward, but must not overwrite admin manual correction or move shipment state backward.
- `REFUNDED` requires a completed refund record.
- Orders can move to `REFUNDED` only after an administrator records actual manual bank-transfer completion.
- MVP supports partial cancellation/refund only at delivery-group order level within a payment group.
- Product, option, or quantity-level partial cancellation/refund inside one delivery-group order is excluded from MVP.
- Planned portal shortage reporting is allowed only before any Shipment has ever been registered, including one later voided, and applies to the whole delivery-group order. Duplicate lookup precedes owner guard; first report only records REPORTED and COREABLE handover, leaving Order/Claim/Refund unchanged. Admin approve invokes the existing out-of-stock/refund service; reject keeps COREABLE owner and exposes CONTACT_COREABLE. Supplier facts require an open Coreable claim task and never approve or complete a refund.
- Customer-facing order status must be mapped from internal order status instead of exposing internal status directly.
- Admin order state changes must use defined actions, not arbitrary status dropdown changes.
- Admin can only progress an order when the next operational step is confirmed.
- Automatic state rollback is excluded from MVP.
- Wrong state changes are handled by admin correction actions with required reason and history.
- State transitions are validated by from status, actor, action, guard, side effect, and target status.
- Current customer transaction notifications are recorded in `NotificationLog` as SMS attempts.
- Checkout creation sends one bank-transfer payment pending 안내 per payment group using the order recipient phone number.
- Admin deposit confirmation, out-of-stock, shipment started, delivery completed, claim status change, and refund completed create transactional notification logs.
- Supplier delay notice is a manual admin action before shipment; automatic delay scheduler is deferred.
- SMS dispatch runs after the main order/payment transaction commits, so provider failure must not roll back the operational action.
- Planned supplier operational notifications use email only. Their subject, body and payload snapshot contain no customer name, phone, address, delivery memo, payment or refund information. Every dispatch/retry revalidates the current verified contact, active portal/manager and time-valid VERIFIED contract; recipient/lifecycle/contract mismatch becomes SKIPPED.

## Risk Points

### Duplicate Deposit Confirmation Or Supplier Response

Admin deposit confirmation, supplier-order retries, and delayed external responses can be submitted more than once. Deposit approval and supplier purchase must remain idempotent.

Payment, supplier purchase, and refund events must retain identifiers and evidence so duplicate confirmations or retries do not create multiple payments, supplier orders, refunds, or state transitions.

### Client-Side Price Tampering

The server must calculate order amount from product and option data. Never trust client-submitted total price.

Portal supplier price calculation is deterministic: `basePrice=price(sourcePrice)`, `optionCustomerTotal=price(sourcePrice+sourceAdditionalPrice)`, and `additionalPrice=optionCustomerTotal-basePrice`, with one active markup/floor/rounding calculator. An approved cost change atomically snapshots the applied policy id/version, rates, rounding unit, resale minimum, and before/after product/option prices.

### Product Status Changed During Checkout

Before creating the order or confirming payment, the server must re-check that product and option are still sellable.

### Supplier Out Of Stock After Payment

This is expected in this business model. The service must support refund and customer notification instead of treating it as a system error.
