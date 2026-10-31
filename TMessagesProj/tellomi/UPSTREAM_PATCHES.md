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

The server terms popup stays off (`popup=false`). The sign-up screen uses the same checkbox and「用户协议及隐私政策」modal as login. Links open https://tellomi.cn/legal/terms/ and https://tellomi.cn/legal/privacy/ in the system browser. Agree checks the box and continues.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/ui/TellomiSignUpAgreement.java` | Checkbox, modal, and the legal links. Lives in `src/main` so every flavor compiles. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` `LoginActivityRegisterView` | `tellomiAgreement.install(privacyView)` replaces the stock terms line. Continue while unchecked opens the modal. |

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

Owner mockups `login-ui-ref/owner-2026-10-31` are the layout. Brand color is not the pink in those shots. A ready CTA is `#111111` with white text. An empty CTA is `#E5E5E5` with gray text. Links are `#3478F6`. Errors stay red. A checked agreement box is black.

Login is still `auth.sendCode` (SMS or email). The phone and email password screens are in the layout. This client has no `auth.*` password-login RPC, so a filled password form shows「账号或密码错误，请重试。」. Apple, Google,「其他登录方式」, and「找回账号」toast「暂未开放」.「邮箱登录」opens the email screen. The cloud password screen is still the 2FA step after a code (`SESSION_PASSWORD_NEEDED`).

The agreement checkbox is on every login and sign-up screen. Submitting while it is clear opens「用户协议及隐私政策」. Agree checks the box and continues. Disagree only dismisses. Phone home includes「运营商服务协议」(`https://tellomi.cn/legal`). Email and password screens link only terms and privacy (`https://tellomi.cn/legal/terms/`, `https://tellomi.cn/legal/privacy/`). Links open in the system browser. The line is a `SpannableString`, so the circle and the links receive the tap.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java` | After the bin lookup, `getStringV2` reads `Resources.getString` for a real resource id. A null key no longer becomes `LOC_ERR:null`; the fallback is `ErrorOccurred` or "An error occurred." |
| `TMessagesProj/src/main/java/org/telegram/tgnet/ConnectionsManager.java` | A response that fails to deserialize is delivered as `TL_error` `-500` `RESPONSE_DESERIALIZE_FAILED` instead of dropping the callback. |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/OutlineTextContainerView.java` | `setTellomiFilled` draws a gray rounded field. Focus stroke is `#111111`. |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/SlideView.java` | `onTellomiProgress` so a page can spin its own button. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiLoginChrome.java` | CTA `#111111` / disabled `#E5E5E5`, social rows, circle buttons, swap link, sent-code pill,「暂未开放」toast. |
| `TMessagesProj/tellomi/res/drawable/tellomi_ic_*.xml` | Swap, Apple, mail, more, and eye icons for this flow. |
| `TMessagesProj/src/main/java/org/telegram/ui/CodeFieldContainer.java` | `setTellomiSquares` draws login OTP boxes as filled gray squares with a black focus stroke. The count follows the server code length (the mock shows 4; a 5-digit server code still fits). |
| `TMessagesProj/src/main/java/org/telegram/ui/TellomiSignUpAgreement.java` | Black checkbox. Agreement modal. Terms, privacy, and carrier links. |
| `TMessagesProj/tellomi/res/values/strings.xml`, `values-zh/strings.xml` | Login, password, email, OTP, and agreement copy. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` | Phone home:「登录发现更多精彩」, `+86`,「密码登录」, full-width Apple / Google / 邮箱 / 其他 rows,「找回账号」, bottom carrier checkbox. Phone password and email password: back arrow, password field (eye on the left for phone, right for email), circle icons. Email code entry:「请输入邮箱地址」. SMS OTP: square boxes,「收不到验证码？」, resend countdown,「验证码发送成功」pill. Email OTP: single field,「输入验证码」,「邮件已发送至 …」, red「请输入正确的验证码」. Nickname sign-up uses the same CTA and checkbox. |

## Email OTP login

Login home stays phone, calling code `86`.「邮箱登录」opens the email screen. `auth.sendCode` puts the address in `phone_number`. `auth.sentCodeTypeEmailCode` reuses `LoginActivityEmailCodeView`, which for an email-start login shows one field instead of digit boxes. An unregistered address comes back as `auth.authorizationSignUpRequired`, then the name screen (nickname only, last name hidden) calls `auth.signUp` with that same address. Nothing on this path reads the SIM or asks for phone permissions.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiEmailLogin.java` | Validates the address, stamps it on the auth bundle, and fills a missing email pattern or code length. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` `PhoneView` | Email field, password field, and the screen switch. `onNextPressed` sends `auth.sendCode` with the email and skips the phone confirmation and SIM permission prompts. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` `fillNextCodeParams` | Email-start `sentCodeTypeEmailCode` keeps Google sign-in off and uses the typed address when the server pattern is empty. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` `LoginActivityEmailCodeView` | Email-start uses one numeric field,「邮件已发送至」, and the red wrong-code line. A phone account's email code still uses the digit boxes. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` `LoginActivityRegisterView` | Email sign-up shows one nickname, puts the address in `auth.signUp.phone_number`, and gates on the agreement checkbox and modal. |

## Agreement taps, login language, and intro

The agreement line is a `SpannableString`. A `SpannableStringBuilder` is `Editable`, and `TextView.setText` then installs an editor that consumes the touch before the circle and the legal links run. Phone home links 运营商服务协议 to `https://tellomi.cn/legal`. Terms stay `https://tellomi.cn/legal/terms/` and privacy stays `https://tellomi.cn/legal/privacy/`.

Login copy in `values/strings.xml` is English. `values-zh` is the Chinese copy. `LocaleController` selects `zh-hans` for a mainland Chinese system (`zh`, `zh-Hans`, `zh-CN`, `zh-SG`) until the user picks another language, and that sets the resource locale, so these screens follow it. `TellomiDefaults.uiIsChinese()` picks the composed agreement sentence.

The intro carousel reads `localization_en.bin` by resource id, which wins over `values-zh`. On a Chinese UI it reads the bundled `TellomiIntro*` strings instead. Other languages stay on the bin. No `versionName` or `versionCode` change.

Checked and left alone: no user-facing `Teamgram` or `@teamgram` string in login, auth, session, or profile UI (the word is only in docs, the server PEM comment, and a native comment). Last-online still goes through `LocaleController.formatUserStatus` / `formatDateOnline` (`formatterYear` is `dd.MM.yy`). Status `0` is "a long time ago", and the negative sentinels are "recently" / "within a week" / "within a month". Nothing formats a year as `year - 2000`, so a zero status does not render `01/01/-30`.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/ui/TellomiSignUpAgreement.java` | `SpannableString` so taps reach the circle and the links. Phone home links 运营商服务协议 to `https://tellomi.cn/legal`. Chinese vs English follows `TellomiDefaults.uiIsChinese()`. The unchecked submit still opens the agreement modal. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` | The sign-up terms view is not given an `Editable` buffer before the checkbox replaces it. |
| `TMessagesProj/tellomi/java/org/telegram/messenger/TellomiDefaults.java` | `uiIsChinese()` and `chineseResource()` for bundled Simplified Chinese that must not come from the English bin. |
| `TMessagesProj/tellomi/res/values/strings.xml` | English login copy, and English intro fallbacks. |
| `TMessagesProj/tellomi/res/values-zh/strings.xml` | Chinese login copy and the bundled intro carousel. |
| `TMessagesProj/src/main/java/org/telegram/ui/IntroActivity.java` | When the UI language is Chinese, titles, messages, and Start use `TellomiIntro*`. |
