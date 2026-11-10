import "fake-indexeddb/auto";
import { IDBFactory } from "fake-indexeddb";
import { describe, expect, it } from "vitest";
import { memoryPersistence, openIdb, type Persistence } from "./idb";
import { RecordStore } from "./store";
import { SyncEngine, PUSH_BATCH, type SyncTransport } from "./sync";
import type { PullResponse, SyncRecord } from "./sync-types";
import { lww } from "./sync-types";
import { clearSession, loadSession, saveSession, deviceId } from "../session";

/** A tiny in-process server with the backend's semantics: LWW per (table, id), a global sequence. */
class FakeServer implements SyncTransport {
  rows = new Map<string, { rec: SyncRecord; seq: number }>();
  seq = 0;
  pushes: number[] = [];
  failNext = false;
  async push(_device: string, records: SyncRecord[]) {
    if (this.failNext) { this.failNext = false; throw new TypeError("network down"); }
    this.pushes.push(records.length);
    let accepted = 0;
    for (const r of records) {
      const k = `${r.table}/${r.id}`;
      const cur = this.rows.get(k)?.rec;
      if (lww(cur, r) === r && r !== cur && !(cur && cur.updatedAt === r.updatedAt && cur.deviceId === r.deviceId)) {
        this.rows.set(k, { rec: structuredClone(r), seq: ++this.seq });
        accepted++;
      }
    }
    return { accepted, serverCursor: this.seq };
  }
  async pull(cursor: number, limit = 500): Promise<PullResponse> {
    const all = [...this.rows.values()].filter((x) => x.seq > cursor).sort((a, b) => a.seq - b.seq);
    const page = all.slice(0, limit);
    return { records: page.map((x) => structuredClone(x.rec)), cursor: page.length ? page[page.length - 1].seq : cursor, hasMore: all.length > limit };
  }
}

const phone = (id: string, updatedAt: number, title: string, deleted = false): SyncRecord => ({
  table: "task", id, updatedAt, deviceId: "phone-1", deleted, json: { id, title, updated_at: updatedAt, device_id: "phone-1", deleted },
});

async function setup(db: Persistence = memoryPersistence(), clock = { t: 1000 }) {
  const store = new RecordStore(db, "web-1", () => clock.t);
  await store.load();
  const server = new FakeServer();
  const engine = new SyncEngine(store, db, server, () => clock.t);
  return { db, store, server, engine, clock };
}

describe("RecordStore writes", () => {
  it("stamps rows like SyncStamper and queues them", async () => {
    const { store } = await setup();
    const r = await store.write("task", "t1", { title: "Pay rent" });
    expect(r).toMatchObject({ table: "task", id: "t1", updatedAt: 1000, deviceId: "web-1", deleted: false });
    expect(r.json).toMatchObject({ id: "t1", title: "Pay rent", created_at: 1000, updated_at: 1000, device_id: "web-1", deleted: false });
    expect(store.pending()).toEqual([r]);
  });
  it("keeps created_at and older columns, and always moves updatedAt forward", async () => {
    const { store, clock } = await setup();
    await store.write("task", "t1", { title: "a", notes: "n" });
    clock.t = 500; // the clock went backwards
    const r = await store.write("task", "t1", { title: "b" });
    expect(r.updatedAt).toBe(1001);
    expect(r.json).toMatchObject({ title: "b", notes: "n", created_at: 1000 });
    expect(store.pending()).toHaveLength(1); // coalesced: one queued version per row
  });
  it("remove tombstones instead of deleting", async () => {
    const { store } = await setup();
    await store.write("task", "t1", { title: "a" });
    await store.remove("task", "t1");
    expect(store.rows("task")).toEqual([]);
    expect(store.record("task", "t1")?.deleted).toBe(true);
    expect(store.pending()[0].deleted).toBe(true);
  });
});

describe("pending-writes queue", () => {
  it("survives a reload from IndexedDB", async () => {
    const factory = new IDBFactory();
    const db = await openIdb(factory, "q1");
    const { store } = await setup(db);
    await store.write("task", "t1", { title: "offline" });
    const again = new RecordStore(await openIdb(factory, "q1"), "web-1");
    await again.load();
    expect(again.pending().map((r) => r.id)).toEqual(["t1"]);
    expect(again.get("task", "t1")?.title).toBe("offline");
  });
  it("stays queued when the network is down, and flushes on the next sync", async () => {
    const { store, server, engine } = await setup();
    await store.write("task", "t1", { title: "a" });
    server.failNext = true;
    await engine.sync();
    expect(engine.state).toBe("offline");
    expect(store.pending()).toHaveLength(1);
    await engine.sync();
    expect(engine.state).toBe("idle");
    expect(store.pending()).toHaveLength(0);
    expect(server.rows.get("task/t1")?.rec.json.title).toBe("a");
  });
  it("an edit made while a push is in flight stays queued", async () => {
    const { store } = await setup();
    const first = await store.write("task", "t1", { title: "a" });
    await store.write("task", "t1", { title: "b" });
    await store.acknowledge([first]);
    expect(store.pending().map((r) => r.json.title)).toEqual(["b"]);
  });
  it("pushes in batches", async () => {
    const { store, server, engine } = await setup();
    for (let i = 0; i < PUSH_BATCH + 3; i++) await store.write("task", `t${i}`, { title: String(i) });
    await engine.sync();
    expect(server.pushes).toEqual([PUSH_BATCH, 3]);
    expect(store.pending()).toHaveLength(0);
  });
});

describe("sync", () => {
  it("pulls a phone's task, pages until caught up and keeps the cursor", async () => {
    const { db, store, server, engine } = await setup();
    for (let i = 0; i < 1203; i++) await server.push("phone-1", [phone(`p${i}`, 10 + i, `task ${i}`)]);
    await engine.sync();
    expect(store.rows("task")).toHaveLength(1203);
    expect(await db.getMeta("sync.cursor")).toBe(1203);
  });
  it("merges with LWW: a newer phone edit beats an older local one, an older one does not", async () => {
    const { store, server, engine, clock } = await setup();
    await store.write("task", "t1", { title: "web" }); // updatedAt 1000
    await server.push("phone-1", [phone("t1", 2000, "phone newer")]);
    await engine.sync();
    expect(store.get("task", "t1")?.title).toBe("phone newer");
    expect(store.pending()).toHaveLength(0);
    clock.t = 3000;
    await store.write("task", "t1", { title: "web newest" });
    await server.push("phone-1", [phone("t1", 2500, "phone older")]);
    await engine.sync();
    expect(store.get("task", "t1")?.title).toBe("web newest");
    expect(server.rows.get("task/t1")?.rec.json.title).toBe("web newest");
  });
  it("our own pushes coming back are no-ops", async () => {
    const { store, engine } = await setup();
    await store.write("task", "t1", { title: "a" });
    const v = store.version;
    await engine.sync();
    expect(store.version).toBe(v);
  });
  it("a 401 signs the device out", async () => {
    const { store, engine, server } = await setup();
    let out = false;
    engine.onUnauthorized = () => { out = true; };
    server.push = async () => { throw Object.assign(new Error("revoked"), { status: 401 }); };
    await store.write("task", "t1", { title: "a" });
    await engine.sync();
    expect(out).toBe(true);
  });
});

describe("device token storage", () => {
  it("saves, loads and clears the session in IndexedDB", async () => {
    const factory = new IDBFactory();
    const db = await openIdb(factory, "s1");
    expect(await loadSession(db)).toBeNull();
    const id = await deviceId(db);
    expect(id).toMatch(/^web-[0-9a-f-]{36}$/);
    expect(await deviceId(db)).toBe(id);
    await saveSession(db, { token: "tok", deviceId: id, deviceName: "Laptop" });
    expect(await loadSession(await openIdb(factory, "s1"))).toEqual({ token: "tok", deviceId: id, deviceName: "Laptop" });
    await clearSession(db);
    expect(await loadSession(db)).toBeNull();
  });
});
