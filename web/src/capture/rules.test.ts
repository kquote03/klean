import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { classify, isBirthday, type ModuleId } from "./rules";
import { isoDate, parseLocalDateTime, parseNaturalTime } from "./natural-time";

// The same fixture the Kotlin CaptureCasesFixtureTest (:shared jvmTest) reads.
interface Fixture {
  now: string;
  optionSets: Record<string, ModuleId[]>;
  capture: { text: string; options: string | ModuleId[]; expect: [ModuleId, string, number][] | null }[];
  time: { text: string; now?: string; date: string | null; minutes: number | null; rest: string }[];
}
const fixture: Fixture = JSON.parse(
  readFileSync(fileURLToPath(new URL("../../../shared/src/commonTest/resources/capture-cases.json", import.meta.url)), "utf8"),
);
const now = parseLocalDateTime(fixture.now);

describe("capture rules (shared fixture)", () => {
  it("has the whole table", () => expect(fixture.capture.length).toBeGreaterThanOrEqual(30));
  for (const c of fixture.capture) {
    const opts = typeof c.options === "string" ? fixture.optionSets[c.options] : c.options;
    it(`${JSON.stringify(c.text)} with ${typeof c.options === "string" ? c.options : c.options.join("+")}`, () => {
      const got = classify(c.text, opts, now);
      if (c.expect === null) return expect(got).toBeNull();
      expect(got?.map((x) => [x.moduleId, x.actionType])).toEqual(c.expect.map(([m, a]) => [m, a]));
      got!.forEach((x, i) => expect(x.confidence).toBeCloseTo(c.expect![i][2], 4));
    });
  }
  it("birthday phrases are recognised for the never-AI rule", () => {
    expect(isBirthday("it's Omar's bday")).toBe(true);
    expect(isBirthday("BDAY of Omar 4/5")).toBe(true);
    expect(isBirthday("buy a bead")).toBe(false);
  });
});

describe("NaturalTime (shared fixture)", () => {
  it("has the whole table", () => expect(fixture.time.length).toBeGreaterThanOrEqual(30));
  for (const c of fixture.time) {
    it(`${JSON.stringify(c.text)}${c.now ? " at " + c.now : ""}`, () => {
      const p = parseNaturalTime(c.text, c.now ? parseLocalDateTime(c.now) : now);
      expect({ date: p.date && isoDate(p.date), minutes: p.minutes, rest: p.rest }).toEqual({ date: c.date, minutes: c.minutes, rest: c.rest });
    });
  }
});
