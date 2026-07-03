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
NEXT_PUBLIC_DROPSHIP_API_BASE_URL=http://localhost:8080
NEXT_PUBLIC_TOSS_CLIENT_KEY=
```

The backend local defaults redirect successful OAuth login to
`http://localhost:3000/auth/callback/success`. For browser-side API calls in
local development, allow `http://localhost:3000` in the API CORS setting.
If `NEXT_PUBLIC_DROPSHIP_API_BASE_URL` is omitted in `next dev`, the web app
defaults browser OAuth links to `http://localhost:8080`.
Set `NEXT_PUBLIC_TOSS_CLIENT_KEY` to a Toss Payments test client key for sandbox checkout.

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

Generate local JWT cookies for the B-003 seed customer/admin. Use the same
`APP_AUTH_JWT_SECRET` value that the API uses; if it is not set, the local
profile default is used.

```bash
export JWT_SECRET="${APP_AUTH_JWT_SECRET:-local-dev-jwt-secret-change-before-production}"
export CUSTOMER_ID="$(docker exec dropship-shop-postgres psql -U dropship -d dropship_shop -Atc "select id from users where provider_user_id='local-b003-customer';")"
export ADMIN_ID="$(docker exec dropship-shop-postgres psql -U dropship -d dropship_shop -Atc "select id from users where provider_user_id='local-b003-admin';")"

make_token() {
  node -e 'const crypto=require("crypto"); const sub=process.argv[1]; const secret=process.env.JWT_SECRET; const now=Math.floor(Date.now()/1000); const b64=(value)=>Buffer.from(value).toString("base64url"); const header=b64(JSON.stringify({alg:"HS256",typ:"JWT"})); const payload=b64(JSON.stringify({iss:"dropship-shop-api",sub,iat:now,exp:now+7200})); const sig=crypto.createHmac("sha256",secret).update(`${header}.${payload}`).digest("base64url"); console.log(`${header}.${payload}.${sig}`);' "$1"
}

export E2E_CUSTOMER_COOKIE="ACCESS_TOKEN=$(make_token "$CUSTOMER_ID")"
export E2E_ADMIN_COOKIE="ACCESS_TOKEN=$(make_token "$ADMIN_ID")"
```

Run smoke tests from `apps/web`. Playwright starts `npm run dev` automatically
when `http://localhost:3000` is not already running.

```bash
npm run test:e2e
```

Screenshot baselines are intentional artifacts. Update them only after visually
checking the changed screens:

```bash
npx playwright test tests/e2e/visual-regression.spec.ts --project=desktop --update-snapshots
npx playwright test tests/e2e/visual-regression.spec.ts --project=desktop
```
