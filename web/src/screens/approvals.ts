// Hermes questions and approvals: a list of what waits; each opens in a sheet where the draft and recipients can
// be edited before the decision the agent waits for.
import { html, nothing, type TemplateResult } from "lit";
import type { App } from "../app";
import { t } from "../copy";
import { str } from "../model";
import { icon } from "../ui/icons";
import { closeSheet, emptyState, groupedList, listRow, openSheet, screen } from "../ui/kit";
import { field, textarea } from "../ui/parts";

/** `send_email` → "Send email". */
const humanAction = (a: string) => { const s = a.replace(/_/g, " ").trim(); return s.charAt(0).toUpperCase() + s.slice(1); };

export function approvalsScreen(app: App): TemplateResult {
  const m = app.model;
  const pending = m.approvals();
  const body = pending.length
    ? html`<p class="muted" style="margin-bottom:var(--s8)">${t.approvals.lead}</p>${groupedList([{ title: t.approvals.waiting(pending.length), rows: pending.map((a) => {
        const d = m.delegationFor(a);
        const task = d ? m.store.get("task", str(d.task_id) ?? "") : undefined;
        const title = humanAction(str(a.action) ?? "");
        return listRow({ lead: icon("approvals", { size: 20 }), title, supporting: str(task?.title) ?? m.recipients(a).join(", "),
          open: () => { app.ui.approval = str(a.id); app.refresh(); openSheet("approval-sheet"); }, label: title, testid: "approval", attrs: { id: str(a.id)! } });
      }) }], "approvals-list")}`
    : html`<div data-testid="approvals-empty">${emptyState({ icon: "approvals", text: t.approvals.none })}</div>`;
  return html`${screen({ title: t.nav.approvals, actions: app.status(), testid: "approvals" }, body)}${approvalSheet(app)}`;
}

function approvalSheet(app: App) {
  const m = app.model;
  const id = app.ui.approval as string | undefined;
  const a = id ? m.store.get("approval_request", id) : undefined;
  const pending = a && (str(a.decision) ?? "PENDING") === "PENDING";
  const ed = a ? m.editable(a) : {};
  const decide = (approve: boolean) => (e: Event) => {
    const form = (e.target as HTMLElement).closest("form") as HTMLFormElement;
    const fd = new FormData(form);
    const draft = ed.draft === false ? undefined : String(fd.get("draft") ?? "");
    const recipients = ed.recipients === false ? undefined : String(fd.get("recipients") ?? "").split(",").map((s) => s.trim()).filter(Boolean);
    void app.act(async () => { await m.decide(id!, approve, draft, recipients); closeSheet("approval-sheet"); app.toast(approve ? t.approvals.approved : t.approvals.rejected); });
  };
  return html`<dialog class="sheet" id="approval-sheet" aria-labelledby="approval-title" data-testid="approval-sheet">
    ${a && pending ? html`<form class="stack" @submit=${(e: Event) => e.preventDefault()}>
      <h2 id="approval-title">${humanAction(str(a.action) ?? "")}</h2>
      ${ed.draft === false ? html`<p>${str(a.draft)}</p>` : textarea({ label: t.approvals.draft, name: "draft", value: str(a.draft) ?? "", rows: 6, testid: "approval-draft" })}
      ${ed.recipients === false ? html`<p class="muted">${m.recipients(a).join(", ")}</p>`
        : field({ label: t.approvals.recipients, name: "recipients", value: m.recipients(a).join(", "), hint: t.approvals.recipientsHint, testid: "approval-recipients" })}
      <div class="actions end">
        <button type="button" class="btn text" @click=${decide(false)} data-testid="reject">${t.approvals.reject}</button>
        <button type="button" class="btn filled" @click=${decide(true)} data-testid="approve">${t.approvals.approve}</button>
      </div>
    </form>` : nothing}
  </dialog>`;
}
