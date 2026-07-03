#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo $0" >&2
  exit 1
fi

apt-get update
apt-get install -y ca-certificates curl gnupg unzip

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

. /etc/os-release
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
usermod -aG docker ubuntu

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
install -d -m 0755 /var/lib/coreable/postgres
install -d -m 0755 /var/lib/coreable/uploads/products
install -d -m 0755 /var/lib/coreable/proxy
install -d -m 0700 /var/lib/coreable/proxy/certs

if ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

cat >/etc/logrotate.d/coreable-docker <<'LOGROTATE'
/var/lib/docker/containers/*/*.log {
  rotate 7
  daily
  compress
  size=20M
  missingok
  delaycompress
  copytruncate
}
LOGROTATE

echo "Bootstrap complete. Copy compose.prod.yml, nginx.conf, Cloudflare origin cert/key, and /opt/coreable/.env before deploying."
