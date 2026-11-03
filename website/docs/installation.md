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

## Google Play (closed test)

Orbiter isn't publicly listed on Google Play yet. Google only lists an app from a new developer
after at least 12 people have tested it for 14 days in a row, so joining the test helps get it
there. Testers get Orbiter from the Play Store, with updates like any other app.

1. Join the [orbiter-testers](https://groups.google.com/g/orbiter-testers) Google Group. You need
   a Google account; joining just puts you on the tester list.
2. Signed in with **the same Google account**, open
   [play.google.com/apps/testing/com.pranavajk.orbiter](https://play.google.com/apps/testing/com.pranavajk.orbiter)
   and tap **Become a tester**.
3. Follow the Play Store link on that page and install Orbiter. It can take a few minutes after
   opting in before the Play Store shows it.

If the testing page says the app isn't available, either the Google account you're signed in
with isn't the one that joined the group, or a new release is still in Google's review. Check the
account, or try again in a day or two.

To stop testing, use **Leave the program** on the same testing page. Leaving the group as well
removes you from the tester list.

## F-Droid

Orbiter isn't on F-Droid yet. It's been submitted and is waiting for review; you can follow it in
[the merge request](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/55629). F-Droid asks for a
few months of development before it includes a new app, so expect it to take a while.

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
