---
name: pixel-shade
description: Continue development, debugging, building, and device testing of AkayamiShurui42/Pixel-shade. Use for Pixel Shade work involving OxygenOS/SystemUI, Shizuku, Quick Settings, notifications/media, handles, theming, CI/APKs, or Termux/Codex handoff.
---

# Pixel Shade Development

For any Pixel Shade task, first locate the current Git repository root and read `docs/pixel-shade-codex-handoff.md` plus `docs/bottom-quick-settings-parity.md` from that repository. This keeps the same skill working whether Codex discovers the repo-scoped copy or a personal copy installed under `$CODEX_HOME`.

Use the live Git repository and GitHub state as the source of truth for current branches, commits, pull requests, and CI. The dated snapshot in the handoff file is only a restart aid.

Preserve the established Pixel/AOSP Android-17 visual direction, working OxygenOS/Shizuku integration, and the rule that settings shown to the user must have real runtime behavior.

When the task is complete, report the branch/head, behavior changed, CI/APK status, remaining device verification, and PR state.
