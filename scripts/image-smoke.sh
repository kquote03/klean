#!/usr/bin/env bash
# Runs a klean-server image in a throwaway container and checks that the API and the web app answer.
# Usage: scripts/image-smoke.sh [image (default klean-server:dev)] [host-port (default 18089)]
set -euo pipefail
image=${1:-klean-server:dev}
port=${2:-18089}
name="klean-smoke-$$"
docker run -d --rm --name "$name" -p "$port:8080" -e KLEAN_ADMIN_PASSWORD=smoke-test-password "$image" >/dev/null
trap 'docker rm -f "$name" >/dev/null 2>&1 || true' EXIT
for _ in $(seq 1 60); do
  curl -fsS "localhost:$port/api/v1/health" >/dev/null 2>&1 && break
  sleep 1
done
fail() { echo "SMOKE FAIL: $1"; docker logs "$name" 2>&1 | tail -40; exit 1; }
curl -fsS "localhost:$port/api/v1/health" | grep -q '"ok"' || fail "health"
curl -fsS "localhost:$port/" | grep -q '<title>Klean</title>' || fail "web app missing at /"
[ "$(curl -s -o /dev/null -w '%{http_code}' "localhost:$port/calendar")" = 200 ] || fail "SPA fallback /calendar"
[ "$(curl -s -o /dev/null -w '%{http_code}' "localhost:$port/api/v1/nope")" = 404 ] || fail "unknown API path must stay 404"
[ "$(docker exec "$name" id -u)" != 0 ] || fail "must not run as root"
docker exec "$name" sh -c 'test -w /data' || fail "/data must be writable"
echo "image smoke OK: $image"
