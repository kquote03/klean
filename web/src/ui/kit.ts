// Soft-cards components as Lit templates, the web twins of core/design's KleanScreen, ModuleCard, SectionHeader,
// ListGroup + ListRow, EmptyState, SegmentedTabs, WeekBars and the overflow menu (docs/ui/design-system.md).
// Values come from the spacing, shape and type scales in styles.css; screens compose these and nothing else.
import { html, nothing, type TemplateResult } from "lit";
import { icon } from "./icons";

type Content = TemplateResult | typeof nothing | string;
export type Tone = "plan" | "track" | "people" | "neutral";

/** The screen scaffold: top bar (title or a custom header, actions), optional segmented tabs, then content. */
export function screen(o: { title?: string; header?: TemplateResult; actions?: Content; tabs?: Content; width?: "narrow" | "wide"; testid?: string }, body: Content) {
  return html`<div class="screen ${o.width === "wide" ? "wide" : ""}" data-testid=${o.testid ?? nothing}>
    ${o.header ?? html`<header class="topbar"><h1 class="title-l grow">${o.title}</h1>${o.actions ?? nothing}</header>`}
    ${o.tabs ?? nothing}
    <div class="screen-body">${body}</div>
  </div>`;
}

export function iconButton(name: string, label: string, run: () => void, testid?: string) {
  return html`<button class="icon-btn" type="button" aria-label=${label} title=${label} data-testid=${testid ?? nothing} @click=${run}>${icon(name)}</button>`;
}

export interface MenuItem { label: string; run: () => void; selected?: boolean; testid?: string }

/** The top bar's ⋮: secondary actions (views, lists, settings) live here, never as rows of buttons. */
export function overflowMenu(id: string, label: string, items: MenuItem[], testid = "overflow") {
  const place = (e: Event) => {
    const ev = e as ToggleEvent;
    if (ev.newState !== "open") return;
    const menu = e.currentTarget as HTMLElement;
    const btn = document.querySelector(`[popovertarget="${id}"]`)?.getBoundingClientRect();
    if (!btn) return;
    menu.style.top = `${btn.bottom + 4}px`;
    menu.style.right = `${Math.max(8, innerWidth - btn.right)}px`;
  };
  const hide = () => (document.getElementById(id) as HTMLElement & { hidePopover(): void } | null)?.hidePopover();
  return html`<button class="icon-btn" type="button" popovertarget=${id} aria-haspopup="menu" aria-label=${label} title=${label} data-testid=${testid}>${icon("more_vert")}</button>
    <div id=${id} popover class="menu" role="menu" aria-label=${label} @beforetoggle=${place}>
      ${items.map((i) => html`<button type="button" role=${i.selected === undefined ? "menuitem" : "menuitemradio"} class="menu-item" aria-checked=${i.selected === undefined ? nothing : String(i.selected)}
        data-testid=${i.testid ?? nothing} @click=${() => { hide(); i.run(); }}><span class="mark">${i.selected ? icon("check", { size: 20 }) : nothing}</span>${i.label}</button>`)}
    </div>`;
}

/**
 * ModuleCard: a large surface-container-low card opening with a tonal hero (label, one big fact, one line),
 * and up to 4 quiet rows. The whole card is one click target; nothing inside it is separately tappable.
 */
export function moduleCard(o: { tone: Tone; label: string; big: string; sub?: string; rows?: TemplateResult[]; open?: () => void; aria?: string; testid?: string; extra?: TemplateResult }) {
  const inner = html`<div class="hero"><span class="label-l hero-label">${o.label}</span><span class="headline-s hero-big">${o.big}</span>${o.sub ? html`<span class="body-m hero-sub">${o.sub}</span>` : nothing}${o.extra ?? nothing}</div>
    ${o.rows?.length ? html`<div class="card-body">${o.rows.slice(0, 4)}</div>` : nothing}`;
  return o.open
    ? html`<button type="button" class="mcard" data-tone=${o.tone} aria-label=${o.aria ?? nothing} data-testid=${o.testid ?? nothing} @click=${o.open}>${inner}</button>`
    : html`<section class="mcard" data-tone=${o.tone} aria-label=${o.aria ?? o.label} data-testid=${o.testid ?? nothing}>${inner}</section>`;
}

/** A quiet row inside a ModuleCard: leading mark, text, meta. */
export function cardRow(lead: Content, text: string, meta?: string, muted = false) {
  return html`<div class="crow ${muted ? "muted body-m" : ""}"><span class="lead">${lead}</span><span class="grow ellipsis">${text}</span>${meta ? html`<span class="meta">${meta}</span>` : nothing}</div>`;
}

export function sectionHeader(title: string, trailing: Content = nothing) {
  return html`<div class="section-head"><h2 class="title-m">${title}</h2>${trailing}</div>`;
}

/**
 * ListGroup: rows on one rounded surface, split by a 1 px surface gap. A list counts once in the clutter
 * budget however many rows it has (data-clutter-exempt="list"), like Android's CLUTTER_EXEMPT lazy list.
 */
export function listGroup(rows: Content[], testid?: string) {
  return html`<ul class="list-group" data-clutter-exempt="list" data-testid=${testid ?? nothing}>${rows}</ul>`;
}

/** Several sections of list groups that read as one list (Today, Coming up, Still want these?). */
export function groupedList(sections: { title: string; rows: Content[]; trailing?: Content }[], testid?: string) {
  return html`<div class="grouped" data-clutter-exempt="list" data-testid=${testid ?? nothing}>
    ${sections.filter((s) => s.rows.length).map((s) => html`<section class="section">${sectionHeader(s.title, s.trailing)}${listGroup(s.rows)}</section>`)}
  </div>`;
}

export interface RowOpts { lead?: Content; leadAction?: TemplateResult; title: Content; supporting?: Content; meta?: Content; open?: () => void; label?: string; testid?: string; attrs?: Record<string, string>; done?: boolean; trailing?: Content }

/** ListRow: leading mark (or a check button), title + optional supporting line, trailing meta. Tap opens. */
export function listRow(o: RowOpts) {
  const text = html`${o.lead !== undefined ? html`<span class="lead">${o.lead}</span>` : nothing}
    <span class="grow"><span class="row-title">${o.title}</span>${o.supporting ? html`<span class="row-sub body-m">${o.supporting}</span>` : nothing}</span>
    ${o.meta ? html`<span class="meta body-m">${o.meta}</span>` : nothing}`;
  const a = o.attrs ?? {};
  return html`<li class="lrow ${o.done ? "done" : ""}" data-testid=${o.testid ?? nothing} data-id=${a.id ?? nothing}>
    ${o.leadAction ?? nothing}
    ${o.open ? html`<button type="button" class="row-main" aria-label=${o.label ?? nothing} @click=${o.open}>${text}</button>` : html`<div class="row-main">${text}</div>`}
    ${o.trailing ?? nothing}
  </li>`;
}

/** A round check that completes the row's item. */
export function checkButton(label: string, run: () => void) {
  return html`<button type="button" class="check-btn" aria-label=${label} title=${label} @click=${run}><span class="ring"></span></button>`;
}

export const dot = (tone: "primary" | "secondary" | "tertiary" | "neutral" = "secondary") => html`<span class="dot" data-tone=${tone}></span>`;

/** EmptyState: one tonal icon, one sentence, at most one action. */
export function emptyState(o: { icon: string; text: string; action?: { label: string; run: () => void; testid?: string } }) {
  return html`<div class="empty-state" data-testid="empty-state"><span class="empty-icon">${icon(o.icon)}</span><p class="body-l">${o.text}</p>
    ${o.action ? html`<button type="button" class="btn tonal" data-testid=${o.action.testid ?? nothing} @click=${o.action.run}>${o.action.label}</button>` : nothing}</div>`;
}

/** SegmentedTabs: the modules inside a group. Hidden when the group has one module. */
export function segmentedTabs<K extends string>(o: { options: [K, string][]; current: K; pick: (k: K) => void; tone: Tone; label: string }) {
  if (o.options.length < 2) return nothing;
  return html`<div class="seg" role="tablist" aria-label=${o.label} data-tone=${o.tone}>
    ${o.options.map(([k, label]) => html`<button type="button" role="tab" aria-selected=${k === o.current ? "true" : "false"} data-tab=${k} @click=${() => o.pick(k)}>${k === o.current ? icon("check", { size: 18 }) : nothing}${label}</button>`)}
  </div>`;
}

/** WeekBars: 7 bars, one per day; days with nothing show a short stub, never a gap. */
export function weekBars(days: { label: string; value: number; on?: boolean; today?: boolean; title?: string }[], height = 80) {
  return html`<div class="week-bars" style="height:${height}px" role="img" aria-label=${days.map((d) => d.title ?? d.label).join(", ")}>
    ${days.map((d) => html`<div class="wd ${d.today ? "today" : ""}"><div class="wbar ${d.on ? "on" : ""}" style="height:${Math.round(Math.max(0.08, Math.min(1, d.value)) * 100)}%"></div><span class="label-m">${d.label}</span></div>`)}
  </div>`;
}

/** A pill button (the one filled or tonal action a sheet or empty state may carry). */
export function button(label: string, run: () => void, variant: "filled" | "tonal" | "text" = "text", testid?: string, type: "button" | "submit" = "button") {
  return html`<button type=${type} class="btn ${variant}" data-testid=${testid ?? nothing} @click=${run}>${label}</button>`;
}

/** Open and close <dialog> sheets by id. */
export const openSheet = (id: string) => queueMicrotask(() => (document.getElementById(id) as HTMLDialogElement | null)?.showModal());
export const closeSheet = (id: string) => (document.getElementById(id) as HTMLDialogElement | null)?.close();
