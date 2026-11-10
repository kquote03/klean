# UI prototypes, November 2026

Plain HTML/CSS, no build step. Three looks (A Soft cards, B Airy list, C Bento) × eight screens, all on navigation A
(Today, Plan, Track, People), with the app's pastel tokens (`tokens.css`, copied from `web/src/generated/tokens.css`)
and Cantarell (`fonts/`, copied from `core/design/src/main/res/font/`).

- `index.html`: every screen side by side, plus the clutter count per screen.
- `sheet.html?d=a|b|c`: one direction's eight screens at full size (used for the contact sheets).
- `check.sh`: headless Chromium screenshots of every screen (light, and dark with `DARK=1`) into
  `~/.hermes/cache/scratch/ui-proto/`, and the clutter counts into `counts.js`.
- `shared/chrome.js`: status bar, navigation bar, the Add button, icons, and the clutter counter.

View on a phone over Tailscale:

```bash
nix shell nixpkgs#python3 -c python3 -m http.server 8787 --directory prototypes/ui-2026-11
```

The design spec derived from these is `docs/ui/design-system.md`.
