#!/usr/bin/env bash
# check.sh [dir/screen ...]: headless screenshots of prototype screens into ~/.hermes/cache/scratch/ui-proto/
# and their clutter counts into counts.js (read by index.html). With no arguments, does all 24 screens.
# Needs: nix (chromium). Run from anywhere.
set -eu
cd "$(dirname "$0")"
OUT=${OUT:-$HOME/.hermes/cache/scratch/ui-proto}
mkdir -p "$OUT"
SCREENS="today plan track people workout sleep diet add-sheet"
if [ $# -eq 0 ]; then set -- $(for d in a-soft-cards b-airy-list c-bento; do for s in $SCREENS; do echo $d/$s; done; done); fi
nix shell nixpkgs#chromium -c bash -c '
  OUT=$1; shift
  for p in "$@"; do
    f="file://$PWD/$p.html"; n=$(echo "$p" | tr / -)
    chromium --headless --no-sandbox --hide-scrollbars --force-device-scale-factor=1 --window-size=412,915 \
      --virtual-time-budget=3000 --screenshot="$OUT/$n.png" "$f" >/dev/null 2>&1
    if [ -n "${DARK:-}" ]; then
      chromium --headless --no-sandbox --hide-scrollbars --force-device-scale-factor=1 --window-size=412,915 \
        --blink-settings=preferredColorScheme=0 --virtual-time-budget=3000 --screenshot="$OUT/$n-dark.png" "$f" >/dev/null 2>&1
    fi
    c=$(chromium --headless --no-sandbox --window-size=412,915 --virtual-time-budget=3000 --dump-dom "$f" 2>/dev/null \
        | grep -o "data-clutter=\"[^\"]*\"" | head -1 | sed "s/data-clutter=//; s/&quot;/\"/g; s/^\"//; s/\"$//")
    echo "$p $c"
  done' _ "$OUT" "$@" | tee "$OUT/counts.txt"
# Merge into counts.js
touch "$OUT/counts.all"; cat "$OUT/counts.txt" "$OUT/counts.all" | awk '!seen[$1]++' | sort > "$OUT/counts.tmp" && mv "$OUT/counts.tmp" "$OUT/counts.all"
{ echo "window.CLUTTER = {"; awk '{ printf "  \"%s\": %s,\n", $1, $2 }' "$OUT/counts.all"; echo "};"; } > counts.js
