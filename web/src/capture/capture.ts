// Universal capture on the web: rules first (the TS port of RuleCaptureClassifier), no AI, and a diet fallback
// with "Move to…" choices, like the phone's capture sheet. Every action persists at once.
import type { Model, ModuleKey } from "../model";
import { atLocal, minuteOfDay, str } from "../model";
import { classify, isBirthday, type CandidateAction, type ModuleId } from "./rules";
import { parseNaturalTime, plusDays, type LocalDateTime } from "./natural-time";
import { uuidV7 } from "../data/ids";

export interface CaptureResult {
  module: ModuleId;
  /** Rows written, so the capture can be moved or undone. */
  written: { table: string; id: string }[];
  /** Other places this text could go. */
  alternatives: CandidateAction[];
}

const ACTION_FOR: Record<ModuleId, string> = {
  diet: "log_food", tasks: "add_task", reminders: "add_reminder", calendar: "add_event",
  contacts: "add_birthday", birthdays: "add_birthday", workout: "log_workout", sleep: "log_sleep",
};

export function localNow(ms: number): LocalDateTime {
  const t = new Date(ms);
  return { date: { year: t.getFullYear(), month: t.getMonth() + 1, day: t.getDate() }, hour: t.getHours(), minute: t.getMinutes() };
}

/** Modules switched on that can take a capture. */
export function captureModules(model: Model): ModuleId[] {
  const out: ModuleId[] = [];
  for (const m of ["diet", "tasks", "reminders", "calendar", "contacts", "birthdays", "workout", "sleep"] as ModuleKey[]) {
    if (model.isOn(m)) out.push(m as ModuleId);
  }
  return out;
}

export async function capture(model: Model, text: string, target?: CandidateAction): Promise<CaptureResult> {
  const present = captureModules(model);
  const candidates = classify(text, present, localNow(model.now())) ?? [];
  // Birthday text never goes anywhere but a rule's answer; without one it becomes a dated task (Android's rule).
  const chosen = target ?? candidates[0] ?? (isBirthday(text) && present.includes("tasks") ? { moduleId: "tasks", actionType: "add_task", confidence: 0.5 } as CandidateAction : null);
  const fallback: CandidateAction = { moduleId: present.includes("diet") ? "diet" : (present[0] ?? "diet"), actionType: "log_food", confidence: 0 };
  const action = chosen ?? fallback;
  const written = await execute(model, text, action);
  const alternatives = present
    .filter((m) => m !== action.moduleId && !(m === "birthdays" && present.includes("contacts")))
    .map((m) => ({ moduleId: m, actionType: ACTION_FOR[m], confidence: 0 }));
  return { module: action.moduleId, written, alternatives };
}

/** Undo a capture: tombstone what it wrote. */
export async function undo(model: Model, r: CaptureResult) {
  for (const w of r.written) await model.store.remove(w.table, w.id);
}

async function execute(model: Model, text: string, a: CandidateAction): Promise<{ table: string; id: string }[]> {
  const now = model.now();
  const p = parseNaturalTime(text, localNow(now));
  const title = p.rest || text.trim();
  switch (a.actionType) {
    case "add_task": {
      const id = await model.addTask({ title, dueDate: p.date, dueMinutes: p.minutes, source: "CAPTURE" });
      return [{ table: "task", id }];
    }
    case "add_reminder": {
      const at = p.date && p.minutes !== null ? atLocal(p.date, p.minutes) : now + 60 * 60_000;
      const t = title.replace(/^remind me\s+(?:to\s+)?/i, "").trim() || title;
      return [{ table: "reminder", id: await model.addReminder(cap(t), at) }];
    }
    case "add_event": {
      const date = p.date ?? model.today();
      const allDay = p.minutes === null;
      const start = allDay ? atLocal(date) : atLocal(date, p.minutes!);
      const end = allDay ? atLocal(plusDays(date, 1)) : start + 60 * 60_000;
      return [{ table: "event", id: await model.addEvent({ title: cap(title), start, end, allDay }) }];
    }
    case "add_birthday": {
      const name = birthdayName(text);
      if (!p.date || !name) {
        const id = await model.addTask({ title, dueDate: p.date, dueMinutes: null, source: "CAPTURE" });
        return [{ table: "task", id }];
      }
      const existing = model.contacts().find((c) => (str(c.display_name) ?? "").toLowerCase() === name.toLowerCase());
      if (existing) {
        await model.store.write("contact", str(existing.id)!, { birthday_month: p.date.month, birthday_day: p.date.day });
        return [];
      }
      return [{ table: "contact", id: await model.addContact({ name, birthday: { month: p.date.month, day: p.date.day } }) }];
    }
    case "log_workout": {
      const id = await model.startSession(text.trim(), "CAPTURE");
      await model.endSession(id);
      return [{ table: "workout_session", id }];
    }
    case "log_sleep": {
      const open = model.asleep();
      if (open || /\b(going to bed|bedtime|went to bed)\b/i.test(text)) {
        await model.toggleSleep();
        return [];
      }
      // "slept…" / "woke up" with no night open: a night from the scheduled bedtime until now.
      const q = model.quietHours();
      const bedDay = q.bed > minuteOfDay(now) ? plusDays(model.today(), -1) : model.today();
      const id = (await model.store.write("sleep_session", uuidV7(now), { bed_at: atLocal(bedDay, q.bed), wake_at: now, source: "MANUAL", notes: null, hc_record_id: null })).id;
      return [{ table: "sleep_session", id }];
    }
    default:
      return [{ table: "food_entry", id: await model.logFood(text) }];
  }
}

const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

/** "Sara's birthday is March 3" → Sara; "bday of Omar 4/5" → Omar. */
export function birthdayName(text: string): string | null {
  const a = /^\s*(.+?)['’]s\s+(?:birthday|b-?day)\b/i.exec(text);
  if (a) return a[1].trim();
  const b = /\b(?:birthday|b-?day)\s+(?:of|for)\s+([\p{L}][\p{L}' -]*?)(?=\s+(?:is|on|\d)|$)/iu.exec(text);
  return b ? b[1].trim() : null;
}
