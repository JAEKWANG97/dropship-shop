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

Current state: paused on `2026-07-13`; the EC2 instance is stopped and both schedules are disabled until development resumes.

- Start: every day at `09:00 KST` (`coreable-ec2-start-daily`).
- Backup: every day at `00:10 KST` (`15:10 UTC` host cron) through `/etc/cron.d/coreable-backup`.
- Stop: every day at `01:00 KST` (`coreable-ec2-stop-daily`).
- Scheduler role: `coreable-ec2-scheduler-role`, limited to starting and stopping `i-0c795cb4b0f0b4177`.

This schedule saves only EC2 compute time; EBS and public IPv4 charges continue. Disable the stop schedule before live orders are enabled. A deployment attempted while the instance is stopped still requires a manual start until the deploy workflow is migrated from SSH to SSM.

## One-Time AWS Setup

```sh
infra/aws/ec2/create-test-instance.sh
```

The script creates or reuses:

- key pair `coreable-saf-deploy-key`
- security group `coreable-saf-test-sg`
- EC2 instance `coreable-saf-test`
- Elastic IP tagged `coreable-saf-test-eip`

Current deployment:

- EC2 instance id: `i-0c795cb4b0f0b4177`
- Elastic IP: `43.200.135.171`
- Latest successful deploy workflow: `28566566959`
- Deployed image tag: `22ea493554d4f0eb81a0237fb9ce879ee85f12d3`

Security group baseline:

- SSH `22`: `0.0.0.0/0` for GitHub-hosted Actions SSH deploy
- HTTP `80`: `0.0.0.0/0`
- HTTPS `443`: `0.0.0.0/0`

GitHub-hosted runners do not have a stable small source IP range. SSH is therefore open to the internet for this first deployment, but access is key-only with the deploy key stored in GitHub Secrets. If this becomes long-lived production infrastructure, replace this with SSM Session Manager, a self-hosted runner, or a fixed egress deploy host.

## One-Time Server Setup

After SSH access works:

```sh
scp infra/aws/ec2/bootstrap-ubuntu.sh ubuntu@<elastic-ip>:/tmp/bootstrap-ubuntu.sh
ssh ubuntu@<elastic-ip> 'sudo bash /tmp/bootstrap-ubuntu.sh'
ssh ubuntu@<elastic-ip> 'sudo mkdir -p /opt/coreable /var/lib/coreable/proxy/certs'
scp infra/aws/ec2/compose.prod.yml infra/aws/ec2/nginx.conf ubuntu@<elastic-ip>:/tmp/
ssh ubuntu@<elastic-ip> 'sudo cp /tmp/compose.prod.yml /opt/coreable/compose.prod.yml && sudo cp /tmp/nginx.conf /opt/coreable/nginx.conf'
```

Create `/opt/coreable/.env` from `infra/aws/ec2/env.example` and replace all `change-me` or blank values. Keep this file only on the server.

Install the backup script after Docker and the AWS CLI are available:

```sh
scp infra/aws/ec2/backup.sh infra/aws/ec2/install-backup.sh ubuntu@<elastic-ip>:/tmp/
ssh ubuntu@<elastic-ip> 'sudo mkdir -p /tmp/coreable-backup-install && sudo mv /tmp/backup.sh /tmp/install-backup.sh /tmp/coreable-backup-install/ && sudo bash /tmp/coreable-backup-install/install-backup.sh'
```

The scheduled backup uses the least-privilege IAM user `coreable-backup-writer` and uploads DB dumps and product uploads to `s3://coreable-backups-prod`. See `docs/backup-restore.md` for restore commands and verification.

Install the Cloudflare Origin Certificate and private key on the server. Do not commit or print the key:

```sh
scp tmp/cloudflare-origin.pem tmp/cloudflare-origin.key ubuntu@<elastic-ip>:/tmp/
ssh ubuntu@<elastic-ip> 'sudo install -m 0644 /tmp/cloudflare-origin.pem /var/lib/coreable/proxy/certs/cloudflare-origin.pem && sudo install -m 0600 /tmp/cloudflare-origin.key /var/lib/coreable/proxy/certs/cloudflare-origin.key && sudo rm -f /tmp/cloudflare-origin.pem /tmp/cloudflare-origin.key'
```

## GitHub Secrets

Required repository secrets:

- `EC2_HOST`: Elastic IP or DNS host
- `EC2_USER`: `ubuntu`
- `EC2_SSH_PRIVATE_KEY`: private key matching the EC2 key pair

Application secrets stay on EC2 in `/opt/coreable/.env`, not in GitHub Actions.

## Deploy Flow

On push to `main`:

1. GitHub Actions builds API and Web Docker images for `linux/arm64`.
2. Images are pushed to GHCR with both commit SHA and `latest` tags.
3. Actions copies compose/nginx config to EC2.
4. EC2 updates `API_IMAGE` and `WEB_IMAGE` in `/opt/coreable/.env`.
5. EC2 runs `docker compose pull && docker compose up -d`.
6. Actions checks `http://localhost:8080/actuator/health/readiness`.
7. After readiness succeeds, EC2 prunes Docker images older than 168 hours to avoid filling the 20GB disk with SHA-tagged images.

Manual deploy is available through the `Deploy` workflow's `workflow_dispatch`.

## Build Time And Cache

Current bottleneck is GitHub Actions image build, not EC2 runtime deploy. A recent baseline was:

- `verify`: 2m58s
- `build-and-push`: 7m39s
- `deploy`: 1m12s

The deploy workflow uses Docker BuildKit GitHub Actions cache:

- API image: `type=gha,scope=api-arm64`
- Web image: `type=gha,scope=web-arm64`

The first run after cache setup may still be slow because it warms the cache. Later runs can reuse dependency and image layers. Next.js application changes can still require a full `npm run build` inside the Web image, so the cache reduces repeated work but does not remove the ARM64 build cost entirely.

Deploy is skipped when only `docs/**`, `README.md`, or `AGENTS.md` changes. Workflow, app, or infra changes still trigger deploy.

Deploy workflow concurrency is `deploy-production` with `cancel-in-progress: false`. Consecutive pushes wait in order instead of running two EC2 deploy scripts at the same time.

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
- `https://coreable-saf.com/api/auth/oauth2/naver/callback`

## Validation

Validate on the EC2 host:

```sh
ssh ubuntu@43.200.135.171 'cd /opt/coreable && sudo docker compose --env-file .env -f compose.prod.yml ps'
ssh ubuntu@43.200.135.171 'curl -fsS http://localhost:8080/actuator/health/readiness'
ssh ubuntu@43.200.135.171 'curl -fsS http://localhost:8080/api/health'
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
ssh ubuntu@<elastic-ip> 'cd /opt/coreable && sudo docker compose --env-file .env -f compose.prod.yml ps'
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
- Live Toss key switch is deferred until the test URL and legal/customer notice pages are verified.
- Real SMS provider activation is deferred unless phone verification is required before live launch. Production default is `SMS_SENS_ENABLED=false`; set it explicitly to `true` only after real Naver SENS credentials are installed.
