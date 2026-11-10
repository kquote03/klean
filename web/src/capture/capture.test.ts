import { describe, expect, it } from "vitest";
import { memoryPersistence } from "../data/idb";
import { RecordStore } from "../data/store";
import { Model, PERSONAL_LIST_ID } from "../model";
import { capture, undo, birthdayName } from "./capture";
import { nameUuid } from "../data/ids";

// 2026-11-09 10:00 local (a Monday).
const NOW = new Date(2026, 10, 9, 10, 0).getTime();

async function model() {
  const store = new RecordStore(memoryPersistence(), "web-1", () => NOW);
  await store.load();
  return new Model(store, () => NOW);
}

describe("universal capture on the web", () => {
  it("a task phrase becomes a dated task in the Personal list (Android's seeded id)", async () => {
    const m = await model();
    const r = await capture(m, "pay rent friday");
    expect(r.module).toBe("tasks");
    const task = m.store.get("task", r.written[0].id)!;
    expect(task).toMatchObject({ title: "pay rent", due_date: "2026-11-13", due_minutes: null, status: "OPEN", source: "CAPTURE", list_id: PERSONAL_LIST_ID, origin: "KLEAN" });
    expect(PERSONAL_LIST_ID).toBe(nameUuid("tasks.list.personal"));
    expect(m.store.get("task_list", PERSONAL_LIST_ID)?.name).toBe("Personal");
  });
  it("a timed phrase becomes an event, a reminder phrase a reminder", async () => {
    const m = await model();
    const e = await capture(m, "dentist friday 9:30");
    expect(e.module).toBe("calendar");
    const ev = m.store.get("event", e.written[0].id)!;
    expect(ev.start_at).toBe(new Date(2026, 10, 13, 9, 30).getTime());
    expect(ev.end_at).toBe(new Date(2026, 10, 13, 10, 30).getTime());
    const r = await capture(m, "remind me at 5 to call Ahmed");
    expect(m.store.get("reminder", r.written[0].id)).toMatchObject({ title: "Call Ahmed", fire_at: new Date(2026, 10, 9, 17, 0).getTime(), state: "SCHEDULED" });
  });
  it("a birthday phrase saves the contact's birthday", async () => {
    const m = await model();
    const r = await capture(m, "Sara's birthday is March 3");
    expect(r.module).toBe("contacts");
    expect(m.store.get("contact", r.written[0].id)).toMatchObject({ display_name: "Sara", birthday_month: 3, birthday_day: 3 });
    expect(birthdayName("bday of Omar 4/5")).toBe("Omar");
  });
  it("no rule match falls back to diet, with Move to… alternatives, and can be undone", async () => {
    const m = await model();
    const r = await capture(m, "2 manakish and tea");
    expect(r.module).toBe("diet");
    expect(r.alternatives.map((a) => a.moduleId)).toContain("tasks");
    expect(m.foodToday()).toHaveLength(1);
    await undo(m, r);
    expect(m.foodToday()).toHaveLength(0);
  });
  it("a switched-off module is never a target", async () => {
    const m = await model();
    await m.setModuleOn("calendar", false);
    const r = await capture(m, "gym tomorrow 6pm");
    expect(r.module).toBe("workout");
    expect(m.setting("core.modules.disabled", [])).toEqual(["calendar"]);
  });
});

describe("model writes", () => {
  it("completing a repeating task rolls the next one forward", async () => {
    const m = await model();
    const id = await m.addTask({ title: "Water plants", dueDate: { year: 2026, month: 11, day: 9 }, recurrence: "FREQ=WEEKLY;COUNT=3" });
    const next = await m.completeTask(id);
    expect(m.store.get("task", id)).toMatchObject({ status: "DONE", completed_at: NOW });
    expect(m.store.get("task", next!)).toMatchObject({ title: "Water plants", due_date: "2026-11-16", recurrence: "FREQ=WEEKLY;COUNT=2", status: "OPEN" });
  });
  it("approving with an edited draft records EDITED and the decision time", async () => {
    const m = await model();
    await m.store.write("approval_request", "a1", { delegation_id: "d1", action: "send_email", draft: "Hi", recipients: '["a@b.c"]', editable: "{}", decision: "PENDING", decided_at: null });
    await m.decide("a1", true, "Hi there", ["a@b.c"]);
    expect(m.store.get("approval_request", "a1")).toMatchObject({ decision: "EDITED", decided_at: NOW, draft: "Hi there" });
    await m.store.write("approval_request", "a2", { delegation_id: "d1", action: "send_email", draft: "Hi", recipients: "[]", decision: "PENDING" });
    await m.decide("a2", true, "Hi", []);
    expect(m.store.get("approval_request", "a2")?.decision).toBe("APPROVED");
    await m.decide("a2", false);
    expect(m.store.get("approval_request", "a2")?.decision).toBe("REJECTED");
    expect(m.approvals()).toHaveLength(0);
  });
  it("quiet hours write the sleep schedule and the older core.quietHours key", async () => {
    const m = await model();
    await m.setQuietHours(23 * 60, 7 * 60);
    expect(m.quietHours()).toEqual({ bed: 1380, wake: 420 });
    expect(m.setting("core.quietHours", null)).toEqual({ startMinute: 1380, endMinute: 420 });
    expect(m.store.get("sleep_schedule", nameUuid("sleep.schedule.primary"))).toMatchObject({ days: 127, reminder_minutes_before: 30 });
  });
});
