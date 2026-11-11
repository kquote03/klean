// Today: the weekday, then one ModuleCard per group (Approvals first when something waits, then Plan, Track,
// People). Each card is one click target into its group; capture lives in the Add menu, never here.
import { html, nothing, type TemplateResult } from "lit";
import type { App } from "../app";
import { t } from "../copy";
import { parseIsoDate, plusDays } from "../capture/natural-time";
import { num, str } from "../model";
import { navGroups } from "../nav";
import { icon } from "../ui/icons";
import { cardRow, dot, emptyState, iconButton, moduleCard, overflowMenu, screen, closeSheet, openSheet } from "../ui/kit";
import { fmtDayMonth, fmtDuration, fmtTime, fmtWeekdayLong } from "../ui/parts";
import { localDate } from "../model";
import { dayItems, type DayItem } from "./agenda";
import { openCapture } from "./add";

/** Card order on this device (Arrange Today). Device-local, like Android's DashboardSlot arrangement. */
export const SLOTS = ["APPROVALS", "PLAN", "TRACK", "PEOPLE"] as const;
export type Slot = (typeof SLOTS)[number];
const LAYOUT_KEY = "klean.today.cards";

export interface Layout { order: Slot[]; hidden: Slot[] }
export function loadLayout(storage: Pick<Storage, "getItem"> = localStorage): Layout {
  try {
    const l = JSON.parse(storage.getItem(LAYOUT_KEY) ?? "null") as Layout | null;
    if (l) {
      const order = [...l.order.filter((s) => SLOTS.includes(s)), ...SLOTS.filter((s) => !l.order.includes(s))];
      return { order, hidden: l.hidden.filter((s) => SLOTS.includes(s)) };
    }
  } catch { /* fall back to the default */ }
  return { order: [...SLOTS], hidden: [] };
}
export function saveLayout(l: Layout, storage: Pick<Storage, "setItem"> = localStorage) { storage.setItem(LAYOUT_KEY, JSON.stringify(l)); }

export function todayScreen(app: App): TemplateResult {
  const m = app.model;
  const layout = loadLayout();
  const groups = new Set(navGroups(app.isOn).map((g) => g.id));
  const cards = layout.order.filter((s) => !layout.hidden.includes(s) && (s === "APPROVALS" || groups.has(s.toLowerCase() as "plan")))
    .map((s) => CARD[s](app)).filter((c): c is TemplateResult => c !== null);
  const header = html`<header class="today-head">
    <div class="grow"><h1 class="display-s">${fmtWeekdayLong(m.today())}</h1>
      <p class="sub"><span class="body-l muted">${fmtDayMonth(m.today())}</span>${app.status()}</p></div>
    ${iconButton("tune", t.nav.settings, () => app.go("/settings"), "settings")}
    ${overflowMenu("today-menu", t.nav.moreOptions, [{ label: t.today.arrange, run: () => openSheet("arrange-dialog"), testid: "arrange" }])}
  </header>`;
  return html`${screen({ header, width: "wide", testid: "today" }, cards.length
    ? html`<div class="today-cards" data-testid="today-cards">${cards}</div>`
    : emptyState({ icon: "today", text: t.today.empty, action: { label: t.today.addSomething, run: () => openCapture(app) } }))}
    ${arrangeDialog(app, layout)}`;
}

const timeOf = (i: DayItem) => fmtTime(i.start);
const leadFor = (i: DayItem) => i.kind === "event" ? dot("secondary") : i.kind === "reminder" ? icon("bell", { size: 20 }) : i.kind === "birthday" ? icon("cake", { size: 20 }) : html`<span class="ring-mark"></span>`;

const CARD: Record<Slot, (app: App) => TemplateResult | null> = {
  APPROVALS(app) {
    const pending = app.model.approvals();
    if (!pending.length) return null;
    const a = pending[0];
    const d = app.model.delegationFor(a);
    const task = d ? app.model.store.get("task", str(d.task_id) ?? "") : undefined;
    const action = (str(a.action) ?? "").replace(/_/g, " ");
    return moduleCard({ tone: "neutral", label: t.today.waiting, big: action.charAt(0).toUpperCase() + action.slice(1),
      sub: pending.length > 1 ? t.approvals.waiting(pending.length) : str(task?.title) ?? undefined,
      open: () => app.go("/approvals"), aria: `${t.today.waiting}: ${t.approvals.waiting(pending.length)}`, testid: "card-approvals" });
  },

  PLAN(app) {
    const m = app.model;
    const today = m.today();
    const now = app.now();
    const items = dayItems(app, today, plusDays(today, 1), ["event", "reminder", "task"]);
    const timed = items.filter((i) => !i.allDay && (i.kind === "event" ? i.end > now : i.start >= now));
    const next = timed[0];
    const rest = [...timed.slice(1), ...items.filter((i) => i.allDay)].slice(0, 3);
    const still = m.isOn("tasks") ? m.stillWant() : [];
    const rows = rest.map((i) => cardRow(leadFor(i), i.title, i.allDay ? undefined : timeOf(i)));
    if (still.length) {
      const first = str(still[0].due_date);
      rows.push(cardRow(nothing, t.today.stillWant(still.length, first ? fmtWeekdayLong(parseIsoDate(first)) : ""), undefined, true));
    }
    const dueCount = items.filter((i) => i.kind === "task").length;
    const hero = next
      ? { label: t.today.next, big: next.title, sub: next.kind === "event" ? t.today.timeRange(fmtTime(next.start), fmtTime(next.end)) : t.today.at(timeOf(next)) }
      : { label: t.today.today, big: dueCount ? t.today.tasksToday(dueCount) : t.today.nothingPlanned, sub: undefined };
    return moduleCard({ tone: "plan", ...hero, rows, open: () => app.go("/plan"), aria: `${t.nav.plan}: ${hero.label}, ${hero.big}`, testid: "card-plan" });
  },

  TRACK(app) {
    const m = app.model;
    const rows: TemplateResult[] = [];
    let hero: { label: string; big: string; sub?: string };
    const open = m.isOn("sleep") ? m.asleep() : undefined;
    const last = m.isOn("sleep") ? m.lastNight() : undefined;
    const food = m.isOn("diet") ? m.foodToday() : [];
    const session = m.isOn("workout") ? m.sessions()[0] : undefined;
    let heroIs: "sleep" | "food" | "workout" = "sleep";
    if (open) hero = { label: t.today.sleep, big: t.sleep.asleepSince(fmtTime(num(open.bed_at)!)) };
    else if (last) hero = { label: t.today.lastNight, big: fmtDuration(num(last.wake_at)! - num(last.bed_at)!), sub: t.today.timeRange(fmtTime(num(last.bed_at)!), fmtTime(num(last.wake_at)!)) };
    else if (m.isOn("diet")) { heroIs = "food"; hero = { label: t.track.foodToday, big: food.length ? t.track.entries(food.length) : t.diet.nothingToday }; }
    else if (m.isOn("workout")) { heroIs = "workout"; hero = { label: t.track.thisWeek, big: t.workout.thisWeek(m.sessionsThisWeek()) }; }
    else hero = { label: t.today.lastNight, big: t.sleep.noLastNight };
    if (food.length && heroIs !== "food") rows.push(cardRow(icon("note", { size: 20 }), str(food[0].raw_text) ?? "", fmtTime(num(food[0].eaten_at)!)));
    if (session && heroIs !== "workout") rows.push(cardRow(icon("workout", { size: 20 }), str(session.notes) ?? t.workout.noteless, t.today.lastOn(fmtWeekdayLong(localDate(num(session.started_at)!)))));
    return moduleCard({ tone: "track", ...hero, rows, open: () => app.go("/track"), aria: `${t.nav.track}: ${hero.label}, ${hero.big}`, testid: "card-track" });
  },

  PEOPLE(app) {
    const m = app.model;
    if (!m.isOn("birthdays")) return null;
    const b = m.birthdays(30)[0];
    if (!b) return null;
    const first = (str(b.contact.display_name) ?? "").split(" ")[0];
    const when = b.inDays === 0 ? t.contacts.todayLabel : b.inDays === 1 ? t.contacts.tomorrow : `${fmtWeekdayLong(b.date)}, ${t.contacts.inDays(b.inDays).toLowerCase()}`;
    return moduleCard({ tone: "people", label: t.today.comingUp, big: t.calendar.birthday(first), sub: b.turns ? t.contacts.turnsOn(b.turns, when.toLowerCase()) : when,
      open: () => app.go("/people"), aria: `${t.nav.people}: ${t.calendar.birthday(first)}, ${when}`, testid: "card-people" });
  },
};

// ---- Arrange Today (device-local) ----

function arrangeDialog(app: App, layout: Layout) {
  const move = (i: number, d: number) => {
    const order = [...layout.order];
    const j = i + d;
    if (j < 0 || j >= order.length) return;
    [order[i], order[j]] = [order[j], order[i]];
    saveLayout({ ...layout, order });
    app.refresh();
  };
  const toggle = (s: Slot, on: boolean) => {
    saveLayout({ ...layout, hidden: on ? layout.hidden.filter((x) => x !== s) : [...layout.hidden, s] });
    app.refresh();
  };
  return html`<dialog class="sheet" id="arrange-dialog" aria-labelledby="arrange-title">
    <div class="stack">
      <h2 id="arrange-title">${t.today.arrange}</h2>
      <p class="lead-text">${t.today.arrangeLead}</p>
      <ul class="list-group">${layout.order.map((s, i) => html`<li class="lrow" data-slot=${s}>
        <span class="grow" style="padding-left:var(--s8)">${t.today.slots[s]}</span>
        <button type="button" class="icon-btn" aria-label="${t.today.up}: ${t.today.slots[s]}" ?disabled=${i === 0} @click=${() => move(i, -1)}>${icon("up")}</button>
        <button type="button" class="icon-btn" aria-label="${t.today.down}: ${t.today.slots[s]}" ?disabled=${i === layout.order.length - 1} @click=${() => move(i, 1)}>${icon("down")}</button>
        <m3e-switch aria-label="${t.today.show}: ${t.today.slots[s]}" ?checked=${!layout.hidden.includes(s)} @change=${(e: Event) => toggle(s, (e.target as HTMLInputElement).checked)}></m3e-switch>
      </li>`)}</ul>
      <div class="actions end"><button type="button" class="btn filled" @click=${() => closeSheet("arrange-dialog")}>${t.today.done}</button></div>
    </div>
  </dialog>`;
}

