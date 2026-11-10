// The web's local-first record store: every synced row lives in memory (backed by IndexedDB), local writes are
// stamped like Android's SyncStamper and queued for push, and pulled records merge with LWW.
import { recordKey, type Persistence } from "./idb";
import { lww, type Json, type Row, type SyncRecord } from "./sync-types";

export type Listener = () => void;

export class RecordStore {
  private records = new Map<string, SyncRecord>();
  private queue = new Map<string, SyncRecord>();
  private listeners = new Set<Listener>();
  /** Bumped on every change; lets views memoise. */
  version = 0;

  constructor(
    private readonly db: Persistence,
    public deviceId: string,
    private readonly now: () => number = Date.now,
  ) {}

  async load(): Promise<void> {
    for (const r of await this.db.loadRecords()) this.records.set(recordKey(r), r);
    for (const r of await this.db.loadQueue()) this.queue.set(recordKey(r), r);
    this.changed();
  }

  subscribe(fn: Listener): () => void {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  private changed() {
    this.version++;
    for (const l of this.listeners) l();
  }

  /** Live (not tombstoned) rows of a table. */
  rows(table: string): Row[] {
    const out: Row[] = [];
    for (const r of this.records.values()) if (r.table === table && !r.deleted) out.push(r.json);
    return out;
  }

  get(table: string, id: string): Row | undefined {
    const r = this.records.get(`${table}/${id}`);
    return r && !r.deleted ? r.json : undefined;
  }

  record(table: string, id: string): SyncRecord | undefined {
    return this.records.get(`${table}/${id}`);
  }

  /** Writes waiting to be pushed, oldest first. */
  pending(): SyncRecord[] {
    return [...this.queue.values()].sort((a, b) => a.updatedAt - b.updatedAt);
  }

  /**
   * Insert or update one row, like a repository write through SyncStamper: `created_at` is kept, `updated_at`
   * is now (and always later than the previous version, so the edit wins LWW even against a skewed clock),
   * `device_id` is this device. The record is cached and queued for push.
   */
  async write(table: string, id: string, values: Row, opts: { deleted?: boolean } = {}): Promise<SyncRecord> {
    const prev = this.records.get(`${table}/${id}`);
    const updatedAt = Math.max(this.now(), (prev?.updatedAt ?? 0) + 1);
    const deleted = opts.deleted ?? false;
    const json: Row = {
      ...(prev?.json ?? {}),
      ...values,
      id,
      created_at: (prev?.json.created_at as Json) ?? updatedAt,
      updated_at: updatedAt,
      device_id: this.deviceId,
      deleted,
    };
    const rec: SyncRecord = { table, id, updatedAt, deviceId: this.deviceId, deleted, json };
    this.records.set(recordKey(rec), rec);
    this.queue.set(recordKey(rec), rec);
    await this.db.putRecords([rec]);
    await this.db.putQueue([rec]);
    this.changed();
    return rec;
  }

  /** Tombstone a row (rows are never hard-deleted). */
  async remove(table: string, id: string): Promise<void> {
    if (this.records.has(`${table}/${id}`)) await this.write(table, id, {}, { deleted: true });
  }

  /** Merge pulled records with LWW. Returns how many changed the cache. */
  async applyRemote(remote: SyncRecord[]): Promise<number> {
    const changed: SyncRecord[] = [];
    const dropped: string[] = [];
    for (const r of remote) {
      const k = recordKey(r);
      const local = this.records.get(k);
      const winner = lww(local, r);
      if (winner !== local) {
        this.records.set(k, winner);
        changed.push(winner);
        // A newer remote version supersedes a queued local one; pushing ours would lose anyway.
        const q = this.queue.get(k);
        if (q && lww(q, winner) === winner && q !== winner) {
          this.queue.delete(k);
          dropped.push(k);
        }
      }
    }
    await this.db.deleteQueue(dropped);
    if (changed.length) {
      await this.db.putRecords(changed);
      this.changed();
    }
    return changed.length;
  }

  /**
   * Mark records as pushed: each leaves the queue only if it is still the version that was sent, so a newer
   * local edit made while the push was in flight stays queued.
   */
  async acknowledge(sent: SyncRecord[]): Promise<void> {
    const keys: string[] = [];
    for (const s of sent) {
      const k = recordKey(s);
      const q = this.queue.get(k);
      if (q && q.updatedAt === s.updatedAt && q.deviceId === s.deviceId) {
        this.queue.delete(k);
        keys.push(k);
      }
    }
    await this.db.deleteQueue(keys);
  }
}
