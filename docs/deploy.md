# Deploying the Klean server

The server is one container image, `klean-server`, with one volume (`/data`) and one port (8080). It runs the
sync backend and serves the Klean web app at `/`. The volume holds the SQLite database, photo/attachment blobs
and generated keys (VAPID). Klean on the phone works fully without it; the server adds sync between devices,
the web app, DAV for other apps, and server-side integrations.

## Run

Every push to `main` builds, tests and publishes `ghcr.io/kquote03/klean-server` for linux/amd64 and
linux/arm64 (`.github/workflows/docker.yml`). Compose uses it by default:

```sh
docker compose -f deploy/docker-compose.yml up -d     # pulls ghcr.io/kquote03/klean-server:latest
curl -s localhost:8080/api/v1/health                 # {"status":"ok"}
docker compose -f deploy/docker-compose.yml logs -f   # one JSON object per line
docker compose -f deploy/docker-compose.yml down      # keeps the klean-data volume
```

Or plain Docker: `docker run -d --name klean -p 8080:8080 -v klean-data:/data ghcr.io/kquote03/klean-server:latest`.

Image tags:

| Tag | Meaning |
|---|---|
| `latest`, `main` | Newest commit on `main` |
| `sha-<short>` | One commit |
| `0.1.0-<count>-<short>` | Same version name as that commit's nightly APK |
| `m25`, `m26`, … | Milestones |

For a deployment that never changes under you, pin a tag: `KLEAN_IMAGE=ghcr.io/kquote03/klean-server:sha-<short>`.

The container runs as an unprivileged user (uid 10001). If you bind-mount a host directory instead of the named
volume, make it writable for that uid: `sudo chown 10001:10001 /srv/klean`.

## Build from source

Only Docker is needed: the `Dockerfile` at the repo root builds the web app (Node, pnpm) and the server (JDK,
Gradle) in their own stages, then copies the result onto a JRE image.

```sh
docker build -t klean-server:dev .
scripts/image-smoke.sh klean-server:dev                       # optional: API, web app, non-root, /data writable
docker compose -f deploy/docker-compose.yml up -d --build     # or build + run through compose
```

Builds are reproducible: base images are pinned by digest, dependencies by lockfiles, jars carry no timestamps.
`scripts/image-repro.sh` builds twice without cache (with `SOURCE_DATE_EPOCH` = the commit time and
`rewrite-timestamp=true`) and checks that both image digests match; CI runs it on every push.
Multi-arch: `docker buildx build --platform linux/amd64,linux/arm64 .` (the build stages run natively; only the
runtime stage is per-architecture, so no emulation is needed).

Without Docker: `nix develop .#web -c ./gradlew :backend:installDist` and run
`backend/build/install/backend/bin/backend` (set `KLEAN_DATA_DIR`, default `./data`). `-Pklean.web` is `auto`
(default: include the web app when pnpm is on PATH), `require`, `prebuilt` (package an existing `web/dist`) or
`false`.

## Configuration (environment)

| Variable | Default | Meaning |
|---|---|---|
| `KLEAN_ADMIN_PASSWORD` | generated | Admin password for the web's first login. Stored argon2id-hashed. If never set, one is generated into `/data/admin-password.txt` (`docker compose exec klean cat /data/admin-password.txt`). Changing the variable and restarting rotates it. |
| `KLEAN_PUBLIC_URL` | `http://localhost:8080` | The URL phones use; it goes into the pairing QR. Set it to the Tailscale/LAN/HTTPS URL. |
| `KLEAN_DATA_DIR` | `/data` in the image | Database, blobs, generated keys. |
| `KLEAN_DB_URL` | `jdbc:sqlite:/data/klean.sqlite` | `jdbc:postgresql://host:5432/klean` selects Postgres (`KLEAN_DB_USER`, `KLEAN_DB_PASSWORD`). |
| `KLEAN_PORT` / `KLEAN_HOST` | `8080` / `0.0.0.0` | Listen address. |
| `KLEAN_BLOB_MAX_MB` | `50` | Largest accepted blob. |
| `KLEAN_AI_UPSTREAM` | unset | OpenAI-compatible base URL including `/v1`, e.g. `http://host.docker.internal:11434/v1` (Ollama). Enables `/api/v1/ai/v1/chat/completions`. `KLEAN_AI_API_KEY` is sent upstream if set. |
| `KLEAN_HERMES_URL` / `KLEAN_HERMES_TOKEN` | unset | Hermes API server; enables the `/api/v1/hermes/*` relay. The token never leaves the server. |
| `KLEAN_GOOGLE_CLIENT_ID` / `KLEAN_GOOGLE_CLIENT_SECRET` | unset | Your own Google OAuth "Web application" client (see `docs/google-oauth.md`). Redirect URI: `<KLEAN_PUBLIC_URL>/api/v1/google/oauth/callback` (override with `KLEAN_GOOGLE_REDIRECT_URI`). |
| `KLEAN_VAPID_SUBJECT` | `mailto:admin@klean.invalid` | Contact URL in web-push VAPID tokens; set a real `mailto:`. |
| `KLEAN_LOG_LEVEL` / `KLEAN_LOG_FORMAT` | `INFO` / `json` | `KLEAN_LOG_FORMAT=plain` for human-readable logs. |

## Access options

Pick one. The phone and the browser must reach `KLEAN_PUBLIC_URL`.

### 1. Tailscale (default, recommended)

No ports open to the internet, real HTTPS, works from anywhere your devices are on the tailnet.

```sh
# on the server
KLEAN_BIND=127.0.0.1:8080 KLEAN_PUBLIC_URL=https://<machine>.<tailnet>.ts.net \
  docker compose -f deploy/docker-compose.yml up -d
tailscale serve --bg 8080          # https://<machine>.<tailnet>.ts.net → 127.0.0.1:8080
```

Install Tailscale on the phone, sign in to the same tailnet, then pair (below). Web push needs HTTPS, which
`tailscale serve` provides.

### 2. LAN only

```sh
KLEAN_PUBLIC_URL=http://192.168.1.20:8080 docker compose -f deploy/docker-compose.yml up -d
```

Plain HTTP on the home network: fine for sync and DAV, but browsers only allow web push and the PWA install on
HTTPS (or `localhost`). Tokens travel in clear text on the LAN, so prefer Tailscale if the network is shared.

### 3. Reverse proxy with TLS (public domain)

- **Caddy:** `deploy/caddy/Caddyfile` (automatic HTTPS; streaming routes are not buffered).
- **Traefik:** `docker compose -f deploy/docker-compose.yml -f deploy/traefik/docker-compose.traefik.yml up -d`
  with `KLEAN_HOST_NAME`, `ACME_EMAIL` and `KLEAN_PUBLIC_URL=https://$KLEAN_HOST_NAME`.

Bind the backend to `127.0.0.1` (`KLEAN_BIND=127.0.0.1:8080`) so only the proxy reaches it. Choose a long
admin password: the login route is on the internet.

## First setup and pairing

1. Open the web client at `KLEAN_PUBLIC_URL` and log in with the admin password. That creates the web's device
   token (`POST /api/v1/auth/login`).
2. Settings → Devices → "Pair a phone" shows a QR (`klean://pair?url=…&code=XXXX-XXXX`, valid 10 minutes, single
   use). On the phone: Settings → Sync → scan, or paste the link.
3. Devices can be revoked from the same screen (`DELETE /api/v1/devices/{id}`).

## DAV (DAVx5, Thunderbird, …)

Create an app password (web: Settings → Devices → "App password for DAV", or `POST /api/v1/devices/app-password`).
In DAVx5 choose "Login with URL and user name": URL `KLEAN_PUBLIC_URL/dav/`, user `klean`, password = the app
password. The address book is `/dav/contacts/klean/`, the calendar `/dav/calendars/klean/`.

## Backups

- `POST /api/v1/admin/backup` (admin token) streams a zip with a consistent SQLite snapshot (`VACUUM INTO`) and
  all blobs. With Postgres it answers 501 with a `pg_dump` hint; back up the database with `pg_dump` and the
  `/data/blobs` directory with any file backup.
- Or snapshot the `klean-data` volume while the container is stopped.

## Updating

```sh
docker compose -f deploy/docker-compose.yml pull && docker compose -f deploy/docker-compose.yml up -d
```

(From source: `docker compose -f deploy/docker-compose.yml up -d --build`.)

Schema changes are additive and applied at start.
