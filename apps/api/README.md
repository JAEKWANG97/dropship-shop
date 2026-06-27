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

## Tests

```sh
cd apps/api
./gradlew test
```

The test profile uses an in-memory H2 database in PostgreSQL compatibility mode.
