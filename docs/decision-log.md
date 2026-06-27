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

Payment needs an internal order anchor so the server can calculate the amount, pass a stable order identifier to the PG flow, and verify the PG-approved amount against the server-side order amount.

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
- Payment method policy still needs to decide which Toss-supported methods are enabled for MVP.
- Virtual account/bank-transfer-like flows require separate async deposit state handling if included.

## 2026-06-27: MVP Payment Methods

Decision:

Enable Toss Payments card, easy payment, and account transfer for MVP. Exclude virtual account/bank-transfer-like async payment, mobile phone payment, and gift certificate payment from MVP. Do not support partial cancellation in MVP. Do not show failed, pending, or expired payment orders in customer order history.

Context:

Card, easy payment, and account transfer fit the current synchronous payment confirmation model: `PAYMENT_PENDING` -> server verification -> `SUPPLIER_ORDER_PENDING`. Virtual account style payment requires account-issued, waiting-for-deposit, deposit-completed, and deposit-expired states, which would complicate the MVP order/payment model.

Consequences:

- MVP payment method enum can start with card, easy pay, and transfer.
- Virtual account state handling is deferred.
- Partial cancel/refund complexity is deferred.
- Refund policy starts with full-order cancellation/refund.
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

Multi-supplier orders introduce partial stock-out, partial shipment, multiple tracking numbers, and partial refund complexity. MVP payment and refund policy intentionally excludes partial cancellation/refund, so order splitting by delivery group keeps fulfillment and refund rules consistent.

Consequences:

- Cart can contain multiple delivery groups.
- Checkout must group items by supplier-backed delivery group.
- Each delivery group creates a separate order.
- Shipping fee remains `0` for all delivery groups in MVP.
- Customer order history may show multiple orders from one cart checkout.
- Future marketplace-like combined orders require partial cancellation/refund and multi-shipment support.

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
