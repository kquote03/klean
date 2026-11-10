# Klean on Wear OS (M27)

The `wear/` module is a Wear OS app (min SDK 30) with the same application id as the phone: `app.klean`, or `app.klean.debug` for debug builds. The Data Layer pairs apps by package name and signing key.

The watch **never talks to the backend**. The phone sends it a snapshot, and the watch sends small messages back. Both directions go through the Google Play services Data Layer, which needs Play services on both devices. That's fine for the watch path; the phone keeps working without Play services.

## What's on the watch

| Surface | Class | Shows / does |
|---|---|---|
| Tile "Klean · Log food" | `QuickLogTileService` | Up to 3 recent foods as one-tap chips (→ `LogFood`), plus "Speak" (voice/keyboard capture for Diet) |
| Tile "Klean · Today's food" | `TodayFoodTileService` | "Today · N entries", the latest 3 with times, "Add" |
| Tile "Klean · Next up" | `NextUpTileService` | The next reminder or task, with its time, plus "Capture" (universal) |
| Tile "Klean · Workout" | `WorkoutTileService` | Up to 3 saved workouts (→ `StartWorkout`). During a session: its name, set count and "Log a set" |
| Complication "Klean food log" | `FoodLogComplicationService` | SHORT_TEXT: today's entry count. Tap opens food capture. With Diet off, it shows "Klean" (universal capture) |
| Launcher entry "Klean" | `WatchCaptureActivity` | System voice/keyboard input → `Capture` |
| Alarm buzz | `WatchListenerService` | `/klean/alarm` from the phone → three long vibrations |

- **Colours:** the dark palette from `core/design/tokens/klean-color-tokens.json`. `WearColorsTest` fails if the tokens change and `WearColors` doesn't.
- **Copy:** calm. When Diet is off, the food tiles say "Diet is off on your phone".

## Protocol (`shared/src/commonMain/kotlin/app/klean/shared/wear/WearProtocol.kt`, used by both apps)

- **Phone → watch:** a DataItem at `/klean/state` holding `WatchState` JSON. It has these fields:
  - `v`, `generatedAt`
  - `todayFood[]`, `recentFoods[]`
  - `next`
  - `workouts[]`, `active`
  - `dietOn`

  The rules for handling it:
  - The phone re-puts the item after any change that matters to the watch: a diet entry, a task or reminder change, a workout template or session change, or a module toggle.
  - The watch keeps only the newest snapshot (by `generatedAt`), then refreshes every tile and the complication.
  - An item over 90 KB is trimmed, oldest food first.
  - A newer protocol version (`v` > 1) is ignored.
- **Watch → phone:** messages (`MessageClient`) to the node with capability `klean_phone`.
  - JSON with a `"t"` discriminator; unknown fields are ignored.
  - Text is trimmed and capped at 500 characters.

  | Path | Message | The phone should… |
  |---|---|---|
  | `/klean/capture` | `{"t":"capture","text","module"?,"viaVoice"}` | `UniversalCaptureService.submit(CaptureInput(text, CaptureSource.WATCH, now, viaVoice, targetModule = module))` |
  | `/klean/food/log` | `{"t":"log_food","text"}` | Submit with `targetModule = diet`, source `WATCH` |
  | `/klean/workout/start` | `{"t":"start_workout","templateId"}` | Start that template's session (as the Workout screen does) |
  | `/klean/workout/set` | `{"t":"log_set","sessionId","exercise","reps","weightKg"?}` | Add a set to the open session |
  | `/klean/alarm` (phone → watch) | `{"t":"alarm","reminderId","title","ringing"}` | (The watch vibrates) |

## Phone side (27.7, `app/src/main/kotlin/app/klean/watch/`)

Everything here sits behind `WearDataLayer`. `WearDataLayerModule` picks `PlayWearDataLayer` when
`GoogleApiAvailability` says Play services work, and `NoWearDataLayer` otherwise (Waydroid, de-Googled phones).
With the no-op, the publisher never starts, nothing reads the database for the watch, and the alarm ping returns at once.
Play services can also be present without the Wearable API (no Wear OS app on the phone; Waydroid's GApps image
does this): the first `API_UNAVAILABLE` turns `PlayWearDataLayer` off for the rest of the process, with the same effect.

| Piece | What it does |
|---|---|
| `WearSyncService` (`WearableListenerService`, manifest filter `MESSAGE_RECEIVED`, `wear://*/klean…`) | Hands each message to `PhoneWearInbox` (blocking, 8 s timeout, never throws) |
| `PhoneWearInbox` | `WearCodec.decode`. Capture → `UniversalCaptureService.submit(CaptureInput(text, WATCH, now, viaVoice, targetModule))`. LogFood → the same with `targetModule = diet`. StartWorkout / LogSet → `WatchWorkouts` (`WorkoutRepository.start` / `addSet`, unknown template or closed session = ignored; Workout off = ignored). The phone's own Alarm message is ignored. `StatusRefresh` after a save |
| `WatchStatePublisher` (via `AppWatchStatePublisher`, a `StatusSurface`) | Every module's existing `StatusRefresh` call after a write reaches it, and it follows `ModuleGate.disabledIds`. Debounced 2 s. It reads `RepoWatchSnapshotSource` and puts `/klean/state` (urgent) unless the snapshot is unchanged. Started in `KleanApp` |
| `RepoWatchSnapshotSource` / `WatchSnapshots` | Today's food (oldest first) and up to 5 quick repeats (Diet on). Next up: the soonest timed reminder (at its snooze time) or task due today still ahead, else the top untimed task for today. Saved workouts by position, the open session (template name, else "Workout") with its set count. `dietOn`, and `modules` (enabled ids; new optional field) |
| `res/values/wear.xml` | Capability `klean_phone` |
| `WearAlarmPing` (`AlarmRingListener`, `core/notifications`) | `ReminderFirer` tells the listeners when an alarm rings on the main phone (also on the looping-notification fallback); `ReminderActions` tells them `ringing=false` when a ringing alarm is handled. Sends `/klean/alarm` to every connected node |
| Status notification (`KleanStatusNotifier`) | A `WearableExtender` copy of "Add something": voice/keyboard plus up to 5 chips from `UniversalCaptureService.suggestions` (`StatusReplyChoices`). Same `StatusReplyReceiver`, so it lands through universal capture |
| Reminder notifications (`AndroidReminderNotifier`) | Watch buttons: ping → Done, 10 min; ringing alarm → Dismiss, 10 min. "Later…" opens a picker on the phone, so it stays phone-only |
| CI | `build.yml` builds `:wear:assembleDebug`, checks its signing key and uploads `wear-apk` (`klean-wear-debug-<version>.apk`); `ci.yml` attaches `wear-debug.apk` to the rolling `nightly` release. The wear debug build uses the same `KLEAN_DEBUG_KEYSTORE` as the phone, which pairing needs |

**Not built:** the optional on-watch timed/cardio session (foreground service type `health`). The dated `nightly.yml` release still carries the phone APK only.

## Checks for M29 (real watch)

- Each tile renders on the watch and updates within a few seconds of a phone change.
- The complication count matches Today's diet count, and tapping it opens food capture.
- A voice capture from the watch lands in the right module, with source WATCH.
- A chip tap logs that food and shows "Sent to your phone".
- With the phone out of range, the watch shows "Couldn't reach your phone…" and nothing is lost on the phone.
- Starting a saved workout from the watch opens a session on the phone. "Log a set" adds a set.
- An alarm on the phone buzzes the watch; dismissing it on the phone sends `ringing=false`.
- The status notification on the watch offers "Add something" with voice and up to 5 chips; a chip lands through universal capture.
- A reminder on the watch shows Done and 10 min; both act on the phone.

## Tests (`:shared:jvmTest` for the codec, `:wear:testDebugUnitTest` for the watch, `:app:testDebugUnitTest` for the phone side)

- `WearCodecTest` (7, now in `:shared`):
  - every message round-trips on its path;
  - a wrong path, garbage or a blank message → null;
  - unknown fields are ignored;
  - a newer state version is ignored;
  - text is capped;
  - a huge state is trimmed oldest food first;
  - the `modules` field travels, and an older phone leaves it empty.
- `TileRendererTest` (7):
  - every tile's layout texts and click targets;
  - the Diet-off text;
  - the `TestTileClient` request path with stored state and with no state.
- `ComplicationAndInboxTest` (8):
  - complication text, description and tap intent;
  - Diet off; only SHORT_TEXT;
  - state stored, with a late older one ignored;
  - only the alarm accepted from the phone;
  - chip/voice → messages;
  - a chip tap sends and toasts;
  - colours match the tokens.
- Phone (`app/src/test/kotlin/app/klean/watch/`, fakes for the Data Layer, capture handlers and workouts):
  - `PhoneWearInboxTest` (7): capture with source WATCH, module and voice flag; no module → phone routing; chip → diet; start/log a set, unknown template or closed session ignored; Workout off; garbage, wrong path and the alarm message ignored; a failing handler never throws;
  - `WatchStatePublisherTest` (7): one put after the debounce; a burst is one put; an unchanged snapshot isn't re-put; a module toggle republishes; a broken Data Layer or source never stops it; no Play services → nothing read; a layer that loses the Wearable API stops reads;
  - `WatchSnapshotsTest` (5): next up (timed soonest, snooze time, untimed fallback, none), workouts and the open session;
  - `WearAlarmPingTest` (3), `WearDeclarationsTest` (2: the `klean_phone` capability, the manifest filter reaches `WearSyncService` only under `/klean`).
- `ReminderEngineTest` (+2: the alarm ping on ring and on handling; fallback pings, pings and other phones don't), `ReminderWearActionsTest` (2), `KleanStatusNotifierTest` (+1: the watch reply action with 5 chips).
