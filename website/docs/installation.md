---
sidebar_position: 2
---

# Installing the app

Orbiter requires **Android 8.0 (API 26)** or newer.

## GitHub releases

Download the APK from the
[latest release](https://github.com/pranavajk1/orbiter-for-bookorbit/releases/latest) and open it on
your phone. Android asks you to allow installing apps from your browser or file manager the first
time.

- `orbiter-<version>.apk` is the full build, with Chromecast.
- `orbiter-fdroid-<version>.apk` leaves out Google's Cast library, so it has no Chromecast.

GitHub doesn't update apps by itself, so check the releases page for new versions.

## F-Droid

Orbiter is coming to F-Droid, which will keep it up to date for you.

## Google Play

Orbiter isn't on Google Play yet. Google only lists an app from a new developer after a closed
test with at least 12 testers, and that test opens soon. This page will say how to join it.

## Switching between them

Every Orbiter build is signed with the same key, whichever source it comes from. You can install
from GitHub now and move to F-Droid or Google Play later: the new source updates the app you have,
without reinstalling or losing your downloads and settings.

## Building from source

If you'd rather build it yourself:

### Prerequisites

| Tool               | Version                                | Notes                                                   |
| ------------------ | --------------------------------------- | -------------------------------------------------------- |
| **Android Studio** | Ladybug (2024.2) or newer               | Easiest path; bundles a compatible JDK + SDK manager.    |
| **JDK**            | 17+ (25 recommended)                    | Android Studio's bundled JBR works.                      |
| **Android SDK**    | Platform **37**, Build-Tools 35+        | Installed via Android Studio's SDK Manager.              |
| **A device**       | Emulator (API 26+) or a physical phone  | Min SDK is **26**; compile/target SDK is **37**.         |

### Steps

1. Clone the [repository](https://github.com/pranavajk1/orbiter-for-bookorbit).
2. Open the repository's root folder in Android Studio and let Gradle sync finish.
3. Pick a device in the toolbar's device dropdown, then click **Run**.

Or from the command line:

```bash
./gradlew :app:installDebug      # build + install on the connected device/emulator
./gradlew :app:assembleDebug     # just build the APK -> app/build/outputs/apk/debug/
```

The debug build installs alongside the release build as `com.pranavajk.orbiter.debug`, so you can have
both on one device.
