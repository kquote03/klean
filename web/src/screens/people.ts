// People: Contacts (the next birthday as a tonal hero, then everyone as one list) and Birthdays. Search and
// New contact live in the top bar; a contact opens in a sheet.
import { html, nothing, type TemplateResult } from "lit";
import type { App } from "../app";
import { t } from "../copy";
import { str } from "../model";
import type { Row } from "../data/sync-types";
import { icon } from "../ui/icons";
import { closeSheet, emptyState, groupedList, iconButton, listRow, moduleCard, openSheet, overflowMenu, screen } from "../ui/kit";
import { field, fmtDayMonth, fmtShortDay, fmtWeekdayLong, formValues } from "../ui/parts";

const list = (j: unknown): { label?: string; value: string }[] => {
  if (typeof j !== "string") return [];
  try { return JSON.parse(j); } catch { return []; }
};

export function peopleScreen(app: App): TemplateResult {
  const m = app.model;
  const dest = app.path.endsWith("/birthdays") ? "birthdays" : "contacts";
  const searching = app.ui.peopleSearch === true;
  const actions = html`${app.status()}
    ${dest === "contacts" && m.isOn("contacts") ? iconButton("search", t.contacts.search, () => { app.ui.peopleSearch = !searching; app.ui.contactsQuery = ""; app.refresh(); queueMicrotask(() => document.getElementById("contact-search")?.focus()); }, "people-search") : nothing}
    ${overflowMenu("people-menu", t.nav.moreOptions, [
      ...(m.isOn("contacts") ? [{ label: t.contacts.newContact, run: () => openSheet("contact-new"), testid: "menu-new-contact" }] : []),
      { label: t.nav.settings, run: () => app.go("/settings") },
    ])}`;
  return html`${screen({ title: t.nav.people, actions, tabs: app.tabs("people", dest), testid: dest }, dest === "birthdays" ? birthdays(app) : contacts(app, searching))}
    ${contactSheet(app)}${newContactSheet(app)}`;
}

const whenLabel = (b: { inDays: number; date: { year: number; month: number; day: number } }) =>
  b.inDays === 0 ? t.contacts.todayLabel : b.inDays === 1 ? t.contacts.tomorrow : b.inDays < 7 ? `${fmtWeekdayLong(b.date)}, ${t.contacts.inDays(b.inDays).toLowerCase()}` : fmtShortDay(b.date);

function contacts(app: App, searching: boolean) {
  const m = app.model;
  const q = ((app.ui.contactsQuery as string) ?? "").toLowerCase();
  const all = m.contacts();
  const shown = all.filter((c) => !q || (str(c.display_name) ?? "").toLowerCase().includes(q));
  const next = m.isOn("birthdays") ? m.birthdays(30)[0] : undefined;
  const search = searching ? html`<label class="capture-input" style="margin-bottom:var(--s16)"><span class="sr-only">${t.contacts.search}</span>${icon("search")}
    <input id="contact-search" type="search" placeholder=${t.contacts.search} .value=${app.ui.contactsQuery ?? ""} @input=${(e: Event) => { app.ui.contactsQuery = (e.target as HTMLInputElement).value; app.refresh(); }} /></label>` : nothing;
  if (!all.length) return emptyState({ icon: "people", text: t.contacts.emptyContacts, action: { label: t.contacts.newContact, run: () => openSheet("contact-new") } });
  return html`${search}
    ${next && !q ? moduleCard({ tone: "people", label: t.contacts.comingUp, big: str(next.contact.display_name) ?? "", sub: next.turns ? t.contacts.turnsOn(next.turns, whenLabel(next).toLowerCase()) : whenLabel(next), testid: "people-next" }) : nothing}
    ${shown.length ? groupedList([{ title: t.contacts.everyone, rows: shown.map((c, i) => contactRow(app, c, i)) }], "contacts-list") : html`<p class="empty-state body-l">${t.contacts.none}</p>`}`;
}

function contactRow(app: App, c: Row, i: number) {
  const name = str(c.display_name) ?? "";
  const phones = list(c.phones);
  const bd = typeof c.birthday_month === "number" && typeof c.birthday_day === "number" ? t.contacts.birthdayOn(fmtDayMonth({ year: 2000, month: c.birthday_month, day: c.birthday_day })) : null;
  return listRow({
    lead: html`<span class="avatar" data-tone=${String(i % 4)} aria-hidden="true">${name.charAt(0).toUpperCase()}</span>`,
    title: name, supporting: bd ?? str(c.org) ?? phones[0]?.value ?? undefined,
    open: () => { app.ui.contact = str(c.id); app.refresh(); openSheet("contact-sheet"); }, label: name, testid: "contact-row",
  });
}

function birthdays(app: App) {
  const all = app.model.birthdays();
  if (!all.length) return emptyState({ icon: "cake", text: t.contacts.noneBirthdays });
  const soon = all.filter((b) => b.inDays <= 30), later = all.filter((b) => b.inDays > 30);
  const row = (b: (typeof all)[number]) => listRow({ lead: icon("cake", { size: 20 }), title: str(b.contact.display_name) ?? "",
    supporting: b.turns ? t.contacts.turns(b.turns) : undefined, meta: whenLabel(b), open: () => { app.ui.contact = str(b.contact.id); app.refresh(); openSheet("contact-sheet"); } });
  return groupedList([{ title: t.contacts.comingUp, rows: soon.map(row) }, { title: t.calendar.buckets.later, rows: later.map(row) }], "birthdays-list");
}

function contactSheet(app: App) {
  const id = app.ui.contact as string | undefined;
  const c = id ? app.model.store.get("contact", id) : undefined;
  const lines = c ? [
    ...list(c.phones).map((p) => ({ icon: "person", text: p.value, href: `tel:${p.value.replace(/\s/g, "")}` })),
    ...list(c.emails).map((e) => ({ icon: "approvals", text: e.value, href: `mailto:${e.value}` })),
    ...(typeof c.birthday_month === "number" && typeof c.birthday_day === "number" ? [{ icon: "cake", text: fmtDayMonth({ year: 2000, month: c.birthday_month, day: c.birthday_day }), href: "" }] : []),
  ] : [];
  return html`<dialog class="sheet" id="contact-sheet" aria-labelledby="contact-title">
    ${c ? html`<div class="stack">
      <h2 id="contact-title">${str(c.display_name)}</h2>
      ${lines.length ? html`<ul class="list-group">${lines.map((l) => listRow({ lead: icon(l.icon, { size: 20 }), title: l.href ? html`<a href=${l.href}>${l.text}</a>` : l.text }))}</ul>` : nothing}
      <div class="actions end"><button type="button" class="btn text" @click=${() => closeSheet("contact-sheet")}>${t.contacts.close}</button></div>
    </div>` : nothing}
  </dialog>`;
}

function newContactSheet(app: App) {
  const m = app.model;
  const add = (e: Event) => {
    const v = formValues(e);
    if (!v.name?.trim()) return;
    (e.currentTarget as HTMLFormElement).reset();
    const [y, mo, da] = (v.birthday || "").split("-").map(Number);
    void app.act(async () => {
      await m.addContact({ name: v.name, phone: v.phone || undefined, email: v.email || undefined, birthday: mo && da ? { month: mo, day: da, year: y || null } : null });
      closeSheet("contact-new");
    });
  };
  return html`<dialog class="sheet" id="contact-new" aria-labelledby="contact-new-title">
    <form class="stack" @submit=${add}>
      <h2 id="contact-new-title">${t.contacts.newContact}</h2>
      ${field({ label: t.contacts.name, name: "name", required: true })}
      <div class="fields">
        ${field({ label: t.contacts.phone, name: "phone", type: "tel" })}
        ${field({ label: t.contacts.email, name: "email", type: "email" })}
        ${m.isOn("birthdays") ? field({ label: t.contacts.birthday, name: "birthday", type: "date" }) : nothing}
      </div>
      <div class="actions end"><button type="button" class="btn text" @click=${() => closeSheet("contact-new")}>${t.add.cancel}</button><button type="submit" class="btn filled">${t.add.save}</button></div>
    </form>
  </dialog>`;
}
