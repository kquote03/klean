// The web app's own device: a local device id (stamped on every row it writes) and the admin token from
// POST /auth/login, both kept in IndexedDB's meta store.
import { uuidV7 } from "./data/ids";
import type { Persistence } from "./data/idb";

export interface Session { token: string; deviceId: string; deviceName: string }

const DEVICE_ID = "device.id";
const DEVICE_NAME = "device.name";
const TOKEN = "auth.token";

/** This browser's device id; created once, matching the server's `[A-Za-z0-9_-]{1,64}`. */
export async function deviceId(db: Persistence): Promise<string> {
  let id = await db.getMeta<string>(DEVICE_ID);
  if (!id) {
    id = "web-" + uuidV7();
    await db.setMeta(DEVICE_ID, id);
  }
  return id;
}

export async function loadSession(db: Persistence): Promise<Session | null> {
  const token = await db.getMeta<string>(TOKEN);
  if (!token) return null;
  return { token, deviceId: await deviceId(db), deviceName: (await db.getMeta<string>(DEVICE_NAME)) ?? "Web" };
}

export async function saveSession(db: Persistence, s: Session): Promise<void> {
  await db.setMeta(DEVICE_ID, s.deviceId);
  await db.setMeta(DEVICE_NAME, s.deviceName);
  await db.setMeta(TOKEN, s.token);
}

/** Sign out: forget the token and the cached data (a fresh device id comes with the next sign-in). */
export async function clearSession(db: Persistence): Promise<void> {
  await db.clear();
}
