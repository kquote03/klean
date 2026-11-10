// Klean service worker: shows web-push messages ({title, body, tag?, url?} from the backend's PushService) and
// keeps the app shell available offline. Plain JS: it is served as-is from /sw.js.
const CACHE = "klean-shell-v1";

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((c) => c.addAll(["/", "/manifest.webmanifest", "/icon.svg"])).catch(() => {}));
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim()),
  );
});

// Network first for the app (so a new build shows at once), cache as the offline fallback. The API is never cached.
self.addEventListener("fetch", (event) => {
  const url = new URL(event.request.url);
  if (event.request.method !== "GET" || url.origin !== self.location.origin || url.pathname.startsWith("/api/") || url.pathname.startsWith("/dav/")) return;
  event.respondWith(
    fetch(event.request)
      .then((res) => {
        if (res.ok) {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put(event.request, copy));
        }
        return res;
      })
      .catch(async () => (await caches.match(event.request)) ?? (event.request.mode === "navigate" ? caches.match("/") : Response.error())),
  );
});

self.addEventListener("push", (event) => {
  let msg = { title: "Klean", body: "" };
  try { msg = event.data ? event.data.json() : msg; } catch { msg.body = event.data ? event.data.text() : ""; }
  event.waitUntil(
    self.registration.showNotification(msg.title || "Klean", {
      body: msg.body || "",
      tag: msg.tag || undefined,
      icon: "/icon.svg",
      badge: "/icon.svg",
      data: { url: msg.url || "/" },
      // Alarms arrive as persistent notifications: they stay until handled.
      requireInteraction: typeof msg.tag === "string" && msg.tag.startsWith("alarm"),
    }),
  );
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const target = (event.notification.data && event.notification.data.url) || "/";
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((wins) => {
      for (const w of wins) if ("focus" in w) { w.navigate(target); return w.focus(); }
      return self.clients.openWindow(target);
    }),
  );
});
