import { html, nothing, type TemplateResult } from "lit";
import type { App } from "../app";
import { t } from "../copy";
import { parseNaturalTime, parseIsoDate, isoDate, type LocalDate } from "../capture/natural-time";
import { localNow } from "../capture/capture";
import { atLocal, num, str } from "../model";
import type { Row } from "../data/sync-types";
import { card, field, fmtMinutes, fmtShortDay, fmtTime, formValues, hhmm, parseHhmm, resetForm, tabs, textarea } from "../ui/parts";
import { icon } from "../ui/icons";

type Tab = "today" | "upcoming" | "lists" | "reminders" | "done";

export function tasksScreen(app: App): TemplateResult {
  const m = app.model;
  const tab = (app.ui.tasksTab as Tab) ?? "today";
  const options: Record<string, string> = { ...t.tasks.tabs };
  if (!m.isOn("reminders")) delete options.reminders;
  const pick = (k: string) => { app.ui.tasksTab = k; app.refresh(); };
  const addTask = (e: Event) => {
    const { title } = formValues(e);
    if (!title?.trim()) return;
    resetForm(e);
    const p = parseNaturalTime(title, localNow(app.now()));
    void app.act(() => m.addTask({ title: p.rest || title, dueDate: p.date ?? (tab === "today" ? m.today() : null), dueMinutes: p.minutes }));
  };
  const body = tab === "reminders" ? remindersTab(app) : html`
    <form class="inline" @submit=${addTask} data-testid="add-task-form" style="margin-bottom:var(--s3)">
      ${field({ label: t.tasks.add, name: "title", placeholder: t.tasks.addPlaceholder, testid: "add-task-input" })}
      <m3e-button variant="filled" type="submit">${icon("add", { slot: "icon" })}${t.capture.submit}</m3e-button>
    </form>
    ${tab === "today" ? todayTab(app) : tab === "upcoming" ? upcomingTab(app) : tab === "lists" ? listsTab(app) : doneTab(app)}`;
  return html`${app.head(t.nav.tasks)}${tabs(tab, options as Record<Tab, string>, pick, t.nav.tasks)}${body}${editDialog(app)}`;
}

function todayTab(app: App) {
  const m = app.model;
  const still = m.stillWant();
  const due = m.tasksDueToday();
  return html`<div class="stack">
    ${still.length ? card({ title: t.tasks.stillWant, icon: "tasks", tone: "tertiary", testid: "still-want" }, html`
      <p class="muted">${t.tasks.stillWantLead}</p>
      <ul class="list">${still.map((x) => html`<li class="item"><span class="text">${str(x.title)} <span class="meta">${fmtShortDay(parseIsoDate(str(x.due_date)!))}</span></span>
        <m3e-button variant="tonal" @click=${() => app.act(() => m.updateTask(str(x.id)!, { due_date: isoDate(m.today()), status: "OPEN" }))}>${t.tasks.keep}</m3e-button>
        <m3e-button variant="text" @click=${() => app.act(() => m.updateTask(str(x.id)!, { due_date: null, due_minutes: null, status: "SOMEDAY" }))}>${t.tasks.letGo}</m3e-button></li>`)}</ul>`) : nothing}
    <section class="card" aria-label=${t.tasks.tabs.today}>
      ${due.length ? html`<ul class="list" data-testid="tasks-today">${due.map((x) => taskRow(app, x))}</ul>` : html`<p class="empty">${t.tasks.noneToday}</p>`}
    </section>
  </div>`;
}

function upcomingTab(app: App) {
  const groups = new Map<string, Row[]>();
  for (const x of app.model.upcoming()) {
    const k = str(x.due_date)!;
    groups.set(k, [...(groups.get(k) ?? []), x]);
  }
  if (!groups.size) return html`<section class="card"><p class="empty">${t.tasks.noneUpcoming}</p></section>`;
  return html`<div class="stack">${[...groups].map(([d, xs]) => card({ title: fmtShortDay(parseIsoDate(d)) }, html`<ul class="list">${xs.map((x) => taskRow(app, x, false))}</ul>`))}</div>`;
}

function listsTab(app: App) {
  const m = app.model;
  const lists = m.lists();
  const open = m.openTasks();
  const known = new Set(lists.map((l) => str(l.id)));
  const orphan = open.filter((x) => !known.has(str(x.list_id)));
  return html`<div class="stack">
    ${lists.map((l) => {
      const xs = open.filter((x) => x.list_id === l.id);
      return card({ title: str(l.name) ?? "" }, xs.length ? html`<ul class="list">${xs.map((x) => taskRow(app, x))}</ul>` : html`<p class="empty">${t.tasks.listEmpty}</p>`);
    })}
    ${orphan.length ? card({ title: t.tasks.noDate }, html`<ul class="list">${orphan.map((x) => taskRow(app, x))}</ul>`) : nothing}
    ${!lists.length && !orphan.length ? html`<section class="card"><p class="empty">${t.tasks.listEmpty}</p></section>` : nothing}
  </div>`;
}

function doneTab(app: App) {
  const done = app.model.done();
  return html`<section class="card">${done.length ? html`<ul class="list">${done.map((x) => html`<li class="item done">
    <span class="text">${str(x.title)}</span>
    <m3e-button variant="text" @click=${() => app.act(() => app.model.reopenTask(str(x.id)!))}>${t.tasks.reopen}</m3e-button></li>`)}</ul>`
    : html`<p class="empty">${t.tasks.noneDone}</p>`}</section>`;
}

/** One task: a checkbox that completes it (repeats roll forward), its title and date, and edit. */
export function taskRow(app: App, x: Row, showDate = true): TemplateResult {
  const id = str(x.id)!;
  const subs = app.model.subtasks(id).filter((s) => str(s.status) !== "DONE");
  const due = str(x.due_date);
  const minutes = num(x.due_minutes);
  const when = [showDate && due && due !== isoDate(app.model.today()) ? fmtShortDay(parseIsoDate(due)) : null, minutes !== null ? fmtMinutes(minutes) : null].filter(Boolean).join(" · ");
  return html`<li class="item" data-testid="task-row" data-task-id=${id}>
    <m3e-checkbox aria-label="${t.tasks.done}: ${str(x.title)}" @change=${() => app.act(() => app.model.completeTask(id))}></m3e-checkbox>
    <span class="text"><span data-testid="task-title">${str(x.title)}</span>
      ${when || subs.length || x.recurrence ? html`<br /><span class="meta">${[when, subs.length ? t.tasks.subtasks(subs.length) : null, x.recurrence ? t.tasks.repeats : null].filter(Boolean).join(" · ")}</span>` : nothing}</span>
    <m3e-icon-button aria-label="${t.tasks.edit}: ${str(x.title)}" @click=${() => { app.ui.editTask = id; app.refresh(); queueMicrotask(() => (document.getElementById("task-edit") as HTMLDialogElement | null)?.showModal()); }}>${icon("edit")}</m3e-icon-button>
  </li>`;
}

function editDialog(app: App) {
  const id = app.ui.editTask as string | undefined;
  const x = id ? app.model.store.get("task", id) : undefined;
  const close = () => { (document.getElementById("task-edit") as HTMLDialogElement | null)?.close(); app.ui.editTask = undefined; app.refresh(); };
  const save = (e: Event) => {
    const v = formValues(e);
    void app.act(async () => {
      await app.model.updateTask(id!, {
        title: v.title.trim() || str(x!.title), notes: v.notes.trim() || null,
        due_date: v.due || null, due_minutes: v.due && v.time ? parseHhmm(v.time) : null,
      });
      close();
    });
  };
  return html`<dialog class="sheet" id="task-edit" @close=${() => { app.ui.editTask = undefined; }}>
    ${x ? html`<form class="stack" @submit=${save}>
      <h2>${t.tasks.edit}</h2>
      ${field({ label: t.tasks.title, name: "title", value: str(x.title) ?? "" })}
      ${textarea({ label: t.tasks.notes, name: "notes", value: str(x.notes) ?? "", rows: 3 })}
      <div class="fields">
        ${field({ label: t.tasks.due, name: "due", type: "date", value: str(x.due_date) ?? "" })}
        ${field({ label: t.tasks.time, name: "time", type: "time", value: num(x.due_minutes) !== null ? hhmm(num(x.due_minutes)!) : "" })}
      </div>
      <div class="actions" style="justify-content:space-between">
        <m3e-button variant="text" @click=${() => app.act(async () => { await app.model.deleteTask(id!); close(); })}>${icon("delete", { slot: "icon" })}${t.tasks.delete}</m3e-button>
        <span class="actions"><m3e-button variant="text" @click=${close}>${t.tasks.cancel}</m3e-button><m3e-button variant="filled" type="submit">${t.tasks.save}</m3e-button></span>
      </div>
    </form>` : nothing}
  </dialog>`;
}

function remindersTab(app: App) {
  const m = app.model;
  const add = (e: Event) => {
    const { title } = formValues(e);
    if (!title?.trim()) return;
    resetForm(e);
    const p = parseNaturalTime(title, localNow(app.now()));
    const at = p.date && p.minutes !== null ? atLocal(p.date as LocalDate, p.minutes) : app.now() + 3_600_000;
    void app.act(() => m.addReminder(p.rest || title, at));
  };
  const rs = m.reminders();
  return html`<form class="inline" @submit=${add} style="margin-bottom:var(--s3)">
      ${field({ label: t.tasks.addReminder, name: "title", placeholder: t.tasks.reminderPlaceholder })}
      <m3e-button variant="filled" type="submit">${icon("add", { slot: "icon" })}${t.capture.submit}</m3e-button>
    </form>
    <section class="card">${rs.length ? html`<ul class="list">${rs.map((r) => {
      const at = num(r.snoozed_until) ?? num(r.fire_at)!;
      return html`<li class="item"><span class="time">${fmtTime(at)}</span>
        <span class="text">${str(r.title)}<br /><span class="meta">${fmtShortDay(localNow(at).date)}${r.is_alarm ? ` · ${t.tasks.alarm}` : ""}</span></span>
        <m3e-button variant="tonal" @click=${() => app.act(() => m.setReminderState(str(r.id)!, "DONE"))}>${t.tasks.done}</m3e-button>
        <m3e-button variant="text" @click=${() => app.act(() => m.setReminderState(str(r.id)!, "DISMISSED"))}>${t.tasks.dismiss}</m3e-button></li>`;
    })}</ul>` : html`<p class="empty">${t.tasks.noneReminders}</p>`}</section>`;
}
