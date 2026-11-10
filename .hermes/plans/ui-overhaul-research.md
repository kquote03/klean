# UI overhaul — research notes (2026-11-10)

Input for the UI overhaul plan, to be written once lane NEXT finishes.

## The user's complaint

- The app "feels like a panel of buttons and input boxes … slapped together".
- The bottom bar shows Today, Tasks, Calendar, Contacts and More. Workout, Sleep and Diet sit behind the "More" overflow, which adds friction.
- Screenshots: the Workout screen is a stack of a pill button, an outlined text field, section labels and an empty divider list. More is a bare list.

## Skills installed

- **ui-ux-pro-max** (`nextlevelbuilder/ui-ux-pro-max-skill`, ~148k★), installed at `~/.hermes/skills/creative/ui-ux-pro-max`.
  - Searchable data: styles, palettes, typography, UX guidelines, and a stack file for Jetpack Compose.
  - Run it with `nix shell nixpkgs#python3 -c python3 ~/.hermes/skills/creative/ui-ux-pro-max/scripts/search.py "<q>" --domain ux|style|… [--stack jetpack-compose]`.
  - `--design-system` output is landing-page-biased. Use the `ux` and `stack` domains instead.
- **frontend-design** (Anthropic official, `anthropics/skills`), installed at `~/.hermes/skills/creative/frontend-design`. It covers aesthetic direction and avoiding templated defaults.
- **Considered, not installed:**
  - `oil-oil/oil-ui` (~3k★): directions side by side, refinement against screenshots. Its workflow is worth borrowing.
  - `szilu/ux-designer-skill`
  - `mae616/design-skills`
  - `tommyjepsen/awesome-ux-skills`
  - `OJO-Design-Skills`

## How other apps handle many modules

- **Material 3 (Expressive, May 2025)**
  - The navigation bar holds 3–5 destinations of equal importance, and its destinations don't change.
  - The flexible (shorter) navigation bar replaces the baseline one.
  - The navigation drawer is deprecated in favour of the expanded nav rail on larger screens.
  - The **FAB menu** (2–6 related actions) replaces speed dials.
  - Floating toolbars are for page actions; never show one at the same time as a nav bar that contains navigation.
- **Apple HIG / general guidance** (neeleshjain.com summary)
  - Avoid a generic "More" tab: hidden content is harder to notice and reach.
  - With many services, use:
    - a **home that works as a directory** (labelled groups plus recents),
    - **search across services**,
    - **one consistent way back**.
  - Keep tabs for what nearly everyone does.
  - Tapping the current tab scrolls to the top, and each tab keeps its place.
- **TickTick** (tasks + calendar + habits + focus): Settings › Tab bar lets the user pick and reorder which features are tabs, up to 5. Everything else is in a sidebar.
- **Taskito:** "More → Personalize". Timeline is fixed first, More fixed last, and the 3 middle tabs are user-chosen.
- **COUPPLY:** long-press the nav bar to reorder, show or hide tabs and choose the launch tab, in place.
- **FamCal:** an "Arrange modules" screen orders the tab bar and the drawer. A FAB expands to Memo/Task/Item/Event.
- **MindKit** (all-in-one life app): 3 tabs that group modules by *how often they're used*:
  - Actions: tasks, habits.
  - Trackers: mood, diet.
  - Minds: notes, wishes.

  Every module is drawn as a card.
- **Mint redesign:** the Overview **cards were the primary navigation**; users rarely used the drawer or tabs. Personalised card order, plus 4 tabs.
- **Samsung Health:** the home screen is a stack of the user's chosen tracker cards ("manage items"). Tapping a card opens that module.
- **Bevel** (health) user feedback: they want a permanent, configurable tab bar including Nutrition. Collapsing or hidden bars and "find it through Home" frustrate them.

## Directions to evaluate in the plan

1. **Grouped tabs (MindKit-style).** For example:
   - Today (hub)
   - Plan (Tasks + Calendar, with segmented views)
   - Track (Workout, Sleep, Diet, via a top segmented control or a swipeable pager)
   - People (Contacts + Birthdays)

   Four tabs, no "More". Settings moves behind a profile or avatar button on Today.
2. **Configurable tab bar (TickTick/Taskito).** Today is fixed, with up to 4 user-picked modules; the rest are reached from Today's cards and a module launcher. This only reduces friction if the launcher is good.
3. **Today as the directory (Mint/Samsung).** Every enabled module is a rich card on Today and opens on tap, plus a FAB menu for "Add task / event / log food / workout / sleep". The nav bar holds 3–4 items.

Likely a blend: grouped tabs (1) + Today cards as entry points (3) + a FAB menu for capture + a long-press nav bar to rearrange it (2-lite).

## Faster UI iteration (answer to the user)

- **Today's loop:** a Gradle compile (1–3 min incremental, ~6 min for the full gate) → install on Waydroid → navigate → screenshot. It's serialised through the shared Gradle and Waydroid locks, which the background agent also holds.
- **Better loop for an overhaul:**
  1. **An HTML/CSS clickable prototype first.** It renders instantly with no compile, can show 2–3 directions side by side, and the user can open it on the phone. Pick a direction, then port it.
  2. **Roborazzi screenshot tests** (JVM, Robolectric native graphics) for Compose. They render screens to PNG in seconds without a device, the agent and user look at the PNGs, and they double as visual regression tests.
  3. Waydroid only for final checks and interaction.
