// End-to-end against the real backend in Docker (started by e2e/run.sh): sign in, pairing QR, create a task,
// a task pushed with curl as a paired phone appears, and approving a seeded approval_request reaches the phone.
import { expect, test, type Page } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { nameUuid } from "../src/data/ids";

const BASE = process.env.E2E_BASE_URL ?? "http://localhost:8080";
const PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? "klean-e2e-password";

/** curl, as the phone would call the API. */
function curl(method: string, path: string, token?: string, body?: unknown): any {
  const args = ["-sS", "--fail-with-body", "-X", method, `${BASE}/api/v1${path}`, "-H", "Content-Type: application/json"];
  if (token) args.push("-H", `Authorization: Bearer ${token}`);
  if (body !== undefined) args.push("--data-binary", JSON.stringify(body));
  const out = execFileSync("curl", args, { encoding: "utf8" });
  return out ? JSON.parse(out) : undefined;
}

const now = () => Date.now();
const today = () => new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Dubai" }); // yyyy-MM-dd
const PERSONAL = nameUuid("tasks.list.personal");

function row(table: string, id: string, deviceId: string, json: Record<string, unknown>) {
  const t = now();
  return { table, id, updatedAt: t, deviceId, deleted: false, json: { id, created_at: t, updated_at: t, device_id: deviceId, deleted: false, ...json } };
}

async function signIn(page: Page) {
  await page.goto("/");
  await page.getByTestId("password").fill(PASSWORD);
  await page.getByTestId("device-name").fill("E2E browser");
  await page.getByTestId("sign-in").click();
  await expect(page.getByTestId("sync-status")).toHaveText(/Up to date/, { timeout: 20_000 });
}

test.describe.configure({ mode: "serial" });

test("sign in, see the pairing QR, create a task, see a phone's task, approve a request", async ({ page }) => {
  // 1. Sign in with the admin password: this browser becomes a WEB device.
  await signIn(page);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(/^(Mon|Tues|Wednes|Thurs|Fri|Satur|Sun)day$/);

  // 2. The pairing QR is drawn in the browser from /pair/start. Settings opens from Today's top bar.
  await page.getByTestId("settings").click();
  await page.getByTestId("pair-start").click();
  const qr = page.getByTestId("pair-qr");
  await expect(qr.locator("svg")).toBeVisible();
  await expect(qr).toHaveAttribute("data-qr", /^klean:\/\/pair\?url=.+&code=[A-Z0-9]{4}-[A-Z0-9]{4}$/);
  const code = (await page.getByTestId("pair-code").textContent())!.trim();

  // A phone claims that code (as the Android app does after scanning).
  const phone = curl("POST", "/pair/claim", undefined, { code, deviceName: "E2E phone", platform: "ANDROID", deviceId: "e2e-phone" });
  expect(phone.token).toBeTruthy();
  await page.reload();
  await expect(page.getByTestId("settings-devices")).toContainText("E2E phone");

  // 3. Create a task on the web through the Add menu; it shows in Plan › Tasks and reaches the server and the phone.
  await page.getByTestId("add").click();
  await page.getByTestId("add-task").click();
  await page.getByTestId("capture-input").fill("Water the plants from the web");
  await page.getByTestId("capture-input").press("Enter");
  await page.locator('m3e-nav-rail m3e-nav-item[data-group="plan"]').click();
  await page.locator('[data-tab="tasks"]').click();
  await expect(page.getByTestId("task-title").filter({ hasText: "Water the plants from the web" })).toBeVisible();
  await expect(page.getByTestId("sync-status")).toHaveText(/Up to date/, { timeout: 20_000 });
  await expect.poll(() => {
    const pulled = curl("GET", "/sync/pull?cursor=0&limit=1000", phone.token);
    return pulled.records.some((r: any) => r.table === "task" && r.json.title === "Water the plants from the web" && r.deviceId !== "e2e-phone");
  }, { timeout: 20_000 }).toBe(true);

  // 4. A task pushed with curl as the phone shows up on the web.
  const listId = curl("GET", "/sync/pull?cursor=0&limit=1000", phone.token).records.find((r: any) => r.table === "task_list")?.id ?? PERSONAL;
  curl("POST", "/sync/push", phone.token, {
    deviceId: "e2e-phone",
    records: [row("task", "e2e-phone-task-1", "e2e-phone", {
      list_id: listId, title: "Phone task: call the bank", notes: null, due_date: today(), due_minutes: null, priority: 0,
      recurrence: null, completed_at: null, position: 0, source: "MANUAL", parent_id: null, status: "OPEN", archived_at: null,
      origin: "KLEAN", remote_id: null, remote_list_id: null, remote_etag: null,
    })],
  });
  await page.evaluate(() => (window as any).klean.syncNow());
  await expect(page.getByTestId("task-title").filter({ hasText: "Phone task: call the bank" })).toBeVisible({ timeout: 20_000 });

  // 5. A seeded approval_request (from the phone, as the agent's relay would write it) is approved with an edit.
  curl("POST", "/sync/push", phone.token, {
    deviceId: "e2e-phone",
    records: [
      row("delegation", "e2e-deleg-1", "e2e-phone", { task_id: "e2e-phone-task-1", mode: "AGENTIC", agent: "HERMES", status: "NEEDS_APPROVAL", remote_ref: null, result: null, auto_complete: false }),
      row("approval_request", "e2e-approval-1", "e2e-phone", {
        delegation_id: "e2e-deleg-1", action: "send_email", draft: "Hello, please call me back.", recipients: JSON.stringify(["bank@example.com"]),
        editable: "{}", decision: "PENDING", decided_at: null,
      }),
    ],
  });
  await page.evaluate(() => (window as any).klean.syncNow());
  await page.locator('m3e-nav-rail m3e-nav-item[data-group="today"]').click();
  await page.getByTestId("card-approvals").click({ timeout: 20_000 });
  const request = page.locator('[data-id="e2e-approval-1"]');
  await expect(request).toBeVisible({ timeout: 20_000 });
  await request.locator(".row-main").click();
  const sheet = page.getByTestId("approval-sheet");
  await sheet.getByTestId("approval-draft").fill("Hello, please call me back after 4.");
  await sheet.getByTestId("approve").click();
  await expect(page.getByTestId("approvals-empty")).toBeVisible();
  await expect.poll(() => {
    const pulled = curl("GET", "/sync/pull?cursor=0&limit=1000", phone.token);
    const a = pulled.records.find((r: any) => r.table === "approval_request" && r.id === "e2e-approval-1");
    return a && `${a.json.decision}|${a.json.draft}|${typeof a.json.decided_at}`;
  }, { timeout: 20_000 }).toBe("EDITED|Hello, please call me back after 4.|number");
});

test("the backend serves the app for client routes and keeps API 404s", async ({ request }) => {
  const page = await request.get("/calendar");
  expect(page.status()).toBe(200);
  expect(await page.text()).toContain('<div id="app">');
  const api = await request.get("/api/v1/nope");
  expect(api.status()).toBe(404);
  expect((await request.get("/api/v1/health")).status()).toBe(200);
});
