import { describe, expect, it } from "vitest";
import { addMenuEntries, navGroups, resolve, loadLast, saveLast } from "./nav";
import { MODULES, type ModuleKey } from "./model";

const on = (...ms: ModuleKey[]) => (m: ModuleKey) => ms.includes(m);
const all = on(...MODULES);

describe("navGroups", () => {
  it("gives four groups in order with every module on", () => {
    const g = navGroups(all);
    expect(g.map((x) => x.id)).toEqual(["today", "plan", "track", "people"]);
    expect(g.find((x) => x.id === "plan")!.dests).toEqual(["tasks", "calendar"]);
    expect(g.find((x) => x.id === "track")!.dests).toEqual(["week", "workout", "sleep", "diet"]);
    expect(g.find((x) => x.id === "people")!.dests).toEqual(["contacts", "birthdays"]);
  });

  it("drops a group with nothing on", () => {
    expect(navGroups(on("tasks", "calendar")).map((x) => x.id)).toEqual(["today", "plan"]);
  });

  it("never has a More destination", () => {
    expect(navGroups(all).some((x) => (x.id as string) === "more")).toBe(false);
  });

  it("shows Track's Week only with more than one tracker", () => {
    expect(navGroups(on("sleep")).find((x) => x.id === "track")!.dests).toEqual(["sleep"]);
    expect(navGroups(on("sleep", "diet")).find((x) => x.id === "track")!.dests).toEqual(["week", "sleep", "diet"]);
  });

  it("keeps Plan's Tasks for reminders alone", () => {
    expect(navGroups(on("reminders")).find((x) => x.id === "plan")!.dests).toEqual(["tasks"]);
  });
});

describe("resolve", () => {
  it("opens a group on its last module, else its first", () => {
    expect(resolve("/plan", all)).toEqual({ kind: "dest", group: "plan", dest: "tasks" });
    expect(resolve("/plan", all, { plan: "calendar" })).toEqual({ kind: "dest", group: "plan", dest: "calendar" });
    expect(resolve("/track", all)).toEqual({ kind: "dest", group: "track", dest: "week" });
  });

  it("maps the old single-module paths", () => {
    expect(resolve("/calendar", all)).toEqual({ kind: "dest", group: "plan", dest: "calendar" });
    expect(resolve("/diet", all)).toEqual({ kind: "dest", group: "track", dest: "diet" });
    expect(resolve("/more", all)).toEqual({ kind: "today" });
  });

  it("falls back when a module is off", () => {
    expect(resolve("/plan/calendar", on("tasks"))).toEqual({ kind: "dest", group: "plan", dest: "tasks" });
    expect(resolve("/track/diet", on("tasks"))).toEqual({ kind: "today" });
  });

  it("knows Settings and Approvals", () => {
    expect(resolve("/settings", all)).toEqual({ kind: "settings" });
    expect(resolve("/approvals", all)).toEqual({ kind: "approvals" });
  });
});

describe("addMenuEntries", () => {
  it("lists typing first and hides modules that are off", () => {
    expect(addMenuEntries(all)).toEqual(["type", "sleep", "workout", "food", "reminder", "event", "task"]);
    expect(addMenuEntries(on("tasks", "diet"))).toEqual(["type", "food", "task"]);
  });
});

describe("last module per group", () => {
  it("is remembered per group", () => {
    const m = new Map<string, string>();
    const s = { getItem: (k: string) => m.get(k) ?? null, setItem: (k: string, v: string) => void m.set(k, v) };
    saveLast("plan", "calendar", s);
    saveLast("track", "sleep", s);
    expect(loadLast(s)).toEqual({ plan: "calendar", track: "sleep" });
  });
});
