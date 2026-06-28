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
- Non-user-authenticated access is allowed for public product pages, public policy/business/legal pages, health checks, OAuth start/callback, verified provider webhooks, and internal scheduler endpoints.
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
| `GET` | `/api/me/agreements` | Authenticated user | Implemented | Current user policy agreement state |
| `POST` | `/api/me/agreements` | Authenticated user | Implemented | Agree to required terms/privacy policies |
| `GET` | `/api/me/addresses` | `CUSTOMER` | Implemented | List saved shipping addresses |
| `POST` | `/api/me/addresses` | `CUSTOMER` | Implemented | Create shipping address |
| `PATCH` | `/api/me/addresses/{addressId}` | `CUSTOMER` | Implemented | Update shipping address |
| `DELETE` | `/api/me/addresses/{addressId}` | `CUSTOMER` | Implemented | Delete shipping address |
| `POST` | `/api/me/deletion-request` | Authenticated user | Planned | Request account deletion |

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
- `POST /api/checkouts` requires current account terms/privacy agreement before order creation.
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
| `PUT` | `/api/admin/products/{productId}/detail-blocks` | `ADMIN` | Implemented | Replace ordered IMAGE/HTML detail blocks |
| `PUT` | `/api/admin/products/{productId}/notice` | `ADMIN` | Implemented | Create next active product notice version |
| `GET` | `/api/admin/products/{productId}/change-history` | `ADMIN` | Planned | Product change audit history |

DS-6 minimum:

- Supplier model and admin create/update API.
- Product model and admin create/update API.
- Product option model and admin create/update API.
- Product image metadata API with one thumbnail and up to ten gallery images.
- Product detail block API with ordered `IMAGE` and sanitized `HTML` blocks.
- Product notice/version source for product information notice, shipping, AS, return, and exchange information.
- Product change history writes for price, product status, option status, and supplier changes.
- Product and option status handling without stock quantity.
- Customer product list/detail read APIs.

DS-6 implementation notes:

- Public `/api/products/**` must be permitted by `SecurityConfig`.
- `/api/admin/**` remains `ADMIN` only.
- Product change history read API can remain planned, but DS-6 mutations must write history.
- Image binary upload can remain planned; DS-6 may store URL or object key metadata.
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
| `POST` | `/api/orders/{orderId}/cancel` | Authenticated user | Planned | Self-service cancel when allowed |

Rules:

- Order creation starts as `PAYMENT_PENDING`.
- Payment-pending orders expire after 30 minutes.
- Checkout creation requires current account terms/privacy agreement.
- DS-8 creates checkouts from cart only; direct-buy checkout is deferred.
- Checkout request includes shipping address fields directly.
- Server calculates all totals and ignores client-submitted totals.
- Checkout creation groups cart items by supplier as the MVP delivery-group boundary.
- Checkout creation empties the cart after payment group and orders are created.
- Checkout create/read responses include `policyLinks` for shipping, cancellation/refund, and payment-after-stockout notice.
- Policy confirmation is stored separately on the payment group before payment approval.
- Customer order history excludes normal `PAYMENT_PENDING`, `EXPIRED`, and failed payment attempts.
- Customer order history can show PG-approved payment exceptions that need customer-visible processing status.
- Customer order list and detail are scoped to the authenticated customer.
- Customer order APIs expose customer display statuses instead of internal order statuses.
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
| `POST` | `/api/payments/toss/confirm` | `CUSTOMER` | Implemented | Confirm Toss Payments approved payment server-side |
| `POST` | `/api/payments/toss/webhook` | Provider verification | Implemented | Receive Toss Payments webhook and reconcile payment state |
| `GET` | `/api/payments/{paymentId}` | Authenticated user | Planned | Customer-visible payment state |
| `POST` | `/api/admin/payments/{paymentId}/retry-cancel` | `ADMIN` | Implemented | Retry failed payment exception cancel |
| `GET` | `/api/admin/payment-exceptions` | `ADMIN` | Implemented | List payment exception queue |

Rules:

- Provider is Toss Payments for MVP.
- Enabled methods: card, easy payment, account transfer.
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
| `GET` | `/api/admin/orders` | `ADMIN` | Implemented | Supplier order pending admin queue |
| `GET` | `/api/admin/orders/{orderId}` | `ADMIN` | Implemented | Admin order detail |
| `POST` | `/api/admin/orders/{orderId}/supplier-work-start` | `ADMIN` | Implemented | Lock address and mark supplier work started |
| `POST` | `/api/admin/orders/{orderId}/supplier-order-completed` | `ADMIN` | Implemented | Mark manual supplier order completed |
| `POST` | `/api/admin/orders/{orderId}/out-of-stock` | `ADMIN` | Implemented | Mark supplier out-of-stock and prepare refund flow |
| `POST` | `/api/admin/orders/{orderId}/shipments` | `ADMIN` | Implemented | Enter carrier and tracking number |
| `PATCH` | `/api/admin/orders/{orderId}/shipment-correction` | `ADMIN` | Planned | Manually correct shipment state with reason |
| `POST` | `/api/admin/orders/{orderId}/corrections` | `ADMIN` | Planned | Admin correction action with reason |

Rules:

- Admin cannot write arbitrary order status values.
- Admin actions must map to valid transition table actions.
- Admin order queue currently returns `SUPPLIER_ORDER_PENDING` orders only.
- `PAYMENT_PENDING` and `EXPIRED` orders are excluded from the supplier order queue.
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
| `POST` | `/api/internal/shipments/tracking-sync` | Internal scheduler | Planned | Sync tracking status |
| `POST` | `/api/admin/shipments/{shipmentId}/tracking-sync` | `ADMIN` | Planned | Manual retry tracking sync |

Rules:

- Customer order detail includes shipment summary when an admin-entered shipment exists.
- Shipment creation requires carrier and tracking number.
- MVP supports one shipment per order.
- Automatic tracking moves shipment forward only.
- Tracking failure must not block order, payment, or refund operations.
- Manual correction requires reason and history.

## Refund And Claim APIs

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `POST` | `/api/orders/{orderId}/cancel` | Authenticated user | Implemented | Self-service cancel when eligible |
| `POST` | `/api/orders/{orderId}/claims` | Authenticated user | Implemented | Create cancellation claim after supplier work starts |
| `GET` | `/api/orders/{orderId}/claims` | Authenticated user | Planned | Customer claim list for an order |
| `GET` | `/api/claims/{claimId}` | Authenticated user | Planned | Customer claim detail |
| `GET` | `/api/admin/claims` | `ADMIN` | Implemented | Admin cancellation claim queue |
| `POST` | `/api/admin/claims/{claimId}/approve` | `ADMIN` | Implemented | Approve cancellation claim |
| `POST` | `/api/admin/claims/{claimId}/reject` | `ADMIN` | Implemented | Reject cancellation claim |
| `POST` | `/api/admin/claims/{claimId}/request-evidence` | `ADMIN` | Planned | Request evidence |
| `POST` | `/api/admin/claims/{claimId}/return-received` | `ADMIN` | Planned | Mark return received |
| `POST` | `/api/admin/claims/{claimId}/exchange-shipped` | `ADMIN` | Planned | Mark exchange shipment |
| `GET` | `/api/admin/refunds` | `ADMIN` | Implemented | Refund queue |
| `POST` | `/api/admin/refunds/{refundId}/approve` | `ADMIN` | Planned | Approve refund execution |
| `POST` | `/api/admin/refunds/{refundId}/request-pg-cancel` | `ADMIN` | Implemented | Request PG cancel/refund |
| `POST` | `/api/admin/refunds/{refundId}/retry` | `ADMIN` | Implemented | Retry failed refund |
| `POST` | `/api/admin/refunds/{refundId}/manual-review` | `ADMIN` | Planned | Mark manual review result |

Rules:

- Customer self-service cancel is allowed only while `SUPPLIER_ORDER_PENDING` and supplier work has not started.
- Self-service cancellation creates an approved cancellation claim, refund record, and moves the order to `REFUND_REQUESTED`.
- After supplier work starts, cancellation becomes a `CANCEL` claim that admin can approve or reject.
- Return and exchange claims remain planned.
- Refund completion requires PG cancel/refund success.
- Refund records are created for approved customer cancellation and supplier out-of-stock.
- PG cancel success moves the delivery-group order to `REFUNDED`, the payment to `REFUNDED` or `PARTIALLY_REFUNDED`, and the payment group to `REFUNDED` or `PARTIALLY_REFUNDED`.
- PG cancel failure leaves the order in `REFUND_REQUESTED`, marks the refund `RETRY_REQUIRED`, and does not expose refund completion.
- Delivery-group order level partial refund is supported.
- Product, option, and quantity-level partial refund inside one delivery-group order is excluded.

## Policy And Legal APIs

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/policies` | Public | Implemented | List customer-facing MVP policy pages |
| `GET` | `/api/policies/{slug}` | Public | Implemented | Customer-facing policy page by slug |
| `GET` | `/api/policies/{type}/current` | Public | Planned | Active managed policy document by type |
| `GET` | `/api/policies/{type}/versions/{version}` | Public | Planned | Specific policy version |
| `GET` | `/api/business-profile` | Public | Planned | Active business disclosure |
| `GET` | `/api/privacy-processing-items` | Public | Planned | Active privacy processing table |
| `GET` | `/api/admin/policies` | `ADMIN` | Planned | Admin policy document list |
| `POST` | `/api/admin/policies` | `ADMIN` | Planned | Create policy draft |
| `PATCH` | `/api/admin/policies/{policyId}` | `ADMIN` | Planned | Update policy draft |
| `POST` | `/api/admin/policies/{policyId}/activate` | `ADMIN` | Planned | Activate policy version |
| `PATCH` | `/api/admin/business-profile` | `ADMIN` | Planned | Update business disclosure |
| `PUT` | `/api/admin/privacy-processing-items` | `ADMIN` | Planned | Replace privacy processing table |

Rules:

- Implemented MVP slugs are `shipping`, `cancellation-refund`, and `stock-risk`.
- Implemented policy pages are static backend responses based on confirmed policy docs; admin policy management remains planned.
- Product detail and checkout responses include links to the implemented policy page endpoints.
- Policy pages are available from customer menu and footer.
- Policy documents have version and effective date.
- Checkout stores policy versions per payment group.
- Actual legal wording requires launch review.

## Notification And Audit APIs

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/admin/orders/{orderId}/status-history` | `ADMIN` | Planned | Order status transition history |
| `GET` | `/api/admin/actions` | `ADMIN` | Planned | Admin action history |
| `GET` | `/api/admin/notifications` | `ADMIN` | Planned | Notification log search |
| `POST` | `/api/admin/notifications/{notificationId}/retry` | `ADMIN` | Planned | Retry failed notification |

Rules:

- Transactional notifications are separate from marketing consent.
- Payment completed, payment exception, out-of-stock, shipment started, delivered, delay notice, claim changed, and refund completed should create notification logs.

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
  "basePrice": 39000,
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
- Image size limit: 5MB per image.

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

- `HTML` blocks must be sanitized.
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

### Product Detail Response Shape

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "name": "Product name",
  "summary": "Short customer-facing summary",
  "basePrice": 39000,
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
- Image upload binary flow is not defined; DS-6 can start with image URL/object key metadata.
- OAuth token/session format is implemented as a stateless JWT access token stored in an HttpOnly cookie.
- Public product APIs are public, but checkout/cart/order APIs require authentication.
