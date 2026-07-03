#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-/opt/coreable/compose.prod.yml}"
ENV_FILE="${COREABLE_ENV_FILE:-/opt/coreable/.env}"
BACKUP_BUCKET="${COREABLE_BACKUP_BUCKET:-coreable-backups-prod}"
LOCAL_DB_BACKUP_DIR="${LOCAL_DB_BACKUP_DIR:-/var/backups/coreable/db}"
UPLOADS_DIR="${UPLOADS_DIR:-/var/lib/coreable/uploads/products}"
LOG_FILE="${LOG_FILE:-/var/log/coreable-backup.log}"
TZ="${TZ:-Asia/Seoul}"

log() {
  printf '[%s] %s\n' "$(TZ="$TZ" date '+%Y-%m-%dT%H:%M:%S%z')" "$*" | tee -a "$LOG_FILE"
}

fail() {
  log "backup failed: line=$1"
}

trap 'fail "$LINENO"' ERR

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo $0" >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  log "env file not found: $ENV_FILE"
  exit 1
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
  log "compose file not found: $COMPOSE_FILE"
  exit 1
fi

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}"

install -d -m 0750 "$LOCAL_DB_BACKUP_DIR"
install -d -m 0755 "$(dirname "$LOG_FILE")"
touch "$LOG_FILE"
chmod 0640 "$LOG_FILE"

timestamp="$(TZ="$TZ" date '+%Y%m%d-%H%M%S')"
db_backup_file="${LOCAL_DB_BACKUP_DIR}/coreable-db-${timestamp}.dump"
db_s3_uri="s3://${BACKUP_BUCKET}/db/$(basename "$db_backup_file")"
uploads_s3_uri="s3://${BACKUP_BUCKET}/uploads/products/"

log "backup started"

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T postgres \
  pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc > "$db_backup_file"

db_size_bytes="$(wc -c < "$db_backup_file" | tr -d ' ')"
aws s3 cp "$db_backup_file" "$db_s3_uri" --only-show-errors
log "db backup uploaded: uri=${db_s3_uri} bytes=${db_size_bytes}"

if [[ -d "$UPLOADS_DIR" ]]; then
  aws s3 sync "$UPLOADS_DIR" "$uploads_s3_uri" --only-show-errors
  upload_count="$(find "$UPLOADS_DIR" -type f 2>/dev/null | wc -l | tr -d ' ')"
  log "uploads synced: uri=${uploads_s3_uri} files=${upload_count}"
else
  log "uploads directory missing, skipped: ${UPLOADS_DIR}"
fi

mapfile -t old_backups < <(find "$LOCAL_DB_BACKUP_DIR" -maxdepth 1 -type f -name 'coreable-db-*.dump' | sort -r | tail -n +4)
if (( ${#old_backups[@]} > 0 )); then
  rm -f "${old_backups[@]}"
  log "local db backup retention pruned: count=${#old_backups[@]}"
fi

log "backup completed"
