#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# One-time VM setup script for data-collection-app
#
# Run this script once on a fresh Ubuntu 22.04/24.04 VM as root.
# No application secrets are collected here — they live exclusively in
# GitHub Secrets and are written to .env by the deploy workflow on each push.
#
# What this script does:
#   1. Installs Docker Engine and Caddy
#   2. Creates a dedicated "deploy" user (no password, SSH only)
#   3. Grants "deploy" permission to run Docker without sudo
#   4. Creates /opt/data-collection-app owned by "deploy"
#   5. Installs the maintenance page and Caddyfile
#   6. Generates an SSH key pair — prints the private key for GitHub Secrets
#
# Usage (as root, from the cloned repo root):
#   bash scripts/vm-setup.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Require root ──────────────────────────────────────────────────────────────
if [[ $EUID -ne 0 ]]; then
  echo "Run as root: sudo bash $0" >&2
  exit 1
fi

DEPLOY_USER=deploy
DEPLOY_DIR=/opt/data-collection-app
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo
echo "════════════════════════════════════════════════════════════════"
echo "  data-collection-app — VM setup"
echo "════════════════════════════════════════════════════════════════"
echo

# ── 1. Install Docker Engine ──────────────────────────────────────────────────
echo "▶ Installing Docker Engine..."
apt-get update -qq
apt-get install -y -qq ca-certificates curl gnupg lsb-release

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update -qq
apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-compose-plugin
systemctl enable --now docker
echo "   Docker installed: $(docker --version)"

# ── 2. Install Caddy ──────────────────────────────────────────────────────────
echo "▶ Installing Caddy..."
apt-get install -y -qq debian-keyring debian-archive-keyring apt-transport-https

curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/gpg.key \
  | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg

curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt \
  > /etc/apt/sources.list.d/caddy-stable.list

apt-get update -qq
apt-get install -y -qq caddy
echo "   Caddy installed: $(caddy version)"

# ── 3. Create the "deploy" user ───────────────────────────────────────────────
echo "▶ Creating '${DEPLOY_USER}' user..."

if id "${DEPLOY_USER}" &>/dev/null; then
  echo "   User '${DEPLOY_USER}' already exists — skipping creation"
else
  # No password; SSH key auth only. Shell is bash for deploy scripts.
  useradd -m -s /bin/bash -c "App deployment user" "${DEPLOY_USER}"
  # Lock the password so no one can log in with a password
  passwd -l "${DEPLOY_USER}"
  echo "   Created user '${DEPLOY_USER}' (password login disabled)"
fi

# Add to docker group so the user can run docker compose without sudo.
# Note: docker group membership is effectively root-equivalent because
# containers can mount the host filesystem. This is an accepted tradeoff
# for a dedicated deployment user on a single-purpose VM.
usermod -aG docker "${DEPLOY_USER}"
echo "   Added '${DEPLOY_USER}' to the docker group"

# ── 4. Create deployment directory ───────────────────────────────────────────
echo "▶ Creating deployment directory at ${DEPLOY_DIR}..."
mkdir -p "${DEPLOY_DIR}"
cp "${REPO_ROOT}/docker-compose.yml" "${DEPLOY_DIR}/docker-compose.yml"
chown -R "${DEPLOY_USER}:${DEPLOY_USER}" "${DEPLOY_DIR}"
chmod 750 "${DEPLOY_DIR}"
echo "   Directory created and owned by '${DEPLOY_USER}'"

# ── 5. Install maintenance page ───────────────────────────────────────────────
echo "▶ Installing maintenance page..."
mkdir -p /var/www/maintenance
cp "${REPO_ROOT}/caddy/maintenance/index.html" /var/www/maintenance/index.html
# Caddy runs as the "caddy" system user and needs read access
chown -R caddy:caddy /var/www/maintenance
echo "   Copied to /var/www/maintenance/ (owned by caddy)"

# ── 6. Configure Caddy ────────────────────────────────────────────────────────
echo "▶ Configuring Caddy..."
mkdir -p /var/log/caddy
chown caddy:caddy /var/log/caddy

read -rp "  Domain name (e.g. app.example.com): " APP_DOMAIN

sed "s/yourdomain.com/${APP_DOMAIN}/g" \
  "${REPO_ROOT}/caddy/Caddyfile" > /etc/caddy/Caddyfile

systemctl reload caddy || systemctl restart caddy
echo "   Caddyfile installed and Caddy reloaded"

# ── 7. Generate SSH deploy key for GitHub Actions ─────────────────────────────
echo
echo "▶ Generating SSH deploy key for GitHub Actions..."
DEPLOY_HOME=$(getent passwd "${DEPLOY_USER}" | cut -d: -f6)
KEY_FILE="${DEPLOY_HOME}/.ssh/github_actions"

mkdir -p "${DEPLOY_HOME}/.ssh"
chmod 700 "${DEPLOY_HOME}/.ssh"

ssh-keygen -t ed25519 -C "github-actions-deploy" -f "${KEY_FILE}" -N "" -q

# Authorise the key for the deploy user
cat "${KEY_FILE}.pub" >> "${DEPLOY_HOME}/.ssh/authorized_keys"
chmod 600 "${DEPLOY_HOME}/.ssh/authorized_keys"
chown -R "${DEPLOY_USER}:${DEPLOY_USER}" "${DEPLOY_HOME}/.ssh"
rm "${KEY_FILE}.pub"   # already in authorized_keys; file is redundant

echo
echo "  ┌──────────────────────────────────────────────────────────────┐"
echo "  │  PRIVATE KEY — paste this as GitHub Secret: VM_SSH_KEY       │"
echo "  └──────────────────────────────────────────────────────────────┘"
cat "${KEY_FILE}"
rm "${KEY_FILE}"       # remove from disk; it will live only in GitHub Secrets
echo

# ── 8. GHCR login for the deploy user (private repositories only) ─────────────
echo "  Note: if your GitHub repository is private, the deploy user must"
echo "  authenticate to GHCR to pull the image on first deploy:"
echo
echo "    su - ${DEPLOY_USER}"
echo "    echo <PAT> | docker login ghcr.io -u <github-username> --password-stdin"
echo
echo "  Create a PAT at https://github.com/settings/tokens with 'read:packages' scope."
echo "  Public repositories require no login."
echo

# ── 9. Firewall — allow only SSH, HTTP, HTTPS ─────────────────────────────────
echo "▶ Configuring UFW firewall..."
if command -v ufw &>/dev/null; then
  ufw allow OpenSSH
  ufw allow 'WWW Full'   # ports 80 and 443
  ufw --force enable
  echo "   UFW enabled: SSH + HTTP/HTTPS allowed. Port 9666 is NOT exposed."
else
  echo "   UFW not found — skip (configure your cloud provider's firewall manually)"
  echo "   Allow: TCP 22, 80, 443.  Block: everything else (including 9666)."
fi

# ── 10. Summary ───────────────────────────────────────────────────────────────
VM_IP=$(curl -sf ifconfig.me 2>/dev/null || echo "<your VM public IP>")

echo
echo "════════════════════════════════════════════════════════════════"
echo "  Setup complete!"
echo "════════════════════════════════════════════════════════════════"
echo
echo "  Add these secrets to the 'production' GitHub Environment"
echo "  (repo → Settings → Environments → production → Add secret):"
echo
echo "  Infrastructure:"
echo "    VM_HOST              = ${VM_IP}"
echo "    VM_USER              = ${DEPLOY_USER}"
echo "    VM_SSH_KEY           = <private key printed above>"
echo
echo "  Application:"
echo "    DB_USER              = app_user"
echo "    DB_PASSWORD          = <strong password>  (try: openssl rand -hex 20)"
echo "    APP_SIGNATURE_SECRET = <random string>    (try: openssl rand -hex 32)"
echo "    APP_ADMIN_PASSWORD   = <admin panel password>"
echo "    APP_API_TOKEN        = <api token>         (try: openssl rand -hex 32)"
echo
echo "  Then push to the master branch to trigger the first deploy."
echo "  GitHub Actions will SSH as '${DEPLOY_USER}', write .env, and start the containers."
echo
echo "  App URL:   https://${APP_DOMAIN}"
echo "  Admin URL: https://${APP_DOMAIN}/#/admin"
echo
