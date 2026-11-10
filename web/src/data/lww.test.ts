import { describe, expect, it } from "vitest";
import { lww, type SyncRecord } from "./sync-types";

// The same table as shared/src/commonTest/.../sync/LwwTest.kt, ported.
const rec = (updatedAt: number, deviceId: string, title = "x", deleted = false): SyncRecord => ({
  table: "task", id: "t1", updatedAt, deviceId, deleted, json: { id: "t1", title },
});

describe("lww", () => {
  it("takes the remote when there is no local version", () => {
    const r = rec(1, "a");
    expect(lww(null, r)).toBe(r);
    expect(lww(undefined, r)).toBe(r);
  });
  it("newer updatedAt wins either way round", () => {
    const old = rec(1, "z"), neu = rec(2, "a");
    expect(lww(old, neu)).toBe(neu);
    expect(lww(neu, old)).toBe(neu);
  });
  it("ties go to the lexically larger deviceId, in any order", () => {
    const a = rec(5, "phone-a"), b = rec(5, "phone-b");
    expect(lww(a, b)).toBe(b);
    expect(lww(b, a)).toBe(b);
  });
  it("an identical version keeps local (re-push is a no-op)", () => {
    const l = rec(5, "web", "local"), r = rec(5, "web", "remote");
    expect(lww(l, r)).toBe(l);
  });
  it("compares deviceIds by UTF-16 code units, like Kotlin", () => {
    // "Z" (0x5A) < "a" (0x61): uppercase sorts first, unlike localeCompare.
    const upper = rec(5, "Zed"), lower = rec(5, "abe");
    expect(lww(upper, lower)).toBe(lower);
    expect(lww(lower, upper)).toBe(lower);
  });
  it("a newer tombstone beats an older live edit, and an older live edit never resurrects it", () => {
    const live = rec(1, "a"), tomb = rec(2, "a", "x", true);
    expect(lww(live, tomb)).toBe(tomb);
    expect(lww(tomb, live)).toBe(tomb);
  });
  it("converges: every permutation of versions picks one winner", () => {
    const versions = [rec(3, "b"), rec(3, "c"), rec(2, "z"), rec(3, "a"), rec(1, "y")];
    const winners = new Set<SyncRecord>();
    const perms = (xs: SyncRecord[]): SyncRecord[][] =>
      xs.length <= 1 ? [xs] : xs.flatMap((x, i) => perms([...xs.slice(0, i), ...xs.slice(i + 1)]).map((p) => [x, ...p]));
    for (const p of perms(versions)) winners.add(p.reduce<SyncRecord | null>((acc, r) => lww(acc, r), null)!);
    expect([...winners]).toEqual([versions[1]]);
  });
});
