// The backend's HTTP API (docs/backend-api.md). Everything is under /api/v1 on the same origin that serves the app.
import type { PullResponse, PushResponse, SyncRecord } from "./data/sync-types";

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
  }
}

export interface DeviceDto {
  id: string; name: string; platform: string; admin: boolean; createdAt: number; lastSeenAt: number; current: boolean;
}
export interface LoginResponse { deviceId: string; token: string }
export interface PairStart { code: string; qr: string; expiresAt: number }

export class Api {
  constructor(public token: string | null = null, private readonly base = "/api/v1", private readonly f: typeof fetch = (...a) => fetch(...a)) {}

  async call<T>(method: string, path: string, body?: unknown, opts: { raw?: boolean } = {}): Promise<T> {
    const headers: Record<string, string> = {};
    if (this.token) headers.Authorization = `Bearer ${this.token}`;
    if (body !== undefined) headers["Content-Type"] = "application/json";
    const res = await this.f(this.base + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    if (!res.ok) {
      let msg = res.statusText;
      try { msg = ((await res.json()) as { error?: string }).error ?? msg; } catch { /* not JSON */ }
      throw new ApiError(res.status, msg);
    }
    if (opts.raw) return res as unknown as T;
    if (res.status === 204 || res.status === 201 && res.headers.get("content-length") === "0") return undefined as T;
    const text = await res.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }

  login(password: string, deviceName: string, deviceId: string) {
    return this.call<LoginResponse>("POST", "/auth/login", { password, deviceName, deviceId });
  }
  me() { return this.call<DeviceDto>("GET", "/auth/me"); }
  devices() { return this.call<DeviceDto[]>("GET", "/devices"); }
  revoke(id: string) { return this.call<void>("DELETE", `/devices/${encodeURIComponent(id)}`); }
  pairStart() { return this.call<PairStart>("POST", "/pair/start"); }
  push(deviceId: string, records: SyncRecord[]) { return this.call<PushResponse>("POST", "/sync/push", { deviceId, records }); }
  pull(cursor: number, limit = 500) { return this.call<PullResponse>("GET", `/sync/pull?cursor=${cursor}&limit=${limit}`); }
  vapidKey() { return this.call<{ publicKey: string }>("GET", "/push/vapid-public-key"); }
  subscribe(sub: PushSubscriptionJSON) { return this.call<void>("POST", "/push/subscribe", sub); }
  unsubscribe(endpoint: string) { return this.call<void>("POST", "/push/unsubscribe", { endpoint }); }
  pushTest() { return this.call<{ sent: number; failed: number }>("POST", "/push/test"); }
  googleStatus() { return this.call<{ configured: boolean; connected: boolean }>("GET", "/google/status"); }
  googleStart() { return this.call<{ url: string }>("GET", "/google/oauth/start"); }
  async backup(): Promise<Blob> {
    const res = await this.call<Response>("POST", "/admin/backup", undefined, { raw: true });
    return res.blob();
  }
}
