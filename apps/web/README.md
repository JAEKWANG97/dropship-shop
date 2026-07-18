# Dropship Shop Web

Next.js App Router frontend for the Dropship Shop MVP.

## Commands

```bash
npm install
npm run dev
npm run lint
npm run build
```

## Environment

Copy `.env.example` to `.env.local` when running against a non-default API.

```bash
DROPSHIP_API_BASE_URL=http://localhost:8080
NEXT_PUBLIC_DROPSHIP_API_BASE_URL=
```

The backend local defaults redirect successful OAuth login to
`http://localhost:3000/auth/callback/success`. Browser requests use the
same-origin `/api` rewrite unless `NEXT_PUBLIC_DROPSHIP_API_BASE_URL` is set.

## Playwright E2E

Local E2E runs against the local API and the B-003 seed orders. Start PostgreSQL
and the API with seed data first:

```bash
cd ../../infra/local/postgres
docker compose up -d

cd ../../../apps/api
SPRING_PROFILES_ACTIVE=local APP_SEED_ENABLED=true ./gradlew bootRun
```

If your local PostgreSQL container exposes a non-default host port, pass it to
the API command, for example `DB_PORT=55432`.

The local profile exposes a dev-only login endpoint for the B-003 seed
customer/admin. Open these URLs in the browser when you want to switch the
current local session:

```bash
open "http://localhost:8080/api/dev/login?role=CUSTOMER"
open "http://localhost:8080/api/dev/login?role=ADMIN"
```

For curl-based checks, capture the `Set-Cookie` header from the same endpoint:

```bash
curl -i "http://localhost:8080/api/dev/login?role=CUSTOMER"
curl -i -X POST "http://localhost:8080/api/dev/login" \
  -H "Content-Type: application/json" \
  -d '{"providerUserId":"local-b003-admin"}'
```

Run smoke tests from `apps/web`. When `E2E_CUSTOMER_COOKIE` or
`E2E_ADMIN_COOKIE` is omitted, Playwright gets the local seed cookie from
`/api/dev/login`. For non-local targets, pass the cookie explicitly. Playwright
starts `npm run dev` automatically when `http://localhost:3000` is not already
running.

```bash
npm run test:e2e
```

Deployment smoke uses only public routes, public health, and the dev-login
404 guard. It does not require auth cookies, seed orders, or screenshots:

```bash
E2E_WEB_BASE_URL=https://coreable-saf.com npx playwright test deploy-smoke
```

Screenshot baselines are intentional artifacts. Update them only after visually
checking the changed screens. Snapshot specs are local-seed only and skip
automatically when `E2E_WEB_BASE_URL` is not localhost:

```bash
npx playwright test tests/e2e/visual-regression.spec.ts --project=desktop --update-snapshots
npx playwright test tests/e2e/visual-regression.spec.ts --project=desktop
```
