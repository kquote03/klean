#!/usr/bin/env bash
# Builds the backend image tarball twice from clean outputs and fails unless both digests match.
# Usage: scripts/image-repro.sh   (needs pnpm on PATH; Gradle command from $GRADLE, default ./gradlew)
set -euo pipefail
gradle=${GRADLE:-./gradlew}
args=(:backend:jibBuildTar --console=plain -Pklean.web=require -Pklean.image.revision=repro-check)
build() {
  rm -rf backend/build/jib-image.* web/dist
  $gradle :backend:clean --console=plain >/dev/null
  $gradle "${args[@]}" >/dev/null
  cat backend/build/jib-image.digest
}
first=$(build)
second=$(build)
echo "first:  $first"
echo "second: $second"
[ "$first" = "$second" ] || { echo "NOT REPRODUCIBLE"; exit 1; }
echo "reproducible: $first"
