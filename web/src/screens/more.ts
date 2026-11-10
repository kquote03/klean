import { html, type TemplateResult } from "lit";
import { ROUTES, type App } from "../app";
import { t } from "../copy";
import { icon } from "../ui/icons";

/** Phone width: everything that doesn't fit the bottom bar. */
export function moreScreen(app: App): TemplateResult {
  const items = ROUTES.filter((r) => r.rail && !r.bar && (!r.module || app.model.isOn(r.module)));
  return html`${app.head(t.nav.more)}
    <section class="card"><ul class="list">${items.map((r) => html`<li class="item">
      <m3e-button variant="text" style="width:100%;justify-content:flex-start" @click=${() => app.go(r.path)} data-path=${r.path}>${icon(r.icon, { slot: "icon" })}${r.title}</m3e-button>
    </li>`)}</ul></section>`;
}
