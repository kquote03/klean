# Google Tasks: your own OAuth client

Klean can show your Google Tasks lists next to its own (M21). It reads every list, and for tasks it lets you tick them off, untick them, and change the title, notes and due date. It never creates or deletes anything in Google. A test (`NoTaskCreationTest`) checks that no `POST …/tasks` request is ever built.

Klean is self-hosted, so there is no shared Klean OAuth client. You create one in your own Google Cloud project. This takes about ten minutes, once.

## What you need

- A Google account and <https://console.cloud.google.com>.
- The SHA-1 fingerprint of the key that signs your Klean APK:
  - Nightly or debug APKs from this repo are signed with the CI debug key. Run `keytool -printcert -jarfile klean.apk | grep SHA1`.
  - A self-built APK uses your own keystore. Run `keytool -list -v -keystore <file>`.
- The package name: `app.klean` (release) or `app.klean.debug` (debug builds). Each one needs its own Android client.

## Steps

1. **Create a project.** In the console, open the project picker, choose New project, give it a name such as "Klean", and create it.
2. **Turn on the Tasks API.** Go to APIs & Services › Library, search for "Google Tasks API" and choose Enable.
3. **Set up the consent screen.** Go to APIs & Services › OAuth consent screen (in newer consoles: Google Auth Platform › Branding and Audience).
   - User type: **External**.
   - App name: "Klean". Support email: yours.
   - Scopes: add `https://www.googleapis.com/auth/tasks`, plus `openid`, `email` and `profile`. The last three are non-sensitive: they let Klean show which account is connected (its email, name and photo) in the account menu, as Google's own apps do.
   - Test users: add your own Google account, plus any family accounts that will use it.
4. **Create the Android client.** Go to Credentials › Create credentials › OAuth client ID › **Android**.
   - Package name: `app.klean`. SHA-1: from "What you need" above.
   - Repeat for `app.klean.debug` if you use debug builds.

   Klean's sign-in uses Play services' `AuthorizationClient`. Google matches the Android client by package name and SHA-1, so **no client ID goes into the app or its settings**.
5. **(Optional) Create a Web client.** Go to Create credentials › OAuth client ID › **Web application**. You only need it if you later want the Klean server to read Google Tasks on its own; refresh tokens for a server need one. Klean doesn't use it today: the phone talks to Google directly. The refresh-token slot is `SecretStore.GOOGLE_REFRESH` (`google.refresh`).
6. **Connect in Klean.** Tap the avatar at the top right of Today and choose Connect Google Tasks (or open Settings › Tasks › Google Tasks › Connect). Pick your account and allow "See, edit, create and delete all your tasks", and "See your personal info" / "See your primary Google Account email address". That wording is Google's name for the only Tasks scope; Klean still never creates or deletes tasks. Then pick which lists to show.

## Testing mode and the 7-day limit

While the consent screen's publishing status is **Testing**, Google issues refresh tokens that expire after **7 days** for any scope beyond basic profile. The Tasks scope counts.

- **On the phone** this rarely matters. `AuthorizationClient` hands out short-lived access tokens and fetches new ones silently while Google still considers the grant valid. If Google drops the grant, Klean shows "Google wants you to connect again" in Settings › Tasks, and tapping Connect fixes it.
- **For a server-side refresh token** (the optional Web client), testing mode means reconnecting every week.

To avoid that, set the publishing status to **In production**. For a personal project with under 100 users, Google shows an "unverified app" warning at consent, which you can click through ("Advanced › Go to Klean"). Verification is only needed for a public app.

## Accounts and the account menu

- **The avatar.** Today's top bar shows the current Google account's photo (or its initial on a coloured circle, or a neutral person icon without Google). Tapping it opens the account menu: the email, "Hi, {first name}", Manage your Google Account (opens <https://myaccount.google.com/> in the browser), Switch account, then More from Klean (Settings, Help).
- **The profile.** After every grant Klean calls `https://openidconnect.googleapis.com/v1/userinfo` with the access token and keeps `email`, `name`, `given_name` and `picture`. The photo is downloaded once at `=s192-c` into app-private storage (`files/google-accounts/`, named by a hash of the email). The profile is refreshed at most daily, after a successful sync.
- **Switching.** Switch account lists every account Klean was authorized with on this phone. Picking one asks Google for that account (`setAccount`), silently when it was granted before. Klean then remembers which of the old account's lists were shown, takes its cached lists and tasks out of Klean, and syncs the new one. Each account keeps its chosen lists. Queued offline edits are tagged with their account and are only ever sent while that account is current, so one account's edits never reach another. Swiping the avatar down or up switches to the next or previous account.
- **Add another account** opens Google's account picker. **Manage accounts on this device** opens Android's account settings.
- **Installs connected before the profile scopes** keep syncing as before (silent refreshes ask for Tasks only). The menu shows "Google Tasks is connected" with a quiet "Show your Google photo", which asks once for the new scopes.

## What Klean stores

| What | Where | Synced |
|---|---|---|
| "Connected" flag, last sync time, offline edits (outbox, per account) | DataStore `google` on the phone | no |
| Accounts (email, name, first name, photo URL and file name), the current one, each account's chosen lists | DataStore `google` on the phone | no, and never in backups |
| Account photos | `files/google-accounts/` (app-private) | no, and never in backups |
| Access tokens | memory only | no |
| Google lists and tasks (cache) | `task_list` / `task` rows with source/origin `GOOGLE`, `remote_id`, `remote_etag` | yes, through Klean sync, like other rows |
| Reminders on a Google task | a local `reminder` row linked by `task_id` | yes (Klean only; Google never sees them) |

**Disconnect** (Settings › Tasks, which shows "Connected as name@gmail.com") removes the Google lists and tasks from Klean, clears the outbox, and deletes every account entry and photo. Nothing changes in Google. To also revoke access, use <https://myaccount.google.com/permissions>.

## How syncing works

- **When it runs:** WorkManager, every hour while connected; after every edit of a Google task; each time you open Tasks; and when you tap "Sync now". It needs a network connection.
- **Order:** queued edits are sent first (PATCH), then every list is read. For selected lists, every task is read, including completed and hidden ones.
- **Conflicts:** last write wins. A queued Klean edit replaces whatever Google had for title, notes, due date and done. Otherwise Google's version replaces the cached row.
- **Times:** Google Tasks stores dates only. A time you set in Klean stays local while the date doesn't change.
- **Klean-only fields** (priority, repeat, someday, steps you add) are not sent to Google:
  - priority stays on the local row;
  - repeat is cleared on Google rows;
  - a step added under a Google task is a Klean task in your default list.
- **New tasks** aimed at a Google list go to your default Klean list instead. Google lists don't appear in the list pickers.
- **Deleting:** Google tasks have no Delete button in Klean; delete them in Google Tasks itself.

## Not built yet

- `calendar.readonly` for Google Calendar. Calendar uses the phone's calendar provider and DAV.
- Sign-in on phones without Google Play services (Waydroid, de-Googled phones). Settings explains that it isn't available, and everything else works as usual.
- **A Google task edited on a second Klean phone that isn't connected to Google.** The edit syncs to the connected phone through Klean, but it is only sent to Google if it was made on a connected phone. Otherwise the next Google pull overwrites it.
