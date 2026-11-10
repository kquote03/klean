# Handover prompt (paste this into a fresh agent session)

You are continuing the build of **Klean**, a local-first holistic life-management app (Android + Wear OS + Ktor backend + web client), in `/home/kenan1099/Src/klean` (NixOS; public repo `github.com/kquote03/klean`). You are the **parent**: you plan waves, launch lanes in git worktrees, merge them one at a time, run the gate, tag, and report to the user.

## Read first, in this order

1. `AGENTS.md`: current as of 2026-11-10 afternoon. It covers toolchain, layout, conventions, parallel-lane rules, devices, gotchas, current state, next steps and deviations. It is protected: change it only with the user's approval (they approved the 2026-11-10 update).
2. `docs/handover-2026-11-10.md`: the latest dated snapshot. It holds per-module details from the M18–M20 lane reports and the full list of checks that still need the user's phone.
3. `.hermes/plans/2026-11-09_165408-klean-holistic-full-product.md`: the governing plan, decisions H1–H9 and milestones M16–M29. Your next sections are §M25 and §M26.
4. `docs/backend-api.md`: the backend's real API (M24). M25 and M26 both build on it.
5. `~/.hermes/cache/scratch/klean-subagent-brief.md`: the rules to hand every lane. Read its last section, "State update (2026-11-10 afternoon)", first; it supersedes the older notes above it.
6. `TASKS.md` (each milestone has its own section) and the `android-app-development` skill (`~/.hermes/skills/software-development/android-app-development/SKILL.md`).

## Where things stand

- `main` is clean, pushed and green on CI. Gate: 1000 tests, 0 failures, 1 skipped. There are no other branches or worktrees.
- Tags `m0`–`m20` (plus `p2-foundation`, `m19-cal`, `m24`). **All six Android life modules are built:** tasks & reminders, calendar, contacts & birthdays, workout, sleep, and the frozen diet module. The Ktor backend (M24) also exists.
- **Not started:** M21 Google, M22 Health Connect + GPS, M23 delegation + Hermes, M25 Android sync, M26 web client, M27 Wear OS, M28 capture everywhere, M29 acceptance.

## Next steps

1. **M25 Android sync ‖ M26 web client:** two parallel worktree lanes.
   - Create the worktrees from `main`: `git worktree add ../klean-sync -b m25-sync main` and `git worktree add ../klean-web -b m26-web main`.
   - **M25** (`:core:sync`) covers:
     - `SyncEngine` over `SyncTable` adapters, with push, pull and `lww`; blobs go first;
     - WorkManager, periodic every 15 min plus expedited on change;
     - pairing by QR (zxing, no Play services), with a paste-URL fallback for Waydroid; the token goes in `SecretStore`;
     - multi-device: alarms only on the main phone, prompts cleared when answered elsewhere;
     - the DAVx5 switch through `DavAccounts.syncsOwnBackend` (`TODO(M25)` in `feature/contacts`).
     - Test against an in-process Ktor `testApplication`, with two converging devices, tombstones and fault injection.
   - **M26** (`web/`) covers:
     - Vite + TypeScript + `@m3e/web` (check the current package on npm), with tokens generated from `core/design/tokens/klean-color-tokens.json`;
     - parity screens, and capture rules ported to TS with a shared JSON fixture `shared/src/commonTest/resources/capture-cases.json`;
     - an IndexedDB cache and write queue, web push, and admin login;
     - the build: `pnpm build` → `web/dist`, served by the backend at `/` via `:backend:processResources`;
     - Vitest, Playwright e2e, and `web/scripts/check-copy.mjs`. `pnpm` builds don't need the Gradle lock.
   - **Each lane's brief:** its governing-plan section, the shared subagent brief, the AGENTS.md rules, and the instruction "work only in your worktree, push only your branch, never merge/tag, never change the Room schema (report needed columns), commit early and small". Ask for a final report covering commits, test counts, gate result, Waydroid evidence, shared-file edits, schema needs, deviations, AGENTS notes, real-phone checks, and anything unfinished.
   - **M25 and M26 both touch the backend.** Expect conflicts there. Have them agree on `docs/backend-api.md` as the contract: neither changes an existing route, and new routes are added and documented.
   - Merge M25, gate, tag `m25`; then merge M26, gate, tag `m26`.
2. **Then:**
   - M21 Google Tasks + Assistant (fakes only; the user's OAuth client IDs are needed for real use; write `docs/google-oauth.md`);
   - M22 Health Connect + GPS (replaces the no-op `HealthConnectBridge` and `SleepImportSource`);
   - M23 delegation + Hermes (local stub);
   - M27 Wear OS (after `m25`);
   - M28 capture everywhere.
   - Pair them two at a time.
3. **M29:** an acceptance run with the user on the Pixel, watch, Docker backend and browser. Record it in `docs/acceptance-p1.md`, then tag `p1`.

The small follow-ups listed in AGENTS.md "Resume here" (birthday line repeated on the status line, week-view title wrapping, and others) can ride along with a later lane.

## Ground rules (details in AGENTS.md)

- **Nix and Gradle:**
  - Nix for every tool (`nix develop -c …`); never install globally.
  - `git add` new files before `nix develop`.
  - The host has no `python3`, so use `nix shell nixpkgs#python3 -c python3 …`.
  - Every Gradle call: `flock ~/.hermes/cache/scratch/gradle.lock nix develop -c ./gradlew <tasks> --console=plain -Pkotlin.compiler.execution.strategy=in-process`. The machine has 14 GB.
- **Gate:** `assembleDebug :app:assembleDebugAndroidTest :shared:jvmTest testDebugUnitTest :backend:test` must be BUILD SUCCESSFUL before tagging. Run it in the background, logging to `~/.hermes/cache/scratch/`.
- **Commits:** Conventional Commits after every task, then push `main`. Commit only your own paths (`git commit -- <paths>`); never `git add -A` or `git stash`. Don't name a tag the same as a branch.
- **Merge conflicts:** `TASKS.md` (keep both sections), `KleanApp.kt` (union), `Channels.kt` and the debug manifests (union).
- **Copy:** calm, no `!`, no guilt words (missed/streak/behind/forgot/overdue). User-facing text never mentions ADHD, autism or neurodivergence. Diet is feature-frozen; keep the app's identity holistic.
- **Devices:**
  - Waydroid only (`adb -s 192.168.240.112:5555`).
  - Do each whole check in ONE `~/.hermes/cache/scratch/waydroid.lock` hold, and install with `adb install -r -d`.
  - Never trigger a camera intent, and avoid the system file picker (it freezes Waydroid).
  - Leave the user's Pixel alone unless they start a testing session.
- **Usage limits are the bottleneck.** Both the 5-hour and the weekly limit were hit on 2026-11-10.
  - Run at most **2 lanes** at once.
  - Lanes must commit early and small.
  - If a lane is cut off, wait for the reset, check its worktree (`git log`, `git status`), then resume the same agent with SendMessage so it keeps its context.
  - Before starting a large wave, tell the user what it involves and confirm they want it run now.
- **Open decisions** (defaults in use; the user hasn't overridden them): the holistic status notification replaces diet's; Hermes is tested against a local stub; Google is tested with fakes; a birthday phrase while Contacts is off becomes a dated task (never AI).

## After each milestone

- Tick `TASKS.md`. Update `AGENTS.md` ("Current state", "Resume here", new gotchas and deviations), but only with the user's approval. Write a dated `docs/handover-YYYY-MM-DD.md`, and update this prompt's "Next steps".
- Append a "State update" section to `~/.hermes/cache/scratch/klean-subagent-brief.md` before the next wave.
- Check CI with `gh run list -R kquote03/klean`. A red run is often the `AppearanceViewModelTest` or `BackupViewModelTest` flake: read the log before rerunning.
- Tell the user what works, what was verified on Waydroid, and what still needs their phone.
