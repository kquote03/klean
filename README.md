# Klean

A local-first personal life-management app for Android. Prototype 1 is the app foundation plus a diet-awareness module: log what you eat in one action from a notification, widget, Quick Settings tile or voice, and keep today's list in view. Everything works offline with no account. AI is optional, and you choose the provider.

## Try it

- Latest build of `main`: [klean-debug.apk](../../releases/download/nightly/klean-debug.apk). It is rebuilt on every push.
- Dated nightly builds: [Releases](../../releases).

These are debug builds (`app.klean.debug`). Each one installs over the previous one and keeps your data. They need Android 9 or newer.

## Build

The toolchain comes from the Nix flake:

```
nix develop -c ./gradlew assembleDebug
nix develop -c ./gradlew :shared:jvmTest testDebugUnitTest
```

Read `AGENTS.md` for conventions and project status, and `PLAN.md` for the full plan.
