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
- Basic login and form login are disabled. Social OAuth/JWT integration is planned.
- Server calculates all order, payment, refund, and shipping amounts.
- Client-submitted totals are never trusted.
- Mutating admin actions that affect order, refund, shipment, claim, or product status should record audit history.
- API error format is not finalized yet.

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
| `GET` | `/actuator/info` | Public | Implemented | Actuator info endpoint |
| `GET` | `/api/me` | Authenticated user | Implemented | Return authenticated user id |
| `GET` | `/api/admin/me` | `ADMIN` | Implemented | Prove admin access and return admin user id |

## Auth And Account APIs

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/auth/oauth2/{provider}/authorize` | Public | Planned | Start Kakao, Google, or Naver social login |
| `GET` | `/api/auth/oauth2/{provider}/callback` | Public | Planned | Handle provider callback |
| `POST` | `/api/auth/logout` | Authenticated user | Planned | Logout current session/token |
| `GET` | `/api/me` | Authenticated user | Implemented | Current user identity |
| `GET` | `/api/me/agreements` | Authenticated user | Planned | Current user policy agreement state |
| `POST` | `/api/me/agreements` | Authenticated user | Planned | Agree to required terms/privacy policies |
| `GET` | `/api/me/addresses` | Authenticated user | Planned | List saved shipping addresses |
| `POST` | `/api/me/addresses` | Authenticated user | Planned | Create shipping address |
| `PATCH` | `/api/me/addresses/{addressId}` | Authenticated user | Planned | Update shipping address |
| `DELETE` | `/api/me/addresses/{addressId}` | Authenticated user | Planned | Delete shipping address |
| `POST` | `/api/me/deletion-request` | Authenticated user | Planned | Request account deletion |

Notes:

- MVP supports only Kakao, Google, and Naver social login.
- Email/password signup and login are excluded.
- Guest checkout is excluded.
- Admin users use the same social login flow, but admin access comes only from DB role.
- Current code proves security boundaries but does not implement real OAuth callback/token parsing yet.

## Catalog APIs

### Customer Catalog

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/products` | Public | Implemented | List customer-visible active products |
| `GET` | `/api/products/{productId}` | Public | Implemented | Product detail with options, images, and detail blocks |

Customer visibility rules:

- Show only products customer can view.
- Purchase is allowed only when product status is `ACTIVE` and option status is `ACTIVE`.
- Do not expose raw supplier information to customers.
- Show delivery, cancellation, refund, and out-of-stock notices outside arbitrary product HTML/images.

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
| `PATCH` | `/api/checkouts/{checkoutNumber}/shipping-address` | Authenticated user | Planned | Update checkout shipping address before payment confirmation |
| `POST` | `/api/checkouts/{checkoutNumber}/policy-confirmation` | `CUSTOMER` | Implemented | Store order policy confirmation |
| `GET` | `/api/orders` | `CUSTOMER` | Implemented | Customer order history |
| `GET` | `/api/orders/{orderId}` | `CUSTOMER` | Implemented | Customer order detail |
| `PATCH` | `/api/orders/{orderId}/shipping-address` | Authenticated user | Planned | Change address before supplier work starts |
| `POST` | `/api/orders/{orderId}/cancel` | Authenticated user | Planned | Self-service cancel when allowed |

Rules:

- Order creation starts as `PAYMENT_PENDING`.
- Payment-pending orders expire after 30 minutes.
- DS-8 creates checkouts from cart only; direct-buy checkout is deferred.
- Checkout request includes shipping address fields directly.
- Server calculates all totals and ignores client-submitted totals.
- Checkout creation groups cart items by supplier as the MVP delivery-group boundary.
- Checkout creation empties the cart after payment group and orders are created.
- Policy confirmation is stored separately on the payment group before payment approval.
- Customer order history excludes normal `PAYMENT_PENDING`, `EXPIRED`, and failed payment attempts.
- Customer order history can show PG-approved payment exceptions that need customer-visible processing status.
- Customer order list and detail are scoped to the authenticated customer.
- Customer order APIs expose customer display statuses instead of internal order statuses.
- Customer order detail includes payment group summary, payment summary, shipping address, order items, and placeholder fulfillment/shipment/refund summaries.
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
| `POST` | `/api/payments/toss/webhook` | Provider verification | Planned | Receive Toss Payments webhook if enabled |
| `GET` | `/api/payments/{paymentId}` | Authenticated user | Planned | Customer-visible payment state |
| `POST` | `/api/admin/payments/{paymentId}/retry-cancel` | `ADMIN` | Planned | Retry failed payment exception cancel |
| `GET` | `/api/admin/payment-exceptions` | `ADMIN` | Planned | List payment exception queue |

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
- Automatic PG cancel execution and admin retry APIs remain planned.

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
| `POST` | `/api/admin/orders/{orderId}/shipments` | `ADMIN` | Planned | Enter carrier and tracking number |
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
- Reason is required for cancellation, refund, out-of-stock, shipment correction, and admin correction.
- `PREPARING_SHIPMENT` is not an MVP order status.

## Shipment Tracking APIs

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/orders/{orderId}/shipment` | Authenticated user | Planned | Customer shipment detail |
| `POST` | `/api/internal/shipments/tracking-sync` | Internal scheduler | Planned | Sync tracking status |
| `POST` | `/api/admin/shipments/{shipmentId}/tracking-sync` | `ADMIN` | Planned | Manual retry tracking sync |

Rules:

- Automatic tracking moves shipment forward only.
- Tracking failure must not block order, payment, or refund operations.
- Manual correction requires reason and history.

## Refund And Claim APIs

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `POST` | `/api/orders/{orderId}/claims` | Authenticated user | Planned | Create cancellation, return, or exchange claim |
| `GET` | `/api/orders/{orderId}/claims` | Authenticated user | Planned | Customer claim list for an order |
| `GET` | `/api/claims/{claimId}` | Authenticated user | Planned | Customer claim detail |
| `POST` | `/api/admin/claims/{claimId}/approve` | `ADMIN` | Planned | Approve claim |
| `POST` | `/api/admin/claims/{claimId}/reject` | `ADMIN` | Planned | Reject claim |
| `POST` | `/api/admin/claims/{claimId}/request-evidence` | `ADMIN` | Planned | Request evidence |
| `POST` | `/api/admin/claims/{claimId}/return-received` | `ADMIN` | Planned | Mark return received |
| `POST` | `/api/admin/claims/{claimId}/exchange-shipped` | `ADMIN` | Planned | Mark exchange shipment |
| `GET` | `/api/admin/refunds` | `ADMIN` | Planned | Refund queue |
| `POST` | `/api/admin/refunds/{refundId}/approve` | `ADMIN` | Planned | Approve refund execution |
| `POST` | `/api/admin/refunds/{refundId}/request-pg-cancel` | `ADMIN` | Planned | Request PG cancel/refund |
| `POST` | `/api/admin/refunds/{refundId}/retry` | `ADMIN` | Planned | Retry failed refund |
| `POST` | `/api/admin/refunds/{refundId}/manual-review` | `ADMIN` | Planned | Mark manual review result |

Rules:

- Customer self-service cancel is allowed only while `SUPPLIER_ORDER_PENDING` and supplier work has not started.
- After supplier work starts, cancellation becomes a claim.
- Return and exchange are claims.
- Refund completion requires PG cancel/refund success.
- Delivery-group order level partial refund is supported.
- Product, option, and quantity-level partial refund inside one delivery-group order is excluded.

## Policy And Legal APIs

| Method | Path | Auth | Status | Purpose |
| --- | --- | --- | --- | --- |
| `GET` | `/api/policies/{type}/current` | Public | Planned | Active policy document by type |
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
  "policyNotice": {
    "shipping": "Policy text or reference",
    "cancellationRefund": "Policy text or reference",
    "outOfStock": "Policy text or reference"
  }
}
```

DS-6 should keep request/response DTOs separate from JPA entities.

## Open API Notes

- Final error response format is not defined.
- Pagination format is not defined.
- Image upload binary flow is not defined; DS-6 can start with image URL/object key metadata.
- OAuth token/session format is not defined.
- Public product APIs are public, but checkout/cart/order APIs require authentication.
