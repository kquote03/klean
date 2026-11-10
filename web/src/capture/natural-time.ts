// TypeScript port of :shared app.klean.shared.time.NaturalTime. Behaviour must match the Kotlin original;
// the shared fixture shared/src/commonTest/resources/capture-cases.json is run against both.

/** A local calendar date. */
export interface LocalDate { year: number; month: number; day: number }
/** A local wall-clock date-time. */
export interface LocalDateTime { date: LocalDate; hour: number; minute: number }

/** Whenever `minutes` (minute of day) is set, `date` is set too. `rest` is the input minus the time words. */
export interface ParsedTime { date: LocalDate | null; minutes: number | null; rest: string }

const DAY_MS = 86_400_000;
const toEpochDay = (d: LocalDate) => Math.round(Date.UTC(d.year, d.month - 1, d.day) / DAY_MS);
const fromEpochDay = (n: number): LocalDate => {
  const t = new Date(n * DAY_MS);
  return { year: t.getUTCFullYear(), month: t.getUTCMonth() + 1, day: t.getUTCDate() };
};
export const plusDays = (d: LocalDate, n: number) => fromEpochDay(toEpochDay(d) + n);
export const sameDate = (a: LocalDate | null, b: LocalDate | null) =>
  !!a && !!b && a.year === b.year && a.month === b.month && a.day === b.day;
export const compareDates = (a: LocalDate, b: LocalDate) => toEpochDay(a) - toEpochDay(b);
/** ISO weekday ordinal: Monday = 0 … Sunday = 6 (Kotlin's DayOfWeek.ordinal). */
export const weekdayOrdinal = (d: LocalDate) => (new Date(Date.UTC(d.year, d.month - 1, d.day)).getUTCDay() + 6) % 7;
export const isoDate = (d: LocalDate) =>
  `${String(d.year).padStart(4, "0")}-${String(d.month).padStart(2, "0")}-${String(d.day).padStart(2, "0")}`;
export const parseIsoDate = (s: string): LocalDate => {
  const [y, m, d] = s.split("-").map(Number);
  return { year: y, month: m, day: d };
};
export function parseLocalDateTime(s: string): LocalDateTime {
  const [date, time = "00:00"] = s.split("T");
  const [h, mi] = time.split(":").map(Number);
  return { date: parseIsoDate(date), hour: h, minute: mi };
}

/** A real calendar date? (Kotlin's LocalDate constructor throws otherwise.) */
function validDate(year: number, month: number, day: number): LocalDate | null {
  if (month < 1 || month > 12 || day < 1) return null;
  const t = new Date(Date.UTC(year, month - 1, day));
  if (year >= 0 && year < 100) t.setUTCFullYear(year);
  return t.getUTCMonth() === month - 1 && t.getUTCDate() === day ? { year, month, day } : null;
}

function plusMinutes(now: LocalDateTime, minutes: number): LocalDateTime {
  const total = toEpochDay(now.date) * 1440 + now.hour * 60 + now.minute + minutes;
  const day = Math.floor(total / 1440);
  const m = total - day * 1440;
  return { date: fromEpochDay(day), hour: Math.floor(m / 60), minute: m % 60 };
}

const WEEKDAYS = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"];
const MONTH_PREFIXES = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"];
const MONTH =
  "(january|february|march|april|may|june|july|august|september|october|november|december|" +
  "jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec)";
const PRE = String.raw`(?:\b(?:at|on)\s+)?`;
const ORD = "(?:st|nd|rd|th)?";
const re = (s: string) => new RegExp(s, "i");

const DURATION = re(String.raw`\bin\s+(an?|\d{1,4})\s+(hours?|hrs?|minutes?|mins?|days?|weeks?)\b`);
const SLASH_DATE = re(String.raw`${PRE}(?<![\w/:.])(\d{1,2})/(\d{1,2})(?:/(\d{4}|\d{2}))?(?![\w/:])`);
const MONTH_DAY = re(String.raw`${PRE}\b${MONTH}\.?\s+(\d{1,2})${ORD}(?![\w/:])(?:,?\s+(\d{4})\b)?`);
const DAY_MONTH = re(String.raw`${PRE}(?<![\w/:.])(\d{1,2})${ORD}\s+(?:of\s+)?${MONTH}\b(?:,?\s+(\d{4})\b)?`);
const RELATIVE = re(String.raw`${PRE}\b(today|tonight|tomorrow|tmrw|next\s+week)\b`);
const WEEKDAY = re(String.raw`${PRE}\b(?:(?:next|this)\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b`);
const MERIDIEM_TIME = re(String.raw`${PRE}(?<![\w/:.])(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)(?!\w)`);
const COLON_TIME = re(String.raw`${PRE}(?<![\w/:.])(\d{1,2}):(\d{2})(?![\w/:])`);
const AT_HOUR = re(String.raw`\bat\s+(\d{1,2})(?![\w/:.])`);
const NAMED_TIME = re(String.raw`${PRE}\b(noon|midnight)\b`);
const DANGLING_END = /\s*\b(?:at|on)$/i;
const DANGLING_START = /^(?:at|on)\b\s*/i;

interface ClockTime { hour: number; minute: number; ambiguous: boolean }

/** Kotlin's Regex.find: the first match; group values are "" when a group did not take part. */
function find(r: RegExp, s: string): { index: number; length: number; g: string[] } | null {
  const m = r.exec(s);
  if (!m) return null;
  return { index: m.index, length: m[0].length, g: m.map((x) => x ?? "") };
}

const monthNumber = (name: string) => MONTH_PREFIXES.indexOf(name.toLowerCase().slice(0, 3)) + 1;

function resolveDate(day: number, month: number, year: number | null, today: LocalDate): LocalDate | null {
  if (month < 1 || month > 12 || day < 1 || day > 31) return null;
  if (year !== null) return validDate(year < 100 ? 2000 + year : year, month, day);
  for (let y = today.year; y <= today.year + 8; y++) {
    const d = validDate(y, month, day);
    if (d && compareDates(d, today) >= 0) return d;
  }
  return null;
}

function trimChars(s: string, chars: string): string {
  let a = 0, b = s.length;
  while (a < b && chars.includes(s[a])) a++;
  while (b > a && chars.includes(s[b - 1])) b--;
  return s.slice(a, b);
}

function cleanRest(s: string): string {
  let r = s.replace(/\s+/g, " ").trim();
  for (;;) {
    const next = trimChars(r.replace(DANGLING_END, "").replace(DANGLING_START, "").trim(), ",;-").trim();
    if (next === r) break;
    r = next;
  }
  return r;
}

export function parseNaturalTime(text: string, now: LocalDateTime): ParsedTime {
  let work = text;
  const take = (r: RegExp) => {
    const m = find(r, work);
    if (m) work = work.slice(0, m.index) + " " + work.slice(m.index + m.length);
    return m;
  };
  const today = now.date;
  const nowMin = now.hour * 60 + now.minute;

  // 1. "in N hours/minutes/days/weeks"
  let durDateTime: LocalDateTime | null = null;
  let durDate: LocalDate | null = null;
  const dm = take(DURATION);
  if (dm) {
    const raw = dm.g[1].toLowerCase();
    const n = raw === "a" || raw === "an" ? 1 : parseInt(raw, 10);
    const unit = dm.g[2].toLowerCase();
    if (unit.startsWith("h")) durDateTime = plusMinutes(now, n * 60);
    else if (unit.startsWith("m")) durDateTime = plusMinutes(now, n);
    else if (unit.startsWith("d")) durDate = plusDays(today, n);
    else durDate = plusDays(today, n * 7);
  }

  // 2. Explicit dates.
  let explicit: LocalDate | null = null;
  for (const r of [SLASH_DATE, MONTH_DAY, DAY_MONTH]) {
    const m = find(r, work);
    if (!m) continue;
    let day: number, month: number;
    const year = m.g[3];
    if (r === SLASH_DATE) { day = +m.g[1]; month = +m.g[2]; }
    else if (r === MONTH_DAY) { day = +m.g[2]; month = monthNumber(m.g[1]); }
    else { day = +m.g[1]; month = monthNumber(m.g[2]); }
    const resolved = resolveDate(day, month, year ? parseInt(year, 10) : null, today);
    if (!resolved) continue;
    explicit = resolved;
    work = work.slice(0, m.index) + " " + work.slice(m.index + m.length);
    break;
  }

  // 3. Relative day words.
  let relative: LocalDate | null = null;
  let tonight = false;
  const rm = take(RELATIVE);
  if (rm) {
    const w = rm.g[1].toLowerCase();
    if (w === "today") relative = today;
    else if (w === "tonight") { relative = today; tonight = true; }
    else if (w === "tomorrow" || w === "tmrw") relative = plusDays(today, 1);
    else relative = plusDays(today, 7);
  }
  const wm = take(WEEKDAY);
  const weekdayDiff = wm ? (WEEKDAYS.indexOf(wm.g[1].toLowerCase()) - weekdayOrdinal(today) + 7) % 7 : null;

  // 4. Time of day.
  const time = parseTime(take);

  const fixedDate = explicit ?? relative ?? durDate ?? durDateTime?.date ?? null;
  const todayLike = fixedDate ? sameDate(fixedDate, today) : weekdayDiff === null || weekdayDiff === 0;

  let minutes: number | null = null;
  let rollIfPassed = false;
  if (durDateTime && !time) {
    minutes = durDateTime.hour * 60 + durDateTime.minute;
  } else if (time) {
    const h = time.hour, mm = time.minute;
    if (!time.ambiguous) { minutes = h * 60 + mm; rollIfPassed = true; }
    else if (tonight) minutes = (h < 12 ? h + 12 : h) * 60 + mm;
    else if (h >= 1 && h <= 6) { minutes = (h + 12) * 60 + mm; rollIfPassed = true; }
    else if (!todayLike) minutes = h * 60 + mm;
    else {
      const candidates = h === 12 ? [12 * 60 + mm] : [h * 60 + mm, (h + 12) * 60 + mm];
      minutes = candidates.find((c) => c > nowMin) ?? candidates[0];
      rollIfPassed = true;
    }
  } else if (tonight) {
    minutes = 20 * 60;
  }

  let date: LocalDate | null;
  if (fixedDate) date = fixedDate;
  else if (weekdayDiff !== null) {
    const diff = weekdayDiff === 0 && (minutes === null || minutes <= nowMin) ? 7 : weekdayDiff;
    date = plusDays(today, diff);
  } else if (minutes !== null) date = rollIfPassed && minutes <= nowMin ? plusDays(today, 1) : today;
  else date = null;
  return { date, minutes, rest: cleanRest(work) };
}

function parseTime(take: (r: RegExp) => { g: string[] } | null): ClockTime | null {
  const mt = take(MERIDIEM_TIME);
  if (mt) {
    const h = +mt.g[1], mm = +(mt.g[2] || "0");
    if (h >= 1 && h <= 12 && mm < 60) {
      const pm = mt.g[3].toLowerCase().startsWith("p");
      return { hour: (h % 12) + (pm ? 12 : 0), minute: mm, ambiguous: false };
    }
  }
  const ct = take(COLON_TIME);
  if (ct) { const c = clock(ct.g[1], +ct.g[2]); if (c) return c; }
  const at = take(AT_HOUR);
  if (at) { const c = clock(at.g[1], 0); if (c) return c; }
  const nt = take(NAMED_TIME);
  if (nt) return { hour: nt.g[1].toLowerCase() === "noon" ? 12 : 0, minute: 0, ambiguous: false };
  return null;
}

function clock(hourText: string, minute: number): ClockTime | null {
  const h = parseInt(hourText, 10);
  if (h > 23 || minute > 59) return null;
  return { hour: h, minute, ambiguous: h >= 1 && h <= 12 && !hourText.startsWith("0") };
}
