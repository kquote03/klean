// Web push: a service worker plus a subscription through /push/vapid-public-key and /push/subscribe.
import type { Api } from "./api";

export type PushStatus = "unsupported" | "denied" | "off" | "on";

export function pushSupported(): boolean {
  return "serviceWorker" in navigator && "PushManager" in window && "Notification" in window && window.isSecureContext;
}

export async function registerServiceWorker(): Promise<ServiceWorkerRegistration | null> {
  if (!("serviceWorker" in navigator)) return null;
  try { return await navigator.serviceWorker.register("/sw.js", { scope: "/" }); } catch { return null; }
}

export async function pushStatus(): Promise<PushStatus> {
  if (!pushSupported()) return "unsupported";
  if (Notification.permission === "denied") return "denied";
  const reg = await navigator.serviceWorker.getRegistration("/");
  const sub = await reg?.pushManager.getSubscription();
  return sub ? "on" : "off";
}

/** base64url → bytes, for applicationServerKey. */
export function base64UrlToBytes(s: string): Uint8Array<ArrayBuffer> {
  const b64 = s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4);
  const bin = atob(b64);
  const out = new Uint8Array(new ArrayBuffer(bin.length));
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export async function enablePush(api: Api): Promise<PushStatus> {
  if (!pushSupported()) return "unsupported";
  const perm = await Notification.requestPermission();
  if (perm !== "granted") return perm === "denied" ? "denied" : "off";
  const reg = (await registerServiceWorker()) ?? (await navigator.serviceWorker.ready);
  const { publicKey } = await api.vapidKey();
  const sub = (await reg.pushManager.getSubscription()) ?? (await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: base64UrlToBytes(publicKey) }));
  await api.subscribe(sub.toJSON());
  return "on";
}
