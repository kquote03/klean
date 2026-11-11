# Klean design system

Phase 1.6 of the UI plan. This spec is written for the **recommended combination**: navigation A (grouped tabs Today, Plan, Track, People) with the **Soft cards** look, as prototyped in `prototypes/ui-2026-11/a-soft-cards/`. The last section lists what changes if the user picks the Airy list or Bento look, or navigation B or C.

Every value here maps to a Kotlin token in Phase 2 (`Space`, `KleanShapes`, `MaterialTheme.typography`, the existing colour tokens). A screen may only use these values. The prototypes use the same values as CSS variables in `prototypes/ui-2026-11/shared/base.css`.

## Principles

1. **Content first, controls second.** A screen opens with what the user has (today's plan, last night, recent workouts), not with an input.
2. **One way to add.** The single Add button opens the Add menu from every tab. Module screens never show a capture field.
3. **One fact per card.** A card's tonal hero carries one thing in large type ("Team sync", "7h 10m", "Sara's birthday"); details sit underneath in body text.
4. **Quiet when empty.** One sentence and at most one action per empty section. Empty days collapse; they are never listed.
5. **Zero guilt** (unchanged from AGENTS.md): no streaks, scores, red badges or counts of what wasn't done. Past-due tasks are "Still want these?".

## Navigation (decision A)

- **Bar:** Material 3 short navigation bar, 4 destinations with icon and label: **Today, Plan, Track, People**. Icons (rounded): `Today`, `EventNote`, `Insights`, `People`. Active item: `primaryContainer` pill indicator.
- **Groups:** Plan = Tasks, Calendar (Reminders stay a tab inside Tasks). Track = Workout, Sleep, Diet. People = Contacts (Birthdays stay a tab inside Contacts).
- **Inside a group:** `SegmentedTabs` in a floating toolbar at the bottom (a raised pill above the navigation bar, user feedback 2026-11-11) switches modules, with the Add button at the end of the same row (the start when Left-handed is on); the last choice per group is remembered. A group with one enabled module shows no toolbar, only the Add button; a group with none disappears from the bar. A sideways swipe moves between pages: Today, then each group's tabs in bar order.
- **Track opens on "Week"**, a neutral overview of all three trackers, until the user picks a module; after that, the last choice. This keeps Diet one module among equals rather than a default.
- **Settings:** an icon button in Today's top bar (`Tune` icon, label "Settings"). Group screens with real secondary actions (Plan: lists, sort, show finished; People: new contact, import) put them in a top-bar overflow menu. Track has no overflow; per-item actions live in long-press menus and detail screens.
- **Today's cards are entry points:** each card opens its group (Plan, Track, People) with a container transform.
- **No "More".** Ever.
- **The Add menu's own line sits nearest the finger** (user feedback 2026-11-11): the current screen's module is always the last line, next to the Add button (Event on Calendar, Log food on Diet). In People that last line is **New contact**, which shows only there (`addMenuEntries`, `AddMenuEntriesTest`).
- **Adding never leaves the app's activity.** The shell shows the capture sheet in place (`ShellSheets`, `LocalCaptureLauncher` for module screens); `CaptureActivity` is only for outside entry points (QS tile, widgets, notification, shortcuts, `klean://capture`, the watch).

## Spacing (`Space`)

| Token | dp | Use |
|---|---|---|
| `xxs` | 4 | icon-to-label gaps, tight stacks inside a row |
| `xs` | 8 | gap between a section header and its content; chip gaps |
| `sm` | 12 | `cardGap`: between cards; between a row's leading element and its text |
| `md` | 16 | `screenEdge`, `cardPadding`; row horizontal padding |
| `lg` | 24 | `sectionGap`: between sections; under Today's header |
| `xl` | 32 | top of an `EmptyState` |
| `xxl` | 48 | minimum touch target |

## Shapes (`KleanShapes`)

| Shape | dp | Components |
|---|---|---|
| small | 8 | chart bars, focus outlines, snackbars |
| medium | 16 | FAB (`KleanAddButton`), text fields in forms, timeline blocks |
| large | 24 | `ModuleCard`, `ListGroup` surfaces, saved-workout cards, bottom sheets' inner cards |
| full | pill | segmented control, navigation indicator, Add menu items, buttons, avatars |

Bottom sheets use the Material default top corners (28).

## Type scale (Cantarell regular 400 and bold 700)

| Role | Size / line | Weight | Use |
|---|---|---|---|
| `displaySmall` | 36 / 44 | 400 | Today's weekday only |
| `headlineSmall` | 24 / 32 | 400 | a card hero's one big fact |
| `titleLarge` | 22 / 28 | 400 | module screen titles (Plan, Track, People) |
| `titleMedium` | 16 / 24 | 700 | section headers, card titles |
| `bodyLarge` | 16 / 24 | 400 | list items, card rows |
| `bodyMedium` | 14 / 20 | 400 | supporting text, row meta (times, days) |
| `labelLarge` | 14 / 20 | 700 | buttons, segments, the hero's small label ("Next", "Last night") |
| `labelMedium` | 12 / 16 | 700 | navigation labels, chart axis letters |

No all-caps. No other sizes.

## Colour and surfaces

Only the existing tokens (`core/design/tokens/klean-color-tokens.json`, light and dark, AA tested).

- **Background:** `surface`.
- **Cards and list groups:** `surfaceContainerLow`.
- **A card's hero band:** the module's tonal container, with its `on…Container` text.
- **Module accents:**

| Module | Hero / selected segment | Small marks (dots, chart bars) |
|---|---|---|
| Tasks, Reminders (Plan) | `primaryContainer` | `primary` |
| Calendar (Plan) | Plan card uses `primaryContainer`; event blocks use `secondaryContainer` | `secondary` |
| Workout, Sleep, Diet (Track) | `secondaryContainer` | `secondary` (current day), white at 55% on the container (other days) |
| Contacts, Birthdays (People) | `tertiaryContainer` | `tertiary` |

- **Add button:** `primaryContainer` with `onPrimaryContainer`; the open menu's close button is `primary`.
- **Error colour:** real errors only.

## Motion

By how the two screens relate (`Motion.kt`, `MotionKindTest`; user feedback 2026-11-11):

| From → to | Motion |
|---|---|
| Top level ↔ top level (Today, a group's module tabs, Track › Week, switching bar groups) | **page slide**: full width, 300 ms, emphasized easing; the direction follows the page order (Today, Plan, Track, People and their tabs), for Back too, so Back to Today slides Today in from the left |
| Parent → child, and Back | **shared axis X**, 250 ms, emphasized easing |
| Into a full-screen page (account menu, voice screen) | **slide up**; Back slides it down over the screen it came from |
| Any, with reduced motion (system animator scale 0 or "remove animations") | cross-fade at 100 ms, no movement |

- Back (button or predictive gesture) plays the NavHost's pop transition, so it never shows the system's shrinking window inside the app; only Back on Today leaves the app. `BackNavigationTest` (Waydroid) walks every screen and overlay.
- Today card to group screen: **container transform**, 250 ms.
- Add button to Add menu: the Material 3 FAB menu expand, items staggered 20 ms.

## Components (Phase 2 targets)

All in `core/design/src/main/kotlin/app/klean/core/design/component/`.

| Component | What it is | Notes from the prototype |
|---|---|---|
| `KleanScreen` | the screen scaffold: top bar (`titleLarge`, optional overflow), content with `screenEdge` margins, FAB slot, snackbar host | Today passes a custom header (weekday in `displaySmall`, date in `bodyLarge` `onSurfaceVariant`, Settings button) |
| `ModuleCard` | `large` shape, `surfaceContainerLow`; a tonal **hero** (label, one big fact, one supporting line) and an optional **body** of up to 4 rows | the whole card is one click target; nothing inside it is separately tappable; no buttons inside on Today |
| `SectionHeader` | `titleMedium`, `sectionGap` above, `xs` below, optional trailing text button | at most 3 visible per viewport |
| `ListGroup` + `ListRow` | rows on one `surfaceContainerLow` `large` surface, separated by a 1 dp `surface` gap, not dividers; row 56–72 dp: leading (check, avatar, dot, icon), title + optional supporting line, trailing meta | grouped lists (Today, This week, Still want these?) are one lazy list with sticky headers |
| `EmptyState` | tonal icon, one sentence, optional one text or tonal button | replaces every stacked grey hint |
| `KleanAddButton` + `AddMenu` | M3 Expressive `FloatingActionButtonMenu`: "Type or speak…" (opens the capture sheet), Log sleep, Log workout, Log food, Reminder, Event, Task; disabled modules hidden | menu items are pills on `primaryContainer`; the scrim is `surface` at 82% so the screen stays legible |
| `SegmentedTabs` | `SingleChoiceSegmentedButtonRow`, outline border, selected = group container colour, 48 dp tall | remembers the last choice per group |
| `WeekBars` | 7 bars, one per day, current day in the accent; days with no data show a short stub, never a gap or a mark | used by Sleep, Workout and Track's Week |
| `SavedCard` | `large` tonal card, title, one line, a "Start" affordance (accent disc with a play icon + label); horizontal row ends with a "New" card | the whole card starts the workout; no separate filled button |
| `FactCard` | small `large` card on `surfaceContainerLow`: label, one fact, a supporting line; not tappable | Track › Week's other trackers, two to a row |
| `WidgetTokens` | the same scales for Glance widgets (no Compose components there): 24 dp corners, `surfaceContainerLow`, card padding, a full pill a touch target tall, sizes from the type scale | `WidgetTokensTest` keeps both widgets on them |

Also: `ModuleCard(hero = …)` puts a week of `WeekBars` inside the tonal hero (Workout, Sleep, Track › Week); `SavedCard(onLongClick)` edits; `KleanScreen(onBack)` gives detail and settings screens a back arrow; `lineLimit(text, max)` gives a single word one line and "…" (never a mid-word break at font 1.5); segment labels shrink to fit before they end in "…".

## Clutter budget (hard rule)

Checked in prototypes (`shared/chrome.js` writes the count; `index.html` shows it), in `ClutterBudgetTest` (§2.3b) and in each Waydroid check.

1. **One primary action per screen.** At most one filled or tonal-filled button, or the FAB, in the first viewport. Everything else is a text button, an icon button or a menu item.
2. **At most 7 interactive elements in the first viewport** (412 × 915), not counting the navigation bar. A list whose rows open detail counts once, including a grouped list with sticky headers (it is one lazy list).
3. **At most 2 visible text inputs on any non-form screen,** and normally 0. Forms (edit task, edit event) are exempt but group fields, with optional ones under "More options".
4. **Progressive disclosure.** Edit, delete, share, settings, sort and filter live in a top-bar overflow menu, a long-press menu or the detail screen.
5. **One message per empty state.**
6. **Breathing room.** `sectionGap` between sections, at most 3 section headers in the first viewport, no dividers where spacing already separates.
7. **No duplicate entry points on one screen.** (The Track Week page shows summaries, not rows linking to Workout/Sleep/Diet, because the segments already do that.)
8. **At most one chip row** per screen, single line, at most 5 chips.
9. **Today quick actions (user feedback 2026-11-11).** Today has no Add button; its one-tap grid (`QuickActionGrid`) replaces it. The grid is one region tagged `CLUTTER_EXEMPT` and counts once: at most 8 tiles in 2 rows of 4 (Speak and Type always, then up to 6 modules in Arrange Today's order; modules past the cap stay reachable through Type and their tabs). The rest of Today's first viewport keeps the ≤ 7 budget. Each whiteboard section (Needs attention, Coming up, So far today) is a list region that counts once. This changes a Decided rule; the user's message of 2026-11-11 is the approval.

Counts of the recommended prototype (controls / inputs / primary): Today 5/0/1, Plan 5/0/1, Track 5/0/1, People 5/0/1, Workout 7/0/1, Sleep 6/0/1, Diet 6/0/1, Add menu 2/0/1. Today's whiteboard (2026-11-11): grid 1 + avatar + ⋮ + up to 3 sections = 6/0/1.

## Copy

Calm, sentence case, no `!`, no guilt words, never ADHD or autism (enforced by `ForbiddenCopyTest`). Times as 24-hour `15:00` in the device locale's style; durations as `7h 10m`. Hero labels are one or two words ("Next", "Last night", "Coming up", "This week").

## If the user picks differently

**Look B, Airy list** (`b-airy-list/`)
- No `ModuleCard` on Today: sections with a `titleMedium`-sized label in `onSurfaceVariant` and an 8 dp module-colour mark; rows sit directly on `surface`.
- Today opens with the day as one sentence in a 30/38 headline (new `headlineMedium` role) instead of the weekday.
- Big facts become `displayMedium`-sized figures (48/56) on Track pages.
- Navigation bar on `surface` with a soft top shadow; active item in `primary` without a pill. FAB is a `primary` circle.
- `ListGroup` loses its surface; spacing alone separates rows (min height 52). Section gap grows to 32.

**Look C, Bento** (`c-bento/`)
- Today becomes a 2-column grid of `Tile`s (new component, `large` shape) sized by importance: next event spans both columns, Tasks spans two rows. Each tile is one click target; budget means at most 5 tiles in the first viewport.
- Plan's Calendar segment is a day timeline with a week strip and wake/bedtime anchors from Sleep (new `DayTimeline` component).
- Track pages are tiles with one number each; FAB is a `primary` rounded square (shape `large`, 64 dp).
- People's list becomes a 3-column avatar grid.

**Navigation B (customizable bar)**
- Replace `NavGroup` with a user-chosen list of 3 modules + Today (Settings › Navigation), and add a module sheet opened from Today's top bar. No segmented controls.

**Navigation C (Today as the directory)**
- Bar becomes Today, Add, Search. Module screens get a back arrow in `KleanScreen` instead of tabs; Today's cards become the only way in, so every enabled module must have a card.
