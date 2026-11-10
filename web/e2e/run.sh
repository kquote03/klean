#!/usr/bin/env sh
# End-to-end run against the real backend image, built first with
#   docker build -t klean-server:e2e .   (from the repo root; override the image with KLEAN_IMAGE)
# Uses its own compose project and volume (klean-m26e2e), so a local Klean's data is never touched, and
# removes them afterwards. Run from web/ inside `nix develop .#web`.
set -eu
cd "$(dirname "$0")/.."
COMPOSE="docker compose -p klean-m26e2e -f ../deploy/docker-compose.yml"
export KLEAN_IMAGE="${KLEAN_IMAGE:-klean-server:e2e}"
export KLEAN_ADMIN_PASSWORD="${E2E_ADMIN_PASSWORD:-klean-e2e-password}"
export KLEAN_BIND="${E2E_PORT:-18080}"
export KLEAN_PUBLIC_URL="http://localhost:${KLEAN_BIND}"
export E2E_BASE_URL="http://localhost:${KLEAN_BIND}"
export E2E_ADMIN_PASSWORD="$KLEAN_ADMIN_PASSWORD"

cleanup() { $COMPOSE down -v >/dev/null 2>&1 || true; }
trap cleanup EXIT
$COMPOSE down -v >/dev/null 2>&1 || true
$COMPOSE up -d
i=0
until curl -fsS "$E2E_BASE_URL/api/v1/health" >/dev/null 2>&1; do
  i=$((i + 1)); [ "$i" -gt 60 ] && { $COMPOSE logs; exit 1; }; sleep 1
done
E2E_ONLY=1 npx playwright test --project=e2e "$@"
