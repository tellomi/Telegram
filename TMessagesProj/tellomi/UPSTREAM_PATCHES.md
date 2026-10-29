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

## Login country and Chinese langpack

The decision logic is the `tellomi` flavor class `TMessagesProj/tellomi/java/org/telegram/messenger/TellomiDefaults.java`. It is not in `src/main`. `TMessagesProj/build.gradle` adds `tellomi/java` to `sourceSets.tellomi` next to `tellomi/res`.

| File | Change |
| --- | --- |
| `TMessagesProj/build.gradle` | `sourceSets.tellomi.java.srcDirs` includes `tellomi/java`. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` | Phone screen no longer reads the SIM country or calls `help.getNearestDc`, and it does not use `Locale.getDefault().getCountry()` to pick a calling code. After `countries.txt` loads it selects ISO `CN` / calling code `86`. The code field and the country list stay editable, including a later manual choice. The old `setCountry` helper was only used by that detection and compared the display name to the ISO code, so it could not apply a detected country; it is removed with the call sites. `languageMap` was only there for that helper. The separate AllowFillNumber path can still copy a SIM line number into the fields after the user grants that permission. |
| `TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java` | If no language is saved yet, a system locale of Simplified Chinese (`zh`, `zh-Hans`, `zh-CN`, `zh-SG`, …) or Traditional Chinese (`zh-Hant`, `zh-TW`, `zh-HK`, `zh-MO`) selects remote langpack `zh-hans` or `zh-hant`. The same hook runs from `onDeviceConfigurationChange` while `languageOverride` is unset, including stepping off that pack when the system language is no longer Chinese. A language already stored in the `language` preference is left alone. Once `langpack.getLangPack` returns, the existing `saveRemoteLocaleStrings` path writes that preference, so the choice then sticks until the user picks another language in settings. `getStringV2` and `formatPluralStringComma` read the downloaded pack for those two codes even when `TELLOMI_USE_CLOUD_STRINGS` is false, so other languages stay on the bundled strings. `initConnection.lang_pack` is already `android`, which is the platform those packs are published for. |

## Application id

`APP_PACKAGE` is `app.tellomi.chat`. These DrKLO files hardcode that id (account type, contact MIME types, and the sync target package) and have to move with it. Provider authorities in `AndroidManifest.xml` stay `${applicationId}` and follow the Gradle property. `google-services.json` clients are `app.tellomi.chat`, `app.tellomi.chat.beta`, and `app.tellomi.chat.web`.

## QR mark

The center of a QR is the Tellomi mark, not the paper plane. New assets live in the `tellomi` flavor (`TMessagesProj/tellomi/res`). `src/main/res/raw/qr_logo.svg` is unchanged; the flavor file replaces it at merge time.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/res/raw/qr_logo.svg` | Dark circle and white mark. `TelegramQRCodeWriter` draws `R.raw.qr_logo`. |
| `TMessagesProj/tellomi/res/drawable/tellomi_qr_badge.xml` | Same badge, used by the QR bottom sheet. |
| `TMessagesProj/tellomi/res/drawable/tellomi_qr_mark.xml` | White mark drawn inside the colored circle on the QR share card. |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/QRCodeBottomSheet.java` | `setCenterAnimation` shows `tellomi_qr_badge` instead of `R.raw.qr_code_logo`. |
| `TMessagesProj/src/main/java/org/telegram/ui/QrShare/QrShareView.java` | The card draws `tellomi_qr_mark` instead of `R.raw.plane_logo_plain`. |

## Link hosts

Public links are generated and shown as `tell.cc` (`BuildVars.LINK_HOST`). `t.me`, `telegram.me`, and `telegram.dog` still parse. Help, privacy, and terms are `BuildVars.HELP_URL`, `PRIVACY_URL`, and `TOS_URL` on `tellomi.cn`. `TellomiBrand.rewrite` applies that to strings, so locale files stay DrKLO's. `core.telegram.org` and `sms@telegram.org` are left alone.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java` | `SITE_HOST`, `HELP_URL`, `PRIVACY_URL`, `TOS_URL`. |
| `TMessagesProj/src/main/java/org/telegram/messenger/TellomiBrand.java` | Rewrites faq, privacy, and tos URLs first, then `telegram.org` / `t.me` / `telegram.me` / `telegram.dog` when they are the link host. |
| `TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java` | Saved-messages manage URL goes through `TellomiBrand.rewrite`. |
| `TMessagesProj/src/main/java/org/telegram/messenger/AppGlobalConfig.java` | Stars learn-more default goes through `TellomiBrand.rewrite`. |
| `TMessagesProj/src/main/java/org/telegram/ui/ThemePreviewActivity.java` | Theme-preview sample link. |
| `TMessagesProj/src/main/java/org/telegram/ui/Cells/ThemePreviewMessagesCell.java` | Preview link and webpage URL. |
| `TMessagesProj/src/main/java/org/telegram/ui/PassportActivity.java` | Deactivate link. |

## Launcher icon

The default launcher is the Tellomi flavor `mipmap/ic_launcher` (adaptive icon, monochrome, legacy pngs, and `drawable-*/notification.webp` under `TMessagesProj/tellomi/res`). `config/*/AndroidManifest.xml` still points at `@mipmap/ic_launcher`; the flavor wins at merge. Alternate aliases (`VintageIcon` and the rest) stay upstream.

Checked against `docs/brand/logo.svg` from Telegram-iOS `tellomi-brand-assets` @ `181d02d`. The foreground, monochrome, and notification marks already used that path data, centered on the painted bounds. The launcher ground was `#FFFFFF` / `#111113`; the official app-icon ground is `#17171A`, so those bitmaps and color resources were regenerated. The QR badge was viewBox-centered; it was regenerated from the same paths, centered on the painted bounds.

## CI APK

GitHub Actions only. A local `./gradlew :TMessagesProj_App:assembleAfatDebug` does not set these.

| File | Change |
| --- | --- |
| `TMessagesProj/build.gradle` | `-PTELLOMI_PREBUILT_JNI=<dir>` skips CMake and packages `<dir>/<abi>/libtmessages.49.so`. Unset, the CMake build is unchanged. |
| `TMessagesProj/jni/CMakeLists.txt` | Includes `TMessagesProj/tellomi/ci/native-ci.cmake` only when `TELLOMI_CI_NATIVE` is set (ccache and ninja job pools). |
