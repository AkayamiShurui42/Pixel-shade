# Pixel Shade

Standalone Android replacement notification shade experiment.

Goals:
- Pixel / Android 17-style replacement shade
- Material adaptive colors plus manual/hybrid overrides
- Invisible configurable top, bottom, and side trigger regions
- Swipe-down shade action and horizontal status-bar brightness gesture
- NotificationListenerService integration
- AccessibilityService fallback/gesture support
- Shizuku / Shizuku+ privileged backend
- One APK, no PowerUserHub dependency

## Stock-shade safety

Pixel Shade does not replace SystemUI. After an explicit in-app warning, it uses Shizuku to run Android's status-bar shell command and disable stock notification-shade expansion while Pixel Shade is active.

Turn Pixel Shade off from its setup screen before uninstalling it. The setup screen restores the stock shade first and includes a **Restore stock shade, then uninstall** action. If the app is removed or loses Shizuku access before it can restore the flag, recover from another authorized ADB session with:

```text
adb shell cmd statusbar send-disable-flag none
```

The `main` branch builds the standalone debug APK through GitHub Actions.
