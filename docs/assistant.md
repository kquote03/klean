# Klean and voice assistants (Google Assistant, Gemini)

Research date: 2026-11-10 (M21, 30-minute timebox).

## Summary

Klean ships **app shortcuts plus a capture deep link**, and declares three **App Actions capabilities**. All of them open the capture sheet, prefilled at most. Nothing is saved until you tap Save. Klean never registers as the default assistant.

Today there is no supported third-party way to make Gemini add a Klean task by voice and have it saved. Google is replacing Assistant with Gemini, and Gemini's route for third-party apps (AppFunctions) is still in private preview.

## What exists in late 2026

| Route | Status | What it gives Klean |
|---|---|---|
| **App Actions** (built-in intents in `shortcuts.xml`) | Still documented. Google removed Assistant from Android phones, tablets and Wear OS from **4 September 2026**, replacing it with Gemini. Whether Gemini honours BIIs is unclear. Developer reports (e.g. [Stack Overflow 79854913](https://stackoverflow.com/questions/79854913)) say `OPEN_APP_FEATURE` doesn't trigger from Gemini even when the adb deep link works. | Declared anyway. It costs nothing, and it works wherever Assistant still runs (cars with Google built-in). |
| **Built-in intents catalogue** | The current [BII index](https://developer.android.com/reference/app-actions/built-in-intents/bii-index) has no `CREATE_TASK` or `CREATE_REMINDER`; the plan's names no longer exist. The closest are `actions.intent.CREATE_THING` (`thing.name`), `actions.intent.UPDATE_ITEM_LIST` (`itemList.itemListElement.name`) and `actions.intent.RECORD_FOOD_OBSERVATION` (`foodObservation.aboutFood.name`). | Those three, each mapped to the capture sheet. |
| **AppFunctions** (`androidx.appfunctions`, Android 16+) | Apps expose functions that agents call, like on-device MCP tools. Callers need `EXECUTE_APP_FUNCTIONS`. [Gemini integration is in **private preview** with trusted testers](https://developer.android.com/ai/appfunctions) (as of May 2026). The Jetpack library reached `1.0.0-rc01` on 2026-11-04. | **Not built.** It's the right future route: functions like `createTask(title, due)` and `logFood(text)` would call the same capture router. Wait for the public Gemini rollout and a stable library. |
| **Gemini "connected apps" / extensions** | Partner-only (Google picks the partners). | Not available to Klean. |
| **App shortcuts** (static, long-press the launcher icon) | Stable. Gemini and the launcher can open them, and they can be pinned. | Shipped: Capture, Log food, New task, Remind me. |
| **Deep link** `klean://capture?text=…&module=…` | Ours. Works from any app, adb, the web, NFC tags and automation apps (Tasker, Macrodroid). | Shipped. Routines can open it with text. |

Sources:
- <https://developer.android.com/develop/devices/assistant/intents>
- <https://developer.android.com/develop/devices/assistant/action-schema>
- <https://developer.android.com/reference/app-actions/built-in-intents/common/create-thing>
- <https://developer.android.com/reference/app-actions/built-in-intents/productivity/update-item-list>
- <https://developer.android.com/reference/app-actions/built-in-intents/health-and-fitness/record-food-observation>
- <https://developer.android.com/ai/appfunctions>
- <https://developer.android.com/jetpack/androidx/releases/appfunctions>
- <https://pixelforce.com/blog/google-assistant-latest-features> (the Assistant shutdown timeline)

## What Klean ships

`app/src/main/res/xml/shortcuts.xml` (and its twin `app/src/debug/res/xml/shortcuts.xml` for `app.klean.debug`; `ShortcutsXmlTest` keeps them in step). It is registered on `MainActivity` with `android.app.shortcuts`. Every intent targets `app.klean.core.capture.CaptureActivity` by class, and that activity is exported for this.

| Entry | Kind | Opens |
|---|---|---|
| `actions.intent.CREATE_THING` | capability | capture, `thing.name` → text |
| `actions.intent.UPDATE_ITEM_LIST` | capability | capture for Tasks, `itemList.itemListElement.name` → text |
| `actions.intent.RECORD_FOOD_OBSERVATION` | capability | capture for Diet, `foodObservation.aboutFood.name` → text |
| Capture | shortcut | `klean://capture` |
| Log food | shortcut | `klean://capture?module=diet` |
| New task | shortcut | `klean://capture?module=tasks` |
| Remind me | shortcut | `klean://capture?module=reminders` |

### The deep link

```
klean://capture?text=<url-encoded text>&module=<module>
```

- **`module`** (optional). Accepted values and aliases:
  - `diet`, `food`, `meal`
  - `tasks`, `task`, `todo`
  - `reminders`, `reminder`, `remind`
  - `calendar`, `event`
  - `contacts`, `contact`
  - `birthdays`, `birthday`
  - `workout`
  - `sleep`
  - `delegation`, `delegate`

  An unknown or missing module means universal capture: the router decides.
- **`text`** (optional). Up to 500 characters, only prefilled.
- **Extras:** App Actions pass `text` and `module` as intent extras, and those win over the query.
- **Source:** these captures are recorded with source `ASSISTANT`.
- **Opened as a plain link** (from a browser, `adb shell am start -d`, or the web), the link reaches `MainActivity`, which forwards it to the capture sheet.

Try it:

```
adb shell am start -a android.intent.action.VIEW -d 'klean://capture?text=Buy%20milk%20tomorrow&module=tasks'
```

Long-press Klean's icon to see the four shortcuts.

### Why only prefill, never auto-save

`CaptureActivity` is exported, so any app could open it. Prefilling is harmless, while saving on behalf of another app is not. It also matches Klean's correction model: you see where the entry goes before it lands.

## What's missing, and how to add it later

1. **AppFunctions** (Android 16+), once Gemini's integration is public:
   - Add `androidx.appfunctions` (+ compiler) to a new `:core:assistant`.
   - Expose `@AppFunction` functions: `capture(text)`, `createTask(title, dueDate?)`, `logFood(text)` and `setReminder(text, at)`. Each calls `UniversalCaptureService` with source `ASSISTANT`.
   - Test with `adb shell cmd app_function list-app-functions --package app.klean`.
   - Because AppFunctions save without a sheet, they need the same "Move to…" correction surfaced afterwards: a notification with Undo and Move.
2. **Verify on the Pixel** (M29):
   - Do the four shortcuts appear on long-press?
   - Does "Hey Google, open Log food in Klean" open the sheet?
   - Does any Gemini phrase trigger a capability?

   Record the results in `docs/acceptance-p1.md`.
3. **Dynamic shortcuts** (recently used modules) and pushing them to Assistant with `ShortcutManagerCompat.pushDynamicShortcut` + `google-shortcuts`. Skipped: that needs Play services libraries on the phone, and the benefit under Gemini is unclear.
