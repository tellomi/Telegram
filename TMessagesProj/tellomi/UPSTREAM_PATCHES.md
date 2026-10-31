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

Public links are generated and shown as `tell.cc` (`BuildVars.LINK_HOST`). `t.me`, `telegram.me`, and `telegram.dog` still parse. Help, privacy, and terms are `BuildVars.HELP_URL`, `PRIVACY_URL`, and `TOS_URL` on `tellomi.cn`. `TellomiBrand.rewrite` applies the same mapping to any string that still contains an old host, including a downloaded langpack. Bundled `values*/strings.xml` copies were rewritten too. Kept for parsing only: `Browser` prefixes, `BuildVars.isLegacyLinkHost`, and the internal `tg://telegram.org` stand-in used to parse `tg:` URLs. The YouTube referer `messenger.telegram.org` is not shown to the user.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java` | `SITE_HOST`, `HELP_URL`, `PRIVACY_URL`, `TOS_URL`. |
| `TMessagesProj/src/main/java/org/telegram/messenger/TellomiBrand.java` | Rewrites faq, privacy, and tos URLs first, then `telegram.org` / `t.me` / `telegram.me` / `telegram.dog` when they are the link host. |
| `TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java` | Saved-messages manage URL goes through `TellomiBrand.rewrite`. |
| `TMessagesProj/src/main/java/org/telegram/messenger/AppGlobalConfig.java` | Stars learn-more default goes through `TellomiBrand.rewrite`. |
| `TMessagesProj/src/main/java/org/telegram/ui/ThemePreviewActivity.java` | Theme-preview sample link. |
| `TMessagesProj/src/main/java/org/telegram/ui/Cells/ThemePreviewMessagesCell.java` | Preview link and webpage URL. |
| `TMessagesProj/src/main/java/org/telegram/ui/PassportActivity.java` | Deactivate link, and the support mailto. |
| `TMessagesProj/src/main/java/org/telegram/ui/IdenticonActivity.java` | Encryption-key description link matches `BuildVars.LINK_HOST`. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` | Login support mail is `support@tellomi.cn`. |
| `TMessagesProj/src/main/java/org/telegram/ui/ReportBottomSheet.java` | Ad guidelines open `https://tell.cc/guidelines`. |
| `TMessagesProj/src/main/java/org/telegram/ui/WearAuthSheet.java` | Wear login requests `BuildVars.LINK_BASE`. |
| `TMessagesProj/src/main/res/values*/strings.xml` | Bundled copy of every `t.me`, `telegram.me`, `telegram.org`, and `telegram.dog` the UI shows. |

## Username length

Client-side minimum is 3 characters. The old check rejected `length() < 4`, and a server `USERNAME_INVALID` on a 4-character name was shown as too short, so the effective minimum was 5.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/ui/ChangeUsernameActivity.java` | Local check is `length() < 3`. A 3- or 4-character name is no longer labeled too short when the server returns `USERNAME_INVALID`. |
| `TMessagesProj/src/main/java/org/telegram/ui/ChannelCreateActivity.java` | Same for a new channel or group username. |
| `TMessagesProj/src/main/java/org/telegram/ui/ChatEditTypeActivity.java` | Same for an existing channel or group username. |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/CreateBotAlert.java` | Same for a bot username. |
| `TMessagesProj/src/main/res/values*/strings.xml` | `UsernameHelp`, `UsernameInvalidShort`, `UsernameInvalidShortPurchase`, `ChannelUsernameHelp`, `MegaUsernameHelp`, `LinkInvalidShort`, and `LinkInvalidShortMega` say 3 where they said 5. |

## Launcher icon

The default launcher is the Tellomi flavor `mipmap/ic_launcher` (adaptive icon, monochrome, legacy pngs, and `drawable-*/notification.webp` under `TMessagesProj/tellomi/res`). `config/*/AndroidManifest.xml` still points at `@mipmap/ic_launcher`; the flavor wins at merge. Alternate aliases (`VintageIcon` and the rest) stay upstream.

Checked against `docs/brand/logo.svg` from Telegram-iOS `tellomi-brand-assets` @ `181d02d`. The foreground, monochrome, and notification marks already used that path data, centered on the painted bounds. The launcher ground was `#FFFFFF` / `#111113`; the official app-icon ground is `#17171A`, so those bitmaps and color resources were regenerated. The QR badge was viewBox-centered; it was regenerated from the same paths, centered on the painted bounds. The default mark is 77% of the icon frame (`LEGACY_FRACTION` in `render_launcher_icons.py`); adaptive layers use the same optical size inside the 72dp-of-108dp visible region. Alternate icons are the same mark at 75–80%. Filled is a solid bubble at 80%. Classic puts the mark on a round disc. Starry adds a star field. Premium puts the bubble and @ on a gold star. New drawables and mipmaps live in `TMessagesProj/tellomi/res`. These upstream files point at them:

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/ui/LauncherIconController.java` | Picker entries are Default, Water, Sunset, Classic, Classic black, Filled, Filled black, Starry, and Premium. `AquaIcon`, `VintageIcon`, `TurboIcon`, `NoxIcon`, and `PremiumIcon` keep their component names. None of the entries are premium-gated. |
| `TMessagesProj/src/main/AndroidManifest.xml` | New disabled aliases `ClassicIcon`, `ClassicBlackIcon`, and `FilledBlackIcon`. |
| `TMessagesProj/config/release/AndroidManifest_standalone.xml` | Standalone application icon and the five replaced aliases use the Tellomi mipmaps (`ic_launcher`, `icon_2_launcher` … `icon_6_launcher`) instead of the `*_sa` paper-plane bitmaps. |

## CI APK

GitHub Actions only. A local `./gradlew :TMessagesProj_App:assembleAfatDebug` does not set these.

| File | Change |
| --- | --- |
| `TMessagesProj/build.gradle` | `-PTELLOMI_PREBUILT_JNI=<dir>` skips CMake and packages `<dir>/<abi>/libtmessages.49.so`. Unset, the CMake build is unchanged. |
| `TMessagesProj/jni/CMakeLists.txt` | Includes `TMessagesProj/tellomi/ci/native-ci.cmake` only when `TELLOMI_CI_NATIVE` is set (ccache and ninja job pools). |
| `.github/workflows/android-apk.yml` | Package job passes `-PAPP_VERSION_CODE`. Gradle sources and `gradle.properties` stay as they are. |

`TMessagesProj_App/build.gradle` sets the afat manifest `versionCode` to `APP_VERSION_CODE * 10 + 9`. Checked-in `APP_VERSION_CODE` is 7237, so that code is 72379. Release `v13.1.1-72379-ea5aa5b` shipped 72379, and Android will not install another build with the same code over it.

The package job (debug or release) passes `-PAPP_VERSION_CODE=$((7238 + github.run_number))`. Gradle command-line `-P` overrides `gradle.properties` for that invocation. The afat APK's `versionCode` is therefore `72389 + github.run_number * 10`. 72389 is the next code above 72379 whose ones digit is still 9 (this tree treats 9 as the universal APK; 1 and 2 mean a store build). `github.run_number` increases on every run of Android APK, including pushes, pull requests, and manual runs, so each packaged APK is a higher code than the last. The job reads the code back with `aapt dump badging` and fails if it is not that value. A local `./gradlew` without `-PAPP_VERSION_CODE` still packages 72379.

## Sign-up agreement checkbox

No in-app terms popup (the server returns `help.termsOfServiceUpdateEmpty` and `popup=false`). The sign-up (name) screen's terms line becomes `☐ 我已阅读并同意《用户服务协议》和《隐私政策》`; the links open https://tellomi.cn/legal/terms/ and https://tellomi.cn/legal/privacy/, tapping the rest toggles the box, and Continue does nothing (the line shakes) until it is checked.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/ui/TellomiSignUpAgreement.java` | New (Tellomi file in `src/main` so every flavor compiles). Builds the checkbox text and state. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` `LoginActivityRegisterView` | One field `tellomiAgreement`; constructor calls `tellomiAgreement.install(privacyView)` after the stock terms text; `onNextPressed` returns early unless `tellomiAgreement.checkAgreed()`. The two links open in the system browser (`Browser.openUrlInSystemBrowser`), not an in-app page. |

## Emoji blocklist

Client-side list. The built-in default is the Taiwan flag (U+1F1F9 U+1F1FC). `help.getAppConfig` field `emoji_blocklist` (array of emoji strings or codepoint sequences such as `U+1F1F9 U+1F1FC` / `1F1F9-1F1FC`) is merged on top of that default. An absent or empty field leaves the default in place. A server entry cannot remove a built-in one.

`EmojiData.data` is not rewritten. Sprite indexes and already-sent messages still draw the emoji. `EmojiData.dataColored` is what the panel and the sticker-set emoji grid read; blocked entries are removed there at init, and again when a server list changes the set.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/messenger/EmojiBlocklist.java` | New (tellomi flavor). Default set, `isBlocked`, `mergeServerList`, and the picker-section filter. |
| `TMessagesProj/src/main/java/org/telegram/messenger/EmojiData.java` | After `dataColored` null sections are filled from `data`, `EmojiBlocklist.installPickerSections` drops blocked emoji from the picker copy. |
| `TMessagesProj/src/main/java/org/telegram/messenger/Emoji.java` | `addRecentEmoji` ignores a blocked emoji. `sortEmoji` drops any that were already saved. `applyBlocklistToRecent` runs after a server list changes the set. |
| `TMessagesProj/src/main/java/org/telegram/messenger/MediaDataController.java` | `getEmojiSuggestions` drops blocked keyword hits (panel search and reaction search, including 台湾 / Taiwan / TW). Enabled, recent, top, and saved reaction lists skip a blocked emoji reaction. The full reaction map used to draw a reaction already on a message is unchanged. |
| `TMessagesProj/src/main/java/org/telegram/messenger/AppGlobalConfig.java` | `emoji_blocklist` string set. |
| `TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java` | After `config.load` and inside `applyAppConfig`, merge that set into `EmojiBlocklist`. |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/ReactionsContainerLayout.java` | The reaction popup list drops a blocked emoji before it is shown. |
| `TMessagesProj/src/main/java/org/telegram/ui/SelectAnimatedEmojiDialog.java` | Pasting a blocked emoji into reaction search does not offer it as an available reaction. The standard emoji grid reads `dataColored`. |
| `TMessagesProj/src/main/java/org/telegram/ui/GroupCallActivity.java` | The random call reel reads `dataColored` instead of the sprite sheet. | |

## Login screens and LOC_ERR:null

`LocaleController.getString(int)` passes a null key. Flavor strings in `tellomi/res` are not packed into `localization_*.bin` (`TelegramStringsTask` only reads `src/main/res`). A miss therefore rendered as the literal `LOC_ERR:null`. That string was the email link under the phone field (`TellomiLoginWithEmail`, color `windowBackgroundWhiteBlueText4`), including while `auth.sendCode` progress was on the arrow button. The FAB spinner is `needShowProgress` for that RPC. It hides when the callback runs. A spinner that never stops means the RPC has not completed (the server has not answered) or the response could not be parsed. A parse failure used to return before the callback, so the button kept spinning and no dialog was shown. Unknown `auth.sendCode` errors were also dropped: `AlertsCreator.processError` has no branch for that request.

There is no phone-password sign-in. Login is `auth.sendCode` (SMS or email). The cloud password screen is only the existing 2FA step after a code (`SESSION_PASSWORD_NEEDED`). The product「密码登录」entry is not shown.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java` | After the bin lookup, `getStringV2` reads `Resources.getString` for a real resource id. A null key no longer becomes `LOC_ERR:null`; the fallback is `ErrorOccurred` or "An error occurred." |
| `TMessagesProj/src/main/java/org/telegram/tgnet/ConnectionsManager.java` | A response that fails to deserialize is delivered as `TL_error` `-500` `RESPONSE_DESERIALIZE_FAILED` instead of dropping the callback. |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/OutlineTextContainerView.java` | `setTellomiFilled` draws a gray rounded field and skips the floating label. |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/SlideView.java` | `onTellomiProgress` so a page can spin its own button. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiLoginChrome.java` | Pink action button (`#FF4B6A`) and the gray field helper. |
| `TMessagesProj/tellomi/res/values/strings.xml`, `values-zh/strings.xml` | Login, email, and OTP copy. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` | Phone home: title「登录发现更多精彩」, `+86` in a gray field (country card hidden, code still editable),「邮箱登录」link, checkbox opening `https://tellomi.cn/legal/terms/` and `…/privacy/`, pink「验证并登录」. No confirm sheet, no in-app terms popup, no Apple/Google row, no「密码登录」. Email home:「请输入邮箱地址」and the auto-register subtitle. OTP (SMS and email):「请输入验证码」,「验证码已发送至 …」, digit boxes, pink「登录」,「收不到验证码？」. Nickname sign-up uses the same button and field, last name hidden, no password. A failed or empty `auth.sendCode` shows `ErrorOccurred` plus the server text when there is one. |

## Email OTP login

Login home stays phone, calling code `86`. A text button switches to email and back. Email is a code only: there is no password field and no Google button on that path. `auth.sendCode` puts the address in `phone_number`. `auth.sentCodeTypeEmailCode` reuses `LoginActivityEmailCodeView`. An unregistered address comes back as `auth.authorizationSignUpRequired`, then the existing name screen (nickname only, last name hidden) calls `auth.signUp` with that same address. Nothing on this path reads the SIM or asks for phone permissions. The in-app terms popup is forced off; the agreement checkbox from the section above is the gate, and its links stay `https://tellomi.cn/legal/terms/` and `https://tellomi.cn/legal/privacy/`.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiEmailLogin.java` | New (tellomi flavor). Validates the address, stamps it on the auth bundle, and fills a missing email pattern or code length. |
| `TMessagesProj/tellomi/res/values/strings.xml`, `values-zh/strings.xml` | Switch labels, email field, invalid/banned email, nickname. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` `PhoneView` | Email field and switch, login mode only. `onNextPressed` sends `auth.sendCode` with the email and skips the phone confirmation and SIM permission prompts. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` `fillNextCodeParams` | Email-start `sentCodeTypeEmailCode` keeps Google sign-in off and uses the typed address when the server pattern is empty. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` `LoginActivityEmailCodeView` | Same screen as a phone account's email code. Email-start hides "can't access this email" and carries the address through resend into sign-up. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` `LoginActivityRegisterView` | Email sign-up shows one nickname, puts the address in `auth.signUp.phone_number`, and does not show the terms popup. |
