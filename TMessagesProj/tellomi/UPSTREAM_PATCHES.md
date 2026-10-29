# Upstream patches

DrKLO files this tree changes on purpose. New Tellomi assets live under `TMessagesProj/tellomi/` (and the `tellomi` resource flavor at `TMessagesProj/tellomi/res`). Prefer those over editing `src/main`.

Paths below are relative to the repo root. "Upstream" means the file exists in DrKLO/Telegram (this tree's snapshot is `3de37c8cf`, "update to 13.1.1").

## Launcher icon

| File | Change |
| --- | --- |
| `TMessagesProj/build.gradle` | Product flavor `tellomi` (dimension `brand`) with `sourceSets.tellomi.res` pointed at `tellomi/res`, so launcher and notification art override `src/main` without replacing those files. `missingDimensionStrategy 'brand', 'tellomi'` on every `com.android.application` module so `assembleAfatDebug` still resolves. |

These DrKLO launcher files were replaced with a placeholder on the rebrand branch. This change restores them to the `3de37c8cf` bytes. They are not an ongoing content patch; the `tellomi` flavor overrides them when resources merge:

- `TMessagesProj/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `TMessagesProj/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- `TMessagesProj/src/main/res/mipmap-mdpi/ic_launcher.png`
- `TMessagesProj/src/main/res/mipmap-mdpi/ic_launcher_round.png`
- `TMessagesProj/src/main/res/mipmap-hdpi/ic_launcher.png`
- `TMessagesProj/src/main/res/mipmap-hdpi/ic_launcher_round.png`
- `TMessagesProj/src/main/res/mipmap-xhdpi/ic_launcher.png`
- `TMessagesProj/src/main/res/mipmap-xhdpi/ic_launcher_round.png`
- `TMessagesProj/src/main/res/mipmap-xxhdpi/ic_launcher.png`
- `TMessagesProj/src/main/res/mipmap-xxhdpi/ic_launcher_round.png`
- `TMessagesProj/src/main/res/mipmap-xxxhdpi/ic_launcher.png`
- `TMessagesProj/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png`

Not edited (flavor ships the same resource names):

- `TMessagesProj/src/main/res/drawable-mdpi/notification.webp`
- `TMessagesProj/src/main/res/drawable-hdpi/notification.webp`
- `TMessagesProj/src/main/res/drawable-xhdpi/notification.webp`
- `TMessagesProj/src/main/res/drawable-xxhdpi/notification.webp`

`NotificationsController`, `FilesMigrationService`, and `call_custom_notification_icon.xml` still reference `@drawable/notification`. No Java change.
