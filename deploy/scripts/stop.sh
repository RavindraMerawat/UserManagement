#!/usr/bin/env bash
#
# Stop Pandal Office Management.
#
#   sudo /opt/pandal/scripts/stop.sh          stop the application
#   sudo /opt/pandal/scripts/stop.sh --all    stop nginx and MySQL as well
#
# By default this stops only the application. nginx and MySQL are left running
# on purpose: nginx keeps answering, so visitors get a clear error page rather
# than a connection that hangs, and MySQL holds the data - stopping it gains
# nothing and risks interrupting a write.
#
# The service is left disabled-free: `systemctl enable` is untouched, so a
# reboot brings the app back. Use `systemctl disable pandal` if you want it to
# stay down across a reboot.

set -uo pipefail

STOP_ALL=false
[[ "${1:-}" == "--all" ]] && STOP_ALL=true

if [[ $EUID -ne 0 ]]; then
  echo "Run this with sudo." >&2
  exit 1
fi

say()  { printf '%s\n' "$*"; }
fail() { printf 'FAILED: %s\n' "$*" >&2; }

# ---------------------------------------------------------------- the app

if systemctl is-active --quiet pandal; then
  say "pandal: stopping"
  systemctl stop pandal
else
  say "pandal: already stopped"
fi

# Confirm it actually let go of the port. systemd returning from `stop` means
# it sent the signal and the unit left the active state - a JVM that is slow to
# die can still be holding 8080, and the next start would then fail to bind.
for _ in $(seq 1 30); do
  ss -lnt 2>/dev/null | grep -q ':8080 ' || break
  sleep 1
done

if ss -lnt 2>/dev/null | grep -q ':8080 '; then
  fail "something is still listening on 8080"
  ss -lntp | grep ':8080 ' >&2
  exit 1
fi
# A unit can be left in the "failed" state if it was stopped mid-startup, which
# makes `systemctl status` look alarming when nothing is actually wrong. Clear
# it so the next status read is honest.
if [[ "$(systemctl is-failed pandal 2>/dev/null)" == "failed" ]]; then
  systemctl reset-failed pandal
  say "pandal: cleared a stale failed state"
fi
say "pandal: stopped, port 8080 free"

# ---------------------------------------------------------------- the rest

if $STOP_ALL; then
  for unit in nginx mysql; do
    if systemctl is-active --quiet "$unit"; then
      say "$unit: stopping"
      systemctl stop "$unit"
    else
      say "$unit: already stopped"
    fi
  done
  echo
  say "Everything stopped. Bring it back with: sudo /opt/pandal/scripts/start.sh"
else
  # With the app down, nginx has nothing to forward to and will answer 502.
  # That is the intended behaviour while stopped: an honest error beats a
  # request that hangs until the browser gives up.
  say "nginx: left running (the site will answer 502 until the app is started)"
  say "mysql: left running (your data is there, and nothing is writing to it)"
  echo
  say "Start again with: sudo /opt/pandal/scripts/start.sh"
fi
