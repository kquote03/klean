// A stand-in for the backend's API inside the browser (page.route), for the visual tests and the visual review:
// deterministic data and time, no server needed. The real backend is exercised by e2e.spec.ts.
import type { Page } from "@playwright/test";
import type { SyncRecord } from "../src/data/sync-types";
import { NOW, seed } from "./seed";

export async function mockBackend(page: Page, records: SyncRecord[] = seed()) {
  const server = new Map(records.map((r) => [`${r.table}/${r.id}`, r]));
  await page.clock.setFixedTime(NOW);
  await page.route("**/api/v1/**", async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    const path = url.pathname.replace("/api/v1", "");
    const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    if (path === "/auth/login") return json({ deviceId: "web-visual", token: "visual-token" });
    if (path === "/sync/pull") {
      const cursor = Number(url.searchParams.get("cursor") ?? 0);
      return json(cursor === 0 ? { records: [...server.values()], cursor: 1, hasMore: false } : { records: [], cursor, hasMore: false });
    }
    if (path === "/sync/push") {
      const body = req.postDataJSON() as { records: SyncRecord[] };
      for (const r of body.records) server.set(`${r.table}/${r.id}`, r);
      return json({ accepted: body.records.length, serverCursor: 1 });
    }
    if (path === "/devices") return json([
      { id: "web-visual", name: "Laptop", platform: "WEB", admin: true, createdAt: NOW - 864e5 * 30, lastSeenAt: NOW, current: true },
      { id: "phone-pixel", name: "Pixel 9a", platform: "ANDROID", admin: false, createdAt: NOW - 864e5 * 60, lastSeenAt: NOW - 600_000, current: false },
    ]);
    if (path === "/pair/start") return json({ code: "KQ7M-4TZP", qr: "klean://pair?url=http%3A%2F%2Fklean.local%3A8080&code=KQ7M-4TZP", expiresAt: NOW + 600_000 });
    if (path === "/google/status") return json({ configured: true, connected: false });
    if (path === "/push/vapid-public-key") return json({ publicKey: "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U" });
    if (path.startsWith("/devices/")) return route.fulfill({ status: 204 });
    return json({ error: "not mocked" }, 404);
  });
}

/** Sign in through the real login form and wait for the first sync. */
export async function signIn(page: Page) {
  await page.goto("/");
  await page.getByTestId("password").fill("visual");
  await page.getByTestId("sign-in").click();
  await page.getByTestId("sync-status").filter({ hasText: "Up to date" }).waitFor();
  await page.evaluate(() => document.fonts.ready);
  // Let the navigation's entry animation settle (M3E suppresses it only after first render).
  await page.waitForTimeout(800);
}
