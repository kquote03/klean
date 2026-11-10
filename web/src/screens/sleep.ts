import { html, type TemplateResult } from "lit";
import type { App } from "../app";
import { t } from "../copy";
import { localDate, num } from "../model";
import { card, field, fmtDuration, fmtShortDay, fmtTime, formValues, hhmm, parseHhmm } from "../ui/parts";

export function sleepScreen(app: App): TemplateResult {
  const m = app.model;
  const open = m.asleep();
  const last = m.lastNight();
  const q = m.quietHours();
  const nights = m.nights().filter((n) => n.wake_at != null).slice(0, 14);
  const save = (e: Event) => {
    const v = formValues(e);
    void app.act(async () => { await m.setQuietHours(parseHhmm(v.bed), parseHhmm(v.wake)); app.toast(t.settings.savedQuiet); });
  };
  return html`${app.head(t.nav.sleep)}
    <div class="stack">
      ${card({ title: open ? t.sleep.asleepSince(fmtTime(num(open.bed_at)!)) : t.sleep.lastNight, icon: "sleep", tone: "primary", emphasis: true, testid: "sleep-now" }, html`
        <p>${open ? "" : last ? html`<strong>${fmtDuration(num(last.wake_at)! - num(last.bed_at)!)}</strong> · ${fmtTime(num(last.bed_at)!)} – ${fmtTime(num(last.wake_at)!)}` : t.sleep.noLastNight}</p>
        <div class="actions"><m3e-button variant="filled" data-testid="sleep-toggle" @click=${() => app.act(async () => app.toast((await m.toggleSleep()) === "bed" ? t.sleep.goodnight : t.sleep.morning))}>${open ? t.sleep.wake : t.sleep.bed}</m3e-button></div>`)}
      ${card({ title: t.sleep.nights, icon: "calendar" }, nights.length ? html`<ul class="list">${nights.map((n) => html`<li class="item">
        <span class="time date">${fmtShortDay(localDate(num(n.wake_at)!))}</span>
        <span class="text">${fmtDuration(num(n.wake_at)! - num(n.bed_at)!)}<br /><span class="meta">${fmtTime(num(n.bed_at)!)} – ${fmtTime(num(n.wake_at)!)}</span></span></li>`)}</ul>`
        : html`<p class="empty">${t.sleep.none}</p>`)}
      ${card({ title: t.sleep.schedule, icon: "bell", tone: "tertiary" }, html`<p class="muted">${t.sleep.scheduleLead}</p>
        <form class="stack" @submit=${save}>
          <div class="fields">${field({ label: t.settings.from, name: "bed", type: "time", value: hhmm(q.bed) })}${field({ label: t.settings.to, name: "wake", type: "time", value: hhmm(q.wake) })}</div>
          <div class="actions"><m3e-button variant="tonal" type="submit">${t.settings.saveQuiet}</m3e-button></div>
        </form>`)}
    </div>`;
}
