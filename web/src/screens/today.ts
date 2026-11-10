import { html, nothing, type TemplateResult } from "lit";
import type { App } from "../app";
import { t } from "../copy";
import { capture, undo, type CaptureResult } from "../capture/capture";
import { isoDate, plusDays } from "../capture/natural-time";
import { atLocal, minuteOfDay, num, str, type ModuleKey } from "../model";
import { card, fmtDay, fmtDuration, fmtMinutes, fmtTime, formValues, resetForm } from "../ui/parts";
import { icon } from "../ui/icons";
import { taskRow } from "./tasks";

/** DashboardSlot order on Android. The arrangement is device-local (H4): this browser keeps its own. */
export const SLOTS = ["APPROVALS", "TASKS", "AGENDA", "BIRTHDAYS", "SLEEP", "WORKOUT", "DIET"] as const;
export type Slot = (typeof SLOTS)[number];
const SLOT_MODULE: Record<Slot, ModuleKey | null> = {
  APPROVALS: null, TASKS: "tasks", AGENDA: "calendar", BIRTHDAYS: "birthdays", SLEEP: "sleep", WORKOUT: "workout", DIET: "diet",
};
const LAYOUT_KEY = "klean.today.layout";

export interface Layout { order: Slot[]; hidden: Slot[] }
export function loadLayout(storage: Pick<Storage, "getItem"> = localStorage): Layout {
  try {
    const l = JSON.parse(storage.getItem(LAYOUT_KEY) ?? "null") as Layout | null;
    if (l) {
      const order = [...l.order.filter((s) => SLOTS.includes(s)), ...SLOTS.filter((s) => !l.order.includes(s))];
      return { order, hidden: l.hidden.filter((s) => SLOTS.includes(s)) };
    }
  } catch { /* fall back to the default */ }
  return { order: [...SLOTS], hidden: [] };
}
export function saveLayout(l: Layout, storage: Pick<Storage, "setItem"> = localStorage) { storage.setItem(LAYOUT_KEY, JSON.stringify(l)); }

export function todayScreen(app: App): TemplateResult {
  const m = app.model;
  const layout = loadLayout();
  const hour = new Date(app.now()).getHours();
  const slots = layout.order.filter((s) => !layout.hidden.includes(s) && (SLOT_MODULE[s] === null || m.isOn(SLOT_MODULE[s]!)));
  const cards = slots.map((s) => SLOT_VIEW[s](app)).filter((c): c is TemplateResult => c !== null);
  return html`
    ${app.head(t.greeting(hour), html`<m3e-button variant="text" class="head-action" aria-label=${t.today.arrange} @click=${() => openArrange(app)} data-testid="arrange">${icon("tune", { slot: "icon" })}<span class="wide-only">${t.today.arrange}</span></m3e-button>`)}
    <p class="muted" style="margin-top:calc(-1 * var(--s2));margin-bottom:var(--s3)">${fmtDay(m.today())}</p>
    <div class="today">
      <div class="stack">
        ${captureBox(app)}
        <div class="today-cards" data-testid="today-cards">${cards}</div>
      </div>
      <aside class="today-side" aria-label=${t.today.yourDay}>${yourDay(app)}</aside>
    </div>
    ${arrangeDialog(app, layout)}`;
}

// ---- universal capture ----

export function captureBox(app: App) {
  const submit = (e: Event) => {
    const { text } = formValues(e);
    if (!text?.trim()) return;
    resetForm(e);
    void app.act(async () => announce(app, await capture(app.model, text), text));
  };
  return html`<form class="capture inline" @submit=${submit} data-testid="capture-form">
    <m3e-form-field variant="outlined" hide-subscript="always">
      <label slot="label" for="capture-input">${t.capture.label}</label>
      <input id="capture-input" name="text" autocomplete="off" placeholder=${t.capture.placeholder} data-testid="capture-input" />
    </m3e-form-field>
    <m3e-button variant="filled" type="submit">${icon("add", { slot: "icon" })}${t.capture.submit}</m3e-button>
  </form>`;
}

function announce(app: App, r: CaptureResult, text: string) {
  const where = t.module[r.module] ?? r.module;
  app.toast(t.capture.savedTo(where), [
    { label: t.capture.undo, run: () => void app.act(async () => { await undo(app.model, r); app.toast(t.capture.undone); }) },
    ...r.alternatives.slice(0, 3).map((alt) => ({
      label: `${t.capture.moveTo} ${t.module[alt.moduleId]}`,
      run: () => void app.act(async () => { await undo(app.model, r); announce(app, await capture(app.model, text, alt), text); }),
    })),
  ], 8000);
}

// ---- cards ----

const SLOT_VIEW: Record<Slot, (app: App) => TemplateResult | null> = {
  APPROVALS(app) {
    const pending = app.model.approvals();
    if (!pending.length) return null;
    return card({ title: t.today.slots.APPROVALS, icon: "approvals", tone: "primary", emphasis: true, testid: "card-approvals",
      action: html`<m3e-button variant="filled" @click=${() => app.go("/approvals")}>${t.approvals.review}</m3e-button>` }, html`
      <p>${t.approvals.waiting(pending.length)}</p>
      <ul class="list">${pending.slice(0, 2).map((a) => {
        const d = app.model.delegationFor(a);
        const task = d ? app.model.store.get("task", str(d.task_id) ?? "") : undefined;
        const action = (str(a.action) ?? "").replace(/_/g, " ");
        return html`<li class="item"><span class="text">${action.charAt(0).toUpperCase() + action.slice(1)}${task ? html`<br /><span class="meta">${str(task.title)}</span>` : nothing}</span></li>`;
      })}</ul>`);
  },
  TASKS(app) {
    const m = app.model;
    const due = m.tasksDueToday();
    const still = m.stillWant();
    return card({ title: t.today.slots.TASKS, icon: "tasks", testid: "card-tasks",
      action: html`<m3e-button variant="text" @click=${() => app.go("/tasks")}>${t.nav.tasks}</m3e-button>` }, html`
      ${still.length ? html`<m3e-button variant="tonal" @click=${() => { app.ui.tasksTab = "today"; app.go("/tasks"); }}>${t.tasks.stillWant} (${still.length})</m3e-button>` : nothing}
      ${due.length ? html`<ul class="list">${due.slice(0, 6).map((x) => taskRow(app, x))}</ul>` : html`<p class="empty">${t.tasks.noneToday}</p>`}`);
  },
  AGENDA(app) {
    const items = agendaItems(app);
    return card({ title: t.today.slots.AGENDA, icon: "calendar", tone: "tertiary", testid: "card-agenda",
      action: html`<m3e-button variant="text" @click=${() => app.go("/calendar")}>${t.nav.calendar}</m3e-button>` },
      items.length ? html`<ul class="list">${items.map((i) => html`<li class="item"><span class="time">${i.time}</span><span class="text">${i.title}</span></li>`)}</ul>`
        : html`<p class="empty">${t.calendar.nothing}</p>`);
  },
  BIRTHDAYS(app) {
    const soon = app.model.birthdays(14);
    if (!soon.length) return null;
    return card({ title: t.today.slots.BIRTHDAYS, icon: "cake", tone: "tertiary", testid: "card-birthdays" }, html`
      <ul class="list">${soon.slice(0, 4).map((b) => html`<li class="item"><span class="text">${str(b.contact.display_name)}${b.turns ? html` <span class="meta">${t.contacts.turns(b.turns)}</span>` : nothing}</span>
        <span class="meta">${b.inDays === 0 ? t.contacts.todayLabel : b.inDays === 1 ? t.contacts.tomorrow : t.contacts.inDays(b.inDays)}</span></li>`)}</ul>`);
  },
  SLEEP(app) {
    const m = app.model;
    const open = m.asleep();
    const last = m.lastNight();
    return card({ title: t.today.slots.SLEEP, icon: "sleep", tone: "primary", testid: "card-sleep" }, html`
      <p>${open ? t.sleep.asleepSince(fmtTime(num(open.bed_at)!)) : last ? html`${t.sleep.lastNight}: <strong>${fmtDuration(num(last.wake_at)! - num(last.bed_at)!)}</strong>` : html`<span class="muted">${t.sleep.noLastNight}</span>`}</p>
      <div class="actions"><m3e-button variant="tonal" @click=${() => app.act(async () => app.toast((await m.toggleSleep()) === "bed" ? t.sleep.goodnight : t.sleep.morning))}>${open ? t.sleep.wake : t.sleep.bed}</m3e-button></div>`);
  },
  WORKOUT(app) {
    const m = app.model;
    const last = m.sessions()[0];
    const active = m.activeSession();
    return card({ title: t.today.slots.WORKOUT, icon: "workout", testid: "card-workout" }, html`
      <p>${t.workout.thisWeek(m.sessionsThisWeek())}</p>
      ${last ? html`<p class="muted">${str(last.notes) ?? t.workout.noteless} · ${fmtTime(num(last.started_at)!)}</p>` : nothing}
      <div class="actions"><m3e-button variant="tonal" @click=${() => app.act(async () => { if (!active) await m.startSession(); app.go("/workout"); })}>${active ? t.workout.inProgress : t.workout.start}</m3e-button></div>`);
  },
  DIET(app) {
    const m = app.model;
    const food = m.foodToday();
    return card({ title: t.today.slots.DIET, icon: "diet", testid: "card-diet",
      action: html`<m3e-button variant="text" @click=${() => app.go("/diet")}>${t.nav.diet}</m3e-button>` }, html`
      <p>${food.length ? t.diet.count(food.length) : html`<span class="muted">${t.diet.none}</span>`}</p>
      ${food.length ? html`<ul class="list">${food.slice(0, 3).map((f) => html`<li class="item"><span class="time">${fmtTime(num(f.eaten_at)!)}</span><span class="text">${str(f.raw_text)}</span></li>`)}</ul>` : nothing}`);
  },
};


interface DayItem { at: number; time: string; title: string; kind: string }

/** Today's agenda: events, reminders and birthdays (Calendar owns the AGENDA slot on Android). */
export function agendaItems(app: App): DayItem[] {
  const m = app.model;
  const today = m.today();
  const items: DayItem[] = [];
  if (m.isOn("calendar")) {
    for (const o of m.occurrences(today, plusDays(today, 1))) {
      items.push({ at: o.allDay ? 0 : o.start, time: o.allDay ? t.calendar.allDay : fmtTime(o.start), title: str(o.event.title) ?? "", kind: "event" });
    }
  }
  if (m.isOn("reminders")) {
    const from = atLocal(today), to = atLocal(plusDays(today, 1));
    for (const r of m.reminders()) {
      const at = num(r.snoozed_until) ?? num(r.fire_at)!;
      if (at >= from && at < to) items.push({ at, time: fmtTime(at), title: str(r.title) ?? "", kind: "reminder" });
    }
  }
  if (m.isOn("birthdays")) {
    for (const b of m.birthdays(0)) items.push({ at: 0, time: t.calendar.allDay, title: t.calendar.birthday(str(b.contact.display_name) ?? ""), kind: "birthday" });
  }
  return items.sort((a, b) => a.at - b.at);
}

/** The right pane: everything at a set time today, in order. */
function yourDay(app: App) {
  const m = app.model;
  const items = agendaItems(app).filter((i) => i.at > 0);
  if (m.isOn("tasks")) {
    const d = isoDate(m.today());
    for (const x of m.openTasks()) {
      if (x.due_date === d && num(x.due_minutes) !== null) items.push({ at: atLocal(m.today(), num(x.due_minutes)!), time: fmtMinutes(num(x.due_minutes)!), title: str(x.title) ?? "", kind: "task" });
    }
  }
  items.sort((a, b) => a.at - b.at);
  const nowMin = minuteOfDay(app.now());
  return card({ title: t.today.yourDay, icon: "today", tone: "primary", testid: "your-day" },
    items.length ? html`<ul class="list timeline">${items.map((i) => html`<li class="item ${minuteOfDay(i.at) < nowMin ? "past" : ""}"><span class="time">${i.time}</span><span class="text">${i.title}</span></li>`)}</ul>`
      : html`<p class="empty">${t.today.nothingTimed}</p>`);
}

// ---- Arrange Today (device-local) ----

function openArrange(app: App) {
  (document.getElementById("arrange-dialog") as HTMLDialogElement | null)?.showModal();
  app.refresh();
}

function arrangeDialog(app: App, layout: Layout) {
  const move = (i: number, d: number) => {
    const order = [...layout.order];
    const j = i + d;
    if (j < 0 || j >= order.length) return;
    [order[i], order[j]] = [order[j], order[i]];
    saveLayout({ ...layout, order });
    app.refresh();
  };
  const toggle = (s: Slot, on: boolean) => {
    const hidden = on ? layout.hidden.filter((x) => x !== s) : [...layout.hidden, s];
    saveLayout({ ...layout, hidden });
    app.refresh();
  };
  return html`<dialog class="sheet" id="arrange-dialog" aria-labelledby="arrange-title">
    <div class="stack">
      <h2 id="arrange-title">${t.today.arrange}</h2>
      <p class="muted">${t.today.arrangeLead}</p>
      <ul class="list">${layout.order.map((s, i) => html`<li class="item" data-slot=${s}>
        <span class="text">${t.today.slots[s]}</span>
        <m3e-icon-button aria-label="${t.today.up}: ${t.today.slots[s]}" ?disabled=${i === 0} @click=${() => move(i, -1)}>${icon("up")}</m3e-icon-button>
        <m3e-icon-button aria-label="${t.today.down}: ${t.today.slots[s]}" ?disabled=${i === layout.order.length - 1} @click=${() => move(i, 1)}>${icon("down")}</m3e-icon-button>
        <m3e-switch aria-label="${t.today.show}: ${t.today.slots[s]}" ?checked=${!layout.hidden.includes(s)} @change=${(e: Event) => toggle(s, (e.target as HTMLInputElement).checked)}></m3e-switch>
      </li>`)}</ul>
      <div class="actions" style="justify-content:flex-end"><m3e-button variant="filled" @click=${() => (document.getElementById("arrange-dialog") as HTMLDialogElement).close()}>${t.today.done}</m3e-button></div>
    </div>
  </dialog>`;
}
