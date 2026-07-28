#!/usr/bin/env bash
# Start / stop the whole Bookero stack natively (no Docker on this workstation).
#   ./scripts/stack.sh up     -> postgres, analytics, api, web
#   ./scripts/stack.sh down
#   ./scripts/stack.sh status
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT/scripts/env.sh"

RUN="$ROOT/.run"
mkdir -p "$RUN"

wait_for() {
  local url="$1" name="$2" tries="${3:-60}"
  for _ in $(seq 1 "$tries"); do
    if curl -fsS -m 3 -o /dev/null "$url"; then echo "  $name ready"; return 0; fi
    sleep 2
  done
  echo "  $name DID NOT COME UP ($url)" >&2
  return 1
}

start_postgres() {
  if "$PGBIN/pg_isready" -q; then echo "  postgres already up on $PGPORT"; return 0; fi
  "$PGBIN/pg_ctl" -D "$ROOT/.localdb/data" -o "-p $PGPORT" -l "$ROOT/.localdb/pg.log" -w start >/dev/null 2>&1
  "$PGBIN/pg_isready" -q && echo "  postgres up on $PGPORT"
}

start_analytics() {
  ( cd "$ROOT/services/analytics" && \
    nohup .venv/Scripts/python.exe -m uvicorn app.main:app --host 127.0.0.1 --port "$ANALYTICS_PORT" \
      > "$RUN/analytics.log" 2>&1 & echo $! > "$RUN/analytics.pid" )
  wait_for "http://localhost:$ANALYTICS_PORT/health" "analytics"
}

start_api() {
  ( cd "$ROOT/services/api" && \
    nohup mvn -B -q spring-boot:run > "$RUN/api.log" 2>&1 & echo $! > "$RUN/api.pid" )
  wait_for "http://localhost:$API_PORT/actuator/health" "api" 90
}

start_web() {
  ( cd "$ROOT/apps/web" && \
    nohup npm run dev -- --port "$WEB_PORT" > "$RUN/web.log" 2>&1 & echo $! > "$RUN/web.pid" )
  wait_for "http://localhost:$WEB_PORT/login" "web" 90
}

kill_port() {
  # Windows: resolve the listener PID for a port and terminate the process tree.
  local port="$1"
  local pids
  pids=$(powershell.exe -NoProfile -Command \
    "(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue).OwningProcess" 2>/dev/null | tr -d '\r')
  for pid in $pids; do
    [ -n "$pid" ] && taskkill.exe //PID "$pid" //T //F >/dev/null 2>&1
  done
}

case "${1:-up}" in
  up)
    echo "starting bookero stack"
    start_postgres
    start_analytics
    start_api
    start_web
    echo "web http://localhost:$WEB_PORT  api http://localhost:$API_PORT  analytics http://localhost:$ANALYTICS_PORT"
    ;;
  down)
    echo "stopping bookero stack"
    kill_port "$WEB_PORT"; kill_port "$API_PORT"; kill_port "$ANALYTICS_PORT"
    rm -f "$RUN"/*.pid
    echo "  services stopped (postgres left running)"
    ;;
  status)
    "$PGBIN/pg_isready" || true
    curl -s -o /dev/null -w "analytics :$ANALYTICS_PORT -> %{http_code}\n" "http://localhost:$ANALYTICS_PORT/health"
    curl -s -o /dev/null -w "api       :$API_PORT -> %{http_code}\n" "http://localhost:$API_PORT/actuator/health"
    curl -s -o /dev/null -w "web       :$WEB_PORT -> %{http_code}\n" "http://localhost:$WEB_PORT/login"
    ;;
  *) echo "usage: stack.sh {up|down|status}" >&2; exit 2 ;;
esac
