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
- payments
- fulfillments
- shipments
- refunds
- order_status_histories

## Frontend

Use React or Next.js.

Initial route groups:

```text
/
/products
/products/:id
/cart
/checkout
/orders
/orders/:id
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

The server must verify payment result directly with the PG before approving an order.

### Object Storage

Product images should be stored outside the application server.

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

