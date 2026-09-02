# AWS EC2 Docker Deployment

Status: single-EC2 Docker deployment baseline for B-039

## Runtime Shape

- Region: `ap-northeast-2`
- Instance: `t4g.micro`
- OS: Ubuntu 24.04 ARM64
- Disk: gp3 20GB
- Public IP: Elastic IP `43.200.135.171`
- Runtime: Docker Compose on a single EC2 instance
- Registry: GHCR
- Domain: `coreable-saf.com`, `www.coreable-saf.com`
- Edge TLS: Cloudflare proxy with SSL/TLS mode `Full (strict)`
- Origin TLS: nginx with a Cloudflare Origin Certificate

This is the low-cost first production-style deployment shape. S3 is used for backup storage. RDS, S3-backed application image serving, CloudFront, and a load balancer are deferred until traffic or operational requirements justify the added cost.

## Pre-Launch Runtime Schedule

EventBridge Scheduler limits EC2 runtime while the service is not yet accepting real orders:

Current state: manually running on `2026-07-28`; both automatic schedules remain disabled.

- Start: every day at `09:00 KST` (`coreable-ec2-start-daily`).
- Backup: every day at `00:10 KST` (`15:10 UTC` host cron) through `/etc/cron.d/coreable-backup`.
- Stop: every day at `01:00 KST` (`coreable-ec2-stop-daily`).
- Scheduler role: `coreable-ec2-scheduler-role`, limited to starting and stopping `i-0c795cb4b0f0b4177`.

This schedule saves only EC2 compute time; EBS and public IPv4 charges continue. Disable the stop schedule before live orders are enabled. A deployment attempted while the instance is stopped still requires a manual start before SSM can receive the command.

## One-Time AWS Setup

```sh
infra/aws/ec2/create-test-instance.sh
```

The script creates or reuses:

- SSM managed instance with the `coreable-temp-ssm-profile` instance profile
- security group `coreable-saf-test-sg`
- EC2 instance `coreable-saf-test`
- Elastic IP tagged `coreable-saf-test-eip`

Current deployment:

- EC2 instance id: `i-0c795cb4b0f0b4177`
- Elastic IP: `43.200.135.171`
- Latest successful deploy workflow: `30336534473`
- Deployed image tag: `087e97e7b84e504705f98890f5f24d4afe2c49e4`

Security group baseline:

- SSH `22`: no inbound rule
- HTTP `80`: Cloudflare public IPv4 ranges only
- HTTPS `443`: Cloudflare public IPv4 ranges only

GitHub Actions assumes `coreable-github-deploy` through OIDC only from this repository's `main` branch. The role can send `AWS-RunShellScript` only to the production EC2 instance, read/cancel that command, and put/delete only the per-run deploy-token SecureString prefix. The EC2 role can get/delete only that prefix. No long-lived AWS key or public SSH ingress is used for deployment.

## One-Time Server Setup

Use Session Manager and download only files from a reviewed immutable commit. Public SSH ingress is not required:

```sh
aws ssm start-session --target i-0c795cb4b0f0b4177
DEPLOY_SHA=<reviewed-commit-sha>
setup_dir=$(mktemp -d /tmp/coreable-setup.XXXXXX)
base_url="https://raw.githubusercontent.com/JAEKWANG97/dropship-shop/${DEPLOY_SHA}/infra/aws/ec2"
curl -fsSL "${base_url}/bootstrap-ubuntu.sh" -o "${setup_dir}/bootstrap-ubuntu.sh"
curl -fsSL "${base_url}/compose.prod.yml" -o "${setup_dir}/compose.prod.yml"
curl -fsSL "${base_url}/nginx.conf" -o "${setup_dir}/nginx.conf"
sudo bash "${setup_dir}/bootstrap-ubuntu.sh"
sudo install -m 0644 "${setup_dir}/compose.prod.yml" /opt/coreable/compose.prod.yml
sudo install -m 0644 "${setup_dir}/nginx.conf" /opt/coreable/nginx.conf
```

Create `/opt/coreable/.env` from `infra/aws/ec2/env.example` and replace all `change-me` or blank values. Keep this file only on the server.

Install the backup script after Docker and the AWS CLI are available:

```sh
curl -fsSL "${base_url}/backup.sh" -o "${setup_dir}/backup.sh"
curl -fsSL "${base_url}/install-backup.sh" -o "${setup_dir}/install-backup.sh"
sudo bash "${setup_dir}/install-backup.sh"
```

The scheduled backup uses the EC2 instance role with least-privilege access and uploads DB dumps and product uploads to `s3://coreable-backups-prod`. Do not configure a static AWS key on the host. See `docs/backup-restore.md` for restore commands and verification.

Provision the Cloudflare Origin Certificate and private key through a private secret-delivery path available inside the managed instance. Install them as `/var/lib/coreable/proxy/certs/cloudflare-origin.pem` mode `0644` and `/var/lib/coreable/proxy/certs/cloudflare-origin.key` mode `0600`. Do not pass the private key through GitHub, a public object, Run Command parameters or logs.

## GitHub Authentication

No EC2 SSH secret or long-lived AWS access key is required. The workflow requests a short-lived AWS credential through GitHub OIDC and writes the job-scoped `GITHUB_TOKEN` to a unique `/coreable/deploy/ghcr-token-<run>-<attempt>` Parameter Store key as `SecureString`. The Run Command carries only that parameter name, so a delayed command cannot consume a later run's credential. EC2 decrypts it at runtime, immediately deletes that exact parameter, pipes the value to `docker login --password-stdin`, and unsets it; the runner also performs best-effort deletion on every exit path.

Application secrets stay on EC2 in `/opt/coreable/.env`, not in GitHub Actions.

## Deploy Flow

Production deploy is manual-only through the `Deploy` workflow's `workflow_dispatch`. A push or merge to `main` does not deploy by itself.

1. GitHub Actions runs the complete API tests and Web lint/build.
2. The preflight checks required bank-transfer and supplier-portal settings without printing their values, requires the production supplier portal flag to remain disabled, and verifies the one-time V40 repair state before running `/opt/coreable/backup.sh`. Once V40 is applied, the repair-specific check is skipped.
3. API and Web Docker images are built for `linux/arm64` and pushed to GHCR with both commit SHA and `latest` tags.
4. Actions sends an SSM command that acquires the host deploy lock, revalidates the environment, takes a fresh backup immediately before runtime mutation, and preserves the current image tags, compose file and nginx config in a root-only temporary directory.
5. EC2 validates the commit SHA's compose/nginx config, rejects a production PostgreSQL service change, and pulls both exact image tags before runtime mutation. It then updates `API_IMAGE` and `WEB_IMAGE` and recreates only API, Web and nginx; persistent PostgreSQL changes require a separate maintenance procedure.
6. Actions verifies API readiness and the HTTPS Web response through local nginx.
7. A config download, validation or image-pull failure before mutation leaves the current stack untouched. On a later failure the candidate API is stopped before reading Flyway state. The previous deployment files are restored without recreating the already-running old services only when candidate API startup was never attempted and the latest successful migration version is provably unchanged.
8. Once candidate API startup has been attempted, recovery is roll-forward-only even if Flyway did not advance: new code may already have written data that the previous binary cannot read. A schema advance or unreadable migration state has the same boundary. Recovery retries only the new API and new Web/nginx. Unverified recovery keeps root-only artifacts and fails the workflow for manual handling.
9. Only after successful readiness does EC2 prune Docker images older than 168 hours.

### One-Time V39 To V40 Production Repair

The audited production snapshot at V39 contains 34 negative source option deltas across 13 products. V40 also backfills legacy system audit actors to a nullable `admin_user_id`; the published migration performs that backfill before its later `DROP NOT NULL`. Run `infra/aws/ec2/remediate-v40-negative-source-options.sql` once before the first V40 deploy. Do not edit the published V40 migration checksum.

Treat the repair and the first V40 deploy as one maintenance sequence:

1. Acquire `/var/lock/coreable-deploy.lock`, take a fresh `/opt/coreable/backup.sh` backup, and confirm the current schema is exactly V39 with the audited 34 negative options, 13 products and 68 non-null affected options.
2. Set `DOMEGGOOK_CATALOG_SYNC_ENABLED=false`, preserve the previous env file, and stop the API before applying the repair through the production PostgreSQL container.
3. Require the script to report zero negative options plus 13 product and 68 option audit rows. It also preserves every option's total source cost, all customer prices and the order-item monetary snapshot in one transaction, and releases the legacy audit-column NOT NULL constraint needed by V40.
4. If service must resume before the new deploy, restart the previous API only with catalog sync still disabled. Do not re-enable the legacy writer between the repair commit and V40.
5. Manually run the `Deploy` workflow for the reviewed `main` SHA. Require schema V44, healthy API/Web/PostgreSQL/nginx, matching API/Web image SHA and `APP_SUPPLIER_PORTAL_ENABLED=false`.
6. Re-enable catalog sync and recreate only the now-current API after its negative-delta normalization code is verified. Any count, invariant or migration mismatch stops the sequence; do not weaken the guards or force V40.

## Build Time And Cache

Current bottleneck is GitHub Actions image build, not EC2 runtime deploy. A recent baseline was:

- `verify`: 2m58s
- `build-and-push`: 7m39s
- `deploy`: 1m12s

The deploy workflow uses Docker BuildKit GitHub Actions cache:

- API image: `type=gha,scope=api-arm64`
- Web image: `type=gha,scope=web-arm64`

The first run after cache setup may still be slow because it warms the cache. Later runs can reuse dependency and image layers. Next.js application changes can still require a full `npm run build` inside the Web image, so the cache reduces repeated work but does not remove the ARM64 build cost entirely.

Deploy workflow concurrency is `deploy-production` with `cancel-in-progress: false`, and both remote phases use the same EC2 `flock`. Consecutive manual runs wait in GitHub while delayed/orphaned SSM commands also fail closed instead of running two host deploy scripts at the same time.

## Production Bank Transfer Account

Before deploying an account-transfer release, edit `/opt/coreable/.env` directly on EC2:

```sh
sudoedit /opt/coreable/.env
```

The following values are required and must not be committed or copied into deployment logs:

```dotenv
APP_BANK_TRANSFER_BANK_NAME=
APP_BANK_TRANSFER_ACCOUNT_NUMBER=
APP_BANK_TRANSFER_ACCOUNT_HOLDER=
```

Check only that each value exists without printing the account information:

```sh
for key in APP_BANK_TRANSFER_BANK_NAME APP_BANK_TRANSFER_ACCOUNT_NUMBER APP_BANK_TRANSFER_ACCOUNT_HOLDER; do
  sudo grep -Eq "^${key}=.+" /opt/coreable/.env || exit 1
done
```

The deploy workflow runs the same non-empty check before replacing containers. The `prod` Spring profile also requires all three environment variables, while local and test profiles keep their development defaults.

## Supplier Portal Production Gate

Keep the supplier portal closed in `/opt/coreable/.env` until its full release gate is verified:

```dotenv
APP_SUPPLIER_PORTAL_ENABLED=false
APP_SUPPLIER_PORTAL_HMAC_SECRET=
OAUTH_KAKAO_SUPPLIER_REDIRECT_URI=https://coreable-saf.com/api/supplier/auth/kakao/callback
APP_SUPPLIER_PORTAL_SUCCESS_REDIRECT_URI=https://coreable-saf.com/supplier
DROPSHIP_WEB_ORIGIN=https://coreable-saf.com
```

Generate `APP_SUPPLIER_PORTAL_HMAC_SECRET` as a random value of at least 32 characters. Do not commit, print, or copy the real value into deployment logs.

`DROPSHIP_WEB_ORIGIN` must be the same canonical HTTPS origin allowed by `APP_CORS_ALLOWED_ORIGINS`. The Web falls back to `APP_PUBLIC_BASE_URL`, but keeping the explicit value makes the Origin/CSRF boundary auditable.

Set `APP_SUPPLIER_PORTAL_ENABLED=true` only after all of the following pass:

- `B-100` through `B-105`
- active managed supplier-application privacy notice
- real invitation and operational email delivery
- B-098 time-valid contract evidence for the supplier

`B-102` inventory and checkout guards alone are not sufficient to open the portal.

The production API uses Spring Boot's native forwarded-header handling. Keep the API bound to localhost/Docker networking behind the configured Nginx proxy so public application rate limits resolve the forwarded client address instead of sharing one Nginx address bucket.

## Runtime Memory Limits

The first deployment runs on `t4g.micro`, so Docker services have conservative memory limits:

- API: `512m`
- PostgreSQL: `256m`
- Web: `192m`
- nginx: `64m`

The API container also receives `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError` from compose so the JVM does not compete without a heap ceiling. All services keep `restart: unless-stopped`; actual behavior under memory pressure must be rechecked on the next live deploy.

## DNS And OAuth

Cloudflare DNS:

- `A coreable-saf.com -> 43.200.135.171`, proxied
- `A www.coreable-saf.com -> 43.200.135.171`, proxied

Cloudflare SSL/TLS:

- Use `Full (strict)` after nginx is serving the Cloudflare Origin Certificate on port `443`.
- Do not use `Flexible`. It leaves the Cloudflare-to-origin leg on HTTP and can create redirect/cookie issues.

OAuth redirect URIs:

- `https://coreable-saf.com/api/auth/oauth2/google/callback`
- `https://coreable-saf.com/api/auth/oauth2/kakao/callback`
- `https://coreable-saf.com/api/supplier/auth/kakao/callback`
- `https://coreable-saf.com/api/auth/oauth2/naver/callback`

## Validation

Validate on the EC2 host:

```sh
aws ssm start-session --target i-0c795cb4b0f0b4177
cd /opt/coreable
sudo docker compose --env-file .env -f compose.prod.yml ps
curl -fsS http://localhost:8080/actuator/health/readiness
curl -fsS http://localhost:8080/api/health
```

Validate origin HTTPS directly. Cloudflare Origin Certificates are not trusted by the local OS trust store, so direct `curl` needs `-k` or the Cloudflare Origin CA root. This check validates origin routing and TLS service; Cloudflare `Full (strict)` performs the trusted validation from Cloudflare's edge:

```sh
curl -kfsS --resolve coreable-saf.com:443:43.200.135.171 https://coreable-saf.com/api/health
curl -kfsS --resolve www.coreable-saf.com:443:43.200.135.171 https://www.coreable-saf.com/
```

After Cloudflare is proxied and SSL/TLS is `Full (strict)`, validate the public URL:

```sh
curl -fsS https://coreable-saf.com/api/health
curl -fsS https://coreable-saf.com/actuator/health/readiness
curl -IfsS https://coreable-saf.com
curl -IfsS https://www.coreable-saf.com
```

Browser smoke:

- `/`
- `/products`
- `/products/{id}`
- `/policies`
- `/company`
- `/support`
- `/admin`
- `/admin/products`
- `/admin/orders`

## Current Deferrals

- S3-backed application image serving, RDS, and CloudFront are deferred. S3 is already used for DB/upload backups.
- Real SMS provider activation is deferred unless phone verification is required before live launch. Production default is `SMS_SENS_ENABLED=false`; set it explicitly to `true` only after real Naver SENS credentials are installed.
