# UI audit — the app as it is on 2026-11-10

Phase 1.1 of the UI plan (`.hermes/plans/2026-11-10_222226-ui-overhaul-and-remaining-milestones.md`).

- **Build audited:** `app.klean.debug` 0.1.0-280-7151229 on Waydroid (540×1212, Android 13), with the data already on the device (3 open tasks, 4 reminders, 3 events, 4 runs, one food entry, no sleep, no contacts).
- **Screenshots:** `~/.hermes/cache/scratch/ui-audit/{light,dark}-NN-<screen>.png` (49 files; not committed). Contact sheets of all of them: `sheet-light.png`, `sheet-dark.png` in the same folder.
- **How to read the clutter count:** the §1.6 budget, counted by hand on the first viewport. Interactive elements exclude the navigation bar; a list of rows that open detail counts once. "Inputs" is the number of visible text fields. "Primary" is the number of filled or tonal-filled buttons plus FABs. **Budget: ≤ 7 interactive, 0 inputs (≤ 2 at most), 1 primary.**

## Rules cited

From `ui-ux-pro-max` (`references/quick-reference.md` and `--domain ux` searches, raw output in `ui-audit/ux-search.txt`):

| Id | Rule |
|---|---|
| `primary-action` | Each screen should have only one primary CTA; secondary actions visually subordinate |
| `content-priority` | Show core content first on mobile; fold or hide secondary content |
| `progressive-disclosure` | Reveal complex options progressively; don't overwhelm users upfront |
| `empty-states` | Helpful message **and action** when no content (ux-guidelines: "Show helpful message and action", severity Medium) |
| `input-labels` | Visible label per input, not placeholder-only (ux-guidelines, severity High) |
| `bottom-nav-limit` / `nav-hierarchy` | ≤ 5 bottom destinations with labels; primary vs secondary navigation clearly separated |
| `visual-hierarchy` | Hierarchy via size, spacing, contrast — not color alone |
| `touch-target-size` | 48 dp on Android |
| `text-styles-system` | Use the Material type roles (display, headline, title, body, label) |
| Compose `Arrangement` | "Use Arrangement and Alignment APIs … Don't: manual spacing hacks" (stack `jetpack-compose`, severity High) |

Nielsen heuristics: **H1** visibility of system status, **H4** consistency and standards, **H6** recognition rather than recall, **H8** aesthetic and minimalist design.

Clutter budget rules from plan §1.6: **C1** one primary action, **C2** ≤ 7 interactive, **C3** ≤ 2 inputs (normally 0), **C4** progressive disclosure, **C5** one message per empty state, **C6** breathing room (≤ 3 section headers, no needless dividers), **C7** no duplicate entry points, **C8** at most one capped chip row.

## Summary

| Screen | Interactive | Inputs | Primary | Verdict |
|---|---|---|---|---|
| Today (top) | 16 | 1 | 1 | over budget |
| Today (scrolled) | 10 | 1 | 2 | over budget |
| Today (bottom) | 10 | 1 | 3 | over budget |
| Tasks › Today | 10 | 1 | 2 | over budget |
| Tasks › Upcoming | 9 | 1 | 2 | over budget |
| Tasks › Lists | 13 | 1 | 2 | over budget |
| Tasks › Reminders | 14 | 1 | 2 | over budget |
| Tasks › Done | 6 | 0 | 1 | within, but two stacked grey sentences |
| Calendar › Agenda | 9 | 0 | 1 | over budget |
| Calendar › Week / Month | 9 | 0 | 1 | over budget |
| Contacts | 6 | 1 | 1 | within count, breaks C3/C5 |
| Birthdays | 4 | 0 | 1 | within |
| Workout | 8 | 1 | 2 | over budget |
| Sleep | 2 | 0 | 1 | within count, breaks C5/C6 |
| Diet | 7 | 1 | 1 | at the limit, breaks C3 |
| More | 4 | 0 | 0 | dead end (navigation) |
| Settings | 1 list + back | 0 | 0 | within, weak grouping |
| New in Klean sheet | 3 | 0 | 1 | within |
| Onboarding (step 1–2) | 2 / 3 + switches | 0 | 1 | within |
| Notification capture | 1 | 1 | 1 | within |

Only 4 of 20 screens meet the budget outright. The common causes, in order of weight:

1. **A capture field on almost every screen** (Today ×3, Tasks ×4 tabs, Workout, Diet, Reminders). Each brings an input + mic + send button: 3 controls and a second filled button before any content. The plan moves all of these into the Add menu.
2. **"More" hides three of the eight modules** (Workout, Sleep, Diet) and Settings behind a bare four-row list.
3. **Empty states are stacked grey sentences,** often two or three per screen, with no action.
4. **No spacing or shape scale.** Every screen lays itself out differently: card padding, section label sizes and gaps differ between Today, Workout and Sleep.
5. **Summary tiles say the same thing twice.** Today shows eight tiles, then a card per module underneath with the same data.

---

## Today

`light-01-today-top.png`, `light-02-today-scrolled.png`, `light-03-today-bottom.png`, `light-60-today-menu.png` (and `dark-…`)

- **Purpose:** see the day at a glance and get to any module.
- **Primary action:** capture something (the "Add anything" field). In practice it competes with "Going to bed", "Start a workout" and three more send buttons further down.
- **Clutter:** top 16 interactive, 1 input, 1 primary; scrolled 10 / 1 / 2; bottom 10 / 1 / 3.
- **Rules broken:**
  - **C2, H8:** eight summary tiles + a week strip with prev/next + a card header in the first viewport.
  - **C3, C7:** three capture fields on one screen (global, "Add a task for today…", "Log a workout"), all doing the same thing as the top field.
  - **C1, `primary-action`:** up to three filled buttons per viewport ("Save", "Going to bed", "Start a workout") with equal weight.
  - **C7, `content-priority`:** the tiles (Tasks 1 for today, Sleep no night logged, Workout last today) duplicate the module cards below.
  - **C5:** "Nothing planned today", "No birthdays in the next 30 days", "Tap when you go to bed and when you're up" — one grey sentence per card.
  - **`input-labels`:** the capture field has a placeholder only.
  - **H4:** card headers use a chevron ">" for "open", the Tasks header in the week card uses "Open" text; tiles are tappable but look static.
  - **Truncation:** "Lunch with Sara Haddad …", "Last workout · Today" wrap or ellipsize inside the 2-column tiles.
  - **Settings** is not on Today; "Arrange Today" hides behind a ⋮ with one item.

## Tasks (Today · Upcoming · Lists · Reminders · Done)

`light-10-tasks-{today,upcoming,lists,reminders,done}.png`

- **Purpose:** decide what to do and tick it off.
- **Primary action:** add a task — offered twice, as the inline field's send button and as the "+" FAB (**C7**).
- **Clutter:** Today 10 / 1 / 2; Upcoming 9 / 1 / 2; Lists 13 / 1 / 2; Reminders 14 / 1 / 2; Done 6 / 0 / 1.
- **Rules broken:**
  - **C1, C7:** the filled send button and the FAB both add a task.
  - **C2:** five tabs + input + mic + send + FAB before any task shows. Five scrollable tabs labelled in small type crowd the top bar.
  - **C4:** Lists shows the list chips ("Personal", "Work", "New list") and a ⋮ inline; Reminders shows a ⋮ on every row (4 extra tap targets) instead of a long-press menu.
  - **C8:** the list chips are a second chip row on a screen that already has tabs.
  - **C5:** Done stacks "Finished tasks stay here for 30 days, then tidy themselves away." and "Finished tasks show here."
  - **`input-labels`:** placeholders only ("Add a task for today…", "Remind me…").
  - **`content-priority`:** on Today and Upcoming, one task or "Nothing coming up." sits under a full capture row; the content is 10% of the screen.

## Calendar (Agenda · Week · Month)

`light-20-calendar-agenda.png` (view picker open), `light-21-calendar-week.png`, `light-22-calendar-month.png`

- **Purpose:** see what's coming and when.
- **Primary action:** "New event" (extended FAB) — clear.
- **Clutter:** 9 / 0 / 1 for each view (today, settings, prev, view picker, next, "Not now", "Show them", events, FAB).
- **Rules broken:**
  - **C2, C4:** the "Your phone's calendars" banner with two buttons sits permanently above the content; it is a one-time setup prompt.
  - **H4:** the screen has its own settings gear; Contacts and Workout too; Tasks doesn't. Settings are reached four different ways.
  - **`visual-hierarchy`:** in Agenda, start and end times ("10:00 AM / 11:00 AM") wrap into four lines and outweigh the event titles.
  - **Week:** events render as narrow blocks truncated to "Quar…", "Phys…"; at 412 dp seven columns don't fit titles.
  - The Month shot didn't switch from Week in the scripted run (the picker's "Month" item needs a second tap); the screen otherwise matches Week's chrome.

## Contacts and Birthdays

`light-30-contacts.png`, `light-31-birthdays.png`

- **Purpose:** find a person / see whose birthday is next.
- **Primary action:** add a contact ("+" FAB).
- **Clutter:** Contacts 6 / 1 / 1; Birthdays 4 / 0 / 1.
- **Rules broken:**
  - **C3:** a search field is shown over an empty list.
  - **C5, `empty-states`:** Contacts stacks two messages: "No contacts yet. Add someone with the button below, or bring in a contacts file from Settings." plus a "Show contacts from this phone" explanation paragraph. The empty state points at "the button below" instead of being the action.
  - **H6:** Birthdays' empty state asks the user to remember a phrase ("type something like 'Sara's birthday is March 3'") instead of offering the action.

## Workout

`light-40-workout.png`, `light-40b-workout-scrolled.png`

- **Purpose:** start or log a workout and see recent ones.
- **Primary action:** "Start a workout" (filled) — but "Start a run" (outlined) and the send button (filled) compete.
- **Clutter:** 8 / 1 / 2.
- **Rules broken (the user's own complaint):**
  - **C1:** two filled buttons ("Start a workout", send) above the fold.
  - **C3:** "Log a workout, e.g. 3x15 pushups" field on the module screen.
  - **C5:** "Save a workout you do often, then start it with one tap." grey hint under "Saved workouts", with an empty section.
  - **C6:** small primary-coloured section labels ("Saved workouts", "Recent") with no spacing scale; an empty "Exercises" divider list at the bottom.
  - **`content-priority`:** history ("Recent": Run ×4) starts below the halfway line; the screen opens with controls, not content.
  - **H8:** each recent row repeats "Today" and "Run · 1 item"; no summary of the week.

## Sleep

`light-41-sleep.png`

- **Purpose:** log going to bed and getting up; see recent nights.
- **Primary action:** "Going to bed" — clear.
- **Clutter:** 2 / 0 / 1.
- **Rules broken:**
  - **C5:** "Tap when you go to bed and when you're up.", "Nights you log show up here." and seven rows of "Nothing logged". Three forms of the same empty message.
  - **H8:** seven empty rows read like a log of failures, which works against the zero-guilt principle even though no guilt word is used.
  - **`visual-hierarchy`:** the instruction sentence is the largest text on the screen.

## Diet

`light-42-diet.png`, `light-42b-diet-scrolled.png`

- **Purpose:** log what you ate and see today's entries.
- **Primary action:** log food (send) — clear, but it is a text field, not a button.
- **Clutter:** 7 / 1 / 1 (calendar icon, input, mic, send, "Log again" chip, "Add something", entries).
- **Rules broken:**
  - **C3:** the capture field is the screen's first element.
  - **C7:** "Add something" text button and the send button both add an entry.
  - **H1:** "No entries 8:00 AM–8:34 PM" gap marker reads as a status line without context.
  - **Data note (not a UI rule):** the one food entry is "Lunch with Sara Haddad tomorrow at 1pm", typed into the global field and routed to Diet. The router sent an event to Diet; worth a classifier test in Phase 3.

## More

`light-50-more.png`

- **Purpose:** reach the modules and Settings that don't fit the bar.
- **Primary action:** none.
- **Clutter:** 4 / 0 / 0.
- **Rules broken:**
  - **`nav-hierarchy`, Apple HIG "avoid a More tab":** three of eight modules are second-class. Workout, Sleep and Diet need two taps from anywhere.
  - **H8:** four plain rows filling 20% of an empty screen.

## Settings

`light-51-settings.png`, `light-52-settings-scrolled.png`

- **Purpose:** turn modules on and off and set up the app.
- **Primary action:** none (a list).
- **Clutter:** within budget; one list.
- **Rules broken:**
  - **H4:** "Modules" lists nine module rows and then "Turn modules on or off" as a tenth row; "Delegation" appears in both Modules and General.
  - **C6:** small primary-coloured section labels; no leading icons, so the 20 rows read as one wall of text.
  - **H6:** "Run setup again" sits between "About" and nothing; it is the only destructive-feeling action and has no explanation.

## "New in Klean" sheet

`light-00-new-modules-sheet.png`

- **Purpose:** tell the user a new module was switched on.
- **Primary action:** "Done".
- **Clutter:** 3 / 0 / 1 (close scrim, switch, Done). Within budget; good pattern.
- **Note:** it opened on top of Today unprompted on launch; fine once, but it covers the capture field.

## Onboarding

`light-80-onboarding.png`, `light-81-onboarding-step2.png` (and dark)

- **Purpose:** fit Klean to the user in a few choices.
- **Primary action:** "Set up" / "Next".
- **Clutter:** step 1: 2 / 0 / 1; step 2: 2 buttons + 9 switches (a form; exempt).
- **Rules broken:**
  - **H1:** "1 of 10" is plain text; ten steps is long for a first run. Group into 3–4 steps.
  - **`visual-hierarchy`:** step 1 is a headline and a paragraph in the top third, then empty space down to the buttons; no visual anchor.

## Notification capture

`light-70-notification-capture.png`

- **Purpose:** add something without opening the app (status notification, "Add something" inline reply).
- **Primary action:** send.
- **Clutter:** within budget.
- **Note:** the in-app `CaptureActivity` sheet is not exported, so it can't be opened from adb; the notification's inline reply is the capture surface reachable on Waydroid. The Add menu (Phase 2.3 #6) will open the sheet from inside the app.

## Dark theme (all `dark-…` shots)

- Surfaces and pastel accents hold up; contrast of primary text is fine.
- Grey supporting text (`onSurfaceVariant` on `surfaceContainer`) is the weakest pairing, and it carries most empty-state copy; at the small size used it reads faint. Fewer, larger empty states fix this better than a colour change.
- Filled buttons (`primary` #cdbdff on dark) are very bright next to tonal cards; three on one screen (Today bottom) pull the eye in three directions.
- Week view event blocks (`tertiaryContainer`) are barely distinguishable from the grid.

## What moves where (to be filled in during Phase 3)

| Action | Today it lives | New home |
|---|---|---|
| Capture text | inline field on Today, Tasks, Workout, Diet, Reminders | Add menu → "Type or speak…" |
| Start a run | Workout, outlined button | Add menu → Log workout → "Run"; Workout saved-workout card |
| Calendar connect banner | Calendar, permanent | Settings › Calendar, plus a one-time `EmptyState` action |
| Per-row ⋮ on reminders | every row | long-press menu and detail screen |
| Workout, Sleep, Diet | More | Track tab |
| Settings | More | gear in Today's top bar, group overflow menus |
| Contacts (bar item) | bottom bar | People tab |
| Tasks, Calendar (bar items) | bottom bar | Plan tab, switched with the Tasks · Calendar segments (last one remembered) |
| More list | bottom bar | removed; every module is one tap away in its group |
| Today: capture field and its mic | top of Today | Add menu → "Type or speak…" (the capture sheet, which keeps "Saved to … · Move to…") |
| Today: greeting | header | the weekday (`displaySmall`) and date |
| Today: eight summary tiles | under the capture field | inside the three group cards (Plan, Track, People), one fact each in the hero, up to 3 rows |
| Today: week strip with previous/next | Today | removed (it repeated the cards); days live in Plan › Calendar (week, month) |
| Today: module cards' buttons ("Going to bed", "Start a workout", card capture fields) | each module card | Add menu → Log sleep / Log workout; the module opens from its group card |
| Today: Settings | More | Today's top bar (Tune icon); since the account menu, the top-bar **avatar** (Google's pattern) opens the account menu, whose "More from Klean" holds Settings and Help. The avatar replaced the Tune button, so Today's first-viewport tappable count is unchanged |
| Tasks: Today and Upcoming tabs | Tasks tab row | one list: Today, Coming up, Still want these? |
| Tasks: Lists, Reminders, Done tabs | Tasks tab row | Tasks overflow → Lists, Reminders, Finished; "All tasks" brings the list back |
| Tasks: "Add a task" field, mic, send, FAB | every Tasks tab | Add menu → Task (capture sheet); full form: Tasks overflow → New task |
| Tasks: review chips (Keep for today, Pick a date, Someday, Done, Let it go) | under each "Still want these?" row | long press on the row |
| Tasks: list ⋮ (rename, delete) | Lists tab | tap the selected list's chip |
| Tasks: "Finished tasks stay here…" + empty sentence | Done tab | one line under the Finished list, or the empty state |
| Reminders: "Remind me…" field | Reminders tab | Add menu → Reminder; overflow → New reminder |
| Reminders: per-row ⋮ | every row | long press (a reminder that went off keeps its one-tap Done) |
| Calendar: view picker button | under the top bar | Calendar overflow → Agenda, Day, 3 days, Week, Month (current one checked) |
| Calendar: Today button, settings gear | top bar | Calendar overflow → Today, Calendar settings |
| Calendar: "New event" extended FAB | bottom right | Add menu → Event (capture sheet); full form: Calendar overflow → New event |
| Calendar: "Your phone's calendars" banner (Not now / Show them) | above the agenda | Calendar overflow → Show phone calendars, the empty agenda's action, Settings › Calendar |
| Calendar: a header per day | agenda | four calm sections: Earlier, Today, This week, Later (rows carry the weekday) |
| Track: opened on Workout | Track tab | Track › **Week** first (a neutral summary of sleep, workouts and food; no links, the segments lead on), until a tracker is picked; then the last one |
| Workout: "Start a workout" filled button | top of Workout | the saved row's "Empty workout" card (and the empty state's one action) |
| Workout: "Start a run" outlined button | top of Workout | the saved row's "Run" card; a run in progress becomes the hero with "Finish run" |
| Workout: "Log a workout…" field and its mic | top of Workout | Add menu → Log workout (capture sheet) |
| Workout: saved-workout rows with a Start button each; tap to edit | Saved workouts list | tonal `SavedCard`s in a row: tap starts, long press edits; "New" card at the end |
| Workout: "New saved workout" text button | under the list | the row's "New" card |
| Workout: settings gear | top bar | Workout overflow → Workout settings (and Settings › Workout) |
| Workout: "Exercises" row | bottom of the list | Workout overflow → Exercises |
| Workout: two grey empty sentences | Saved workouts, Recent | one `EmptyState` ("Your workouts will show up here.", action "Start a workout") |
| Sleep: "Going to bed" / "I'm up" filled button over a grey sentence | top of Sleep | the hero card's one tonal action (Last night or Asleep, with a bar a night); the empty state's action when nothing is logged |
| Sleep: 14 rows with bars, empty nights as "Nothing logged" | Sleep | "Recent nights" list group, "Sunday into Monday · 6 h 50 m"; empty nights collapse; last night is the hero |
| Sleep: "Add a night" text button | bottom of the list | Sleep overflow → Add a night |
| Sleep: settings gear | top bar | Sleep overflow → Sleep settings |
| Diet: pinned capture field, mic and voice status | top of Diet | Add menu → Log food, or "Type or speak…" (capture sheet, with voice) |
| Diet: "Log again" chips | under the field | Add menu → Log food: the capture sheet's "Log again" chips |
| Diet: History icon (calendar) | top bar | Diet overflow → History |
| Diet: calories so far | top bar subtitle | the hero's supporting line ("8:10 · ~1,850 kcal so far") |
| Diet: entries with gap markers' "Add something" | the list | "Today" list group; a gap row taps to add at that time |
| Diet: (new) the rest of the week | — | "Earlier this week" list group (last 5 entries of the six days before) |
| People: search field always on top | Contacts | top-bar Search icon opens the field (Close search clears it) |
| People: "New contact" FAB | bottom right | People's Add menu → New contact (its last line, nearest the finger; plan 2026-11-11 D2); the empty state's action. It left the People overflow |
| People: settings gear | top bar | People overflow → Contacts settings |
| People: People · Birthdays tab row | under the top bar | the "Coming up" hero (next birthday) opens Birthdays; also People overflow → Birthdays; "All people" goes back; `klean://contacts?tab=birthdays` still opens it |
| People: merge card with Merge / Keep apart per pair and Not now | top of the list | one row "These look like the same people" that opens the choices in a dialog |
| People: A–Z letter headers | Contacts | one "Everyone" list group with initials; other accounts keep their own group |
| Contact detail: primary-coloured section labels and plain list items | detail | `SectionHeader`s and list groups; a number calls, an email writes; Edit stays the top bar's one action |
| Settings: 20 plain rows under small primary labels | Settings | three list groups with an icon per row: Modules (each module, then "Turn modules on or off"), Klean (quiet hours, appearance, AI providers, backup, sync, permissions), Help (About, "Run setup again" with a line saying what it does) |
| Settings sub-screens: Material top app bar | each sub-screen | `KleanScreen` with a back arrow and no Add button (`SettingsSubScaffold`) |
| Onboarding: "1 of 10" as plain text | top of each page | a thin progress bar with "1 of 4" under it |
| Onboarding: "Set up AI providers", "Add widget" tonal buttons | AI and widget pages | outlined buttons, so Next stays each page's one primary action (Skip is outlined, same width) |
| Widgets: 20 dp corners on `surfaceContainer`, 13–16 sp ad-hoc sizes | Klean Today, Diet | Soft-card tokens (`WidgetTokens`): `large` 24 dp corners on `surfaceContainerLow`, card padding, a full pill a touch target tall, type sizes from the scale (titleMedium, bodyLarge, bodyMedium, labelLarge) |

## Account menu (Today's avatar)

Google's full-screen account menu pattern, in the Soft-cards look (`core/google/ui/AccountMenu.kt`, route `AccountRoute`).

- **Entry:** the avatar at the top right of Today (48 dp target, "Account and settings"). It replaced the Settings (Tune) button one for one: Today still counts the same tappables (avatar + overflow + group cards + Add) and passes `assertClutterBudget()`.
- **Layout:** ✕ and the email in the top row; the large avatar, "Hi, {first name}", the Manage your Google Account pill; Switch account (collapsed: one row; expanded: every account with a tick on the current one, Add another account, Manage accounts on this device); More from Klean (Settings, Help); Privacy policy · Terms only once their URLs exist.
- **Without Google:** a neutral avatar, one calm line, and one "Connect Google Tasks" row.
- **Clutter:** the expanded account list and the More from Klean group each count once (`CLUTTER_EXEMPT` lists), so the menu stays within budget with two accounts expanded and at font scale 1.5 (`AccountMenuShotsTest`).
- **Moved:** Settings is reached through the menu (one more tap than the Tune button); Disconnect stays in Settings › Tasks, which now says "Connected as name@gmail.com" with the small avatar.

