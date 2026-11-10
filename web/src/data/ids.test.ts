import { createHash } from "node:crypto";
import { describe, expect, it } from "vitest";
import { md5, nameUuid, timestampOf, uuidV7 } from "./ids";

const hex = (b: Uint8Array) => [...b].map((x) => x.toString(16).padStart(2, "0")).join("");

describe("ids", () => {
  it("md5 matches node's", () => {
    for (const s of ["", "a", "klean:core.modules.disabled", "x".repeat(55), "y".repeat(56), "z".repeat(200), "ünïcødé"]) {
      expect(hex(md5(new TextEncoder().encode(s)))).toBe(createHash("md5").update(s, "utf8").digest("hex"));
    }
  });
  it("nameUuid is Java's UUID.nameUUIDFromBytes of klean:<name>", () => {
    // java.util.UUID.nameUUIDFromBytes("klean:core.modules.disabled".toByteArray()).toString()
    const h = createHash("md5").update("klean:core.modules.disabled").digest();
    h[6] = (h[6] & 0x0f) | 0x30;
    h[8] = (h[8] & 0x3f) | 0x80;
    const s = h.toString("hex");
    expect(nameUuid("core.modules.disabled")).toBe(`${s.slice(0, 8)}-${s.slice(8, 12)}-${s.slice(12, 16)}-${s.slice(16, 20)}-${s.slice(20)}`);
    expect(nameUuid("core.modules.disabled")[14]).toBe("3");
  });
  it("uuidV7 carries the timestamp and the version and variant bits", () => {
    const id = uuidV7(1_762_000_000_123, (n) => new Uint8Array(n).fill(0xff));
    expect(timestampOf(id)).toBe(1_762_000_000_123);
    expect(id[14]).toBe("7");
    expect("89ab").toContain(id[19]);
    expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  });
  it("uuidV7 sorts by time", () => {
    expect(uuidV7(1000) < uuidV7(2000)).toBe(true);
  });
});
