import { describe, expect, it } from "vitest";
// @ts-expect-error plain ESM build script without types
import { problems, templateText, stringLiterals } from "../scripts/check-copy.mjs";
import { t } from "./copy";

describe("copy guard", () => {
  it("catches exclamation marks, guilt words and medical labels", () => {
    for (const bad of ["Great job!", "You missed lunch", "3-day streak", "You're behind", "Forgot to log?", "Overdue", "ADHD tips", "autistic", "ADD"]) {
      expect(problems(bad), bad).not.toEqual([]);
    }
  });
  it("allows calm copy, including the verb Add", () => {
    for (const ok of ["Add something", "Still want these?", "Behold", "Nothing yet today", "Address"]) expect(problems(ok), ok).toEqual([]);
  });
  it("finds strings and template text", () => {
    expect(stringLiterals('const a = "Hello"; const b = `x ${y} z`;')).toEqual(["Hello", "x ", " z"]);
    expect(templateText("html`<p>${a}Calm text</p><b>Two</b>`")).toEqual(["Calm text", "Two"]);
  });
  it("every string in copy.ts is calm", () => {
    const walk = (v: unknown): string[] => typeof v === "string" ? [v] : typeof v === "function" ? [String((v as (n: number) => string)(2)), String((v as (n: number) => string)(1))]
      : v && typeof v === "object" ? Object.values(v).flatMap(walk) : [];
    const all = walk(t);
    expect(all.length).toBeGreaterThan(150);
    expect(all.flatMap((s) => problems(s).map((p: string) => `${p}: ${s}`))).toEqual([]);
  });
});
