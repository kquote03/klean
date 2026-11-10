// The app shell: session, store, sync loop, routing, and rendering with lit-html. Screens are plain functions of
// the App; M3E web components do the Material 3 parts.
import { html, render, nothing, type TemplateResult } from "lit";
import { Api, ApiError } from "./api";
import { memoryPersistence, openIdb, type Persistence } from "./data/idb";
import { RecordStore } from "./data/store";
import { SyncEngine, type SyncState } from "./data/sync";
import { Model, type ModuleKey } from "./model";
import { clearSession, deviceId, loadSession, saveSession, type Session } from "./session";
import { t } from "./copy";
import { icon } from "./ui/icons";
import { loginScreen } from "./screens/login";
import { todayScreen } from "./screens/today";
import { tasksScreen } from "./screens/tasks";
import { calendarScreen } from "./screens/calendar";
import { contactsScreen } from "./screens/contacts";
import { workoutScreen } from "./screens/workout";
import { sleepScreen } from "./screens/sleep";
import { dietScreen } from "./screens/diet";
import { approvalsScreen } from "./screens/approvals";
import { settingsScreen } from "./screens/settings";
import { moreScreen } from "./screens/more";

export interface Route { path: string; title: string; icon: string; module?: ModuleKey; render: (app: App) => TemplateResult; rail: boolean; bar: boolean }

export const ROUTES: Route[] = [
  { path: "/", title: t.nav.today, icon: "today", render: todayScreen, rail: true, bar: true },
  { path: "/tasks", title: t.nav.tasks, icon: "tasks", module: "tasks", render: tasksScreen, rail: true, bar: true },
  { path: "/calendar", title: t.nav.calendar, icon: "calendar", module: "calendar", render: calendarScreen, rail: true, bar: true },
  { path: "/contacts", title: t.nav.contacts, icon: "person", module: "contacts", render: contactsScreen, rail: true, bar: true },
  { path: "/workout", title: t.nav.workout, icon: "workout", module: "workout", render: workoutScreen, rail: true, bar: false },
  { path: "/sleep", title: t.nav.sleep, icon: "sleep", module: "sleep", render: sleepScreen, rail: true, bar: false },
  { path: "/diet", title: t.nav.diet, icon: "diet", module: "diet", render: dietScreen, rail: true, bar: false },
  { path: "/approvals", title: t.nav.approvals, icon: "approvals", render: approvalsScreen, rail: true, bar: false },
  { path: "/settings", title: t.nav.settings, icon: "settings", render: settingsScreen, rail: true, bar: false },
  { path: "/more", title: t.nav.more, icon: "more", render: moreScreen, rail: false, bar: true },
];

export interface Toast { text: string; actions?: { label: string; run: () => void }[]; at: number }

export class App {
  db!: Persistence;
  api = new Api();
  session: Session | null = null;
  store: RecordStore | null = null;
  model!: Model;
  sync: SyncEngine | null = null;
  syncState: SyncState = "idle";
  path = location.pathname;
  /** Per-screen UI state (tabs, open editors, …); survives re-renders, not reloads. */
  ui: Record<string, unknown> = {};
  toastMsg: Toast | null = null;
  private timer: number | undefined;
  private toastTimer: number | undefined;
  private scheduled = false;

  constructor(private readonly root: HTMLElement, readonly now: () => number = Date.now) {}

  async start() {
    try { this.db = await openIdb(); } catch { this.db = memoryPersistence(); }
    window.addEventListener("popstate", () => { this.path = location.pathname; this.refresh(); });
    window.addEventListener("online", () => this.syncNow());
    document.addEventListener("visibilitychange", () => { if (!document.hidden) this.syncNow(); });
    window.matchMedia("(prefers-color-scheme: dark)").addEventListener?.("change", () => this.refresh());
    const s = await loadSession(this.db);
    if (s) await this.open(s);
    this.refresh();
  }

  private async open(s: Session) {
    this.session = s;
    this.api.token = s.token;
    this.store = new RecordStore(this.db, s.deviceId, this.now);
    this.model = new Model(this.store, this.now);
    await this.store.load();
    this.store.subscribe(() => { this.refresh(); this.scheduleSync(); });
    this.sync = new SyncEngine(this.store, this.db, this.api, this.now);
    this.sync.onState = (st) => { this.syncState = st; this.refresh(); };
    this.sync.onUnauthorized = () => void this.signOut();
    void this.sync.sync();
    clearInterval(this.timer);
    this.timer = window.setInterval(() => this.syncNow(), 30_000);
  }

  async login(password: string, deviceName: string) {
    const id = await deviceId(this.db);
    const res = await this.api.login(password, deviceName, id);
    const s: Session = { token: res.token, deviceId: res.deviceId, deviceName };
    await saveSession(this.db, s);
    await this.open(s);
    this.go("/");
  }

  async signOut() {
    clearInterval(this.timer);
    try { if (this.session) await this.api.revoke(this.session.deviceId); } catch { /* already revoked or offline */ }
    await clearSession(this.db);
    this.session = null;
    this.store = null;
    this.sync = null;
    this.api.token = null;
    this.ui = {};
    this.go("/");
  }

  syncNow() { void this.sync?.sync(); }
  private syncDebounce: number | undefined;
  private scheduleSync() {
    clearTimeout(this.syncDebounce);
    this.syncDebounce = window.setTimeout(() => this.syncNow(), 400);
  }

  go(path: string) {
    if (path !== location.pathname + location.search) history.pushState(null, "", path);
    this.path = path.split("?")[0];
    window.scrollTo(0, 0);
    this.refresh();
  }

  toast(text: string, actions?: Toast["actions"], ms = 6000) {
    this.toastMsg = { text, actions, at: this.now() };
    clearTimeout(this.toastTimer);
    this.toastTimer = window.setTimeout(() => { this.toastMsg = null; this.refresh(); }, ms);
    this.refresh();
  }

  /** Run an action, turning failures into a calm toast. */
  async act(fn: () => Promise<unknown>) {
    try { await fn(); } catch (e) {
      console.error(e);
      this.toast(e instanceof ApiError && e.status !== 500 ? e.message : t.error.generic);
    }
  }

  refresh() {
    if (this.scheduled) return;
    this.scheduled = true;
    queueMicrotask(() => {
      this.scheduled = false;
      render(this.view(), this.root);
    });
  }

  route(): Route {
    const r = ROUTES.find((x) => x.path === this.path) ?? ROUTES[0];
    if (r.module && !this.model.isOn(r.module)) return ROUTES[0];
    return r;
  }

  private navItems(where: "rail" | "bar") {
    const current = this.route();
    return ROUTES.filter((r) => r[where] && (!r.module || this.model.isOn(r.module))).map((r) => {
      const selected = r === current || (where === "bar" && !current.bar && r.path === "/more");
      return html`<m3e-nav-item ?selected=${selected} @click=${(e: Event) => { e.preventDefault(); this.go(r.path); }}
        aria-current=${selected ? "page" : "false"} data-path=${r.path}>${icon(r.icon, { slot: "icon" })}${r.title}</m3e-nav-item>`;
    });
  }

  private view(): TemplateResult {
    if (!this.session || !this.store) return html`${loginScreen(this)}${this.toastView()}`;
    const r = this.route();
    return html`
      <div class="shell">
        <m3e-nav-rail class="rail" mode="compact" aria-label="Klean">
          <div class="rail-brand">${icon("today", { size: 32, label: t.appName })}</div>
          ${this.navItems("rail")}
        </m3e-nav-rail>
        <main class="content" id="main">${r.render(this)}</main>
        <m3e-nav-bar class="bar" mode="compact" aria-label="Klean">${this.navItems("bar")}</m3e-nav-bar>
      </div>
      ${this.toastView()}`;
  }

  private toastView() {
    const m = this.toastMsg;
    if (!m) return nothing;
    return html`<div class="toast" role="status" aria-live="polite"><span class="grow">${m.text}</span>
      ${m.actions?.map((a) => html`<m3e-button variant="text" @click=${() => { this.toastMsg = null; a.run(); this.refresh(); }}>${a.label}</m3e-button>`)}</div>`;
  }

  /** The page header: title, sync status and optional actions. */
  head(title: string, actions: TemplateResult | typeof nothing = nothing) {
    const pending = this.store?.pending().length ?? 0;
    const label = this.syncState === "syncing" ? t.sync.syncing : this.syncState === "offline" ? t.sync.offline
      : this.syncState === "error" ? t.sync.error : pending ? t.sync.pending(pending) : t.sync.synced;
    return html`<header class="page-head"><h1>${title}</h1>
      <span class="status" data-state=${this.syncState} data-testid="sync-status"><span class="dot"></span>${label}</span>${actions}</header>`;
  }
}
