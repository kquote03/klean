// The app shell: session, store, sync loop, grouped routing (Today · Plan · Track · People) and rendering with
// lit-html. Screens are plain functions of the App built from the Soft-cards kit (ui/kit.ts).
import { html, render, nothing, type TemplateResult } from "lit";
import { Api, ApiError } from "./api";
import { memoryPersistence, openIdb, type Persistence } from "./data/idb";
import { RecordStore } from "./data/store";
import { SyncEngine, type SyncState } from "./data/sync";
import { Model } from "./model";
import { clearSession, deviceId, loadSession, saveSession, type Session } from "./session";
import { t } from "./copy";
import { icon } from "./ui/icons";
import { segmentedTabs } from "./ui/kit";
import { loadLast, navGroups, pathOf, resolve, saveLast, type Dest, type GroupId, type Resolved } from "./nav";
import { loginScreen } from "./screens/login";
import { todayScreen } from "./screens/today";
import { tasksScreen } from "./screens/tasks";
import { calendarScreen } from "./screens/calendar";
import { peopleScreen } from "./screens/people";
import { weekScreen, workoutScreen, sleepScreen, dietScreen } from "./screens/track";
import { approvalsScreen } from "./screens/approvals";
import { settingsScreen } from "./screens/settings";
import { addButton, addMenu, captureSheet } from "./screens/add";

const DEST_VIEW: Record<Dest, (app: App) => TemplateResult> = {
  tasks: tasksScreen, calendar: calendarScreen,
  week: weekScreen, workout: workoutScreen, sleep: sleepScreen, diet: dietScreen,
  contacts: peopleScreen, birthdays: peopleScreen,
};
const GROUP_ICON: Record<GroupId, string> = { today: "today", plan: "plan", track: "track", people: "people" };
const DEST_LABEL: Record<Dest, string> = {
  tasks: t.nav.tasks, calendar: t.nav.calendar, week: t.nav.week, workout: t.nav.workout, sleep: t.nav.sleep,
  diet: t.nav.diet, contacts: t.nav.contacts, birthdays: t.nav.birthdays,
};
const GROUP_TONE = { plan: "plan", track: "track", people: "people" } as const;

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
  /** Per-screen UI state (views, open sheets, …); survives re-renders, not reloads. */
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

  isOn = (m: Parameters<Model["isOn"]>[0]) => this.model.isOn(m);

  /** Where the current path lands; group paths open on the module last used in that group. */
  route(): Resolved {
    const r = resolve(this.path, this.isOn, loadLast());
    if (r.kind === "dest") saveLast(r.group, r.dest);
    const canonical = pathOf(r);
    if (canonical !== this.path) { history.replaceState(null, "", canonical); this.path = canonical; }
    return r;
  }

  /** The segmented control across a group's modules (hidden when the group has one). */
  tabs(group: GroupId, current: Dest) {
    const entry = navGroups(this.isOn).find((g) => g.id === group);
    if (!entry) return nothing;
    return segmentedTabs({
      options: entry.dests.map((d) => [d, DEST_LABEL[d]] as [Dest, string]), current, label: t.nav[group],
      tone: GROUP_TONE[group as keyof typeof GROUP_TONE] ?? "neutral",
      pick: (d) => this.go(`/${group}/${d}`),
    });
  }

  private navItems(current: GroupId) {
    return navGroups(this.isOn).map((g) => {
      const selected = g.id === current;
      const path = g.id === "today" ? "/" : `/${g.id}`;
      return html`<m3e-nav-item ?selected=${selected} @click=${(e: Event) => { e.preventDefault(); this.go(path); }}
        aria-current=${selected ? "page" : "false"} data-path=${path} data-group=${g.id}>${icon(GROUP_ICON[g.id], { slot: "icon" })}${t.nav[g.id]}</m3e-nav-item>`;
    });
  }

  private view(): TemplateResult {
    if (!this.session || !this.store) return html`${loginScreen(this)}${this.toastView()}`;
    const r = this.route();
    const group: GroupId = r.kind === "dest" ? r.group : "today";
    const body = r.kind === "dest" ? DEST_VIEW[r.dest](this) : r.kind === "settings" ? settingsScreen(this) : r.kind === "approvals" ? approvalsScreen(this) : todayScreen(this);
    return html`
      <div class="shell">
        <m3e-nav-rail class="rail" mode="compact" aria-label=${t.nav.groups} data-clutter-exempt>${this.navItems(group)}</m3e-nav-rail>
        <main class="content" id="main">${body}</main>
        <m3e-nav-bar class="bar" mode="compact" aria-label=${t.nav.groups} data-clutter-exempt>${this.navItems(group)}</m3e-nav-bar>
      </div>
      ${addButton(this)}${addMenu(this)}${captureSheet(this)}
      ${this.toastView()}`;
  }

  private toastView() {
    const m = this.toastMsg;
    if (!m) return nothing;
    return html`<div class="toast" role="status" aria-live="polite"><span class="grow">${m.text}</span>
      ${m.actions?.map((a) => html`<button type="button" class="btn text" @click=${() => { this.toastMsg = null; a.run(); this.refresh(); }}>${a.label}</button>`)}</div>`;
  }

  /** Sync status: a quiet dot and a few words, in each screen's top bar. */
  status() {
    const pending = this.store?.pending().length ?? 0;
    const label = this.syncState === "syncing" ? t.sync.syncing : this.syncState === "offline" ? t.sync.offline
      : this.syncState === "error" ? t.sync.error : pending ? t.sync.pending(pending) : t.sync.synced;
    return html`<span class="status" data-state=${this.syncState} data-testid="sync-status"><span class="sdot"></span><span class="slabel">${label}</span></span>`;
  }
}
