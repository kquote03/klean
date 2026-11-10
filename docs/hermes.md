# Klean ↔ Hermes (M23 delegation)

Klean hands a task to an agent through one interface, `DelegationAgent`
(`feature/delegation/.../agent/DelegationAgent.kt`). Two agents exist:

| Agent | When | What it does |
|---|---|---|
| `PartialAgent` (`AI:<provider>`) | mode PARTIAL | One `AiService` call with the schema `{draft, subtasks[], notes}`; the result is attached to the delegation, subtasks are created only when the user accepts |
| `HermesAgent` (`HERMES`) | modes INTERACTIVE and AGENTIC | Runs the task on the user's own Hermes through its API Server |

Nothing in Klean holds the user's mail, calendar or other credentials: Hermes acts with its own tools.
`DelegationPayload` has no credential fields (`DelegationPayloadTest` checks it).

## What the Hermes API Server offers (research, 2026-11-10)

Read from the local install (`~/.hermes/hermes-agent/gateway/platforms/api_server*.py`):

- **Endpoint and auth.** The API Server adapter listens on `http://<host>:8642` (OpenAI-compatible under
  `/v1`). Every call needs `Authorization: Bearer <API_SERVER_KEY>`; the server refuses to start without a
  strong key. Multi-profile installs mirror every route under `/p/<profile>/…`.
- **Runs API** (`api_server_runs.py`), the delegation surface Klean uses:
  - `POST /v1/runs` `{"input": "...", "instructions": "...", "session_id": "..."}` → `{"run_id": "run_…"}`
    (returns at once; `Idempotency-Key` header supported);
  - `GET /v1/runs/{run_id}/events`: SSE, one JSON object per frame with `event`, `run_id`, `seq`
    (resumable with `Last-Event-ID`). Events: `run.queued`, `run.started`, `message.delta` (`delta`),
    `tool.started` / `tool.completed` / `tool.failed`, `approval.request`, `clarify.request`,
    `run.completed` (`output`), `run.failed` (`error`), `run.cancelled`, `run.interrupted`;
  - `POST /v1/runs/{run_id}/approval` `{"choice": "once" | "deny", "request_id": "..."}` resolves a pending
    tool approval;
  - `POST /v1/runs/{run_id}/clarify` `{"request_id", "execution_generation", "answer"}` answers a question
    the agent asked through its clarify tool;
  - `GET /v1/runs/{run_id}`, `POST /v1/runs/{run_id}/stop`, `POST /v1/runs/{run_id}/steer`.
- **Sessions** (`/api/sessions…`) keep a conversation across runs: passing the same `session_id` to every
  run continues it.
- **Tool calls and approvals reach a client:** dangerous tool calls raise `approval.request` on the run's
  stream, and the clarify tool raises `clarify.request`. Both are answered over HTTP, so a phone can act on
  them without the Hermes UI.
- **Other routes:** webhooks for inbound platforms (`/api/platforms/{platform}/events`), cron jobs
  (`/api/jobs`). ntfy is a Hermes messaging platform, so Hermes can also notify the user directly; Klean
  doesn't rely on it.

## The protocol Klean speaks

Klean starts one run per delegation step, all in one Hermes session `klean-<delegation id>`:

1. **Start.** `POST /v1/runs` with `instructions` = the Klean conventions below, `input` = the task (title,
   notes, due date, list) as plain text. `remote_ref` = the session id; the current run id is kept with it.
2. **Stream.** `GET /v1/runs/{run}/events`, mapped to `AgentEvent`s:
   - `message.delta` text is collected; when the run ends, any Klean block in it becomes an event, and the
     remaining text is the result;
   - `clarify.request` → `Question` (answered through `/clarify`);
   - `approval.request` → `ApprovalNeeded` (decided through `/approval`: Approve/Edit = `once`, Reject = `deny`);
   - `run.completed` without a block → `Result`; `run.failed` / `run.cancelled` → `Failed`.
3. **Klean blocks.** Hermes is told to stop and emit exactly one JSON object on its own line when it needs
   the user:
   ```json
   {"klean":"question","text":"Which email address should I use for Khalid?"}
   {"klean":"approval","action":"send_email","draft":"Hi all, …","recipients":["a@x.com","b@x.com"]}
   {"klean":"draft","text":"…"}
   ```
   `KleanBlocks.parse` (`:shared`, unit-tested) finds them anywhere in the text, tolerating code fences.
4. **Answer / decide.** For a Klean block, Klean starts the next run in the same session with
   `{"klean":"answer","text":"…"}` or
   `{"klean":"decision","decision":"APPROVED|EDITED|REJECTED","draft":"…","recipients":[…]}` as `input`.
   The edited draft and recipients are what Hermes must use.
5. **Approval policy.** `ApprovalPolicy` (`:shared`) decides whether an action needs the user. More than 5
   recipients always needs approval, in every mode, even when Hermes didn't ask (Klean then turns the
   result into an approval request rather than accepting it).

## Configuration

Settings › Delegation: the Hermes URL (the API Server, or the Klean backend's relay
`<backend>/api/v1/hermes`, see `docs/backend-api.md`) and the token (stored in `SecretStore`
`hermes.token`, never synced or backed up unless the user opts in).

## Tests

- `HermesAgentTest` drives `HermesAgent` against a scripted stub (Ktor `MockEngine` playing the API
  Server: run → question → answer → approval with 6 recipients → edited decision → result), and checks the
  requests Klean sends.
- On Waydroid, `scripts/device/hermes-stub.py` serves the same script over HTTP on the host, so the whole
  flow runs with notifications. No real Hermes is ever called by tests.
