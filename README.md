# Checkpoint (Android)

An Android companion to Can's Checkpoint program for macOS — a periodic "what
have you been doing?" check-in logger. This is an independent build: no
server, no accounts, no live sync between devices. Each device writes its own
local log; getting both files in one place (Syncthing, AirDrop, email,
whatever) is up to you.

## What it does

- Starts a check-in session automatically when your phone is unlocked, or
  manually via the in-app **Start session** button.
- Every 15/30/60 minutes (your choice) while a session is active, it fires a
  notification asking what you've been doing. Tap it, type a one-line answer,
  save.
- Every entry is appended to `checkpoint-<device>.jsonl` in the app's external
  files directory, in the same schema the Mac app uses:
  ```json
  {"id": "<uuid>", "activity": "<string>", "timestamp": "<ISO8601 UTC>", "durationMinutes": <int>}
  ```
- **Export CSV** turns your log into a CSV and hands it to Android's share
  sheet (email it to yourself, save to Drive, whatever you'd rather use).
- Session goes quiet 2 minutes after the screen turns off, unless you started
  it manually — a manual session keeps prompting regardless of screen state.

## Differences from the Mac app

The Mac app prompts on a fixed timer whenever it's enabled, because its job is
just to nag periodically. The Android app only prompts during an actual
"session" (screen on/unlocked, or manually started), because its job is to
gauge whether your phone use was productive — not to nag you constantly.

## Installing

Not on the Play Store or F-Droid. Two ways to get it, pick whichever:

### Option A: Download the APK directly (works for anyone, no extra app needed)

1. Go to the [Releases page](https://github.com/r-fy/checkpoint-android/releases) and download
   `app-release.apk` from the latest release.
2. Open the downloaded file on your phone. Android will ask you to allow installing from this
   source the first time — approve that for your browser or file manager, then continue the
   install.
3. To update later, come back to the Releases page and repeat with the newest version.

### Option B: Obtainium (auto-updates for you)

If you already use [Obtainium](https://github.com/ImranR98/Obtainium) (or want to start), it'll
track new releases and prompt you to update automatically instead of you checking back manually:

1. Install Obtainium (get it from [its own releases page](https://github.com/ImranR98/Obtainium/releases) or F-Droid).
2. Open Obtainium, tap **Add App**.
3. Paste this repo's URL: `https://github.com/r-fy/checkpoint-android`
4. Tap **Add**, then install.

## Bugs / requests

This is a small side project, not actively maintained on a schedule — but
[issues](https://github.com/r-fy/checkpoint-android/issues) are open to
anyone. File one for bugs, feature requests, whatever. It'll get looked at
periodically, no promises on timing.

## Building from source

Requires JDK 17 and the Android SDK (compileSdk/targetSdk 36, minSdk 29).

```bash
./gradlew assembleDebug    # debug build
./gradlew assembleRelease  # release build (needs a signing config, see below)
```

## License

MIT — see [LICENSE](LICENSE).
