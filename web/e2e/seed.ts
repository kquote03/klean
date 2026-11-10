// Realistic seeded data for the visual tests and the visual review: one person's Tuesday, 10 Nov 2026, in Dubai.
// Rows match the Room schema v3 columns (core/data/schemas/…/3.json), as a phone would sync them.
import type { SyncRecord } from "../src/data/sync-types";
import { nameUuid } from "../src/data/ids";

export const NOW_ISO = "2026-11-10T09:30:00+04:00";
export const NOW = Date.parse(NOW_ISO);
const PHONE = "phone-pixel";
const at = (day: number, h: number, m = 0, month = 11) => Date.parse(`2026-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}T${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:00+04:00`);
let n = 0;
const id = (p: string) => `${p}-${String(++n).padStart(4, "0")}`;

function rec(table: string, rowId: string, json: Record<string, unknown>, updatedAt = NOW - 3_600_000): SyncRecord {
  return {
    table, id: rowId, updatedAt, deviceId: PHONE, deleted: false,
    json: { id: rowId, created_at: updatedAt - 86_400_000, updated_at: updatedAt, device_id: PHONE, deleted: false, ...json } as SyncRecord["json"],
  };
}

// The list ids Android seeds.
export const PERSONAL = nameUuid("tasks.list.personal");
const WORK = nameUuid("tasks.list.work");

const task = (title: string, due: string | null, extra: Record<string, unknown> = {}) => rec("task", id("task"), {
  list_id: PERSONAL, title, notes: null, due_date: due, due_minutes: null, priority: 0, recurrence: null, completed_at: null,
  position: 0, source: "MANUAL", parent_id: null, status: "OPEN", archived_at: null, origin: "KLEAN", remote_id: null,
  remote_list_id: null, remote_etag: null, ...extra,
});
const event = (title: string, start: number, end: number, extra: Record<string, unknown> = {}) => rec("event", id("event"), {
  title, start_at: start, end_at: end, all_day: false, zone: "Asia/Dubai", location: null, notes: null, recurrence: null,
  reminder_minutes: 10, provider_event_id: null, calendar_id: null, uid: null, rrule: null, exdates: "[]", alarm: false, remote_etag: null, ...extra,
});
const contact = (name: string, phone: string, bday: [number, number, number | null] | null) => rec("contact", id("contact"), {
  display_name: name, phones: JSON.stringify([{ label: "mobile", value: phone }]), emails: "[]", notes: null,
  birthday_month: bday?.[0] ?? null, birthday_day: bday?.[1] ?? null, birthday_year: bday?.[2] ?? null, birthday_reminders: "0,3",
  provider_raw_id: null, name_json: null, addresses: "[]", org: null, photo_blob: null, anniversary: null, custom: "{}", source_account: "KLEAN", remote_uid: null,
});

export function seed(): SyncRecord[] {
  n = 0;
  const sessionA = id("ws"), sessionB = id("ws");
  const delegation = id("deleg");
  const delegTask = task("Ask the landlord about the AC service", "2026-11-10", { due_minutes: null });
  return [
    rec("task_list", PERSONAL, { name: "Personal", kind: "PERSONAL", position: 0, source: "KLEAN", remote_id: null, selected: true }),
    rec("task_list", WORK, { name: "Work", kind: "WORK", position: 1, source: "KLEAN", remote_id: null, selected: true }),
    task("Pay the electricity bill", "2026-11-10"),
    task("Call the dentist to move Thursday", "2026-11-10", { due_minutes: 14 * 60 }),
    task("Pick up dry cleaning", "2026-11-10", { due_minutes: 18 * 60 + 30 }),
    task("Send the slides to Mariam", "2026-11-10", { list_id: WORK }),
    delegTask,
    task("Renew the car registration", "2026-11-07"),
    task("Water the plants", "2026-11-12", { recurrence: "FREQ=WEEKLY" }),
    task("Book flights for December", "2026-11-14"),
    task("Draft the quarterly report", "2026-11-16", { list_id: WORK }),
    task("Read the lease renewal", null),
    task("Buy a birthday card for Omar", "2026-11-09", { status: "DONE", completed_at: at(9, 19) }),
    rec("reminder", id("rem"), { task_id: null, title: "Take vitamins", fire_at: at(10, 13), zone: "Asia/Dubai", is_alarm: false, recurrence: null, state: "SCHEDULED", snoozed_until: null, last_fired_at: null }),
    rec("reminder", id("rem"), { task_id: null, title: "Leave for the gym", fire_at: at(10, 17, 45), zone: "Asia/Dubai", is_alarm: true, recurrence: null, state: "SCHEDULED", snoozed_until: null, last_fired_at: null }),
    event("Team stand-up", at(9, 10), at(9, 10, 30), { rrule: "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH" }),
    event("Lunch with Layla", at(10, 12, 30), at(10, 13, 30), { location: "Arabian Tea House" }),
    event("Gym: legs", at(10, 18), at(10, 19)),
    event("Dentist", at(12, 9, 30), at(12, 10, 15)),
    event("Design review", at(11, 15), at(11, 16)),
    event("Weekend in Hatta", Date.parse("2026-11-14T00:00:00+04:00"), Date.parse("2026-11-16T00:00:00+04:00"), { all_day: true, reminder_minutes: null }),
    event("Parents' anniversary dinner", at(21, 19, 30), at(21, 22)),
    contact("Omar Haddad", "+971 50 123 4567", [11, 13, 1990]),
    contact("Layla Mansour", "+971 55 987 6543", [11, 10, null]),
    contact("Sara Al Amiri", "+971 52 222 3344", [3, 3, 1993]),
    contact("Yusuf Rahman", "+971 56 765 4321", null),
    contact("Mariam Khoury", "+971 50 555 0101", [12, 2, 1988]),
    rec("workout_session", sessionA, { template_id: null, started_at: at(8, 18, 5), ended_at: at(8, 19, 0), notes: "Push day", source: "MANUAL", route_blob: null, distance_m: null, hc_record_id: null }),
    rec("workout_set", id("set"), { session_id: sessionA, exercise: "Bench press", kind: "STRENGTH", position: 0, reps: 8, weight_kg: 60, duration_s: null, distance_m: null, exercise_id: null }),
    rec("workout_set", id("set"), { session_id: sessionA, exercise: "Bench press", kind: "STRENGTH", position: 1, reps: 8, weight_kg: 60, duration_s: null, distance_m: null, exercise_id: null }),
    rec("workout_set", id("set"), { session_id: sessionA, exercise: "Shoulder press", kind: "STRENGTH", position: 2, reps: 10, weight_kg: 20, duration_s: null, distance_m: null, exercise_id: null }),
    rec("workout_session", sessionB, { template_id: null, started_at: at(9, 7, 0), ended_at: at(9, 7, 35), notes: "5 km run", source: "CAPTURE", route_blob: null, distance_m: 5000, hc_record_id: null }),
    rec("sleep_session", id("sleep"), { bed_at: at(9, 23, 40), wake_at: at(10, 7, 5), source: "MANUAL", notes: null, hc_record_id: null }),
    rec("sleep_session", id("sleep"), { bed_at: at(8, 23, 10), wake_at: at(9, 6, 50), source: "MANUAL", notes: null, hc_record_id: null }),
    rec("sleep_session", id("sleep"), { bed_at: at(8, 0, 20), wake_at: at(8, 8, 0), source: "MANUAL", notes: null, hc_record_id: null }),
    rec("food_entry", id("food"), { raw_text: "Oats with dates and a coffee", eaten_at: at(10, 7, 40), eaten_at_zone: "Asia/Dubai", photo_file: null, source: "NOTIFICATION_REPLY", via_voice: false, meal_type: "BREAKFAST", meal_type_provider: null, enrichment: "NOT_REQUESTED" }),
    rec("food_entry", id("food"), { raw_text: "Apple and a handful of almonds", eaten_at: at(10, 9, 15), eaten_at_zone: "Asia/Dubai", photo_file: null, source: "QS_TILE", via_voice: false, meal_type: "SNACK", meal_type_provider: null, enrichment: "NOT_REQUESTED" }),
    rec("delegation", delegation, { task_id: delegTask.id, mode: "AGENTIC", agent: "HERMES", status: "NEEDS_APPROVAL", remote_ref: null, result: null, auto_complete: false }),
    rec("approval_request", id("appr"), {
      delegation_id: delegation, action: "send_email",
      draft: "Hi Khalid,\n\nCould the AC service be booked for this Saturday morning? Any time after 9 works for me.\n\nThanks,\nNour",
      recipients: JSON.stringify(["khalid@example.com"]), editable: "{}", decision: "PENDING", decided_at: null,
    }),
  ];
}
