# Security Policy

InstallBlock handles package installation, uninstallation, Android Keystore keys,
Shizuku IPC, and untrusted blocklist/icon content. Security reports are welcome.

## Supported versions

Only the latest released version receives security fixes. Development builds are
not supported for production use.

## Reporting a vulnerability

Please report vulnerabilities privately through GitHub's **Report a
vulnerability** flow on the repository's Security page. Do not publish
credentials, private blocklists, device identifiers, or exploit payloads in a
public issue.

Include:

- affected version or commit;
- Android version and device/ROM;
- affected file and the trust boundary involved;
- minimal reproduction steps and concrete impact;
- whether Shizuku, root, or physical device access is required.

We will acknowledge valid reports and coordinate a fix and disclosure timeline
with the reporter.

## Security boundaries

- The remote blocklist and icon URLs are untrusted input.
- Generated placeholders are signed only with the app's Android Keystore key.
- Shizuku operations require explicit Shizuku permission.
- Icon decoding runs in an isolated Android process.
- No analytics, crash telemetry, or user data upload is intentionally included.

## Development requirements

Security-sensitive changes must preserve input validation, bounded resource
usage, explicit IPC authorization, private temporary-file handling, and
clear error reporting. Run `./gradlew :app:testDebugUnitTest :app:lintDebug`
before submitting a change.
