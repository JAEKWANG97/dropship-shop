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

Do not manage real stock quantity in MVP. Use product and option sales status instead.

Context:

The site assumes products are available and discovers supplier stock issues after payment/order processing.

Consequences:

- Product status must support active, sold out, hidden, and stopped.
- Supplier out-of-stock flow must be designed.
- Customer-facing policy must explain possible post-order stock issues.

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

