# Handover prompt (paste this into a fresh agent session)

You are continuing the build of **Klean**, a local-first holistic life-management app (Android + Wear OS + Ktor backend + web client), in `/home/kenan1099/Src/klean` (NixOS; public repo `github.com/kquote03/klean`).

## Read first, in this order

1. `docs/handover-2026-11-09.md`: the current state, what's done and what's next. (dated snapshot).
2. `AGENTS.md`: toolchain, conventions, gotchas, current state and next steps. It is protected; edit it only with the user's approval.
3. `.hermes/plans/2026-11-09_165408-klean-holistic-full-product.md`: the governing plan, decisions H1–H9 and milestones M16–M29.
4. `.hermes/plans/2026-11-09_170500-parallel-life-modules.md`: the per-module "Common checklist" and specs S1–S6, used by M18–M20 with the amendments in the governing plan.
5. `~/.hermes/cache/scratch/klean-subagent-brief.md`: the rules to hand every subagent, including the "State update" sections at the end.
6. `TASKS.md`, and the `android-app-development` skill (load it).

## Ground rules

- **Toolchain:** use the Nix shell for every tool (`nix develop -c …`); never install anything globally.
- **Gradle:** wrap every call in `flock ~/.hermes/cache/scratch/gradle.lock`. Only one build runs at a time on this 14 GB machine.
- **Commits:** commit after every task (Conventional Commits) and push to `main`. Tag each milestone `mN` after the gate passes:
  ```
  assembleDebug :app:assembleDebugAndroidTest :shared:jvmTest testDebugUnitTest :backend:test
  ```
- **Tests:** TDD for logic.
- **Copy:** all user-facing text is calm, with no `!` and no guilt words. It never mentions ADHD, autism or neurodivergence. Internal docs may mention AuDHD.
- **Test device:** Waydroid only (`adb -s 192.168.240.112:5555`). Hold `~/.hermes/cache/scratch/waydroid.lock` while using it, and never trigger the camera. Leave the user's Pixel alone unless they start a testing session.
- **Diet:** the module is feature-frozen. Prioritise the other modules.
- **Decisions marked Decided** in the brief need the user's approval to change.
- **Usage limit:** the 5-hour limit is the bottleneck. Run at most 2–3 parallel subagents, each in its own git worktree (`../klean-<lane>`) on its own branch. Have them commit early, then merge their branches one at a time, running the gate after each merge.

## Next steps

1. Run M18 Tasks and reminders, plus M19 Calendar, as two parallel worktree lanes. M18 owns `shared/.../time/RecurrenceRule.kt`. Merge them, gate, tag `m18` and `m19` (calendar part).
2. Run M19 Contacts and birthdays, plus M20 Workout and sleep. After merging, move the calendar and contacts `DayItemsProvider` definitions into `core/ui/module/Dashboard.kt`, and replace calendar's private recurrence expander with `RecurrenceRule`.
3. Run M26 Web client against the merged backend, alongside M25 Android sync.
4. Then: M21 Google, M22 Health Connect and GPS, M23 delegation and Hermes, M27 Wear OS, M28 capture everywhere.
5. M29: an acceptance run with the user on the Pixel, watch, Docker backend and browser. Record it in `docs/acceptance-p1.md`, then tag `p1`.

After each milestone:
- update `TASKS.md` and the handover doc;
- check CI with `gh run list -R kquote03/klean`;
- tell the user what works, what was verified on Waydroid, and what still needs their phone.
