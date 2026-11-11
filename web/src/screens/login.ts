import { html } from "lit";
import type { App } from "../app";
import { ApiError } from "../api";
import { t } from "../copy";
import { icon } from "../ui/icons";
import { field, formValues } from "../ui/parts";

/** Sign-in and first setup: the admin password creates this browser's device token. */
export function loginScreen(app: App) {
  const state = (app.ui.login ??= { busy: false, error: "" }) as { busy: boolean; error: string };
  const submit = async (e: Event) => {
    const v = formValues(e);
    state.busy = true;
    state.error = "";
    app.refresh();
    try {
      await app.login(v.password, v.deviceName?.trim() || t.login.deviceNameDefault);
      delete app.ui.login;
    } catch (err) {
      state.error = err instanceof ApiError && err.status === 401 ? t.login.wrong : err instanceof ApiError ? err.message : t.login.offline;
    } finally {
      state.busy = false;
      app.refresh();
    }
  };
  return html`<div class="login"><section class="panel" aria-labelledby="login-title">
    <div class="brand">${icon("today", { size: 48 })}<h1 id="login-title" class="headline-s">${t.login.title}</h1></div>
    <p>${t.login.lead}</p>
    <form class="stack" @submit=${submit} data-testid="login-form">
      ${field({ label: t.login.password, name: "password", type: "password", required: true, autocomplete: "current-password", testid: "password" })}
      ${field({ label: t.login.deviceName, name: "deviceName", value: t.login.deviceNameDefault, testid: "device-name" })}
      ${state.error ? html`<p class="error-text" role="alert">${state.error}</p>` : ""}
      <div class="actions end"><button type="submit" class="btn filled" ?disabled=${state.busy} data-testid="sign-in">${state.busy ? t.login.working : t.login.submit}</button></div>
    </form>
    <p class="muted body-m">${t.login.firstSetup}</p>
  </section></div>`;
}
