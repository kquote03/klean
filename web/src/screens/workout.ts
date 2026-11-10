import { html, nothing, type TemplateResult } from "lit";
import type { App } from "../app";
import { t } from "../copy";
import { localDate, num, str } from "../model";
import { card, field, fmtShortDay, fmtTime, formValues } from "../ui/parts";
import { icon } from "../ui/icons";

export function workoutScreen(app: App): TemplateResult {
  const m = app.model;
  const active = m.activeSession();
  const addSet = (e: Event) => {
    const v = formValues(e);
    if (!active || !v.exercise?.trim()) return;
    void app.act(() => m.addSet(str(active.id)!, v.exercise, v.reps ? Number(v.reps) : null, v.weight ? Number(v.weight) : null));
  };
  /** "+1 set": repeat the last set of the session. */
  const again = () => {
    if (!active) return;
    const sets = m.sets(str(active.id)!);
    const last = sets[sets.length - 1];
    if (last) void app.act(() => m.addSet(str(active.id)!, str(last.exercise)!, num(last.reps), num(last.weight_kg)));
  };
  const history = m.sessions().filter((s) => s.ended_at != null).slice(0, 20);
  return html`${app.head(t.nav.workout)}
    <div class="stack">
      <p class="muted">${t.workout.thisWeek(m.sessionsThisWeek())}</p>
      ${active ? card({ title: t.workout.inProgress, icon: "workout", tone: "primary", emphasis: true, testid: "workout-active",
        action: html`<m3e-button variant="filled" @click=${() => app.act(() => m.endSession(str(active.id)!))}>${t.workout.end}</m3e-button>` }, html`
        <ul class="list">${m.sets(str(active.id)!).map((s) => html`<li class="item"><span class="text">${str(s.exercise)}</span>
          <span class="meta">${[num(s.reps) !== null ? `${num(s.reps)} ×` : null, num(s.weight_kg) !== null ? `${num(s.weight_kg)} kg` : null].filter(Boolean).join(" ")}</span></li>`)}</ul>
        <form class="stack" @submit=${addSet}>
          <div class="fields">
            ${field({ label: t.workout.exercise, name: "exercise", required: true })}
            ${field({ label: t.workout.reps, name: "reps", type: "number", min: "0" })}
            ${field({ label: t.workout.weight, name: "weight", type: "number", min: "0", step: "0.5" })}
          </div>
          <div class="actions">
            <m3e-button variant="filled" type="submit">${icon("add", { slot: "icon" })}${t.capture.submit}</m3e-button>
            <m3e-button variant="tonal" @click=${again} ?disabled=${!m.sets(str(active.id)!).length}>${t.workout.addSet}</m3e-button>
          </div>
        </form>`)
        : html`<div class="actions"><m3e-button variant="filled" @click=${() => app.act(() => m.startSession())} data-testid="start-workout">${icon("workout", { slot: "icon" })}${t.workout.start}</m3e-button></div>`}
      ${card({ title: t.workout.history, icon: "calendar" }, history.length ? html`<ul class="list">${history.map((s) => {
        const sets = m.sets(str(s.id)!);
        return html`<li class="item"><span class="time">${fmtShortDay(localDate(num(s.started_at)!))}</span>
          <span class="text">${str(s.notes) ?? t.workout.noteless}<br /><span class="meta">${fmtTime(num(s.started_at)!)}${sets.length ? ` · ${t.workout.sets(sets.length)}` : ""}</span></span></li>`;
      })}</ul>` : html`<p class="empty">${t.workout.none}</p>`)}
    </div>${nothing}`;
}
