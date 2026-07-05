# Performance And Security Baseline

## 2026-07-05 Pre-Upgrade Baseline

- Target: `https://coreable-saf.com`
- Infra: Cloudflare proxied -> nginx -> Next.js/Spring Boot/PostgreSQL on EC2 `t4g.micro` 1GB
- Code baseline: `4f3dc00`
- Time: 2026-07-05 14:01-14:13 KST
- Tools: k6 `v1.6.1`, Lighthouse `13.4.0`, OWASP ZAP `2.17.0`
- Scope: measurement only. No order, signup, inquiry, active security scan, or server write action was executed.

## B-037 Load Smoke

Command:

```bash
k6 run --summary-export tmp/perf-security/k6-summary.json scripts/load/k6-smoke.js
```

Scenario:

- Ramp from 5 VU for 1 minute to 20 VU for 2 minutes, then ramp down for 10 seconds.
- Public targets only: `/`, `/products`, `/products/{id}`, `/api/products`, `/api/health`.
- Product detail IDs were resolved from the public product API during setup.

Cloudflare cache check:

| Target | `cf-cache-status` | Interpretation |
| --- | --- | --- |
| `/` | `DYNAMIC` | HTML reached origin path. |
| `/products` | `DYNAMIC` | HTML reached origin path. |
| `/products/{id}` | `DYNAMIC` | HTML reached origin path. |
| `/api/products` | `DYNAMIC` | API reached origin path. |
| `/api/health` | `DYNAMIC` | API reached origin path. |

Result:

| Metric | Value |
| --- | ---: |
| Requests | 1,173 |
| RPS | 6.15/s |
| 5xx rate | 0.00% |
| HTTP failure rate | 0.00% |
| p50 | 356.10ms |
| p95 | 511.18ms |
| p99 | 887.50ms |
| max | 10.41s |

Server snapshots during load:

| Time (KST) | Available memory | Swap used | Web memory | API memory | Notes |
| --- | ---: | ---: | ---: | ---: | --- |
| 14:01:40 | 314MB | 296MB | 52.43MiB / 192MiB | 228.1MiB / 512MiB | Start of load. |
| 14:02:43 | 281MB | 293MB | 75.6MiB / 192MiB | 236.3MiB / 512MiB | Ramp in progress. |
| 14:03:47 | 191MB | 301MB | 81.74MiB / 192MiB | 324.2MiB / 512MiB | Peak load window. |
| 14:04:52 | 180MB | 317MB | 97.41MiB / 192MiB | 317.2MiB / 512MiB | After ramp down. |

Verdict:

- Pass for basic public smoke: 20 VU completed without 5xx and p95 stayed below 1 second.
- Capacity warning remains: `t4g.micro` memory is tight. This baseline supports comparing a later `t4g.small` upgrade, especially available memory and swap usage.

## B-038 Lighthouse Mobile Baseline

Command shape:

```bash
npx --yes lighthouse@latest <url> \
  --only-categories=performance,accessibility,best-practices,seo \
  --chrome-flags="--headless=new --no-sandbox" \
  --output=json
```

Scores:

| Page | Performance | Accessibility | Best Practices | SEO | FCP | LCP | TBT | CLS |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `/` | 60 | 92 | 100 | 92 | 6.0s | 24.3s | 10ms | 0.001 |
| `/products` | 73 | 92 | 100 | 100 | 2.0s | 19.9s | 0ms | 0.005 |
| `/products/d46846a3-c743-4fe4-8b9b-45383be1d000` | 73 | 93 | 100 | 100 | 2.0s | 18.8s | 0ms | 0.004 |

Top findings:

| Page | Finding | Impact |
| --- | --- | --- |
| `/` | Largest Contentful Paint `24.3s` | Main performance bottleneck. Likely large hero/image/render work on mobile. |
| `/` | Time to Interactive `24.4s` | Initial page is slow to become fully interactive under Lighthouse throttling. |
| `/`, `/products`, product detail | Color contrast warning | Accessibility polish needed before final design QA. |
| `/products`, product detail | Accessible name mismatch | Some visible labels/buttons do not match accessible names. |

Verdict:

- No functional failure was detected.
- Performance needs optimization before a serious launch push, but this work is a baseline only. LCP is the main number to improve and compare after image/font/render tuning.

## B-038 ZAP Baseline And Headers

Command:

```bash
docker run --rm zaproxy/zap-stable zap-baseline.py -t https://coreable-saf.com
```

ZAP summary:

- URLs scanned: 224
- `FAIL-NEW`: 0
- `WARN-NEW`: 15
- `PASS`: 52
- Active scan: not run

Warnings and launch-gate judgement:

| Severity | Alert | Count | Launch-gate judgement |
| --- | --- | ---: | --- |
| Medium | Content Security Policy Header Not Set | 5 | Not an immediate test-deploy blocker, but should be fixed before real payment traffic. |
| Medium | Missing Anti-clickjacking Header | 5 | Not an immediate blocker because sensitive pages require auth/API checks, but `X-Frame-Options` or CSP `frame-ancestors` should be added to HTML before real payment. |
| Medium | Absence of Anti-CSRF Tokens | 5 | Needs manual review. Product/support pages contain forms, but backend auth/session rules and SameSite cookies must be checked before real payment. |
| Low | Strict-Transport-Security Header Not Set | 5 | Not an immediate blocker while HTTPS works, but HSTS should be enabled before live commerce. |
| Low | X-Content-Type-Options Header Missing | 5 | Not an immediate blocker; add consistently for HTML/static assets. API already sends `nosniff`. |
| Low | X-Powered-By Header Leaks Next.js | 5 | Not a blocker; remove as hardening. |
| Low | Permissions/COOP/COEP/CORP headers missing | 12 | Not a blocker for current app, but hardening candidate. |
| Low | Big Redirect Detected | 3 | Not a blocker; login redirects with `redirectTo` should remain on same-origin allowlist. |
| Informational | Cache-control, modern web app, retrieved/storable content, session response | 32 | Not a blocker; record only. |

Manual header check:

| URL | HSTS | X-Content-Type-Options | X-Frame-Options | CSP | Notes |
| --- | --- | --- | --- | --- | --- |
| `/` | Missing | Missing | Missing | Missing | HTML includes `x-powered-by: Next.js`. |
| `/api/health` | Missing | `nosniff` | `DENY` | Missing | API security headers are stronger than HTML. |

Verdict:

- No ZAP `FAIL` item and no clear active exploit evidence were found in passive baseline.
- Security header hardening is the main follow-up before real payment launch. Candidate issue: `B-058 실결제 전 보안 헤더 hardening`.
