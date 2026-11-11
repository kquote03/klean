// The clutter budget (docs/ui/design-system.md, the web twin of :core:testing's assertClutterBudget): in the first
// viewport, count visible interactive elements and text inputs outside [data-clutter-exempt]. A subtree marked
// data-clutter-exempt="list" is a list whose rows open detail: it counts once, however many rows it shows.
// Navigation (rail and bar) is marked data-clutter-exempt and doesn't count.
import { expect, type Page } from "@playwright/test";

export interface ClutterCount { interactive: number; inputs: number; primary: number; items: string[] }

export async function clutterCount(page: Page): Promise<ClutterCount> {
  return page.evaluate(() => {
    const INTERACTIVE = "a[href], button, input, select, textarea, [role=button], [role=tab], [role=link], [role=checkbox], [role=switch], [role=menuitem], [tabindex]:not([tabindex='-1']), m3e-button, m3e-icon-button, m3e-checkbox, m3e-switch, m3e-filter-chip, m3e-nav-item";
    const TEXT_INPUT = "input:not([type=hidden]):not([type=checkbox]):not([type=radio]):not([type=button]):not([type=submit]), textarea, select, [contenteditable=''], [contenteditable=true]";
    const vh = innerHeight, vw = innerWidth;
    const visible = (el: Element) => {
      const r = el.getBoundingClientRect();
      if (r.width <= 0 || r.height <= 0 || r.bottom <= 0 || r.top >= vh || r.right <= 0 || r.left >= vw) return false;
      const cs = getComputedStyle(el);
      if (cs.visibility === "hidden" || cs.display === "none" || Number(cs.opacity) === 0) return false;
      // Closed dialogs and popovers aren't on screen.
      return !el.closest("dialog:not([open]), [popover]:not(:popover-open)");
    };
    const lists = new Set<Element>();
    const items: string[] = [];
    let interactive = 0;
    let primary = 0;
    for (const el of document.querySelectorAll(INTERACTIVE)) {
      if (!visible(el)) continue;
      // A control inside another control counts once (the outer one).
      if (el.parentElement?.closest(INTERACTIVE)) continue;
      const exempt = el.closest("[data-clutter-exempt]");
      if (exempt) {
        let list: Element | null = null;
        for (let p: Element | null = el.closest("[data-clutter-exempt]"); p; p = p.parentElement?.closest("[data-clutter-exempt]") ?? null) {
          if (p.getAttribute("data-clutter-exempt") !== "list") { list = null; break; }
          list = p; // the outermost list wins, so nested list groups count once
        }
        if (list && !lists.has(list)) { lists.add(list); interactive++; items.push(`list ${list.getAttribute("data-testid") ?? list.className}`); }
        continue;
      }
      interactive++;
      items.push(`${el.tagName.toLowerCase()} ${(el.getAttribute("aria-label") ?? el.textContent ?? "").trim().slice(0, 30)}`);
      if (el.matches(".fab, .btn.filled, .btn.tonal, m3e-button[variant=filled], m3e-button[variant=tonal]")) primary++;
    }
    const inputs = [...document.querySelectorAll(TEXT_INPUT)].filter((el) => visible(el) && !el.closest("[data-clutter-exempt]:not([data-clutter-exempt=list])")).length;
    return { interactive, inputs, primary, items };
  });
}

/** Fail when the first viewport has more than [maxInteractive] interactive elements or [maxInputs] text inputs. */
export async function expectClutterBudget(page: Page, budget: { maxInteractive?: number; maxInputs?: number; maxPrimary?: number } = {}): Promise<ClutterCount> {
  const { maxInteractive = 7, maxInputs = 0, maxPrimary = 1 } = budget;
  const c = await clutterCount(page);
  expect(c.interactive, `${c.interactive} interactive elements in the first viewport (budget ${maxInteractive}): ${c.items.join(" | ")}`).toBeLessThanOrEqual(maxInteractive);
  expect(c.inputs, `${c.inputs} text inputs in the first viewport (budget ${maxInputs})`).toBeLessThanOrEqual(maxInputs);
  expect(c.primary, `${c.primary} primary actions in the first viewport (budget ${maxPrimary})`).toBeLessThanOrEqual(maxPrimary);
  return c;
}
