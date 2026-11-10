// Typed reads and writes over the generic record store, mirroring the Android repositories closely enough that
// rows written here look exactly like rows written on the phone (same columns, same enums, same seeded ids).
import { nameUuid, uuidV7 } from "./data/ids";
import type { RecordStore } from "./data/store";
import type { Json, Row } from "./data/sync-types";
import { compareDates, isoDate, parseIsoDate, plusDays, type LocalDate } from "./capture/natural-time";
import { formatRule, nextOccurrence, occurrences, parseRule, remainingAfterOne } from "./data/recurrence";

// ---- small helpers ---------------------------------------------------------------------------------------

export const bool = (v: Json | undefined) => v === true || v === 1;
export const str = (v: Json | undefined) => (typeof v === "string" ? v : null);
export const num = (v: Json | undefined) => (typeof v === "number" ? v : null);
export const zone = () => Intl.DateTimeFormat().resolvedOptions().timeZone;

export function localDate(ms: number): LocalDate {
  const t = new Date(ms);
  return { year: t.getFullYear(), month: t.getMonth() + 1, day: t.getDate() };
}
export const minuteOfDay = (ms: number) => { const t = new Date(ms); return t.getHours() * 60 + t.getMinutes(); };
export const atLocal = (d: LocalDate, minutes = 0) =>
  new Date(d.year, d.month - 1, d.day, Math.floor(minutes / 60), minutes % 60).getTime();
export const startOfDay = (d: LocalDate) => atLocal(d, 0);
export const weekStart = (d: LocalDate) => plusDays(d, -((new Date(d.year, d.month - 1, d.day).getDay() + 6) % 7));

function parseJson<T>(raw: Json | undefined, fallback: T): T {
  if (typeof raw !== "string") return (raw as T) ?? fallback;
  try { return JSON.parse(raw) as T; } catch { return fallback; }
}

// ---- modules -------------------------------------------------------------------------------------------

export type ModuleKey = "tasks" | "reminders" | "calendar" | "contacts" | "birthdays" | "workout" | "sleep" | "diet";
export const MODULES: ModuleKey[] = ["tasks", "reminders", "calendar", "contacts", "birthdays", "workout", "sleep", "diet"];

export class Model {
  constructor(readonly store: RecordStore, readonly now: () => number = Date.now) {}

  today(): LocalDate { return localDate(this.now()); }

  // ---- synced settings (the `setting` table; id = nameUuid(key), value_json = JSON) ----
  setting<T>(key: string, fallback: T): T {
    const row = this.store.get("setting", nameUuid(key));
    return row ? parseJson<T>(row.value_json, fallback) : fallback;
  }
  setSetting(key: string, value: unknown) {
    return this.store.write("setting", nameUuid(key), { key, value_json: JSON.stringify(value) });
  }
  disabledModules(): Set<string> { return new Set(this.setting<string[]>("core.modules.disabled", [])); }
  isOn(m: ModuleKey) { return !this.disabledModules().has(m); }
  setModuleOn(m: ModuleKey, on: boolean) {
    const s = this.disabledModules();
    if (on) s.delete(m); else s.add(m);
    return this.setSetting("core.modules.disabled", [...s].sort());
  }

  /** Quiet hours = the sleep schedule (bed → wake), like Android's QuietHoursService. */
  quietHours(): { bed: number; wake: number } {
    const s = this.store.get("sleep_schedule", SLEEP_SCHEDULE_ID);
    if (s && num(s.bed_minutes) !== null) return { bed: num(s.bed_minutes)!, wake: num(s.wake_minutes)! };
    const q = this.setting<{ startMinute: number; endMinute: number }>("core.quietHours", { startMinute: 0, endMinute: 480 });
    return { bed: q.startMinute, wake: q.endMinute };
  }
  async setQuietHours(bed: number, wake: number) {
    const prev = this.store.get("sleep_schedule", SLEEP_SCHEDULE_ID);
    await this.store.write("sleep_schedule", SLEEP_SCHEDULE_ID, {
      bed_minutes: bed, wake_minutes: wake, days: 127, reminder_minutes_before: num(prev?.reminder_minutes_before) ?? 30,
    });
    // Android mirrors schedule writes into the older key, which frozen diet code still reads.
    await this.setSetting("core.quietHours", { startMinute: bed, endMinute: wake });
  }

  // ---- tasks ----
  lists(): Row[] { return this.store.rows("task_list").sort((a, b) => (num(a.position) ?? 0) - (num(b.position) ?? 0)); }
  tasks(): Row[] { return this.store.rows("task").filter((t) => t.archived_at == null); }
  openTasks(): Row[] { return this.tasks().filter((t) => str(t.status) !== "DONE" && t.completed_at == null && t.parent_id == null); }
  subtasks(id: string): Row[] { return this.tasks().filter((t) => t.parent_id === id); }
  tasksDueToday(): Row[] { const d = isoDate(this.today()); return sortTasks(this.openTasks().filter((t) => t.due_date === d)); }
  /** Past-due open tasks: Android's "Still want these?" review (never "overdue"). */
  stillWant(): Row[] { const d = isoDate(this.today()); return sortTasks(this.openTasks().filter((t) => typeof t.due_date === "string" && t.due_date < d)); }
  upcoming(): Row[] { const d = isoDate(this.today()); return sortTasks(this.openTasks().filter((t) => typeof t.due_date === "string" && t.due_date > d)); }
  undated(): Row[] { return this.openTasks().filter((t) => t.due_date == null).sort(byCreated); }
  done(): Row[] { return this.tasks().filter((t) => str(t.status) === "DONE" && t.parent_id == null).sort((a, b) => (num(b.completed_at) ?? 0) - (num(a.completed_at) ?? 0)); }

  /** Android seeds Personal and Work with name-based ids, so seeding here merges with the phone's rows. */
  async ensureLists(): Promise<string> {
    if (!this.store.get("task_list", PERSONAL_LIST_ID)) {
      await this.store.write("task_list", PERSONAL_LIST_ID, { name: "Personal", kind: "PERSONAL", position: 0, source: "KLEAN", remote_id: null, selected: true });
    }
    if (!this.store.get("task_list", WORK_LIST_ID)) {
      await this.store.write("task_list", WORK_LIST_ID, { name: "Work", kind: "WORK", position: 1, source: "KLEAN", remote_id: null, selected: true });
    }
    return this.setting<string>("tasks.defaultListId", PERSONAL_LIST_ID);
  }

  async addTask(t: { title: string; dueDate?: LocalDate | null; dueMinutes?: number | null; listId?: string; notes?: string | null; recurrence?: string | null; parentId?: string | null; source?: "MANUAL" | "CAPTURE" }): Promise<string> {
    const listId = t.listId ?? (t.parentId ? str(this.store.get("task", t.parentId)?.list_id) : null) ?? (await this.ensureLists());
    const id = uuidV7(this.now());
    await this.store.write("task", id, {
      list_id: listId, title: t.title.trim(), notes: t.notes ?? null,
      due_date: t.dueDate ? isoDate(t.dueDate) : null, due_minutes: t.dueDate ? t.dueMinutes ?? null : null,
      priority: 0, recurrence: t.recurrence ?? null, completed_at: null, position: 0, source: t.source ?? "MANUAL",
      parent_id: t.parentId ?? null, status: "OPEN", archived_at: null, origin: "KLEAN",
      remote_id: null, remote_list_id: null, remote_etag: null,
    });
    return id;
  }
  updateTask(id: string, values: Row) { return this.store.write("task", id, values); }

  /** Done, plus the next instance of a repeating task (as TasksRepository.complete does). */
  async completeTask(id: string): Promise<string | null> {
    const t = this.store.get("task", id);
    if (!t || t.completed_at != null) return null;
    await this.store.write("task", id, { status: "DONE", completed_at: this.now() });
    const rule = str(t.recurrence) ? parseRule(str(t.recurrence)!) : null;
    if (!rule) return null;
    const from = str(t.due_date) ? parseIsoDate(str(t.due_date)!) : this.today();
    const next = nextOccurrence(rule, from);
    if (!next) return null;
    const rest = remainingAfterOne(rule);
    const nextId = await this.addTask({
      title: str(t.title) ?? "", listId: str(t.list_id) ?? undefined, notes: str(t.notes), dueDate: next,
      dueMinutes: num(t.due_minutes), recurrence: rest ? formatRule(rest) : null, parentId: str(t.parent_id),
      source: (str(t.source) as "MANUAL" | "CAPTURE") ?? "MANUAL",
    });
    for (const s of this.subtasks(id)) await this.addTask({ title: str(s.title) ?? "", listId: str(s.list_id) ?? undefined, notes: str(s.notes), parentId: nextId });
    return nextId;
  }
  reopenTask(id: string) { return this.store.write("task", id, { status: "OPEN", completed_at: null, archived_at: null }); }
  async deleteTask(id: string) {
    for (const s of this.subtasks(id)) await this.store.remove("task", str(s.id)!);
    await this.store.remove("task", id);
  }

  // ---- reminders ----
  reminders(): Row[] {
    return this.store.rows("reminder").filter((r) => ["SCHEDULED", "SNOOZED", "RINGING"].includes(str(r.state) ?? ""))
      .sort((a, b) => fireAt(a) - fireAt(b));
  }
  async addReminder(title: string, at: number, taskId: string | null = null): Promise<string> {
    const id = uuidV7(this.now());
    await this.store.write("reminder", id, {
      task_id: taskId, title: title.trim(), fire_at: at, zone: zone(), is_alarm: false, recurrence: null,
      state: "SCHEDULED", snoozed_until: null, last_fired_at: null,
    });
    return id;
  }
  setReminderState(id: string, state: "DONE" | "DISMISSED") { return this.store.write("reminder", id, { state }); }

  // ---- calendar ----
  async addEvent(e: { title: string; start: number; end: number; allDay?: boolean; location?: string | null; rrule?: string | null }): Promise<string> {
    const id = uuidV7(this.now());
    await this.store.write("event", id, {
      title: e.title.trim(), start_at: e.start, end_at: e.end, all_day: e.allDay ?? false, zone: zone(),
      location: e.location ?? null, notes: null, recurrence: null, reminder_minutes: e.allDay ? null : this.setting("calendar.defaultReminderMinutes", 10),
      provider_event_id: null, calendar_id: null, uid: id, rrule: e.rrule ?? null, exdates: "[]", alarm: false, remote_etag: null,
    });
    return id;
  }

  /** Event occurrences overlapping [from, to) local days, repeats expanded (RFC 5545: missing days skipped). */
  occurrences(from: LocalDate, toExclusive: LocalDate): Occurrence[] {
    const lo = startOfDay(from), hi = startOfDay(toExclusive);
    const out: Occurrence[] = [];
    for (const e of this.store.rows("event")) {
      const start = num(e.start_at), end = num(e.end_at);
      if (start === null || end === null) continue;
      const dur = Math.max(0, end - start);
      const rule = str(e.rrule) ? parseRule(str(e.rrule)!) : str(e.recurrence) ? parseRule(str(e.recurrence)!) : null;
      const allDay = bool(e.all_day);
      if (!rule) {
        if (start < hi && (end > lo || start >= lo)) out.push({ event: e, start, end, allDay });
        continue;
      }
      const ex = new Set(parseJson<number[]>(e.exdates, []));
      const first = localDate(start), minute = minuteOfDay(start);
      const daysBack = Math.ceil(dur / 86_400_000);
      for (const d of occurrences(rule, first, plusDays(from, -daysBack), plusDays(toExclusive, -1), "skip")) {
        const s = atLocal(d, minute);
        if (ex.has(s)) continue;
        if (s < hi && s + dur > lo) out.push({ event: e, start: s, end: s + dur, allDay });
      }
    }
    return out.sort((a, b) => Number(b.allDay) - Number(a.allDay) || a.start - b.start);
  }

  // ---- contacts & birthdays ----
  contacts(): Row[] { return this.store.rows("contact").sort((a, b) => (str(a.display_name) ?? "").localeCompare(str(b.display_name) ?? "")); }
  /** Birthdays in the next `days` days (today first), with the age they turn when the year is known. */
  birthdays(days = 366): { contact: Row; date: LocalDate; inDays: number; turns: number | null }[] {
    const today = this.today();
    const out: { contact: Row; date: LocalDate; inDays: number; turns: number | null }[] = [];
    for (const c of this.store.rows("contact")) {
      const m = num(c.birthday_month), d = num(c.birthday_day);
      if (!m || !d) continue;
      let next: LocalDate | null = null;
      for (let y = today.year; y <= today.year + 8 && !next; y++) {
        const cand = { year: y, month: m, day: d };
        const real = new Date(y, m - 1, d);
        if (real.getMonth() === m - 1 && compareDates(cand, today) >= 0) next = cand;
      }
      if (!next) continue;
      const inDays = Math.round((startOfDay(next) - startOfDay(today)) / 86_400_000);
      if (inDays > days) continue;
      const by = num(c.birthday_year);
      out.push({ contact: c, date: next, inDays, turns: by ? next.year - by : null });
    }
    return out.sort((a, b) => a.inDays - b.inDays);
  }
  async addContact(c: { name: string; phone?: string; email?: string; birthday?: { month: number; day: number; year?: number | null } | null }): Promise<string> {
    const id = uuidV7(this.now());
    await this.store.write("contact", id, {
      display_name: c.name.trim(),
      phones: JSON.stringify(c.phone ? [{ label: "mobile", value: c.phone }] : []),
      emails: JSON.stringify(c.email ? [{ label: "home", value: c.email }] : []),
      notes: null, birthday_month: c.birthday?.month ?? null, birthday_day: c.birthday?.day ?? null, birthday_year: c.birthday?.year ?? null,
      birthday_reminders: "0,3", provider_raw_id: null, name_json: null, addresses: "[]", org: null, photo_blob: null,
      anniversary: null, custom: "{}", source_account: "KLEAN", remote_uid: null,
    });
    return id;
  }

  // ---- workout ----
  sessions(): Row[] { return this.store.rows("workout_session").sort((a, b) => (num(b.started_at) ?? 0) - (num(a.started_at) ?? 0)); }
  sets(sessionId: string): Row[] { return this.store.rows("workout_set").filter((s) => s.session_id === sessionId).sort((a, b) => (num(a.position) ?? 0) - (num(b.position) ?? 0)); }
  activeSession(): Row | undefined { return this.sessions().find((s) => s.ended_at == null); }
  async startSession(notes: string | null = null, source: "MANUAL" | "CAPTURE" = "MANUAL"): Promise<string> {
    const id = uuidV7(this.now());
    await this.store.write("workout_session", id, { template_id: null, started_at: this.now(), ended_at: null, notes, source, route_blob: null, distance_m: null, hc_record_id: null });
    return id;
  }
  endSession(id: string) { return this.store.write("workout_session", id, { ended_at: this.now() }); }
  async addSet(sessionId: string, exercise: string, reps: number | null, weightKg: number | null = null): Promise<string> {
    const id = uuidV7(this.now());
    await this.store.write("workout_set", id, {
      session_id: sessionId, exercise: exercise.trim(), kind: "STRENGTH", position: this.sets(sessionId).length,
      reps, weight_kg: weightKg, duration_s: null, distance_m: null, exercise_id: null,
    });
    return id;
  }
  /** Sessions started since Monday of this week. */
  sessionsThisWeek(): number { const from = startOfDay(weekStart(this.today())); return this.sessions().filter((s) => (num(s.started_at) ?? 0) >= from).length; }

  // ---- sleep ----
  nights(): Row[] { return this.store.rows("sleep_session").sort((a, b) => (num(b.bed_at) ?? 0) - (num(a.bed_at) ?? 0)); }
  asleep(): Row | undefined { return this.nights().find((n) => n.wake_at == null); }
  /** "Last night": the longest night that ended today (Android's logical-day rule, simplified to calendar days). */
  lastNight(): Row | undefined {
    const from = startOfDay(this.today());
    return this.nights().filter((n) => (num(n.wake_at) ?? 0) >= from)
      .sort((a, b) => (num(b.wake_at)! - num(b.bed_at)!) - (num(a.wake_at)! - num(a.bed_at)!))[0];
  }
  /** One-tap bed/wake, like the Today card on the phone. */
  async toggleSleep(): Promise<"bed" | "wake"> {
    const open = this.asleep();
    if (open) { await this.store.write("sleep_session", str(open.id)!, { wake_at: this.now() }); return "wake"; }
    await this.store.write("sleep_session", uuidV7(this.now()), { bed_at: this.now(), wake_at: null, source: "MANUAL", notes: null, hc_record_id: null });
    return "bed";
  }

  // ---- diet ----
  foodToday(): Row[] {
    const from = startOfDay(this.today()), to = startOfDay(plusDays(this.today(), 1));
    return this.store.rows("food_entry").filter((f) => { const t = num(f.eaten_at) ?? 0; return t >= from && t < to; })
      .sort((a, b) => (num(b.eaten_at) ?? 0) - (num(a.eaten_at) ?? 0));
  }
  foodItems(entryId: string): Row[] { return this.store.rows("food_item").filter((i) => i.entry_id === entryId && str(i.state) !== "REJECTED"); }
  async logFood(text: string, at = this.now()): Promise<string> {
    const id = uuidV7(this.now());
    // Logging never waits for AI: enrichment stays NOT_REQUESTED, the phone may enrich it later.
    await this.store.write("food_entry", id, {
      raw_text: text.trim(), eaten_at: at, eaten_at_zone: zone(), photo_file: null, source: "MODULE_SCREEN", via_voice: false,
      meal_type: null, meal_type_provider: null, enrichment: "NOT_REQUESTED",
    });
    return id;
  }

  // ---- approvals ----
  approvals(): Row[] { return this.store.rows("approval_request").filter((a) => (str(a.decision) ?? "PENDING") === "PENDING").sort(byCreated); }
  /** Approve (EDITED when the draft or recipients changed) or reject; this is the `decision` the agent waits for. */
  decide(id: string, approve: boolean, draft?: string, recipients?: string[]) {
    const a = this.store.get("approval_request", id);
    if (!a) return Promise.resolve();
    const prevRecipients = parseJson<string[]>(a.recipients, []);
    const edited = approve && ((draft !== undefined && draft !== str(a.draft)) || (recipients !== undefined && JSON.stringify(recipients) !== JSON.stringify(prevRecipients)));
    return this.store.write("approval_request", id, {
      decision: approve ? (edited ? "EDITED" : "APPROVED") : "REJECTED",
      decided_at: this.now(),
      ...(draft !== undefined ? { draft } : {}),
      ...(recipients !== undefined ? { recipients: JSON.stringify(recipients) } : {}),
    });
  }
  recipients(a: Row): string[] { return parseJson<string[]>(a.recipients, []); }
  editable(a: Row): { draft?: boolean; recipients?: boolean } { return parseJson(a.editable, {}); }
  delegationFor(a: Row): Row | undefined { return this.store.get("delegation", str(a.delegation_id) ?? ""); }
}

export interface Occurrence { event: Row; start: number; end: number; allDay: boolean }

export const PERSONAL_LIST_ID = nameUuid("tasks.list.personal");
export const WORK_LIST_ID = nameUuid("tasks.list.work");
export const SLEEP_SCHEDULE_ID = nameUuid("sleep.schedule.primary");

const fireAt = (r: Row) => num(r.snoozed_until) ?? num(r.fire_at) ?? 0;
const byCreated = (a: Row, b: Row) => (num(a.created_at) ?? 0) - (num(b.created_at) ?? 0);
function sortTasks(ts: Row[]): Row[] {
  return ts.sort((a, b) =>
    (str(a.due_date) ?? "").localeCompare(str(b.due_date) ?? "") ||
    (num(a.due_minutes) ?? 1e9) - (num(b.due_minutes) ?? 1e9) ||
    byCreated(a, b));
}
