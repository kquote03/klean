import { html, nothing, type TemplateResult } from "lit";
import type { App } from "../app";
import { t } from "../copy";
import { compareDates, parseNaturalTime, plusDays, sameDate, type LocalDate } from "../capture/natural-time";
import { localNow } from "../capture/capture";
import { atLocal, localDate, str, weekStart, type Occurrence } from "../model";
import { field, fmtDay, fmtMonth, fmtShortDay, fmtTime, fmtWeekday, formValues, resetForm, tabs } from "../ui/parts";
import { icon } from "../ui/icons";

type View = "agenda" | "week" | "month";

interface DayEntry { allDay: boolean; start: number; title: string; kind: "event" | "birthday" }

export function calendarScreen(app: App): TemplateResult {
  const m = app.model;
  const view = (app.ui.calView as View) ?? "week";
  const anchor = (app.ui.calAnchor as LocalDate) ?? m.today();
  const step = (dir: number) => {
    app.ui.calAnchor = view === "month" ? { year: anchor.year + Math.floor((anchor.month - 1 + dir) / 12), month: ((anchor.month - 1 + dir + 12) % 12) + 1, day: 1 }
      : plusDays(anchor, dir * (view === "week" ? 7 : 14));
    app.refresh();
  };
  const add = (e: Event) => {
    const { title } = formValues(e);
    if (!title?.trim()) return;
    resetForm(e);
    const p = parseNaturalTime(title, localNow(app.now()));
    const date = p.date ?? anchor;
    const allDay = p.minutes === null;
    const start = allDay ? atLocal(date) : atLocal(date, p.minutes!);
    const end = allDay ? atLocal(plusDays(date, 1)) : start + 3_600_000;
    void app.act(async () => { await m.addEvent({ title: p.rest || title, start, end, allDay }); });
  };
  const label = view === "month" ? fmtMonth(anchor) : view === "week" ? `${fmtShortDay(weekStart(anchor))} – ${fmtShortDay(plusDays(weekStart(anchor), 6))}` : fmtMonth(anchor);
  return html`${app.head(t.nav.calendar)}
    ${tabs(view, t.calendar.views, (k) => { app.ui.calView = k; app.refresh(); }, t.nav.calendar)}
    <div class="cal-toolbar">
      <m3e-icon-button aria-label=${t.calendar.previous} @click=${() => step(-1)}>${icon("left")}</m3e-icon-button>
      <m3e-icon-button aria-label=${t.calendar.next} @click=${() => step(1)}>${icon("right")}</m3e-icon-button>
      <h2 class="grow" data-testid="cal-label">${label}</h2>
      <m3e-button variant="outlined" @click=${() => { app.ui.calAnchor = m.today(); app.refresh(); }}>${t.calendar.today}</m3e-button>
    </div>
    <form class="inline" @submit=${add} style="margin-bottom:var(--s3)">
      ${field({ label: t.calendar.add, name: "title", placeholder: t.calendar.addPlaceholder, testid: "add-event-input" })}
      <m3e-button variant="filled" type="submit">${icon("add", { slot: "icon" })}${t.capture.submit}</m3e-button>
    </form>
    ${view === "agenda" ? agenda(app, anchor) : view === "week" ? week(app, anchor) : month(app, anchor)}`;
}

/** Events plus birthdays per local day in [from, to). */
function entries(app: App, from: LocalDate, to: LocalDate): Map<string, DayEntry[]> {
  const m = app.model;
  const out = new Map<string, DayEntry[]>();
  const put = (d: LocalDate, e: DayEntry) => { const k = `${d.year}-${d.month}-${d.day}`; out.set(k, [...(out.get(k) ?? []), e]); };
  for (const o of m.occurrences(from, to)) {
    // A multi-day occurrence shows on each day it covers.
    let d = localDate(o.start);
    const last = localDate(Math.max(o.start, o.end - 1));
    while (compareDates(d, last) <= 0) {
      if (compareDates(d, from) >= 0 && compareDates(d, to) < 0) put(d, toEntry(o));
      d = plusDays(d, 1);
    }
  }
  if (m.isOn("birthdays")) {
    for (const c of m.contacts()) {
      const mo = c.birthday_month, da = c.birthday_day;
      if (typeof mo !== "number" || typeof da !== "number") continue;
      for (let y = from.year; y <= to.year; y++) {
        const d = { year: y, month: mo, day: da };
        if (compareDates(d, from) >= 0 && compareDates(d, to) < 0) put(d, { allDay: true, start: 0, title: t.calendar.birthday(str(c.display_name) ?? ""), kind: "birthday" });
      }
    }
  }
  for (const v of out.values()) v.sort((a, b) => Number(b.allDay) - Number(a.allDay) || a.start - b.start);
  return out;
}
const toEntry = (o: Occurrence): DayEntry => ({ allDay: o.allDay, start: o.start, title: str(o.event.title) ?? "", kind: "event" });
const keyOf = (d: LocalDate) => `${d.year}-${d.month}-${d.day}`;

function evChip(e: DayEntry) {
  return html`<div class="ev ${e.allDay ? "all-day" : ""}" data-testid="cal-event">${e.allDay ? nothing : html`<span class="t">${fmtTime(e.start)}</span>`}${e.title}</div>`;
}

function agenda(app: App, anchor: LocalDate) {
  const days = Array.from({ length: 14 }, (_, i) => plusDays(anchor, i));
  const map = entries(app, anchor, plusDays(anchor, 14));
  const withItems = days.filter((d) => map.get(keyOf(d))?.length);
  if (!withItems.length) return html`<section class="card"><p class="empty">${t.calendar.nothing}</p></section>`;
  return html`<div class="stack">${withItems.map((d) => html`<section class="card" aria-label=${fmtDay(d)}>
    <h3 class=${sameDate(d, app.model.today()) ? "" : "muted"}>${fmtDay(d)}</h3>
    <ul class="list">${map.get(keyOf(d))!.map((e) => html`<li class="item"><span class="time">${e.allDay ? t.calendar.allDay : fmtTime(e.start)}</span><span class="text">${e.title}</span></li>`)}</ul>
  </section>`)}</div>`;
}

function week(app: App, anchor: LocalDate) {
  const start = weekStart(anchor);
  const map = entries(app, start, plusDays(start, 7));
  const today = app.model.today();
  return html`<div class="week" data-testid="cal-week">${Array.from({ length: 7 }, (_, i) => {
    const d = plusDays(start, i);
    const es = map.get(keyOf(d)) ?? [];
    return html`<div class="day ${sameDate(d, today) ? "is-today" : ""}" aria-label=${fmtDay(d)}>
      <div class="day-head"><span>${fmtWeekday(d)}</span><span class="num">${d.day}</span></div>
      ${es.map(evChip)}
    </div>`;
  })}</div>`;
}

function month(app: App, anchor: LocalDate) {
  const first = { year: anchor.year, month: anchor.month, day: 1 };
  const gridStart = weekStart(first);
  const map = entries(app, gridStart, plusDays(gridStart, 42));
  const today = app.model.today();
  const weeks = 6;
  return html`<div class="month" data-testid="cal-month">
    ${Array.from({ length: 7 }, (_, i) => html`<div class="dow" aria-hidden="true">${fmtWeekday(plusDays(gridStart, i))}</div>`)}
    ${Array.from({ length: weeks * 7 }, (_, i) => {
      const d = plusDays(gridStart, i);
      const es = map.get(keyOf(d)) ?? [];
      return html`<div class="cell ${d.month !== anchor.month ? "other" : ""} ${sameDate(d, today) ? "is-today" : ""}" aria-label=${fmtDay(d)}>
        <span class="num">${d.day}</span>
        ${es.slice(0, 2).map(evChip)}${es.length > 2 ? html`<span class="muted small more">${t.calendar.more(es.length - 2)}</span>` : nothing}
        <span class="dotline">${es.slice(0, 4).map(() => html`<span class="pip"></span>`)}</span>
      </div>`;
    })}
  </div>`;
}
