#!/usr/bin/env bash
#
# Steps 4, 6 and 7 of README-DEPLOY.md, in one go.
#
# It does NOT create the MySQL user (step 3) and it does NOT set your secrets
# (step 5) - do those yourself, then run this. It stops if anything fails rather
# than carrying on with a half-installed service.
#
#   sudo bash /opt/pandal/scripts/install.sh
#
set -euo pipefail

DOMAIN="pandaldepartment.tech"
APP_DIR="/opt/pandal"
WEB_DIR="/var/www/${DOMAIN}"

if [[ $EUID -ne 0 ]]; then
  echo "Run this with sudo." >&2
  exit 1
fi

if [[ ! -f "${APP_DIR}/pandal.env" ]]; then
  echo "ERROR: ${APP_DIR}/pandal.env is missing." >&2
  echo "       mv ${APP_DIR}/config/pandal.env ${APP_DIR}/pandal.env, then edit it (step 5)." >&2
  exit 1
fi

# Refuse to start something reachable from the internet with the shipped
# placeholder as its signing key.
if grep -q 'CHANGE_ME' "${APP_DIR}/pandal.env"; then
  echo "ERROR: ${APP_DIR}/pandal.env still has CHANGE_ME placeholders in it." >&2
  grep -n 'CHANGE_ME' "${APP_DIR}/pandal.env" >&2
  echo "       Set the real values first - see step 5." >&2
  exit 1
fi

echo "==> service account"
id -u pandal >/dev/null 2>&1 || adduser --system --group --no-create-home pandal
chown -R pandal:pandal "${APP_DIR}"
chown root:root "${APP_DIR}/pandal.env"
chmod 600 "${APP_DIR}/pandal.env"

echo "==> browser app -> ${WEB_DIR}"
mkdir -p "${WEB_DIR}"
cp -r "${APP_DIR}/web/." "${WEB_DIR}/"
chown -R www-data:www-data "${WEB_DIR}"

echo "==> application service"
cp "${APP_DIR}/config/pandal.service" /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now pandal

echo "==> waiting for it to answer"
for i in $(seq 1 60); do
  if curl -fsS -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/auth/me 2>/dev/null | grep -q 401; then
    echo "    up (401 on an unauthenticated request is the correct answer)"
    break
  fi
  if [[ $i -eq 60 ]]; then
    echo "ERROR: it did not come up. Look at: journalctl -u pandal -n 100" >&2
    exit 1
  fi
  sleep 2
done

echo "==> nginx"
cp "${APP_DIR}/config/nginx-${DOMAIN}.conf" "/etc/nginx/sites-available/${DOMAIN}"
ln -sf "/etc/nginx/sites-available/${DOMAIN}" "/etc/nginx/sites-enabled/${DOMAIN}"
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx

echo
echo "Done. Open http://${DOMAIN} and sign in as admin."
echo "Then turn on HTTPS - step 8:"
echo "    apt install -y certbot python3-certbot-nginx"
echo "    certbot --nginx -d ${DOMAIN} -d www.${DOMAIN}"
