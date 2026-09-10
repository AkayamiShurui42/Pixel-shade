# Pixel Shade settings parity target

Reference: Bottom Quick Settings screenshots supplied during device testing.

This is a capability/organization reference only. Pixel Shade keeps its Pixel / Android-17 runtime visual direction and does not copy Bottom Quick Settings branding or legacy OEM-specific behavior.

## Handles

Relevant controls to support:

- Hide handle icon / visible strip.
- Hide trigger in fullscreen.
- Hide trigger in landscape.
- Hide trigger while the keyboard is open.
- Top trigger: enabled implicitly by the primary shade trigger, length/width, touch size/height, horizontal position, vertical offset.
- Bottom trigger: enabled, length/width, touch size/height, horizontal position.
- Left trigger: enabled, length/height, touch size/width, vertical position.
- Right trigger: enabled, length/height, touch size/width, vertical position.
- Pull distance / activation threshold.

Existing Pixel Shade geometry maps directly to most of this model; RC81 adds the missing bottom-trigger and environment-rule configuration keys.

## Layout

Relevant controls to support:

- Number of rows.
- Number of columns.
- Number of compact/small columns.
- Panel corner radius.
- Panel padding.
- Tile size/height.
- System icons visibility.
- Panel header visibility.
- Panel footer visibility.
- Hide tile text.
- Crop app icons to circles.
- 12/24-hour clock selection.

Pixel Shade should retain the Pixel-style mixed compact/wide tile model rather than reproducing Bottom Quick Settings' exact grid renderer.

## Notifications

Relevant controls to support:

- Show/hide notification section.
- Notification background/theme integration.
- Dynamic notification color option.
- Notification blacklist (future per-package filter UI).
- Hide persistent/ongoing notifications.
- Only show media notifications.
- Remove spacing between notifications / compact stack.
- Auto-expand notification content.
- Quick clear-all control.
- Auto-close shade after clearing notifications.

Explicitly excluded:

- MIUI notification fix. Pixel Shade targets OnePlus/OxygenOS and modern AOSP behavior, so a legacy MIUI workaround should not be exposed unless an actual compatibility bug requires it.

## Advanced behavior

Relevant controls to support:

- Show shade on lock screen.
- Vibrate on touch.
- Use device haptic feedback.
- Horizontal trigger swipe changes brightness.
- Smooth/logarithmic brightness response.
- Open directly to expanded state only if Pixel Shade later introduces an anchored/collapsed state.
- Auto-close after tapping a Quick Settings tile.
- Show Wi-Fi SSID when available and permission-compatible.
- Quick-expand gesture only if a second panel state exists.

## Bottom status bar

The reference app includes a synthetic bottom status bar. This is not part of Pixel Shade's current product goal. Do not add it merely for parity.

Potentially reusable sub-features:

- network type display;
- notification/status icon visibility;
- status/header icon visibility controls.

Explicitly excluded for RC81:

- replacing/disabling the system top status bar;
- per-app bottom-status-bar blacklist;
- separate bottom-status-bar background/icon color system.

Those features expand Pixel Shade from a shade replacement into a persistent status-bar replacement and materially increase SystemUI risk.

## RC81 implementation rule

Do not expose a setting unless its runtime behavior is implemented. Configuration keys may land before UI wiring, but release builds should not present dead switches.

RC81 priorities:

1. Preserve working OxygenOS shade suppression, Shizuku/Shizuku+ integration, custom tiles, notifications, and media handling.
2. Add meaningful handle/layout/notification/behavior controls from the reference.
3. Fix the device-observed brightness slider and notification/media presentation.
4. Run CI and device acceptance testing before merging to main.
