// Small shared view helpers: cards, form fields, time formatting.
import { html, nothing, type TemplateResult } from "lit";
import { icon } from "./icons";
import type { LocalDate } from "../capture/natural-time";

let seq = 0;
const uid = (p: string) => `${p}-${++seq}`;

export function card(o: { title: string; icon?: string; tone?: "primary" | "secondary" | "tertiary"; action?: TemplateResult | typeof nothing; testid?: string; emphasis?: boolean }, body: TemplateResult | TemplateResult[]) {
  return html`<section class="card ${o.emphasis ? "emphasis" : ""}" data-tone=${o.tone ?? "secondary"} data-testid=${o.testid ?? nothing} aria-label=${o.title}>
    <div class="card-head">${o.icon ? html`<span class="badge-icon">${icon(o.icon)}</span>` : nothing}<h2>${o.title}</h2>${o.action ?? nothing}</div>
    ${body}
  </section>`;
}

/** An M3E outlined field around a native input; `name` is what FormData sees. The subscript row is reserved
 * only for fields with a hint, so inline forms line up with their button. */
export function field(o: { label: string; name: string; type?: string; value?: string; placeholder?: string; required?: boolean; autocomplete?: string; testid?: string; hint?: string; min?: string; step?: string }) {
  const id = uid(o.name);
  return html`<m3e-form-field variant="outlined" hide-subscript=${o.hint ? "auto" : "always"}>
    <label slot="label" for=${id}>${o.label}</label>
    <input id=${id} name=${o.name} type=${o.type ?? "text"} .value=${o.value ?? ""} placeholder=${o.placeholder ?? nothing}
      ?required=${o.required ?? false} autocomplete=${o.autocomplete ?? "off"} data-testid=${o.testid ?? nothing} min=${o.min ?? nothing} step=${o.step ?? nothing} />
    ${o.hint ? html`<span slot="hint">${o.hint}</span>` : nothing}
  </m3e-form-field>`;
}

export function textarea(o: { label: string; name: string; value?: string; rows?: number; testid?: string }) {
  const id = uid(o.name);
  return html`<m3e-form-field variant="outlined" hide-subscript="always">
    <label slot="label" for=${id}>${o.label}</label>
    <textarea id=${id} name=${o.name} rows=${o.rows ?? 4} .value=${o.value ?? ""} data-testid=${o.testid ?? nothing}></textarea>
  </m3e-form-field>`;
}

/** Read a submitted form and reset it. */
export function formValues(e: Event): Record<string, string> {
  e.preventDefault();
  const form = e.currentTarget as HTMLFormElement;
  const out = Object.fromEntries([...new FormData(form).entries()].map(([k, v]) => [k, String(v)]));
  return out;
}
export function resetForm(e: Event) { (e.currentTarget as HTMLFormElement).reset(); }

/** Tabs as a wrapping row of filter chips (no sideways scrolling at phone width). */
export function tabs<K extends string>(current: K, options: Record<K, string>, pick: (k: K) => void, label: string) {
  return html`<div class="chip-row" role="tablist" aria-label=${label} style="margin-bottom:var(--s2)">
    ${(Object.keys(options) as K[]).map((k) => html`<m3e-filter-chip role="tab" aria-selected=${k === current ? "true" : "false"} ?selected=${k === current}
      data-tab=${k} @click=${() => pick(k)}>${options[k]}</m3e-filter-chip>`)}
  </div>`;
}

const timeFmt = new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" });
const dayFmt = new Intl.DateTimeFormat(undefined, { weekday: "long", day: "numeric", month: "long" });
const shortDayFmt = new Intl.DateTimeFormat(undefined, { weekday: "short", day: "numeric", month: "short" });
const monthFmt = new Intl.DateTimeFormat(undefined, { month: "long", year: "numeric" });
const wdFmt = new Intl.DateTimeFormat(undefined, { weekday: "short" });

export const fmtTime = (ms: number) => timeFmt.format(ms);
export const fmtMinutes = (m: number) => timeFmt.format(new Date(2000, 0, 1, Math.floor(m / 60), m % 60));
export const jsDate = (d: LocalDate) => new Date(d.year, d.month - 1, d.day);
export const fmtDay = (d: LocalDate) => dayFmt.format(jsDate(d));
export const fmtShortDay = (d: LocalDate) => shortDayFmt.format(jsDate(d));
export const fmtMonth = (d: LocalDate) => monthFmt.format(jsDate(d));
export const fmtWeekday = (d: LocalDate) => wdFmt.format(jsDate(d));
export function fmtDuration(ms: number) {
  const m = Math.round(ms / 60000);
  const h = Math.floor(m / 60);
  return h ? `${h} h ${m % 60} min` : `${m} min`;
}
export const hhmm = (m: number) => `${String(Math.floor(m / 60)).padStart(2, "0")}:${String(m % 60).padStart(2, "0")}`;
export const parseHhmm = (s: string) => { const [h, m] = s.split(":").map(Number); return h * 60 + (m || 0); };
