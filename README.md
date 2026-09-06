# Timer Game

Android app built with Kotlin + Jetpack Compose.

## Features

- 30-minute session timer
- Alternating draw/execution phases
- Automatic 1–6 random draw during draw phases
- Six cumulative 10-second slots (0–9, 10–19, ..., 50–59 seconds)
- Each accumulated count creates a quota of `count × 10`
- Mark the current quota as success/failure
- Unprocessed quotas are recorded as failures when their time window ends
- Pause, resume, reset, and final statistics
- GitHub Actions builds a debug APK automatically
- GitHub Releases build a fixed-key signed APK
- The app checks the public GitHub Release for newer versions

## Debug APK

After a push to `main`, open **Actions → Build APK** and download the `timer-game-debug-apk` artifact.

## Signed release APK

Release builds use one fixed keystore so future APKs can update an already-installed release build. Android requires APKs to be signed with a compatible signing key, and the private key should be kept secure and backed up.

Before creating the first release, configure these **repository Actions secrets**:

- `ANDROID_KEYSTORE_BASE64` — Base64-encoded `.jks`/`.keystore` file
- `ANDROID_KEYSTORE_PASSWORD` — keystore password
- `ANDROID_KEY_ALIAS` — key alias
- `ANDROID_KEY_PASSWORD` — key password

GitHub Actions secrets are encrypted and are only exposed to workflows that explicitly reference them.

The current release is `v1.0.5`. Release version `1.0.5` uses versionCode `10500`. The release workflow runs the unit tests, builds the signed APK, and attaches `timer-game-release.apk` to the GitHub Release.

**Important:** the existing debug APK is not the fixed-key release build. The first migration from the debug build to the signed release build may require uninstalling the debug app and installing the first signed release manually. After that, future releases must keep using the same keystore.

The repository is public so the app can check GitHub Releases without an embedded access token.
