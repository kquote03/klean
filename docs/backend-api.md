# Klean backend API (M24)

Everything is under `/api/v1` unless noted. Bodies are JSON (`kotlinx.serialization`, unknown keys ignored).
Errors are `{"error": "…"}` with a matching status. Auth is `Authorization: Bearer <device token>` unless noted.
"Admin" means a device token created by `POST /auth/login` (the web's token); paired phones are not admins.
Code: `backend/src/main/kotlin/app/klean/backend/`. Wire DTOs for sync live in `:shared`
(`app.klean.shared.sync`), so Android uses the same classes.

## Ops

| Method & path | Auth | Notes |
|---|---|---|
| `GET /health` | none | `{"status":"ok"}`, or 503 `{"status":"unavailable"}` if the DB is down |
| `POST /admin/backup` | admin | `application/zip`: `klean.sqlite` (VACUUM INTO snapshot) + `blobs/…`. Postgres: 501 + `pg_dump` hint |

Every response carries `X-Request-Id` (echoed if sent). Logs are JSON lines with `callId`.

## Auth, pairing, devices

| Method & path | Auth | Body → response |
|---|---|---|
| `POST /auth/login` | none | `{password, deviceName?, deviceId?}` → `{deviceId, token}` (a WEB admin device) |
| `GET /auth/me` | device | → `DeviceDto` |
| `POST /pair/start` | admin | → `{code: "ABCD-EFGH", qr: "klean://pair?url=<urlencoded KLEAN_PUBLIC_URL>&code=ABCD-EFGH", expiresAt}` (10 min, single use) |
| `POST /pair/claim` | none | `{code, deviceName, platform: ANDROID\|WEAR\|WEB, deviceId?}` → `{deviceId, token}`. 401 bad/expired/used code; 409 deviceId taken |
| `GET /devices` | device | → `[DeviceDto]` (`{id, name, platform, admin, createdAt, lastSeenAt, current}`), revoked ones hidden |
| `DELETE /devices/{id}` | admin, or the device itself | 204; the token stops working at once |
| `POST /devices/app-password` | admin | `{name}` → `{deviceId, username: "klean", token}`: a DAV-only device for DAVx5 etc. |

`deviceId` on claim/login lets a client keep its own local device id (the `device_id` it stamps on rows),
so server and client ids match. Allowed: `[A-Za-z0-9_-]{1,64}`. Tokens: 32 random bytes, base64url; only their
sha256 is stored. Pair codes are case-insensitive and the dash is optional.

## Sync

| Method & path | Body → response |
|---|---|
| `POST /sync/push` | `PushRequest{deviceId, records: [SyncRecord]}` (≤ 1000) → `PushResponse{accepted, serverCursor}` |
| `GET /sync/pull?cursor=0&limit=500&tables=a,b` | → `PullResponse{records, cursor, hasMore}`; `limit` 1..1000 |

- `SyncRecord{table, id, updatedAt, deviceId, deleted, json}`; `json` = the row's snake_case columns minus
  `change_seq`. `table` must match `^[a-z][a-z0-9_]{0,63}$`, `id`/`deviceId` ≤ 64 chars; invalid records are skipped.
- The server stores one row per `(table, id)` and merges with `lww()` from `:shared`: higher `updatedAt` wins,
  ties go to the lexically larger `deviceId`; an identical version is a no-op. Losers are not counted in
  `accepted` and do not advance the cursor.
- Each accepted write gets the next server-wide `server_seq`; pull returns `server_seq > cursor` ascending.
  Keep the returned `cursor` and loop while `hasMore`. A client receives its own pushes back; LWW makes that a no-op.
- Tombstones (`deleted=true`) are ordinary versions and are never purged. An older live edit never resurrects
  a tombstone.
- Server-only JSON keys start with `dav_` (e.g. `dav_extras`, unmodelled vCard/iCal properties). Clients may
  ignore them; when a client pushes a row without them, the server carries them over.
- Rows written over DAV use the server clock as `updatedAt` and the DAV device's id as `deviceId`.

## Blobs

| Method & path | Notes |
|---|---|
| `PUT /blobs/{sha256}` | raw body; 201 new, 200 already stored, 400 hash mismatch or bad hash, 413 over `KLEAN_BLOB_MAX_MB` |
| `GET /blobs/{sha256}` | the bytes, original `Content-Type`, `ETag`, immutable caching |
| `HEAD /blobs/{sha256}` | 200 with `Content-Length`, or 404: use it to skip uploads |

`sha256` is lowercase hex of the content. Upload blobs before pushing records that reference them.

## DAV (outside `/api/v1`; HTTP Basic, user `klean`, password = any device token, normally an app password)

`/.well-known/carddav` and `/.well-known/caldav` → 301 `/dav/`. Principal `/dav/principal/`; address book
`/dav/contacts/klean/` (members `<id>.vcf`, the `contact` table); calendar `/dav/calendars/klean/` (members
`<id>.ics`, the `event` table). Methods: OPTIONS, PROPFIND (Depth 0/1), REPORT (`sync-collection`,
`addressbook-multiget`, `calendar-multiget`, `addressbook-query`, `calendar-query` — query filters ignored),
GET/HEAD, PUT (`If-Match`, `If-None-Match: *`), DELETE (tombstone). ETag = the record's sha256; sync token
`http://klean.app/ns/sync/<seq>`. vCard served as 3.0 unless `address-data version="4.0"` is requested.

Column mapping (`dav/DavMapping.kt`): contact `display_name`↔FN, `name_json`↔N, `phones`/`emails`
(`[{label,value}]`, label mobile↔TYPE=cell, home, work, fax, main↔pref, other↔no type)↔TEL/EMAIL,
`addresses`↔ADR, `org`, `notes`↔NOTE, `birthday_month/day/year`↔BDAY, `anniversary`, `remote_uid`↔UID
(when it differs from the id). Event `title`↔SUMMARY, `notes`↔DESCRIPTION, `location`, `start_at`/`end_at`/
`all_day`/`zone`↔DTSTART/DTEND, `rrule` (and `recurrence` if present)↔RRULE, `exdates` (JSON array of epoch ms)
↔EXDATE, `reminder_minutes`↔VALARM, `uid`↔UID. JSON-in-TEXT columns are written as JSON strings, like Room.

## Web push

| Method & path | Body → response |
|---|---|
| `GET /push/vapid-public-key` | → `{publicKey}` (base64url raw P-256 point; pass to `pushManager.subscribe`) |
| `POST /push/subscribe` | `PushSubscription.toJSON()` = `{endpoint, keys: {p256dh, auth}}` → 201 (https endpoints only) |
| `POST /push/unsubscribe` | `{endpoint}` → 204 |
| `POST /push/test` | → `{sent, failed}`; sends `{"title":"Klean","body":…,"tag":"klean-test"}` to the caller's subscriptions |

Payloads are `PushMessage{title, body, tag?, url?}` JSON, encrypted `aes128gcm`. Endpoints answering 404/410 are
dropped. `PushService.send(message, deviceId?)` is the hook for server-side schedules (M26).

## Google (server-side, for the web)

| Method & path | Auth | Notes |
|---|---|---|
| `GET /google/status` | device | `{configured, connected}` |
| `GET /google/oauth/start` | admin | → `{url}`; open it in the browser (scopes: tasks, calendar.readonly; offline) |
| `GET /google/oauth/callback?code&state` | none (state) | Google redirects here; stores the refresh token, then 302 `/?google=connected` |
| `DELETE /google/oauth` | admin | forget the refresh token |
| `GET /google/calendar/events?calendarId=primary&timeMin&timeMax&pageToken` | device | Google's JSON, single events, ordered by start |
| `GET /google/tasks/lists` | device | Google's JSON |
| `GET /google/tasks/lists/{list}/tasks?pageToken` | device | Google's JSON, completed + hidden included |
| `PATCH /google/tasks/lists/{list}/tasks/{task}` | device | only `title`, `notes`, `due` (`YYYY-MM-DD`), `status` (`needsAction`\|`completed`) are forwarded |

There is no route or code path that creates a Google task. 409 when not connected, 503 when not configured.

## Hermes relay and AI proxy

| Method & path | Upstream |
|---|---|
| any `/hermes/{path...}` | `KLEAN_HERMES_URL/{path}` + query, `Authorization: Bearer KLEAN_HERMES_TOKEN`; responses (incl. SSE) streamed |
| `POST /ai/v1/chat/completions`, `GET /ai/v1/models` | `KLEAN_AI_UPSTREAM/chat/completions`, `/models` (+ `KLEAN_AI_API_KEY`) |

The caller's token is never forwarded. 503 when not configured, 502 when unreachable, `..` path segments refused.
