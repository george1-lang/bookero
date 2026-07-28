#!/usr/bin/env bash
# One-command demo preparation: ingest reference data, seed the flight network,
# generate demand, and train the forecast model.
#   ./scripts/seed.sh            # against the native local stack
#   API=http://localhost:8080 ANALYTICS=http://localhost:8001 ./scripts/seed.sh   # against Compose
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
[ -f "$ROOT/scripts/env.sh" ] && source "$ROOT/scripts/env.sh"

API="${API:-http://localhost:${API_PORT:-8090}}"
ANALYTICS="${ANALYTICS:-http://localhost:${ANALYTICS_PORT:-8001}}"

echo "api       $API"
echo "analytics $ANALYTICS"

echo
echo "[1/5] login as analyst"
TOKEN=$(curl -fsS -X POST "$API/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"analyst@bookero.local","password":"password"}' \
  | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || { echo "login failed" >&2; exit 1; }
echo "      token acquired"

echo "[2/5] ETL: OpenFlights airports and routes -> postgres"
curl -fsS -X POST "$ANALYTICS/etl/run" -H 'Content-Type: application/json' | head -c 400; echo

echo "[3/5] seed Bookero flights, fare classes and inventory"
curl -fsS -X POST "$API/api/simulate/seed" -H "Authorization: Bearer $TOKEN" | head -c 400; echo

echo "[4/5] run demand simulation"
curl -fsS -X POST "$API/api/simulate" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"intensity":5}' | head -c 400; echo

echo "[5/5] train demand model"
curl -fsS -X POST "$ANALYTICS/demand/train" | head -c 400; echo

echo
echo "ready. open the web app and sign in as analyst@bookero.local / password"
