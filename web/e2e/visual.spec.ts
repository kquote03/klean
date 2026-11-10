// Visual regression: Today, Tasks and Calendar in light and dark, laptop and phone, with seeded data and a fixed
// clock. Baselines live in e2e/__screenshots__/; refresh with `pnpm visual --update-snapshots` after a reviewed change.
import { expect, test } from "@playwright/test";
import { mockBackend, signIn } from "./mock";

const sizes = { laptop: { width: 1440, height: 900 }, phone: { width: 390, height: 844 } } as const;
const screens = [
  { name: "today", path: "/" },
  { name: "tasks", path: "/tasks" },
  { name: "calendar", path: "/calendar" },
] as const;

for (const scheme of ["light", "dark"] as const) {
  for (const [size, viewport] of Object.entries(sizes)) {
    test.describe(`${scheme} ${size}`, () => {
      test.use({ colorScheme: scheme, viewport });
      for (const s of screens) {
        test(s.name, async ({ page }) => {
          await mockBackend(page);
          await signIn(page);
          await page.evaluate((p) => (window as unknown as { klean: { go(path: string): void } }).klean.go(p), s.path);
          await page.waitForTimeout(400);
          await expect(page).toHaveScreenshot(`${s.name}-${scheme}-${size}.png`);
        });
      }
    });
  }
}
