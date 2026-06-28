# QA Report

Date: 2026-06-29 KST
Scope: storefront, auth-aware navigation, checkout entry, admin dashboard/list pages

## Verdict

Status: REVISE

The service is usable for catalog browsing and auth-gated page entry, but QA found state-dependent UI defects that would confuse real users and operators. Admin mock-looking operational data was removed during DS-75. Full OAuth provider completion still needs manual account-login verification in Chrome.

## Environment

- Branch: `feature/ds-76-oauth-checkout-readiness`
- Base commit: `70b347f`
- Node: `v25.9.0`
- API: `http://localhost:8080`
- Web: `http://localhost:3000`
- DB: `dropship-shop-postgres` using `postgres:17-alpine`
- Test data: local seed catalog enabled

## Checks Performed

- Anonymous storefront route smoke test: `/`, `/products`, `/login`, `/account`, `/cart`, `/orders`, `/checkout`
- Authenticated customer SSR smoke test using a temporary local QA user cookie
- Authenticated admin SSR smoke test using a temporary local QA admin cookie
- Admin API sanity check: `/api/admin/products`, `/api/admin/suppliers`, `/api/admin/orders`
- OAuth entry check: Kakao, Google, Naver authorize redirects return provider 302 responses
- Checkout/payment API preflight: cookie login, required agreement, cart item add, checkout creation, checkout policy confirmation, local Toss exception path
- Build checks:
  - `npm run lint`: passed
  - `npm run build`: passed

Temporary QA users and their generated empty carts were removed after verification.

## Findings

### QA-001: Login Menu Remained Visible After Login

Severity: High
Status: Fixed in working tree

Repro:
1. Log in as a normal customer.
2. View the global header.

Expected:
- Header shows logged-in affordances such as `내 계정` and `로그아웃`.
- `로그인` is hidden.
- `관리자` is hidden for a normal customer.

Actual:
- `로그인` remained visible because the root layout only checked `getAdminUser()`.

Fix:
- Root layout now reads both `getCurrentUser()` and `getAdminUser()`.
- Logged-in customers see `내 계정` and `로그아웃`.
- Admin link remains role-gated.

Changed files:
- `apps/web/src/app/layout.tsx`
- `apps/web/src/app/globals.css`

### QA-002: Admin Orders Showed Mock Orders When API Returned Empty List

Severity: Critical
Status: Fixed in working tree

Repro:
1. Log in as admin.
2. Open `/admin` or `/admin/orders`.
3. Backend returns `{"orders":[]}` from `/api/admin/orders`.

Expected:
- Admin UI shows zero orders.

Actual:
- UI showed hardcoded sample orders such as `20240522-000123`, creating false operational data.

Fix:
- Admin frontend no longer falls back to mock products/suppliers/orders when API returns an empty successful response.
- Admin orders now show `0건` when backend has no orders.

Changed file:
- `apps/web/src/lib/admin.ts`

### QA-003: Empty Cart Checkout Asked For Policy Agreement First

Severity: Medium
Status: Fixed in working tree

Repro:
1. Log in as customer with an empty cart.
2. Open `/checkout`.

Expected:
- User sees `장바구니가 비어 있습니다` and a path back to products.

Actual:
- Checkout showed required policy agreement before telling the user the cart was empty.

Fix:
- Empty cart state is now shown before agreement collection.
- Policy agreement form appears only when cart has items.

Changed file:
- `apps/web/src/app/checkout/page.tsx`

### QA-005: Product Images Broke After Moving Seed Fixtures To Upload URLs

Severity: High
Status: Fixed in DS-74 working tree

Repro:
1. Use local seed product image URLs such as `/uploads/products/local-seed/helmet-thumb.png`.
2. Open the storefront home, product list, or cart.

Expected:
- Product images render from the API upload-serving path.

Actual:
- Browser image requests either targeted the web origin or received API `401`.
- Cart item images rendered at their original 640px size once the image URL became a real PNG.

Fix:
- Shared `ProductImage` now resolves `/uploads/**` paths through the API base URL.
- API security permits public reads for `/uploads/products/**`.
- Cart item images are constrained to the cart grid size.

Changed files:
- `apps/web/src/app/products/product-image.tsx`
- `apps/web/src/app/globals.css`
- `apps/api/src/main/java/com/dropshipshop/api/auth/security/SecurityConfig.java`
- `apps/api/src/test/java/com/dropshipshop/api/dev/LocalCatalogSeedDataTest.java`

### QA-006: Admin Dashboard And Filters Showed Mock-Looking Operational Data

Severity: Medium
Status: Fixed in DS-75 working tree

Repro:
1. Log in as admin with an empty order list.
2. Open `/admin`, `/admin/products`, and `/admin/orders`.

Expected:
- Admin dashboard and filters reflect current API data only.
- Empty order state does not show fake sales trends or dated search criteria.

Actual:
- Dashboard showed a hardcoded seven-day revenue bar chart while orders were `0건`.
- Product management showed hardcoded pagination `1 2 3 ... 13`.
- Order management prefilled 2024 date filters.

Fix:
- Dashboard now shows current real counts instead of fake sales bars.
- Product status/search filters now filter the server-rendered list.
- Order filters now submit real query params without default dates.
- Hardcoded pagination was removed until real pagination exists.

Verification:
- `npm run lint`: passed
- `npm run build`: passed
- Admin route smoke passed for anonymous, customer, admin, product filter, product empty search, order empty state, and order status filter.

### QA-007: Checkout Detail Kept Showing Actions After Payment Left Pending State

Severity: Medium
Status: Fixed in DS-76 working tree

Repro:
1. Create checkout and confirm checkout policies.
2. Trigger a local Toss confirm failure so the payment group enters `PAYMENT_EXCEPTION`.
3. Open `/checkout/{checkoutNumber}`.

Expected:
- Checkout detail shows only actions that are valid for the current payment group state.
- Payment exception state does not keep showing shipping edit and payment approval forms.

Actual:
- Shipping, policy confirmation, and Toss confirmation forms were still visible after the checkout left `PAYMENT_PENDING`.
- Policy confirmation form also remained visible after policy confirmation.

Fix:
- Checkout detail now shows shipping edit only while `PAYMENT_PENDING`.
- Policy confirmation form is hidden after `policyConfirmedAt` exists.
- Toss confirmation form is shown only when the checkout is pending and policy-confirmed.
- Non-pending checkout shows a locked notice instead of retryable forms.

Verification:
- Google/Kakao/Naver authorize endpoints returned 302 to provider domains.
- Cookie login returned `200` on `/api/me`.
- Checkout preflight passed through required agreement, cart add, checkout create, and checkout policy confirmation.
- Local fake Toss confirm returned the payment exception path.
- Web route smoke passed for `/checkout`, `/checkout/{checkoutNumber}`, `/checkout/payment/exception`, and `/checkout/payment/fail`.
- `npm run lint`: passed
- `npm run build`: passed

## Remaining Risks

### QA-004: Real OAuth Completion Still Needs Manual Browser Verification

Severity: High
Status: Open

Verified:
- Kakao, Google, Naver authorize endpoints route to each provider authorize/login screen.

Not yet fully verified:
- Provider login and consent submission.
- Callback return to `/auth/callback/success`.
- Success page session confirmation.
- `/account` with the real browser cookie.
- Normal customer header hiding `관리자`.

Reason:
- These steps require real account login/consent in Chrome. Automated page text inspection is currently limited because Chrome Apple Events JavaScript execution is disabled.

### QA-008: Toss Sandbox Payment Completion Still Needs Real Keys And Browser Checkout

Severity: High
Status: Open

Verified:
- Checkout creation and policy confirmation are functional with cookie auth.
- Payment exception path is functional when server confirmation fails.

Not yet fully verified:
- Toss widget/client-key browser payment.
- Real Toss sandbox `paymentKey` confirmation.
- Redirect from Toss success URL to server confirmation.

Reason:
- Local `.env` does not currently include Toss Payments keys. No secret or client key was printed or committed.

## Current Working Tree Changes

- `apps/web/src/app/checkout/[checkoutNumber]/page.tsx`
- `docs/QA_REPORT.md`
- `docs/production-readiness.md`

## Recommendation

Commit the DS-76 checkout readiness fix, then do manual browser OAuth callback and Toss sandbox verification after real provider accounts/keys are available.
