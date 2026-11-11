// Track: Week (a neutral overview), Workout, Sleep and Diet, each content first: one tonal hero card with the
// week's bars, then recent entries as a list group. Logging lives in the Add menu (Log sleep, workout, food).
import { html, nothing, type TemplateResult } from "lit";
import type { App } from "../app";
import { t } from "../copy";
import { plusDays, sameDate, type LocalDate } from "../capture/natural-time";
import { localDate, num, startOfDay, str, weekStart } from "../model";
import type { Row } from "../data/sync-types";
import { icon } from "../ui/icons";
import { closeSheet, emptyState, groupedList, listRow, moduleCard, openSheet, screen, weekBars } from "../ui/kit";
import { field, fmtDuration, fmtTime, fmtWeekday, fmtWeekdayLong, formValues } from "../ui/parts";
import { openCapture } from "./add";

const track = (app: App, dest: "week" | "workout" | "sleep" | "diet", body: TemplateResult, extra: TemplateResult | typeof nothing = nothing) =>
  html`${screen({ title: t.nav.track, actions: app.status(), tabs: app.tabs("track", dest), testid: dest }, body)}${extra}`;

/** The 7 days ending today, oldest first. */
const lastWeek = (today: LocalDate) => Array.from({ length: 7 }, (_, i) => plusDays(today, i - 6));
const initial = (d: LocalDate) => fmtWeekday(d).charAt(0);
const minutes = (s: Row) => Math.max(0, Math.round(((num(s.ended_at) ?? num(s.started_at)!) - num(s.started_at)!) / 60000));
const dayOf = (ms: number) => localDate(ms);

function sleepWeek(app: App) {
  const today = app.model.today();
  const nights = app.model.nights().filter((n) => n.wake_at != null);
  const days = lastWeek(today).map((d) => {
    const ms = nights.filter((n) => sameDate(dayOf(num(n.wake_at)!), d)).reduce((a, n) => a + num(n.wake_at)! - num(n.bed_at)!, 0);
    return { d, ms };
  });
  const logged = days.filter((x) => x.ms > 0);
  const avg = logged.length ? logged.reduce((a, x) => a + x.ms, 0) / logged.length : 0;
  const bars = days.map((x) => ({ label: initial(x.d), value: x.ms / (10 * 3_600_000), on: sameDate(x.d, today), today: sameDate(x.d, today), title: `${fmtWeekday(x.d)} ${x.ms ? fmtDuration(x.ms) : "–"}` }));
  return { avg, count: logged.length, bars };
}

function workoutWeek(app: App) {
  const today = app.model.today();
  const sessions = app.model.sessions();
  const from = startOfDay(weekStart(today));
  const week = sessions.filter((s) => (num(s.started_at) ?? 0) >= from);
  const days = lastWeek(today).map((d) => ({ d, min: sessions.filter((s) => sameDate(dayOf(num(s.started_at)!), d)).reduce((a, s) => a + minutes(s), 0) }));
  const bars = days.map((x) => ({ label: initial(x.d), value: x.min / 75, on: x.min > 0, today: sameDate(x.d, today), title: `${fmtWeekday(x.d)} ${x.min ? t.workout.minutes(x.min) : "–"}` }));
  return { count: week.length, min: week.reduce((a, s) => a + minutes(s), 0), bars, last: sessions.find((s) => s.ended_at != null) };
}

// ---- Week ----

export function weekScreen(app: App): TemplateResult {
  const m = app.model;
  const cards: TemplateResult[] = [];
  if (m.isOn("sleep")) {
    const s = sleepWeek(app);
    cards.push(moduleCard({ tone: "track", label: t.track.thisWeek, big: s.count ? t.track.sleepAvg(fmtDuration(s.avg)) : t.track.noSleep, sub: s.count ? t.track.overNights(s.count) : undefined, extra: weekBars(s.bars, 96), testid: "week-sleep" }));
  }
  const minis: TemplateResult[] = [];
  if (m.isOn("workout")) {
    const w = workoutWeek(app);
    minis.push(moduleCard({ tone: "neutral", label: t.track.workouts, big: String(w.count), sub: t.track.workoutsSub(w.min, w.last ? fmtWeekdayLong(dayOf(num(w.last.started_at)!)) : null), testid: "week-workout" }));
  }
  if (m.isOn("diet")) {
    const f = m.foodToday();
    minis.push(moduleCard({ tone: "neutral", label: t.track.foodToday, big: f.length ? t.track.entries(f.length) : t.diet.nothingToday, sub: f.length ? `${str(f[0].raw_text)} · ${fmtTime(num(f[0].eaten_at)!)}` : undefined, testid: "week-food" }));
  }
  return track(app, "week", html`${cards}${minis.length ? html`<div class="mini-cards">${minis}</div>` : nothing}`);
}

// ---- Workout ----

export function workoutScreen(app: App): TemplateResult {
  const m = app.model;
  const active = m.activeSession();
  const w = workoutWeek(app);
  const history = m.sessions().filter((s) => s.ended_at != null).slice(0, 20);
  if (!history.length && !active) {
    return track(app, "workout", emptyState({ icon: "workout", text: t.workout.empty, action: { label: t.workout.start, run: () => start(app), testid: "start-workout" } }), sessionSheet(app));
  }
  const hero = moduleCard({ tone: "track", label: t.track.thisWeek, big: t.workout.week(w.count, w.min), extra: weekBars(w.bars, 72), testid: "workout-week" });
  const go = active
    ? html`<button type="button" class="saved" data-testid="workout-active" @click=${() => { app.ui.session = str(active.id); app.refresh(); openSheet("session-sheet"); }}>
        <span class="title-m">${t.workout.inProgress}</span><span class="body-m">${t.workout.since(fmtTime(num(active.started_at)!))} · ${t.workout.sets(m.sets(str(active.id)!).length)}</span>
        <span class="go"><span class="disc">${icon("add", { size: 20 })}</span>${t.workout.logSet}</span></button>`
    : html`<button type="button" class="saved" data-testid="start-workout" @click=${() => start(app)}>
        <span class="title-m">${t.workout.start}</span><span class="body-m">${t.workout.startLead}</span>
        <span class="go"><span class="disc">${icon("play", { size: 20 })}</span>${t.workout.startShort}</span></button>`;
  const rows = history.map((s) => {
    const sets = m.sets(str(s.id)!);
    const names = [...new Set(sets.map((x) => str(x.exercise)))].filter(Boolean) as string[];
    const sub = names.length ? names.slice(0, 3).join(", ") : num(s.distance_m) ? `${(num(s.distance_m)! / 1000).toFixed(1)} km` : undefined;
    return listRow({ title: str(s.notes) ?? t.workout.noteless, supporting: sub, meta: html`${fmtWeekdayLong(dayOf(num(s.started_at)!))}<br />${t.workout.minutes(minutes(s))}`,
      open: () => { app.ui.session = str(s.id); app.refresh(); openSheet("session-sheet"); }, label: str(s.notes) ?? t.workout.noteless, testid: "workout-row" });
  });
  return track(app, "workout", html`${hero}<div class="section">${go}</div>${groupedList([{ title: t.workout.recent, rows }], "workout-recent")}`, sessionSheet(app));
}

function start(app: App) {
  void app.act(async () => { app.ui.session = await app.model.startSession(); app.refresh(); openSheet("session-sheet"); });
}

/** A session: its sets, and while it runs, a form to log the next set, "+1 set" and Finish. */
function sessionSheet(app: App) {
  const m = app.model;
  const id = app.ui.session as string | undefined;
  const s = id ? m.store.get("workout_session", id) : undefined;
  const running = s && s.ended_at == null;
  const sets = id ? m.sets(id) : [];
  const addSet = (e: Event) => {
    const v = formValues(e);
    if (!v.exercise?.trim()) return;
    void app.act(() => m.addSet(id!, v.exercise, v.reps ? Number(v.reps) : null, v.weight ? Number(v.weight) : null));
  };
  const again = () => { const last = sets[sets.length - 1]; if (last) void app.act(() => m.addSet(id!, str(last.exercise)!, num(last.reps), num(last.weight_kg))); };
  return html`<dialog class="sheet" id="session-sheet" aria-labelledby="session-title">
    ${s ? html`<div class="stack">
      <h2 id="session-title">${running ? t.workout.inProgress : str(s.notes) ?? t.workout.noteless}</h2>
      <p class="lead-text">${[fmtWeekdayLong(dayOf(num(s.started_at)!)), fmtTime(num(s.started_at)!), running ? null : t.workout.minutes(minutes(s)), num(s.distance_m) ? `${(num(s.distance_m)! / 1000).toFixed(1)} km` : null].filter(Boolean).join(" · ")}</p>
      ${sets.length ? html`<ul class="list-group">${sets.map((x) => listRow({ title: str(x.exercise) ?? "", meta: [num(x.reps) !== null ? `${num(x.reps)} ×` : null, num(x.weight_kg) !== null ? `${num(x.weight_kg)} kg` : null].filter(Boolean).join(" ") }))}</ul>` : nothing}
      ${running ? html`<form class="stack" @submit=${addSet}>
        <div class="fields">
          ${field({ label: t.workout.exercise, name: "exercise", required: true, testid: "set-exercise" })}
          ${field({ label: t.workout.reps, name: "reps", type: "number", min: "0" })}
          ${field({ label: t.workout.weight, name: "weight", type: "number", min: "0", step: "0.5" })}
        </div>
        <div class="actions split">
          <button type="button" class="btn text" ?disabled=${!sets.length} @click=${again}>${t.workout.addSet}</button>
          <span class="actions"><button type="submit" class="btn tonal">${t.add.save}</button>
            <button type="button" class="btn filled" data-testid="finish-workout" @click=${() => app.act(async () => { await m.endSession(id!); closeSheet("session-sheet"); })}>${t.workout.end}</button></span>
        </div>
      </form>` : html`<div class="actions end"><button type="button" class="btn text" @click=${() => closeSheet("session-sheet")}>${t.contacts.close}</button></div>`}
    </div>` : nothing}
  </dialog>`;
}

// ---- Sleep ----

export function sleepScreen(app: App): TemplateResult {
  const m = app.model;
  const open = m.asleep();
  const last = m.lastNight();
  const nights = m.nights().filter((n) => n.wake_at != null).slice(0, 14);
  if (!nights.length && !open) {
    return track(app, "sleep", emptyState({ icon: "sleep", text: t.sleep.empty, action: { label: t.add.sleep, run: () => openCapture(app, "sleep") } }));
  }
  const w = sleepWeek(app);
  const hero = open
    ? moduleCard({ tone: "track", label: t.today.sleep, big: t.sleep.asleepSince(fmtTime(num(open.bed_at)!)), extra: weekBars(w.bars, 80), testid: "sleep-now" })
    : moduleCard({ tone: "track", label: t.sleep.lastNight, big: last ? fmtDuration(num(last.wake_at)! - num(last.bed_at)!) : t.sleep.noLastNight,
      sub: last ? t.today.timeRange(fmtTime(num(last.bed_at)!), fmtTime(num(last.wake_at)!)) : undefined, extra: weekBars(w.bars, 80), testid: "sleep-now" });
  const rows = nights.map((n) => {
    const bed = dayOf(num(n.bed_at)!), wake = dayOf(num(n.wake_at)!);
    const title = sameDate(bed, wake) ? fmtWeekdayLong(wake) : t.sleep.into(fmtWeekdayLong(bed), fmtWeekdayLong(wake));
    return listRow({ title, supporting: t.today.timeRange(fmtTime(num(n.bed_at)!), fmtTime(num(n.wake_at)!)), meta: fmtDuration(num(n.wake_at)! - num(n.bed_at)!) });
  });
  return track(app, "sleep", html`${hero}${groupedList([{ title: t.sleep.nights, rows }], "sleep-recent")}`);
}

// ---- Diet ----

export function dietScreen(app: App): TemplateResult {
  const m = app.model;
  const today = m.today();
  const from = startOfDay(plusDays(today, -6));
  const all = m.store.rows("food_entry").filter((f) => (num(f.eaten_at) ?? 0) >= from).sort((a, b) => num(b.eaten_at)! - num(a.eaten_at)!);
  if (!all.length) return track(app, "diet", emptyState({ icon: "diet", text: t.diet.empty, action: { label: t.add.food, run: () => openCapture(app, "food") } }));
  const todays = m.foodToday();
  const latest = todays[0];
  const hero = moduleCard({ tone: "track", label: t.diet.today, big: latest ? str(latest.raw_text) ?? "" : t.diet.nothingToday,
    sub: latest ? [fmtTime(num(latest.eaten_at)!), todays.length > 1 ? t.diet.count(todays.length) : null].filter(Boolean).join(" · ") : undefined, testid: "diet-today" });
  const rows = all.filter((f) => f !== latest).map((f) => {
    const items = m.foodItems(str(f.id)!);
    const d = dayOf(num(f.eaten_at)!);
    return listRow({ title: str(f.raw_text) ?? "", supporting: items.length ? items.map((i) => str(i.name)).join(", ") : undefined,
      meta: html`${sameDate(d, today) ? t.diet.today : fmtWeekdayLong(d)}<br />${fmtTime(num(f.eaten_at)!)}` });
  });
  return track(app, "diet", html`${hero}${groupedList([{ title: t.diet.earlier, rows }], "diet-recent")}`);
}
