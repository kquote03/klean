// Plan › Calendar: previous · period · next, then the agenda in four calm sections (Earlier, Today, This week,
// Later) of list groups. Views, Today and New event live in the overflow; adding is the Add menu.
import { html, nothing, type TemplateResult } from "lit";
import type { App } from "../app";
import { t } from "../copy";
import { compareDates, plusDays, sameDate, type LocalDate } from "../capture/natural-time";
import { weekStart } from "../model";
import { icon } from "../ui/icons";
import { dot, emptyState, groupedList, iconButton, listRow, overflowMenu, screen, type MenuItem } from "../ui/kit";
import { fmtDay, fmtMonth, fmtShortDay, fmtTime, fmtWeekday } from "../ui/parts";
import { dayItems, dayKey, type DayItem } from "./agenda";
import { openCapture } from "./add";

type View = "agenda" | "week" | "month";
const AGENDA_DAYS = 14;

export function calendarScreen(app: App): TemplateResult {
  const m = app.model;
  const view = (app.ui.calView as View) ?? "agenda";
  const anchor = (app.ui.calAnchor as LocalDate) ?? m.today();
  const step = (dir: number) => {
    app.ui.calAnchor = view === "month" ? { year: anchor.year + Math.floor((anchor.month - 1 + dir) / 12), month: ((anchor.month - 1 + dir + 12) % 12) + 1, day: 1 }
      : plusDays(anchor, dir * (view === "week" ? 7 : AGENDA_DAYS));
    app.refresh();
  };
  const label = view === "week" ? `${fmtShortDay(weekStart(anchor))} – ${fmtShortDay(plusDays(weekStart(anchor), 6))}` : fmtMonth(anchor);
  const setView = (v: View) => () => { app.ui.calView = v; app.refresh(); };
  const menu: MenuItem[] = [
    ...(Object.keys(t.calendar.views) as View[]).map((v) => ({ label: t.calendar.views[v], selected: v === view, run: setView(v), testid: `view-${v}` })),
    { label: t.calendar.today, run: () => { app.ui.calAnchor = m.today(); app.refresh(); }, testid: "cal-today" },
    { label: t.calendar.newEvent, run: () => openCapture(app, "event") },
    { label: t.nav.settings, run: () => app.go("/settings") },
  ];
  const period = html`<div class="period">
    ${iconButton("left", t.calendar.previous, () => step(-1), "cal-prev")}
    <h2 class="title-m" data-testid="cal-label">${label}</h2>
    ${iconButton("right", t.calendar.next, () => step(1), "cal-next")}
  </div>`;
  return screen({ title: t.nav.plan, actions: html`${app.status()}${overflowMenu("cal-menu", t.nav.moreOptions, menu)}`, tabs: app.tabs("plan", "calendar"), width: view === "agenda" ? "narrow" : "wide", testid: "calendar" },
    html`${period}${view === "agenda" ? agenda(app, anchor) : view === "week" ? week(app, anchor) : month(app, anchor)}`);
}

const LEAD: Record<DayItem["kind"], () => TemplateResult> = {
  event: () => dot("secondary"), task: () => dot("primary"), reminder: () => icon("bell", { size: 20 }), birthday: () => dot("tertiary"),
};

function agenda(app: App, anchor: LocalDate) {
  const today = app.model.today();
  const items = dayItems(app, anchor, plusDays(anchor, AGENDA_DAYS));
  if (!items.length) return emptyState({ icon: "calendar", text: t.calendar.empty, action: { label: t.calendar.newEvent, run: () => openCapture(app, "event") } });
  const bucket = (d: LocalDate) => compareDates(d, today) < 0 ? "earlier" : sameDate(d, today) ? "today" : compareDates(d, plusDays(today, 6)) <= 0 ? "thisWeek" : "later";
  const b = t.calendar.buckets;
  const sections = (["earlier", "today", "thisWeek", "later"] as const).map((k) => ({
    title: b[k],
    rows: items.filter((i) => bucket(i.day) === k).map((i) => {
      const time = i.allDay ? t.calendar.allDay : fmtTime(i.start);
      return listRow({ lead: LEAD[i.kind](), title: i.title, supporting: t.calendar.kind[i.kind], meta: k === "today" ? time : `${fmtWeekday(i.day)} ${time}`, testid: "cal-event" });
    }),
  }));
  return groupedList(sections, "cal-agenda");
}

function evChip(e: DayItem) {
  return html`<div class="ev ${e.allDay ? "all-day" : ""}" data-testid="cal-event">${e.allDay ? nothing : html`<span class="t">${fmtTime(e.start)}</span>`}${e.title}</div>`;
}

function byDay(items: DayItem[]) {
  const map = new Map<string, DayItem[]>();
  for (const i of items) map.set(dayKey(i.day), [...(map.get(dayKey(i.day)) ?? []), i]);
  return map;
}

function week(app: App, anchor: LocalDate) {
  const start = weekStart(anchor);
  const map = byDay(dayItems(app, start, plusDays(start, 7), ["event", "birthday"]));
  const today = app.model.today();
  return html`<div class="cal-week" data-testid="cal-week">${Array.from({ length: 7 }, (_, i) => {
    const d = plusDays(start, i);
    return html`<div class="day ${sameDate(d, today) ? "is-today" : ""}" aria-label=${fmtDay(d)}>
      <div class="day-head"><span>${fmtWeekday(d)}</span><span class="num">${d.day}</span></div>
      ${(map.get(dayKey(d)) ?? []).map(evChip)}
    </div>`;
  })}</div>`;
}

function month(app: App, anchor: LocalDate) {
  const first = { year: anchor.year, month: anchor.month, day: 1 };
  const gridStart = weekStart(first);
  const map = byDay(dayItems(app, gridStart, plusDays(gridStart, 42), ["event", "birthday"]));
  const today = app.model.today();
  return html`<div class="cal-month" data-testid="cal-month">
    ${Array.from({ length: 7 }, (_, i) => html`<div class="dow" aria-hidden="true">${fmtWeekday(plusDays(gridStart, i))}</div>`)}
    ${Array.from({ length: 42 }, (_, i) => {
      const d = plusDays(gridStart, i);
      const es = map.get(dayKey(d)) ?? [];
      return html`<div class="cell ${d.month !== anchor.month ? "other" : ""} ${sameDate(d, today) ? "is-today" : ""}" aria-label=${fmtDay(d)}>
        <span class="num">${d.day}</span>
        ${es.slice(0, 2).map(evChip)}${es.length > 2 ? html`<span class="muted more">${t.calendar.more(es.length - 2)}</span>` : nothing}
        <span class="pips">${es.slice(0, 4).map(() => html`<span class="pip"></span>`)}</span>
      </div>`;
    })}
  </div>`;
}
