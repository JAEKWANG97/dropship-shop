# Roadmap

## Phase 0: Product And Domain Design

- Confirm product model
- Confirm order and payment state model
- Confirm admin workflow
- Confirm cancellation/refund policy
- Choose PG provider
- Choose frontend approach

Exit criteria:

- Domain model is documented.
- Order flow is documented.
- MVP scope and non-goals are documented.

## Phase 1: Backend Foundation

- Create Spring Boot project
- Configure PostgreSQL
- Add auth and roles
- Implement product/supplier model
- Implement order model
- Implement payment model
- Add basic admin APIs

Exit criteria:

- Admin can create products and options.
- Customer can create a pending order.
- Order amount is calculated on the server.

## Phase 2: Checkout MVP

- Implement cart
- Implement checkout
- Integrate PG sandbox
- Verify payment success on server
- Handle failed payments
- Handle duplicate payment confirmation

Exit criteria:

- Customer can complete a sandbox payment.
- Confirmed order enters supplier order pending state.

## Phase 3: Admin Fulfillment

- Build order queue
- Build order detail
- Add supplier order completed action
- Add out-of-stock action
- Add tracking number input
- Add customer order status view

Exit criteria:

- Admin can process a paid order through supplier order and shipment.
- Admin can mark supplier out of stock and send order to refund flow.

## Phase 4: Refund And Policy

- Implement cancellation request
- Implement refund request
- Integrate PG refund/cancel API
- Add customer-facing policy pages
- Add admin refund handling

Exit criteria:

- Cancellation and refund flows are traceable and visible.

## Phase 5: Launch Preparation

- Production deployment
- Logging and monitoring
- Admin audit history
- Basic SEO
- Email or notification integration
- Terms, privacy policy, refund policy review

Exit criteria:

- Real orders can be accepted with known operational procedures.

## Later

- Supplier CSV import
- Product bulk upload
- Coupon
- Review
- Basic search improvements
- Dashboard metrics
- Automated supplier API integration
- Customer notifications

