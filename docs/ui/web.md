# Klean web: the UI overhaul (plan Phase 4)

The web client (`web/`) follows the Android redesign: navigation A (grouped tabs) and the Soft-cards look from
`prototypes/ui-2026-11/a-soft-cards/`, with the values in `docs/ui/design-system.md`.

## Navigation

- Four groups, the same as Android's `NavGroup`: **Today · Plan (Tasks · Calendar) · Track (Week · Workout ·
  Sleep · Diet) · People (Contacts · Birthdays)**. `web/src/nav.ts` holds the pure model (`navGroups`,
  `resolve`, `addMenuEntries`), tested in `nav.test.ts`.
  - Laptop (≥ 840 px): an M3E navigation rail with the Add button at its top.
  - Phone: an M3E bottom bar; the Add button floats above it on the right.
  - A group whose modules are all off disappears. A group with one module shows no segmented control. Track's
    Week only appears with two or more trackers on.
  - No "More". Settings opens from Today's top bar (Tune icon) and from each group's overflow.
- Paths: `/`, `/plan/{tasks,calendar}`, `/track/{week,workout,sleep,diet}`, `/people/{contacts,birthdays}`,
  `/settings`, `/approvals`. `/plan`, `/track`, `/people` open on the module last used in that group
  (remembered per browser in `localStorage` `klean.nav.last`). The old paths (`/tasks`, `/calendar`,
  `/contacts`, `/workout`, `/sleep`, `/diet`, `/more`) redirect.

## The Add menu

One Add button on every screen opens the M3 Expressive FAB menu as a full-screen popover with a surface scrim at
82 %: "Type something…" first, then Log sleep, Log workout, Log food, Reminder, Event and Task. Modules that are
off are hidden. Each line opens the capture sheet aimed at its module (`capture(model, text, target)`); "Type
something…" lets the rules decide. After saving, a snackbar offers Undo and Move to…. Log sleep is one button
(Going to bed / I'm up) because a typed night isn't needed on the web.

## Components (`web/src/ui/kit.ts`)

| Android | Web (Lit template) |
|---|---|
| `KleanScreen` | `screen({ title \| header, actions, tabs, width })` |
| `ModuleCard` + `CardTone` | `moduleCard({ tone, label, big, sub, rows, open })` and `cardRow()` |
| `SectionHeader` | `sectionHeader()` |
| `ListGroup` / `ListRow` / `listGroup` | `listGroup()`, `groupedList()`, `listRow()`, `checkButton()` |
| `EmptyState` | `emptyState({ icon, text, action })` |
| `SegmentedTabs` | `segmentedTabs()` (via `app.tabs(group, dest)`) |
| `WeekBars` | `weekBars()` |
| `SavedCard` | `.saved` (Workout's start / in-progress card) |
| top-bar `MenuAction`s | `overflowMenu()` (a native popover) |

Styles live in `web/src/styles.css`: the spacing scale (`--s4` … `--s48`), shapes (8, 16, 24, pill), the eight
type roles (`.display-s` … `.label-m`) and the token colours. No other sizes.

## Screens: what moved

| What | Was | Now |
|---|---|---|
| Capture field | top of Today | Add menu › Type something… |
| Greeting | Today's header | the weekday (`display-s`) and the date, with the sync status |
| Seven Today cards with buttons (Going to bed, Start a workout, Review…) | Today | one ModuleCard per group (Approvals when something waits, Plan, Track, People); each card is one click into its group; logging is in the Add menu |
| "Your day" side panel | Today (laptop) | removed; the Plan card's hero is the next timed item, its rows the rest of today |
| Arrange Today | header button | Today's overflow; the cards to arrange are now Approvals, Plan, Track, People |
| Tasks tabs Today, Upcoming | Tasks | one grouped list: Today, Coming up, Still want these?, No date |
| Tasks tabs Lists, Reminders, Done | Tasks | Plan overflow › Lists, Reminders, Finished (All tasks brings the list back) |
| "Add a task" / "Add a reminder" fields | Tasks | Add menu › Task / Reminder; overflow › New task |
| Keep for today / Someday buttons | each "Still want these?" row | the task's sheet (tap the row) |
| Edit pencil per task | each row | tap the row |
| Reminder Done / Dismiss buttons | each row | the reminder's sheet (tap the row) |
| Calendar view chips, Today button | under the title | Calendar overflow (Agenda, Week, Month, Today, New event) |
| Calendar "Add an event" field | Calendar | Add menu › Event; overflow › New event |
| Calendar agenda, a card per day | Calendar | four sections: Earlier, Today, This week, Later (14 days from the anchor, like Android) |
| Workout set form, Start button | Workout | Workout's start card opens the session sheet (sets, Log a set, +1 set, Finish) |
| Sleep "Going to bed" button | Sleep | Add menu › Log sleep |
| Sleep schedule form | Sleep | Settings › Quiet hours (the same values) |
| Diet "Log food" field | Diet | Add menu › Log food |
| Contacts search field | Contacts | the top bar's search icon shows it |
| "Add a contact" form | Contacts | People overflow › New contact (a sheet) |
| Approval draft editor | a card per request | a list; each request opens in a sheet |
| Settings cards with buttons and a time form | Settings | list groups: Modules, Devices (Pair a phone opens the QR sheet), This browser and the server (Quiet hours, Notifications, Google, Backup) |
| More | phone bar | removed |

## Clutter budget

`web/e2e/clutter.ts` has `clutterCount(page)` and `expectClutterBudget(page, { maxInteractive: 7, maxInputs: 0 })`:
visible interactive elements in the first viewport, outside `[data-clutter-exempt]`. The rail and the bar are
exempt; a subtree marked `data-clutter-exempt="list"` (every `listGroup`/`groupedList`) is a list whose rows open
detail and counts once. It also checks at most one primary action (the Add button, a filled or tonal button).
`e2e/review.spec.ts` calls it for every screen at 390×844 and 1440×900 in light and dark, and has five tests of
the helper itself (8 buttons fail, 7 pass, an exempt list counts once, a text field fails, below the fold
doesn't count).

Counts (interactive / inputs / primary, the same at both sizes and in both themes):

| Screen | Count |
|---|---|
| Today | 7 / 0 / 1 (Settings, ⋮, Approvals, Plan, Track, People, Add) |
| Plan › Tasks (all, Lists, Reminders, Finished) | 5 / 0 / 1 |
| Plan › Calendar (agenda, week, month) | 6 / 0 / 1 |
| Track › Week | 5 / 0 / 1 |
| Track › Workout | 7 / 0 / 1 |
| Track › Sleep, Diet | 5 / 0 / 1 |
| People › Contacts | 6 / 0 / 1 |
| People › Birthdays | 5 / 0 / 1 |
| Approvals | 2 / 0 / 1 |
| Settings | 4 / 0 / 1 |

## Tests

- `pnpm -C web test`: unit tests, including `nav.test.ts` (groups, routing, Add menu entries, last module).
- `pnpm -C web visual`: Today, Plan › Tasks and Plan › Calendar, light and dark, laptop and phone
  (`e2e/__screenshots__/visual.spec.ts/`).
- `pnpm -C web review`: every screen and overlay into `~/.hermes/cache/scratch/ui-web/<REVIEW_LABEL or latest>/`
  with `report.json` (clutter counts, sideways scroll, clipped text, sizes off the type scale, fonts).
- `pnpm -C web e2e`: against the `klean-server:e2e` image; signs in, pairs through Settings, adds a task
  through the Add menu, sees a phone's task in Plan › Tasks and approves a request from Today's card.

## Not ported (yet)

- Motion: shared-axis tabs and the Today-card container transform (Android doesn't have them yet either). The
  Add menu items fade and rise in 250 ms with a 20 ms stagger; reduced motion turns it off.
- Saved workouts (templates): the web model has no templates, so Workout shows one start card instead of the row
  of saved cards.
- Voice in the capture sheet: the line reads "Type something…", not "Type or speak…".
