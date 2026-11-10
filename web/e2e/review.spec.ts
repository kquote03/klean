// The M26 visual review: every screen, light and dark, laptop (1440×900) and phone (390×844), with seeded data.
// Screenshots and a measurements report go to $REVIEW_DIR (default ~/.hermes/cache/scratch/m26/visual/<label>).
// Run: nix develop .#web -c pnpm -C web review   (REVIEW_LABEL=before|after)
import { test, type Page } from "@playwright/test";
import { mkdirSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import { mockBackend, signIn } from "./mock";

const dir = process.env.REVIEW_DIR ?? join(homedir(), ".hermes/cache/scratch/m26/visual", process.env.REVIEW_LABEL ?? "latest");
mkdirSync(dir, { recursive: true });

const sizes = { laptop: { width: 1440, height: 900 }, phone: { width: 390, height: 844 } } as const;
type Screen = { name: string; path: string; prepare?: (p: Page) => Promise<void>; phoneOnly?: boolean };
const screens: Screen[] = [
  { name: "today", path: "/" },
  { name: "today-arrange", path: "/", prepare: async (p) => { await p.getByTestId("arrange").click(); await p.waitForTimeout(300); } },
  { name: "tasks-today", path: "/tasks" },
  { name: "tasks-upcoming", path: "/tasks", prepare: (p) => p.locator('[data-tab="upcoming"]').click() },
  { name: "tasks-lists", path: "/tasks", prepare: (p) => p.locator('[data-tab="lists"]').click() },
  { name: "tasks-reminders", path: "/tasks", prepare: (p) => p.locator('[data-tab="reminders"]').click() },
  { name: "tasks-done", path: "/tasks", prepare: (p) => p.locator('[data-tab="done"]').click() },
  { name: "calendar-week", path: "/calendar" },
  { name: "calendar-month", path: "/calendar", prepare: (p) => p.locator('[data-tab="month"]').click() },
  { name: "calendar-agenda", path: "/calendar", prepare: (p) => p.locator('[data-tab="agenda"]').click() },
  { name: "contacts", path: "/contacts" },
  { name: "birthdays", path: "/contacts", prepare: (p) => p.locator('[data-tab="birthdays"]').click() },
  { name: "workout", path: "/workout" },
  { name: "sleep", path: "/sleep" },
  { name: "diet", path: "/diet" },
  { name: "approvals", path: "/approvals" },
  { name: "settings", path: "/settings" },
  { name: "settings-pair", path: "/settings", prepare: (p) => p.getByTestId("pair-start").click() },
  { name: "more", path: "/more", phoneOnly: true },
];

/** Layout checks the eye can miss: sideways scroll, clipped text, text below 16 px, fonts other than Cantarell. */
async function measure(page: Page) {
  return page.evaluate(() => {
    const doc = document.scrollingElement!;
    const small: string[] = [];
    const notCantarell = new Set<string>();
    const clipped: string[] = [];
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    while (walker.nextNode()) {
      const n = walker.currentNode as Text;
      const text = n.textContent?.trim();
      if (!text) continue;
      const el = n.parentElement!;
      const cs = getComputedStyle(el);
      if (cs.visibility === "hidden" || cs.display === "none" || el.closest("[aria-hidden=true], dialog:not([open])")) continue;
      const r = el.getBoundingClientRect();
      if (!r.width || !r.height) continue;
      // Body text ≥ 16 px; UI labels (field labels and hints, navigation labels) ≥ 14 px.
      const label = el.closest("m3e-nav-item, label, [slot=hint], [slot=label]");
      if (parseFloat(cs.fontSize) < (label ? 14 : 16)) small.push(`${text.slice(0, 40)} (${cs.fontSize})`);
      if (!cs.fontFamily.includes("Cantarell")) notCantarell.add(cs.fontFamily);
      if (r.right > innerWidth + 1) clipped.push(text.slice(0, 40));
    }
    return { scrollWidth: doc.scrollWidth, innerWidth, sideways: doc.scrollWidth > innerWidth + 1, small: [...new Set(small)].slice(0, 20), notCantarell: [...notCantarell], clipped: clipped.slice(0, 20) };
  });
}

const report: Record<string, unknown> = {};
test.afterAll(() => writeFileSync(join(dir, "report.json"), JSON.stringify(report, null, 2)));

for (const scheme of ["light", "dark"] as const) {
  for (const [size, viewport] of Object.entries(sizes)) {
    test(`login ${scheme} ${size}`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: scheme });
      await page.setViewportSize(viewport);
      await mockBackend(page);
      await page.goto("/");
      await page.getByTestId("password").waitFor();
      await page.evaluate(() => document.fonts.ready);
      const file = `login-${scheme}-${size}.png`;
      await shot(page, join(dir, file), viewport);
      report[file] = await measure(page);
    });
    test(`screens ${scheme} ${size}`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: scheme });
      await page.setViewportSize(viewport);
      await mockBackend(page);
      await signIn(page);
      for (const s of screens) {
        if (s.phoneOnly && size !== "phone") continue;
        await page.evaluate((path) => (window as unknown as { klean: { go(p: string): void } }).klean.go(path), s.path);
        await page.waitForTimeout(150);
        if (s.prepare) await s.prepare(page);
        await page.waitForTimeout(250);
        const file = `${s.name}-${scheme}-${size}.png`;
        await shot(page, join(dir, file), viewport);
        report[file] = await measure(page);
        await page.keyboard.press("Escape");
      }
    });
  }
}

/**
 * A whole-page screenshot taken by growing the viewport to the page's height first. Playwright's fullPage
 * capture resizes mid-frame, which catches M3E's navigation half-way through a relayout (labels missing).
 */
async function shot(page: Page, path: string, viewport: { width: number; height: number }) {
  const height = await page.evaluate(() => document.scrollingElement!.scrollHeight);
  if (height > viewport.height) {
    await page.setViewportSize({ width: viewport.width, height });
    await page.waitForTimeout(500);
  }
  await page.screenshot({ path });
  await page.setViewportSize(viewport);
}
