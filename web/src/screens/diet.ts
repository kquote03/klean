import { html, nothing, type TemplateResult } from "lit";
import type { App } from "../app";
import { t } from "../copy";
import { num, str } from "../model";
import { fmtTime, formValues, resetForm } from "../ui/parts";
import { icon } from "../ui/icons";

/** Today's list and a one-line log field. Logging is one action and never waits for AI. */
export function dietScreen(app: App): TemplateResult {
  const m = app.model;
  const food = m.foodToday();
  const log = (e: Event) => {
    const { text } = formValues(e);
    if (!text?.trim()) return;
    resetForm(e);
    void app.act(() => m.logFood(text));
  };
  return html`${app.head(t.nav.diet)}
    <div class="stack">
      <form class="capture inline" @submit=${log} data-testid="diet-form">
        <m3e-form-field variant="outlined">
          <label slot="label" for="diet-input">${t.diet.log}</label>
          <input id="diet-input" name="text" autocomplete="off" placeholder=${t.diet.placeholder} />
        </m3e-form-field>
        <m3e-button variant="filled" type="submit">${icon("add", { slot: "icon" })}${t.capture.submit}</m3e-button>
      </form>
      <section class="card" aria-label=${t.diet.today}>
        <h2>${t.diet.today}</h2>
        ${food.length ? html`<ul class="list">${food.map((f) => {
          const items = m.foodItems(str(f.id)!);
          return html`<li class="item"><span class="time">${fmtTime(num(f.eaten_at)!)}</span>
            <span class="text">${str(f.raw_text)}${items.length ? html`<br /><span class="meta">${items.map((i) => str(i.name)).join(", ")}</span>` : nothing}</span></li>`;
        })}</ul>` : html`<p class="empty">${t.diet.none}</p>`}
      </section>
    </div>`;
}
