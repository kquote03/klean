// The one Add button, its menu ("Type something…" plus a line per module that is on) and the capture sheet each
// line opens. Mirrors Android's KleanAddButton + AddMenu + CaptureSheet: capture never lives on a module screen.
import { html, nothing } from "lit";
import type { App } from "../app";
import { t } from "../copy";
import { capture, undo, type CaptureResult } from "../capture/capture";
import type { CandidateAction, ModuleId } from "../capture/rules";
import { addMenuEntries, type AddEntry } from "../nav";
import { icon } from "../ui/icons";
import { closeSheet, openSheet } from "../ui/kit";

const ENTRY_ICON: Record<AddEntry, string> = { type: "text", sleep: "sleep", workout: "workout", food: "note", reminder: "bell", event: "event", task: "tasks" };
const TARGET: Partial<Record<AddEntry, CandidateAction>> = {
  workout: { moduleId: "workout", actionType: "log_workout", confidence: 1 },
  food: { moduleId: "diet", actionType: "log_food", confidence: 1 },
  reminder: { moduleId: "reminders", actionType: "add_reminder", confidence: 1 },
  event: { moduleId: "calendar", actionType: "add_event", confidence: 1 },
  task: { moduleId: "tasks", actionType: "add_task", confidence: 1 },
};
const SHEET = "capture-sheet";
const MENU = "add-menu";

type Popover = HTMLElement & { showPopover(): void; hidePopover(): void };
const menu = () => document.getElementById(MENU) as Popover | null;

export function addButton(_app: App) {
  return html`<button type="button" class="fab" popovertarget=${MENU} aria-haspopup="menu" aria-label=${t.add.open} title=${t.add.open} data-testid="add">${icon("add", { size: 28 })}</button>`;
}

/** Open the capture sheet aimed at one Add-menu line (or at nothing: "Type something…"). */
export function openCapture(app: App, entry: AddEntry = "type") {
  menu()?.hidePopover();
  app.ui.addTarget = entry;
  app.refresh();
  openSheet(SHEET);
  queueMicrotask(() => queueMicrotask(() => (document.getElementById("capture-input") as HTMLInputElement | null)?.focus()));
}

export function addMenu(app: App) {
  const entries = addMenuEntries(app.isOn);
  // Where the Add button sits, so the close button lands exactly on it.
  const place = (e: Event) => {
    if ((e as ToggleEvent).newState !== "open") return;
    const fab = document.querySelector(".fab:not(.close)")?.getBoundingClientRect();
    const close = (e.currentTarget as HTMLElement).querySelector(".close") as HTMLElement | null;
    if (fab && close) Object.assign(close.style, { left: `${fab.left}px`, top: `${fab.top}px` });
  };
  return html`<div id=${MENU} popover class="add-menu" data-testid="add-menu" @beforetoggle=${place}>
    <div class="scrim" @click=${() => menu()?.hidePopover()}></div>
    <div class="add-items" role="menu" aria-label=${t.add.open}>
      ${entries.map((e) => html`<button type="button" role="menuitem" class=${e === "type" ? "type" : ""} data-testid="add-${e}" @click=${() => openCapture(app, e)}>${icon(ENTRY_ICON[e])}${t.add[e]}</button>`)}
    </div>
    <button type="button" class="fab close" aria-label=${t.add.close} @click=${() => menu()?.hidePopover()}>${icon("close")}</button>
  </div>`;
}

export function captureSheet(app: App) {
  const entry = (app.ui.addTarget as AddEntry | undefined) ?? "type";
  const close = () => closeSheet(SHEET);
  const submit = (e: Event) => {
    e.preventDefault();
    const input = (e.currentTarget as HTMLFormElement).elements.namedItem("text") as HTMLInputElement;
    const text = input.value.trim();
    if (!text) return;
    input.value = "";
    close();
    void app.act(async () => announce(app, await capture(app.model, text, TARGET[entry]), text));
  };
  const open = app.model?.asleep();
  const sleep = () => { close(); void app.act(async () => app.toast((await app.model.toggleSleep()) === "bed" ? t.sleep.goodnight : t.sleep.morning)); };
  return html`<dialog class="sheet" id=${SHEET} aria-labelledby="capture-title" data-testid="capture-sheet">
    <div class="stack">
      <h2 id="capture-title">${t.add.title[entry]}</h2>
      ${entry === "sleep" ? html`
        <p class="lead-text">${open ? t.add.sleepLeadWake : t.add.sleepLeadBed}</p>
        <div class="actions end"><button type="button" class="btn text" @click=${close}>${t.add.cancel}</button>
          <button type="button" class="btn filled" data-testid="sleep-toggle" @click=${sleep}>${open ? t.sleep.wake : t.sleep.bed}</button></div>`
      : html`
        <form class="stack" @submit=${submit} data-testid="capture-form">
          <label class="capture-input"><span class="sr-only">${t.add.title[entry]}</span>
            <input id="capture-input" name="text" autocomplete="off" placeholder=${t.add.placeholder[entry] ?? ""} data-testid="capture-input" /></label>
          ${entry === "type" ? html`<p class="lead-text body-m">${t.add.lead}</p>` : nothing}
          <div class="actions end"><button type="button" class="btn text" @click=${close}>${t.add.cancel}</button>
            <button type="submit" class="btn filled" data-testid="capture-save">${t.add.save}</button></div>
        </form>`}
    </div>
  </dialog>`;
}

/** "Saved to Tasks · Undo · Move to …", like the phone's sheet after a capture. */
export function announce(app: App, r: CaptureResult, text: string) {
  const where = t.module[r.module] ?? r.module;
  app.toast(t.capture.savedTo(where), [
    { label: t.capture.undo, run: () => void app.act(async () => { await undo(app.model, r); app.toast(t.capture.undone); }) },
    ...r.alternatives.slice(0, 2).map((alt: { moduleId: ModuleId } & CandidateAction) => ({
      label: `${t.capture.moveTo} ${t.module[alt.moduleId]}`,
      run: () => void app.act(async () => { await undo(app.model, r); announce(app, await capture(app.model, text, alt), text); }),
    })),
  ], 8000);
}
