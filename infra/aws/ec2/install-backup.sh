#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo $0" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

apt-get update
apt-get install -y ca-certificates curl unzip

if ! command -v aws >/dev/null 2>&1; then
  arch="$(uname -m)"
  case "$arch" in
    aarch64|arm64) awscli_arch="aarch64" ;;
    x86_64|amd64) awscli_arch="x86_64" ;;
    *) echo "Unsupported architecture for AWS CLI: $arch" >&2; exit 1 ;;
  esac
  tmpdir="$(mktemp -d)"
  curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-${awscli_arch}.zip" -o "${tmpdir}/awscliv2.zip"
  unzip -q "${tmpdir}/awscliv2.zip" -d "$tmpdir"
  "${tmpdir}/aws/install" --bin-dir /usr/local/bin --install-dir /usr/local/aws-cli
  rm -rf "$tmpdir"
fi

install -d -m 0755 /opt/coreable
install -d -m 0750 /var/backups/coreable/db
install -d -m 0755 /var/log
install -m 0750 "${SCRIPT_DIR}/backup.sh" /opt/coreable/backup.sh
touch /var/log/coreable-backup.log
chmod 0640 /var/log/coreable-backup.log

cat >/etc/cron.d/coreable-backup <<'CRON'
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
TZ=Asia/Seoul
10 3 * * * root /opt/coreable/backup.sh >/dev/null 2>&1
CRON

chmod 0644 /etc/cron.d/coreable-backup

echo "Coreable backup installed. Configure /root/.aws with the coreable-backup-writer key before scheduled runs."
