import { describe, expect, it } from "vitest";
import { formatRule, nextOccurrence, occurrences, parseRule, remainingAfterOne, type RecurrenceRule } from "./recurrence";
import type { LocalDate } from "../capture/natural-time";

// Ported from shared/src/commonTest/.../time/RecurrenceRuleTest.kt.
const d = (year: number, month: number, day: number): LocalDate => ({ year, month, day });
const rule = (t: string): RecurrenceRule => {
  const r = parseRule(t);
  expect(r, t).not.toBeNull();
  return r!;
};
const monday = d(2026, 11, 9);

describe("RecurrenceRule port", () => {
  it("every weekday from Monday", () => {
    const r = rule("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR");
    const got: LocalDate[] = [monday];
    while (got.length < 7) got.push(nextOccurrence(r, got[got.length - 1])!);
    expect(got).toEqual([d(2026, 11, 9), d(2026, 11, 10), d(2026, 11, 11), d(2026, 11, 12), d(2026, 11, 13), d(2026, 11, 16), d(2026, 11, 17)]);
  });
  it("monthly on Jan 31 clamps, then returns to the 31st", () => {
    const r = rule("FREQ=MONTHLY;BYMONTHDAY=31");
    expect(nextOccurrence(r, d(2026, 1, 31))).toEqual(d(2026, 2, 28));
    expect(nextOccurrence(r, d(2026, 2, 28))).toEqual(d(2026, 3, 31));
    expect(nextOccurrence(r, d(2026, 3, 31))).toEqual(d(2026, 4, 30));
    expect(nextOccurrence(rule("FREQ=MONTHLY"), d(2026, 2, 28), d(2026, 1, 31))).toEqual(d(2026, 3, 31));
  });
  it("every other week, also on two days", () => {
    expect(nextOccurrence(rule("FREQ=WEEKLY;INTERVAL=2"), monday)).toEqual(d(2026, 11, 23));
    expect(occurrences(rule("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TH"), monday, monday, d(2026, 12, 7)))
      .toEqual([d(2026, 11, 9), d(2026, 11, 12), d(2026, 11, 23), d(2026, 11, 26), d(2026, 12, 7)]);
  });
  it("COUNT and UNTIL", () => {
    const r = rule("FREQ=DAILY;COUNT=3");
    expect(nextOccurrence(r, d(2026, 11, 10), monday)).toEqual(d(2026, 11, 11));
    expect(nextOccurrence(r, d(2026, 11, 11), monday)).toBeNull();
    expect(nextOccurrence(rule("FREQ=DAILY;COUNT=1"), monday)).toBeNull();
    expect(formatRule(remainingAfterOne(r)!)).toBe("FREQ=DAILY;COUNT=2");
    expect(remainingAfterOne(rule("FREQ=DAILY;COUNT=1"))).toBeNull();
    const u = rule("FREQ=DAILY;UNTIL=20261111");
    expect(nextOccurrence(u, d(2026, 11, 10))).toEqual(d(2026, 11, 11));
    expect(nextOccurrence(u, d(2026, 11, 11))).toBeNull();
    expect(rule("FREQ=WEEKLY;UNTIL=20261130T235959Z").until).toEqual(d(2026, 11, 30));
  });
  it("yearly, nth weekday and last day", () => {
    expect(nextOccurrence(rule("FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=29"), d(2028, 2, 29))).toEqual(d(2029, 2, 28));
    expect(nextOccurrence(rule("FREQ=YEARLY;BYMONTH=3;BYMONTHDAY=3"), monday)).toEqual(d(2027, 3, 3));
    expect(nextOccurrence(rule("FREQ=MONTHLY;BYDAY=2MO"), monday)).toEqual(d(2026, 12, 14));
    expect(nextOccurrence(rule("FREQ=MONTHLY;BYDAY=-1MO"), monday)).toEqual(d(2026, 11, 30));
    expect(nextOccurrence(rule("FREQ=MONTHLY;BYMONTHDAY=-1"), d(2027, 1, 31))).toEqual(d(2027, 2, 28));
    expect(nextOccurrence(rule("FREQ=DAILY;BYDAY=SA,SU"), monday)).toEqual(d(2026, 11, 14));
  });
  it("calendars skip missing month days", () => {
    expect(occurrences(rule("FREQ=MONTHLY"), d(2026, 1, 31), d(2026, 1, 1), d(2026, 4, 30), "skip"))
      .toEqual([d(2026, 1, 31), d(2026, 3, 31)]);
  });
  it("refuses what it can't follow, and formats canonically", () => {
    for (const t of ["", "FREQ=HOURLY", "FREQ=DAILY;BYHOUR=3", "FREQ=WEEKLY;BYDAY=2MO", "FREQ=DAILY;COUNT=2;UNTIL=20270101", "FREQ=DAILY;INTERVAL=0"]) {
      expect(parseRule(t), t).toBeNull();
    }
    expect(formatRule(rule("rrule:freq=weekly;byday=fr,mo;interval=2"))).toBe("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,FR");
  });
});
