// Settings, opened from Today's top bar and every group's overflow: modules, devices (pairing in a sheet), and
// one list for quiet hours, notifications, Google and backup. Each row opens or does one thing.
import { html, nothing, type TemplateResult } from "lit";
import { unsafeSVG } from "lit/directives/unsafe-svg.js";
import qrcode from "qrcode-generator";
import type { App } from "../app";
import type { DeviceDto, PairStart } from "../api";
import { t } from "../copy";
import { MODULES } from "../model";
import { enablePush, pushStatus, type PushStatus } from "../push";
import { icon } from "../ui/icons";
import { closeSheet, groupedList, listRow, openSheet, screen } from "../ui/kit";
import { field, formValues, hhmm, parseHhmm } from "../ui/parts";

interface SettingsUi { devices?: DeviceDto[]; pair?: PairStart; push?: PushStatus; google?: { configured: boolean; connected: boolean }; loaded?: boolean; backupBusy?: boolean }

/** The pairing QR (klean://pair?url=…&code=…), drawn here in the browser. */
export function qrSvg(text: string): string {
  const qr = qrcode(0, "M");
  qr.addData(text);
  qr.make();
  // Dark modules use currentColor so the code follows the theme's on-surface colour on a light tile.
  return qr.createSvgTag({ cellSize: 4, margin: 2, scalable: true }).replace(/fill="black"/g, 'fill="currentColor"').replace(/fill="white"/g, 'fill="transparent"');
}

export function settingsScreen(app: App): TemplateResult {
  const m = app.model;
  const ui = (app.ui.settings ??= {}) as SettingsUi;
  if (!ui.loaded) {
    ui.loaded = true;
    void app.api.devices().then((d) => { ui.devices = d; app.refresh(); }).catch(() => {});
    void pushStatus().then((s) => { ui.push = s; app.refresh(); });
    void app.api.googleStatus().then((g) => { ui.google = g; app.refresh(); }).catch(() => {});
  }
  const q = m.quietHours();
  const pair = () => app.act(async () => { ui.pair = await app.api.pairStart(); app.refresh(); openSheet("pair-sheet"); });
  const backup = () => app.act(async () => {
    ui.backupBusy = true;
    app.refresh();
    try {
      const blob = await app.api.backup();
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = `klean-backup-${new Date(app.now()).toISOString().slice(0, 10)}.zip`;
      a.click();
      setTimeout(() => URL.revokeObjectURL(a.href), 10_000);
    } catch {
      app.toast(t.settings.backupFailed);
    } finally {
      ui.backupBusy = false;
      app.refresh();
    }
  });
  const push = () => app.act(async () => {
    if (ui.push === "on") { await app.api.pushTest(); app.toast(t.settings.pushSent); }
    else if (ui.push === "unsupported") app.toast(t.settings.pushUnsupported, undefined, 10_000);
    else if (ui.push === "denied") app.toast(t.settings.pushDenied, undefined, 10_000);
    else { ui.push = await enablePush(app.api); app.refresh(); }
  });
  const google = () => app.act(async () => {
    if (ui.google?.configured === false) app.toast(t.settings.googleUnavailable);
    else if (!ui.google?.connected) location.href = (await app.api.googleStart()).url;
  });

  const modules = MODULES.map((k) => html`<li class="lrow">
    <label class="row-main" for="mod-${k}"><span class="grow"><span class="row-title">${t.module[k]}</span><span class="row-sub body-m">${t.moduleLead[k]}</span></span></label>
    <m3e-switch id="mod-${k}" data-module=${k} ?checked=${m.isOn(k)} @change=${(e: Event) => app.act(() => m.setModuleOn(k, (e.target as HTMLInputElement).checked))}></m3e-switch>
  </li>`);
  const devices = [
    ...(ui.devices ?? []).map((d) => listRow({ lead: icon("devices", { size: 20 }), title: d.name,
      supporting: d.current ? t.settings.thisDevice : d.platform.charAt(0) + d.platform.slice(1).toLowerCase(),
      trailing: d.current ? nothing : html`<button type="button" class="btn text" @click=${() => app.act(async () => { await app.api.revoke(d.id); ui.devices = await app.api.devices(); app.refresh(); })}>${t.settings.revoke}</button>` })),
    html`<li class="lrow"><button type="button" class="row-main" data-testid="pair-start" @click=${pair}><span class="lead">${icon("add", { size: 20 })}</span><span class="grow row-title">${t.settings.pair}</span></button></li>`,
  ];
  const pushMeta = ui.push === "on" ? t.settings.on : ui.push === undefined ? "" : t.settings.off;
  const prefs = [
    listRow({ lead: icon("sleep", { size: 20 }), title: t.settings.quietHours, supporting: t.settings.quietLead, meta: t.settings.quietValue(hhmm(q.bed), hhmm(q.wake)), open: () => openSheet("quiet-sheet"), testid: "quiet-hours" }),
    listRow({ lead: icon("bell", { size: 20 }), title: ui.push === "on" ? t.settings.pushTest : t.settings.notifications, supporting: t.settings.notificationsLead, meta: pushMeta, open: () => void push() }),
    listRow({ lead: icon("cloud", { size: 20 }), title: t.settings.google, supporting: ui.google?.connected ? t.settings.googleConnected : undefined,
      meta: ui.google?.configured === false ? t.settings.unavailable : ui.google?.connected ? t.settings.on : t.settings.notConnected, open: () => void google() }),
    listRow({ lead: icon("download", { size: 20 }), title: ui.backupBusy ? t.settings.backupWorking : t.settings.backupDownload, supporting: t.settings.backupLead, open: () => void backup(), testid: "backup" }),
  ];
  return html`${screen({ title: t.nav.settings, actions: app.status(), testid: "settings" }, html`
    <div data-testid="settings-modules">${groupedList([{ title: t.settings.modules, rows: modules }])}</div>
    <div data-testid="settings-devices">${groupedList([{ title: t.settings.devices, rows: devices }])}</div>
    ${groupedList([{ title: t.settings.preferences, rows: prefs }])}
    <div class="actions section"><button type="button" class="btn text" @click=${() => app.act(() => app.signOut())} data-testid="sign-out">${t.settings.signOut}</button></div>`)}
    ${pairSheet(app, ui)}${quietSheet(app)}`;
}

function pairSheet(app: App, ui: SettingsUi) {
  const p = ui.pair;
  return html`<dialog class="sheet" id="pair-sheet" aria-labelledby="pair-title">
    ${p ? html`<div class="stack" data-testid="pairing">
      <h2 id="pair-title">${t.settings.pair}</h2>
      <p>${t.settings.pairLead}</p>
      <div class="qr" role="img" aria-label=${t.settings.pair} data-testid="pair-qr" data-qr=${p.qr}>${unsafeSVG(qrSvg(p.qr))}</div>
      <p>${t.settings.pairCode} <strong class="code" data-testid="pair-code">${p.code}</strong></p>
      <p class="muted body-m">${t.settings.pairExpires(Math.max(1, Math.round((p.expiresAt - app.now()) / 60000)))}</p>
      <div class="actions end"><button type="button" class="btn text" @click=${() => closeSheet("pair-sheet")}>${t.today.done}</button></div>
    </div>` : nothing}
  </dialog>`;
}

function quietSheet(app: App) {
  const q = app.model.quietHours();
  const save = (e: Event) => {
    const v = formValues(e);
    void app.act(async () => { await app.model.setQuietHours(parseHhmm(v.from), parseHhmm(v.to)); closeSheet("quiet-sheet"); app.toast(t.settings.savedQuiet); });
  };
  return html`<dialog class="sheet" id="quiet-sheet" aria-labelledby="quiet-title">
    <form class="stack" @submit=${save}>
      <h2 id="quiet-title">${t.settings.quietHours}</h2>
      <p class="lead-text">${t.settings.quietLead}</p>
      <div class="fields">${field({ label: t.settings.from, name: "from", type: "time", value: hhmm(q.bed) })}${field({ label: t.settings.to, name: "to", type: "time", value: hhmm(q.wake) })}</div>
      <div class="actions end"><button type="button" class="btn text" @click=${() => closeSheet("quiet-sheet")}>${t.add.cancel}</button><button type="submit" class="btn filled">${t.add.save}</button></div>
    </form>
  </dialog>`;
}
