// Grouped navigation (UI plan, decision A), the web twin of Android's NavGroup: Today · Plan · Track · People.
// Pure functions of "which modules are on", so routing, the rail, the bar and the Add menu all agree.
import type { ModuleKey } from "./model";

export type GroupId = "today" | "plan" | "track" | "people";
export type Dest = "tasks" | "calendar" | "week" | "workout" | "sleep" | "diet" | "contacts" | "birthdays";

/** A destination inside a group, and the modules that make it worth showing (any of them on). */
interface Member { dest: Dest; needs: ModuleKey[] }

const TRACKERS: ModuleKey[] = ["workout", "sleep", "diet"];

export const GROUPS: { id: GroupId; members: Member[] }[] = [
  { id: "today", members: [] },
  { id: "plan", members: [{ dest: "tasks", needs: ["tasks", "reminders"] }, { dest: "calendar", needs: ["calendar"] }] },
  {
    id: "track",
    members: [
      // The neutral overview of all trackers; only worth a segment when there is more than one tracker.
      { dest: "week", needs: TRACKERS },
      { dest: "workout", needs: ["workout"] },
      { dest: "sleep", needs: ["sleep"] },
      { dest: "diet", needs: ["diet"] },
    ],
  },
  { id: "people", members: [{ dest: "contacts", needs: ["contacts"] }, { dest: "birthdays", needs: ["birthdays"] }] },
];

export interface NavGroupEntry { id: GroupId; dests: Dest[] }

/** Today always; every other group only when at least one of its destinations has a module on. No "More". */
export function navGroups(isOn: (m: ModuleKey) => boolean): NavGroupEntry[] {
  return GROUPS.flatMap((g): NavGroupEntry[] => {
    if (g.id === "today") return [{ id: g.id, dests: [] }];
    let dests = g.members.filter((m) => m.needs.some(isOn)).map((m) => m.dest);
    if (g.id === "track" && dests.length <= 2) dests = dests.filter((d) => d !== "week");
    return dests.length ? [{ id: g.id, dests }] : [];
  });
}

export const groupOf = (d: Dest): GroupId => GROUPS.find((g) => g.members.some((m) => m.dest === d))!.id;

/** Where a path lands. Old single-module paths (/tasks, /calendar, …) and /more still work. */
export type Resolved =
  | { kind: "today" }
  | { kind: "settings"; section?: string }
  | { kind: "approvals" }
  | { kind: "dest"; group: GroupId; dest: Dest };

const LEGACY: Record<string, string> = {
  "/tasks": "/plan/tasks", "/calendar": "/plan/calendar", "/contacts": "/people/contacts",
  "/workout": "/track/workout", "/sleep": "/track/sleep", "/diet": "/track/diet", "/more": "/",
};

export function resolve(path: string, isOn: (m: ModuleKey) => boolean, last: Partial<Record<GroupId, Dest>> = {}): Resolved {
  const p = LEGACY[path] ?? path.replace(/\/+$/, "");
  if (p === "" || p === "/") return { kind: "today" };
  if (p === "/approvals") return { kind: "approvals" };
  if (p === "/settings") return { kind: "settings" };
  const [, g, d] = p.split("/");
  const entry = navGroups(isOn).find((x) => x.id === g);
  if (!entry || entry.id === "today") return { kind: "today" };
  const dest = (entry.dests as string[]).includes(d) ? (d as Dest)
    : last[entry.id] && entry.dests.includes(last[entry.id]!) ? last[entry.id]!
    : entry.dests[0];
  return { kind: "dest", group: entry.id, dest };
}

export const pathOf = (r: Resolved): string =>
  r.kind === "dest" ? `/${r.group}/${r.dest}` : r.kind === "today" ? "/" : `/${r.kind}`;

// ---- the Add menu ----

export type AddEntry = "type" | "sleep" | "workout" | "food" | "reminder" | "event" | "task";
const ADD_NEEDS: Record<Exclude<AddEntry, "type">, ModuleKey> = {
  sleep: "sleep", workout: "workout", food: "diet", reminder: "reminders", event: "calendar", task: "tasks",
};

/** "Type something…" first, then one targeted line per module that is on (Android's AddMenu order). */
export function addMenuEntries(isOn: (m: ModuleKey) => boolean): AddEntry[] {
  return ["type", ...(Object.keys(ADD_NEEDS) as Exclude<AddEntry, "type">[]).filter((e) => isOn(ADD_NEEDS[e]))];
}

// ---- the last module per group, remembered on this device ----

const LAST_KEY = "klean.nav.last";
export function loadLast(storage: Pick<Storage, "getItem"> = localStorage): Partial<Record<GroupId, Dest>> {
  try { return JSON.parse(storage.getItem(LAST_KEY) ?? "{}") ?? {}; } catch { return {}; }
}
export function saveLast(group: GroupId, dest: Dest, storage: Pick<Storage, "getItem" | "setItem"> = localStorage) {
  storage.setItem(LAST_KEY, JSON.stringify({ ...loadLast(storage), [group]: dest }));
}
