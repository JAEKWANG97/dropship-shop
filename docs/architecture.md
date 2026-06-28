# Architecture

## Initial Architecture

```text
Browser
  -> Frontend
  -> Spring Boot API
  -> PostgreSQL
  -> Object Storage
  -> Payment Gateway
```

## Repository Structure

Use a monorepo for MVP development.

Planned structure:

```text
dropship-shop/
  apps/
    web/        Next.js frontend
    api/        Spring Boot backend
  docs/
  infra/
  .github/
```

Rationale:

- The project is developed by a single developer.
- Frontend, backend, docs, and infra decisions are tightly coupled during MVP development.
- One Linear issue can map to one PR even when the change touches both frontend and backend.
- Splitting repositories can be revisited if release cadence, team ownership, or security boundaries require it later.

## Backend

Use a Spring Boot modular monolith for the MVP.

Suggested modules/packages:

```text
auth
user
catalog
cart
order
payment
fulfillment
shipment
refund
policy
admin
common
```

## Database

Use PostgreSQL as the primary source of truth.

Primary data:

- users
- suppliers
- products
- product_options
- carts
- cart_items
- orders
- order_items
- payment_groups
- payments
- payment_events
- fulfillments
- shipments
- refunds
- order_status_histories

## Frontend

Use Next.js.

Initial route groups:

```text
/
/products
/products/:id
/cart
/checkout
/orders
/orders/:id
/policies/:slug
/admin
/admin/products
/admin/orders
/admin/suppliers
```

## Admin

Admin should not be treated as an afterthought. For this business model, admin workflows are part of the core product.

First admin screens:

- Dashboard
- Product list
- Product editor
- Supplier list
- Order queue
- Order detail
- Fulfillment action panel
- Refund action panel

## External Services

### Payment Gateway

Use Toss Payments for MVP.

The server must verify payment result directly with Toss Payments before approving an order.

### Object Storage

Product images should be stored outside the application server.

Runtime storage rules:

- PostgreSQL stores product data and image metadata only.
- Image binaries are not stored in PostgreSQL.
- Local development may use filesystem-backed product image storage.
- Production should use S3-compatible object storage and return stable image URLs to the frontend.
- Frontend catalog screens should consume backend API data instead of maintaining long-lived mock product JSON.

### Supplier

MVP supplier operation is manual.

Later expansion:

- Supplier CSV import
- Supplier API integration
- Automated purchase order export
- Supplier stock polling

## Security Notes

- Admin APIs require admin role.
- Customer APIs must scope data by authenticated user.
- Do not log payment secrets, personal identifiers, or raw PG payloads without filtering.
- Keep PG secret keys only on the server.
- Use HTTPS in production.

## Deployment Notes

Start simple:

```text
Single server
+ Spring Boot app
+ managed PostgreSQL
+ object storage
+ CDN later if needed
```

Do not introduce microservices before order and fulfillment workflows are stable.

Production baseline is tracked in [Production Readiness](production-readiness.md).

- Run the API with `SPRING_PROFILES_ACTIVE=prod`.
- Use managed PostgreSQL with automated backup and point-in-time recovery before live payments.
- Run Flyway migrations for schema changes; production Hibernate mode is `validate`.
- Use `/actuator/health/readiness` for readiness probes and `/actuator/health/liveness` for liveness probes.
- Configure CORS through `APP_CORS_ALLOWED_ORIGINS` with only the deployed customer/admin frontend origins.
- Keep Toss Payments secret key and DB credentials in runtime environment or secret manager only.
