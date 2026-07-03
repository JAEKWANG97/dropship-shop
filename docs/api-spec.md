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

Order and payment state conflicts caused by optimistic locking return `409 CONFLICT` with code `CONFLICT` and the message `Order state was just changed. Please refresh and try again.` The client should not retry automatically; the user or admin should reload the latest state before submitting another action.

## Grouping Index

- Customer: catalog browsing, cart, checkout, orders, shipment, claims, account profile.
- Admin: suppliers, products, order queue, fulfillment, shipment correction, refunds, claims, policies, audit, notifications.
- Auth: social OAuth start/callback, logout, current user.
- Catalog: public product APIs and admin supplier/product/option/detail management.
- Cart: current customer cart and cart item mutations.
- Checkout/Order: payment group creation, policy confirmation, customer order history, address changes, self-service cancel.
- Payment: Toss confirmation, webhook, payment exception handling.
- Fulfillment/Shipment: admin supplier actions, shipment entry, tracking sync, shipment correction.
- Refund/Claim: customer claim submission and admin review/refund execution.
- Policy Pages: public policy, business disclosure, privacy processing table, admin policy management.

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
| `PATCH` | `/api/me/profile` | Authenticated user | Implemented | Update display name and contact email |
| `POST` | `/api/me/phone-verifications` | Authenticated user | Implemented | Request SMS OTP phone verification |
| `POST` | `/api/me/phone-verifications/confirm` | Authenticated user | Implemented | Confirm SMS OTP phone verification |
| `GET` | `/api/me/agreements` | Authenticated user | Implemented | Current user policy agreement state |
| `POST` | `/api/me/agreements` | Authenticated user | Implemented | Agree to required terms/privacy policies |
| `GET` | `/api/me/addresses` | `CUSTOMER` | Implemented | List saved shipping addresses |
| `POST` | `/api/me/addresses` | `CUSTOMER` | Implemented | Create shipping address |
| `PATCH` | `/api/me/addresses/{addressId}` | `CUSTOMER` | Implemented | Update shipping address |
| `DELETE` | `/api/me/addresses/{addressId}` | `CUSTOMER` | Implemented | Delete shipping address |
| `POST` | `/api/me/deletion-request` | `CUSTOMER` | Implemented | Delete/anonymize current customer account and clear access token cookie. Rejects while any order/refund/claim is still in progress. |

Notes:

- MVP supports only Kakao, Google, and Naver social login.
- Email/password signup and login are excluded.
- Guest checkout is excluded.
- Admin users use the same social login flow, but admin access comes only from DB role.
- OAuth login uses provider authorization-code callbacks, provider token/userinfo requests, and social identity lookup by provider plus provider user id.
- Successful login sets `ACCESS_TOKEN` as an HttpOnly cookie with `SameSite=Lax`; production must use `Secure`.
- Access tokens are stateless JWTs signed by the API. Refresh tokens are deferred from MVP auth foundation.
- Current required versions start as `terms-2026-06-01` and `privacy-2026-06-01`.
- `POST /api/me/agreements` requires both `termsAgreed=true` and `privacyAgreed=true` with current required versions.
- Reposting the same current versions is idempotent and returns the existing agreement record.
- Required customer info is display name, reachable contact email, and verified phone number.
- Phone verification uses SMS OTP with hashed code storage, expiration, resend cooldown, and attempt limits.
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
  "termsVersion": "terms-2026-06-01",
  "privacyVersion": "privacy-2026-06-01"
}

GET /api/me/profile-completion
{
  "displayName": "Customer",
  "displayNameComplete": true,
  "email": "customer@example.com",
  "emailRequired": false,
  "emailComplete": true,
  "phoneNumber": "01012345678",
  "phoneVerified": true,
  "phoneVerifiedAt": "2026-06-29T00:00:00Z",
  "requiredInfoComplete": true
}

PATCH /api/me/profile
{
  "displayName": "Customer",
  "email": "customer@example.com"
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

## Catalog APIs

### Customer Catalog

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/products` | Public | Implemented | List customer-visible active products |
| `GET` | `/api/products/{productId}` | Public | Implemented | Product detail with options, images, detail blocks, and customer policy links |

Customer visibility rules:

- Show only products customer can view.
- Purchase is allowed only when product status is `ACTIVE` and option status is `ACTIVE`.
- Do not expose raw supplier information to customers.
- Product detail responses include `policyLinks` for shipping, cancellation/refund, and payment-after-stockout notices so operational policy is not embedded only in arbitrary product HTML/images.

### Admin Catalog

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/admin/suppliers` | `ADMIN` | Implemented | List suppliers |
| `POST` | `/api/admin/suppliers` | `ADMIN` | Implemented | Create supplier |
| `GET` | `/api/admin/suppliers/{supplierId}` | `ADMIN` | Implemented | Supplier detail |
| `PATCH` | `/api/admin/suppliers/{supplierId}` | `ADMIN` | Implemented | Update supplier |
| `GET` | `/api/admin/products` | `ADMIN` | Implemented | List products including hidden/stopped products |
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
- Products carry one fixed `categoryCode`; category administration and multi-category assignment are future scope.
- Product detail block API with ordered `IMAGE` and sanitized `HTML` blocks.
- Product notice/version source for product information notice, shipping, AS, return, and exchange information.
- Product change history writes for product, option, image, detail, notice, and supplier changes.
- Product and option status handling without stock quantity.
- Admin product responses include `sourcePrice`; public product responses do not expose supplier cost.
- Active pricing policy stores the default margin rates used to calculate customer sale prices from supplier cost.
- Customer product list/detail read APIs.

DS-6 implementation notes:

- Public `/api/products/**` must be permitted by `SecurityConfig`.
- `/api/admin/**` remains `ADMIN` only.
- DS-43 implements the admin product change history read API at `GET /api/admin/products/{productId}/changes`.
- Product image binary upload is implemented for local product image storage and returns URL/object key metadata.
- Product detail and notice version sources must exist before DS-8 order creation can safely snapshot order items.

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
- Adding the same product option increases the existing cart item quantity.
- Cart item quantity is 1 through 99.
- Product option can be added only when product status is `ACTIVE` and option status is `ACTIVE`.
- If product or option status changes after being added, the cart item remains but `checkoutAvailable` becomes false.
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
| `PATCH` | `/api/orders/{orderId}/shipping-address` | `CUSTOMER` | Implemented | Change address before supplier work starts |
| `POST` | `/api/orders/{orderId}/cancel` | `CUSTOMER` | Implemented | Self-service cancel when allowed |

Rules:

- Order creation starts as `PAYMENT_PENDING`.
- `PAYMENT_PENDING` means bank-transfer deposit waiting in the current MVP flow.
- Bank-transfer deposit deadline defaults to 24 hours.
- Checkout creation requires current account terms/privacy agreement and completed required customer info.
- DS-8 creates checkouts from cart only; direct-buy checkout is deferred.
- Checkout request includes shipping address fields directly.
- Server calculates all totals and ignores client-submitted totals.
- Checkout creation groups cart items by supplier as the MVP delivery-group boundary.
- Checkout creation pessimistically locks the customer's cart row before reading cart items to prevent duplicate submit from creating two payment groups.
- Checkout creation empties the cart after payment group and orders are created.
- A duplicate checkout submit after the first transaction commits returns `400 BUSINESS_RULE_VIOLATION` with `Checkout was already submitted for this cart. Please check your checkout or cart.`
- Checkout create/read responses include `policyLinks` for shipping, cancellation/refund, and payment-after-stockout notice.
- Checkout create/read responses include `bankTransferDeposit` with bank name, account number, account holder, depositor name, amount, deadline, and cash receipt notice.
- Policy confirmation is stored separately on the payment group before admin deposit confirmation.
- Customer order history excludes normal `PAYMENT_PENDING`, `EXPIRED`, and failed payment attempts.
- Customer order history can show PG-approved payment exceptions that need customer-visible processing status.
- Customer order list and detail are scoped to the authenticated customer.
- Customer order APIs expose stable status codes. Customer-facing display labels are owned by the frontend.
- Customer order detail includes payment group summary, payment summary, shipping address, order items, and placeholder fulfillment/shipment/refund summaries.
- Checkout shipping address changes are allowed only while the payment group and its orders are still `PAYMENT_PENDING`.
- Checkout shipping address changes are rejected after checkout policy confirmation because the confirmation text includes shipping address.
- Paid order shipping address changes are allowed only while the order is `SUPPLIER_ORDER_PENDING` and both `supplierOrderStartedAt` and `addressLockedAt` are empty.
- Address changes are rejected after `address_locked_at` or supplier order completion.

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
  "termsVersion": "terms-2026-06-01",
  "privacyVersion": "privacy-2026-06-01",
  "orderPolicyVersion": "order-2026-06-01",
  "cancellationRefundPolicyVersion": "refund-2026-06-01",
  "outOfStockNoticeVersion": "out-of-stock-2026-06-01",
  "confirmedNoticeText": "I agree to the checkout policies."
}
```

## Payment APIs

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `POST` | `/api/payments/toss/confirm` | `CUSTOMER` | Implemented, deferred from primary path | Confirm Toss Payments approved payment server-side |
| `POST` | `/api/payments/toss/webhook` | Provider verification | Implemented, deferred from primary path | Receive Toss Payments webhook and reconcile payment state |
| `GET` | `/api/payments/{paymentId}` | Authenticated user | Planned | Customer-visible payment state |
| `POST` | `/api/admin/payments/{paymentId}/retry-cancel` | `ADMIN` | Implemented | Retry failed payment exception cancel |
| `GET` | `/api/admin/payment-exceptions` | `ADMIN` | Implemented | List payment exception queue |

Rules:

- Current MVP primary provider is direct bank transfer.
- Bank-transfer payment records use `PaymentProvider.BANK_TRANSFER`, `PaymentMethod.BANK_TRANSFER`, and `providerPaymentKey = BANK-{checkoutNumber}`.
- Toss Payments remains implemented but deferred from the customer primary checkout path.
- Deferred Toss methods: card, easy payment, account transfer.
- Virtual account, mobile phone payment, and gift certificate payment are excluded.
- Payment confirmation must verify amount, expiration, policy confirmation, PG key uniqueness, and product/option sellability.
- PG-approved validation failure becomes payment exception and blocks supplier ordering.
- Duplicate Toss confirmation with the same payment key and same checkout returns the existing payment result.
- A payment key already attached to a different checkout is rejected as a conflict.
- DS-9 stores payment events for confirm requested, approved, rejected, and payment exception paths.
- Toss secret key is read from environment/config as `payments.toss.secret-key` and must not be committed.
- PG-approved amount mismatch creates a payment exception and immediately attempts full PG cancel.
- Payment exception cancel uses a stable idempotency key derived from the payment id.
- Successful payment exception cancel moves the payment group and orders to `CANCELLED`.
- Failed payment exception cancel moves the payment and payment group to `CANCEL_FAILED`.
- The admin payment exception queue is DB state based; it lists payments in `CANCEL_REQUIRED`, `CANCEL_REQUESTED`, `CANCEL_FAILED`, or `REVIEW_REQUIRED`.
- Admin retry reuses the stored idempotency key.
- Toss webhook verification re-fetches the payment from Toss by `paymentKey` and compares the verified status with the webhook payload status.
- Toss webhook idempotency uses `TossPayments-Webhook-Transmission-Id` when present, with event type, payment key, and created time as fallback.
- Duplicate Toss webhook deliveries do not create duplicate `PaymentEvent` rows.
- Unknown local `paymentKey` webhooks are accepted after Toss lookup verification but do not create local payment events.
- Webhook status conflicts with the local server-confirmed payment state move the payment to `REVIEW_REQUIRED` for admin review.
- Payment detail API remains planned.

Implemented request body:

```json
POST /api/payments/toss/confirm
{
  "checkoutNumber": "CO123456789012",
  "paymentKey": "toss-payment-key",
  "amount": 10000
}
```

## Admin Order And Fulfillment APIs

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/admin/orders` | `ADMIN` | Implemented | Supplier order pending admin queue by default; supports `status` filter |
| `GET` | `/api/admin/orders/{orderId}` | `ADMIN` | Implemented | Admin order detail |
| `POST` | `/api/admin/orders/{orderId}/confirm-deposit` | `ADMIN` | Implemented | Confirm direct bank-transfer deposit and move checkout orders to supplier order pending |
| `POST` | `/api/admin/orders/{orderId}/unpaid-cancel` | `ADMIN` | Implemented | Cancel unpaid bank-transfer checkout |
| `POST` | `/api/admin/orders/{orderId}/deposit-mismatch` | `ADMIN` | Implemented | Record bank-transfer deposit mismatch memo |
| `POST` | `/api/admin/orders/{orderId}/supplier-work-start` | `ADMIN` | Implemented | Lock address and mark supplier work started |
| `POST` | `/api/admin/orders/{orderId}/supplier-order-completed` | `ADMIN` | Implemented | Mark manual supplier order completed |
| `POST` | `/api/admin/orders/{orderId}/out-of-stock` | `ADMIN` | Implemented | Mark supplier out-of-stock and prepare refund flow |
| `POST` | `/api/admin/orders/{orderId}/shipments` | `ADMIN` | Implemented | Enter carrier and tracking number |
| `PATCH` | `/api/admin/orders/{orderId}/shipment-correction` | `ADMIN` | Planned | Manually correct shipment state with reason |
| `POST` | `/api/admin/orders/{orderId}/corrections` | `ADMIN` | Planned | Admin correction action with reason |

Rules:

- Admin cannot write arbitrary order status values.
- Admin actions must map to valid transition table actions.
- Admin order queue defaults to `SUPPLIER_ORDER_PENDING` orders.
- `GET /api/admin/orders?status=PAYMENT_PENDING` returns the bank-transfer deposit waiting queue.
- `PAYMENT_PENDING` and `EXPIRED` orders are excluded from the supplier order queue.
- Admin deposit confirmation requires a reason, confirmed checkout policies, `PAYMENT_PENDING` checkout orders, and currently sellable products/options.
- Admin deposit confirmation creates a `BANK_TRANSFER` payment row, marks the payment group `APPROVED`, and moves all checkout orders to `SUPPLIER_ORDER_PENDING`.
- Admin unpaid cancellation requires a reason and moves all checkout orders to `CANCELLED`.
- Admin deposit mismatch memo keeps checkout orders `PAYMENT_PENDING`.
- Stale customer/admin order or payment group updates are rejected with `409 CONFLICT` instead of overwriting the latest state.
- Admin order detail exposes internal order/payment/fulfillment statuses plus supplier, product option, customer shipping, and payment summary fields.
- Supplier work start requires a reason and records `supplierOrderStartedAt`, `addressLockedAt`, and `addressLockedByAdminId`.
- Supplier order completion requires `supplierOrderNumber` and reason. `expectedShipDate` and `supplierResponseMemo` are optional evidence fields.
- Supplier out-of-stock requires a reason and moves the order to `OUT_OF_STOCK`.
- Shipment creation requires `carrier` and `trackingNumber`, creates one shipment for the order, and moves the order to `SHIPPED`.
- MVP allows only one shipment per order; duplicate shipment creation is rejected.
- Reason is required for cancellation, refund, out-of-stock, shipment correction, and admin correction.
- `PREPARING_SHIPMENT` is not an MVP order status.

## Shipment Tracking APIs

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/orders/{orderId}/shipment` | Authenticated user | Planned | Customer shipment detail |
| `POST` | `/api/internal/shipments/tracking-sync` | Internal scheduler token | Implemented | Sync tracking status batch by carrier/tracking number |
| `POST` | `/api/admin/shipments/{shipmentId}/tracking-sync` | `ADMIN` | Implemented | Manual retry tracking sync |
| `POST` | `/api/admin/shipments/{shipmentId}/manual-correction` | `ADMIN` | Implemented | Manually correct shipment status to delivered |

Rules:

- Customer order detail includes shipment summary when an admin-entered shipment exists.
- Internal scheduler calls must include `X-Internal-Sync-Token`; the token is configured only on the API server and scheduler.
- Shipment creation requires carrier and tracking number.
- MVP supports one shipment per order.
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
| `POST` | `/api/admin/refunds/{refundId}/request-pg-cancel` | `ADMIN` | Implemented | Request PG cancel/refund |
| `POST` | `/api/admin/refunds/{refundId}/retry` | `ADMIN` | Implemented | Retry failed refund |
| `POST` | `/api/admin/refunds/{refundId}/manual-review` | `ADMIN` | Implemented | Mark manual review result |
| `POST` | `/api/admin/refunds/{refundId}/manual-complete` | `ADMIN` | Implemented | Complete actual manual bank-transfer refund |

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
- `return-refund` requires a `RETURN_RECEIVED` return claim, creates a `RETURN_REQUESTED` refund, links it to the claim, moves the order to `REFUND_REQUESTED`, and moves the claim to `REFUND_PROCESSING`.
- Bank-transfer refund completion moves the linked return claim to `COMPLETED`.
- Refund execution requires admin approval before PG cancel/refund request or manual bank-transfer refund completion.
- First PG cancel failure moves refund to `RETRY_REQUIRED`; retry failure moves refund to `MANUAL_REVIEW_REQUIRED`.
- Manual review can approve the refund again or reject it with reason.
- Bank-transfer refund completion requires actual manual refund completion by an admin.
- `GET /api/orders/{orderId}` includes `claims` plus the latest `claim` summary for compatibility. `GET /api/admin/orders/{orderId}` includes the latest claim summary and claim evidence metadata.
- Deferred PG refund completion requires PG cancel/refund success.
- Refund records are created for approved customer cancellation and supplier out-of-stock.
- Manual bank-transfer refund completion or PG cancel success moves the delivery-group order to `REFUNDED`, the payment to `REFUNDED` or `PARTIALLY_REFUNDED`, and the payment group to `REFUNDED` or `PARTIALLY_REFUNDED`.
- PG cancel failure leaves the order in `REFUND_REQUESTED`, marks the refund `RETRY_REQUIRED`, and does not expose refund completion.
- Delivery-group order level partial refund is supported.
- Product, option, and quantity-level partial refund inside one delivery-group order is excluded.

## Policy And Legal APIs

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/policies` | Public | Implemented | List customer-facing MVP policy pages |
| `GET` | `/api/policies/{slug}` | Public | Implemented | Customer-facing policy page by slug |
| `GET` | `/api/policies/{type}/current` | Public | Implemented | Active managed policy document by type |
| `GET` | `/api/policies/{type}/versions/{version}` | Public | Implemented | Specific policy version |
| `GET` | `/api/business-profile` | Public | Implemented | Active business disclosure |
| `GET` | `/api/privacy-processing-items` | Public | Implemented | Active privacy processing table |
| `POST` | `/api/customer-inquiries` | Public | Implemented | Create customer support inquiry |
| `GET` | `/api/admin/policies` | `ADMIN` | Implemented | Admin policy document list |
| `POST` | `/api/admin/policies` | `ADMIN` | Implemented | Create policy draft |
| `PATCH` | `/api/admin/policies/{policyId}` | `ADMIN` | Implemented | Update policy draft |
| `POST` | `/api/admin/policies/{policyId}/activate` | `ADMIN` | Implemented | Activate policy version |
| `PATCH` | `/api/admin/business-profile` | `ADMIN` | Planned | Update business disclosure |
| `PUT` | `/api/admin/privacy-processing-items` | `ADMIN` | Planned | Replace privacy processing table |
| `GET` | `/api/admin/customer-inquiries` | `ADMIN` | Implemented | List customer support inquiries |

Rules:

- Implemented MVP slugs are `shipping`, `cancellation-refund`, and `stock-risk`.
- Implemented policy pages are backed by active `policy_documents` rows.
- Business profile and privacy processing item APIs are backed by DB tables; admin management remains planned.
- Managed policy documents support draft creation, draft update, activation, current public lookup, and version public lookup in DS-41.
- Public policy document types include `SHIPPING_POLICY`, `CANCELLATION_REFUND_POLICY`, and `OUT_OF_STOCK_NOTICE`.
- Product detail and checkout responses include links to the implemented policy page endpoints; link labels use active policy document titles when configured.
- Policy pages are available from customer menu and footer.
- Policy documents have version and effective date.
- Checkout stores policy versions per payment group.
- Customer inquiry MVP stores name, email, optional phone, subject, message, and created timestamp. Reply workflow is planned later.
- Actual legal wording requires launch review.

## Notification And Audit APIs

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/admin/orders/{orderId}/status-history` | `ADMIN` | Implemented | Order status transition history |
| `GET` | `/api/admin/actions` | `ADMIN` | Implemented | Admin order action history |
| `GET` | `/api/admin/notifications?status=FAILED` | `ADMIN` | Implemented | Notification log search, optionally filtered by status |
| `POST` | `/api/admin/notifications/{notificationId}/retry` | `ADMIN` | Implemented | Retry failed notification |
| `POST` | `/api/admin/orders/{orderId}/delay-notice` | `ADMIN` | Implemented | Send manual supplier delay notice before shipment |

Rules:

- Transactional notifications are separate from marketing consent.
- Payment pending, payment completed, payment exception, out-of-stock, shipment started, delivered, delay notice, claim changed, and refund completed should create notification logs.
- B-011 sends transactional notifications through SMS first. Logs start as `PENDING` and become `SENT`, `FAILED`, or `SKIPPED`.
- `sms.sens.enabled=false` is the default safe fallback and records logs as `SKIPPED`.
- DS-44 exposes order status history and admin order action history read APIs.

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
- Options are purchasable only when option status is `ACTIVE` and product status is `ACTIVE`.

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

- Default customer sale price is supplier cost plus the total markup rate, rounded up by `roundingUnit`.

### Product Detail Response Shape

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "name": "Product name",
  "summary": "Short customer-facing summary",
  "basePrice": 39000,
  "categoryCode": "PPE_SAFETY_HELMET",
  "status": "ACTIVE",
  "detailVersion": 3,
  "productNoticeVersion": 2,
  "images": [],
  "options": [],
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
