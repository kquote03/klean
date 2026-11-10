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

## Protocol (`wear/src/main/kotlin/app/klean/wear/data/WearProtocol.kt`)

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

## Not built yet (phone side, outside `wear/`)

Lane NEXT was limited to `wear/` for M27, so the phone half is still to do:

1. **Move `WearProtocol.kt` to `:shared`.** It's pure Kotlin with no Android types, so both apps can use it.
2. **Add `:core:wear` (or an `app/` service) with `WearSyncService : WearableListenerService`:**
   - manifest filter `MESSAGE_RECEIVED` with path prefix `/klean`;
   - it decodes each message with `WearCodec.decode` and runs it as in the table above.
3. **Add `res/values/wear.xml` on the phone:** `<string-array name="android_wear_capabilities"><item>klean_phone</item></string-array>`.
4. **Add `WatchStatePublisher`:**
   - It builds a `WatchState` from Diet, Tasks/Reminders, Workout and `ModuleGate`.
   - It runs through the existing change listeners (diet, `TaskChangeListener`, `ReminderChangeListener`, `WorkoutChangeListener`, module toggles), debounced by about 2 s.
   - It calls `DataClient.putDataItem(PutDataRequest.create("/klean/state").setData(WearCodec.encodeState(s)).setUrgent())`.
5. **Alarm:** when `AlarmActivity` rings on the main phone, send `/klean/alarm` to connected nodes.
6. **Notifications:** phone notifications bridge to the watch by default. Add `NotificationCompat.WearableExtender` actions to the status notification (voice reply + recent-food chips) and to reminders.
7. **Optional on-watch session:** a timed or cardio session as a foreground service of type `health`.
8. **CI:** the nightly attaches `wear-debug.apk` (`.github/workflows` belongs to the parent).

## Checks for M29 (real watch)

- Each tile renders on the watch and updates within a few seconds of a phone change.
- The complication count matches Today's diet count, and tapping it opens food capture.
- A voice capture from the watch lands in the right module, with source WATCH.
- A chip tap logs that food and shows "Sent to your phone".
- With the phone out of range, the watch shows "Couldn't reach your phone…" and nothing is lost on the phone.
- Starting a saved workout from the watch opens a session on the phone. "Log a set" adds a set.
- An alarm on the phone buzzes the watch.

## Tests (Robolectric, `./gradlew :wear:testDebugUnitTest`)

- `WearCodecTest` (6):
  - every message round-trips on its path;
  - a wrong path, garbage or a blank message → null;
  - unknown fields are ignored;
  - a newer state version is ignored;
  - text is capped;
  - a huge state is trimmed oldest food first.
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
