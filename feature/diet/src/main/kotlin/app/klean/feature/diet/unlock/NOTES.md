# Unlock sheet: spike notes (Task 11.1)

Spike run on **Waydroid, Android 13 (API 33), x86_64**, not on a real Android 14+ phone. Results for
14+ (full-screen-intent permission, Play policy) still need a check on real hardware (M16).

## Mechanism chosen (least invasive that works on 14+)
- `ScreenStateMonitor` registers a **runtime** receiver for `SCREEN_OFF`, `SCREEN_ON` and
  `USER_PRESENT` from `KleanApp.onCreate` (these broadcasts cannot be declared in the manifest).
  `RECEIVER_NOT_EXPORTED`: system broadcasts still arrive, other apps can't spoof them.
- No foreground service: nothing needs to run continuously and "watch the screen" isn't a valid FGS
  type. No `SYSTEM_ALERT_WINDOW`, no background activity start.
- On wake, `UnlockSheetTrigger` asks `UnlockSheetPolicy` (≥ 3 h screen off, not quiet hours, setting
  on, diet on, no sheet in the last 3 h). If yes: a `prompt_log` row (trigger UNLOCK_SHEET, format
  HEADS_UP) and a notification on channel `unlock_sheet` (HIGH) with a **full-screen intent** to
  `UnlockSheetActivity` when `NotificationManagerCompat.canUseFullScreenIntent()`, otherwise the same
  notification as a heads-up whose tap opens the sheet.
- `UnlockSheetActivity` is **not** `showWhenLocked` (manifest + `setShowWhenLocked(false)`), so it
  waits behind the keyguard and shows right after unlock. It cancels its notification in `onResume`.

## What happened on Waydroid
Waydroid ships with the lock screen disabled (`locksettings get-disabled` → true). Both modes tried;
lock = `adb shell input keyevent KEYCODE_SLEEP`, wake = `KEYCODE_WAKEUP`, unlock = `input keyevent 82`.
Debug "Pretend the phone was locked for 3 h" (Diet settings → Debug) stands in for the 3 h wait.

1. **Swipe keyguard on** (`adb shell locksettings set-disabled false`): on `SCREEN_ON` the sheet was
   posted with `fullScreen=true`; ActivityTaskManager started `UnlockSheetActivity` at once, but the
   keyguard stayed on top (screenshot: plain lock screen, no sheet over it). After `keyevent 82` the
   sheet was the focused window. **No `requestDismissKeyguard` needed.**
2. **No keyguard** (Waydroid default): wake = unlock. The full-screen intent did not launch the
   activity (the device counts as in use), the system showed it as a heads-up "Today so far · 3 entries
   today. Tap to see the list."; tapping it (from the shade) opened the sheet. This is also what the
   Android 14+ fallback looks like.
3. During quiet hours (07:46, default 00:00–08:00) wake + unlock posted nothing.
4. Second lock/unlock right after: nothing (screen was off for seconds, last sheet minutes ago).

## Known limits
- The receiver only lives as long as the process. If Android kills Klean, screen events are missed
  until something restarts it (check-in alarm, widget update, tile, notification action, app open).
  At process start with the screen off, `lastScreenOffAt = now` (conservative: may skip one sheet,
  never shows one after a short break). Battery-optimisation exemption (Permissions) helps.
- A glance at the lock screen without unlocking counts as "screen on": the break is measured from
  the last screen-off, and the sheet may already be posted at that `SCREEN_ON` (it then waits behind
  the keyguard, or sits in the shade for its 10 min timeout).
- Android 14+: `USE_FULL_SCREEN_INTENT` may be refused (Play policy limits it to calls/alarms); the
  Permissions screen and setup show a row for it on API 34+, and without it the heads-up path is used.
- No widget highlight for the fallback yet (plan §6.3 mentions one); the status line and widget stay
  as they are.
