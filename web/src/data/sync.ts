// Push the pending-writes queue, then pull everything after our cursor, through the same /sync API as Android.
import type { RecordStore } from "./store";
import type { Persistence } from "./idb";
import type { PullResponse, PushResponse, SyncRecord } from "./sync-types";

export interface SyncTransport {
  push(deviceId: string, records: SyncRecord[]): Promise<PushResponse>;
  pull(cursor: number, limit?: number): Promise<PullResponse>;
}

export type SyncState = "idle" | "syncing" | "offline" | "error";

export const PUSH_BATCH = 500;
const CURSOR = "sync.cursor";

export class SyncEngine {
  state: SyncState = "idle";
  lastSyncedAt: number | null = null;
  private running: Promise<void> | null = null;
  private again = false;
  onState: (s: SyncState) => void = () => {};
  onUnauthorized: () => void = () => {};

  constructor(private readonly store: RecordStore, private readonly db: Persistence, private readonly api: SyncTransport, private readonly now: () => number = Date.now) {}

  /** Run one sync; concurrent calls coalesce into at most one follow-up run. */
  sync(): Promise<void> {
    if (this.running) {
      this.again = true;
      return this.running;
    }
    this.running = (async () => {
      try {
        do {
          this.again = false;
          await this.once();
        } while (this.again);
      } finally {
        this.running = null;
      }
    })();
    return this.running;
  }

  private set(s: SyncState) {
    this.state = s;
    this.onState(s);
  }

  private async once() {
    this.set("syncing");
    try {
      // 1. Push in batches (the server takes ≤ 1000 per request).
      for (;;) {
        const batch = this.store.pending().slice(0, PUSH_BATCH);
        if (!batch.length) break;
        await this.api.push(this.store.deviceId, batch);
        await this.store.acknowledge(batch);
        if (batch.length < PUSH_BATCH) break;
      }
      // 2. Pull until caught up. Our own pushes come back too; LWW makes them no-ops.
      let cursor = (await this.db.getMeta<number>(CURSOR)) ?? 0;
      for (;;) {
        const page = await this.api.pull(cursor, 500);
        await this.store.applyRemote(page.records);
        cursor = page.cursor;
        await this.db.setMeta(CURSOR, cursor);
        if (!page.hasMore) break;
      }
      this.lastSyncedAt = this.now();
      this.set("idle");
    } catch (e) {
      const status = (e as { status?: number }).status;
      if (status === 401) {
        this.set("error");
        this.onUnauthorized();
      } else this.set(status ? "error" : "offline");
    }
  }
}
