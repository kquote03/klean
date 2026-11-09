# Klean — build the remaining life modules in parallel

## Goal

Build the non-diet life modules: Tasks, Reminders, Calendar, Contacts with Birthdays, Workout and Sleep. Each must be a local-first, daily-usable, switch-off-able module that plugs into the registry, Today, universal capture, settings, backup and quiet hours. Parallel agents build them, each in its own git worktree, after one shared foundation step on `main`.

## Current context and assumptions

- **Repo:** `/home/kenan1099/Src/klean`, branch `main`.
  - Tags `m0`–`m15` exist. The Today dashboard (summary tiles + week overview, `core/ui/.../module/Dashboard.kt`) is done.
  - Remote: public GitHub repo `kquote03/klean`. CI runs on every push, and every push to main refreshes the rolling `nightly` release.
- **A Part F close-out agent may still be running on `main`.** Phase 0 below must not start until it has finished; check `delegate_task(action='list')`. Also check:
  ```
  git -C /home/kenan1099/Src/klean status --short
  ```
  Expected: empty output.
- **Read these first:**
  - `AGENTS.md`. Its conventions and gotchas are accurate; its "Current state" is stale.
  - `PLAN.md`. §2 describes how future modules plug in, and the original brief's "Future modules" section lives in the conversation and in this plan's decisions.
  - `TASKS.md`.
  - The shared subagent brief, `/home/kenan1099/.hermes/cache/scratch/klean-subagent-brief.md`.
- **How a module plugs in today (all of it exists):**
  - `KleanModule` (`core/ui/src/main/kotlin/app/klean/core/ui/module/KleanModule.kt`) carries id, label, icon, navOrder, `dashboardSlot`, routes and `registerGraph`, plus `DashboardCard`, the onboarding hooks, `widgetReceiver` and `onEnabledChanged`.
  - `ModuleRegistry` filters modules by `ModuleGate` (`SettingsModuleGate`, synced key `core.modules.disabled`).
  - `DashboardSummaryProvider` and `DayActivityProvider` feed the Today tiles and the week overview.
  - `CaptureHandler` (`shared/src/commonMain/kotlin/app/klean/shared/capture/CaptureContracts.kt`) and `DefaultCaptureRouter` handle universal capture. With no classifier, the router falls back to diet.
  - `Reschedulable` (`core/notifications/.../Alarms.kt`) re-arms work after boot, an app update or a time change. `AlarmScheduler` handles exact and inexact alarms.
  - `QuietHoursService.isQuiet(at, exactUserAlarm)`: user alarms pass `exactUserAlarm = true` and are exempt.
  - `SyncStamper` + `db.transact {}` for every write. The entities carry `SyncColumns`, a UUIDv7 id and tombstones.
  - `KleanDatabase` is at version 1, with schema `core/data/schemas/app.klean.core.data.db.KleanDatabase/1.json`. Backup copies the whole DB file, and restoring an older file migrates it when Room opens it.
  - `ModuleId` already reserves `TASKS`, `REMINDERS`, `CALENDAR`, `CONTACTS`, `BIRTHDAYS`, `WORKOUT` and `SLEEP`. `DashboardSlot` already has `TASKS`, `REMINDERS`, `EVENTS`, `BIRTHDAYS`, `SLEEP` and `WORKOUT`.
  - The bottom bar (`app/src/main/kotlin/app/klean/nav/KleanAppUi.kt:50-70`) shows Today, every enabled module, and Settings. With 7 modules that would be 9 items, which is too many.
- **Device:** Waydroid only (`192.168.240.112:5555`, Android 13, x86_64). LineageOS ships Calendar and Contacts providers. There is no camera, no Play services and no GPS. The Pixel is reserved for the user's session.
- **Machine:** 14 GB RAM, so only one Gradle build may run at a time. Every Gradle call goes through `flock /home/kenan1099/.hermes/cache/scratch/gradle.lock …`. Agents can write code in parallel; their builds queue on the lock, and the single Gradle/Kotlin daemon is reused across worktrees.

### Scope decisions (the user asked for "the other modules"; these are reversible)

**Built** (local-first, works offline, no account):

| Module | Built now | Deferred (needs cloud, Play services or hardware) |
|---|---|---|
| Tasks (`:feature:tasks`) | Lists (Work, Personal, user-created), tasks with due date and optional time, priority, recurrence, notes; "done" is separate from reminders; a once-a-day neutral "Still want these?" review of past-due tasks; capture handler | Google Tasks overlay (OAuth); Hermes agent delegation |
| Reminders (`:feature:reminders`) | A ping at a time or a full alarm (full-screen, rings until handled, snooze 10 min / 1 h / tomorrow); optionally attached to a task; recurrence; a "This is my main phone" flag gates ringing; exempt from quiet hours; capture handler ("remind me at 5 to call Ahmed") | Cross-device clearing (needs sync) |
| Calendar (`:feature:calendar`) | Read-only agenda and month view of the device's calendars, plus Klean's own events (Room is the source of truth, mirrored into a local "Klean" calendar under Klean's own Android account); capture handler ("gym tomorrow 6pm") | Google Calendar pull and CalDAV (backend); using a DAVx5 account instead of Klean's |
| Contacts + Birthdays (`:feature:contacts`, two `KleanModule`s: CONTACTS and BIRTHDAYS) | Klean contacts (Room, mirrored to the system Contacts provider under the Klean account), VCF import/export, birthdays on the contact, birthday reminders on the day and N days before (default 0 and 3, per contact), birthdays shown on the calendar; capture handler with **rules only, never AI** ("Sara's birthday is March 3") | Reading and editing other DAVx5 accounts' contacts |
| Workout (`:feature:workout`) | Templates ("3×15 pushups, 3×20 squats, 1 min plank"); logging sessions with strength sets (reps, optional weight), timed and cardio items; an optional gentle suggestion after N inactive days; capture handler ("did 30 pushups") | Health Connect read/write, GPS jog, Wear OS |
| Sleep (`:feature:sleep`) | Manual "Going to bed" / "I'm up" taps (Today tile action + notification action), sleep log, a "last night" tile, a gentle bedtime reminder at the start of the sleep window (off by default) | Health Connect / Wear OS import |

**Cross-cutting:** a rule-based, offline capture classifier (decision 6) so routing works with no AI. The bottom bar overflows into "More". New modules are off until the user turns them on.

**Out:** sync backend, web client, Wear OS, Health Connect, Google Assistant, Hermes agent.

### Decisions

1. **Phase 0 freezes the Room schema at version 2, with every entity for all six modules,** using Room `AutoMigration(1 → 2)` (it only adds tables).
   - Streams may NOT change the schema.
   - If a stream truly needs a column, it records that in its report. The parent adds one `AutoMigration(2 → 3)` after merging.
   - This is the price of running in parallel.
2. **Module settings keys live in each feature module** (`object TasksKeys { val … = SettingKey(…) }`), not in `SyncedKeys`. This avoids merge conflicts in `SettingsRepository.kt`.
3. **New modules are OFF by default on every install,** existing and fresh.
   - A synced set `core.modules.known` records which module ids the user has already been offered. At startup, any registered id missing from it is added to `core.modules.disabled` and to `known`. Diet is exempt.
   - The user turns modules on in Settings → Modules or in the wizard's Modules step, which already lists `registry.all`.
4. **Bottom bar:** Today, then up to 3 enabled modules by `navOrder`, then "More" if there are more than 3 enabled modules, otherwise Settings.
   - "More" is a screen listing the remaining enabled modules and then Settings.
   - Nav orders: Diet 10, Tasks 20, Reminders 30, Calendar 40, Contacts 50, Birthdays 55, Workout 60, Sleep 70.
5. **Klean's Android account** (`app.klean.account`, type `app.klean`, one account named "Klean") is built in Phase 0 in `:core:data/account`. It has a stub `AbstractAccountAuthenticator` and stub `AbstractThreadedSyncAdapter`s for `com.android.calendar` and `com.android.contacts`, so Calendar and Contacts can own provider rows. The debug build uses type `app.klean.debug`, from `BuildConfig.APPLICATION_ID`.
6. **Capture routing without AI:** a `RuleCaptureClassifier` in `:shared` uses keyword and time-expression rules. It runs first; the AI classifier runs only if the rules return nothing. Birthday phrases always resolve by rules and never reach AI.
7. **Natural time parsing** (`NaturalTime` in `:shared`) is built in Phase 0, because Tasks, Reminders, Calendar, Contacts and capture all depend on it.
8. **Copy rules apply to every module:** calm, no `!`, no guilt words (missed/streak/behind/forgot/overdue), never mention ADHD or autism. Past-due tasks say "Still want these?", not "overdue".

## Architecture

Phase 0 runs on `main`, done by one agent. It lays the shared foundation that every module would otherwise fight over:
- schema v2
- Gradle module skeletons and app wiring
- the nav overflow
- seeding new modules as off
- the Klean account
- `NaturalTime` and `RuleCaptureClassifier`
- per-stream TASKS.md sections

Then six module streams run in parallel, one worktree and branch each. Each touches only its own `feature/<name>` module and its own TASKS.md section, plus at most an `@IntoSet` binding. They share the build machine through a lock file and Waydroid through a second lock file. The parent merges the branches one at a time, gates and pushes after each merge, and tags `m17`.

## Shared rules for every agent (append these to each subagent's context)

- Every Gradle call:
  ```
  flock /home/kenan1099/.hermes/cache/scratch/gradle.lock nix develop -c ./gradlew <tasks> --console=plain -Pkotlin.compiler.execution.strategy=in-process
  ```
  The in-process Kotlin compiler avoids one extra Kotlin daemon per worktree.
- Every Waydroid session (install plus checks): run inside `flock /home/kenan1099/.hermes/cache/scratch/waydroid.lock bash -c '…'`. Hold the lock for as short a time as possible.
  - Install with `adb -s 192.168.240.112:5555 install -r -d app/build/outputs/apk/debug/app-debug.apk`. `-d` allows a lower versionCode from another branch.
  - Then grant notifications: `adb shell pm grant app.klean.debug android.permission.POST_NOTIFICATIONS`.
- Commit only your own paths (`git commit -- <paths>`), never `git add -A`. Stream agents push their **branch** (`git push -q origin <branch>`), never `main`.
- TDD for logic: write the failing test, run it to see it fail, implement, run it to see it pass, commit. Use Conventional Commits.
- No schema changes in streams (decision 1). No edits outside your stream's files list without recording why in the report.
- User-facing copy goes in the module's `src/main/res/values/strings.xml`. `ForbiddenCopyTest` scans every module's `res`.

---

## Phase 0 — Foundation (serial, one agent, on `main`)

Start only when `main` is clean and no other agent is running on it. Commit and push after every task.

### P0.1 Module skeletons and app wiring

Run this for each of `tasks reminders calendar contacts workout sleep`:

```
mkdir -p feature/<m>/src/main/kotlin/app/klean/feature/<m> feature/<m>/src/test/kotlin/app/klean/feature/<m> feature/<m>/src/test/resources feature/<m>/src/main/res/values
```

- `feature/<m>/build.gradle.kts`:
  ```kotlin
  plugins {
      id("klean.android.feature")
      id("klean.sqlite.jvmtest")
  }
  dependencies {
      implementation(project(":core:notifications"))
      implementation(project(":core:capture"))
      implementation(libs.core.ktx)
      testImplementation(libs.androidx.junit)
      testImplementation(libs.datastore.preferences)
      testImplementation(platform(libs.compose.bom))
      testImplementation(libs.compose.ui.test.junit4)
      debugImplementation(libs.compose.ui.test.manifest)
  }
  android { namespace = "app.klean.feature.<m>" }
  ```
  Check how `feature/diet/build.gradle.kts` sets its namespace. If the convention plugin derives it, drop the `android {}` line.
- `feature/<m>/src/main/AndroidManifest.xml`: `<manifest />`.
- `feature/<m>/src/test/resources/robolectric.properties`: `sdk=36`.
- `feature/<m>/src/main/res/values/strings.xml`: `<resources></resources>`.
- `feature/<m>/src/main/kotlin/app/klean/feature/<m>/Placeholder.kt`: `package app.klean.feature.<m>` plus `internal object Placeholder` (an empty Kotlin source keeps KSP and Hilt happy). Streams delete it.
- `settings.gradle.kts`: add `":feature:tasks", ":feature:reminders", ":feature:calendar", ":feature:contacts", ":feature:workout", ":feature:sleep"` to the `include(...)` list.
- `app/build.gradle.kts`: change `listOf("diet", "onboarding", "settings")` to `listOf("diet", "onboarding", "settings", "tasks", "reminders", "calendar", "contacts", "workout", "sleep")`.

`:feature:reminders` must be usable from `:feature:tasks` (to attach reminders) without depending on its UI. So:
- the reminder **repository and scheduler interface** go in `:core:data` (P0.2);
- `:feature:tasks` adds no dependency on `:feature:reminders`.

Verify:
```
git add feature settings.gradle.kts app/build.gradle.kts
flock … nix develop -c ./gradlew assembleDebug --console=plain
```
Expected: `BUILD SUCCESSFUL`.

Commit: `build: skeleton modules for tasks, reminders, calendar, contacts, workout, sleep`.

### P0.2 Schema v2: entities, DAOs, AutoMigration (test first)

Test: `core/data/src/test/kotlin/app/klean/core/data/db/MigrationV2Test.kt` (Robolectric). It copies the existing v1 fixture `core/data/src/test/resources/backup-v1.sqlite` into a temp file, then opens it with `Room.databaseBuilder(ctx, KleanDatabase::class.java, file.path).setDriver(BundledSQLiteDriver()).build()`. Assert:
- `foodEntryDao` still returns the fixture's rows. Read the fixture first: `nix develop -c sqlite3 core/data/src/test/resources/backup-v1.sqlite "select count(*) from food_entry"`.
- `PRAGMA user_version` is now 2.
- Each new DAO's `observeAll().first()` is empty.

Run `:core:data:testDebugUnitTest --tests '*MigrationV2Test'`. Expected: it fails to compile.

Create one file per entity in `core/data/src/main/kotlin/app/klean/core/data/db/entity/`. Every entity has `@PrimaryKey val id: String`, `@Embedded val sync: SyncColumns` and `Index("change_seq")`. Enums are stored by name as `String`. Times are UTC epoch ms; local dates are ISO `yyyy-MM-dd` strings.

```kotlin
@Entity(tableName = "task_list", indices = [Index("change_seq")])
data class TaskListEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** WORK, PERSONAL or CUSTOM. */
    val kind: String,
    val position: Int,
    @Embedded val sync: SyncColumns,
)

@Entity(tableName = "task", indices = [Index("change_seq"), Index("list_id"), Index("due_date")])
data class TaskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "list_id") val listId: String,
    val title: String,
    val notes: String? = null,
    /** Local date the task is due, ISO yyyy-MM-dd; null = no date. */
    @ColumnInfo(name = "due_date") val dueDate: String? = null,
    /** Minutes after local midnight; null = all day. */
    @ColumnInfo(name = "due_minutes") val dueMinutes: Int? = null,
    /** 0 none, 1 low, 2 medium, 3 high. */
    val priority: Int = 0,
    /** RRULE subset (see shared RecurrenceRule); null = once. */
    val recurrence: String? = null,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    val position: Int = 0,
    /** MANUAL or CAPTURE. */
    val source: String = "MANUAL",
    @Embedded val sync: SyncColumns,
)

@Entity(tableName = "reminder", indices = [Index("change_seq"), Index("task_id"), Index("fire_at")])
data class ReminderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String? = null,
    val title: String,
    @ColumnInfo(name = "fire_at") val fireAt: Long,
    /** IANA zone the time was set in, so "5 pm" stays 5 pm after travel. */
    val zone: String,
    @ColumnInfo(name = "is_alarm") val isAlarm: Boolean = false,
    val recurrence: String? = null,
    /** SCHEDULED, RINGING, SNOOZED, DONE. Dismissing an alarm never completes the task. */
    val state: String = "SCHEDULED",
    @ColumnInfo(name = "snoozed_until") val snoozedUntil: Long? = null,
    @ColumnInfo(name = "last_fired_at") val lastFiredAt: Long? = null,
    @Embedded val sync: SyncColumns,
)

@Entity(tableName = "event", indices = [Index("change_seq"), Index("start_at")])
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "start_at") val startAt: Long,
    @ColumnInfo(name = "end_at") val endAt: Long,
    @ColumnInfo(name = "all_day") val allDay: Boolean = false,
    val zone: String,
    val location: String? = null,
    val notes: String? = null,
    val recurrence: String? = null,
    @ColumnInfo(name = "reminder_minutes") val reminderMinutes: Int? = null,
    /** _ID of the mirrored row in CalendarContract.Events, device-local. */
    @ColumnInfo(name = "provider_event_id") val providerEventId: Long? = null,
    @Embedded val sync: SyncColumns,
)

@Entity(tableName = "contact", indices = [Index("change_seq"), Index("birthday_month", "birthday_day")])
data class ContactEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    /** JSON array of {"label":"mobile","value":"+971…"}. */
    val phones: String = "[]",
    /** JSON array of {"label":"home","value":"a@b.c"}. */
    val emails: String = "[]",
    val notes: String? = null,
    @ColumnInfo(name = "birthday_month") val birthdayMonth: Int? = null,
    @ColumnInfo(name = "birthday_day") val birthdayDay: Int? = null,
    @ColumnInfo(name = "birthday_year") val birthdayYear: Int? = null,
    /** Comma-separated days before the birthday to remind, e.g. "0,3". */
    @ColumnInfo(name = "birthday_reminders") val birthdayReminders: String = "0,3",
    /** RawContacts._ID of the mirror, device-local. */
    @ColumnInfo(name = "provider_raw_id") val providerRawId: Long? = null,
    @Embedded val sync: SyncColumns,
)

@Entity(tableName = "workout_template", indices = [Index("change_seq")])
data class WorkoutTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** JSON array of shared WorkoutItem. */
    val items: String,
    val position: Int = 0,
    @Embedded val sync: SyncColumns,
)

@Entity(tableName = "workout_session", indices = [Index("change_seq"), Index("started_at")])
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "template_id") val templateId: String? = null,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    val notes: String? = null,
    /** MANUAL, CAPTURE, HEALTH_CONNECT. */
    val source: String = "MANUAL",
    @Embedded val sync: SyncColumns,
)

@Entity(tableName = "workout_set", indices = [Index("change_seq"), Index("session_id")])
data class WorkoutSetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val exercise: String,
    /** STRENGTH, TIMED, CARDIO. */
    val kind: String,
    val position: Int,
    val reps: Int? = null,
    @ColumnInfo(name = "weight_kg") val weightKg: Double? = null,
    @ColumnInfo(name = "duration_s") val durationS: Int? = null,
    @ColumnInfo(name = "distance_m") val distanceM: Int? = null,
    @Embedded val sync: SyncColumns,
)

@Entity(tableName = "sleep_session", indices = [Index("change_seq"), Index("bed_at")])
data class SleepSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "bed_at") val bedAt: Long,
    @ColumnInfo(name = "wake_at") val wakeAt: Long? = null,
    /** MANUAL, HEALTH_CONNECT. */
    val source: String = "MANUAL",
    val notes: String? = null,
    @Embedded val sync: SyncColumns,
)
```

Add the needed imports (`androidx.room.*`), copying them from `GeofenceEntity.kt`.

DAOs go in `core/data/src/main/kotlin/app/klean/core/data/db/dao/`, one per entity. Each DAO has at least:
- `@Upsert suspend fun upsert(x)`
- `@Query("SELECT * FROM <t> WHERE id = :id") suspend fun get(id: String)`
- `@Query("SELECT * FROM <t> WHERE deleted = 0 …order…") fun observeAll(): Flow<List<…>>`

Plus these specific queries:
- `TaskDao`:
  - `observeList(listId)`, ordered by `completed_at IS NOT NULL, due_date IS NULL, due_date, due_minutes, position`;
  - `observeDue(fromIso, toIso)`;
  - `pastDue(todayIso)`, which returns tasks `WHERE deleted=0 AND completed_at IS NULL AND due_date < :todayIso`.
- `ReminderDao`:
  - `upcoming(): List` (state IN SCHEDULED/SNOOZED);
  - `observeUpcoming(from)`;
  - `forTask(taskId)`.
- `EventDao`: `observeRange(from, to)`, `WHERE start_at < :to AND end_at > :from`.
- `ContactDao`:
  - `withBirthdays()`;
  - `observeSearch(q)`, `WHERE display_name LIKE '%' || :q || '%'`.
- `WorkoutSessionDao`:
  - `observeRange(from, to)`;
  - `lastSessionStart(): Long?`.
- `WorkoutSetDao`: `forSession(id)`, `observeForSession(id)`.
- `SleepDao`:
  - `observeRange(from, to)`;
  - `open(): SleepSessionEntity?`, `WHERE wake_at IS NULL AND deleted=0 ORDER BY bed_at DESC LIMIT 1`.

Then update `KleanDatabase.kt`:
- add all 9 entities to `entities = [...]`;
- add `autoMigrations = [AutoMigration(from = 1, to = 2)]`;
- set `VERSION = 2`;
- add an abstract DAO accessor per DAO.

Add matching `@Provides` lines in `DatabaseModule.kt` and `app/src/androidTest/kotlin/app/klean/TestDatabaseModule.kt`.

Run the migration test. Expected: it passes. Then check:
```
ls core/data/schemas/app.klean.core.data.db.KleanDatabase/
```
Expected: `1.json 2.json`. Commit `2.json`.

`BackupManager` rejects backups newer than `KleanDatabase.VERSION` and accepts older ones. Run the backup tests (`:core:data:testDebugUnitTest --tests '*Backup*'`) and confirm that restoring the v1 fixture now migrates. Expected: pass. If a test hard-codes version 1, change its expectation to `KleanDatabase.VERSION`.

Commit: `feat(data): schema v2 — tables for tasks, reminders, events, contacts, workouts, sleep (AutoMigration 1→2)`.

### P0.3 Reminder contract in `:core:data` (so Tasks can attach reminders)

`core/data/src/main/kotlin/app/klean/core/data/reminder/ReminderRepository.kt`:
- an `interface ReminderRepository` with:
  - `suspend fun add(title: String, fireAt: Long, zone: String, isAlarm: Boolean, taskId: String? = null, recurrence: String? = null): String`
  - `suspend fun update(r: ReminderEntity)`
  - `suspend fun delete(id: String)` (tombstone)
  - `fun observeForTask(taskId: String): Flow<List<ReminderEntity>>`
  - `fun observeUpcoming(fromMs: Long): Flow<List<ReminderEntity>>`
- `DefaultReminderRepository` (SyncStamper in `db.transact`, UUIDv7 from `:shared`).
- `fun interface ReminderChangeListener { suspend fun onChanged(id: String) }`, multibound with `@Multibinds`, called after every write.

Reminders' scheduler (built in Stream 2) is one such listener. Tasks calls the repository and never touches alarms.

Test `DefaultReminderRepositoryTest` (Robolectric + in-memory DB). Copy the setup from `core/data/src/test/.../place/PlaceRepositoryTest.kt`, or whichever test `PlaceRepository` has. Cover:
- `add` stamps the sync columns and fires the listener;
- `delete` tombstones;
- `observeForTask` filters.

Commit.

### P0.4 New modules start off (test first)

In `core/data/src/main/kotlin/app/klean/core/data/module/`, add to `SettingsModuleGate.kt` (or a new `ModuleSeeding.kt`):

```kotlin
/** Ids the user has been offered at least once. Synced so every device agrees. */
val KnownModules = SettingKey("core.modules.known", emptySet<String>(), SetSerializer(String.serializer()))

/**
 * Modules added after an install start switched off, so an update never fills the app with things
 * the user didn't ask for. Diet is the original module and is never seeded off.
 */
fun seedNewModules(registered: Set<String>, known: Set<String>, disabled: Set<String>, alwaysOn: Set<String> = setOf("diet", "core")):
    Pair<Set<String>, Set<String>> {
    val fresh = registered - known
    return (known + fresh) to (disabled + (fresh - alwaysOn))
}
```

- Use the same serializer setup as the existing `core.modules.disabled` key.
- Test the pure function in `ModuleSeedingTest`:
  - first run with {diet, tasks} → tasks is disabled;
  - running again with nothing new changes nothing;
  - a module the user re-enabled stays enabled on the next run.
- Wire it into `ModuleRegistry`'s construction path. Add a `suspend fun seed()` to `ModuleRegistry` (in `core/ui/.../KleanModule.kt`) that reads both keys, applies `seedNewModules(all.map { it.id.value }.toSet(), …)` and writes both keys with `SettingsRepository.update`.
- Call it at the very start of `KleanApp.onCreate`'s startup block, inside the existing `runCatching`, before `dietStatus.refresh()`.

Commit: `feat(core): modules added in an update start switched off`.

### P0.5 Bottom-bar overflow (test first)

`app/src/main/kotlin/app/klean/nav/NavLayout.kt`:

```kotlin
package app.klean.nav

/** Bottom bar: Today + up to [max] modules + (More | Settings). Keeps the bar at 5 items or fewer. */
data class NavLayout<T>(val primary: List<T>, val overflow: List<T>)

fun <T> navLayout(enabled: List<T>, max: Int = 3): NavLayout<T> =
    if (enabled.size <= max) NavLayout(enabled, emptyList()) else NavLayout(enabled.take(max), enabled.drop(max))
```

Test `NavLayoutTest`: sizes 0, 3, 4 and 7.

In `KleanAppUi.kt`:
- iterate `navLayout(enabled).primary` for the module items;
- when `overflow` is non-empty, replace the Settings item with "More" (`Icons.Rounded.MoreHoriz`, string `nav_more` = "More") routing to `MoreRoute`;
- `MoreRoute` is a new `@Serializable object` in the nav routes file. Its screen `app/src/main/kotlin/app/klean/nav/MoreScreen.kt` is a `LazyColumn` of `ListItem`s, one per overflow module (icon + label, opens `startRoute`), followed by Settings;
- keep Settings and the overflow modules' graphs selected-highlighting "More".

Add a ShellSmokeTest case: with 5 fake modules enabled, "More" is shown and lists the 4th and 5th modules.

Commit: `feat(app): bottom bar keeps five items; the rest live under More`.

### P0.6 Klean account (Calendar/Contacts provider owner)

In `core/data/src/main/kotlin/app/klean/core/data/account/`:
- `KleanAccount.kt`:
  - `fun accountType(ctx) = ctx.packageName` (`app.klean` / `app.klean.debug`);
  - `suspend fun ensure(ctx): Account`, which calls `AccountManager.addAccountExplicitly(Account("Klean", type), null, null)` if missing and then `ContentResolver.setIsSyncable(account, "com.android.calendar"/"com.android.contacts", 1)`.
- `KleanAuthenticatorService.kt`: a `Service` whose binder is a stub `AbstractAccountAuthenticator`. Every method throws `UnsupportedOperationException`, except `addAccount`, which returns a null bundle.
- `KleanSyncService.kt`: a `Service` returning a no-op `AbstractThreadedSyncAdapter.syncAdapterBinder`.
- Resources in `core/data/src/main/res/xml/`:
  - `authenticator.xml` with `android:accountType="@string/klean_account_type"` and label `@string/klean_account_label` = "Klean";
  - `sync_calendar.xml` and `sync_contacts.xml` with `contentAuthority`, `accountType`, `userVisible="false"` and `supportsUploading="false"`;
  - `contacts.xml`, the `ContactsAccountType` metadata.
- `klean_account_type`: build-variant strings. Put `<string name="klean_account_type">app.klean</string>` in `core/data/src/main/res/values/strings.xml`. In `app/src/debug/res/values/strings.xml`, override `klean_account_type` with `app.klean.debug`. Library resources are merged with app overrides winning; verify this in the merged manifest.
- Manifest entries in `core/data/src/main/AndroidManifest.xml`:
  - the two services, with the intent filters `android.accounts.AccountAuthenticator` and `android.content.SyncAdapter` and their `meta-data`;
  - the permissions `GET_ACCOUNTS`, `READ_SYNC_SETTINGS`, `WRITE_SYNC_SETTINGS`, `AUTHENTICATE_ACCOUNTS` (maxSdkVersion 22), `READ_CALENDAR`, `WRITE_CALENDAR`, `READ_CONTACTS`, `WRITE_CONTACTS`. The runtime ones are requested just in time by the streams.

Verify on Waydroid. `ensure()` runs from a debug-only broadcast, `app.klean.debug.ENSURE_ACCOUNT`, added to `app/src/debug`:
```
adb shell am broadcast -a app.klean.debug.ENSURE_ACCOUNT -p app.klean.debug
adb shell dumpsys account | grep -A2 "app.klean.debug"
```
Expected: `Account {name=Klean, type=app.klean.debug}`.

Commit: `feat(data): Klean Android account with stub authenticator and sync adapters`.

### P0.7 `NaturalTime` parser in `:shared` (test first)

`shared/src/commonMain/kotlin/app/klean/shared/time/NaturalTime.kt`:

```kotlin
/** What a short phrase says about time. All fields optional; times are local. */
data class ParsedTime(val date: LocalDate?, val minutes: Int?, val rest: String)

object NaturalTime {
    /**
     * Understands, case-insensitively: "today", "tonight" (20:00), "tomorrow", weekday names ("friday" = next
     * occurrence, today if later today has a time), "in 2 hours", "in 30 min", "at 5" (17:00 if before 07:00
     * would be odd → treat 1–6 as pm), "at 5pm", "5:30", "17:30", "6pm", "March 3", "3 March", "3/3" (day/month,
     * UAE order), "next week" (+7 days). Returns the text with the time words removed in [rest].
     */
    fun parse(text: String, now: LocalDateTime): ParsedTime
}
```

Tests (`shared/src/commonTest/kotlin/app/klean/shared/time/NaturalTimeTest.kt`), with `now = 2026-11-09T10:00` (a Monday):

| input | date | minutes | rest |
|---|---|---|---|
| `remind me at 5 to call Ahmed` | 2026-11-09 | 1020 | `remind me to call Ahmed` |
| `gym tomorrow 6pm` | 2026-11-10 | 1080 | `gym` |
| `Sara's birthday is March 3` | 2027-03-03 | null | `Sara's birthday is` |
| `dentist friday 9:30` | 2026-11-13 | 570 | `dentist` |
| `in 2 hours stretch` | 2026-11-09 | 720 | `stretch` |
| `pay rent` | null | null | `pay rent` |
| `call mum at 9` | 2026-11-09 | 1260 | `call mum` |
| `meeting 3/12 14:00` | 2026-12-03 | 840 | `meeting` |

"at 9" at 10:00 means 21:00 today: the next occurrence that is still in the future. A date without a year that has already passed this year rolls to next year. Remove dangling "at"/"on" from `rest` and collapse whitespace.

Run `:shared:jvmTest --tests '*NaturalTimeTest'`: red, then green. Commit.

### P0.8 `RuleCaptureClassifier` (test first)

`shared/src/commonMain/kotlin/app/klean/shared/capture/RuleCaptureClassifier.kt`. It implements `CaptureClassifier`, returning confidence 0.9 on a rule hit, or `null` when no rule matches. Rules are applied in order, and each one returns only if that module is among the `options`:
1. `birthday|bday` → (CONTACTS or BIRTHDAYS, `add_birthday`)
2. `^remind me|^remind|alarm|wake me` → (REMINDERS, `add_reminder`)
3. `slept|went to bed|woke up|going to bed|bedtime` → (SLEEP, `log_sleep`)
4. `gym|workout|pushups?|squats?|plank|ran \d|run \d|km run|lifted` → (WORKOUT, `log_workout`). If the text also contains a future time ("gym tomorrow 6pm"), instead return (CALENDAR, `add_event`) at 0.8, plus (WORKOUT, …) at 0.5.
5. A time or date with no food words: if the phrase has a time → (CALENDAR, `add_event`) 0.7; date only → (TASKS, `add_task`) 0.7.
6. `^todo|^to do|need to|have to|buy |call |email |pay ` → (TASKS, `add_task`) 0.8.
7. Otherwise `null`, so the router falls back to diet.

A new `CompositeCaptureClassifier(rules, ai)` returns the rules result if non-null; otherwise it returns `ai?.classify`, **except** that when the text matches the birthday rule it never calls the AI.

- Bind it in `core/ai/.../AiCaptureClassifier.kt`'s Hilt module (where `DefaultCaptureRouter(classifier)` is built) as `DefaultCaptureRouter(CompositeCaptureClassifier(RuleCaptureClassifier(clock, zone), aiClassifier))`. The rules need "now" for `NaturalTime`, so construct them with a `() -> LocalDateTime`.
- Tests:
  - each example input from the brief with all modules present → expected first candidate;
  - with only DIET present → `null`;
  - the birthday text never reaches a fake AI (count its calls).

Commit: `feat(capture): offline rule classifier for universal capture; birthdays never reach AI`.

### P0.9 Per-stream TASKS.md sections, and freezing the shared files

Append to `TASKS.md` a `## P2 — Life modules` section with one subsection per stream (S1–S6) listing that stream's task ids from this plan as `- [ ]`. Commit, then run the gate. Expected: `BUILD SUCCESSFUL`, `0 fail`.

Push and wait for CI (`gh run list -R kquote03/klean --limit 1` shows `completed success`). Then tag `p2-foundation` and push the tag.

---

## Phase 1 — Six parallel streams

The parent creates the worktrees (from repo root, after `p2-foundation`):

```
for s in tasks reminders calendar contacts workout sleep; do
  git worktree add ../klean-$s -b p2-$s p2-foundation
done
```

Each stream agent works **only** in `/home/kenan1099/Src/klean-<s>`. They all follow the same pattern.

### Common checklist for every stream (S1–S6)

1. Delete `Placeholder.kt`.
2. **Repository** in `feature/<m>/.../data/<X>Repository.kt`:
   - an interface plus a `Default…` implementation using SyncStamper in `db.transact`, UUIDv7, tombstones;
   - a Robolectric in-memory-DB test (copy the setup from `feature/diet/src/test/.../DietEnablementTest.kt` helpers: `inMemoryDb()`, `FakeClock`, `Dubai`).
3. **`KleanModule` implementation** `<X>KleanModule`, bound with `@IntoSet`:
   - id, label, icon from `material-icons-extended` `Icons.Rounded.*`, navOrder (decision 4), dashboardSlot, routes (`@Serializable`), `registerGraph`, `settingsRoute`, `onEnabledChanged` (cancel this module's alarms/notifications when off, re-arm when on);
   - `onboardingTitleRes` + `OnboardingContent` only if the module has a setting worth asking about.
4. **Screens** (Compose, Material 3, `KleanCard`, `EntryRow`, `NeutralEmpty`, `CaptureField` from `:core:design`). Each has a ViewModel with a fake-repository unit test (Turbine). Clear each VM's `ViewModelStore` in `@After` before closing the DB or DataStore; this is the known CI flake (see AGENTS gotchas).
5. **Today:** a `DashboardCard`, ≤5 rows, tap → module, never a red badge. A `DashboardSummaryProvider` with 1–2 tiles. A `DayActivityProvider` (counts per local day, `dayRoute`).
6. **`CaptureHandler`** with `actionSpecs` matching P0.8's action names. `execute` persists immediately with no AI; `undo` tombstones.
7. **Settings screen** for the module, reachable from Settings → module row.
8. **Notifications:** create channels via the existing `ChannelRegistrar` pattern, adding the channel ids to `core/notifications/.../Channels.kt` **only in your own section with a comment**. If you can, avoid touching Channels.kt by registering channels from the module's own `Initializer`/startup listener. Respect quiet hours unless it's a user alarm. Make every scheduled thing `Reschedulable` (`@IntoSet`).
9. **Tests:**
   - unit tests for every piece of logic;
   - ViewModel tests;
   - one Compose/Robolectric screen test;
   - extend `app/src/androidTest/.../ModuleDisableTest.kt` with a test method for your module **in a new file** `app/src/androidTest/kotlin/app/klean/<M>DisableTest.kt`, to avoid merge conflicts.
10. **Waydroid**, under `waydroid.lock`:
    - install;
    - turn the module on in Settings → Modules;
    - walk the main flows with `~/.hermes/cache/scratch/bin/ui.py`;
    - screenshot with `~/.hermes/cache/scratch/bin/wshot` into `~/.hermes/cache/scratch/p2-<m>/`;
    - check `adb logcat -d -b crash` is empty;
    - check the module disappears from the bar, Today and notifications when turned off.
11. **Gate in your worktree**, which must pass:
    ```
    flock … ./gradlew assembleDebug :app:assembleDebugAndroidTest :shared:jvmTest testDebugUnitTest
    ```
12. Tick your TASKS.md subsection.
13. Push your branch: `git push -q origin p2-<m>`. Do NOT merge.
14. **Report:** commits, test counts, Waydroid evidence, any schema/Channel/shared-file changes you needed, deviations, AGENTS notes.

### S1 — Tasks (`feature/tasks`, ModuleId.TASKS, navOrder 20, slot TASKS)

- **S1.1 `RecurrenceRule`** in `shared/src/commonMain/kotlin/app/klean/shared/time/RecurrenceRule.kt`. **S1 owns this file; S2 and S3 reuse it after merge.** S2 may use a local copy only if it must (then note it); prefer storing the RRULE string and calling this after merge.
  - It parses and formats an RRULE subset: `FREQ=DAILY|WEEKLY|MONTHLY|YEARLY`, `INTERVAL`, `BYDAY` (weekly), `COUNT`, `UNTIL`.
  - `fun next(after: LocalDate): LocalDate?`.
  - Tests:
    - every weekday from Monday;
    - `FREQ=MONTHLY` on Jan 31 → Feb 28 (clamp);
    - an `INTERVAL=2` weekly;
    - `COUNT` exhaustion.
- **S1.2 Default lists:** on first enable, create "Personal" and "Work". `TasksKeys.seeded` is a synced flag.
- **S1.3 Screens:**
  - `TasksRoute`: list tabs (chips) + "New list". Each task is a row with a check circle, title, due label ("Today", "Tomorrow", "Fri 14:00") and a priority dot (neutral colours, no red).
    - Completing a task moves it to a collapsed "Done" section. Undo comes from a snackbar.
    - A recurring task, when completed, creates the next occurrence with `RecurrenceRule.next`.
  - `TaskEditRoute`: title, notes, list, due date/time pickers, priority segmented button, a repeat picker (None/Daily/Weekdays/Weekly/Monthly/Yearly), and a "Reminders" section with "Add reminder" (at due time, 1 h before, custom). That section calls `ReminderRepository.add(isAlarm = false)`, shows existing ones from `observeForTask`, and offers a toggle "Ring as an alarm".
- **S1.4 "Still want these?" review.** A daily inexact alarm at 10:00 local, moved out of quiet hours via `QuietHoursService.nextAllowed`, and `Reschedulable`:
  - if `pastDue(today)` is non-empty, post one low-importance notification "Still want these?", body "3 tasks from earlier days".
  - It opens `TaskReviewRoute`: each task has "Keep for today" (moves the due date to today), "Pick a day", "Done" and "Let it go" (tombstone).
  - No counts in red, no "overdue" wording anywhere.
  - Test the decision logic purely: `reviewNeeded(pastDue, lastShownDate, today)` shows at most once per day.
- **S1.5 Capture:** `add_task` → `NaturalTime.parse` gives the due date and time, `rest` becomes the title, and the task goes into the default list "Personal". Text with no date gets no due date.
- **S1.6 Today:**
  - Card: "Today" (due today or past-due-not-done, max 5) with check circles that complete inline.
  - Tile: "Tasks · 3 for today".
  - Activity: tasks due per day; `dayRoute` → `TasksRoute(filterDate)`.
- **Waydroid:**
  - Add three tasks: one by capture "pay rent friday", one recurring weekly, one due yesterday by editing.
  - Complete the recurring task → the next occurrence appears.
  - Trigger the review via a debug broadcast `app.klean.debug.TASK_REVIEW`, added in `feature/tasks/src/debug/AndroidManifest.xml` → the notification appears.

### S2 — Reminders (`feature/reminders`, ModuleId.REMINDERS, navOrder 30, slot REMINDERS)

- **S2.1 `ReminderScheduler`:** implements `ReminderChangeListener` (`@IntoSet`) and `Reschedulable`. It arms one exact alarm per upcoming reminder using `AlarmScheduler`. Exact alarms are required for "full alarm"; degrade gracefully when exact alarms aren't allowed by using inexact alarms and showing a calm hint in reminder settings.
  - Use `setAlarmClock` for `isAlarm` reminders. It is exempt from Doze and shows in the status bar alarm icon.
  - Use `setExactAndAllowWhileIdle` for pings.
  - A pure test for `nextFireTime(reminder, now)` covers recurrence (via `RecurrenceRule` once S1 is merged; until then, handle `FREQ=DAILY|WEEKLY` locally in a private function and leave `// TODO(after S1 merge)`) and snooze.
- **S2.2 Ping:** a notification on channel `reminders` (importance HIGH) with actions Done, Snooze 10 min, Snooze 1 h, Tomorrow.
  - Done marks the reminder `DONE` and, if a `taskId` is set, does **not** complete the task. It offers a second action, "Mark task done", which does.
  - Not quiet-hours gated: user alarms are exempt, and pings are user-set times too. Note this in the report.
- **S2.3 Full alarm:** `AlarmActivity` (showWhenLocked, turnScreenOn) is launched through a full-screen-intent notification on channel `alarms`. It plays the default alarm ringtone looped (`RingtoneManager.TYPE_ALARM`, `AudioAttributes.USAGE_ALARM`) with vibration until handled, and has big buttons: Dismiss, Snooze 10 min, Snooze 1 h, Tomorrow. Dismiss ≠ done.
  - Ring only if `DevicePrefs.isMainPhone`, a new device-local pref. Default true. Add it in your stream's own new file `feature/reminders/.../MainPhonePrefs.kt` using its own DataStore name `reminders_device`, not DevicePrefs, to avoid conflicts.
  - Otherwise it shows a silent ping.
  - Auto-stop after 10 minutes of ringing and leave a ping.
  - Declare `USE_FULL_SCREEN_INTENT`, `SCHEDULE_EXACT_ALARM` (already present) and `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` if you use a foreground service for the ringing. A ringing alarm is a legitimate foreground-service use; type `mediaPlayback`, or `systemExempted` if allowed — check the API 34+ rules and document the choice.
- **S2.4 Screens:**
  - `RemindersRoute`: upcoming reminders grouped by Today, Tomorrow and Later, plus "Earlier" (fired, not done) shown neutrally.
  - `ReminderEditRoute`: title, date/time, repeat, alarm toggle.
  - Settings: main phone switch, alarm sound picker (system ringtone picker intent), default snooze.
- **S2.5 Capture:** `add_reminder` uses `NaturalTime.parse`. With no time: tomorrow 09:00. Strip "remind me (to)" from the title.
- **S2.6 Today:** card "Coming up" (next 3), tile "Next reminder · 17:00". Activity: reminders per day.
- **Waydroid:**
  - Capture "remind me in 2 min to stretch" → after ~2 min the ping appears; Snooze 10 min reschedules (check `dumpsys alarm`).
  - An alarm reminder 1 minute ahead → the full-screen `AlarmActivity` appears with sound (`dumpsys audio | grep -i "USAGE_ALARM"` shows an active player) → Dismiss stops it, and the task linked to it is not completed.
  - Waydroid restart (`waydroid session stop/start`, with the start run as a persistent background process) → the alarm is re-armed.

### S3 — Calendar (`feature/calendar`, ModuleId.CALENDAR, navOrder 40, slot EVENTS)

- **S3.1 `DeviceCalendarSource`:** reads `CalendarContract.Instances` for a range (after just-in-time `READ_CALENDAR`), across all visible calendars, read-only, excluding the Klean calendar (those rows come from Room). Map rows to `AgendaItem(title, start, end, allDay, calendarName, color, source=DEVICE)`.
  - Test with a fake `ContentResolver`-free interface, `DeviceCalendarQuery`, behind the source.
- **S3.2 `KleanCalendarMirror`:** ensures one calendar under the Klean account (`CALLER_IS_SYNCADAPTER=true`, `ACCOUNT_TYPE=klean_account_type`), named "Klean", with a pastel lavender colour. It inserts, updates and deletes `Events` rows for Room events (stores `provider_event_id`). It runs after each `EventRepository` write and on full resync at startup (`Reschedulable`).
  - Tests: Robolectric with `ShadowContentResolver`, or an interface-level fake. Assert that the right `ContentValues` were produced.
- **S3.3 Screens:**
  - `CalendarRoute`: Agenda (default: today and the next 14 days, grouped by day, Klean and device items merged and sorted) and Month (grid with dots per day, same neutrality rules as diet history). Tapping a day goes to the day agenda.
  - `EventEditRoute`: title, all-day, start/end, location, notes, repeat, reminder (none, at start, 10 min, 1 h, 1 day). The reminder writes a `ReminderRepository` entry with `isAlarm=false` and the event title.
- **S3.4 Capture:** `add_event` → `NaturalTime`; the default duration is 1 h; date only → all-day.
- **S3.5 Birthdays on the calendar:** `BirthdayCalendarSource` lives in `:feature:contacts` (S4). To avoid a cross-stream dependency, Calendar consumes a **`DayItemsProvider`** multibinding (define the interface in your own module first; it becomes shared after merge). After merge, the parent moves it to `core/ui/module/Dashboard.kt` if S4 needs it; until then S4 does the same thing in its own module. The parent reconciles the two at merge (see Phase 2).
- **S3.6 Today:** card "Next up" (today's remaining items, max 4); tile "Next · 14:00 Dentist". Activity: events per day across both sources; `dayRoute` → `CalendarDayRoute(date)`.
- **Waydroid:**
  - Grant calendar permission in-app.
  - Capture "dentist friday 9:30".
  - The agenda shows it.
  - `adb shell content query --uri content://com.android.calendar/events --projection title:calendar_id` shows "dentist" in the Klean calendar.
  - Insert a device event through the Waydroid Calendar app if present; otherwise use `content insert` into a local calendar → it appears read-only in the agenda.

### S4 — Contacts + Birthdays (`feature/contacts`, two KleanModules: CONTACTS navOrder 50 / slot null, BIRTHDAYS navOrder 55 / slot BIRTHDAYS)

- **S4.1 `ContactRepository`:** phones and emails are JSON through kotlinx serialization (`@Serializable data class LabeledValue(val label: String, val value: String)`, defined in your module). Search by name. Birthday fields are validated (Feb 29 is allowed).
- **S4.2 `SystemContactsMirror`:** Klean contacts are written as RawContacts under the Klean account with `StructuredName`, `Phone`, `Email` and `Event(TYPE_BIRTHDAY, "--MM-DD" or "YYYY-MM-DD")`, so the dialer and other apps see them. Update and delete follow, and `provider_raw_id` is stored. Use a `ContactsProviderOps` interface that builds `ArrayList<ContentProviderOperation>`, and test the operations it produces.
- **S4.3 VCF:**
  - Import (SAF `OpenDocument`, `text/vcard`, `text/x-vcard`): a minimal vCard 3.0/4.0 parser in `feature/contacts/.../vcf/Vcf.kt` handling `FN`, `N`, `TEL;TYPE=`, `EMAIL;TYPE=`, `BDAY` (`YYYY-MM-DD`, `--MM-DD`, `YYYYMMDD`) and `NOTE`, with line folding and quoted-printable `ENCODING` ignored.
  - Export (SAF `CreateDocument`) writes 3.0.
  - Round-trip tests with three fixture cards under `src/test/resources/`.
- **S4.4 Birthday reminders:** `BirthdayScheduler` (`Reschedulable`, and also run after contact writes). For each contact with a birthday and each `n` in `birthday_reminders`, it arms an inexact alarm at 09:00 local on (birthday − n days), moved out of quiet hours.
  - The notification: "Sara's birthday is in 3 days" / "It's Sara's birthday today" (with the age if the year is known and `TasksKeys`-style setting `showAge` is on).
  - **No AI anywhere in this module.** Add a test that `ContactsCaptureHandler` has no AI dependency (constructor check) and document it in KDoc.
  - Pure tests: `nextBirthday(month, day, today)` covers Feb 29 in non-leap years → Feb 28, and reminders scheduled for the right dates.
- **S4.5 Screens:**
  - `ContactsRoute`: a search field and an A–Z list.
  - `ContactEditRoute`: name, phones, emails, birthday (month/day picker with an optional year), reminder days chips (On the day, 1, 3, 7 days before), notes.
  - `BirthdaysRoute`: upcoming birthdays in the next 60 days, then the rest by month.
  - Settings: import/export VCF, "Show age".
- **S4.6 Capture:** `add_birthday` ("Sara's birthday is March 3"). Parse the name: the text before `'s birthday`/`birthday`, trimmed. Find a contact by case-insensitive exact name, or create one. Set the birthday. Rules only.
- **S4.7 Calendar integration:** a `DayItemsProvider`-shaped contribution (see S3.5) lists birthdays per day, and a `DayActivityProvider` for the BIRTHDAYS module covers Today's week strip.
- **S4.8 Today:** Birthdays card "Coming up" (next 3 within 30 days); tile "Next birthday · Sara, Mar 3".
- **Waydroid:**
  - Capture "Sara's birthday is March 3" → contact created.
  - `content query --uri content://com.android.contacts/raw_contacts --projection display_name:account_type` shows Sara under `app.klean.debug`.
  - Import a fixture VCF pushed to `/sdcard/Download`.
  - Export to Downloads and `adb pull` it; diff the names.
  - Set a birthday to today+0 with reminder "On the day", then fire via a debug broadcast `app.klean.debug.BIRTHDAYS_NOW` → notification.

### S5 — Workout (`feature/workout`, ModuleId.WORKOUT, navOrder 60, slot WORKOUT)

- **S5.1 Shared models**, in your module (`feature/workout/.../model/WorkoutItem.kt`, `@Serializable`): `WorkoutItem(exercise, kind, sets, reps?, weightKg?, durationS?, distanceM?)`.
- **S5.2 Template parser.** `TemplateText.parse("3x15 pushups, 3×20 squats, 1 min plank, 5 km run")` returns 4 items: strength 3×15, strength 3×20, timed 60 s, cardio 5000 m. It accepts `x`/`×`, `min`/`s`/`sec`, `km`/`m`. `format` is the inverse. Table test of 8 inputs.
- **S5.3 Screens:**
  - `WorkoutRoute`: templates list ("Start" buttons) + recent sessions.
  - `TemplateEditRoute`: one text field with the parser, and a live preview of the parsed items.
  - `SessionRoute` (active session): the template's items with a big "+1 set" per item, editable reps/weight, a timer for timed items (foreground-visible chronometer, not a foreground service), and "Finish".
  - `SessionDetailRoute`.
  - No scores, no PR (personal-record) badges.
- **S5.4 Capture:** `log_workout` ("did 30 pushups", "ran 5k") → a finished session with the parsed items, through `TemplateText` with "did/ran" stripped.
- **S5.5 Gentle suggestion:** setting "Suggest a workout after a few quiet days" (off by default, N = 3). A daily inexact alarm at 18:00 outside quiet hours. If `lastSessionStart` is more than N days ago, post one low-importance notification, "Fancy a short workout?", with an action "Start <first template>". It is sent at most every N days. Pure test of the decision.
- **S5.6 Today:** card "Recent" (last 3 sessions, summary "3 items · 25 min"); tile "Last workout · Sat". Activity: sessions per day.
- **S5.7 Health Connect:** **not built.** Add a `HealthConnectBridge` interface with a no-op binding and a KDoc saying it is deferred.
- **Waydroid:**
  - Create a template from text.
  - Start a session, add sets, finish → it appears in Recent and in Today's week strip.
  - Capture "did 30 pushups" → a session is created.

### S6 — Sleep (`feature/sleep`, ModuleId.SLEEP, navOrder 70, slot SLEEP)

- **S6.1 `SleepRepository`:**
  - `goToBed(at)` opens a session; if one is already open, it closes that first if it's older than 20 h, otherwise it's a no-op.
  - `wakeUp(at)` closes the open one.
  - `observeRange`.
  - Pure `duration(session)`; `lastNight(today, quietHours)` = the latest session whose wake falls in the logical day.
- **S6.2 Surfaces (one-tap):**
  - A Today tile "Last night · 7 h 20 m". The tile's tap target toggles: "Going to bed" before wake, "I'm up" while a session is open. Use `SummaryItem.route` to a tiny `SleepToggleRoute` that performs and pops, or make the tile action a module-provided lambda if `SummaryItem` gains an action. Do NOT change `SummaryItem` (shared file); use the route approach.
  - An optional ongoing low-importance notification while a session is open, "Sleeping since 23:40", with an "I'm up" action. Off by default.
  - A capture handler, `log_sleep`: "slept at 11 woke at 7", "going to bed".
- **S6.3 Bedtime reminder:** setting off by default. At (sleep-window start − 30 min), a gentle notification "Winding down soon?" with an action "Going to bed". `Reschedulable`. It does not respect quiet hours, because it fires before them; document that.
- **S6.4 Screens:** `SleepRoute` (the last 14 nights as a neutral bar list of durations — no target lines, no "short" labels) and `SleepEditRoute` (edit bed/wake).
- **S6.5 Health Connect:** deferred. Add a no-op `SleepImportSource` interface.
- **Waydroid:**
  - Tap the Today tile → "Going to bed".
  - Tap again → "I'm up".
  - The session appears with its duration.
  - Capture "slept at 11 woke at 7" → a session from 23:00 to 07:00.
  - Fire the bedtime reminder via a debug broadcast → the notification appears.

---

## Phase 2 — Merge, reconcile, verify (parent agent)

1. Merge in this order, running the gate after **each** merge:
   ```
   git checkout main
   git merge --no-ff p2-tasks       # owns RecurrenceRule
   flock … ./gradlew assembleDebug :app:assembleDebugAndroidTest :shared:jvmTest testDebugUnitTest
   git merge --no-ff p2-reminders   # then replace its local recurrence helper with RecurrenceRule; delete the TODO
   git merge --no-ff p2-calendar
   git merge --no-ff p2-contacts    # reconcile DayItemsProvider: move the interface to core/ui/module/Dashboard.kt; S3 and S4 both import it
   git merge --no-ff p2-workout
   git merge --no-ff p2-sleep
   ```
   Expected conflicts:
   - TASKS.md: keep both sides.
   - `Channels.kt`: keep both sides.
   - `app/src/debug/AndroidManifest.xml`: keep both receivers.

   Each merge must end with the gate `BUILD SUCCESSFUL` before the next one starts. Push `main` after each merge.
2. **Reconcile the cross-stream TODOs:** reminders' recurrence → `RecurrenceRule`, plus the `DayItemsProvider` move. This needs a test-first commit.
3. **Waydroid end-to-end,** with all modules on:
   - The bottom bar shows Today, Diet, Tasks, Reminders and More. More lists Calendar, Contacts, Birthdays, Workout, Sleep and Settings.
   - Universal capture routes each brief example correctly, with no AI configured:
     - "ate 2 manakish" → diet
     - "gym tomorrow 6pm" → calendar event, with a correction chip offering workout
     - "Sara's birthday is March 3" → contacts
     - "remind me at 5 to call Ahmed" → reminder

     Check each in its module's list.
   - Today shows a card and tiles per enabled module, and the week strip sums activity across modules.
   - Turning each module off removes its bar item, card, tiles, notifications and alarms (`dumpsys alarm | grep -c app.klean.debug` drops).
   - Backup export → `pm clear` → restore brings back rows from every module.
   - `connectedDebugAndroidTest` passes, including the six new `*DisableTest`s.
4. Update TASKS.md and `docs/acceptance-p1.md` (add a P2 section with a Pixel column). Then either update AGENTS.md with user approval, or write `docs/handover-p2.md`.
5. Tag `m17` (P2 modules) and push it. Remove the worktrees: `git worktree remove ../klean-<s>` and `git branch -d p2-<s>`.

## Parallelism and resource plan

| Phase | Agents at once | Notes |
|---|---|---|
| 0 | 1 | Serial: schema, wiring and shared parsers |
| 1 | 6 (the limit is 10) | Builds serialize on `gradle.lock`; Waydroid sessions serialize on `waydroid.lock`. Writing code, thinking and running JVM tests take most of the time, so parallel coding still gains several-fold. |
| 2 | 1 | Merges and E2E |

If memory pressure appears (an OOM in the gate), drop to 3 concurrent streams: S1+S2, S3+S4, S5+S6 paired in sequence.

## Risks, tradeoffs, open questions

- **Scope change.** The original brief put these modules out of Prototype 1 and asked to live with diet for two weeks first. The user has now asked for them. The cloud and hardware pieces stay deferred (see the table). Ask the user whether Google Tasks, Health Connect or the Hermes agent should come next.
- **Schema freeze.** Parallel streams can't add columns. Any need goes through a parent-made `AutoMigration(2 → 3)` after merge, so the schema was designed generously up front.
- **System providers on Waydroid:**
  - The Calendar and Contacts providers exist on LineageOS, but sync-adapter-owned rows can behave differently on the Pixel. Writing as a sync adapter needs the account's type to match exactly (debug vs release).
  - Alarm audio on Waydroid may be silent; verify through `dumpsys audio` rather than ears.
- **Exact alarms.** `setAlarmClock` for full alarms is allowed for alarm-clock apps. Pings use `setExactAndAllowWhileIdle` when the permission is granted, otherwise an inexact fallback with a calm explanation in settings.
- **Foreground service for ringing.** The Android 14+ type rules need a careful choice. S2 documents it, and the Pixel session re-verifies it.
- **Universal-capture rules are English keyword heuristics.** The correction chip is the safety net, and the AI classifier refines the result when it is configured.
- **"No AI touches birthdays"** is enforced in the composite classifier and the contacts handler. A user's own AI provider still never sees birthday text.
- **Open (defaults chosen; tell me if wrong):**
  - new modules start off;
  - the bottom bar is Today + 3 + More;
  - pings ignore quiet hours like alarms do;
  - the task review runs at 10:00;
  - the workout suggestion is off with N = 3;
  - the bedtime reminder is off.
