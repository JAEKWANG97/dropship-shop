# Architecture

## Current Architecture

```text
Browser
  -> Cloudflare
  -> nginx on EC2
     -> Next.js web container
     -> Spring Boot API container
        -> PostgreSQL container on EBS
        -> EBS-backed product uploads
        -> Domeggook Private API
        -> AWS SES
  -> Backup job copies database and uploads to S3
```

이 다이어그램은 현재 구현 기준이다. 공급처 포털의 `B-100` 신청·초대·Kakao 연결·lifecycle, `B-101` 개별 상품·검토, `B-102` 옵션 재고·24시간 예약·계좌입금 예외 Web/API와 additive schema는 같은 Next.js web, Spring Boot modular monolith와 PostgreSQL 안에 구현됐다. `B-103`~`B-105`는 Planned 확장이며 별도 판매자 서비스, 정산 시스템 또는 결제 시스템을 만들지 않는다.

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
- One backlog item normally maps to one commit; larger reviewable changes may use one PR.
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

Supplier portal data (`B-100`~`B-102` Implemented, `B-103`~`B-105` Planned):

- public supplier applications and one-time invitation digests — Implemented B-100
- one active supplier manager link per supplier — Implemented B-100
- product review state separated from existing compliance state — Implemented B-101
- `TRACKED` option inventory and order-item reservation lifecycle — Implemented B-102
- fulfillment channel/owner/handover additive base — Implemented B-100; portal request timestamp behavior, minimal supplier PII access logs and append-only claim access grants — Planned B-103
- multiple shipments with item quantity allocations and correction/void/delivery evidence — Planned B-104
- shortage reports, Coreable-owned claim tasks and append-only supplier claim facts — Planned B-105; operational supplier email audit — Planned B-103

V39 implements the B-100 schema extensions, V40 implements the B-101 catalog/review extensions, and V41 implements the B-102 inventory, reservation, payment-command replay and received-payment exception extensions without changing legacy behavior. B-103 and later fulfillment/shipment changes remain Planned and will use expand-contract migrations.

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

Supplier portal routes:

```text
/supplier/apply                 Implemented B-100 public application
/supplier/activate              Implemented B-100 one-time invitation exchange and Kakao login
/supplier                       Implemented B-100 supplier home
/supplier/products              Implemented B-101 product list
/supplier/products/new          Implemented B-101 individual product registration
/supplier/products/:productId   Implemented B-101 product edit/resubmit
/supplier/orders                Planned B-103 fulfillment request queue
/supplier/orders/:orderNumber   Planned B-103 minimum-PII detail and B-104 tracking registration
/supplier/claim-tasks           Planned B-105 Coreable-requested safe fact tasks
/admin/product-reviews          Implemented B-101 review queue
/admin/product-reviews/:productId Implemented B-101 review detail/action
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

## Supplier Portal — `B-100`~`B-102` Implemented, `B-103`~`B-105` Planned

The portal is a tenant-scoped operational surface for approved suppliers. Coreable remains the only customer-facing seller and keeps customer price, payment, refund, CS, claim decisions and final product control. The portal has no supplier settlement or seller-led customer transaction flow.

```text
Public applicant
  -> Coreable approval
  -> one-time email invitation
  -> Kakao-only login
  -> one active manager for one supplier in the first version

Supplier manager
  -> own individual products and tracked option inventory
  -> own paid fulfillment requests
  -> item-allocated tracking registrations
  -> whole-delivery-group shortage reports
  -> Coreable-requested claim facts
```

Implementation boundaries:

- `B-100` reuses the current OAuth/JWT cookie stack but derives supplier authority from an active user, active portal status and the manager link. `Supplier.status` independently gates new sales, not access to already-paid fulfillment. Suspension/disconnect persists Coreable operational ownership for open work. Planned B-103 routes new paid work to `COREABLE_MANUAL` under KEEP until access returns. Existing `CUSTOMER` or `ADMIN` roles are not replaced.
- `B-101` reuses catalog, image storage, product notice, sanitize, pricing and audit boundaries. A no-option product receives one internal `기본` option. Only a never-submitted, unreferenced portal DRAFT can be hard-deleted under the common `Supplier -> fresh Product -> Option(id)` lock contract; submitted/used rows remain hidden or stopped, while immutable subject ids and durable image-cleanup jobs preserve audit/storage integrity. One visible registration action auto-publishes ordinary valid products and queues only flagged products for Coreable review. A Coreable approval for `CERTIFICATION_REVIEW` completes only portal human review and does not change compliance: legacy `PENDING` still permits sale and only `REJECTED` blocks readiness. B-102 inventory support is necessary but does not by itself open production sale.
- Admin/review/cart/checkout/source writers perform scalar supplier/ownership discovery before locking Supplier, reloading a fresh Product, and locking Options by id. They revalidate ownership after lock waits and return the appropriate conflict or tenant-safe not-found instead of committing with stale ownership.
- Domeggook applies either a fetched snapshot or its failure only while the fetched `sourceItemNo` still equals the fresh locked Product identity. V40 defaults/backfills durable `sourceAutoSoldOut` to false and sync sets it only on an actual confirmed-unavailable `ACTIVE -> SOLD_OUT` transition. Targeting and recovery require that provenance marker; recovery clears it only after source MOQ is at most 10 and current capped positive price, non-rejected compliance, active Option, thumbnail, and active notice readiness all pass. Any successful admin status command, including the same `SOLD_OUT`, clears the marker so manual sold-out state remains protected.
- Image cleanup keys are single-use tombstones once a job exists. Enqueue is unique and idempotent; the worker checks live ProductImage references immediately before deletion, completes without deleting as `LIVE_REFERENCE` when one exists, and the later metadata-removal enqueue reopens the same job.
- `B-102` defaults new portal options to `TRACKED` with 24-hour reservations while allowing an explicit `UNTRACKED` choice. Existing manual/Domeggook options remain `UNTRACKED`. It also owns the shared bank-transfer exception boundary: a mismatched receipt becomes one actual-amount PaymentGroup Refund with no fulfillment, while exact late/saleability failures keep order-scoped Refunds. An exact receipt found after qualifying unpaid cancellation also uses order-scoped Refunds but never reacquires or revives the checkout, for portal and legacy groups alike.
- `B-103` creates the portal fulfillment request and address lock in the successful deposit-confirmation transaction. There is no supplier accept/reject step.
- `B-104` expands Shipment from the legacy single record to multiple records with immutable item allocations for portal orders. Tracking registration uses `TRACKING_REGISTERED`, generates an official carrier URL and does not call a live carrier-status API. Supplier carrier/tracking correction and Coreable void/delivery-complete/guarded delivery-correction actions are idempotent, preserve evidence and recalculate the Order aggregate.
- `B-105` accepts shortage only before any tracking registration and lets suppliers answer only Coreable-created claim tasks without granting refund or claim-decision authority.

Supplier order lists contain no customer PII. Supplier order detail returns only the recipient and address data needed for the owning supplier's delivery, for a limited access window, and every access is audited without copying PII values into the log.

Supplier operational notifications use verified email only. The invitation is the sole pre-verification contact-validation message and contains only the token/link and generic connection instructions. Later fulfillment-request, product-review and approved claim-task messages include identifiers and portal links but no customer name, phone, address, delivery memo, payment or refund data.

## External Services

### Customer Payment

Use direct bank transfer with manual admin deposit confirmation for the current customer checkout path.
No PG runtime or PG secret is used. Historical PG enum values and migrations remain only for existing-data compatibility.

### Object Storage

Product images should be stored outside the application server.

Runtime storage rules:

- PostgreSQL stores product data and image metadata only.
- Image binaries are not stored in PostgreSQL.
- Local development may use filesystem-backed product image storage.
- The initial production-style deployment uses the EC2 EBS-backed local upload volume for product images and copies them to S3 as backup data.
- Move serving to S3-compatible object storage only when image volume, multi-server deployment, recovery time, or traffic makes local disk risky.
- Backend file storage is behind a small storage boundary so the API can keep returning stable image URLs to the frontend.
- Product-image cleanup never relies only on an earlier metadata snapshot: a tombstoned key cannot be attached again, and the worker checks its live metadata reference immediately before deleting the binary.
- Frontend catalog screens should consume backend API data instead of maintaining long-lived mock product JSON.

### Supplier

- Domeggook source snapshot orders use the approved Private API after customer deposit confirmation and pay with prefunded e-money.
- The API revalidates item, option, source price, shipping, and e-money immediately before purchase.
- Orders without a supported source snapshot stay on the manual supplier-order path.
- The B-100 supplier onboarding portal is an authenticated first-party workflow inside the existing application, not another supplier purchasing API. Implemented B-101 adds supplier-managed catalog/review; Implemented B-102 adds `TRACKED` inventory and reservations while preserving existing manual/Domeggook `UNTRACKED` behavior.
- Additional automated supplier purchasing APIs and live carrier-status APIs are not part of `B-100`~`B-105`.

## Security Notes

- Admin APIs require admin role.
- Customer APIs must scope data by authenticated user.
- B-100 dynamically derives supplier authority from an active user, active portal status and manager link. Implemented B-101 catalog and B-102 inventory APIs scope supplier resources by both resource id and supplier id; Planned B-103~B-105 operational resource APIs must preserve the same boundary. `Supplier.status` separately gates new catalog sales/checkouts, and cross-supplier access returns `404` without revealing existence.
- One-time invitation tokens are stored only as digests and must not appear in application logs, email payload snapshots, access logs or Referer values.
- Supplier list responses contain no customer PII. Detail responses expose only delivery-required fields for a bounded period and use `Cache-Control: no-store` on PII-bearing responses.
- Supplier PII access logs record only actor, order, access basis and time, never PII values or the response body.
- Supplier cookie-authenticated unsafe methods require an allowlisted `Origin`; when `Origin` is absent they require a same-origin `Referer`, otherwise return `403`. Production cookies use `HttpOnly`, `Secure`, and `SameSite=Lax`.
- Supplier invitation exchange binds a short-lived HttpOnly cookie to OAuth state and permits only Kakao callback; invite consumption and manager activation are atomic.
- Do not log bank-transfer evidence, supplier credentials, e-money balances, recipient personal information, or raw external API payloads without filtering.
- Keep OAuth, Domeggook, database, email, and backup credentials only in runtime secrets or IAM roles.
- Use HTTPS in production.

## Deployment Notes

Start simple:

```text
Single EC2 server
+ nginx reverse proxy with Cloudflare Origin Certificate
+ Next.js web container
+ Spring Boot API container
+ PostgreSQL container
+ EBS-backed local uploads
+ S3/RDS/CDN later if needed
```

Do not introduce microservices before order and fulfillment workflows are stable.

The supplier portal does not change this deployment topology. New routes, tables, schedulers and email templates ship in the existing web/API containers, and each `B-100`~`B-105` slice must preserve legacy customer/admin and Domeggook contracts.

Production keeps `APP_SUPPLIER_PORTAL_ENABLED=false` by default. External routes and portal-product sale stay disabled, and admin commands or dispatch jobs that would create/send an activation invite fail closed, until B-100 through B-105, required tenant/CSRF/migration/concurrency tests, real email delivery, the new privacy disclosure and B-098 supplier contract/privacy obligations all pass their activation gates. Safety/admin cleanup paths remain available. Even afterward each portal-product public read and checkout requires its Supplier to be ACTIVE with time-valid VERIFIED contract evidence; a global flag never substitutes for that per-supplier gate.

Production baseline is tracked in [Production Readiness](production-readiness.md).

- Run the API with `SPRING_PROFILES_ACTIVE=prod`.
- The initial low-cost deployment may run PostgreSQL on the same EC2 host only while daily S3 backup, EBS snapshot, and restore rehearsal are maintained. Move to managed PostgreSQL when availability or recovery requirements exceed this baseline.
- Run Flyway migrations for schema changes; production Hibernate mode is `validate`.
- Use `/actuator/health/readiness` for readiness probes and `/actuator/health/liveness` for liveness probes.
- Configure CORS through `APP_CORS_ALLOWED_ORIGINS` with only the deployed customer/admin frontend origins.
- Keep DB credentials and application secrets in runtime environment or secret manager only.
