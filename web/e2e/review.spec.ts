// The UI-WEB visual review: every screen, light and dark, laptop (1440×900) and phone (390×844), with seeded data,
// plus the clutter budget on every screen's first viewport. Screenshots and a report (layout measurements and
// clutter counts) go to $REVIEW_DIR (default ~/.hermes/cache/scratch/ui-web/<label>).
// Run: nix develop .#web -c pnpm -C web review   (REVIEW_LABEL=before|after)
import { expect, test, type Page } from "@playwright/test";
import { mkdirSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import { mockBackend, signIn } from "./mock";
import { clutterCount, expectClutterBudget } from "./clutter";

const dir = process.env.REVIEW_DIR ?? join(homedir(), ".hermes/cache/scratch/ui-web", process.env.REVIEW_LABEL ?? "latest");
mkdirSync(dir, { recursive: true });

const sizes = { laptop: { width: 1440, height: 900 }, phone: { width: 390, height: 844 } } as const;
const go = (p: Page, path: string) => p.evaluate((x) => (window as unknown as { klean: { go(p: string): void } }).klean.go(x), path);
const setUi = (p: Page, ui: Record<string, unknown>) => p.evaluate((u) => { const k = (window as unknown as { klean: { ui: Record<string, unknown>; refresh(): void } }).klean; Object.assign(k.ui, u); k.refresh(); }, ui);
const menu = async (p: Page, item: string) => { await p.getByTestId("overflow").click(); await p.getByTestId(item).click(); };

/** A screen (budget checked) or an overlay on one (sheet, menu: screenshot only). */
type Screen = { name: string; path: string; prepare?: (p: Page) => Promise<unknown>; overlay?: boolean };
const screens: Screen[] = [
  { name: "today", path: "/" },
  { name: "today-menu", path: "/", overlay: true, prepare: (p) => p.getByTestId("overflow").click() },
  { name: "today-arrange", path: "/", overlay: true, prepare: (p) => menu(p, "arrange") },
  { name: "add-menu", path: "/", overlay: true, prepare: (p) => p.getByTestId("add").click() },
  { name: "capture-sheet", path: "/", overlay: true, prepare: async (p) => { await p.getByTestId("add").click(); await p.getByTestId("add-type").click(); } },
  { name: "plan-tasks", path: "/plan/tasks", prepare: (p) => setUi(p, { tasksView: "all" }) },
  { name: "plan-tasks-menu", path: "/plan/tasks", overlay: true, prepare: (p) => p.getByTestId("overflow").click() },
  { name: "task-edit", path: "/plan/tasks", overlay: true, prepare: (p) => p.getByTestId("task-row").last().locator(".row-main").click() },
  { name: "plan-tasks-lists", path: "/plan/tasks", prepare: (p) => menu(p, "menu-lists") },
  { name: "plan-tasks-reminders", path: "/plan/tasks", prepare: (p) => menu(p, "menu-reminders") },
  { name: "plan-tasks-finished", path: "/plan/tasks", prepare: (p) => menu(p, "menu-finished") },
  { name: "plan-calendar", path: "/plan/calendar", prepare: (p) => setUi(p, { tasksView: "all", calView: "agenda" }) },
  { name: "plan-calendar-week", path: "/plan/calendar", prepare: (p) => menu(p, "view-week") },
  { name: "plan-calendar-month", path: "/plan/calendar", prepare: (p) => menu(p, "view-month") },
  { name: "track-week", path: "/track/week" },
  { name: "track-workout", path: "/track/workout" },
  { name: "workout-session", path: "/track/workout", overlay: true, prepare: (p) => p.getByTestId("workout-row").first().locator(".row-main").click() },
  { name: "track-sleep", path: "/track/sleep" },
  { name: "track-diet", path: "/track/diet" },
  { name: "people-contacts", path: "/people/contacts" },
  { name: "people-birthdays", path: "/people/birthdays" },
  { name: "contact-sheet", path: "/people/contacts", overlay: true, prepare: (p) => p.getByTestId("contact-row").first().locator(".row-main").click() },
  { name: "approvals", path: "/approvals" },
  { name: "approval-sheet", path: "/approvals", overlay: true, prepare: (p) => p.getByTestId("approval").first().locator(".row-main").click() },
  { name: "settings", path: "/settings" },
  { name: "settings-pair", path: "/settings", overlay: true, prepare: (p) => p.getByTestId("pair-start").click() },
];

/** Layout checks the eye can miss: sideways scroll, clipped text, sizes off the type scale, fonts other than Cantarell. */
async function measure(page: Page) {
  return page.evaluate(() => {
    const doc = document.scrollingElement!;
    const SCALE = new Set([12, 14, 16, 22, 24, 36]);
    const small: string[] = [];
    const offScale: string[] = [];
    const notCantarell = new Set<string>();
    const clipped: string[] = [];
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    while (walker.nextNode()) {
      const n = walker.currentNode as Text;
      const text = n.textContent?.trim();
      if (!text) continue;
      const el = n.parentElement!;
      const cs = getComputedStyle(el);
      if (cs.visibility === "hidden" || cs.display === "none" || el.closest("[aria-hidden=true], dialog:not([open]), [popover]:not(:popover-open), .sr-only")) continue;
      const r = el.getBoundingClientRect();
      if (!r.width || !r.height) continue;
      const size = parseFloat(cs.fontSize);
      // labelMedium (12) only for navigation labels, chart axes and weekday heads; everything else ≥ 14.
      const label = el.closest("m3e-nav-item, .week-bars, .dow, .label-m");
      if (size < (label ? 12 : 14)) small.push(`${text.slice(0, 40)} (${cs.fontSize})`);
      if (!SCALE.has(Math.round(size))) offScale.push(`${text.slice(0, 40)} (${cs.fontSize})`);
      if (!cs.fontFamily.includes("Cantarell")) notCantarell.add(cs.fontFamily);
      if (r.right > innerWidth + 1) clipped.push(text.slice(0, 40));
    }
    return { scrollWidth: doc.scrollWidth, innerWidth, sideways: doc.scrollWidth > innerWidth + 1, small: [...new Set(small)].slice(0, 20), offScale: [...new Set(offScale)].slice(0, 20), notCantarell: [...notCantarell], clipped: clipped.slice(0, 20) };
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
      const failures: string[] = [];
      for (const s of screens) {
        await go(page, s.path);
        await page.waitForTimeout(150);
        if (s.prepare) await s.prepare(page);
        await page.mouse.move(0, 0);
        await page.waitForTimeout(350);
        const file = `${s.name}-${scheme}-${size}.png`;
        const entry: Record<string, unknown> = {};
        if (!s.overlay) {
          // The budget is about the first viewport at rest, before the page is grown for the full screenshot.
          entry.clutter = await clutterCount(page);
          try { await expectClutterBudget(page, { maxInteractive: 7, maxInputs: 0 }); } catch (e) { failures.push(`${file}: ${(e as Error).message.split("\n")[0]}`); }
        }
        await shot(page, join(dir, file), viewport, !s.overlay);
        report[file] = { ...entry, ...(await measure(page)) };
        // Close whatever the step opened (menus, sheets).
        await page.keyboard.press("Escape");
        await page.waitForTimeout(100);
        await page.evaluate(() => { document.querySelectorAll("dialog[open]").forEach((d) => (d as HTMLDialogElement).close()); (document.activeElement as HTMLElement | null)?.blur(); });
      }
      expect(failures, failures.join("\n")).toEqual([]);
    });
  }
}

test.describe("the clutter budget helper", () => {
  const page = async (p: Page, body: string) => { await p.setViewportSize(sizes.phone); await p.setContent(`<body style="margin:0">${body}</body>`); };
  const buttons = (n: number) => Array.from({ length: n }, (_, i) => `<button>B${i}</button>`).join("");
  test("eight buttons break the budget", async ({ page: p }) => {
    await page(p, buttons(8));
    expect((await clutterCount(p)).interactive).toBe(8);
    await expect(expectClutterBudget(p)).rejects.toThrow(/8 interactive elements/);
  });
  test("seven buttons fit", async ({ page: p }) => {
    await page(p, buttons(7));
    await expectClutterBudget(p);
  });
  test("an exempt list counts once, navigation not at all", async ({ page: p }) => {
    await page(p, `<nav data-clutter-exempt>${buttons(4)}</nav><ul data-clutter-exempt="list">${Array.from({ length: 20 }, (_, i) => `<li><button>Row ${i}</button><button>Check ${i}</button></li>`).join("")}</ul>${buttons(2)}`);
    expect((await clutterCount(p)).interactive).toBe(3);
  });
  test("a text field breaks the default budget", async ({ page: p }) => {
    await page(p, `<input type="text">`);
    await expect(expectClutterBudget(p)).rejects.toThrow(/1 text inputs/);
  });
  test("what is below the first viewport doesn't count", async ({ page: p }) => {
    await page(p, `<div style="height:2000px"></div>${buttons(10)}`);
    expect((await clutterCount(p)).interactive).toBe(0);
  });
});

/**
 * A whole-page screenshot taken by growing the viewport to the page's height first. Playwright's fullPage
 * capture resizes mid-frame, which catches M3E's navigation half-way through a relayout (labels missing).
 * Overlays (sheets, menus) are shot at the viewport size, as a person sees them.
 */
async function shot(page: Page, path: string, viewport: { width: number; height: number }, full = true) {
  const height = await page.evaluate(() => document.scrollingElement!.scrollHeight);
  if (full && height > viewport.height) {
    await page.setViewportSize({ width: viewport.width, height });
    await page.waitForTimeout(500);
  }
  await page.screenshot({ path });
  await page.setViewportSize(viewport);
}
