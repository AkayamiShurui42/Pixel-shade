# Pixel Shade

Standalone Android replacement notification-shade experiment. Pixel Shade draws its own overlay/activity surface; it does not patch or replace SystemUI.

## Current goals

- Pixel / Android 17 visual direction with Material adaptive colors plus manual and hybrid overrides
- Bottom Quick Settings-inspired setup organization without copying its branding
- Configurable top, bottom, left, and right trigger regions
- Downward pulls from the top and sides; upward swipe or double-tap from the bottom
- Horizontal top-edge brightness control when Modify system settings permission is granted
- NotificationListenerService integration
- AccessibilityService and Display-over-apps trigger paths
- Shizuku / Shizuku+ privileged backend
- One APK with no PowerUserHub dependency

The app currently compiles and targets API 35. Android 17 is the visual/interaction target, not a claim that the project uses Android 17-only APIs.

## Stock-shade safety

After an explicit warning, Pixel Shade can use Shizuku to run Android's ADB-equivalent status-bar command and disable stock notification-shade expansion while a verified Pixel Shade trigger is attached. It records recovery state before issuing the command, bounds privileged calls with timeouts, and checks `dumpsys statusbar` for the effective expansion bit before reporting success or removing its recovery surface.

Android implements `send-disable-flag` as shared shell status-bar state. Another ADB/Shizuku tool can overwrite those flags, and restoring with `none` can clear flags set through that same shell state. Direct uninstall or app-data clearing cannot run Pixel Shade's cleanup.

Keep notification permission enabled while privileged suppression is active. The ongoing **Pixel Shade recovery** notification includes a **Restore Android shade** action. Always turn Pixel Shade off from its setup screen before uninstalling it; the **Restore stock shade, then uninstall** action restores both the Android shade and any OxygenOS Quick Settings plug-in isolated by Pixel Shade. If the app is removed or loses Shizuku access before it can restore the flag, recover from another authorized ADB session with:

```text
adb shell cmd statusbar send-disable-flag none
```

## Build in Termux or Linux

Prerequisites:

- JDK 17
- Android SDK platforms 35 and 36, plus Build Tools 36.0.0
- Bash, Base64, and keytool
- Network access for the first Gradle/dependency download

From the repository root, run:

```text
bash scripts/build-debug.sh
```

That command limits the vendored Shizuku checkout to the four client modules, builds and stages their AARs, runs the unit tests, and assembles Pixel Shade. The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The root wrapper is pinned to Gradle 9.3.1 with the official distribution checksum; the vendored Shizuku wrapper remains pinned to Gradle 8.14 with its checksum. The build script uses client-only Shizuku project configuration without rewriting tracked files, restores the stable debug signing key, and is the same entry point GitHub Actions uses before publishing the APK artifact.