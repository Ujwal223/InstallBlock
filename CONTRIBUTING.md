# Contributing to InstallBlock

Thank you for helping improve InstallBlock. Keep changes focused, reviewable,
and compatible with the project's GPL-3.0-or-later license.

## Development setup

- JDK 21
- Android SDK with compile SDK 37, target SDK 36, and build-tools installed
- Android Studio or the Gradle wrapper

Build and validate locally:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Device-only behavior must also be tested on a disposable Android device or
emulator before release, especially PackageInstaller, Android Keystore,
Shizuku, certificate verification, and the isolated icon decoder.

## Code expectations

- Use Kotlin coroutines and existing repository patterns.
- Keep package-manager, signing, network, bitmap, and filesystem work off the
  main thread.
- Treat blocklist JSON, package names, labels, icon URLs, and Binder inputs as
  untrusted.
- Keep downloads, decoded images, generated APKs, and subprocesses bounded.
- Do not add analytics, advertising, tracking, or silent network telemetry.
- Do not weaken manifest component isolation or Shizuku authorization.
- Preserve the generated placeholder's no-launcher-activity behavior.

## Pull requests

1. Explain the behavior change and security/performance implications.
2. Include focused tests for new validation or state behavior.
3. Run the build, unit tests, and lint checks.
4. Document any device or Android-version limitations.
5. Keep unrelated formatting and refactors out of the change.
