# Pixel Shade Codex / Termux handoff

This file is the durable development handoff for the Pixel Shade project. It exists so a new Codex session can reconstruct the project without relying on chat history.

## Project identity

Repository: `AkayamiShurui42/Pixel-shade`

Pixel Shade is a replacement Android notification shade / Quick Settings surface targeting modern OnePlus/OxygenOS behavior while keeping a Pixel/AOSP Android-17 visual direction.

Bottom Quick Settings is used as a feature and settings-capability reference. It is not a visual-cloning target. The detailed mapping from the supplied device screenshots is in `docs/bottom-quick-settings-parity.md`.

## Session startup checklist

Before changing source, inspect the current repository state:

```bash
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git branch --show-current
git log -8 --oneline --decorate
git diff --stat main...HEAD
```

Treat the live repository and GitHub state as authoritative for branches, commits, pull requests, and CI. Preserve unrelated local changes.

Read `docs/bottom-quick-settings-parity.md` before changing settings-parity behavior.

## Product constraints

Unless the user explicitly changes direction, preserve these decisions:

- Pixel/AOSP Android-17 is the runtime visual direction.
- Do not drift into iPhone, ColorOS, or OxygenOS visual styling.
- Bottom Quick Settings contributes capability ideas and organization, not branding or exact rendering.
- A setting should not appear in release UI unless it changes real runtime behavior.
- Preserve working OxygenOS shade suppression and Shizuku/Shizuku+ integration while changing presentation code.
- Stock-shade suppression must not depend on disabling all of SystemUI.
- Runtime status values should be live rather than fixed placeholders.
- Editor settings must affect the runtime shade.
- Device behavior and screenshots outrank assumptions based on a desktop/editor preview.

## Current V2 source map

Important files under `app/src/main/java/com/crimson/pixelshade/`:

- `PixelShadePanelV2Activity.kt`: active runtime shade, built-in/custom tiles, brightness control, notification/media presentation, footer and dismiss behavior.
- `PixelShadeEditorV2.kt`: active V2 editor and live preview.
- `PixelShadeConfig.kt`: persisted handle, layout, notification, behavior and motion settings.
- `PixelShadeThemeEngine.kt`: Dynamic, Hybrid and Manual base-color system.
- `PixelShadeTileStyle.kt`: optional active/inactive tile gradient layer kept separate from base colors.
- `PixelShadeTriggerService.kt`: standalone foreground-service/application-overlay trigger.
- `PixelShadeAccessibilityService.kt`: accessibility overlay and accessibility-backed actions.
- `PixelShadeRuntime.kt`: runtime enablement and routing helpers.
- `PixelShadeBootReceiver.kt`: trigger restoration after reboot or package replacement when Pixel Shade remains enabled.
- `PixelShadeNotificationListener.kt`: notification capture, opening, dismissal and actions.
- `RuntimeSystemStatus.kt`: live time/date/network/battery/charging state.
- `SystemActionController.kt`: Wi-Fi, mobile data, Bluetooth, flashlight, DND/Modes and rotation actions.
- `OplusQsPluginControl.kt`: Oplus separate-QS plugin diagnostic/control path.
- `PixelShadeTileEditorNext.kt`, `PixelShadeTileStore.kt`, `TileIconRegistry.kt`, `IconPackResolver.kt`: custom tiles, shortcuts and icon selection.

Older V1 files remain in the repository. Confirm the active V2 path before modifying a similarly named legacy file.

The project-specific CI pipeline is `.github/workflows/build.yml`. It handles the Shizuku Plus client staging, standalone APK build, signature verification and artifact upload.

## OnePlus / OxygenOS research anchors

Prior analysis traced the separate notification/QS path through these vendor classes and calls:

- `NotificationPanelViewController.handleExternalTouch()`
- `enableSeparateNotificationAndQS()`
- `OplusSeparateNotificationAndQSExImpl`
- `OplusPanelViewPager.dispatchTouchEvent()`
- `PageType.NOTIFICATION`
- `PageType.QS`
- `OplusPanelViewPagerController`
- `OplusSeparateQSManager`
- `NotificationPanelViewControllerExImp`
- `QSTileHostHelper`

When vendor behavior is uncertain, prefer current repository code and device evidence first, then supplied OnePlus/OxygenOS dumps and decompiled references, matching AOSP source, and other vendor/framework source only where relevant.

## Established edge-handle behavior

The supplied Bottom Quick Settings screenshots explicitly establish these semantics:

- top handle: swipe down to open;
- bottom handle: swipe up to open;
- left handle: swipe up to open;
- right handle: swipe up to open.

The side handles should not be changed to inward horizontal swipes unless the user asks for a different model.

Settings-parity work includes per-edge enablement and geometry, a hidden visual handle that can retain its touch region, environment visibility rules where implemented, activation threshold, and haptic behavior.

## Theme and tile-style architecture

Base colors and style effects are deliberately separate.

`PixelShadeThemeEngine` owns the base palette and the three theme modes:

- Dynamic: derive from Material You/wallpaper colors;
- Hybrid: dynamic defaults plus selected overrides;
- Manual: explicit overrides for all implemented elements.

The palette can include panel, notification, tile, icon, text, handle, footer and brightness-control colors as those elements are implemented.

`PixelShadeTileStyle` is a second rendering layer. When gradients are disabled, runtime tiles use the base solid colors. When gradients are enabled, built-in and custom tiles can render separate active/inactive start colors, end colors and directions while leaving icon/text colors independent.

Tile icon-shape selection should remain hidden until built-in and custom tiles can apply it consistently without damaging the approved mixed compact/wide Pixel layout.

## Notification and media direction

The runtime stack should read like SystemUI, not a generic settings list.

Current direction includes:

- compact ongoing/persistent notifications;
- suppressing duplicate title text when it merely repeats the app label;
- a separate media card;
- recognizable previous/play/pause/next icons when action semantics can be identified;
- functional notification actions;
- meaningful Clear all behavior;
- filtering, spacing, expansion and auto-close options only when they really alter runtime behavior.

Legacy MIUI-specific compatibility controls are not part of the normal Pixel Shade target unless a real compatibility issue requires them.

## Development and CI workflow

Prefer focused commits that isolate one behavior or closely related set of behaviors. Review the diff before committing or pushing.

Use local compilation when practical, but GitHub Actions remains the authoritative project gate because it reproduces the Shizuku Plus staging/signing/artifact flow.

A cancelled CI run may simply mean a newer commit superseded it under workflow concurrency. Distinguish that from an actual compile/test failure by checking the current head run and its job steps.

Keep release-candidate work in its branch/PR until device-dependent behavior is accepted on the OnePlus device.

## Device acceptance checklist

For features touched by a release candidate, verify on the actual target device as applicable:

- top and enabled bottom/side handles open the replacement shade with correct gestures;
- OxygenOS stock shade suppression behaves as configured without breaking SystemUI;
- Back and swipe/tap dismissal work;
- built-in Quick Settings actions operate;
- custom app/shortcut tiles launch;
- brightness UI looks correct and changes brightness when permission allows;
- time/date/network/battery/charging values are live and correct;
- notifications open, dismiss, clear and invoke actions;
- media controls perform their actions;
- editor/settings changes visibly alter runtime behavior;
- Dynamic, Hybrid and Manual themes render correctly;
- tile gradients work and fall back to solids when disabled;
- reboot/package replacement restores the trigger when Pixel Shade remains enabled;
- no stuck overlay, repeated launch loop or navigation lockout appears.

A green build proves the APK compiled and passed the repository pipeline. It does not prove OEM SystemUI interception or gesture behavior on the phone.

## Termux / Codex environment

The user's `setup-codex-termux.sh` creates `~/bin/codex-termux`.

The script:

- tries native Termux Codex first;
- otherwise installs/uses Debian Bookworm through `proot-distro` with container name `codex-debian`;
- in the Debian fallback, binds the Termux current directory to `/workspace` and starts Codex there.

Normal launch:

```bash
cd ~/path/to/Pixel-shade
codex-termux
```

Inside the Debian fallback, the same checked-out repository is visible as `/workspace`. Do not assume the Termux host path and Debian path are identical.

The repository also contains `.agents/skills/pixel-shade/`. If the current Codex build does not discover that repo-scoped skill automatically, use its `scripts/install-termux.sh` helper to copy the skill into the active Codex user's skill directory.

## Dated restart snapshot

This snapshot is a convenience only. Refresh it from Git/GitHub at session start.

As of 2026-09-10 before the skill files were added:

- `main`: `72a0d3975cb9a45f2bc4f68ed7dce530a683ff5d`;
- previous main artifact line: build #80;
- active RC branch: `settings-parity-rc81`;
- PR #4: open, draft and mergeable;
- RC81 code head: `8b59325be9ee584124c98ce3c6d6427f4d504e6e`;
- GitHub Actions run #91 for that code head: successful;
- RC81 includes corrected edge gestures, expanded settings/editor wiring, a custom Pixel-style brightness control, notification/media cleanup, granular colors, and runtime active/inactive tile gradients.

Adding this handoff/skill changes the branch head, so the hash above must not be treated as the current head without checking Git.

Known gated or intentionally unfinished items at that snapshot include consistent tile icon-shape support, per-app handle blacklisting, full verification of some fullscreen/keyboard trigger behavior, true app-derived dynamic notification coloring, and final OnePlus device acceptance of RC81.

## Handoff report format

At the end of a coding session, record concrete state: branch/head, files or subsystems changed, functional behavior changed, latest CI result/run number, APK artifact status, remaining device verification, and PR state.
