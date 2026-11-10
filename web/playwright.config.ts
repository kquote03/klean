import { defineConfig, devices } from "@playwright/test";

// Browsers come from Nix (nix develop .#web sets PLAYWRIGHT_BROWSERS_PATH); @playwright/test is pinned to
// the same version as nixpkgs' playwright-driver.
//   e2e:    against the real backend in Docker (e2e/run.sh starts and stops it) at E2E_BASE_URL
//   visual: toHaveScreenshot for Today, Tasks and Calendar in both themes, against `vite preview` + a mocked API
//   review: screenshots of every screen for the visual review, into ~/.hermes/cache/scratch/m26/visual/
const common = {
  timezoneId: "Asia/Dubai",
  locale: "en-GB",
  serviceWorkers: "block" as const,
};

export default defineConfig({
  testDir: "e2e",
  timeout: 60_000,
  expect: { toHaveScreenshot: { maxDiffPixelRatio: 0.002, animations: "disabled", caret: "hide" } },
  snapshotPathTemplate: "{testDir}/__screenshots__/{testFilePath}/{arg}{ext}",
  reporter: [["list"]],
  fullyParallel: false,
  workers: 1,
  webServer: process.env.E2E_ONLY ? undefined : {
    command: "pnpm preview",
    url: "http://localhost:4173",
    reuseExistingServer: true,
    timeout: 60_000,
  },
  projects: [
    { name: "e2e", testMatch: "e2e.spec.ts", use: { ...devices["Desktop Chrome"], ...common, baseURL: process.env.E2E_BASE_URL ?? "http://localhost:8080" } },
    { name: "visual", testMatch: "visual.spec.ts", use: { ...devices["Desktop Chrome"], ...common, baseURL: "http://localhost:4173" } },
    { name: "review", testMatch: "review.spec.ts", use: { ...devices["Desktop Chrome"], ...common, baseURL: "http://localhost:4173" } },
  ],
});
