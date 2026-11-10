# Plan: reproducible GHCR image for backend + web, and a parallel milestone agent

## Goal

Every push to `main` builds one reproducible container image holding the Klean sync server and the web app, smoke-tests it, and publishes it to `ghcr.io/kquote03/klean-backend` for amd64 and arm64. Meanwhile, one background agent continues with milestones M21–M29 in its own worktree.

## Answer to "is it dockerized?"

**Partly.**

What is already in place:
- **The image:** the backend has been a container image since M24. It is built with **Jib** (no Dockerfile): `./gradlew :backend:jibDockerBuild` produces `klean-backend:dev`, and `deploy/docker-compose.yml` runs it.
- **The web app:** since M26 it is bundled *inside* that same image. `:backend:processResources` runs `pnpm build` and the server serves `web/dist` at `/`.

What is missing:
1. **The web app is silently left out when `pnpm` isn't on PATH.** The CI `backend` job (`.github/workflows/build.yml`) has no pnpm, so its `jibBuildTar` image has **no web app**.
2. **The image is not reproducible:**
   - `container.creationTime = "USE_CURRENT_TIMESTAMP"` changes the digest on every build.
   - The base image `eclipse-temurin:21-jre` is a floating tag.
3. **Nothing publishes to GHCR.** CI only builds a tarball and throws it away.
4. **There is no smoke test of the built image.**
5. **There are no OCI labels,** so GHCR can't link the package to the repo.

## Current context / assumptions

- **Repo:**
  - `/home/kenan1099/Src/klean`, branch `main` at `bd13b54`, public on GitHub as `kquote03/klean`.
  - Tags `m0`–`m20`, `m24`, `m25`, `m26`.
  - Gate: 1032 tests green.
- **Build:**
  - Gradle 9.9.0, Jib Gradle plugin 3.5.4 (`gradle/libs.versions.toml`: `jib = "3.5.4"`). Jib config is in `backend/build.gradle.kts`, lines ~57–120.
  - `web/package.json` pins `"packageManager": "pnpm@12.9.0"`. Node 22.
  - The web build reads `core/design/tokens/klean-color-tokens.json`, the Cantarell fonts and the launcher vector from the Android tree.
- **CI:**
  - `ci.yml` (push to main, PRs) calls the reusable `build.yml`. `build.yml` has three jobs: `gate` (Android), `backend` (tests + `jibBuildTar`) and `web` (pnpm test/build).
  - `nightly.yml` calls `build.yml` for dated APK releases.
  - Every job needs the Android SDK set up, because Gradle configures every project.
- **Server behaviour:**
  - Health endpoint: `GET /api/v1/health` returns `{"status":"ok"}`.
  - `web/index.html` has `<title>Klean</title>`.
  - Unknown `/api/...` paths return 404. Non-API paths fall back to `index.html` (`backend/src/main/kotlin/app/klean/backend/web/WebRoutes.kt`).
- **Local machine rules:**
  - NixOS. Every tool comes from `nix develop -c …` or `nix develop -c nix shell nixpkgs#<pkg> -c …`.
  - Every Gradle call goes through `flock ~/.hermes/cache/scratch/gradle.lock`.
  - `nix develop .#web` provides node and pnpm.
  - Docker 29 runs rootless on the host.
  - The host shell sometimes lacks coreutils/git on PATH. Prefix commands with `export PATH=/run/current-system/sw/bin:/etc/profiles/per-user/kenan1099/bin:$PATH`.
- **The user will send bug reports and suggestions over Telegram while this runs.** The parent agent (me) handles those on `main`. So the parent owns `main`'s working tree, and the milestone agent must work in a separate worktree.
- **`AGENTS.md` is protected.** Change it only after the user says "approve AGENTS.md". Until then, record new facts in `docs/handover-2026-11-10.md`.

## Architecture / proposed approach

**Keep Jib, without adding a Dockerfile.** Jib:
- builds without a Docker daemon;
- already uses fixed file timestamps;
- produces byte-identical images when the inputs are identical and the base is pinned.

**Make the image reproducible:**
- pin the base image by digest;
- drop the current-timestamp creation time;
- make the web build *required* for release images (`-Pklean.web=require`, failing loudly instead of skipping).

**CI:** a new reusable workflow `.github/workflows/docker.yml` replaces the `backend` job. It:
- runs backend tests;
- builds a single-platform tarball;
- smoke-tests it in Docker (API + web);
- builds it a second time from clean and checks the digest is identical;
- on pushes to `main` and milestone tags, pushes a multi-arch image to GHCR with `GITHUB_TOKEN`.

**The milestone agent** runs in `../klean-next` on branch `next`, never touches the Docker/CI files, and pushes its branch. The parent merges it into `main` milestone by milestone.

---

## Part A — launch the milestone agent (parent, first, ~5 min)

### A1. Create the worktree

```bash
export PATH=/run/current-system/sw/bin:/etc/profiles/per-user/kenan1099/bin:$PATH
cd /home/kenan1099/Src/klean
git worktree add ../klean-next -b next main
git worktree list
```

Expected: two lines, `/home/kenan1099/Src/klean bd13b54 [main]` and `/home/kenan1099/Src/klean-next bd13b54 [next]`.

### A2. Write the lane brief `~/.hermes/cache/scratch/lane-next.md`

Write this file with exactly the content below.

````markdown
# Lane NEXT — milestones M21–M28 (+ UI polish), then M29 prep

## Where
- Worktree `/home/kenan1099/Src/klean-next`, branch `next`.
- Push the branch after every commit: `git push -q -u origin next`. Never push `main`, never tag.
- The parent merges `next` into `main` when a milestone's TASKS.md section is fully ticked.
- At the start of every milestone, run `git fetch -q origin && git merge --no-edit origin/main`. The parent keeps landing Telegram bug fixes and the Docker work on `main`. If a merge conflicts, resolve it by keeping both sides and run the gate.

## Read first
- `AGENTS.md`
- `docs/handover-2026-11-10.md`
- The governing plan `.hermes/plans/2026-11-09_165408-klean-holistic-full-product.md`, sections M21, M22, M23, M27, M28, M29 and "Risks".
- `~/.hermes/cache/scratch/klean-subagent-brief.md`. Its last "State update" section wins over older notes.
- The `android-app-development` skill.
- `TASKS.md`

## Hands off (the parent owns these right now)
- `backend/build.gradle.kts`
- `.github/workflows/*`
- `deploy/*`
- `docs/deploy.md`
- `scripts/image-*.sh`

If you need a backend route, add a new file under `backend/src/main/kotlin/app/klean/backend/` and a `routing {}` call in `Application.kt`, and document it in `docs/backend-api.md`.

## Rules
- Commit early and small (Conventional Commits), and only your own paths. Never `git add -A`, never `git stash`.
- Use test-driven development for logic.
- Never change an existing Room entity without an additive AutoMigration plus a migration test from schema 3's fixture.
- **Gradle:** `flock ~/.hermes/cache/scratch/gradle.lock nix develop -c ./gradlew … --console=plain -Pkotlin.compiler.execution.strategy=in-process`.
- **Gate:** `assembleDebug :app:assembleDebugAndroidTest :shared:jvmTest testDebugUnitTest :backend:test`. Add `:wear:testDebugUnitTest` once it exists.
- **Device:** Waydroid only (`adb -s 192.168.240.112:5555`).
  - Do one whole check per `~/.hermes/cache/scratch/waydroid.lock` hold, and never hold it longer than 20 minutes.
  - Install with `adb install -r -d`.
  - No camera intents, no system file picker.
  - Don't touch the user's Pixel.
- **Copy:** calm, no `!`, no guilt words, never ADHD/autism. Diet stays frozen.
- **No python3 on the host:** use `nix shell nixpkgs#python3 -c python3`. If `git` or coreutils are missing, `export PATH=/run/current-system/sw/bin:/etc/profiles/per-user/kenan1099/bin:$PATH`.
- **Usage limits can cut you off; only commits survive.** Keep TASKS.md ticked as you go, so a fresh agent can resume from it.

## Order of work (one milestone at a time; each ends with the gate green and a `## Mxx` TASKS.md section)

### 0. UI polish (from the M25 visual review)

**a. Calendar view selector runs off a 540 px screen.**
- In `feature/calendar/src/main/kotlin/app/klean/feature/calendar/ui/CalendarScreen.kt`, replace the horizontally scrolling `FilterChip` row (around lines 179–191, inside `CalendarScreenContent`) with a one-button picker.
- Add the string `<string name="calendar_view_pick">View: %1$s</string>` to `feature/calendar/src/main/res/values/strings.xml`.
- Test first. In `feature/calendar/src/test/kotlin/app/klean/feature/calendar/ui/CalendarScreenTest.kt`, change `switchingViewsReportsTheChoice` to:
  ```kotlin
  @Test fun switchingViewsReportsTheChoice() {
      val views = mutableListOf<CalendarView>()
      show(CalendarView.AGENDA, onView = { views += it })
      compose.onNodeWithContentDescription("View: Agenda").performClick()
      compose.onNodeWithText("Month").performClick()
      assertEquals(listOf(CalendarView.MONTH), views)
  }

  @Test @Config(qualifiers = "w360dp-h740dp") fun viewPickerFitsANarrowPhone() {
      show(CalendarView.WEEK)
      val root = compose.onRoot().getUnclippedBoundsInRoot()
      val picker = compose.onNodeWithContentDescription("View: Week").getUnclippedBoundsInRoot()
      assertTrue(picker.right <= root.right)
  }
  ```
  Add the imports `androidx.compose.ui.test.onRoot` and `androidx.compose.ui.test.getUnclippedBoundsInRoot`.
- Run `:feature:calendar:testDebugUnitTest --tests '*CalendarScreenTest'`. It must FAIL: there is no node "View: Agenda".
- Implement. Put the picker in the row in place of the chip row: `ViewPicker(state.view, onView, Modifier.weight(1f))`. Keep the previous/next arrows.
  ```kotlin
  /** One button showing the current view; its menu lists all views (fits any phone width, unlike a chip row). */
  @Composable
  private fun ViewPicker(current: CalendarView, onView: (CalendarView) -> Unit, modifier: Modifier = Modifier) {
      var open by remember { mutableStateOf(false) }
      val label = stringResource(current.labelRes())
      val desc = stringResource(R.string.calendar_view_pick, label)
      Box(modifier, contentAlignment = Alignment.Center) {
          TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = desc }) {
              Text(label, maxLines = 1)
              Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
          }
          DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
              CalendarView.entries.forEach { v ->
                  DropdownMenuItem(
                      text = { Text(stringResource(v.labelRes())) },
                      onClick = { open = false; onView(v) },
                      modifier = Modifier.heightIn(min = 48.dp),
                  )
              }
          }
      }
  }
  ```
  If `Icons.Rounded.ArrowDropDown` doesn't resolve, use `Icons.Rounded.KeyboardArrowDown`. Remove the now-unused `FilterChip` and `horizontalScroll` imports.
- Run the test again. Expected: PASS. Commit: `fix(calendar): one view picker instead of a chip row that ran off narrow phones`.

**b. Week-view block titles break mid-word.** In `Block(...)` (same file, around line 390):
- Use `maxLines = 1` and `overflow = TextOverflow.Ellipsis` for the title when the view is `WEEK`. Pass `compact: Boolean` from `TimeGrid` (`compact = state.view == CalendarView.WEEK`).
- Hide the time line when `compact`.
- Verify on Waydroid: open the Calendar, choose Week, screenshot with `~/.hermes/cache/scratch/m25/bin/wshot`. Titles end in "…" and never wrap mid-word.
- Commit: `fix(calendar): week blocks show one ellipsized title line`.

**c. The Tasks tab row scrolls and cuts the last tab at 540 px.**
- In `feature/tasks/src/main/kotlin/app/klean/feature/tasks/ui/TasksScreen.kt` around line 128, keep `PrimaryScrollableTabRow` but set `edgePadding = 0.dp`.
- Give each `Tab` `text = { Text(stringResource(t.label), maxLines = 1, softWrap = false) }`.
- Add a Robolectric test at `w360dp`: every tab label node exists, and the selected tab's bounds are within the root.
- Verify with a Waydroid screenshot, then commit.

### 1. M22 Health Connect + GPS (governing plan §M22)
- Replace `NoHealthConnectBridge` (`feature/workout/.../WorkoutModule.kt`) and `NoSleepImportSource` (`feature/sleep/.../SleepModule.kt`) with real bindings from a new `:core:health`.
- On Waydroid, prove the Health-Connect-unavailable path and a mock-GPS jog with the distance within 5%.

### 2. M23 Delegation + Hermes (§M23)
- The Hermes API server source is in `~/.hermes/hermes-agent/gateway/platforms/api_server*.py`; read it during the research step.
- With no user URL/token, test against a scripted local stub (`backend/src/test` style, or a tiny Ktor stub under `feature/delegation/src/test`). Never call a real Hermes.
- `ApprovalPolicy` (more than 5 recipients always needs approval) goes in `:shared`.

### 3. M21 Google Tasks overlay + Assistant (§M21)
- Use fakes and MockEngine only; the user's OAuth client IDs aren't available yet.
- Add a test that no `POST …/tasks` request is ever built.
- Write `docs/google-oauth.md` and `docs/assistant.md`.

### 4. M28 Capture everywhere (§M28)
- A handler for every action, including `delegate`.
- A "Move to…" correction for each module pair, each with a test.
- Disabling a module removes it from routing.

### 5. M27 Wear OS (§M27)
- Create the `wear/` module (min SDK 30): tiles, a complication, the Data Layer codec.
- Robolectric tests only. Real-watch checks are listed for M29.
- Add `:wear:testDebugUnitTest` to the gate.

### 6. M29 prep
- Write `docs/acceptance-p1.md` with every acceptance criterion from `/home/kenan1099/Downloads/Klean — Prototype 1 Build Prompt (1).md`, in the columns Waydroid / Pixel / Watch / Backend+Browser.
- Pre-fill the Waydroid and Backend+Browser columns with evidence.
- Stop there. The Pixel and watch columns are the user's session.

## Final report (also when cut off)
- commits per milestone
- test counts
- gate results
- Waydroid evidence paths
- shared files touched
- schema changes and their migrations
- deviations
- notes for AGENTS.md
- checks that need the Pixel or watch
- exactly where to resume
````

### A3. Dispatch it

Call `delegate_task` with one task:
- `goal`: "Execute lane NEXT exactly as specified in /home/kenan1099/.hermes/cache/scratch/lane-next.md."
- `context`: "Read /home/kenan1099/.hermes/cache/scratch/lane-next.md first and follow it completely. If git or coreutils are missing, export PATH=/run/current-system/sw/bin:/etc/profiles/per-user/kenan1099/bin:$PATH."

Expected: `status: dispatched` with one subagent id. Note the id; the parent can `steer` it when Telegram reports touch the same files.

### A4. Telegram reports while it runs (standing procedure)

1. Fix them on `main` in `/home/kenan1099/Src/klean`, test first, then gate. Commit and push.
2. If a fix touches a file the lane is probably editing (calendar, tasks, workout, sleep, capture), `steer` the agent with a one-line note naming the file and the change. It picks the change up at its next `git merge origin/main`.

---

## Part B — reproducible image and GHCR publishing (parent, on `main`)

All commands assume:

```bash
export PATH=/run/current-system/sw/bin:/etc/profiles/per-user/kenan1099/bin:$PATH
cd /home/kenan1099/Src/klean
G="flock $HOME/.hermes/cache/scratch/gradle.lock nix develop .#web -c ./gradlew --console=plain -Pkotlin.compiler.execution.strategy=in-process"
```

`nix develop .#web` is used so pnpm is on PATH. Check first that it also provides the JDK and Android SDK:

```bash
nix develop .#web -c bash -c 'java -version 2>&1 | head -1; echo $ANDROID_HOME; pnpm -v'
```

Expected: a 21.x JDK, a non-empty SDK path, and `12.9.0`.

If the JDK or SDK is missing, use this form instead:

```bash
G="flock … nix develop -c nix shell nixpkgs#pnpm nixpkgs#nodejs_22 -c ./gradlew …"
```

### B1. Red: a smoke test that fails against today's image (~5 min)

Create `scripts/image-smoke.sh` and make it executable (`chmod +x`):

```bash
#!/usr/bin/env bash
# Loads a Jib image tarball, runs it with a throwaway container and checks that the API and the web app answer.
# Usage: scripts/image-smoke.sh [backend/build/jib-image.tar] [host-port]
set -euo pipefail
tar=${1:-backend/build/jib-image.tar}
port=${2:-18089}
image=$(docker load -i "$tar" | sed -n 's/^Loaded image: //p' | tail -1)
[ -n "$image" ] || { echo "could not load $tar"; exit 1; }
name="klean-smoke-$$"
docker run -d --rm --name "$name" -p "$port:8080" -e KLEAN_ADMIN_PASSWORD=smoke-test-password "$image" >/dev/null
trap 'docker rm -f "$name" >/dev/null 2>&1 || true' EXIT
for _ in $(seq 1 60); do
  curl -fsS "localhost:$port/api/v1/health" >/dev/null 2>&1 && break
  sleep 1
done
fail() { echo "SMOKE FAIL: $1"; docker logs "$name" 2>&1 | tail -40; exit 1; }
curl -fsS "localhost:$port/api/v1/health" | grep -q '"ok"' || fail "health"
curl -fsS "localhost:$port/" | grep -q '<title>Klean</title>' || fail "web app missing at /"
[ "$(curl -s -o /dev/null -w '%{http_code}' "localhost:$port/calendar")" = 200 ] || fail "SPA fallback /calendar"
[ "$(curl -s -o /dev/null -w '%{http_code}' "localhost:$port/api/v1/nope")" = 404 ] || fail "unknown API path must stay 404"
echo "image smoke OK: $image"
```

Build today's image the way CI does (no pnpm) and run the script:

```bash
flock ~/.hermes/cache/scratch/gradle.lock nix develop -c ./gradlew :backend:jibBuildTar --console=plain -Pkotlin.compiler.execution.strategy=in-process -Pklean.web=false
scripts/image-smoke.sh
```

Expected: `SMOKE FAIL: web app missing at /`. This is the red step and proves the CI image gap.

Commit: `test(deploy): image smoke script (API, web app at /, SPA fallback, API 404)`.

### B2. Red: a reproducibility check that fails today (~5 min)

Create `scripts/image-repro.sh` (`chmod +x`):

```bash
#!/usr/bin/env bash
# Builds the backend image tarball twice from clean outputs and fails unless both digests match.
# Usage: scripts/image-repro.sh   (needs pnpm on PATH; runs Gradle through the caller's GRADLE env or ./gradlew)
set -euo pipefail
gradle=${GRADLE:-./gradlew}
args=(:backend:jibBuildTar --console=plain -Pklean.web=require -Pklean.image.revision=repro-check)
build() {
  rm -rf backend/build/jib-image.* web/dist
  $gradle :backend:clean >/dev/null
  $gradle "${args[@]}" >/dev/null
  cat backend/build/jib-image.digest
}
first=$(build)
second=$(build)
echo "first:  $first"
echo "second: $second"
[ "$first" = "$second" ] || { echo "NOT REPRODUCIBLE"; exit 1; }
echo "reproducible: $first"
```

Run it:

```bash
GRADLE="flock $HOME/.hermes/cache/scratch/gradle.lock nix develop .#web -c ./gradlew -Pkotlin.compiler.execution.strategy=in-process" scripts/image-repro.sh
```

Expected now: the build fails with an error about the unknown value `klean.web=require`, or, if the property is read as "not false", it prints `NOT REPRODUCIBLE` because `USE_CURRENT_TIMESTAMP` changes the config digest. Either counts as red.

Commit: `test(deploy): image reproducibility check (two clean builds, same digest)`.

### B3. Green: `klean.web=require` (~3 min)

In `backend/build.gradle.kts`, replace these two lines:

```kotlin
val webEnabled = providers.gradleProperty("klean.web").map { it != "false" }.orElse(true)
val buildWeb = webEnabled.get() && pnpmOnPath.get() && webDir.file("package.json").asFile.exists()
```

with:

```kotlin
// klean.web: auto (default: build when pnpm is on PATH), require (release images: fail without pnpm), false (skip).
val webMode = providers.gradleProperty("klean.web").orElse("auto").get()
val buildWeb = when (webMode) {
    "false" -> false
    "require" -> {
        check(pnpmOnPath.get()) { "-Pklean.web=require but pnpm is not on PATH (use nix develop .#web)" }
        check(webDir.file("package.json").asFile.exists()) { "-Pklean.web=require but web/package.json is missing" }
        true
    }
    "auto" -> pnpmOnPath.get() && webDir.file("package.json").asFile.exists()
    else -> error("klean.web must be auto, require or false (was '$webMode')")
}
```

Verify:

```bash
flock ~/.hermes/cache/scratch/gradle.lock nix develop -c env PATH=/run/current-system/sw/bin ./gradlew :backend:help -Pklean.web=require --console=plain 2>&1 | grep -m1 "pnpm is not on PATH"
```

Expected: the line `-Pklean.web=require but pnpm is not on PATH …`.

Then:

```bash
$G :backend:jibBuildTar -Pklean.web=require && scripts/image-smoke.sh
```

Expected: `image smoke OK: klean-backend:dev`.

Commit: `build(backend): -Pklean.web=require fails instead of shipping an image without the web app`.

### B4. Green: pin the base image and drop the current timestamp (~5 min)

1. Get the multi-arch index digest of the base image:

   ```bash
   nix develop -c nix shell nixpkgs#crane -c crane digest docker.io/library/eclipse-temurin:21-jre
   ```

   Expected: one line `sha256:<64 hex>`. Call it `DIGEST`.

2. In `backend/build.gradle.kts`, replace the whole `jib { … }` block with the following, substituting `DIGEST`:

   ```kotlin
   // Release images build for amd64+arm64 (-Pklean.image.platforms=linux/amd64,linux/arm64, registry push only);
   // local jibDockerBuild/jibBuildTar keep one platform because Jib can't load a multi-platform image into Docker.
   val imagePlatforms = providers.gradleProperty("klean.image.platforms").orElse("linux/amd64").get()
       .split(",").map { it.trim() }.filter { it.isNotEmpty() }
   val imageRevision = providers.gradleProperty("klean.image.revision").orElse("dev").get()

   jib {
       from {
           // Pinned by digest so the same sources always give the same image. Bump: crane digest eclipse-temurin:21-jre
           image = "eclipse-temurin:21-jre@DIGEST"
           platforms {
               imagePlatforms.forEach { p ->
                   val (os, arch) = p.split("/")
                   platform { this.os = os; architecture = arch }
               }
           }
       }
       to {
           // CI overrides with -Djib.to.image=ghcr.io/kquote03/klean-backend:<version> and -Djib.to.tags=…
           image = "klean-backend:dev"
       }
       container {
           mainClass = "app.klean.backend.MainKt"
           ports = listOf("8080")
           volumes = listOf("/data")
           environment = mapOf(
               "KLEAN_DATA_DIR" to "/data",
               "KLEAN_PORT" to "8080",
           )
           jvmFlags = listOf("-XX:MaxRAMPercentage=75")
           // creationTime left at Jib's default (epoch) so builds are reproducible.
           labels.set(
               mapOf(
                   "org.opencontainers.image.source" to "https://github.com/kquote03/klean",
                   "org.opencontainers.image.title" to "klean-backend",
                   "org.opencontainers.image.description" to "Klean sync server with the Klean web app at /",
                   "org.opencontainers.image.revision" to imageRevision,
               ),
           )
       }
   }
   ```

3. Verify:
   - `scripts/image-repro.sh` with the same `GRADLE=` as in B2. Expected: the last line is `reproducible: sha256:…`.
   - `$G :backend:jibBuildTar -Pklean.web=require && scripts/image-smoke.sh`. Expected: `image smoke OK`.
   - `$G :backend:test`. Expected: `BUILD SUCCESSFUL`.

If the repro check still differs, find which layer differs: after each of the two builds, copy `backend/build/jib-image.tar` aside (`cp backend/build/jib-image.tar ~/.hermes/cache/scratch/img-1.tar`, then `img-2.tar`), and compare the layer lists with `diff <(tar -tvf ~/.hermes/cache/scratch/img-1.tar) <(tar -tvf ~/.hermes/cache/scratch/img-2.tar)`. Extract the differing layer from both tarballs and `diff -r` their contents. The usual culprit is a non-deterministic file in `web/dist` from `scripts/assets.mjs`; fix it there by sorting its output.

Commit: `build(backend): reproducible image — base pinned by digest, epoch creation time, OCI labels, configurable platforms`.

### B5. CI: `.github/workflows/docker.yml`, replacing the `backend` job (~5 min)

Create `.github/workflows/docker.yml`:

```yaml
# Server image (backend + web app at /): tests, a smoke run of the real image, a reproducibility check, and
# (pushes to main and milestone tags) a multi-arch push to ghcr.io/<owner>/klean-backend. Called by ci.yml.
name: docker

on:
  workflow_call:
    inputs:
      push:
        type: boolean
        default: false

jobs:
  image:
    runs-on: ubuntu-latest
    timeout-minutes: 45
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0 # version = commit count

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - uses: android-actions/setup-android@v3
        with:
          packages: "platforms;android-37.0 platforms;android-36 build-tools;36.0.0"

      - uses: gradle/actions/setup-gradle@v4

      - uses: pnpm/action-setup@v4
        with:
          package_json_file: web/package.json

      - uses: actions/setup-node@v4
        with:
          node-version: 22
          cache: pnpm
          cache-dependency-path: web/pnpm-lock.yaml

      - name: Backend tests
        run: ./gradlew :backend:test --console=plain

      - name: Image tarball (linux/amd64, web app required)
        run: ./gradlew :backend:jibBuildTar -Pklean.web=require -Pklean.image.revision=$GITHUB_SHA --console=plain

      - name: Smoke test the image
        run: scripts/image-smoke.sh backend/build/jib-image.tar

      - name: Reproducible build check
        run: scripts/image-repro.sh

      - name: Tags
        id: meta
        run: |
          owner="${GITHUB_REPOSITORY_OWNER,,}"
          version="0.1.0-$(git rev-list --count HEAD)-$(git rev-parse --short HEAD)"
          tags="sha-$(git rev-parse --short HEAD)"
          if [ "$GITHUB_REF" = "refs/heads/main" ]; then tags="$tags,main,latest"; fi
          if [ "$GITHUB_REF_TYPE" = "tag" ]; then tags="$tags,$GITHUB_REF_NAME"; fi
          echo "image=ghcr.io/$owner/klean-backend" >> "$GITHUB_OUTPUT"
          echo "version=$version" >> "$GITHUB_OUTPUT"
          echo "tags=$tags" >> "$GITHUB_OUTPUT"

      - name: Push to GHCR (linux/amd64 + linux/arm64)
        if: inputs.push
        env:
          REGISTRY_PASSWORD: ${{ secrets.GITHUB_TOKEN }}
        run: >
          ./gradlew :backend:jib --console=plain
          -Pklean.web=require
          -Pklean.image.revision=$GITHUB_SHA
          -Pklean.image.platforms=linux/amd64,linux/arm64
          -Djib.to.image=${{ steps.meta.outputs.image }}:${{ steps.meta.outputs.version }}
          -Djib.to.tags=${{ steps.meta.outputs.tags }}
          -Djib.to.auth.username=${{ github.actor }}
          -Djib.to.auth.password=$REGISTRY_PASSWORD

      - name: Summary
        if: inputs.push
        run: |
          echo "Pushed \`${{ steps.meta.outputs.image }}:${{ steps.meta.outputs.version }}\` (+ ${{ steps.meta.outputs.tags }})" >> "$GITHUB_STEP_SUMMARY"
          echo "Digest: \`$(cat backend/build/jib-image.digest)\`" >> "$GITHUB_STEP_SUMMARY"

      - name: Test reports
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: backend-test-reports
          path: backend/build/reports/tests/
          retention-days: 7
```

Then:

1. **`build.yml`:** delete the whole `backend:` job (from the comment `# The sync backend (M24)…` through its `retention-days: 7`). The `docker` workflow now owns backend tests and the image. Leave the `gate` and `web` jobs alone.
2. **`ci.yml`:**
   - Add tags to the trigger:
     ```yaml
     on:
       push:
         branches: [main]
         tags: ["m*", "p*"]
       pull_request:
       workflow_dispatch:
     ```
   - Add this job after `build:`:
     ```yaml
       docker:
         uses: ./.github/workflows/docker.yml
         with:
           push: ${{ github.event_name == 'push' }}
         permissions:
           contents: read
           packages: write
     ```
   - Make sure `rolling-release` still has `if: … github.ref == 'refs/heads/main'`. It does, so tag pushes don't replace the nightly.
3. **Lint the workflows locally:**
   ```bash
   nix develop -c nix shell nixpkgs#actionlint -c actionlint .github/workflows/*.yml
   ```
   Expected: no output, exit 0.

Commit: `ci: docker workflow — backend tests, image smoke + reproducibility, multi-arch push to GHCR`.

### B6. Compose and docs use the published image (~4 min)

1. **`deploy/docker-compose.yml`:**
   - Change `image: ${KLEAN_IMAGE:-klean-backend:dev}` to `image: ${KLEAN_IMAGE:-ghcr.io/kquote03/klean-backend:latest}`.
   - Replace the header comment lines 2–4 with:
     ```yaml
     #   docker compose -f deploy/docker-compose.yml up -d           # pulls ghcr.io/kquote03/klean-backend:latest
     #   curl -s localhost:8080/api/v1/health                        # {"status":"ok"}
     # Local build instead: ./gradlew :backend:jibDockerBuild && KLEAN_IMAGE=klean-backend:dev docker compose … up -d
     ```
2. **`docs/deploy.md`, section "## Build and run":** state that the published image is the default, and list the tags:
   - `latest`/`main`: newest `main`.
   - `sha-<short>`: one commit.
   - `0.1.0-<count>-<sha>`: matches the APK's version name.
   - `m25`, `m26`, …: milestones.

   Also say:
   - It runs on amd64 and arm64.
   - Pin `KLEAN_IMAGE=ghcr.io/kquote03/klean-backend:<tag>` for reproducible deployments.
   - Building locally uses `KLEAN_IMAGE=klean-backend:dev`.
3. **`docs/deploy.md`, section "## Updating":** `docker compose pull && docker compose up -d`.
4. **`web/e2e/run.sh`:** already exports `KLEAN_IMAGE`, so no change. Check: `grep -n KLEAN_IMAGE web/e2e/run.sh` shows line 9.

Verify locally against the local build:

```bash
$G :backend:jibDockerBuild -Pklean.web=require
KLEAN_IMAGE=klean-backend:dev KLEAN_BIND=18090 docker compose -p klean-ghcrcheck -f deploy/docker-compose.yml up -d
curl -s localhost:18090/api/v1/health
curl -s localhost:18090/ | grep -o '<title>Klean</title>'
docker compose -p klean-ghcrcheck -f deploy/docker-compose.yml down -v
```

Expected: `{"status":"ok"}`, then `<title>Klean</title>`.

Commit: `docs(deploy): run the published GHCR image by default; tags and update steps`.

### B7. Gate, push, watch CI, verify GHCR (~10 min, mostly waiting)

```bash
timeout 1500 flock ~/.hermes/cache/scratch/gradle.lock nix develop -c ./gradlew assembleDebug :app:assembleDebugAndroidTest :shared:jvmTest testDebugUnitTest :backend:test --console=plain -Pkotlin.compiler.execution.strategy=in-process > ~/.hermes/cache/scratch/gate.log 2>&1; grep -E "BUILD (SUCC|FAIL)" ~/.hermes/cache/scratch/gate.log
git push -q origin main
gh run list -R kquote03/klean --limit 1 --json databaseId --jq '.[0].databaseId' | xargs -I{} gh run watch {} -R kquote03/klean --exit-status
```

Expected:
- `BUILD SUCCESSFUL`.
- `gh run watch` ends with exit 0. In the run, job `docker / image` shows these steps green: "Smoke test the image", "Reproducible build check", "Push to GHCR".

Then check the published image:

```bash
nix develop -c nix shell nixpkgs#crane -c crane manifest ghcr.io/kquote03/klean-backend:latest | nix develop -c nix shell nixpkgs#jq -c jq -r '.manifests[].platform | "\(.os)/\(.architecture)"'
docker pull ghcr.io/kquote03/klean-backend:latest && scripts/image-smoke.sh <(docker save ghcr.io/kquote03/klean-backend:latest) 18091
```

Expected:
- `linux/amd64` and `linux/arm64`.
- `image smoke OK`.

The first anonymous pull fails until the package is public (see Risks 1). Use `gh auth token | docker login ghcr.io -u kquote03 --password-stdin` for the check.

Last, retag the latest milestone once so it gets an image: push the `m26` tag again with `git push origin :refs/tags/m26 && git push origin m26`. That deletes and re-pushes the tag only; no history changes. Expected: the CI run for tag `m26` pushes `ghcr.io/kquote03/klean-backend:m26`. **Optional — ask the user first, because it rewrites a tag on the remote.**

### B8. Record it (~3 min)

1. In `TASKS.md`, add a section `## Release image (GHCR)` with ticked lines:
   - B1–B7, with the CI run URL;
   - the image digest from the step summary;
   - the platforms list.
2. In `docs/handover-2026-11-10.md`, under a new "Container image" heading, record:
   - the `klean.web` modes;
   - the platforms property;
   - the reproducibility check;
   - that `docker.yml` owns the backend tests now;
   - the package-visibility step.
3. Commit `docs: record the GHCR image pipeline`. Push.
4. Ask the user for "approve AGENTS.md" to fold this into AGENTS.md's CI section.

---

## Tests / validation summary

| What | Command | Expected |
|---|---|---|
| Image has the API and the web app | `scripts/image-smoke.sh backend/build/jib-image.tar` | `image smoke OK: …` |
| Reproducible | `GRADLE=… scripts/image-repro.sh` | `reproducible: sha256:…` |
| Release build refuses to skip the web app | `./gradlew :backend:help -Pklean.web=require` without pnpm | fails with "pnpm is not on PATH" |
| Workflows valid | `actionlint .github/workflows/*.yml` | no output |
| Android/backend unchanged | the gate | `BUILD SUCCESSFUL`, about 1032 tests, 0 failures |
| Published, multi-arch | `crane manifest ghcr.io/kquote03/klean-backend:latest` | amd64 + arm64 |
| CI | `gh run watch … --exit-status` | exit 0, docker job green |

## Risks, tradeoffs, open questions

1. **GHCR package visibility.** The first push creates the package as **private**, even for a public repo. The user must open https://github.com/users/kquote03/packages/container/klean-backend/settings once and set "Change visibility → Public". There is no API for this. The `org.opencontainers.image.source` label links the package to the repo. **Ask the user to do this after B7.**
2. **Jib instead of a Dockerfile.**
   - Pros: reproducible by design, no daemon, already in use.
   - Con: you can't `docker build .` from a fresh clone. Building from source needs JDK 21, the Android SDK (Gradle configures every module) and pnpm, all provided by `nix develop .#web`.
   - If the user wants a plain `Dockerfile` for self-builders, a multi-stage one would need a backend-only Gradle settings file. That is a separate, larger task. **Ask whether a Dockerfile is wanted in addition.**
3. **Image name.** It stays `klean-backend`, as in the governing plan, even though it also serves the web app. Renaming to `ghcr.io/kquote03/klean` later breaks anyone who pinned the old name. **Default kept; confirm.**
4. **`latest` follows `main`** because there are no releases yet. Deployments that want stability pin `sha-…` or a milestone tag.
5. **arm64 is a cross-build.** Jib builds arm64 without running anything, so it's safe. The arm64 image itself is only proven by the manifest listing; it isn't smoke-tested on CI.
6. **CI time.** The docker job builds the image three times: a tarball, a second tarball for the repro check, then the push. That adds about 5 min, but it runs in parallel with the Android gate, so wall time barely changes.
7. **Base image updates.** Pinning by digest means security fixes don't flow in automatically. Mitigation: bump with `crane digest` monthly, or add a scheduled workflow later (YAGNI for now).
8. **Merge friction with the milestone agent.** The lane is told to stay out of `backend/build.gradle.kts`, the workflows, `deploy/` and `docs/deploy.md`. The parent merges `origin/main` changes into `next` only by asking the agent, via its `git merge origin/main` at each milestone start.
9. **Usage limits.** The single background agent can be cut off. It commits per task and keeps TASKS.md ticked, so the parent can re-dispatch it with the same brief: "resume from TASKS.md and `git log main..next`".
10. **Telegram fixes compete with the lane for Waydroid and the Gradle lock.** Both are serialised by `flock`. The lane's 20-minute cap on Waydroid holds keeps parent checks from waiting long. The M25 agent once held the lock for 90 minutes.
````
