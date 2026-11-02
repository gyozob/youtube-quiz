# YouTube Quiz Interrupter

A personal-use Android app that pauses YouTube on a timer and asks a
voice-recorded question. You answer by tapping one of three pictures; the video
resumes when you get it right.

Implements [requirements.md](requirements.md). Not intended for the Play Store —
it relies on an accessibility service and a system overlay, which is why it is
sideloaded.

---

## Getting started on a fresh Windows machine

Nothing needs to be installed first, and **no administrator rights are
required**. One script fetches a complete portable toolchain (JDK, Android SDK,
Gradle):

```powershell
pwsh -File tools\setup-toolchain.ps1
```

It is idempotent — re-run it any time; add `-Force` to re-download everything.
It writes `local.properties` and `tools\activate.ps1`, and sets user-scope
`JAVA_HOME`, `ANDROID_HOME` and `PATH`.

### Where the toolchain goes

By default, an `android-tools` folder **beside this repository** — so a checkout
at `…\code\youtube-quiz` installs to `…\code\android-tools`. Roughly 700 MB.
Nothing is written to Program Files or the registry.

Both scripts resolve the location the same way, first match wins:

1. `-ToolsRoot "C:\somewhere\else"` on the command line
2. the `YTQUIZ_TOOLS_ROOT` environment variable
3. an existing install, inferred from `ANDROID_HOME`
4. the repo-relative default above

So an existing toolchain is picked up automatically wherever it already lives,
and moving the checkout does not strand it.

To use the toolchain in the current shell:

```powershell
. .\tools\activate.ps1
```

## Build, install, watch logs

Installing needs a phone with USB debugging already authorised — if this is a
new device, do [Phone setup](#phone-setup-for-development-and-debugging) first.

```powershell
pwsh -File tools\run.ps1
```

That builds a debug APK, installs it over USB, prints the phone's Android
version, and tails the app's logs. `-NoLogs` skips the tail; `-Logs` only tails.

Manually, the equivalents are:

```powershell
.\gradlew.bat assembleDebug          # app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat testDebugUnitTest      # unit tests
.\gradlew.bat lintDebug              # static analysis
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb logcat -s YTQuiz:V
```

## Phone setup for development and debugging

Everything below has to be done once per phone, in order. Nothing in the app
works until all of it is in place, and most of the failure modes are silent —
the quiz simply never appears — so it is worth verifying each step rather than
assuming.

Paths are given for **Xiaomi HyperOS / MIUI**, which is what this was developed
against (Xiaomi 25098RA98G, Android 16). Stock Android is similar but shallower;
the Xiaomi-only steps are called out.

### 1. Enable developer options

**Settings → About phone → tap "OS version" seven times.**

On Xiaomi it is *OS version* (older MIUI: *MIUI version*), not *Build number*.

### 2. Developer-options toggles

**Settings → Additional settings → Developer options**

| Toggle | Needed for |
|---|---|
| **USB debugging** | adb working at all |
| **Install via USB** | **Xiaomi only.** Without it `adb install` fails with `INSTALL_FAILED_USER_RESTRICTED` |
| **USB debugging (Security settings)** | **Xiaomi only.** Some HyperOS builds need it for `adb shell` |

Xiaomi often demands a Mi account sign-in and an inserted SIM before it will let
you enable *Install via USB*. If that is blocked, use **Wireless debugging**
instead (same menu) — `adb pair <ip>:<port>` with the on-screen code, then
`adb connect`.

### 3. USB mode

Plug the cable in, pull down the notification shade, tap the **USB** notification
and choose **File transfer**. "Charging only" does not expose the ADB interface.

A quick way to tell whether debugging is actually live, before adb sees anything:

```powershell
Get-PnpDevice | Where-Object FriendlyName -match 'Android|MTP'
```

A Xiaomi in MTP-only mode shows `VID_2717&PID_FF40`. The PID changes once the
ADB interface is exposed. Same idea for other brands: one PID for MTP, another
for MTP+ADB.

### 4. Authorise the computer

Unplug and replug. **Unlock the phone screen** — the dialog only appears on an
unlocked device — and accept **Allow USB debugging**, ticking *Always allow from
this computer*.

```powershell
adb devices -l     # must say "device", not "unauthorized" and not empty
```

If no prompt ever appears: **Developer options → Revoke USB debugging
authorisations**, then replug.

### 5. Install

```powershell
pwsh -File tools\run.ps1
```

See [Build, install, watch logs](#build-install-watch-logs) for the individual
Gradle and adb commands.

### 6. In-app permissions

Open the app. The **Setup** tab shows live status for all five checks.

| Permission | Why | Where |
|---|---|---|
| Display over other apps | Draws the quiz on top of YouTube | Setup → *Grant* |
| **Accessibility service** | Detects YouTube in the foreground **and hosts the timer** | see below |
| Microphone | Records questions and feedback sounds | Setup → *Grant* |
| Unrestricted battery | Stops the OS freezing the timer | Setup → *Grant* |
| YouTube installed | Informational | — |

#### Finding the accessibility service on HyperOS

This one is genuinely hard to find, and the in-app *Grant* button can only open
the top-level Accessibility screen — Android blocks apps from deep-linking
straight to their own service page.

> **Settings → Accessibility → `General` tab → scroll to the very bottom →
> Permissions → Downloaded apps → "YouTube Quiz watcher"** → on → **Allow**

Two things catch people out:

- The **Permissions → Downloaded apps** group sits right at the bottom of the
  *General* tab, below "Accessibility menu", and looks like the end of the page.
  HyperOS split this screen into General / Vision / Hearing / Physical tabs, so
  the usual "Accessibility → Downloaded services" advice does not match.
- The entry is labelled **"YouTube Quiz watcher"**, not "YouTube Quiz" — that is
  the service label, not the app name. Searching for the app name finds nothing.

#### If the accessibility toggle is greyed out

Android 13+ blocks accessibility for sideloaded apps until you unlock it:

**Settings → Apps → YouTube Quiz → ⋮ (top right) → Allow restricted settings**,
then authenticate. Installing through `tools\run.ps1` normally avoids this
entirely, because `adb install` is not treated as a sideload and the script also
clears the app-op.

#### Battery, on Xiaomi specifically

The Setup tab's *Grant* covers the standard Doze whitelist, but HyperOS has its
own killer on top:

**Settings → Apps → YouTube Quiz → Battery saver → No restrictions**

Skip this and MIUI freezes the accessibility service after a while; the timer
stops and the quiz silently never fires.

### 7. Add content

A tag is only usable with **three images sharing that tag** and **at least one
question**. With fewer, the service logs `no playable question is available` and
skips the interval.

- **Images** → *Add images* → pick 3+ → give them all the same tag
- **Questions** → *New question* → same tag → record → pick the correct image → Save
- **Sounds** → record the good/wrong clips (optional)

Tags are trimmed and lowercased, then matched exactly — `Test` and `test` are the
same tag, `test ` and `tests` are not.

The Setup → Content section lists every tag and says exactly what each one is
missing.

### 8. Verify it is actually working

```powershell
pwsh -File tools\run.ps1 -Logs      # or: adb logcat -s YTQuiz:V
```

Play a YouTube video and watch for:

```
YouTube in foreground (watched so far: 0s)
next quiz in 30s
pause via BOTH (music active: true)
overlay shown for question 1 tag=test
wrong answer for question 1
correct answer for question 1
resume via BOTH
abandoned audio focus
```

If the overlay ever gets stuck, **press and hold the top-left corner for three
seconds** to force-dismiss it.

### Checking state from the PC

All read-only, useful when the UI says one thing and the system thinks another:

```powershell
$pkg = 'com.personal.youtubequiz'

adb shell cmd appops get $pkg SYSTEM_ALERT_WINDOW          # expect: allow
adb shell settings get secure enabled_accessibility_services
adb shell dumpsys deviceidle whitelist | Select-String youtubequiz
adb shell dumpsys package $pkg | Select-String 'RECORD_AUDIO.*granted'

# Is the service even registered? If it is missing here, it is a manifest
# problem, not a navigation problem.
adb shell cmd package query-services --brief `
  -a android.accessibilityservice.AccessibilityService | Select-String youtubequiz

# What content actually made it onto the device
adb shell run-as $pkg ls -1 files/images
adb shell run-as $pkg ls -1 files/audio
```

### Troubleshooting

| Symptom | Cause |
|---|---|
| `adb devices` empty, phone charges fine | USB mode is "Charging only", or USB debugging is off |
| Listed as `unauthorized` | RSA prompt not accepted — unlock the screen and replug |
| `INSTALL_FAILED_USER_RESTRICTED` | Xiaomi *Install via USB* is off |
| Accessibility toggle greyed out | Android 13+ restricted settings — see above |
| Cannot find the service in the list | Look for **"YouTube Quiz watcher"** under *Permissions → Downloaded apps* |
| Quiz never fires | Master toggle off, no playable tag, or MIUI battery saver killed the service |
| `no playable question is available` in logs | Tag has fewer than 3 images, or no question |
| Overlay never appears but logs say it should | *Display over other apps* was revoked |
| Video does not pause | Try another pause strategy — see below |

> **A note on `adb shell input` for automation:** driving the UI with scripted
> taps works, but only when nobody is touching the phone. Blind taps racing a
> human land in whatever happens to be on screen — during development this
> wandered into Settings and silently switched on an unrelated accessibility
> shortcut. Script the device or use it by hand, never both at once.

## Pausing YouTube

This is the one part of the design that could not be settled from
documentation. YouTube is not obliged to pause when another app takes transient
audio focus — some builds duck instead, and auto-resume when focus is returned
is a courtesy, not a contract. So three strategies ship and you pick whichever
your device honours:

- **Audio focus + media key** (default) — takes focus, then sends a pause key
  only if playback is still running 400 ms later.
- **Audio focus only**
- **Media key only** — `dispatchMediaKeyEvent`, like a headset button. Needs no
  permission but does nothing if the app published no media session.

**Verified working:** the default *Audio focus + media key* pauses and resumes
the real YouTube app on Xiaomi 25098RA98G / Android 16 (HyperOS). That is one
device and one YouTube build, so re-check on any other phone.

To re-test: start a video, then use **Setup → Diagnostics → Pause / Resume**.
The Pause toast reports whether anything was actually playing, which
distinguishes "the strategy failed" from "nothing was playing to begin with".

## How it works

```
AccessibilityService (YouTubeWatchService)
  - window-state changes carry the foreground package name
  - accumulates YouTube watch time; fires when the interval elapses
        |
        +-- MediaControl ....... audio focus and/or media key events
        +-- OverlayController .. TYPE_APPLICATION_OVERLAY window, 3 image cards
        +-- AudioPlayer ........ question and feedback clips, one at a time
        |
   QuizRepository -> SQLite metadata + internal-storage images/audio
```

Design decisions worth knowing:

- **The timer lives in the accessibility service**, not a separate foreground
  service. The system already keeps accessibility services alive, which avoids
  a permanent notification and Android 14's foreground-service-type rules.
- **Watch time accumulates across visits.** Switching away from YouTube pauses
  the countdown rather than resetting it.
- **The overlay swallows the Back button**, as the requirements ask. Because
  that could trap you if content were ever broken, a **3-second press in the
  top-left corner** force-dismisses it.
- **Storage is plain `SQLiteOpenHelper`**, not Room. The schema is two tables;
  this removes the KSP annotation processor and a whole class of version-
  alignment breakage from a build with no IDE behind it.
- **Images are copied into internal storage** and downscaled to 1080 px on
  import, with a 320 px thumbnail, so the library keeps working if the original
  photo is deleted and the overlay is cheap to draw.

## Layout

```
app/src/main/java/com/personal/youtubequiz/
  data/     DbHelper, QuizRepository, MediaFiles, Prefs, Models
  service/  YouTubeWatchService, OverlayController, MediaControl, AudioPlayer
  ui/       MainActivity + Setup/Images/Questions/Sounds screens (Compose)
app/src/test/                      unit tests for the selection rules
tools/setup-toolchain.ps1          portable no-admin toolchain installer
tools/run.ps1                      build + install + logcat
```

## Toolchain versions

JDK 17 (Temurin) · Gradle 9.8.2 · AGP 9.5.0 · Kotlin (bundled with AGP) ·
compileSdk/targetSdk 37 · minSdk 26 · Compose BOM 2026.09.00

AGP 9 supplies Kotlin support itself, so there is no separate
`org.jetbrains.kotlin.android` plugin. Current AndroidX releases require
AGP 9.1+ and compileSdk 37, so the 8.x line is not an option.
