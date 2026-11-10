import { html, nothing, type TemplateResult } from "lit";
import type { App } from "../app";
import { t } from "../copy";
import { str } from "../model";
import { field, textarea } from "../ui/parts";
import { icon } from "../ui/icons";

/** Hermes questions and approvals: the draft and recipients are editable; the decision is what the agent waits for. */
export function approvalsScreen(app: App): TemplateResult {
  const m = app.model;
  const pending = m.approvals();
  return html`${app.head(t.nav.approvals)}
    <p class="muted" style="margin-bottom:var(--s3)">${t.approvals.lead}</p>
    <div class="stack">
      ${pending.length ? pending.map((a) => {
        const id = str(a.id)!;
        const ed = m.editable(a);
        const delegation = m.delegationFor(a);
        const task = delegation ? m.store.get("task", str(delegation.task_id) ?? "") : undefined;
        const decide = (approve: boolean) => (e: Event) => {
          const form = (e.target as HTMLElement).closest("form") as HTMLFormElement;
          const fd = new FormData(form);
          const draft = ed.draft === false ? undefined : String(fd.get("draft") ?? "");
          const recipients = ed.recipients === false ? undefined : String(fd.get("recipients") ?? "").split(",").map((s) => s.trim()).filter(Boolean);
          void app.act(async () => { await m.decide(id, approve, draft, recipients); app.toast(approve ? t.approvals.approved : t.approvals.rejected); });
        };
        return html`<section class="card" data-testid="approval" data-approval-id=${id} aria-label=${str(a.action) ?? ""}>
          <div class="card-head"><span class="badge-icon">${icon("approvals")}</span><h2>${humanAction(str(a.action) ?? "")}</h2></div>
          ${task ? html`<p class="muted">${str(task.title)}</p>` : nothing}
          <form class="stack" @submit=${(e: Event) => e.preventDefault()}>
            ${ed.draft === false ? html`<p>${str(a.draft)}</p>` : textarea({ label: t.approvals.draft, name: "draft", value: str(a.draft) ?? "", rows: 6, testid: "approval-draft" })}
            ${ed.recipients === false ? html`<p class="muted">${m.recipients(a).join(", ")}</p>`
              : field({ label: t.approvals.recipients, name: "recipients", value: m.recipients(a).join(", "), hint: t.approvals.recipientsHint, testid: "approval-recipients" })}
            <div class="actions">
              <m3e-button variant="filled" @click=${decide(true)} data-testid="approve">${icon("check", { slot: "icon" })}${t.approvals.approve}</m3e-button>
              <m3e-button variant="outlined" @click=${decide(false)} data-testid="reject">${t.approvals.reject}</m3e-button>
            </div>
          </form>
        </section>`;
      }) : html`<section class="card"><p class="empty" data-testid="approvals-empty">${t.approvals.none}</p></section>`}
    </div>`;
}

/** `send_email` → "Send email". */
const humanAction = (a: string) => { const s = a.replace(/_/g, " ").trim(); return s.charAt(0).toUpperCase() + s.slice(1); };
