# F-Droid

[`com.pranavajk.orbiter.yml`](com.pranavajk.orbiter.yml) is Orbiter's build recipe for the main
F-Droid repository. The copy that F-Droid uses lives in
[fdroiddata](https://gitlab.com/fdroid/fdroiddata) as `metadata/com.pranavajk.orbiter.yml`; this one
is kept here so changes to the build can be made and reviewed next to the code.

## How a release reaches F-Droid

1. Merging the release-please PR tags `vX.Y.Z`. `release.yml` then builds the `fdroid` flavor,
   signs it with the release key, and attaches it to the GitHub Release as
   `orbiter-fdroid-X.Y.Z.apk`.
2. F-Droid's `checkupdates` sees the new tag (`UpdateCheckMode: Tags`) and adds a build block.
3. F-Droid builds the same tag itself, on JDK 25 like CI. It checks its unsigned APK against the one
   on the GitHub Release (`Binaries`), signed by the key in `AllowedAPKSigningKeys`. If they match
   apart from the signature, F-Droid publishes the copy signed with our key; if not, the build fails.

So F-Droid, GitHub Releases and anything built by `release.yml` share one signature, and each can
update the others.

## What keeps the build reproducible

- `versionCode`/`versionName` are plain literals in `app/build.gradle.kts`, which F-Droid reads
  with a regex.
- `dependenciesInfo` is off, so there's no Google-encrypted dependency block in the APK.
- `packaging.jniLibs.keepDebugSymbols` keeps AndroidX's prebuilt `.so` files as published.
  Otherwise AGP strips them only when an NDK is installed, which differs between CI and F-Droid.
- CI and F-Droid both build on JDK 25 (`release.yml`; `sudo` in the recipe).

## The release key

`AllowedAPKSigningKeys` is the SHA-256 of the release certificate (alias `orbiter`). The keystore
and its password are in the repo's `RELEASE_*` secrets. Losing the key means no more updates for
anyone who installed Orbiter, so keep a backup outside GitHub.
