#!/usr/bin/env bash
#
# Start Pandal Office Management.
#
#   sudo /opt/pandal/scripts/start.sh
#
# Brings up the database and the web server if they are down, then the
# application, and does not report success until the app actually answers a
# request. A service that systemd calls "active" can still be a Java process
# that is on its way to failing, so the check here is an HTTP reply, not a
# process table entry.
#
# Exit status is 0 only if the app is genuinely serving.

set -uo pipefail

APP_URL="http://127.0.0.1:8080/api/auth/me"
DOMAIN="pandaldepartment.tech"
WAIT_SECONDS=120

if [[ $EUID -ne 0 ]]; then
  echo "Run this with sudo." >&2
  exit 1
fi

say()  { printf '%s\n' "$*"; }
fail() { printf 'FAILED: %s\n' "$*" >&2; }

# ---------------------------------------------------------------- the stack

for unit in mysql nginx; do
  if systemctl is-active --quiet "$unit"; then
    say "$unit: already running"
  else
    say "$unit: starting"
    systemctl start "$unit" || { fail "could not start $unit"; exit 1; }
  fi
done

if systemctl is-active --quiet pandal; then
  say "pandal: already running - restart it instead if you want to pick up changes"
else
  say "pandal: starting"
  systemctl start pandal || { fail "systemd could not start pandal"; exit 1; }
fi

# ------------------------------------------------------------- the real check
#
# A cold start builds and validates the schema before it listens, so give it
# time rather than declaring failure while it is still working.

say "waiting for the application to answer (up to ${WAIT_SECONDS}s)"
deadline=$((SECONDS + WAIT_SECONDS))
code=""
while (( SECONDS < deadline )); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$APP_URL" 2>/dev/null)
  # 401 is the correct answer to an unauthenticated request: it proves the app
  # is up AND that security is wired in. A 200 here would be alarming.
  [[ "$code" == "401" ]] && break
  sleep 2
done

if [[ "$code" != "401" ]]; then
  fail "the application did not come up (last reply: ${code:-no response})"
  echo
  echo "Last 30 log lines:" >&2
  journalctl -u pandal -n 30 --no-pager >&2
  exit 1
fi
say "pandal: up and answering"

# And the same thing through nginx, which is how the world reaches it.
#
# It has to be asked by name over HTTPS. Both nginx server blocks are name-based,
# so a plain request to 127.0.0.1 carries "Host: 127.0.0.1", matches no
# server_name and comes back 404 - which says nothing about whether the site
# works. --resolve sends the right Host and SNI while still connecting to this
# machine, so the check does not depend on public DNS either.
site=$(curl -sk -o /dev/null -w '%{http_code}' --max-time 10   --resolve "${DOMAIN}:443:127.0.0.1"   "https://${DOMAIN}/api/auth/me" 2>/dev/null)
if [[ "$site" == "401" ]]; then
  say "nginx: forwarding correctly"
else
  fail "the app is up but nginx is not forwarding to it (got ${site:-no response})"
  echo "Check: nginx -t && systemctl status nginx" >&2
  exit 1
fi

echo
say "Running: https://${DOMAIN}"
