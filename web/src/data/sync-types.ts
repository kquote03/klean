// The sync wire protocol, mirrored from :shared (app.klean.shared.sync). Keep the two in step.

export type Json = null | boolean | number | string | Json[] | { [k: string]: Json };
/** A Room row as JSON: snake_case columns, minus `change_seq`. */
export type Row = { [column: string]: Json };

/** One row of one syncable table. */
export interface SyncRecord {
  /** The Room table name, e.g. "task". */
  table: string;
  id: string;
  /** UTC epoch ms of the last change; the LWW clock. */
  updatedAt: number;
  /** The device that made the change; the LWW tie-breaker. */
  deviceId: string;
  /** Tombstone. Rows are never hard-deleted. */
  deleted: boolean;
  json: Row;
}

export interface PushRequest { deviceId: string; records: SyncRecord[] }
export interface PushResponse { accepted: number; serverCursor: number }
export interface PullResponse { records: SyncRecord[]; cursor: number; hasMore: boolean }

/**
 * Last-write-wins, exactly as `lww()` in :shared: the higher `updatedAt` wins; ties go to the lexically larger
 * `deviceId`, so every participant picks the same winner whatever order it sees versions in. An identical
 * version keeps `local`, so re-applying is a no-op.
 */
export function lww(local: SyncRecord | null | undefined, remote: SyncRecord): SyncRecord {
  if (!local) return remote;
  if (remote.updatedAt > local.updatedAt) return remote;
  if (remote.updatedAt === local.updatedAt && compareCodeUnits(remote.deviceId, local.deviceId) > 0) return remote;
  return local;
}

/** Kotlin's String.compareTo compares UTF-16 code units, which is what JS `<`/`>` do too. */
function compareCodeUnits(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

/** Table names accepted on the wire: lowercase snake_case, as in Room. */
export const SYNC_TABLE_NAME = /^[a-z][a-z0-9_]{0,63}$/;

export const key = (table: string, id: string) => `${table}/${id}`;
