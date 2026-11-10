#!/usr/bin/env bash
# Loads a Jib image tarball, runs it with a throwaway container and checks that the API and the web app answer.
# Usage: scripts/image-smoke.sh [backend/build/jib-image.tar] [host-port]
set -euo pipefail
tar=${1:-backend/build/jib-image.tar}
port=${2:-18089}
image=$(docker load -i "$tar" | sed -n 's/^Loaded image: //p' | tail -1)
[ -n "$image" ] || { echo "could not load $tar"; exit 1; }
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
echo "image smoke OK: $image"
