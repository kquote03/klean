# Reference apps

Phase 1.2 of the UI plan. For each app: navigation, how the home screen summarises modules, how "add" becomes one action, and how empty states look. What Klean should take is at the end of each section, and the synthesis is at the bottom.

**Sources.** Store listings and official help pages, linked per app. Screenshots were viewed from those listings (contact sheets in `~/.hermes/cache/scratch/ui-audit/refs.png` and `refs2.png`, not committed); no assets are copied into the repo. Quoted phrases come from the linked pages. Empty-state and some navigation details aren't shown in store screenshots; those are from general knowledge of the current apps and are worth a quick re-check on a device before being cited elsewhere.

---

## TickTick (tasks, calendar, habits, focus)

Sources: [Play listing](https://play.google.com/store/apps/details?id=com.ticktick.task), [tab bar customization (TickTick blog)](https://blog.ticktick.com/2018/10/31/ticktickcustomtabbar), [TickTick 6.0](https://blog.ticktick.com/2021/05/28/ticktick-6-0).

- **Navigation:** a bottom tab bar of up to 5 features chosen in *Settings › Tab bar* (Tasks, Calendar, Focus, Habits, Search, Settings…). Lists live in a left sidebar inside Tasks.
- **Home summary:** none. The Tasks tab opens on a smart list (Today/Inbox); each feature is its own world.
- **Add:** a single round "+" FAB on Tasks opens a one-line quick-add with natural-language date parsing ("tomorrow 3pm"); the listing's "AI Voice Add" turns a spoken sentence into several tasks.
- **Empty states:** one small illustration + one line ("No tasks"), centred.
- **For Klean:** the user-chosen bar is decision B. The quick-add sheet with parsing is what Klean's capture sheet already does; it belongs behind one "+".

## Things 3 (tasks)

Sources: [App Store listing](https://apps.apple.com/us/app/things-3/id904237743), [Using gestures — Magic Plus](https://culturedcode.com/things/support/articles/2803582/), [features](https://culturedcode.com/things/features/).

- **Navigation:** no tab bar. A single home list (Inbox, Today, Upcoming, Anytime, Someday, Logbook, then Areas and Projects) is the directory; everything drills in from there and comes back with one back gesture.
- **Home summary:** the home list shows counts per list; Today shows calendar events at the top of the to-dos ("This Evening" as a second section).
- **Add:** the **Magic Plus** button: tap to add at the top, or drag it to drop a to-do exactly where it goes, or onto the Inbox target. Quick Entry is a small card, not a full screen.
- **Empty states:** a single faint glyph of the list's icon, centred, no text, no button. The calmest empty state of the eight.
- **Containers:** almost none. Hierarchy comes from type weight, a blue/yellow icon per list and generous spacing. This is the model for direction **B (Airy list)**.
- **For Klean:** type and spacing do the work; one plus, always in the same corner; empty means quiet.

## Todoist (tasks)

Sources: [Play listing](https://play.google.com/store/apps/details?id=com.todoist), [customize the navigation bar](https://todoist.com/help/articles/customize-the-todoist-navigation-bar-L4qpkI0xj), [Dynamic Add button](https://todoist.com/help/todoist/features/use-the-dynamic-add-button-in-todoist-ysybl2M1), [Upcoming view](https://todoist.com/help/todoist/get-started/plan-your-week-with-the-upcoming-view-OKOg1mR8).

- **Navigation:** bottom bar *Today · Upcoming · Search · Browse*. Browse is the directory of everything else. Users can add views to the bar; "any view added to a tab in the navigation bar is automatically removed from the Browse menu" (help page), so nothing appears twice.
- **Home summary:** Today is the home; overdue tasks sit in their own collapsed group at the top.
- **Add:** a red **Dynamic Add** FAB; tap for quick add, or drag to drop a task at a position. Quick add parses dates, labels and projects inline.
- **Empty states:** one illustration + one sentence + nothing else ("Enjoy your day" style).
- **For Klean:** "Browse" is effectively a better-designed More; it's still a second hop. The rule *a destination lives in one place only* is worth copying (clutter rule C7).

## Google Calendar

Sources: [Play listing](https://play.google.com/store/apps/details?id=com.google.android.calendar), [Create an event (Android)](https://support.google.com/calendar/answer/72143?co=GENIE.Platform%3DAndroid&hl=en), [View day, week, month](https://support.google.com/calendar/answer/6110849?co=GENIE.Platform%3DAndroid&hl=en).

- **Navigation:** no bottom bar. A navigation drawer holds the view switcher (Schedule, Day, 3 days, Week, Month) and calendar visibility. The top bar is just the month name, a today button, search and the avatar.
- **Home summary:** Schedule view is the home: a vertical agenda where each event is a full-width tonal block in its calendar colour; birthdays and reminders mix in.
- **Add:** one "+" FAB (Material) that expands into a small menu: Event, Task, Birthday, Out of office… ("Tap Create › Event", help page). Exactly the M3 FAB menu the plan wants.
- **Empty states:** in Schedule, empty days collapse; a month header with an illustration replaces runs of blank days.
- **For Klean:** the FAB menu, and **collapse empty days instead of listing them** (Klean's Sleep screen lists seven "Nothing logged" rows). Agenda rows put the title first and the time small on the left.

## Samsung Health

Sources: [Play listing](https://play.google.com/store/apps/details?id=com.sec.android.app.shealth), [manage trackers (Samsung support)](https://www.samsung.com/ca/support/mobile-devices/galaxy-phone-use-and-manage-trackers-in-samsung-health), [How-To Geek overview](https://www.howtogeek.com/348348/what-can-you-do-with-samsung-health).

- **Navigation:** bottom bar *Home · Together · Fitness · My page*. Trackers aren't tabs.
- **Home summary:** a vertical stack of tracker cards (Steps, Exercise, Sleep, Food, Water, Body composition…), each showing one big number and one small chart. Trackers "can be rearranged, added, and deleted" (Samsung support). Cards are the navigation: tapping one opens that tracker.
- **Add:** per card, a small "+" or "Record" button on the card (e.g. water +250 ml); there is no global add.
- **Empty states:** a card with no data shows its title and a single action ("Record").
- **Look:** pastel per-tracker tonal cards in the listing (Activity mint, Sleep lavender, Vitals blue, Nutrition peach) on a light grey background — closest to direction **A (Soft cards)** and to Klean's own pastel tokens.
- **For Klean:** cards as entry points (the plan's "container transform from Today cards"), one number per card. Avoid the per-card add buttons: that's one action per card, which breaks C1 on a home screen; Klean puts add behind the single "+".

## Bevel (health: recovery, sleep, strain, nutrition)

Sources: [Play listing](https://play.google.com/store/apps/details?id=com.performance.recovery.coach), [App Store listing](https://apps.apple.com/us/app/bevel-ai-health-coach/id6456176249), [bevel.health](https://www.bevel.health/), [widgets help](https://help.bevel.health/en/articles/10431489).

- **Navigation:** bottom bar with Today first and a few areas (Fitness, Sleep, Nutrition, Coach); research notes in `.hermes/plans/ui-overhaul-research.md` record users asking for a *permanent, configurable* tab including Nutrition rather than finding it through Home.
- **Home summary:** "Today" opens with three rings (Strain 41%, Recovery 82%, Sleep 91% in the listing), then a short written insight card, then sections.
- **Add:** a "+" in Today's top bar opens a sheet (log food by photo, workout, journal).
- **Empty states:** rings at 0 with a one-line nudge.
- **For Klean:** the three-ring header is a *score*, which Klean avoids (zero guilt: no scores). Take the composition (one strong header, then a short list), not the metric. The user feedback supports decision A: Track must be a real tab.

## Structured (day planner)

Sources: [App Store listing](https://apps.apple.com/us/app/structured-daily-planner-todo/id1499198946), [Getting started](https://help.structured.app/en/articles/380546), [Inbox](https://help.structured.app/en/articles/338178), [Structured 4.0](https://structured.app/blog/4-0).

- **Navigation:** bottom bar *Inbox · Timeline · AI · Settings*, with a large coral "+" at the bottom right ("the inbox can be found in the tab bar", help page).
- **Home summary:** the Timeline: a week strip at the top, then the day as a vertical line of rounded icon pills (Wake up 7:00, Yoga 7:30, Take a shower 8:00…), each with a duration and a check circle. All-day items sit as small chips above the line.
- **Add:** one "+" opens a single sheet: title, time, duration, colour, icon.
- **Empty states:** the timeline still shows wake-up and bedtime anchors, so a day is never blank.
- **For Klean:** the timeline reads the day in one glance without any cards. The "anchors" idea (wake, sleep) is a calm alternative to an empty state, and Klean already has sleep data to provide them. Used in the Plan screen of direction C.

## Notion Calendar (formerly Cron)

Sources: [App Store listing](https://apps.apple.com/us/app/notion-calendar/id1607562761), [Play listing](https://play.google.com/store/apps/details?id=com.cron.calendar), [Notion Calendar apps](https://www.notion.com/help/notion-calendar-apps).

- **Navigation:** no bottom bar. Month title at the top; a compact view switcher (Schedule, Month, Week, 3 days, 1 day) from a small menu; calendars managed in a sheet.
- **Home summary:** a dense, monochrome week grid where only the events carry colour.
- **Add:** a floating "+" bottom right; tap-and-drag in the grid to create at a time.
- **Empty states:** none — the grid is the content.
- **For Klean:** restraint: neutral chrome so the user's data carries colour. The view switcher hides in one menu instead of a row of tabs.

---

## Synthesis for Klean

| Question | What the references agree on | Klean decision |
|---|---|---|
| Navigation | 3–5 fixed destinations; the home screen doubles as a directory; no "More" (Todoist's Browse is the closest, and it's still a hop) | Decision A: Today · Plan · Track · People; Today's cards open modules |
| Home summary | One card or row per area, one number or line each; no duplicate tiles | One `ModuleCard` per enabled module, in `DashboardSlot` order, no separate tile grid |
| Add | One "+" in the same corner everywhere (Things, Todoist, Google Calendar, Structured); Google Calendar's opens a short menu | One FAB menu: "Type or speak…" on top, then Task, Event, Reminder, Log food, Log workout, Log sleep |
| Empty states | One glyph or one sentence; collapse empty days; never a list of blanks | One `EmptyState` per section with one action; empty history collapses to a single line |
| Look | Samsung Health = pastel tonal cards; Things = typography only; Structured = timeline; Notion Calendar = neutral chrome, colour for data | A = Soft cards, B = Airy list, C = Bento (with a Structured-style timeline in Plan) |
| Avoid | Scores and rings (Bevel), per-card add buttons (Samsung), red overdue groups (Todoist) | Zero guilt: no scores, no streaks, past-due under "Still want these?" |
