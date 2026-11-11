// What's on a day across modules: events, reminders, dated tasks and birthdays. Today's Plan card and the
// Calendar agenda read the same list, so they never disagree.
import type { App } from "../app";
import { t } from "../copy";
import { compareDates, isoDate, parseIsoDate, plusDays, type LocalDate } from "../capture/natural-time";
import { atLocal, localDate, num, str } from "../model";

export type ItemKind = "event" | "reminder" | "task" | "birthday";
export interface DayItem { day: LocalDate; start: number; end: number; allDay: boolean; title: string; kind: ItemKind; id: string }

export const dayKey = (d: LocalDate) => `${d.year}-${d.month}-${d.day}`;

/** Everything dated in [from, to), sorted by day, all-day first, then by start. */
export function dayItems(app: App, from: LocalDate, to: LocalDate, kinds: ItemKind[] = ["event", "reminder", "task", "birthday"]): DayItem[] {
  const m = app.model;
  const out: DayItem[] = [];
  const inRange = (d: LocalDate) => compareDates(d, from) >= 0 && compareDates(d, to) < 0;
  if (kinds.includes("event") && m.isOn("calendar")) {
    for (const o of m.occurrences(from, to)) {
      // A multi-day occurrence shows on each day it covers.
      let d = localDate(o.start);
      const last = localDate(Math.max(o.start, o.end - 1));
      while (compareDates(d, last) <= 0) {
        if (inRange(d)) out.push({ day: d, start: o.allDay ? atLocal(d) : o.start, end: o.end, allDay: o.allDay, title: str(o.event.title) ?? "", kind: "event", id: str(o.event.id) ?? "" });
        d = plusDays(d, 1);
      }
    }
  }
  if (kinds.includes("reminder") && m.isOn("reminders")) {
    for (const r of m.reminders()) {
      const at = num(r.snoozed_until) ?? num(r.fire_at) ?? 0;
      const d = localDate(at);
      if (inRange(d)) out.push({ day: d, start: at, end: at, allDay: false, title: str(r.title) ?? "", kind: "reminder", id: str(r.id) ?? "" });
    }
  }
  if (kinds.includes("task") && m.isOn("tasks")) {
    for (const x of m.openTasks()) {
      const due = str(x.due_date);
      if (!due) continue;
      const d = parseIsoDate(due);
      if (!inRange(d)) continue;
      const min = num(x.due_minutes);
      out.push({ day: d, start: atLocal(d, min ?? 0), end: atLocal(d, min ?? 0), allDay: min === null, title: str(x.title) ?? "", kind: "task", id: str(x.id) ?? "" });
    }
  }
  if (kinds.includes("birthday") && m.isOn("birthdays")) {
    for (const c of m.contacts()) {
      const mo = c.birthday_month, da = c.birthday_day;
      if (typeof mo !== "number" || typeof da !== "number") continue;
      for (let y = from.year; y <= to.year; y++) {
        const d = { year: y, month: mo, day: da };
        if (inRange(d)) out.push({ day: d, start: atLocal(d), end: atLocal(d), allDay: true, title: t.calendar.birthday(str(c.display_name) ?? ""), kind: "birthday", id: str(c.id) ?? "" });
      }
    }
  }
  return out.sort((a, b) => compareDates(a.day, b.day) || Number(b.allDay) - Number(a.allDay) || a.start - b.start);
}

export const todayIso = (app: App) => isoDate(app.model.today());
