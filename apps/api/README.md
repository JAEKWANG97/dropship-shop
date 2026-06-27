# Dropship Shop API

Spring Boot backend for the Dropship Shop MVP.

## Stack

- Java 21
- Spring Boot 4.1
- Gradle
- PostgreSQL for local and production profiles
- H2 for tests only

## Local Profile

Run the API with the `local` profile:

```sh
cd apps/api
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Default local database settings:

```text
DB_HOST=localhost
DB_PORT=5432
DB_NAME=dropship_shop
DB_USERNAME=dropship
DB_PASSWORD=dropship
```

The local profile reads the values above from environment variables and falls back to those defaults.

## Health Checks

```sh
curl http://localhost:8080/api/health
curl http://localhost:8080/actuator/health
```

## Authentication Foundation

- User accounts live in the `users` table.
- Social identity is keyed by `provider` and `provider_user_id`.
- Supported roles start with `CUSTOMER` and `ADMIN`.
- `/api/admin/**` requires `ADMIN`.
- Customer APIs read the authenticated user id through `CurrentUser`.
- Basic login and form login are disabled; social OAuth/JWT integration is added in later auth work.

## Catalog Foundation

- Public product APIs are available at `/api/products`.
- Admin catalog APIs are available under `/api/admin`.
- Catalog tables include suppliers, products, options, images, detail blocks, notices, and product change histories.
- Product and option sellability uses status fields, not stock quantity.
- Product images and detail images store URL/object-key metadata; binary upload is separate future work.
- Product notice versions support later order item snapshot references.

## Cart Foundation

- Customer cart APIs are available at `/api/cart`.
- Cart tables include one current cart per customer and cart items keyed by product option.
- Adding the same product option increases quantity instead of creating duplicate rows.
- Quantity is limited to 1 through 99.
- Cart APIs require `CUSTOMER`; admin access is forbidden.
- Cart checkout validation blocks empty carts and unavailable product/option states.
- Cart prices are current display prices; order creation snapshots final prices later.

## Checkout And Order Foundation

- Customer checkout APIs are available at `/api/checkouts`.
- Checkout creation is cart-based; direct-buy checkout is deferred.
- Checkout creation creates one payment group and one `PAYMENT_PENDING` order per supplier-backed delivery group.
- Shipping address fields are stored as order snapshots.
- Order items snapshot product name, summary, option name, unit price, detail version, and notice version.
- Server-calculated totals are authoritative; client-submitted totals are ignored.
- Checkout creation empties the cart after successful order creation.
- Policy confirmation is stored through `/api/checkouts/{checkoutNumber}/policy-confirmation`.

## Payment Foundation

- Toss Payments confirmation API is available at `/api/payments/toss/confirm`.
- Toss secret key is read from `payments.toss.secret-key` or `PAYMENTS_TOSS_SECRET_KEY`.
- Payment confirmation verifies checkout ownership, pending state, expiration, policy confirmation, amount, payment key uniqueness, and sellability.
- Successful confirmation moves the payment group to `APPROVED` and orders to `SUPPLIER_ORDER_PENDING`.
- Duplicate confirmation with the same payment key and checkout is idempotent.
- Payment exception paths record `Payment(CANCEL_REQUIRED)` and block supplier ordering.
- Webhooks, payment detail API, automatic cancel execution, and admin retry APIs remain future work.

## Customer Order Foundation

- Customer order APIs are available at `/api/orders`.
- Customer order list and detail are scoped to the authenticated customer.
- `PAYMENT_PENDING` and `EXPIRED` orders are excluded from customer order history.
- Customer responses expose display statuses instead of raw internal order statuses.
- Order detail includes payment group, payment summary, shipping address snapshot, order items, and placeholder fulfillment/shipment/refund summaries.

## Admin Order Foundation

- Admin order queue APIs are available at `/api/admin/orders`.
- Only `ADMIN` users can access admin order APIs.
- `GET /api/admin/orders` returns `SUPPLIER_ORDER_PENDING` orders for supplier order handling.
- `PAYMENT_PENDING` and `EXPIRED` orders are excluded from the supplier order queue.
- Admin order detail includes internal order/payment/fulfillment statuses, supplier info, product and option ids, customer shipping info, and payment summary.
- `POST /api/admin/orders/{orderId}/supplier-work-start` records supplier work start and locks the order shipping address.
- `POST /api/admin/orders/{orderId}/supplier-order-completed` records supplier order evidence and moves the order to `SUPPLIER_ORDERED`.
- `POST /api/admin/orders/{orderId}/out-of-stock` records stockout reason and moves the order to `OUT_OF_STOCK`.
- Supplier order actions write admin order action history rows with before/after order status and reason.

## Tests

```sh
cd apps/api
./gradlew test
```

The test profile uses an in-memory H2 database in PostgreSQL compatibility mode.
