import { html, nothing, type TemplateResult } from "lit";
import type { App } from "../app";
import { t } from "../copy";
import { str } from "../model";
import type { Row } from "../data/sync-types";
import { card, field, fmtShortDay, formValues, resetForm, tabs } from "../ui/parts";
import { icon } from "../ui/icons";

type Tab = "contacts" | "birthdays";

export function contactsScreen(app: App): TemplateResult {
  const m = app.model;
  const options: Record<string, string> = { ...t.contacts.tabs };
  if (!m.isOn("birthdays")) delete options.birthdays;
  const tab = (options[(app.ui.contactsTab as string) ?? ""] ? app.ui.contactsTab : "contacts") as Tab;
  return html`${app.head(t.nav.contacts)}
    ${tabs(tab, options as Record<Tab, string>, (k) => { app.ui.contactsTab = k; app.refresh(); }, t.nav.contacts)}
    ${tab === "birthdays" ? birthdays(app) : contacts(app)}`;
}

const list = (j: unknown): { label?: string; value: string }[] => {
  if (typeof j !== "string") return [];
  try { return JSON.parse(j); } catch { return []; }
};

function contacts(app: App) {
  const m = app.model;
  const q = ((app.ui.contactsQuery as string) ?? "").toLowerCase();
  const all = m.contacts().filter((c) => !q || (str(c.display_name) ?? "").toLowerCase().includes(q));
  const add = (e: Event) => {
    const v = formValues(e);
    if (!v.name?.trim()) return;
    resetForm(e);
    const [, mo, da] = (v.birthday || "").split("-").map(Number);
    const y = Number((v.birthday || "").split("-")[0]);
    void app.act(() => m.addContact({ name: v.name, phone: v.phone || undefined, email: v.email || undefined, birthday: mo && da ? { month: mo, day: da, year: y || null } : null }));
  };
  return html`<div class="stack">
    <m3e-form-field variant="outlined">
      <label slot="label" for="contact-search">${t.contacts.search}</label>
      <input id="contact-search" type="search" .value=${app.ui.contactsQuery ?? ""} @input=${(e: Event) => { app.ui.contactsQuery = (e.target as HTMLInputElement).value; app.refresh(); }} />
    </m3e-form-field>
    <section class="card">${all.length ? html`<ul class="list">${all.map((c) => contactRow(c))}</ul>` : html`<p class="empty">${t.contacts.none}</p>`}</section>
    ${card({ title: t.contacts.add, icon: "person" }, html`<form class="stack" @submit=${add}>
      <div class="fields">
        ${field({ label: t.contacts.name, name: "name", required: true })}
        ${field({ label: t.contacts.phone, name: "phone", type: "tel" })}
        ${field({ label: t.contacts.email, name: "email", type: "email" })}
        ${m.isOn("birthdays") ? field({ label: t.contacts.birthday, name: "birthday", type: "date" }) : nothing}
      </div>
      <div class="actions"><m3e-button variant="filled" type="submit">${icon("add", { slot: "icon" })}${t.capture.submit}</m3e-button></div>
    </form>`)}
  </div>`;
}

function contactRow(c: Row) {
  const phones = list(c.phones), emails = list(c.emails);
  const detail = [phones[0]?.value, emails[0]?.value, str(c.org)].filter(Boolean).join(" · ");
  const bd = typeof c.birthday_month === "number" && typeof c.birthday_day === "number"
    ? fmtShortDay({ year: 2000, month: c.birthday_month, day: c.birthday_day }).replace(/^\S+\s/, "") : null;
  return html`<li class="item"><span class="badge-icon" aria-hidden="true">${icon("person")}</span>
    <span class="text">${str(c.display_name)}${detail ? html`<br /><span class="meta">${detail}</span>` : nothing}</span>
    ${bd ? html`<span class="meta">${icon("cake", { size: 16 })}</span><span class="meta">${bd}</span>` : nothing}</li>`;
}

function birthdays(app: App) {
  const all = app.model.birthdays();
  return html`<section class="card">${all.length ? html`<ul class="list">${all.map((b) => html`<li class="item">
    <span class="time">${fmtShortDay(b.date)}</span>
    <span class="text">${str(b.contact.display_name)}${b.turns ? html`<br /><span class="meta">${t.contacts.turns(b.turns)}</span>` : nothing}</span>
    <span class="meta">${b.inDays === 0 ? t.contacts.todayLabel : b.inDays === 1 ? t.contacts.tomorrow : t.contacts.inDays(b.inDays)}</span></li>`)}</ul>`
    : html`<p class="empty">${t.contacts.noneBirthdays}</p>`}</section>`;
}
