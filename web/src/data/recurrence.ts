// TypeScript port of :shared app.klean.shared.time.RecurrenceRule (the date-level RRULE subset shared by
// Tasks, Reminders and Calendar). Tasks use MissingDay "clamp", calendars "skip" (RFC 5545).
import { compareDates, plusDays, weekdayOrdinal, type LocalDate } from "../capture/natural-time";

export type Freq = "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY";
export type MissingDay = "clamp" | "skip";
/** day: 0 = Monday … 6 = Sunday; ordinal: n-th (1..5) or n-th last (-1..-5) in the month, MONTHLY only. */
export interface WeekdayNum { day: number; ordinal: number | null }

export interface RecurrenceRule {
  freq: Freq; interval: number; byDay: WeekdayNum[]; byMonthDay: number | null; byMonth: number | null;
  count: number | null; until: LocalDate | null;
}

const CODES = ["MO", "TU", "WE", "TH", "FR", "SA", "SU"];
const MAX_PERIODS = 4000;
const BYDAY = /^([+-]?[1-5])?(MO|TU|WE|TH|FR|SA|SU)$/;
const UNTIL = /^(\d{4})(\d{2})(\d{2})(T\d{6}Z?)?$/;

const monthLength = (y: number, m: number) => new Date(Date.UTC(y, m, 0)).getUTCDate();
const date = (year: number, month: number, day: number): LocalDate => ({ year, month, day });
const addMonths = (d: LocalDate, n: number): LocalDate => {
  const total = d.year * 12 + (d.month - 1) + n;
  return date(Math.floor(total / 12), (total % 12) + 1, 1);
};
const two = (n: number) => String(n).padStart(2, "0");

export function parseRule(text: string): RecurrenceRule | null {
  const body = text.trim().toUpperCase().replace(/^RRULE:/, "").trim();
  if (!body) return null;
  const m = new Map<string, string>();
  for (const p of body.split(";").filter((s) => s.trim())) {
    const i = p.indexOf("=");
    if (i <= 0) return null;
    const k = p.slice(0, i).trim();
    if (m.has(k)) return null;
    m.set(k, p.slice(i + 1).trim());
  }
  const freq = m.get("FREQ") as Freq | undefined;
  if (!freq || !["DAILY", "WEEKLY", "MONTHLY", "YEARLY"].includes(freq)) return null;
  const known = new Set(["FREQ", "INTERVAL", "BYDAY", "BYMONTHDAY", "BYMONTH", "COUNT", "UNTIL", "WKST"]);
  if ([...m.keys()].some((k) => !known.has(k))) return null;
  const int = (v: string) => (/^[+-]?\d+$/.test(v) ? parseInt(v, 10) : NaN);
  let interval = 1;
  if (m.has("INTERVAL")) { interval = int(m.get("INTERVAL")!); if (!(interval >= 1)) return null; }
  let byDay: WeekdayNum[] = [];
  if (m.has("BYDAY")) {
    const seen = new Set<string>();
    for (const code of m.get("BYDAY")!.split(",")) {
      const mm = BYDAY.exec(code.trim());
      if (!mm) return null;
      const ord = mm[1] ? parseInt(mm[1], 10) : null;
      if (ord !== null && freq !== "MONTHLY") return null;
      const wd = { day: CODES.indexOf(mm[2]), ordinal: ord };
      const k = `${wd.day}/${wd.ordinal}`;
      if (!seen.has(k)) { seen.add(k); byDay.push(wd); }
    }
    byDay = byDay.sort((a, b) => a.day - b.day || (a.ordinal ?? 0) - (b.ordinal ?? 0));
  }
  let byMonthDay: number | null = null;
  if (m.has("BYMONTHDAY")) {
    const n = int(m.get("BYMONTHDAY")!);
    if (Number.isNaN(n) || (freq !== "MONTHLY" && freq !== "YEARLY") || n === 0 || n < -1 || n > 31) return null;
    byMonthDay = n;
  }
  let byMonth: number | null = null;
  if (m.has("BYMONTH")) {
    if (freq !== "YEARLY") return null;
    const n = int(m.get("BYMONTH")!);
    if (!(n >= 1 && n <= 12)) return null;
    byMonth = n;
  }
  if (byMonthDay !== null && byDay.length) return null;
  let count: number | null = null;
  if (m.has("COUNT")) { count = int(m.get("COUNT")!); if (!(count >= 1)) return null; }
  let until: LocalDate | null = null;
  if (m.has("UNTIL")) {
    const g = UNTIL.exec(m.get("UNTIL")!);
    if (!g) return null;
    const [y, mo, d] = [+g[1], +g[2], +g[3]];
    if (mo < 1 || mo > 12 || d < 1 || d > monthLength(y, mo)) return null;
    until = date(y, mo, d);
  }
  if (count !== null && until !== null) return null;
  return { freq, interval, byDay, byMonthDay, byMonth, count, until };
}

export function formatRule(r: RecurrenceRule): string {
  const out = [`FREQ=${r.freq}`];
  if (r.interval !== 1) out.push(`INTERVAL=${r.interval}`);
  if (r.byDay.length) out.push("BYDAY=" + r.byDay.map((w) => (w.ordinal ?? "") + CODES[w.day]).join(","));
  if (r.byMonth !== null) out.push(`BYMONTH=${r.byMonth}`);
  if (r.byMonthDay !== null) out.push(`BYMONTHDAY=${r.byMonthDay}`);
  if (r.count !== null) out.push(`COUNT=${r.count}`);
  if (r.until) out.push(`UNTIL=${String(r.until.year).padStart(4, "0")}${two(r.until.month)}${two(r.until.day)}`);
  return out.join(";");
}

function inMonth(r: RecurrenceRule, year: number, month: number, start: LocalDate, missing: MissingDay): LocalDate[] {
  const len = monthLength(year, month);
  if (r.byDay.length && r.freq === "MONTHLY") {
    const firstWd = weekdayOrdinal(date(year, month, 1));
    return r.byDay
      .flatMap((wd) => {
        const offset = (wd.day - firstWd + 7) % 7;
        const all: LocalDate[] = [];
        for (let d = 1 + offset; d <= len; d += 7) all.push(date(year, month, d));
        if (wd.ordinal === null) return all;
        const pick = wd.ordinal > 0 ? all[wd.ordinal - 1] : all[all.length + wd.ordinal];
        return pick ? [pick] : [];
      })
      .sort(compareDates);
  }
  const wanted = r.byMonthDay ?? start.day;
  if (missing === "skip" && wanted > len) return [];
  const day = wanted < 0 ? len + 1 + wanted : Math.min(wanted, len);
  return [date(year, month, Math.max(1, Math.min(day, len)))];
}

/** Occurrences on or after `start`, in order, honouring COUNT and UNTIL. */
export function* sequence(r: RecurrenceRule, start: LocalDate, missing: MissingDay = "clamp"): Generator<LocalDate> {
  let emitted = 0;
  const emit = function* (d: LocalDate): Generator<LocalDate, boolean> {
    if (compareDates(d, start) < 0) return true;
    if (r.until && compareDates(d, r.until) > 0) return false;
    if (r.count !== null && emitted >= r.count) return false;
    emitted++;
    yield d;
    return true;
  };
  if (r.freq === "DAILY") {
    for (let k = 0; k < MAX_PERIODS * 7; k++) {
      const d = plusDays(start, k * r.interval);
      if (r.byDay.length && !r.byDay.some((w) => w.day === weekdayOrdinal(d))) continue;
      if (!(yield* emit(d))) return;
    }
  } else if (r.freq === "WEEKLY") {
    const days = (r.byDay.length ? r.byDay.map((w) => w.day) : [weekdayOrdinal(start)]).sort((a, b) => a - b);
    const monday = plusDays(start, -weekdayOrdinal(start));
    for (let k = 0; k < MAX_PERIODS; k++) {
      const week = plusDays(monday, 7 * k * r.interval);
      for (const wd of days) if (!(yield* emit(plusDays(week, wd)))) return;
    }
  } else if (r.freq === "MONTHLY") {
    for (let k = 0; k < MAX_PERIODS; k++) {
      const first = addMonths(date(start.year, start.month, 1), k * r.interval);
      for (const d of inMonth(r, first.year, first.month, start, missing)) if (!(yield* emit(d))) return;
    }
  } else {
    for (let k = 0; k < MAX_PERIODS / 4; k++) {
      for (const d of inMonth(r, start.year + k * r.interval, r.byMonth ?? start.month, start, missing)) if (!(yield* emit(d))) return;
    }
  }
}

/** The first occurrence strictly after `after`; the series starts at `start` (default: `after`). */
export function nextOccurrence(r: RecurrenceRule, after: LocalDate, start?: LocalDate): LocalDate | null {
  for (const d of sequence(r, start ?? after)) if (compareDates(d, after) > 0) return d;
  return null;
}

/** Every occurrence starting at `start` within from..to (inclusive). */
export function occurrences(r: RecurrenceRule, start: LocalDate, from: LocalDate, to: LocalDate, missing: MissingDay = "clamp", limit = 1000): LocalDate[] {
  const out: LocalDate[] = [];
  for (const d of sequence(r, start, missing)) {
    if (compareDates(d, to) > 0 || out.length >= limit) break;
    if (compareDates(d, from) >= 0) out.push(d);
  }
  return out;
}

/** COUNT goes down by one; null means the series is used up. */
export function remainingAfterOne(r: RecurrenceRule): RecurrenceRule | null {
  if (r.count === null) return r;
  return r.count <= 1 ? null : { ...r, count: r.count - 1 };
}
