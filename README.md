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

## APK

After a push to `main`, open **Actions → Build APK** and download the `timer-game-debug-apk` artifact.

The repository is intended to remain private.
