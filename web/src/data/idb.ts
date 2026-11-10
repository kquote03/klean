// IndexedDB persistence: the read cache (every synced record), the pending-writes queue and small metadata
// (the device token, the sync cursor). One database, three object stores. Works with fake-indexeddb in tests.
import type { SyncRecord } from "./sync-types";

export interface Persistence {
  loadRecords(): Promise<SyncRecord[]>;
  putRecords(records: SyncRecord[]): Promise<void>;
  loadQueue(): Promise<SyncRecord[]>;
  putQueue(records: SyncRecord[]): Promise<void>;
  deleteQueue(keys: string[]): Promise<void>;
  getMeta<T>(key: string): Promise<T | undefined>;
  setMeta(key: string, value: unknown): Promise<void>;
  deleteMeta(key: string): Promise<void>;
  /** Forget everything (sign out). */
  clear(): Promise<void>;
}

const DB_NAME = "klean";
const VERSION = 1;
const RECORDS = "records";
const QUEUE = "queue";
const META = "meta";

const req = <T>(r: IDBRequest<T>) =>
  new Promise<T>((resolve, reject) => {
    r.onsuccess = () => resolve(r.result);
    r.onerror = () => reject(r.error);
  });

const done = (tx: IDBTransaction) =>
  new Promise<void>((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onabort = tx.onerror = () => reject(tx.error);
  });

export const recordKey = (r: Pick<SyncRecord, "table" | "id">) => `${r.table}/${r.id}`;

export async function openIdb(factory: IDBFactory = indexedDB, name = DB_NAME): Promise<Persistence> {
  const open = factory.open(name, VERSION);
  open.onupgradeneeded = () => {
    const db = open.result;
    if (!db.objectStoreNames.contains(RECORDS)) db.createObjectStore(RECORDS);
    if (!db.objectStoreNames.contains(QUEUE)) db.createObjectStore(QUEUE);
    if (!db.objectStoreNames.contains(META)) db.createObjectStore(META);
  };
  const db = await req(open);

  async function putAll(store: string, records: SyncRecord[]) {
    if (!records.length) return;
    const tx = db.transaction(store, "readwrite");
    const s = tx.objectStore(store);
    for (const r of records) s.put(r, recordKey(r));
    await done(tx);
  }
  async function getAll(store: string): Promise<SyncRecord[]> {
    const tx = db.transaction(store, "readonly");
    return req(tx.objectStore(store).getAll() as IDBRequest<SyncRecord[]>);
  }

  return {
    loadRecords: () => getAll(RECORDS),
    putRecords: (r) => putAll(RECORDS, r),
    loadQueue: () => getAll(QUEUE),
    putQueue: (r) => putAll(QUEUE, r),
    async deleteQueue(keys) {
      if (!keys.length) return;
      const tx = db.transaction(QUEUE, "readwrite");
      for (const k of keys) tx.objectStore(QUEUE).delete(k);
      await done(tx);
    },
    async getMeta<T>(key: string) {
      const tx = db.transaction(META, "readonly");
      return (await req(tx.objectStore(META).get(key))) as T | undefined;
    },
    async setMeta(key, value) {
      const tx = db.transaction(META, "readwrite");
      tx.objectStore(META).put(value, key);
      await done(tx);
    },
    async deleteMeta(key) {
      const tx = db.transaction(META, "readwrite");
      tx.objectStore(META).delete(key);
      await done(tx);
    },
    async clear() {
      const tx = db.transaction([RECORDS, QUEUE, META], "readwrite");
      for (const s of [RECORDS, QUEUE, META]) tx.objectStore(s).clear();
      await done(tx);
    },
  };
}

/** In-memory persistence, for tests and as a fallback when IndexedDB is unavailable (private windows). */
export function memoryPersistence(): Persistence {
  const records = new Map<string, SyncRecord>();
  const queue = new Map<string, SyncRecord>();
  const meta = new Map<string, unknown>();
  const clone = <T>(x: T): T => structuredClone(x);
  return {
    loadRecords: async () => [...records.values()].map(clone),
    putRecords: async (rs) => rs.forEach((r) => records.set(recordKey(r), clone(r))),
    loadQueue: async () => [...queue.values()].map(clone),
    putQueue: async (rs) => rs.forEach((r) => queue.set(recordKey(r), clone(r))),
    deleteQueue: async (keys) => keys.forEach((k) => queue.delete(k)),
    getMeta: async <T>(k: string) => meta.get(k) as T | undefined,
    setMeta: async (k, v) => void meta.set(k, clone(v)),
    deleteMeta: async (k) => void meta.delete(k),
    clear: async () => { records.clear(); queue.clear(); meta.clear(); },
  };
}
