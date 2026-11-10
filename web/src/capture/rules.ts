// TypeScript port of :shared app.klean.shared.capture.RuleCaptureClassifier: offline keyword and time-expression
// routing for universal capture. Kept in step with Kotlin by shared/src/commonTest/resources/capture-cases.json.
import { parseNaturalTime, type LocalDateTime } from "./natural-time";

export type ModuleId = "diet" | "tasks" | "reminders" | "calendar" | "contacts" | "birthdays" | "workout" | "sleep";

export const ADD_BIRTHDAY = "add_birthday";
export const ADD_REMINDER = "add_reminder";
export const LOG_SLEEP = "log_sleep";
export const LOG_WORKOUT = "log_workout";
export const ADD_EVENT = "add_event";
export const ADD_TASK = "add_task";
export const LOG_FOOD = "log_food";

export interface CandidateAction { moduleId: ModuleId; actionType: string; confidence: number }

const HIT = 0.9;
// Kotlin uses Float: 0.9f - 0.05f. Rounded to float precision so comparisons agree.
const f32 = (x: number) => Math.fround(x);
const BIRTHDAY = /birthday|\bb-?day\b/i;
const REMINDER = /^remind|\balarm\b|\bwake me\b/i;
const SLEEP = /\bslept\b|\bwent to bed\b|\bwoke up\b|\bgoing to bed\b|\bbedtime\b/i;
const WORKOUT = /\bgym\b|\bworkout\b|\bpush-?ups?\b|\bsquats?\b|\bplank\b|\bran \d|\brun \d|\bkm run\b|\blifted\b/i;
const PAST_TENSE = /\b(?:did|done|ran|lifted|finished|completed)\b/i;
const FOOD = /\b(?:ate|eat|eating|breakfast|lunch|dinner|snack|brunch|coffee|tea|drank|drink|meal)\b/i;
const TASK = /^todo\b|^to do\b|\bneed to\b|\bhave to\b|\bbuy |\bcall |\bemail |\bpay /i;

/** True for birthday phrases; these are resolved by rules alone and never reach an AI provider. */
export const isBirthday = (text: string) => BIRTHDAY.test(text);

/**
 * Candidate actions for `text`, best first, limited to the modules in `present`; null when no rule matches
 * (capture then falls back to diet with "Move to…" chips).
 */
export function classify(text: string, present: Iterable<ModuleId>, now: LocalDateTime): CandidateAction[] | null {
  const have = new Set(present);
  const pick = (...c: CandidateAction[]) => {
    const r = c.filter((x) => have.has(x.moduleId));
    return r.length ? r : null;
  };
  const c = (moduleId: ModuleId, actionType: string, confidence: number): CandidateAction => ({ moduleId, actionType, confidence: f32(confidence) });

  const t = text.trim();
  let hit: CandidateAction[] | null;
  if (isBirthday(t) && (hit = pick(c("contacts", ADD_BIRTHDAY, HIT), c("birthdays", ADD_BIRTHDAY, f32(f32(HIT) - f32(0.05)))))) return hit;
  if (REMINDER.test(t) && (hit = pick(c("reminders", ADD_REMINDER, HIT)))) return hit;
  if (SLEEP.test(t) && (hit = pick(c("sleep", LOG_SLEEP, HIT)))) return hit;

  const parsed = parseNaturalTime(t, now);
  if (WORKOUT.test(t)) {
    const planned = parsed.date !== null && !PAST_TENSE.test(t);
    hit = planned ? pick(c("calendar", ADD_EVENT, 0.8), c("workout", LOG_WORKOUT, 0.5)) : pick(c("workout", LOG_WORKOUT, HIT));
    if (hit) return hit;
  }
  if (parsed.date !== null && !FOOD.test(t)) {
    hit = parsed.minutes !== null ? pick(c("calendar", ADD_EVENT, 0.7)) : pick(c("tasks", ADD_TASK, 0.7));
    if (hit) return hit;
  }
  if (TASK.test(t) && (hit = pick(c("tasks", ADD_TASK, 0.8)))) return hit;
  return null;
}
