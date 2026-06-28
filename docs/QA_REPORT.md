# QA Report

Date: 2026-06-29 KST
Scope: storefront, auth-aware navigation, checkout entry, admin dashboard/list pages

## Verdict

Status: REVISE

The service is usable for catalog browsing and auth-gated page entry, but QA found state-dependent UI defects that would confuse real users and operators. Three small defects were fixed during this pass. Full OAuth provider completion still needs manual account-login verification in Chrome.

## Environment

- Branch: `main`
- Base commit: `4a411c5`
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
- OAuth entry check: Kakao, Google, Naver authorize redirects were previously verified
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

## Remaining Risks

### QA-004: Real OAuth Completion Still Needs Manual Browser Verification

Severity: High
Status: Open

Verified:
- Kakao, Google, Naver buttons route to each provider authorize/login screen.

Not yet fully verified:
- Provider login and consent submission.
- Callback return to `/auth/callback/success`.
- Success page session confirmation.
- `/account` with the real browser cookie.
- Normal customer header hiding `관리자`.

Reason:
- These steps require real account login/consent in Chrome. Automated page text inspection is currently limited because Chrome Apple Events JavaScript execution is disabled.

## Current Working Tree Changes

- `apps/web/src/app/layout.tsx`
- `apps/web/src/app/globals.css`
- `apps/web/src/lib/admin.ts`
- `apps/web/src/app/checkout/page.tsx`

## Recommendation

Commit the fixed QA defects as one frontend bugfix commit after a quick browser refresh check. Then continue manual OAuth verification provider by provider.
