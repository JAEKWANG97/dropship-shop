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
```

The backend local defaults redirect successful OAuth login to
`http://localhost:3000/auth/callback/success`. For browser-side API calls in
local development, allow `http://localhost:3000` in the API CORS setting.
