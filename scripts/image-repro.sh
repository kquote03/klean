#!/usr/bin/env bash
# Builds the klean-server image twice without cache and fails unless both image digests match.
# Usage: scripts/image-repro.sh [platform (default linux/amd64)]
# Needs docker buildx with an OCI-capable builder (the containerd image store or a docker-container builder).
set -euo pipefail
platform=${1:-linux/amd64}
SOURCE_DATE_EPOCH=$(git log -1 --format=%ct)
export SOURCE_DATE_EPOCH
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
build() {
  docker buildx build --no-cache --platform "$platform" --build-arg REVISION=repro-check \
    --output "type=oci,dest=$out/$1.tar,rewrite-timestamp=true" . >/dev/null 2>"$out/$1.log" \
    || { tail -30 "$out/$1.log" >&2; exit 1; }
  tar -xOf "$out/$1.tar" index.json | grep -o '"digest":"sha256:[0-9a-f]*"' | head -1 | cut -d'"' -f4
}
first=$(build a)
second=$(build b)
echo "first:  $first"
echo "second: $second"
[ -n "$first" ] && [ "$first" = "$second" ] || { echo "NOT REPRODUCIBLE"; exit 1; }
echo "reproducible: $first"
