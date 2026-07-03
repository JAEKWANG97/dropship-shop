# Backup And Restore Runbook

Status: implemented for the single-EC2 deployment baseline.

## Scope

Current production-style deployment keeps PostgreSQL and uploaded product images on one EC2 instance. This runbook defines the minimum backup and restore process before real orders are accepted.

Resources:

- EC2 instance: `i-0c795cb4b0f0b4177`
- Root EBS volume: `vol-0d886b1bea4f76982`
- Backup bucket: `s3://coreable-backups-prod`
- Backup IAM user: `coreable-backup-writer`
- Backup script: `/opt/coreable/backup.sh`
- Backup log: `/var/log/coreable-backup.log`
- Local DB backup directory: `/var/backups/coreable/db`
- Product uploads directory: `/var/lib/coreable/uploads/products`

## Backup Policy

- DB backup: `pg_dump -Fc` custom-format dump.
- DB backup schedule: every day at `03:10 KST`.
- DB S3 location: `s3://coreable-backups-prod/db/`.
- Local DB retention: latest 3 dump files.
- S3 DB retention: lifecycle expiration after 30 days.
- Upload backup: `aws s3 sync /var/lib/coreable/uploads/products s3://coreable-backups-prod/uploads/products/`.
- Upload sync does not use `--delete`; remote images are kept unless intentionally cleaned.
- Root volume snapshot: AWS DLM weekly snapshot, retain 4 snapshots.
- Current DLM policy: `policy-07f1e7ad6713e55f9`.
- Root volume `DeleteOnTermination`: `false`.

The EC2 host must use only the minimal IAM access key for `coreable-backup-writer`. Do not leave root or admin AWS credentials under `/opt/coreable`, `/home/ubuntu/.aws`, or any application directory.

## Manual Backup

Run this on EC2:

```sh
ssh ubuntu@43.200.135.171
sudo /opt/coreable/backup.sh
sudo tail -n 50 /var/log/coreable-backup.log
```

Verify from a trusted admin machine:

```sh
aws s3 ls s3://coreable-backups-prod/db/ --recursive | tail -n 10
aws s3 ls s3://coreable-backups-prod/uploads/products/ --recursive | wc -l
```

Verify from EC2 that the scheduled credential is the least-privilege backup user:

```sh
sudo aws sts get-caller-identity --query Arn --output text
```

Expected ARN:

```text
arn:aws:iam::445567114845:user/coreable-backup-writer
```

## Restore Rehearsal

Use a temporary container first. This does not touch the running production database.

```sh
ssh ubuntu@43.200.135.171

latest="$(sudo aws s3 ls s3://coreable-backups-prod/db/ --recursive | awk '{print $4}' | sort | tail -n 1)"
test -n "$latest"

sudo rm -rf /tmp/coreable-restore-check
sudo mkdir -p /tmp/coreable-restore-check
sudo aws s3 cp "s3://coreable-backups-prod/$latest" /tmp/coreable-restore-check/latest.dump

sudo docker rm -f coreable-restore-check >/dev/null 2>&1 || true
sudo docker run -d --name coreable-restore-check \
  -e POSTGRES_DB=coreable_restore \
  -e POSTGRES_USER=coreable_restore \
  -e POSTGRES_PASSWORD=restore \
  -v /tmp/coreable-restore-check:/restore \
  postgres:17-alpine

until sudo docker exec coreable-restore-check pg_isready -U coreable_restore -d coreable_restore >/dev/null 2>&1; do
  sleep 1
done

sudo docker exec coreable-restore-check pg_restore --no-owner \
  -U coreable_restore \
  -d coreable_restore \
  /restore/latest.dump

sudo docker exec coreable-restore-check psql -U coreable_restore -d coreable_restore \
  -tAc "select count(*) from flyway_schema_history;"
sudo docker exec coreable-restore-check psql -U coreable_restore -d coreable_restore \
  -tAc "select count(*) from products;"

sudo docker rm -f coreable-restore-check
sudo rm -rf /tmp/coreable-restore-check
```

Use `--no-owner`. The dump can contain the production role name, and a rehearsal DB often uses a different temporary role.

## Production DB Restore

This is destructive. Before running it, take an EBS snapshot or confirm that the latest S3 dump is usable through the restore rehearsal above.

```sh
ssh ubuntu@43.200.135.171
cd /opt/coreable
set -a
. ./.env
set +a

latest="$(sudo aws s3 ls s3://coreable-backups-prod/db/ --recursive | awk '{print $4}' | sort | tail -n 1)"
test -n "$latest"

sudo mkdir -p /tmp/coreable-restore
sudo aws s3 cp "s3://coreable-backups-prod/$latest" /tmp/coreable-restore/latest.dump

sudo docker compose --env-file .env -f compose.prod.yml stop api web
sudo docker compose --env-file .env -f compose.prod.yml exec -T postgres \
  psql -U "$POSTGRES_USER" -d postgres \
  -c "select pg_terminate_backend(pid) from pg_stat_activity where datname = '$POSTGRES_DB';"
sudo docker compose --env-file .env -f compose.prod.yml exec -T postgres \
  dropdb -U "$POSTGRES_USER" --if-exists "$POSTGRES_DB"
sudo docker compose --env-file .env -f compose.prod.yml exec -T postgres \
  createdb -U "$POSTGRES_USER" "$POSTGRES_DB"
postgres_container="$(sudo docker compose --env-file .env -f compose.prod.yml ps -q postgres)"
sudo docker cp /tmp/coreable-restore/latest.dump "$postgres_container:/tmp/latest.dump"
sudo docker compose --env-file .env -f compose.prod.yml exec -T postgres \
  pg_restore --no-owner -U "$POSTGRES_USER" -d "$POSTGRES_DB" /tmp/latest.dump
sudo docker compose --env-file .env -f compose.prod.yml up -d
```

## Upload Image Restore

Restore product uploads from S3 to the EC2 local volume:

```sh
ssh ubuntu@43.200.135.171
sudo mkdir -p /var/lib/coreable/uploads/products
sudo aws s3 sync s3://coreable-backups-prod/uploads/products/ /var/lib/coreable/uploads/products/
sudo find /var/lib/coreable/uploads/products -type d -exec chmod 0755 {} +
sudo find /var/lib/coreable/uploads/products -type f -exec chmod 0644 {} +
```

After restore, verify image serving:

```sh
curl -fsS http://localhost:8080/api/health
curl -fsS http://localhost:8080/actuator/health/readiness
curl -IfsS https://coreable-saf.com/products
```

## Operational Checks

Weekly:

```sh
aws s3 ls s3://coreable-backups-prod/db/ --recursive | tail -n 10
aws ec2 describe-instance-attribute \
  --instance-id i-0c795cb4b0f0b4177 \
  --attribute blockDeviceMapping \
  --query 'BlockDeviceMappings[].Ebs.{VolumeId:VolumeId,DeleteOnTermination:DeleteOnTermination}'
aws dlm get-lifecycle-policy \
  --policy-id policy-07f1e7ad6713e55f9 \
  --query 'Policy.{PolicyId:PolicyId,State:State,PolicyDetails:PolicyDetails.Schedules}'
```

Credential hygiene:

```sh
ssh ubuntu@43.200.135.171 \
  'sudo aws sts get-caller-identity --query Arn --output text && \
   sudo test ! -d /home/ubuntu/.aws && \
   sudo find /opt/coreable -type f \( -name credentials -o -name config -o -name "*.pem" -o -name "*.key" \) -print'
```

The final `find` command should not print AWS credentials or root/admin keys.
