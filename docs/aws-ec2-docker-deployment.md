# AWS EC2 Docker Test Deployment

Status: Test deployment baseline for B-039

## Runtime Shape

- Region: `ap-northeast-2`
- Instance: `t4g.micro`
- OS: Ubuntu 24.04 ARM64
- Disk: gp3 20GB
- Public IP: Elastic IP `43.200.135.171`
- Runtime: Docker Compose on a single EC2 instance
- Registry: GHCR
- Domain: `coreable-saf.com`, `www.coreable-saf.com`

This is a test deployment for external integration preparation. It is not the final live-payment production shape.

## One-Time AWS Setup

```sh
infra/aws/ec2/create-test-instance.sh
```

The script creates or reuses:

- key pair `coreable-saf-deploy-key`
- security group `coreable-saf-test-sg`
- EC2 instance `coreable-saf-test`
- Elastic IP tagged `coreable-saf-test-eip`

Current test deployment:

- EC2 instance id: `i-0c795cb4b0f0b4177`
- Elastic IP: `43.200.135.171`
- Latest successful deploy workflow: `28566566959`
- Deployed image tag: `22ea493554d4f0eb81a0237fb9ce879ee85f12d3`

Security group baseline:

- SSH `22`: `0.0.0.0/0` for GitHub-hosted Actions SSH deploy
- HTTP `80`: `0.0.0.0/0`
- HTTPS `443`: `0.0.0.0/0`

GitHub-hosted runners do not have a stable small source IP range. SSH is therefore open to the internet for this test deployment, but access is key-only with the deploy key stored in GitHub Secrets. If this becomes long-lived production infrastructure, replace this with SSM Session Manager, a self-hosted runner, or a fixed egress deploy host.

## One-Time Server Setup

After SSH access works:

```sh
scp infra/aws/ec2/bootstrap-ubuntu.sh ubuntu@<elastic-ip>:/tmp/bootstrap-ubuntu.sh
ssh ubuntu@<elastic-ip> 'sudo bash /tmp/bootstrap-ubuntu.sh'
ssh ubuntu@<elastic-ip> 'sudo mkdir -p /opt/coreable'
scp infra/aws/ec2/compose.prod.yml infra/aws/ec2/Caddyfile ubuntu@<elastic-ip>:/tmp/
ssh ubuntu@<elastic-ip> 'sudo cp /tmp/compose.prod.yml /opt/coreable/compose.prod.yml && sudo cp /tmp/Caddyfile /opt/coreable/Caddyfile'
```

Create `/opt/coreable/.env` from `infra/aws/ec2/env.example` and replace all `change-me` or blank values. Keep this file only on the server.

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
3. Actions copies compose/Caddy config to EC2.
4. EC2 updates `API_IMAGE` and `WEB_IMAGE` in `/opt/coreable/.env`.
5. EC2 runs `docker compose pull && docker compose up -d`.
6. Actions checks `http://localhost:8080/actuator/health/readiness`.

Manual deploy is available through the `Deploy` workflow's `workflow_dispatch`.

## DNS And OAuth

Cloudflare DNS:

- `A coreable-saf.com -> <Elastic IP>`
- `A www.coreable-saf.com -> <Elastic IP>`

OAuth redirect URIs:

- `https://coreable-saf.com/api/auth/oauth2/google/callback`
- `https://coreable-saf.com/api/auth/oauth2/kakao/callback`
- `https://coreable-saf.com/api/auth/oauth2/naver/callback`

## Validation

Before DNS is connected, validate on the EC2 host and with a temporary Host header:

```sh
ssh ubuntu@43.200.135.171 'cd /opt/coreable && sudo docker compose --env-file .env -f compose.prod.yml ps'
ssh ubuntu@43.200.135.171 'curl -fsS http://localhost:8080/actuator/health/readiness'
ssh ubuntu@43.200.135.171 'curl -fsS http://localhost:8080/api/health'
curl -i -H 'Host: coreable-saf.com' http://43.200.135.171/api/health
```

After Cloudflare DNS points to the Elastic IP, validate the public URL:

```sh
curl -fsS https://coreable-saf.com/api/health
curl -fsS https://coreable-saf.com/actuator/health/readiness
curl -fsS https://coreable-saf.com
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

- S3/RDS/CloudFront are deferred.
- Cloudflare DNS is not connected yet. Caddy can start and redirect HTTP to HTTPS, but Let's Encrypt certificate issuance waits for valid `A` records for `coreable-saf.com` and `www.coreable-saf.com`.
- Live Toss key switch is deferred until the test URL and legal/customer notice pages are verified.
- Real SMS provider activation is deferred unless phone verification is required on the test deployment.
