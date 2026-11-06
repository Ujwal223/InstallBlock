<div align="center">

# InstallBlock

**Prevent unauthorized and unwanted apps from installing on Android**

An open-source Android utility that permanently prevents specified apps from being installed on your device. Protects your system from unwanted OEM bloatware, silent system update installs, malicious actors, and unauthorized app installations unless explicitly unblocked.

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL_3.0-blue.svg?style=for-the-badge)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84.svg?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Release](https://img.shields.io/github/v/release/Ujwal223/InstallBlock?style=for-the-badge&color=0969da)](https://github.com/Ujwal223/InstallBlock/releases)
<!-- [![Downloads](https://img.shields.io/github/downloads/Ujwal223/InstallBlock/total?style=for-the-badge&color=fa8900)](https://github.com/Ujwal223/InstallBlock/releases) -->
<!-- [![F-Droid](https://img.shields.io/badge/F--Droid-Download-0085FF?style=for-the-badge&logo=f-droid&logoColor=white)](https://f-droid.org/packages/com.ujwal.installblock) -->
[![CodeQL](https://img.shields.io/badge/CodeQL-passing-success?style=for-the-badge&logo=github)](https://github.com/Ujwal223/InstallBlock/actions/workflows/codeql.yml)
[![Support](https://img.shields.io/badge/Support-BuyMeMomo-FFDD00.svg?style=for-the-badge&logo=buymeacoffee&logoColor=black)](https://buymemomo.com/ujwal)

<br/>
</div>

---

## What We Do & Why

### The Problem
Android phones often end up with apps you never asked for. Phone makers push new bloatware through system updates, carriers bundle sponsored apps, and sideloaded APKs or unauthorized users can install things behind your back. Normal uninstalls only work until the next update or auto-installer runs, bringing that same software right back.

### The Solution
InstallBlock stops this directly at the Android system level. When you block an app, InstallBlock locks that package from being added again. Whether the install comes from an OEM system update, Google Play, a browser download, or a third party, the system rejects it immediately. The app stays off your phone until you choose to unblock it.

---

## Key Features

- **Permanent install prevention**: Stops unwanted apps and bloatware from returning through system updates, app stores, or sideloading.
- **Two install modes**:
  - **Shizuku Mode (Recommended)**: Fast, silent 1-tap blocking using elevated permissions with no prompt clutter.
  - **Normal Mode**: Works out of the box on any stock Android device using standard system prompts.
- **Tamper-resistant**: Built-in safeguards keep your block rules active and hard to bypass from device settings.
- **Curated & custom catalog**: Search thousands of pre-listed apps and bloatware packages, or type in any package name yourself.
- **Completely private**: No accounts, no analytics, no ads, and no network tracking. Runs entirely on your own device.

---

## Quick Start

### Installation

- **GitHub Releases**: Download the latest verified APK from [GitHub Releases](https://github.com/Ujwal223/InstallBlock/releases).
- <!-- **F-Droid**: Download verified builds on [F-Droid](https://f-droid.org/packages/com.ujwal.installblock). -->

---

<details>
<summary><b>Building from Source</b></summary>
<br/>

#### Prerequisites
- Android SDK (API 35+)
- JDK 21 (Temurin / OpenJDK recommended)
- Android SDK Platform-Tools & Build-Tools 35.0.0

#### Build Commands

```bash
# Clone the repository
git clone https://github.com/Ujwal223/InstallBlock.git
cd InstallBlock

# Run unit tests
./gradlew :app:testDebugUnitTest

# Build debug APK
./gradlew :app:assembleDebug

# Build release APK (requires configured release signing)
./gradlew :app:assembleRelease
```

</details>

---

<details>
<summary><b>Manual Recovery with ADB</b></summary>
<br/>

If InstallBlock is removed while system-level blocks remain active, blocks can be removed manually over `adb`:

```bash
# Standard unblock
adb shell pm uninstall <package_name>
# Fallback for standard user space:
adb shell pm uninstall --user 0 <package_name>

# Admin-locked unblock
adb shell dpm remove-active-admin <package_name>/com.ujwal.installblock.placeholder.BlockAdmin
adb shell pm uninstall <package_name>

# Clear residual state
adb shell pm clear <package_name>
```

</details>

---

## License

This project is licensed under the **GNU General Public License v3.0 or later** ([GPL-3.0-or-later](LICENSE)).
