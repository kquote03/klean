import { html, nothing, type TemplateResult } from "lit";
import { unsafeSVG } from "lit/directives/unsafe-svg.js";
import qrcode from "qrcode-generator";
import type { App } from "../app";
import type { DeviceDto, PairStart } from "../api";
import { t } from "../copy";
import { MODULES } from "../model";
import { enablePush, pushStatus, type PushStatus } from "../push";
import { card, field, formValues, hhmm, parseHhmm } from "../ui/parts";
import { icon } from "../ui/icons";

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
  const saveQuiet = (e: Event) => {
    const v = formValues(e);
    void app.act(async () => { await m.setQuietHours(parseHhmm(v.from), parseHhmm(v.to)); app.toast(t.settings.savedQuiet); });
  };
  const pair = () => app.act(async () => { ui.pair = await app.api.pairStart(); app.refresh(); });
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
  return html`${app.head(t.nav.settings)}
  <div class="masonry">
    ${card({ title: t.settings.modules, icon: "tune", testid: "settings-modules" }, html`<p class="muted">${t.settings.modulesLead}</p>
      <ul class="list">${MODULES.map((k) => html`<li class="item">
        <span class="text"><label for="mod-${k}">${t.module[k]}</label><br /><span class="meta">${t.moduleLead[k]}</span></span>
        <m3e-switch id="mod-${k}" data-module=${k} ?checked=${m.isOn(k)} @change=${(e: Event) => app.act(() => m.setModuleOn(k, (e.target as HTMLInputElement).checked))}></m3e-switch>
      </li>`)}</ul>`)}

      ${card({ title: t.settings.devices, icon: "devices", tone: "primary", testid: "settings-devices" }, html`
        <ul class="list">${(ui.devices ?? []).map((d) => html`<li class="item">
          <span class="text">${d.name}<br /><span class="meta">${d.current ? t.settings.thisDevice : d.platform.charAt(0) + d.platform.slice(1).toLowerCase()}</span></span>
          ${d.current ? nothing : html`<m3e-button variant="text" @click=${() => app.act(async () => { await app.api.revoke(d.id); ui.devices = await app.api.devices(); app.refresh(); })}>${t.settings.revoke}</m3e-button>`}
        </li>`)}</ul>
        ${ui.pair ? html`<div class="stack" data-testid="pairing">
            <p>${t.settings.pairLead}</p>
            <div class="qr" role="img" aria-label=${t.settings.pair} data-testid="pair-qr" data-qr=${ui.pair.qr}>${unsafeSVG(qrSvg(ui.pair.qr))}</div>
            <p>${t.settings.pairCode} <strong class="code" data-testid="pair-code">${ui.pair.code}</strong></p>
            <p class="muted">${t.settings.pairExpires(Math.max(1, Math.round((ui.pair.expiresAt - app.now()) / 60000)))}</p>
          </div>`
          : html`<div class="actions"><m3e-button variant="filled" @click=${pair} data-testid="pair-start">${icon("devices", { slot: "icon" })}${t.settings.pair}</m3e-button></div>`}`)}

      ${card({ title: t.settings.quietHours, icon: "sleep", tone: "tertiary" }, html`<p class="muted">${t.settings.quietLead}</p>
        <form class="stack" @submit=${saveQuiet}>
          <div class="fields">${field({ label: t.settings.from, name: "from", type: "time", value: hhmm(q.bed) })}${field({ label: t.settings.to, name: "to", type: "time", value: hhmm(q.wake) })}</div>
          <div class="actions"><m3e-button variant="tonal" type="submit">${t.settings.saveQuiet}</m3e-button></div>
        </form>`)}

      ${card({ title: t.settings.notifications, icon: "bell" }, html`<p class="muted">${t.settings.notificationsLead}</p>
        ${ui.push === "unsupported" ? html`<p>${t.settings.pushUnsupported}</p>` : ui.push === "denied" ? html`<p>${t.settings.pushDenied}</p>`
          : ui.push === "on" ? html`<p>${t.settings.pushOn}</p><div class="actions"><m3e-button variant="tonal" @click=${() => app.act(async () => { await app.api.pushTest(); app.toast(t.settings.pushSent); })}>${t.settings.pushTest}</m3e-button></div>`
          : html`<div class="actions"><m3e-button variant="tonal" @click=${() => app.act(async () => { ui.push = await enablePush(app.api); app.refresh(); })}>${t.settings.enablePush}</m3e-button></div>`}`)}

      ${card({ title: t.settings.google, icon: "cloud" }, ui.google?.configured === false ? html`<p class="muted">${t.settings.googleUnavailable}</p>`
        : ui.google?.connected ? html`<p>${t.settings.googleConnected}</p>`
        : html`<div class="actions"><m3e-button variant="tonal" @click=${() => app.act(async () => { location.href = (await app.api.googleStart()).url; })}>${t.settings.googleConnect}</m3e-button></div>`)}

      ${card({ title: t.settings.backup, icon: "download", testid: "settings-backup" }, html`<p class="muted">${t.settings.backupLead}</p>
        <div class="actions"><m3e-button variant="tonal" ?disabled=${ui.backupBusy ?? false} @click=${backup} data-testid="backup">${icon("download", { slot: "icon" })}${ui.backupBusy ? t.settings.backupWorking : t.settings.backupDownload}</m3e-button></div>`)}

      <div class="actions"><m3e-button variant="text" @click=${() => app.act(() => app.signOut())} data-testid="sign-out">${t.settings.signOut}</m3e-button></div>
  </div>`;
}
