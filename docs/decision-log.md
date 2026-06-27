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
