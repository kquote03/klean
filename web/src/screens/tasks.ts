// Plan › Tasks: one calm list in list groups (Today, Coming up, Still want these?, No date). Check to finish,
// tap to open. New task, Lists, Reminders and Finished live in the top-bar overflow; adding is the Add menu.
import { html, nothing, type TemplateResult } from "lit";
import type { App } from "../app";
import { t } from "../copy";
import { isoDate, parseIsoDate } from "../capture/natural-time";
import { localNow } from "../capture/capture";
import { num, str } from "../model";
import type { Row } from "../data/sync-types";
import { icon } from "../ui/icons";
import { checkButton, closeSheet, emptyState, groupedList, listRow, openSheet, overflowMenu, screen, type MenuItem } from "../ui/kit";
import { field, fmtMinutes, fmtShortDay, fmtTime, fmtWeekday, formValues, hhmm, parseHhmm, textarea } from "../ui/parts";
import { openCapture } from "./add";

type View = "all" | "lists" | "reminders" | "finished";

export function tasksScreen(app: App): TemplateResult {
  const m = app.model;
  let view = (app.ui.tasksView as View) ?? "all";
  if (!m.isOn("tasks")) view = "reminders";
  if (view === "reminders" && !m.isOn("reminders")) view = "all";
  const pick = (v: View) => () => { app.ui.tasksView = v; app.refresh(); };
  const menu: MenuItem[] = [];
  if (m.isOn("tasks")) {
    menu.push({ label: t.tasks.newTask, run: () => openCapture(app, "task"), testid: "menu-new-task" });
    if (view !== "all") menu.push({ label: t.tasks.allTasks, run: pick("all"), testid: "menu-all" });
    menu.push({ label: t.tasks.lists, run: pick("lists"), testid: "menu-lists" });
  }
  if (m.isOn("reminders") && view !== "reminders") menu.push({ label: t.tasks.reminders, run: pick("reminders"), testid: "menu-reminders" });
  if (m.isOn("tasks")) menu.push({ label: t.tasks.finished, run: pick("finished"), testid: "menu-finished" });
  menu.push({ label: t.nav.settings, run: () => app.go("/settings") });
  const body = view === "reminders" ? remindersView(app) : view === "lists" ? listsView(app) : view === "finished" ? finishedView(app) : allView(app);
  return html`${screen({ title: t.nav.plan, actions: html`${app.status()}${overflowMenu("tasks-menu", t.nav.moreOptions, menu)}`, tabs: app.tabs("plan", "tasks"), testid: "tasks" }, body)}
    ${editDialog(app)}${reminderDialog(app)}`;
}

function allView(app: App) {
  const m = app.model;
  const today = m.tasksDueToday(), upcoming = m.upcoming(), still = m.stillWant(), undated = m.undated();
  if (!today.length && !upcoming.length && !still.length && !undated.length) {
    return emptyState({ icon: "tasks", text: t.tasks.empty, action: { label: t.tasks.newTask, run: () => openCapture(app, "task") } });
  }
  const s = t.tasks.sections;
  return groupedList([
    { title: s.today, rows: today.map((x) => taskRow(app, x, "time")) },
    { title: s.comingUp, rows: upcoming.map((x) => taskRow(app, x, "day")) },
    { title: s.stillWant, rows: still.map((x) => taskRow(app, x, "from")) },
    { title: s.noDate, rows: undated.map((x) => taskRow(app, x, "none")) },
  ], "tasks-list");
}

function listsView(app: App) {
  const m = app.model;
  const open = m.openTasks();
  const lists = m.lists();
  const known = new Set(lists.map((l) => str(l.id)));
  const sections = lists.map((l) => ({ title: str(l.name) ?? "", rows: open.filter((x) => x.list_id === l.id).map((x) => taskRow(app, x, "day")) }));
  sections.push({ title: t.tasks.noDate, rows: open.filter((x) => !known.has(str(x.list_id))).map((x) => taskRow(app, x, "day")) });
  if (!sections.some((x) => x.rows.length)) return emptyState({ icon: "tasks", text: t.tasks.listEmpty });
  return groupedList(sections, "tasks-lists");
}

function finishedView(app: App) {
  const done = app.model.done();
  if (!done.length) return emptyState({ icon: "check", text: t.tasks.emptyFinished });
  return groupedList([{ title: t.tasks.finished, rows: done.map((x) => listRow({
    title: str(x.title) ?? "", done: true, lead: icon("check", { size: 20 }),
    trailing: html`<button type="button" class="btn text" @click=${() => app.act(() => app.model.reopenTask(str(x.id)!))}>${t.tasks.reopen}</button>`,
  })) }], "tasks-finished");
}

/** One task: a round check that finishes it (repeats roll forward), its title, and when. Tap opens it. */
export function taskRow(app: App, x: Row, when: "time" | "day" | "from" | "none"): TemplateResult {
  const id = str(x.id)!;
  const title = str(x.title) ?? "";
  const subs = app.model.subtasks(id).filter((s) => str(s.status) !== "DONE");
  const due = str(x.due_date);
  const minutes = num(x.due_minutes);
  const time = minutes !== null ? fmtMinutes(minutes) : null;
  const meta = when === "time" ? time
    : when === "day" && due ? [daysAway(app, due) < 7 ? fmtWeekday(parseIsoDate(due)) : fmtShortDay(parseIsoDate(due)), time].filter(Boolean).join(" ")
    : when === "from" && due ? t.tasks.from(fmtShortDay(parseIsoDate(due))) : null;
  const supporting = [subs.length ? t.tasks.subtasks(subs.length) : null, x.recurrence ? t.tasks.repeats : null].filter(Boolean).join(" · ");
  return listRow({
    leadAction: checkButton(`${t.tasks.done}: ${title}`, () => app.act(() => app.model.completeTask(id))),
    title: html`<span data-testid="task-title">${title}</span>`, supporting: supporting || undefined, meta: meta ?? undefined,
    open: () => { app.ui.editTask = id; app.refresh(); openSheet("task-edit"); }, label: t.tasks.open(title),
    testid: "task-row", attrs: { id },
  });
}

const daysAway = (app: App, iso: string) => Math.round((Date.parse(`${iso}T12:00:00`) - Date.parse(`${isoDate(app.model.today())}T12:00:00`)) / 864e5);

function editDialog(app: App) {
  const id = app.ui.editTask as string | undefined;
  const x = id ? app.model.store.get("task", id) : undefined;
  const close = () => { closeSheet("task-edit"); app.ui.editTask = undefined; app.refresh(); };
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
  const past = x && typeof x.due_date === "string" && x.due_date < isoDate(app.model.today());
  return html`<dialog class="sheet" id="task-edit" aria-labelledby="task-edit-title" @close=${() => { app.ui.editTask = undefined; }}>
    ${x ? html`<form class="stack" @submit=${save}>
      <h2 id="task-edit-title">${t.tasks.edit}</h2>
      ${past ? html`<div class="actions"><span class="grow lead-text">${t.tasks.stillWantLeadShort}</span>
        <button type="button" class="btn tonal" @click=${() => app.act(async () => { await app.model.updateTask(id!, { due_date: isoDate(app.model.today()), status: "OPEN" }); close(); })}>${t.tasks.keep}</button>
        <button type="button" class="btn text" @click=${() => app.act(async () => { await app.model.updateTask(id!, { due_date: null, due_minutes: null, status: "SOMEDAY" }); close(); })}>${t.tasks.someday}</button></div>` : nothing}
      ${field({ label: t.tasks.title, name: "title", value: str(x.title) ?? "" })}
      ${textarea({ label: t.tasks.notes, name: "notes", value: str(x.notes) ?? "", rows: 3 })}
      <div class="fields">
        ${field({ label: t.tasks.due, name: "due", type: "date", value: str(x.due_date) ?? "" })}
        ${field({ label: t.tasks.time, name: "time", type: "time", value: num(x.due_minutes) !== null ? hhmm(num(x.due_minutes)!) : "" })}
      </div>
      <div class="actions split">
        <button type="button" class="btn text" @click=${() => app.act(async () => { await app.model.deleteTask(id!); close(); })}>${t.tasks.delete}</button>
        <span class="actions"><button type="button" class="btn text" @click=${close}>${t.tasks.cancel}</button><button type="submit" class="btn filled">${t.tasks.save}</button></span>
      </div>
    </form>` : nothing}
  </dialog>`;
}

function remindersView(app: App) {
  const m = app.model;
  const rs = m.reminders();
  if (!rs.length) return emptyState({ icon: "bell", text: t.tasks.emptyReminders, action: { label: t.add.reminder, run: () => openCapture(app, "reminder") } });
  return groupedList([{ title: t.tasks.reminders, rows: rs.map((r) => {
    const at = num(r.snoozed_until) ?? num(r.fire_at)!;
    const title = str(r.title) ?? "";
    return listRow({ lead: icon("bell", { size: 20 }), title, supporting: [fmtShortDay(localNow(at).date), r.is_alarm ? t.tasks.alarm : null].filter(Boolean).join(" · "),
      meta: fmtTime(at), open: () => { app.ui.editReminder = str(r.id); app.refresh(); openSheet("reminder-sheet"); }, label: t.tasks.open(title), testid: "reminder-row" });
  }) }], "reminders-list");
}

function reminderDialog(app: App) {
  const id = app.ui.editReminder as string | undefined;
  const r = id ? app.model.store.get("reminder", id) : undefined;
  const done = (state: "DONE" | "DISMISSED") => app.act(async () => { await app.model.setReminderState(id!, state); closeSheet("reminder-sheet"); });
  return html`<dialog class="sheet" id="reminder-sheet" aria-labelledby="reminder-title">
    ${r ? html`<div class="stack">
      <h2 id="reminder-title">${str(r.title)}</h2>
      <p class="lead-text">${fmtShortDay(localNow(num(r.snoozed_until) ?? num(r.fire_at)!).date)} · ${fmtTime(num(r.snoozed_until) ?? num(r.fire_at)!)}</p>
      <div class="actions split">
        <button type="button" class="btn text" @click=${() => done("DISMISSED")}>${t.tasks.dismiss}</button>
        <span class="actions"><button type="button" class="btn text" @click=${() => closeSheet("reminder-sheet")}>${t.tasks.cancel}</button>
          <button type="button" class="btn filled" @click=${() => done("DONE")}>${t.tasks.done}</button></span>
      </div>
    </div>` : nothing}
  </dialog>`;
}
