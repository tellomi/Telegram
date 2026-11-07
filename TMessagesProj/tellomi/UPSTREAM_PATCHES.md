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

Public usernames are at least 4 characters, the same minimum teamgram #182 accepts. Upstream rejected `length() < 4` locally and also labeled a 4-character name too short when the server returned `USERNAME_INVALID`, so the effective minimum was 5. A 4-character name now passes the local check. A shorter name is still too short. Theme links and sticker-pack links keep their own 5-character check.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/messenger/TellomiUsername.java` | `MIN_LENGTH` is 4. `isTooShort` is the shared check. |
| `TMessagesProj/src/main/java/org/telegram/ui/ChangeUsernameActivity.java` | The local check and the `USERNAME_INVALID` / purchase short copy use `TellomiUsername.isTooShort`. |
| `TMessagesProj/src/main/java/org/telegram/ui/ChannelCreateActivity.java` | Same for a new channel or group public link. |
| `TMessagesProj/src/main/java/org/telegram/ui/ChatEditTypeActivity.java` | Same for an existing channel or group public link. |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/CreateBotAlert.java` | Same for a bot username. |
| `TMessagesProj/src/main/res/values*/strings.xml` | `UsernameHelp`, `UsernameInvalidShort`, `UsernameInvalidShortPurchase`, `ChannelUsernameHelp`, `MegaUsernameHelp`, `LinkInvalidShort`, and `LinkInvalidShortMega` say 4 where they said 5. Bundled copies that had already been edited to 3 say 4 as well. |

## Launcher icon

The default launcher is the Tellomi flavor `mipmap/ic_launcher` (adaptive icon, monochrome, legacy pngs, and `drawable-*/notification.webp` under `TMessagesProj/tellomi/res`). `config/*/AndroidManifest.xml` still points at `@mipmap/ic_launcher`; the flavor wins at merge. Alternate aliases (`VintageIcon` and the rest) stay upstream.

Checked against `docs/brand/logo.svg` from Telegram-iOS `tellomi-brand-assets` @ `181d02d`. The foreground, monochrome, and notification marks already used that path data, centered on the painted bounds. The launcher ground was `#FFFFFF` / `#111113`; the official app-icon ground is `#17171A`, so those bitmaps and color resources were regenerated. The QR badge was viewBox-centered; it was regenerated from the same paths, centered on the painted bounds. The default mark is 77% of the icon frame (`LEGACY_FRACTION` in `render_launcher_icons.py`); adaptive layers use the same optical size inside the 72dp-of-108dp visible region. Alternate icons are the same mark at 75–80%. Filled is a solid bubble at 80%. Classic puts the mark on a round disc. Starry adds a star field. Premium puts the bubble and @ on a gold star. New drawables and mipmaps live in `TMessagesProj/tellomi/res`. These upstream files point at them:

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/ui/LauncherIconController.java` | Picker entries are Default, Water, Sunset, Classic, Classic black, Filled, Filled black, Starry, and Premium. `AquaIcon`, `VintageIcon`, `TurboIcon`, `NoxIcon`, and `PremiumIcon` keep their component names. None of the entries are premium-gated. |
| `TMessagesProj/src/main/AndroidManifest.xml` | New disabled aliases `ClassicIcon`, `ClassicBlackIcon`, and `FilledBlackIcon`. |
| `TMessagesProj/config/release/AndroidManifest_standalone.xml` | Standalone application icon and the five replaced aliases use the Tellomi mipmaps (`ic_launcher`, `icon_2_launcher` … `icon_6_launcher`) instead of the `*_sa` paper-plane bitmaps. |

## Settings icon plates

List icons that used Telegram's colored rounded plates are black, white, or gray. `IconBackgroundColors` holds the neutral pairs. `SettingCell.Background.setColor` also runs `TellomiIconColors.plate`, so a hardcoded blue, green, orange, red, or purple passed from a settings row is drawn as a dark gray. A color that is already neutral, and a fully transparent color, is left as-is. Charts, gifts, and avatar gradients are not remapped.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/IconBackgroundColors.java` | Named plate colors are gray. Enum names are unchanged. |
| `TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java` | `SettingCell.Background.setColor` calls `TellomiIconColors.plate`. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiIconColors.java` | The plate remap. Flavor source only. |

## CI APK

GitHub Actions only. A local `./gradlew :TMessagesProj_App:assembleAfatDebug` does not set these. Ordinary push and pull_request runs compile native code, Java, and resources. They do not install the release keystore or upload an installable APK. Run workflow, a `v*` tag, and `release/android` still package.

| File | Change |
| --- | --- |
| `TMessagesProj/build.gradle` | `-PTELLOMI_PREBUILT_JNI=<dir>` skips CMake and packages `<dir>/<abi>/libtmessages.49.so`. Unset, the CMake build is unchanged. |
| `TMessagesProj/jni/CMakeLists.txt` | Includes `TMessagesProj/tellomi/ci/native-ci.cmake` only when `TELLOMI_CI_NATIVE` is set (ccache and ninja job pools). |
| `gradle.properties` | `APP_VERSION_CODE=180004`. `APP_VERSION_NAME` stays `14.0.4`. |
| `.github/workflows/android-apk.yml` | Package job passes `-PAPP_VERSION_CODE` above that checked-in value. The afat floor is website `latest.json` 178001. |

`TMessagesProj_App/build.gradle` (and the other application modules) set the manifest `versionCode` to `APP_VERSION_CODE * 10 + abiVersionCode`. The ones digit is the ABI marker: 9 is the universal afat APK, 1 and 2 are a store or bundle build. Checked-in `APP_VERSION_CODE` is 180004, so afat is 1800049 and the store/bundle digit is 1800041. Digit 0 would be 1800040. All of those are above website-prod `latest.json` versionCode 178001. `APP_VERSION_NAME` stays 14.0.4. 180004 is that marketing version's era in this number space (above 178001). It is not a website 0.1.x code.

An earlier package formula used checked-in 7237 (afat 72379) and CI `72389 + github.run_number * 10`. A 14.0.4 CI APK landed near 73269. Android will not install that over a device whose versionCode is already 178001, so a later 官网 or in-app APK could not replace those installs. `latest.json` is not edited here.

The package job (debug or release) passes `-PAPP_VERSION_CODE=$((180005 + github.run_number))`. Gradle command-line `-P` overrides `gradle.properties` for that invocation. The afat APK's `versionCode` is therefore `1800059 + github.run_number * 10`. 1800059 is the next code above the checked-in afat 1800049 whose ones digit is still 9. `github.run_number` increases on every run of Android APK, including pushes, pull requests, and manual runs, so each packaged APK is a higher code than the last. The job rejects a code that is not above 178001, whose ones digit is not 9, or whose ABI digit 0 (`APP_VERSION_CODE * 10`) is not above 178001. It reads the code back with `aapt dump badging` and fails if it is not the computed value. A local `./gradlew` without `-PAPP_VERSION_CODE` still packages 1800049.

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

Login is still `auth.sendCode` (SMS or email). The phone and email password screens are in the layout. This client has no `auth.*` password-login RPC, so 登录 on those screens toasts「暂未开放」, the same as Apple, Google,「其他登录方式」, and「找回账号」. It does not show a fake「账号或密码错误」.「邮箱登录」opens the email screen. The cloud password screen is still the 2FA step after a code (`SESSION_PASSWORD_NEEDED`).

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
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` | Phone home:「登录发现更多精彩」, `+86`,「密码登录」, full-width Apple / Google / 邮箱 / 其他 rows,「找回账号」, bottom carrier checkbox. Phone password and email password: back arrow, password field (eye on the left for phone, right for email), circle icons. 登录 on those screens toasts「暂未开放」. Email code entry:「请输入邮箱地址」. SMS OTP: square boxes,「收不到验证码？」, resend countdown,「验证码发送成功」pill. Email OTP: single field,「输入验证码」,「邮件已发送至 …」, red「请输入正确的验证码」. Nickname sign-up uses the same CTA and checkbox. |

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

## Auth chrome, OTP home, add account, logout (owner 14.0.1)

Welcome, login, and OTP stayed English on a Chinese phone because a later configuration callback copied the activity locale (English) over `currentLocale` after `zh-hans` was chosen. Settings Chinese worked only after the user saved a language. Built-in flavor strings now win over the English bin when the device locale is Chinese and no language is saved. The welcome screen has one button,「开始使用」.「使用英文继续」is not shown.

The first successful code no longer stacks the main UI on top of the welcome screen. That leftover intro is what Add Account opened when only one account existed, and it has no back. Add Account removes any intro still in the stack and opens the login flow for a free slot. If the welcome screen is showing while an account is already signed in, it leaves for the main UI.

Signing in an account that is already on the device switches to that account and shows「该账号已登录」instead of keeping a second session. Email used at sign-in is remembered for the account page and the switcher. The account page shows the phone when there is one, then the email, and「绑定手机号」when the phone is empty (existing +86 change-number OTP, button「绑定」). The switcher label is the phone, otherwise `a***@x.com`. Logout confirms immediately for email and phone sessions.

Login controls follow the light palette (`#111111` / `#E5E5E5` / `#3478F6`) and flip the primary button and field text in dark mode so they stay distinct from the page.

Deferred: a full profile layout for email, binding a phone as a separate product screen beyond the existing change-number OTP, and locales other than zh/en.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java` | Device locale comes from `Resources.getSystem()`, not the locale the app already installed. Staying on `zh-hans` no longer lets the activity configuration replace `currentLocale`. `getStringV2` prefers a real `values-zh` string over the English bin. |
| `TMessagesProj/src/main/java/org/telegram/ui/IntroActivity.java` | Hides「Continue in English」. One start button. If an account is already signed in, the welcome screen leaves for the main UI and back is not a trap. |
| `TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java` | `showMainTabsReplacingStack` drops the welcome screen after sign-in. An empty selected slot does not open the intro when another account is live. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` | OTP success replaces the stack with the main UI. An already-signed-in user id switches instead of duplicating. Bind-phone uses the +86 code flow with「绑定」. Login chrome colors follow night mode. |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/OutlineTextContainerView.java` | Filled-field stroke uses the theme-aware focus color. |
| `TMessagesProj/src/main/java/org/telegram/ui/CodeFieldContainer.java` | OTP squares and the digits inside them follow the same light/dark contrast. |
| `TMessagesProj/src/main/java/org/telegram/ui/TellomiSignUpAgreement.java` | Agreement card and the checked box stay visible in dark mode. |
| `TMessagesProj/src/main/java/org/telegram/ui/UserInfoActivity.java` | Account page: phone, then email, then「绑定手机号」. Logout confirms immediately. |
| `TMessagesProj/src/main/java/org/telegram/ui/LogoutActivity.java` | Logout no longer waits on a wallet fetch that did not finish for email sessions. |
| `TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java` | Logout still clears local state if the wallet wipe throws, and forgets a saved sign-in email. |
| `TMessagesProj/src/main/java/org/telegram/ui/MainTabsActivity.java` | Add Account goes through the shared helper. Switcher label is phone or a masked email. |
| `TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java` | Same switcher label. |
| `TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java` | Header subtitle and account row use the same label. |
| `TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java` | Settings search「add account」uses the same helper. |
| `TMessagesProj/tellomi/java/org/telegram/messenger/TellomiDefaults.java` | Device locale and bundled zh/en resource lookup. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiAccount.java` | New. Saved email, masked label, already-on-device match, Add Account routing. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiLoginChrome.java` | Light palette kept. Dark mode uses a white primary button and light field text. |
| `TMessagesProj/tellomi/res/values/strings.xml`, `values-zh/strings.xml` |「开始使用」, bind-phone copy,「该账号已登录」. |

## Login locale, welcome mark, Help, bind (14.0.3)

v14.0.2 release datacenter selection is not changed here. That pack's `release` build type still inherits `local` (`10.0.2.2:10443`) unless another change points it at `8.137.39.82:10443`.

Welcome page 1 used `R.drawable.telegram_logo` as an image span, so the title stayed the Telegram wordmark, and the intro circle was `ThemeColors.TELEGRAM_COLOR` plus `intro_tg_plane`. The circle is now `#17171A` with the Tellomi mark. The title is the bundled Tellomi string.

Login copy stayed English while the agreement line was Chinese because that line is composed in code and the other labels come from `LocaleController`. `getStringV2` now uses a `values-zh` / `values-zh-rCN` / `values-b+zh+Hans` string whenever the UI is Chinese and that string differs from English. 简体中文 and 繁體中文 are built-in language rows. `applyLanguage` sets the locale list, not only the deprecated `Configuration.locale` field.

The proxy shield is the upstream top-right control. Help is Tellomi and was given the same corner, so the two drew on top of each other. Help moves left while the shield is visible.

`auth.sendCode` had no client deadline. If the request never returns, the button clears after 20 seconds and shows an error. A response that is not `auth.authorization` is not cast. Phone OTP no longer runs the paper-plane progress animation after that art is hidden, which skipped `needHideProgress`. A wrong code is a line under the button. The calling code opens `CountrySelectActivity` and is not typed digit by digit. Password eyes sit on the right. Phone accounts get a bind-email row; email accounts get change-email through `LoginActivity.changeEmail`. Both phone rows use the upstream change-number intro.

`APP_VERSION_NAME` is `14.0.3`.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/ui/IntroActivity.java` | Page-1 title is text. Intro circle and mark are Tellomi. |
| `TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java` | Built-in Chinese rows. Locale list on apply. Chinese flavor strings replace the English bin. |
| `TMessagesProj/tellomi/java/org/telegram/messenger/TellomiDefaults.java` | Built-in locale, device-locale fallback, Chinese resource qualifiers. |
| `TMessagesProj/tellomi/res/values-zh-rCN/strings.xml`, `values-b+zh+Hans/strings.xml` | Same Chinese copy as `values-zh`. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` | Help beside the proxy. sendCode timeout. Country list. OTP error line. Password eye. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiLoginChrome.java` | Social icons use center-inside. |
| `TMessagesProj/src/main/java/org/telegram/ui/UserInfoActivity.java` | Bind phone uses the change-number intro. Bind or change email uses `changeEmail`. |
| `gradle.properties` | `APP_VERSION_NAME=14.0.3`. |

## Chat list brand, bundled Simplified Chinese, service avatar (14.0.3)

The chat-list title is not the `AppName` string. `DialogsActivity` and `DialogStoriesCell` draw `R.drawable.telegram_logo_2`, a vector that spells Telegram, and tint it blue. `AppName` is already Tellomi. The flavor file replaces that drawable (and the unused `telegram_logo`) with a Tellomi wordmark. The stories header slot was a fixed 90dp, which was the width of the old wordmark; it now wraps the new one so the star sits against the word. Launcher mipmaps, notification art, QR mark, and intro circle were already the Tellomi mark. Other user-visible "Telegram" copy still goes through `TellomiBrand.rewrite`. No `BuildConfig` or datacenter change.

Simplified Chinese did not apply. Built-in `zh_hans` sets `currentLocale` to `zh` (so `formatterWeek` prints 周六) but `TelegramStringsTask` only packs `src/main/res/values-*/strings.xml`. There was no `values-zh` there, so `LocalizationUtils` had no `localization_zh.bin` and every string stayed on `localization_en.bin`. Flavor `values-zh` is login copy only and is not an input to that task. Picking 简体中文 in settings used the same built-in row (`pathToFile` null), so the switch changed dates and not labels. A later `langpack.getLanguages` result could also mark that row remote; with no file downloaded, the UI never left the English bin.

`src/main/res/values-zh/strings.xml` is the official zh-hans Android export, filtered to keys this tree already has. The packer emits `localization_zh.bin`. System Chinese and the 简体中文 row both apply `Locale("zh")` and read that bin. The built-in row is not replaced by a remote pack. Clock patterns for Chinese use the Chinese locale so 上午/下午 is not printed as `AM`. Flavor login strings still win when they differ from English. 繁體中文 uses this same simplified pack.

Service users (`777000`, `333000`, `42777`) draw `tellomi_qr_mark` on `#17171A` instead of the first letter of the name. `ImageReceiver` does not replace that with a missing server photo. A channel named "tellomi" with no photo is a normal peer and still uses a letter avatar.

The bind-email and login-email rows used `msg2_email.webp`, an opaque bitmap in a 24dp slot. They use `tellomi_ic_email`, a white envelope vector. `TextCell` tints it; the account row shows it white on the blue tile.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/res/drawable/telegram_logo_2.xml`, `telegram_logo.xml` | Tellomi wordmark. White fill, tinted by the title color. |
| `TMessagesProj/src/main/java/org/telegram/ui/Stories/DialogStoriesCell.java` | Logo slot wraps the wordmark. |
| `TMessagesProj/src/main/res/values-zh/strings.xml` | Bundled Simplified Chinese. Packed as `localization_zh.bin`. |
| `TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java` | Keep bundled Chinese off the remote-pack path. Chinese clock locale. |
| `TMessagesProj/tellomi/java/org/telegram/messenger/TellomiDefaults.java` | `bundledChinese`. |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/AvatarDrawable.java` | Service avatar is the Tellomi mark. |
| `TMessagesProj/src/main/java/org/telegram/messenger/ImageReceiver.java` | Service peers skip the photo fetch. |
| `TMessagesProj/tellomi/res/drawable/tellomi_ic_email.xml` | Envelope glyph. |
| `TMessagesProj/src/main/java/org/telegram/ui/UserInfoActivity.java` | Bind and change email use that glyph. |
| `TMessagesProj/src/main/java/org/telegram/ui/PrivacySettingsActivity.java` | Login-email row uses the same glyph. |

## Splash mark and system Chinese on first launch

The Android 12 splash (`Theme.TMessages.Start` → `@drawable/tg_splash_320`) was still the upstream paper-plane animation, so a fresh launch flashed the Telegram logo before the welcome screen. The flavor drawable of the same name is the Tellomi mark, ink only: `#111113` in light and `#FFFFFF` in `drawable-night`. It does not paint a disc. The start theme keeps the upstream window background (white / `#1f2732`). `src/main` keeps the upstream file. The welcome bubble is unchanged. There is no `ic_splash` resource in this tree; the theme already names `tg_splash_320`.

Welcome stayed English (“The world's fastest…”, “Get started”) on a Chinese phone even after `localization_zh.bin` existed. `onDeviceConfigurationChange` passed the activity locale into `applyTellomiSystemLocale`. That locale is English (and on API 24+ `Configuration.locale` is often null) after `zh-hans` is installed. `zh-hans` is a `useCloudStrings` locale, so the callback treated that as “system is no longer Chinese” and applied English. The welcome screen then read the English flavor `TellomiIntro*` strings. The callback now uses `TellomiDefaults.deviceLocale()`. A flavor lookup that is stuck on the English default falls through to the stock key in `localization_zh.bin`. `resourceFor` builds a fresh configuration so a zh lookup is not a copy of the activity locale.

A few client strings were missing from `values-zh` (`Chats`, `Files`, `Voice`, typing and call counts, the internal notification channel). The channel name was hardcoded English.

No `versionName`, `versionCode`, or datacenter change.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/res/drawable/tg_splash_320.xml` | Light splash mark, ink `#111113`. Replaces the paper plane at merge. |
| `TMessagesProj/tellomi/res/drawable-night/tg_splash_320.xml` | Night splash mark, ink `#FFFFFF`. |
| `TMessagesProj/tellomi/res/drawable-*/book_logo.webp` | Pinned-shortcut badge. Replaces the paper plane. |
| `TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java` | Configuration changes keep the device Chinese pack. |
| `TMessagesProj/tellomi/java/org/telegram/messenger/TellomiDefaults.java` | Locale lookup uses a fresh configuration. |
| `TMessagesProj/src/main/java/org/telegram/ui/IntroActivity.java` | Chinese welcome falls back to `localization_zh.bin`. |
| `TMessagesProj/src/main/res/values/strings.xml`, `values-zh/strings.xml` | Internal-notification name, and the zh keys that were absent. |
| `TMessagesProj/src/main/java/org/telegram/messenger/NotificationsController.java` | Internal channel uses that string. |

## Login language, keyboard, code boxes, back, bind (owner Honor)

`localeFilters` keeps only the dummy locale `zz`, so `values-zh` never ships in the APK. Upstream copy still comes from `localization_zh.bin`. Tellomi strings lived only in `tellomi/res/values-zh`, which that filter drops, and `getString(int)` looked them up with a null key so the bin missed them too. A Chinese phone therefore showed the English flavor strings ("Log in to see more", "Bind phone number", "Carrier agreement") next to Chinese sentences that are written in code. The string task now packs `tellomi/res/values/strings.xml` into `localization_en.bin` and `tellomi/res/values-zh/strings.xml` into `localization_zh.bin`. `getStringV2` resolves a null key to the resource name before the bin lookup. The same gap covered the intro lines; those now read the bin.

The phone form was a vertical layout with center gravity inside a slot that shrinks when the keyboard opens. The column was taller than the slot, so the top slid under the close button and Help. Login mode keeps the header fixed and scrolls the form inside the page. The code boxes were a fixed 52dp, so six of them ran past the right edge. They now shrink to the field width.

Back on the code screen opened the edit-number dialog and returned false, so the arrow did nothing useful, and `logininfo2` kept that code page. Force-close or Add account reopened it, including a previous number and `phone_code_hash`. Back and close now cancel that code, clear the saved code page, and return to the phone or email form. A relaunch does not restore a code page. Each `auth.sendCode` takes a ticket; a late response for an older number is ignored, and the code screen shows the number from that submit.

Apple, Google, other methods, recover, and password login open a card (title, one sentence, OK) with the same copy as the iOS text alert, instead of the "Not available yet" toast.

Settings > Account draws the phone row with the upstream `settings_calls` icon. An empty phone is "绑定手机号" / "绑定后可使用手机号登录", which fits the two-line cell.

| File | Change |
| --- | --- |
| `buildSrc/src/main/kotlin/org/telegram/plugin/TelegramBuildAppPlugin.kt` | Flavor login strings are inputs of the localization bins. |
| `TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java` | A null string key is the resource name, so the bin hit wins over the stripped `values-zh`. |
| `TMessagesProj/src/main/java/org/telegram/ui/IntroActivity.java` | Chinese intro lines come from the bin. |
| `TMessagesProj/tellomi/res/values/strings.xml`, `values-zh/strings.xml`, `values-zh-rCN/strings.xml`, `values-b+zh+Hans/strings.xml` | Unavailable-dialog copy and the shorter bind-phone subtitle. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiLoginChrome.java` | Unavailable methods use the card dialog. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiPendingAuth.java` | Submit ticket. A cleared or replaced ticket cannot paint an older number. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiCodeBoxes.java` | Box size that fits the row. |
| `TMessagesProj/src/main/java/org/telegram/ui/CodeFieldContainer.java` | Login code boxes use that size. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` | Scrolling phone form, code-page back, no restored code page, submit ticket. |
| `TMessagesProj/src/main/java/org/telegram/ui/UserInfoActivity.java` | Phone row uses `settings_calls`. |
| `TMessagesProj/src/testTellomi/java/org/telegram/ui/TellomiLoginFixesTest.java` | Ticket, box width, and Chinese string coverage. |

## Login language, sent-code copy, duplicate session, sendCode wait

The top-right Help control on the login screens is a language button. It shows the current built-in name plus ▾ (`简体中文 ▾`, `繁體中文 ▾`, `English ▾`). The card lists those three rows, marks the current one, and Cancel. There is no "Other" row and no `langpack.getStrings` round-trip: `LaunchActivity.showLanguageAlertInternal` loads those strings from the network and its Other row opens `LanguageSelectActivity`, which is not usable before login. The row still applies with `LocaleController.applyLanguage(info, true, false, false, true, account, null)`, the same call the alert's OK button uses, so the choice is stored in the `language` preference and the bundled bins apply with no network. `reloadInterface` does not rebuild the top fragment, so this screen is rebuilt after the apply. The phone, email, password, and code the user already typed are kept in memory for that rebuild. They are not written into `logininfo2`, so a killed process still does not restore a code page.

`values-zh-rTW` is not added. `localeFilters` is `zz`, so a region qualifier never ships in the APK. `LocalizationUtils` only maps language tags that have no hyphen, and both Chinese rows use `localization_zh.bin` (simplified). Choosing 繁體中文 stores `zh_hant` and the button shows 繁體中文. The sentences stay on that simplified pack, which is the same fallback this tree already uses when the server has no `zh-hant` langpack.

Help is no longer the top-right control. `收不到验证码？` opens a card whose Help row opens `BuildVars.HELP_URL` (`https://tellomi.cn/help`, no trailing slash). The email code screen has the same row. An app code (`auth.sentCodeTypeApp`) no longer says the SMS was sent. The line is「验证码已发送到你在其他设备上登录的 Tellomi」, and「改用短信接收」on that screen calls `auth.resendCode`. SMS, call, and email keep their own lines.

Signing in an account that is already on the device used to `ConnectionsManager.cleanup(true)` on the new authorization and leave it on the server. `auth.logOut` is sent on that session first. The local keys are reset when that RPC finishes.

`auth.sendCode` had a 20-second client timeout that cancelled the request and showed an error while the server could still send the SMS. The next tap sent another code. The wait now stays until the server answers. Back, or a tap on the spinning button, cancels that wait and does not show an error.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` | Language button. Sent-code copy. `auth.logOut` before discarding a duplicate login. No 20-second sendCode timeout. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiLanguageSheet.java` | New. Offline language card. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiSentCode.java` | New. App / SMS / call / email kind, and the button label. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiLoginChrome.java` | Code-not-received card. Help opens `HELP_URL`. The busy button stays tappable so it can cancel. |
| `TMessagesProj/tellomi/res/values/strings.xml`, `values-zh/strings.xml`, `values-zh-rCN/strings.xml`, `values-b+zh+Hans/strings.xml` | Language title, app/call sent-code lines,「改用短信接收」. |
| `TMessagesProj/src/testTellomi/java/org/telegram/ui/TellomiLoginFixesTest.java` | Label, sent-code kind, Chinese copy, logout, and the removed timeout. |

## Map apps for mainland China

Tapping a location used to stop in `AndroidUtilities.isMapsInstalled`. That method checks `com.google.android.apps.maps` and, when the package is missing, shows "Install Google Maps?" (`InstallGoogleMaps` via `GoogleMapsProvider.getInstallMapsString`). The alert is built there, not in `AlertsCreator`. `AlertsCreator.showSecretLocationAlert` is only the secret-chat static-preview picker (Telegram / Google / Yandex).

Tellomi no longer asks for the Google Maps package. A point or a route opens a bottom sheet of the map apps that are installed. Coordinates stay WGS-84. Each app gets its documented type flag (`dev=1`, `coord_type=wgs84`, `coord_type=1`) instead of a second GCJ-02 conversion. If nothing is installed, the browser opens the Amap marker page `https://uri.amap.com/marker` with `coordinate=gps` (longitude first). There is no `values-zh-rTW`; 繁體中文 keeps using the simplified pack.

The in-app map is unchanged. See the note under the table.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiMaps.java` | New. Detects 高德 `com.autonavi.minimap`, 百度 `com.baidu.BaiduMap`, 腾讯 `com.tencent.map`, Google `com.google.android.apps.maps`, and any other `geo:` handler. Sheet lists them. Point and route URIs include the place name. Web fallback is the Amap marker. |
| `TMessagesProj/src/main/java/org/telegram/messenger/AndroidUtilities.java` | `isMapsInstalled` no longer shows the Google Maps install dialog. It returns true so a location picker is not blocked on that package. |
| `TMessagesProj/src/main/AndroidManifest.xml` | `<queries>` for the three China packages, `geo`, `androidamap`, `baidumap`, and `qqmap`, so Android 11+ can see them. |
| `TMessagesProj/src/main/res/values/strings.xml`, `values-zh/strings.xml` | Chooser title, the four app names, and "My location". Packed into `localization_en.bin` / `localization_zh.bin`. |
| `TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java` | A static pin or a poll place opens the chooser when the in-app map cannot draw. A live location still opens `LocationActivity`. |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java` | Same for a poll place in shared media. |
| `TMessagesProj/src/main/java/org/telegram/ui/ChannelAdminLogActivity.java` | Same for a location in the admin log. |
| `TMessagesProj/src/main/java/org/telegram/ui/LocationActivity.java` | Open in maps and Get directions use the chooser. The place name is the venue title or the address. |
| `TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java` | A business location opens the chooser. The in-app screen is used only when that map can draw. |
| `TMessagesProj/src/main/java/org/telegram/ui/Business/ProfileLocationCell.java` | The external open uses the chooser. |
| `TMessagesProj/src/main/java/org/telegram/ui/ArticleViewer.java` | An article map opens the chooser. |
| `TMessagesProj/src/main/java/org/telegram/messenger/RichMessageLayout.java` | A rich-message map opens the chooser. |

In-app preview, not changed here. `ApplicationLoader.onCreateMapsProvider` returns `GoogleMapsProvider` (`com.google.android.gms.maps`). `LocationActivity` and `ChatAttachAlertLocationLayout` draw that SDK. Without Play services the view stays on `MapPlaceholderDrawable` (the preload thread already swallows the init failure). This tree has no OSM `IMapsProvider`. The Huawei app module is the only other provider (`HuaweiMapsProvider` when Play services are absent). Upstream DrKLO later plugs OSM in through the same `IMapsProvider`; that swap is a separate change. The bubble thumbnail is not the SDK: `WebFile.createWithGeoPoint` asks the server (`mapProvider` 2, `static_maps_provider=telegram`), or the client downloads a Yandex or Google static image when the server config says so (`AndroidUtilities.formapMapUrl`). Default before config loads is `mapProvider` 0, which is the Google static URL.

## Email is the first login page

After 开始使用, `LoginActivity` opens `VIEW_TELLOMI_EMAIL_CODE` (`TellomiAuthPage`) instead of the phone form. 「使用手机号登录」calls `setPage(VIEW_PHONE_INPUT)` with the same 300ms slide as the other login steps. Back on the phone page slides back to email. Back on the email page closes login. A code started from email returns to the email page. Black/white buttons and the shared agreement line are unchanged.

## Login motion matches Telegram

The three-language card was hardcoded in `TellomiLanguageSheet.choices()`. It was not a `langpack.getLanguages` result. The picker now lists every pack this tree ships strings for: en, it, es, de, nl, ar, pt_br, ko, uk, ru, zh_hans, zh_hant. Those bins come from `values/` and `values-ar|de|es|it|ko|nl|pt-rBR|ru|uk|zh`. zh_hant still reads `localization_zh.bin` (simplified). `langpack.getLanguages`, `langpack.getLangPack`, and `langpack.getDifference` in `LocaleController` are sent with `RequestFlagWithoutLogin` (and `RequestFlagFailOnServerErrors`). `langpack.getStrings` and `langpack.getLanguage` in `LaunchActivity` use the same flag. tgnet parks any request that lacks it while `currentUserId` is 0, so a signed-out client can fetch the catalog. A failed `getLanguages` clears `loadingRemoteLanguages` so the login picker can retry. Rows the server returns are appended under the bundled packs when `NotificationCenter.suggestedLangpack` fires. Packs the server does not publish still cannot be translated.

`auth.sendCode`, `auth.resendCode`, and the other login RPCs that call `needShowProgress` arm a 20 second watch. On timeout the request is cancelled, the spinner stops, and `TellomiAuthTimeout` is shown. A second tap on the spinning button still cancels quietly. `-1000` and an empty error text on those paths show the same error instead of leaving the button spinning.

Password login, email code login, and email password login are `TellomiAuthPage` slide views. `LoginActivity.setPage` moves between them (300ms, `AccelerateDecelerateInterpolator`, full display width). The phone form no longer swaps those fields in place. Country selection is still `presentFragment(CountrySelectActivity)`. The language control is `msg2_language` and opens `TellomiLanguageSelectActivity` with the same search field and `TextRadioCell` row as `LanguageSelectActivity`.

`setIntroView` runs Telegram's intro morph again (`onCustomTransitionAnimation`). Code pages stay `GONE` and alpha 0 until `setPage` shows them, so they do not flash under that fade. The welcome button and the login floating button use the black/white CTA (`TellomiLoginChrome.ctaFill` / `ctaLabel`). Every login and sign-up agreement line is `TellomiAgreementPrefix` + terms + `TellomiAgreementAnd` + privacy (`已阅读并同意《用户协议》和《隐私政策》`).

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` | Send-code watch. Separate slide pages for password and email. Language icon opens the picker. Intro morph restored. Code pages stay hidden until `setPage`. Floating button is black/white. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiAuthPage.java` | New. Phone password, email code, and email password pages. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiLanguageSelectActivity.java` | New. Searchable bundled language list, `TextRadioCell`. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiLanguageSheet.java` | Bundled code list replaces the three hardcoded rows. |
| `TMessagesProj/src/main/java/org/telegram/ui/TellomiSignUpAgreement.java` | One agreement sentence on every login and sign-up screen. |
| `TMessagesProj/src/main/java/org/telegram/ui/IntroActivity.java` | Start button is black with white text, inverted in dark mode. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiLoginChrome.java` | Spinner on the black button uses the label color. |

## Hidden Telegram features

Teamgram does not implement AI compose, translation, Premium, Stars, gifts, the TON wallet, Fragment, channel ads, Business, Boosts, Passport, or AI summaries. Stories stay. `TellomiFeatures` is the single gate. Official appConfig keys are `premium_purchase_blocked` (missing value stays blocked), `stars_purchase_blocked` (already blocked by default), and `wallet_available` (missing value is now off, including debug builds). Tellomi keys default to false when the server omits them: `tellomi_ai_compose_enabled`, `tellomi_translate_enabled`, `tellomi_gifts_enabled`, `tellomi_ton_enabled`, `tellomi_fragment_enabled`, `tellomi_channel_revenue_enabled`, `tellomi_business_enabled`, `tellomi_boosts_enabled`, `tellomi_passport_enabled`, `tellomi_ai_summary_enabled`.

AI compose: `showAiButton` in the chat field, attach alert, and photo caption, plus the rich-editor buttons, return without showing. `AIEditorAlert.show` and `AiStyleAlert.show` return immediately, which covers the eight constructor call sites. Translation and Cocoon: `tellomi_translate_enabled` is required by `isTranslationsManualEnabled`, `isTranslationsAutoEnabled`, `isTranslatable`, the chat translate bar, and `TranslateButton.showCocoonAlert`. Premium, Stars, TON, and wallet screens return false from `onFragmentCreate`. Settings and the profile menu omit those rows and do not call `getBalance` while they are off. Gifts: profile tab, pinned gifts, and the input gift button. Boosts and channel revenue: stats tabs, the chat boost menu, sponsored messages, and search ads. Passport: privacy row, `tg://passport`, and the external auth activity. The passport recovery dialog no longer opens `telegram.org/deactivate`. Saved-messages reply-bot URL is `BuildVars.LINK_BASE`. Stars learn-more default is empty. Fragment username, phone, gift, and top-up clicks run only when `tellomi_fragment_enabled` is true. `nft.fragment.com` host checks in `TL_wallet` stay; they classify server payloads and are not links the user opens.
| `TMessagesProj/tellomi/res/values/strings.xml`, `values-zh/strings.xml`, `values-zh-rCN/strings.xml`, `values-b+zh+Hans/strings.xml` | Shared agreement sentence. |

## Default chat wallpaper

Superseded by "Telegram theme colors, chat themes, and wallpapers" below. The stock chat background is no longer the flat color described here.

The stock chat background is a flat color. Light (Blue, accent 99) is `#F2F2F2`. Dark (Dark Blue, accent 0) is `#000000`. `createDefaultWallpaper()` no longer builds the four-stop green gradient or draws `R.raw.default_pattern`. `createBackgroundDrawable` takes that path for the stock day and night themes before the accent gradient is painted.

A saved wallpaper whose slug is not `d` is left as the user set it. Other built-in accents keep the colors stored on the accent. Their pattern files are Telegram cloud slugs (`account.getMultiWallPapers`); this server does not have them. When that file is missing the client used to paint and cache `R.raw.default_pattern`. It no longer does. The accent gradient stays; the Telegram pattern does not.

The default accent colors are unchanged. Blue's home accent is `0xFF5890C5` and Dark Blue's home accent is `0xFF3685FA`. `fillAccentColors` recolors the palette by hue. Black and white have no hue, so pointing the default accent at them would flatten links, checks, and bubbles. That swap is not in this change.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/ui/ActionBar/Theme.java` | Solid default wallpaper. No `default_pattern` fallback. Stock Blue / Dark Blue skip the accent gradient. Theme-list preview of the Blue default is `#F2F2F2`. |
| `TMessagesProj/src/main/java/org/telegram/ui/WallpapersListActivity.java` | The "default" tile is that solid color, not the green gradient at intensity 34. |
| `TMessagesProj/src/main/java/org/telegram/ui/Cells/WallpaperCell.java` | The default tile is a solid swatch. It does not draw `default_pattern`. |
| `TMessagesProj/src/main/java/org/telegram/ui/ThemePreviewActivity.java` | Previewing the default slug does not overlay `default_pattern`. |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/ThemeSmallPreviewView.java` | A built-in accent with no downloaded pattern is not filled with `default_pattern`. |
| `TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java` | The default chat-theme stub uses the same light and dark solids. |

## MingCute icons, phase 1

Phase 1 swaps the icons on the bottom tabs, the chat input, the chat list and its swipe actions, message menus (`msg_*` / `menu_*` used there), the action bar, and profile/settings rows. The art is [MingCute](https://github.com/Richard9394/MingCute) (Apache-2.0; the tree now lives at `mingcute-design/mingcute-icons`). `TMessagesProj/tellomi/icons/mingcute/LICENSE` and `NOTICE` are the attribution. This client has no in-app open-source licenses screen.

Replacements sit in the `tellomi` resource flavor under the same resource name. A drawable that already ships as a density bitmap is a white-on-transparent WebP at that bitmap's pixel size, so the existing tint still applies. A drawable that is only a vector stays a vector. Density bitmaps are not turned into VectorDrawables. `tellomi/icons/mapping.csv` is the sheet (`telegram_resource`, `mingcute_name`, `style` line or fill, `kind`). `tellomi/icons/generate_icons.py` rebuilds it from a MingCute `packages/svg` checkout (`MINGCUTE_SVG`).

332 resource names are replaced (333 rows: `chats_archive` is both the bitmap and the swipe Lottie). Of those, 240 are rasters, 70 are vectors, 15 are one-frame Lotties standing in for swipe/archive raw files, and 8 are the new tab line/fill bitmaps.

The four main tabs no longer play `R.raw.tab_*`. `GlassTabView` crossfades `tab_chats` / `tab_contacts` / `tab_calls` / `tab_settings` with the matching `_fill` bitmap (scale plus alpha). The emoji, sticker, keyboard, GIF, mic, and camera button uses the same static glyphs with a short scale crossfade instead of the morph Lottie.

`send_outline` is still MingCute `send`. `send_plane_24` is MingCute `arrow-up` (the `arrow-left` glyph turned to point up). The new send disk is `key_chat_messagePanelSend`: black by day, white at night. The arrow is the opposite color.

`msg_inputarrow` is a white chevron the login country row tints, so it is MingCute `arrow-right`. The other login-only icons stay: `tellomi_ic_*` bake `#111111`, `#C7C7CC`, or `#3478F6` and nothing tints them, and `login_arrow1` / `login_phone1` are 128px missed-call drawings.

Left for later (`tellomi/icons/remaining.txt`, plus everything outside this phase):

- Login-only icons left as they are: `tellomi_ic_*`, `login_arrow1`, `login_phone1`. The shared back, close, and overflow glyphs do change on the login screens because they are the action-bar icons.
- Brand marks and the notification silhouette: `telegram_logo*`, `ic_gram`, `outline_gram_24`, `settings_gram_24`, `dialog_gram_transfer`, `filled_gram_earnings`, `notification.webp`.
- Layered drawings: archive-hint pieces (`chats_archive_arrow`, `_box`, `_muted`, `_pin`), the two-layer calendar (`input_calendar1`, `input_calendar2`), the add-poll pair, the small star pair, `tooltip_arrow`, `redcircle`, `floating_shadow_profile`, `avd_speed`.
- Colored vectors: `settings_wallet`, `settings_wallet_dark`.
- Every other Lottie (attach-sheet tabs, stickers, stories, photo editor, calls) and icons that those screens use but the phase-1 activities do not.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/icons/generate_icons.py` | Builds the overlays from MingCute SVGs. |
| `TMessagesProj/tellomi/icons/mapping.csv` | Resource to MingCute name, line or fill. |
| `TMessagesProj/tellomi/icons/mingcute/` | Apache-2.0 LICENSE, NOTICE, and the SVGs that were used. |
| `TMessagesProj/tellomi/res/` | Same-name WebP, vector, and raw overlays. |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/glass/GlassTabView.java` | Main tabs crossfade line and fill. |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterViewAnimatedIconView.java` | Input glyphs are static, with a short scale crossfade. |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java` | Send glyph is MingCute arrow-up. The disk is `key_chat_messagePanelSend`: black by day, white at night. The arrow is the opposite color. |

## Monochrome default theme

Superseded by "Telegram theme colors, chat themes, and wallpapers" below. The built-in palette is Telegram's again.

The default accent (id 99) does not pass through `fillAccentColors`, so the Blue and Dark Blue `.attheme` files and `ThemeColors` defaults are the colors the app actually draws. Day accent is `#111111`. Night accent is white. Outgoing bubbles are `#E0E0E0` on the `#F2F2F2` wallpaper and `#2C2C2E` on black, not Telegram green `#EFFFDE`. Checks, links, switches, checkboxes, the compose FAB, and unread badges follow the same black/white pair. `TELEGRAM_COLOR` is `#111111`. The home accent of Dark Blue and Night no longer paints a blue outgoing gradient. Theme settings lists only Blue and Dark Blue, without the accent swatches. The other built-in theme files are gray too, in case a saved choice still points at them.

The send control is `tellomi/res/drawable/send_plane_24.xml`, an arrow-up, on that disk. `notification.webp` in the flavor is a white Tellomi mark (speech bubble) for the status bar, at mdpi through xxxhdpi.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/assets/bluebubbles.attheme`, `darkblue.attheme`, `day.attheme`, `night.attheme`, `arctic.attheme` | Brand blue and green keys become black, white, or gray. |
| `TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ThemeColors.java` | `TELEGRAM_COLOR` and the fallback bubble, check, link, switch, and FAB colors. |
| `TMessagesProj/src/main/java/org/telegram/ui/ActionBar/Theme.java` | Default accent swatch, preview bubble, and the night outgoing gradient. |
| `TMessagesProj/src/main/java/org/telegram/ui/ThemeActivity.java` | Built-in list is Blue and Dark Blue. Accent swatches are not shown. |
| `TMessagesProj/tellomi/res/drawable/send_plane_24.xml` | Arrow-up instead of the paper plane. |
| `TMessagesProj/tellomi/res/drawable-*/notification.webp` | White Tellomi silhouette. |

## Login screens match the 2026-11-03 spec

The first page is the phone form (`PhoneView`). Email entry is the other mode of that form. SMS stays `LoginActivitySmsView`. The email code stays `LoginActivityEmailCodeView`. `auth.sendCode` is unchanged.

Entries with no backend are not shown: Apple, Google, other methods, 找回账号, and password login. The phone page keeps the number field, 验证并登录, the 或 divider, 邮箱登录, and the agreement row. The email page has no password link and no round icon row. Back on email returns to the phone page.

The phone and email pages do not open a keyboard until the field is tapped. A tap on empty space closes it. The phone field uses the numeric keypad, including after returning from email. Light mode is a white page and a black button. Dark mode follows the active theme: black page, white button. Switching language rebuilds the screen and rewrites every label, including 或.

The numeric keypad used to cover the phone form. On a short window and in landscape that hid the button and the agreement, so the page now stays above the keypad and scrolls. Agreement text is 已阅读并同意 用户协议 和 隐私政策, with no carrier line. SMS boxes use the server length. A wrong SMS code stays in the boxes and the line under them is 验证码错误，请重新输入. The primary button shows ••• while the request is in flight. The top-right control is still the language globe.

## Login layout fit

Phone, email, SMS, email-code, and sign-up scroll inside the page. A wide window (tablet or an unfolded foldable) caps the column at 420dp. System font scale is applied up to 1.6 and the field, button, and code digit grow with it; the digit stays inside a square that had to shrink. Agreement, code-options, and language cards scroll and stay inside the window, including above the navigation bar. Colors are unchanged.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiLoginFit.java` | Width, font, dialog, and digit sizes. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiFormHost.java` | Scrolling column for the code and sign-up pages. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiLoginChrome.java` | Capped text, taller fields, scrolling cards. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiAuthPage.java` | Same scroll and type size. |
| `TMessagesProj/src/main/java/org/telegram/ui/TellomiSignUpAgreement.java` | The modal scrolls. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiLanguageSheet.java` | The card scrolls. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` | Login pages scroll. The keypad no longer covers the phone form. |
| `TMessagesProj/src/main/java/org/telegram/ui/CodeFieldContainer.java` | Login digits shrink to the square. |

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` | Phone home and email entry only. Keypad overlay, clear buttons, code error line. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiAuthPage.java` | Password page, password link, and round icons removed. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiLoginChrome.java` | ••• busy state. No unavailable card and no login sheet. |
| `TMessagesProj/tellomi/res/values/strings.xml` and `values-zh*` | Agreement sentence without the carrier line. SMS error copy. |

## Send glyph, suggested folders, back arrow

The chat field and the media-preview send button already draw `send_plane_24` (arrow-up). The other send controls were still a paper plane: MingCute `send` on `attach_send`, `msg_send`, `msg_sendfile`, and `send_outline`, and the upstream `ic_send` bitmap in the group-call composer. Those are the same arrow-up, white so the existing tint applies. `attach_send` keeps its larger canvas and draws a 24dp arrow in the middle, because that bitmap is shown with `CENTER` inside the round button.

`messages.getSuggestedDialogFilters` returns the folder title and description as server text. On this server that text is English (Unread, Personal, Groups, Channels, Bots). `FiltersSetupActivity` maps those five presets, by flags or by that English copy, onto bundled strings before the row is shown and before the folder is saved. A suggestion that names specific chats is not rewritten.

`msg_arrow_back` had been generated from MingCute `arrow-right`, so menu Back rows pointed the wrong way. It is arrow-left again. `ic_ab_back` and `msg_arrow_back` are scaled so the stroke is about as tall as the upstream back arrow (the first MingCute fit was shorter than the action-bar icons around it). Attach-sheet gallery and location tabs are the same 24dp Lottie slot as the other attach tabs; their artwork fills the canvas about as much as Files, so those two were not enlarged. Launcher icons, the Tellomi wordmark, and night-theme color code are unchanged.

`TellomiBrand.rewrite` already turns the word Telegram in `TelegramVersion` and `TelegramPremium` into the app name when the string is loaded. Those rows were not edited. Premium, gifts, and the wallet stay behind `TellomiFeatures`.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/res/drawable-*/{attach_send,msg_send,msg_sendfile,ic_send,ic_ab_back,msg_arrow_back}.webp` | Arrow-up send glyphs. Back arrows point left and are slightly larger. |
| `TMessagesProj/tellomi/res/drawable/send_outline.xml` | Ephemeral send outline is the arrow, not the plane. |
| `TMessagesProj/src/main/java/org/telegram/ui/FiltersSetupActivity.java` | Localize the five suggested folders. |
| `TMessagesProj/src/main/res/values/strings.xml`, `values-zh/strings.xml` | Suggested-folder name and descriptions. |
| `TMessagesProj/tellomi/icons/mapping.csv`, `generate_icons.py` | Do not regenerate these glyphs from MingCute `send`. |

## Transcribe button script glyphs

The voice-to-text button is still the upstream 20-frame trim morph (`transcribe_in` / `transcribe_out`): an arrow plus a capital A that becomes the collapse chevron. Latin, Cyrillic, and Greek keep that A. Chinese, Japanese, Korean, Arabic, Persian, Hebrew, Hindi, and Thai replace only the A strokes (groups 3 and 4) with 文, あ, 가, ع, ا, א, अ, and ก. Same layer name (`Artboard Outlines`), so `setLayerColor` still paints the icon black or white. The new files live in the tellomi raw flavor. `TranscribeButton` picks the suffix from `LocaleController` (`zh`, `zh-Hans`, `zh_TW`, `ja`, `ko`, `ar`, `fa`, `he`/`iw`, `hi`, `th`) and reloads the drawables when the cell rebinds or the next draw sees a language change. The glyphs are not mirrored in RTL.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/TranscribeButton.java` | Language suffix and drawable reload. |
| `TMessagesProj/tellomi/res/raw/transcribe_in_*.json`, `transcribe_out_*.json` | A-stroke replacements. Upstream `transcribe_in.json` / `transcribe_out.json` are unchanged. |

## Soft update on the shipped App module

`:TMessagesProj_App:assembleAfatRelease` is the APK that ships. Its `ApplicationLoaderImpl` only used to override `onGetApplicationId`, so `isStandalone()` stayed false and `LaunchActivity.checkAppUpdate` returned before `help.getAppUpdate`. Flipping `isStandalone()` would also change billing, premium links, and the SMS-job build, so the App module overrides `isInAppUpdatesEnabled()` instead.

`TELLOMI_CHECK_UPDATES` was false, and `checkAppUpdate` returns before the request when that flag is off and the call is not forced. It is now true. `-PTELLOMI_CHECK_UPDATES=false` still skips the automatic check. The delay default is changed in "Faster in-app update checks" below.

A soft update (`can_not_skip=false`) shows `TellomiSoftUpdateDialog`: black and white card, close control, swipe-to-update. Day is a gray card, black type, white track, black thumb. Night keeps the same layout with the dialog background and inverted ink. A completed swipe opens `appUpdate.url` through `Browser.openUrl`. A document with an empty url uses `TellomiApkInstall`. Neither path opens a store listing. Dismissing clears that pending soft update so a later check can show it again. Force updates stay on `BlockingUpdateView`. That button prefers `url`, then the APK when this module can install it. The button label contrasts with `featuredStickers_addButton` so a white night button does not keep white type.

| File | Change |
| --- | --- |
| `TMessagesProj_App/.../ApplicationLoaderImpl.java` | Enables in-app updates and shows the soft-update dialog. APK permission and install delegate to `TellomiApkInstall`. |
| `TMessagesProj/tellomi/java/.../TellomiAppUpdate.java` | Request gate, soft-popup gate, and url-over-document action. |
| `TMessagesProj/tellomi/java/.../TellomiApkInstall.java` | Unknown-sources check and APK intent. |
| `TMessagesProj/tellomi/java/.../TellomiSoftUpdateDialog.java` | Dismissible swipe card. |
| `TMessagesProj/tellomi/res/values/strings.xml` and `values-zh*` | Title, body, slide label, and download label. |
| `TMessagesProj/src/main/java/.../ApplicationLoader.java` | `supportsInAppUpdates()` / `isInAppUpdatesEnabled()`. Default false. |
| `TMessagesProj/src/main/java/.../LaunchActivity.java` | `checkAppUpdate` uses the gate above. Soft popup includes the App module. A saved soft update does not skip the next check on this module, because there is no side-menu banner to fall back on. |
| `TMessagesProj/src/main/java/.../BlockingUpdateView.java` | Force button opens `url` first. App module can install a document. Label contrast. |
| `gradle.properties`, `TMessagesProj/build.gradle` | `TELLOMI_CHECK_UPDATES=true`. Fallback if the property is missing is true. |
| `TMessagesProj/src/test/java/.../TellomiAppUpdateTest.java` | Gate and url-vs-document cases. |

## MTProto server public keys

`TMessagesProj/config/tellomi-server-public.pem` can hold more than one key, newest first. The 2026-11 key is fingerprint `4381149949997195609`. The Teamgram docker key `12240908862933197005` stays as a fallback until the server retires it. Parsing lives in `TellomiNetConfig`. The only upstream edit is the handshake copy.

| File | Change |
| --- | --- |
| `TMessagesProj/jni/tgnet/Handshake.cpp` | On the custom backend, copy every `TellomiNetConfig` public key into `serverPublicKeys` / `serverPublicKeysFingerprints`. The existing match loop still walks the server's fingerprint list first. |

## Login layout, location without Play services, unsupported mark

Login keeps Tellomi copy and the black/white CTA. The name screen uses Telegram's floating Next again (`LoginActivityRegisterView` no longer replaces it with an in-page button or a `TellomiFormHost`). Avatar selection is `ImageUpdater.openMenu` (camera, gallery, crop). The outer login scroller follows Telegram again except on the phone form, which scrolls itself. 2FA is a page that does not have a custom keypad, so the slide is extended by the keypad height the same way as upstream. Closing login while it is the only fragment finishes the activity and the next launch is the welcome screen; switching to an account that is already on the device uses `showMainTabsReplacingStack` / `switchToAccount` instead of `finishFragment`.

Phones without Play services take a fix from `LocationManager` (GPS and network). When `TellomiMaps.canShowInAppMap` is false the Google map view is hidden and the preview is `WebFile.createWithGeoPoint` (map provider 2, the OSM static map). Venue search still runs from that coordinate. If the venue bot cannot be resolved, the loading row stops.

`messageMediaToDo` is layer 232 constructor `0x8a53b014`. A checklist that only fails after a restart is the stored media not deserializing as that object. The unsupported card's plane is the flavor `send_plane_26`, now the white Tellomi mark. Attach gallery and location tabs are the white `tab_gallery` / `tab_location` lotties tinted with `glass_tab*` (`#111111` by day).

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` | Name screen floating Next. 2FA scroll and keypad height. Existing-account handoff does not finish the activity. |
| `TMessagesProj/tellomi/java/.../TellomiFrameworkLocation.java` | Framework GPS/network fix and the OSM static preview. |
| `TMessagesProj/src/main/java/.../ChatAttachAlertLocationLayout.java`, `LocationActivity.java` | Start that fix. Hide the Google map when it cannot draw. Search venues from the fix. |
| `TMessagesProj/src/main/java/.../BaseLocationAdapter.java` | A failed venue-bot lookup ends the loading row. |
| `TMessagesProj/tellomi/res/drawable/send_plane_26.xml` | White Tellomi mark on the unsupported card. |

## Delete account now

Settings → Privacy and Security → Delete my account already set the inactivity TTL. iOS also has a red “Delete Account Now” row under that period. Android now has the same row. It explains that private chats go away for both sides, the account leaves groups and channels, the profile and photos are removed, and messages already sent in groups stay. A second dialog confirms with “Delete my account”. If the account has a cloud password, the existing two-step screen collects it and `account.deleteAccount` is sent with `reason = user_initiated` and `password` set to `InputCheckPasswordSRP` (`account.deleteAccount#a2c0cf74`, flag 0). Without a password the request stays on the legacy constructor `418d4e0b` (reason only), which is what login reset and the terms screen already send. Tellomi’s server accepts both. A correct password deletes immediately; a missing password on a protected account returns `2FA_CONFIRM_WAIT_*` and the account is not logged out. `PASSWORD_HASH_INVALID`, that wait, and `FLOOD_WAIT` use the Tellomi string overlay. Success calls `MessagesController.performLogout(1)`, the same path as Settings → Log out, which returns to the welcome screen.

The password screen does not call `account.getPasswordSettings` for this flow. That check consumes the single-use SRP id, so `account.deleteAccount` would then fail with `SRP_ID_INVALID`.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/ui/PrivacySettingsActivity.java` | Red row under the away period. |
| `TMessagesProj/src/main/java/org/telegram/ui/TwoStepVerificationActivity.java` | Delete-account delegate builds one SRP and does not pre-check it. |
| `TMessagesProj/src/main/java/org/telegram/tgnet/tl/TL_account.java` | Optional password on constructor `a2c0cf74`. Legacy constructor unchanged when password is absent. |
| `TMessagesProj/tellomi/java/.../TellomiDeleteAccount.java` | Confirm, send, errors, logout. |
| `TMessagesProj/tellomi/res/values/strings.xml` and `values-zh*` | English and Chinese copy. |

## DC addresses from help.getConfig (M7 / D-33)

`TELLOMI_LOCK_DC_ADDRESSES` was true, so `help.getConfig` built a `dc_options` map and then skipped `Datacenter::replaceAddresses`. The build-config seed (`TELLOMI_PROFILE_DEV`, `8.137.39.82:10443` for DC 1 and DC 2) and any addresses already stored in `tgnet.dat` stayed until the next app build. Telegram production IPs are still not installed (`TELLOMI_CUSTOM_BACKEND`). DNS bootstrap (`apv3.stel.com`) is still skipped.

The flag now defaults to false. `ConnectionsManager` has one call into `TellomiNetConfig::applyConfigAddresses`. On the custom backend that function keeps a family only when `tellomiAcceptDcAddress` accepts it (`inet_pton` for the family's address type, port 1–65535). A hostname is logged and dropped. If that leaves the family empty, `replaceAddresses` is not called, so the seed or the last good IP remains. An empty `dc_options` list still does nothing (`map.empty()`). The seed is still what `initDatacenters` installs when that DC id is not already saved. A saved id is not rewritten from the seed.

Official Telegram (custom backend off) still replaces all four families, including an empty one. The lock still skips replacement when set back to true.

| File | Change |
| --- | --- |
| `TMessagesProj/jni/tgnet/ConnectionsManager.cpp` | `help.getConfig` calls `TellomiNetConfig::applyConfigAddresses` instead of the locked `replaceAddresses` block. |
| `TMessagesProj/jni/CMakeLists.txt` | Compiles `tgnet/TellomiDcApply.cpp`. |
| `gradle.properties`, `TMessagesProj/build.gradle` | `TELLOMI_LOCK_DC_ADDRESSES` default false. |
| `TMessagesProj/jni/tgnet/TellomiDcAddress.h` | New. `inet_pton` check. Not an upstream file. |
| `TMessagesProj/jni/tgnet/TellomiDcApply.cpp` | Applies the filtered lists. Not an upstream file. |
| `TMessagesProj/tellomi/check_dc_address.cpp` | Host check for the `inet_pton` rule. Not linked into the app. |

## Settings row icons

The MingCute pass matched the substring `settings` to `settings-3`, so the main settings rows all drew one gear. Those rows now use one glyph each, the same meanings as iOS `Tellomi/Icons/mapping.csv` (`Item List/Icons/*`). Stars is the iOS semantic coin, not a MingCute star. The art stays white on the gray plate. `src/main` vectors are unchanged; the `tellomi` flavor overlays them. `settings_arrow` and `settings_gram_24` are unchanged. `settings_wallet` stays the colored upstream vector.

Language is `globe` (`Item List/Icons/Language`), not `translate` (`Item List/Icons/Translation`). Privacy Policy has no iOS row; it uses the shield, distinct from Privacy (`lock`). iOS Proxy is `semantic/proxy` and is not this row.

M6 reads iOS `Tellomi/Icons/mapping.csv` (`Item List/Icons/*`, tellomi/Telegram-iOS @ `6966e7ce`). Three rows still used the previous glyphs: Data is `cylinder`, Devices is `device`, Stars is `semantic/star-coin`. Premium stays the line `star`. The star sits in the coin through the same nested-svg viewport as iOS (`x=4.4`, `y=4.4`, `15.2` inside viewBox `24`). `settings_business` stays the upstream briefcase. `settings_arrow` and `settings_gram_24` are unchanged. No settings-screen Java.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/res/drawable/settings_{account,ask,chat,data,devices,faq,features,folders,gift,language,policy,power,premium,privacy,sounds,stars}.xml` | Flavor vectors. One glyph each. Data, Devices, and Stars were regenerated for M6. |
| `TMessagesProj/tellomi/icons/generate_icons.py` | `SETTINGS_ROWS` wins over the `settings` substring rule. A nested svg viewport becomes a vector group. |
| `TMessagesProj/tellomi/icons/mapping.csv` | Those rows. Data, Devices, and Stars follow the current iOS names. |
| `TMessagesProj/tellomi/icons/mingcute/svg/line/cylinder.svg` | MingCute cylinder for `Item List/Icons/Data`. |
| `TMessagesProj/tellomi/icons/semantic/star-coin.svg` | iOS `semantic/star-coin` for `Item List/Icons/Stars`. Not a MingCute glyph. |
| `TMessagesProj/tellomi/icons/test_settings_rows.py` | Locks the mapping, the distinct files, and the generated vectors. |

## No language chooser after login

Upstream shows "Choose your language" once per `suggestedLangCode`, and the welcome screen writes `language_showed2` when it offers that language. Tellomi's welcome picker applies the language and never writes that key, and a Chinese phone is already on `zh-hans` without a saved choice. The dialog then opens after the code is accepted.

Picking a language on the login screen writes `language_showed2`. The dialog is skipped when a language was saved (`languageOverride`) or the current language already matches the suggestion (`zh_hans` matches `zh-hans` and `zh`; `zh-hant` does not match `zh-hans`). The shared plural `zh` is not treated as a match, or traditional would count as simplified.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiLanguageAlert.java` | New. Records the choice and decides when to skip. |
| `TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java` | `showLanguageAlert` returns when that check says so. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` | The welcome picker records the choice. |
| `TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java` | `hasLanguageOverride`. |

## Downloaded languages and the welcome screen

`TELLOMI_USE_CLOUD_STRINGS` stays false. `langpack.getLangPack` still writes the file, and `getStringV2` now reads it for any remote or unofficial language. Japanese and the other packs are no longer stuck on the English bin. Bundled Chinese still uses `localization_zh.bin`. `TellomiBrand.rewrite` still runs on the string that is shown. The Chinese flavor overlay runs only when the active language is `zh*`, not because the phone is Chinese.

The welcome carousel built its six pages once, while that overlay still followed the phone. Picking Italian left those pages in Chinese. A language change (`reloadInterface`) fills them again. Each page view keeps its index so the text already on screen is replaced.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java` | `cloudStringsEnabled` is true for a remote or unofficial pack. `peekCurrentLocaleInfo` for the overlay check. |
| `TMessagesProj/tellomi/java/org/telegram/messenger/TellomiDefaults.java` | `uiIsChinese` follows the app language. `readDownloadedPack`. |
| `TMessagesProj/src/main/java/org/telegram/ui/IntroActivity.java` | Welcome copy is rebuilt after a language change. |

## Bottom tabs (M2 / D-4, D-5, D-7, D-8, D-36, D-37)

`TellomiTabs` (tellomi flavor) builds the visible list: Discover, Chats, Calls, Contacts, Settings, Profile. Chats and Settings stay. Profile stays last. A fresh install is Discover, Chats, Settings, Profile (`showCallsTab` is already false; `tellomi_show_feed_tab` defaults true; `tellomi_show_contacts_tab` defaults false). The pager opens on Chats. `TellomiTabs.enabled()` is the flag; when it is false the four upstream positions are unchanged. Discover is an empty screen until the feed client (M3). Six tabs use a wider bar so a 360dp screen does not ellipsize the labels. The night theme is unchanged.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/ui/MainTabsActivity.java` | `indexToPosition`, `getFragmentsCount`, `getStartPosition`, `createBaseFragmentAt`, `prepareDialogsActivity`, folder scroll, profile fade, and the calls-tab notification call `TellomiTabs`. `tabsView` and `dialogsActivity` are package-visible so that class can reach them. `install` runs once from `createView` and skips the calls/settings swap. Tab clicks resolve the position when they fire. |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/glass/GlassTabView.java` | `TabAnimation.FEED` and `setLabelHorizontalPadding`. |

## Navigation bar, long-press hide, post links (M2 / D-6, D-38, D-57)

Settings → Navigation Bar sits after Language. Discover, Calls, and Contacts are switches. Chats and Settings are on and cannot be turned off. Calls writes `UserConfig.showCallsTab`, so Settings and the calls list keep the old Show Calls Tab switch. Long-press Discover or Contacts and choose Hide. Calls already had that menu. A hidden tab comes back from Navigation Bar.

`https://tell.cc/m#<id>` turns Discover back on and opens that post. A link opened from a chat removes that chat immediately, with no close animation, so the fragment transition does not defer `callTabsVisibleToggled` or block the post screen. The bar refresh keeps Discover selected (`TellomiTabNavigation.tabToKeep`) even when that notification arrives late. The id is stored with `FeedActivity.setPendingPostId`. While the feed client is off, the empty screen says the post cannot be opened. When it is on, `TellomiDiscoverScreen` presents `TellomiDiscoverPostFragment` and retries for about a second if a transition is still running. `https://tell.cc/m/<slug>` is still a message link. `TellomiTabs.enabled()` false does not consume the post link: `consumeFeedPostLink` returns false and `LaunchActivity` keeps the original parser. `t.me` is not a post host.

New Message is `DialogsActivity.openWriteContacts` (and `open_new_dialog`). It presents its own `ContactsActivity` picker and does not use the contacts tab, so hiding Contacts leaves that list in place. A lookup of the contacts tab does not create a page.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java` | After Language, `TellomiNavigationSettings.appendRow`. One `case` opens the page. |
| `TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java` | At the start of the http(s) branch, `TellomiTabs.consumeFeedPostLink`. False leaves the link alone. |
| `TMessagesProj/src/main/java/org/telegram/ui/MainTabsActivity.java` | `TellomiTabs.appendHideContacts` on the existing contacts long-press. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiTabNavigation.java` | New. Copy, row rules, long-press hide, the post id, and the contacts lookup. Not an upstream file. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiNavigationSettings.java` | New. The Navigation Bar page. Not an upstream file. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiTabs.java` | Writes the three switches, the Discover long-press, and opening a post link. A post link pops back to the tabs without animation and selects Discover. |
| `TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/TellomiDiscoverScreen.java` | Retries presenting the post when a fragment transition is still running. |
| `TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/FeedActivity.java` | Empty copy, and the "can't open yet" line when a post id is set. The live feed (below) replaces that screen only when `tellomi_discover_feed_enabled` is on. |

## Check for updates on the settings screen

A normal account could only start a check from the debug menu (`SettingsActivity` item index 9), and that item is hidden unless the build is standalone, beta, or a private debug build. The menu also passed a null progress, so a response with no newer build said nothing.

Settings now has a row directly above the version footer. It is shown for every account. The tap calls `LaunchActivity.checkAppUpdate(true, progress)`. The request, the soft-update popup, and the force-update screen are unchanged. Progress is a spinner the user can cancel. When the response is not a newer build, the existing bulletin uses `YourVersionIsLatest`: "Already the latest version" / "已是最新版本". The debug menu uses the same tap.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiSettingsUpdate.java` | New. The row stays visible. The debug-menu item stays gated. The tap attaches progress. English and Chinese copy. |
| `TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java` | The row above the version. Both taps call `checkAppUpdate(true, progress)`. |
| `TMessagesProj/tellomi/res/values/strings.xml` and `values-zh*` | `TellomiCheckForUpdates`. `YourVersionIsLatest` overlay. |
| `TMessagesProj/src/test/java/org/telegram/ui/TellomiSettingsUpdateTest.java` | Row visibility, copy, and the existing `help.getAppUpdate` call. |

## Discover feed client (M3 / C-M3b)

The Discover tab stays on the empty screen until `help.getAppConfig` includes `tellomi_discover_feed_enabled`. A missing key is off, including after the key was previously on. An `http` base URL does not turn the client on. While the flag is off, or the account is logged out, the client does not call `discover.getEngineCredential` and does not open an engine URL. The credential is teamgram's `discover.getEngineCredential` (the Signal-Server `GET /v1/tellomi/feed/auth` note in the engine doc is stale). `discover.getAudienceToken` supplies `audience.audience_token` for include and exclude. Tabs come from `GET /v1/feed/config`; an unknown name is ignored. Nearby is `GET /v1/feed/nearby?city=`. The city code is `<iso>:<slug>`. Hong Kong, Macau, and Taiwan use the `cn:` prefix. Only `Address.getLocality()` is kept; `Geocoder` is the lookup when Play services are absent, and a failure stays on the manual list. Location is requested the first time the nearby tab is opened with no city, and only at coarse accuracy. Visibility is `StoryPrivacyBottomSheet`. Login and the session are untouched.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java` | After the emoji-blocklist hook, `TellomiDiscoverFlags.capture` reads `tellomi_discover_feed_enabled` and `tellomi_discover_feed_base_url` from the same `help.getAppConfig` object. |

## Chat wallpaper and accent colors

Superseded by "Telegram theme colors, chat themes, and wallpapers" below. The wallpaper screen is no longer the bundled doodle list.

The server `account.getWallPapers` result stays empty. Wallpapers are bundled. The default chat background is a doodle on a gradient (light `#EFE7DD`, dark `#0B141A` to `#16232B`), not a flat color. The wallpaper screen shows those ten built-in backgrounds plus one solid (`#F0F2F5` / `#101418`). Incoming bubbles are `#FFFFFF` / `#202C33`. On a solid wallpaper the bubble draws a 0.5dp shadow and the date chip is a translucent black. Built-in theme accents are the twelve shared hex values, default `#3390EC`. Pattern art is Tabler and Lucide icons plus Pattern Monster dots and Hero Patterns topography. Settings → Open-source licenses names them. Login and the session are untouched.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiWallpaperCatalog.java` | New. Color table, the ten wallpapers, the solid preset, bubble colors, and the license text. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiWallpaperDraw.java` | New. Builds the pattern drawable from `tellomi/assets/wallpapers`. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiLicensesActivity.java` | New. The open-source license page. |
| `TMessagesProj/tellomi/assets/wallpapers/` | `doodle.png`, `dots.png`, `waves.png`, `WallpaperCatalog.json`, and the license notices. |
| `TMessagesProj/tellomi/wallpapers/generate_patterns.py` | Regenerates those bitmaps from the named open-source icons. |
| `TMessagesProj/build.gradle` | The tellomi flavor assets directory is `tellomi/assets`. |
| `TMessagesProj/src/main/java/org/telegram/ui/ActionBar/Theme.java` | Default wallpaper loads the doodle only when the saved choice is the stock slug `d` with no color and no file. A chosen color (including black), gallery file, theme wallpaper, or other slug is left in place. Accent lists are the twelve colors. Incoming and outgoing bubbles follow the catalog. Solid wallpapers use the translucent date chip. |
| `TMessagesProj/src/main/java/org/telegram/ui/ActionBar/MessageDrawable.java` | Solid wallpaper bubbles draw the 0.5dp shadow. |
| `TMessagesProj/src/main/java/org/telegram/ui/WallpapersListActivity.java` | Color presets are the one solid. The list starts with the bundled wallpapers. |
| `TMessagesProj/src/main/java/org/telegram/ui/Cells/WallpaperCell.java` | A bundled row draws its pattern locally. |
| `TMessagesProj/src/main/java/org/telegram/ui/ThemePreviewActivity.java` | Preview and save keep a bundled wallpaper's id. |
| `TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java` | The default theme stub uses the doodle. |
| `TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java` | Help section opens the license page. |
| `TMessagesProj/src/main/res/values/strings.xml` | `TellomiOpenSourceLicenses`. |
| `TMessagesProj/src/main/res/values-zh/strings.xml` | Same string in Chinese. |

## Icon meanings

The MingCute pass matched short substrings, so `gift` became the GIF film, every search filter became a magnifying glass, and an unmatched `menu_*` name became the overflow dots. `list_check` was the double-check glyph. Chat list then drew that plus `list_halfcheck`, which was a full single check, and a sent message showed two ticks while a read one showed three.

`match()` now takes an exact override, then the longest rule whose key is a whole run of underscore-separated tokens and at least 8 characters. Short tokens (`gif`, `menu`, `search`, `video`, `photo`, and the rest of that list) do not match. A miss keeps the upstream drawable. Nothing falls through to `more-2`.

Slash, plus, minus, and call-direction arrows are MingCute glyphs with a second path. The half-check is the rising arm of MingCute `check`, placed in the same box as the upstream bitmap so the existing 5.5dp offset still makes one double check. GIF is the word in a rounded rect. The sticker is a folded sheet with a face cut out of the fill. Gram vectors are the full Tellomi mark in white, including `mini_gram_72_white`.

Send arrows (`send_plane_24`, `attach_send`, `msg_send`, `msg_sendfile`, `ic_send`) and the unsupported-card mark (`send_plane_26`) are unchanged. `send_outline` is the dashed ring on an ephemeral send button again. Login and the session are untouched.

Reverted to the upstream drawable, because a MingCute stand-in lost the drawing:

- Premium and star art: `filled_giveaway_premium`, `filled_gift_premium`, `filled_premium_away`, `filled_premium_bots`, `filled_premium_business`, `filled_premium_chatlink`, `filled_premium_hours`, `filled_premium_intro`, `filled_premium_status2`, `menu_feature_premium`, `menu_premium_away`, `menu_premium_chatbot`, `menu_premium_chatlink`, `menu_premium_clock`, `menu_premium_clock_add`, `menu_premium_effects`, `menu_premium_location`, `menu_premium_seen`, `menu_premium_tools`, `msg_mini_premiumlock`, `msg_premium_ads`, `msg_premium_avatar`, `msg_premium_badge`, `msg_premium_icons`, `msg_premium_limits`, `msg_premium_lock2`, `msg_premium_normal`, `msg_premium_reactions`, `msg_premium_speed`, `msg_premium_stickers`, `msg_premium_translate`, `msg_premium_uploads`, `msg_premium_voice`, `premium_ai_editor`, `premium_colors`, `premium_rich_editor`, `premium_status`, `premium_tags`, `premium_wallpaper`, `star_premium`, `star_premium_cutout`
- `settings_business` (briefcase), `send_outline` (dashed ring), `community_cards`, `msg_speed`, `msg_voicechat`, `msg_voicechat2`, `msg_stories_saved`, `menu_clear_cookies`, `msg_filled_sdcard`, `filled_requests_24`

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/icons/generate_icons.py` | Exact override, then longest token-bounded rule. No short substring. No `more-2` fallback. Small bitmaps are drawn larger and scaled down. |
| `TMessagesProj/tellomi/icons/icon_fixes.py` | Composites, the half-check, GIF, sticker, white Gram mark, and the upstream reverts. |
| `TMessagesProj/tellomi/icons/mapping.csv` | The resolved names. Reverted rows are gone. |
| `TMessagesProj/tellomi/icons/test_icon_meanings.py` | Single check, distinct pairs, no overflow fallback, white Gram mark. |
| `TMessagesProj/tellomi/icons/test_settings_rows.py` | Business is not a flavor gear. Gram is the Tellomi mark. |
| `TMessagesProj/tellomi/res/` | The redrawn overlays. Reverted names are deleted here so `src/main` wins. |

## Faster in-app update checks

`checkAppUpdate(false)` still runs from `LaunchActivity` when the UI returns to the foreground. Three things kept that request from ever showing the popup. A missing `appUpdateCheckTime` used `System.currentTimeMillis()` as the last check, so a fresh install (and any install that had never finished a check) started a new 24-hour window on every cold start. A finished check inside that window was skipped, including the first launch after an upgrade. The fallback delay was 24 hours.

A missing check time is now 0, so the next resume checks. The installed `versionCode` is stored in `appUpdateCheckVersion` when a check is sent. No stored code, or a code that differs from this run, skips the delay once. The next launch of the same code uses the delay again. The fallback is 6 hours. `help.getAppConfig` `inapp_update_check_delay` still writes `updateCheckDelay` and that saved value wins. Login and the session are untouched.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/.../TellomiAppUpdate.java` | Fresh-install check time, 6-hour default, version-change force, and the skip decision. |
| `TMessagesProj/src/main/java/.../SharedConfig.java` | Load the check time through that helper. Remember the version code after a check is sent. |
| `TMessagesProj/src/main/java/.../MessagesController.java` | `updateCheckDelay` falls back to 6 hours. The `inapp_update_check_delay` write is unchanged. |
| `TMessagesProj/src/main/java/.../LaunchActivity.java` | The foreground check uses the skip helper, then records the version code. |
| `TMessagesProj/src/test/java/.../TellomiAppUpdateTest.java` | Fresh install, upgrade, and the 6-hour / app-config delay. |
| `docs/TELLOMI.md` | The in-app update section matches this schedule. |

## Discover report, signup avatar, and langpack keys

Discover posts and comments use the `…` menu. The reason list is `ReportBottomSheet` (the same sheet as a chat report). The chosen official option id (`spam`, `violence`, `pornography`, `child`, `drugs`, `personal`, `other`) is sent as `POST /v1/feed/reports` with `detail`. `other` asks for a comment before that request. A signed-out account does not open the sheet and does not send. Success and failure show the existing bulletin. This client does not call `messages.report` for a Discover post.

`auth.signUp` still finishes the account before any avatar upload. `photos.uploadProfilePhoto` failure (including HTTP 500) leaves the account in place, shows 「头像上传失败」 with the server error code, and can retry the upload. The same prompt is used for a profile photo. Login and the session are otherwise unchanged.

Discover, tab, and city copy is read from the same langpack keys as iOS (`Tellomi.Discover.Feed`, `Tellomi.Discover.City.cn_hong_kong`, `Tellomi.SignUp.AvatarFailed`, and the rest of that list). Android resource names replace `.` and `-` with `_`. A downloaded pack value for the active language, including Japanese, replaces the bundle. `Tellomi.Discover.Report*` is Android-only.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/ui/ReportBottomSheet.java` | `openLocal` shows the native sheet without sending `messages.report`. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` | After a successful `auth.signUp`, the avatar upload is scheduled only when `TellomiSignUpAvatar.afterSignUp` says the account is already created. |
| `TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java` | A failed signup or profile avatar upload shows the server code and can retry. It does not clear the session. |
| `TMessagesProj/src/main/java/org/telegram/messenger/utils/PhotoUtilities.java` | Profile photo RPC errors use the same prompt. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiPack.java` | Langpack keys and the bundled English and Chinese. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiSignUpAvatar.java` | The non-blocking avatar decision and the real error code. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiSignUpAvatarPrompt.java` | The dialog. |
| `TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/TellomiDiscoverReport.java` | The `…` menu and the sheet, then `POST /v1/feed/reports`. |
| `TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/TellomiDiscover.java` | Copy comes from the pack. Report is an engine operation. |
| `TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/TellomiDiscoverCity.java` | Display names use `Tellomi.Discover.City.*`. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiTabNavigation.java` | Tab copy uses the iOS keys `Tellomi.Discover.Feed`, `HideFeed`, `HideContacts`, `NavigationTitle`, `NavigationFooter`, `FeedEmptyTitle`, `FeedEmptyText`, and `FeedPostUnavailable`. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiTabs.java` | The bar reads the pack for the active language. |
| `TMessagesProj/tellomi/res/values/strings.xml` and `values-zh*` | The new keys. |

## Discover city lookup keys

The shared language pack cannot put a hyphen in a key. The iOS string generator rejects it. Four Discover city lookup keys therefore use an underscore. The city code on a post is unchanged (`cn:hong-kong`, `us:new-york`, `us:los-angeles`, `us:san-francisco`). Bundled resource names were already `Tellomi_Discover_City_cn_hong_kong`, `Tellomi_Discover_City_us_new_york`, `Tellomi_Discover_City_us_los_angeles`, and `Tellomi_Discover_City_us_san_francisco`.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/TellomiDiscoverCity.java` | `displayKey` replaces both `:` and `-`, so the lookup keys are `Tellomi.Discover.City.cn_hong_kong`, `us_new_york`, `us_los_angeles`, and `us_san_francisco`. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiPack.java` | The resource-name note matches those underscore lookup keys. `resourceName` still turns a leftover `-` into `_`. |
| `TMessagesProj/src/test/java/org/telegram/ui/tellomi/feed/TellomiDiscoverI18nTest.java` | The four lookup keys, and the resource names they already map to. |

## Discover nearby tab (D-11 / D-12)

The Discover bar always shows 推荐, 关注, and 同城, in that order. `GET /v1/feed/config` may omit `nearby`; the tab stays. An unknown config name is still not added.

The first open of 同城 with no saved city asks for coarse location only (`ACCESS_COARSE_LOCATION`). The coordinate is used once for `Geocoder` and then dropped. Only `Address.getLocality()` can become a catalog code such as `cn:chengdu`. A street, a province, or a coordinate does not. If permission is denied, or geocoding returns nothing, the city list stays up and the user picks by hand. Nothing is stored until they pick.

Publishing still uses `POST /v1/feed/posts` and the existing `city` field. The value is that catalog code. There is no latitude, longitude, or address field. The line above the feed (`Posting in Chengdu` / `发布到 成都`) is the city the next post will carry. The composer screen is unchanged, so it does not collide with the video and mention composers.

`GET /v1/feed/nearby?city=` is the nearby read. `content-engine` was not readable from this tree (private, HTTP 404). A 404, 405, or 501 on that route is shown as 「同城暂时还不能刷新」. An empty 200 is 「这个城市还没有帖子」. The client does not invent another path. Simplified Chinese in `TellomiPack` is what zh-Hans and zh-Hant (including zh-TW) both read. `values-zh-rTW` is not added.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/TellomiDiscoverNearby.java` | Home tabs, the publish city code, the compose line, and the missing-route check. No Android types. |
| `TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/TellomiDiscoverScreen.java` | The three tabs, the first coarse-location ask, the city list, and the degraded empty state. |
| `TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/TellomiDiscover.java` | A missing nearby route is `NEARBY_UNAVAILABLE`, not a generic load failure. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiPack.java` | English and simplified Chinese for those lines. |
| `TMessagesProj/tellomi/res/values/strings.xml` and `values-zh*` | The same lines. zh-Hant keeps this simplified pack. |
| `TMessagesProj/src/test/java/org/telegram/ui/tellomi/feed/TellomiDiscoverNearbyTest.java` | City normalize, tab order, the nearby query, and the publish keys. |

## Settings home (M1)

The settings tab is still `SettingsActivity`. `TellomiSettingsLayout` rebuilds that list into the same three groups iOS uses: Account, General, and Support. The home screen shows those three titles. Each opens a secondary page with the rows that used to sit on the home list. Tips (`Telegram Features`, row 23) and Apple Watch (row 28, or a row titled Apple Watch) are left off. Suggestions, other accounts, the experimental block, and the debug block stay on the home screen. The check-for-updates row (`TellomiSettingsUpdate.ROW_ID`, 41) stays on the home screen, directly above the version footer, and still calls `checkAppUpdate` with progress. It is not a Support page row. The General group uses the white gear `msg_settings`. The header card gets a 12dp side inset when it is attached, because the list binds it with match-parent params and no margins.

The top block is a card. It shows the display name, and when the account has them, the phone, the email, and the public username. Phone and email are both shown. The email is the saved sign-in address, not the masked switcher label.

Help Center, the old FAQ row, the settings-search FAQ webpage (`messages.getWebPage`), `tg://settings/faq`, and `tg://settings/features` open `help.getAppConfig` `tellomi_help_url`. A missing, blank, or non-http value is `TellomiHelp.URL` (`https://tellomi.cn/help`, no trailing slash). `BuildVars.HELP_URL` is that same constant, so login opens it too. The search index does not read `TelegramFaqUrl`. Login and the session are otherwise unchanged.

New lookup keys use only letters, digits, `.`, and `_`: `Tellomi.Settings.Group.Account`, `Tellomi.Settings.Group.General`, `Tellomi.Settings.Group.Support`, `Tellomi.Settings.Help`, `Tellomi.Settings.Card.Phone`, `Tellomi.Settings.Card.Email`, `Tellomi.Settings.Card.Username`.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/messenger/TellomiHelp.java` | The one help-center URL, `https://tellomi.cn/help`. |
| `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java` | `HELP_URL` is `TellomiHelp.URL`. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiSettingsModel.java` | Group order, the card lines, and the help URL. No Android types. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiSettingsLayout.java` | Draws that model: the card (12dp side inset once the header is attached), the three rows (General uses `msg_settings`), and the secondary pages. The update row stays on the home list. |
| `TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java` | `arrange` after the list is built, `applyCard` on the header, the group tap, and the two help opens. The header is 36dp taller so the card can show three lines. |
| `TMessagesProj/src/main/java/org/telegram/messenger/AppGlobalConfig.java` | `tellomi_help_url`, default `TellomiHelp.URL`. |
| `TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java` | The FAQ row, the settings search hit, and the FAQ webpage index use the same help URL. |
| `TMessagesProj/src/main/java/org/telegram/ui/LinkManager.java` | `tg://settings/faq` and `tg://settings/features` use the same help URL. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiPack.java` | The seven `Tellomi.Settings.*` keys. |
| `TMessagesProj/tellomi/res/values/strings.xml` and `values-zh*` | English and Chinese for those keys. |
| `TMessagesProj/src/test/java/org/telegram/ui/TellomiSettingsLayoutTest.java` | Groups, pages, the card, the help URL, and the lookup keys. |

## Discover polls, forwards, and notification switch (M3)

Android only. The feed client already posted text, read recommend / following / nearby, and showed a deleted quote. This adds the missing interactions that the engine already serves.

A public (`shareable`) post gets a forward menu: direct repost (`PUT /v1/feed/posts/{id}/repost`), quote (`quote_of` on `POST /v1/feed/posts`), share into a Tellomi chat (`ShareAlert` with `https://tell.cc/m#<id>`), and the system share sheet. A post that is not shareable has no menu. A quote whose original is `deleted` still shows the existing「原动态已被作者删除」line. The engine removes the repost when the original is deleted; the client does not keep one.

A post can include a poll (`poll.options`, two to ten strings, each at most 40 code points). A quote and a poll are not sent together. Option labels are `card.poll.options`. Counts and the chosen indexes are the sibling `poll.counts` and `poll.voted`; an empty `voted` means this account has not voted. A tap sends `PUT /v1/feed/posts/{id}/vote` with `{"options":[index]}`. Success is `200 {"counts":[Int],"voted":[Int]}`. An empty body or `{}` is not a success.

The in-app list is `GET /v1/feed/me/notifications`. The system banner uses `loc_key` `DISCOVER_*` (or `custom.discover = 1`) and opens the post. It does not open a chat with user 777005. The switch on Notifications and Sounds is `account.updateNotifySettings` for @faxian (user id 777005). No row means on. `mute_until` in the future, including mute forever, means off. Muting all private chats does not write this peer.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/ui/NotificationsSettingsActivity.java` | One check row, `discoverRow`, after reactions. The title and the toggle live in the flavor class. |
| `TMessagesProj/src/main/java/org/telegram/messenger/PushListenerController.java` | After the account is known, a discover payload is shown by `TellomiDiscoverPush` and is not turned into a chat message. |
| `TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java` | The notification extra `tellomi_discover_post` opens `https://tell.cc/m#<id>` through the existing post-link path. |

## Discover compose @mentions (M3)

Android only. The composer parses and inserts `@username`. The local check is the same one `ChangeUsernameActivity.checkUserName` already uses: no leading or trailing `_`, no leading digit, only `0-9` / `A-Z` / `a-z` / `_`, at least `TellomiUsername.MIN_LENGTH` (4), at most 32. It does not call `account.checkUsername`. A token that fails that check keeps 发布 disabled and shows the reason. A lone `@`, and an `@` inside an address (`a@b.com`), are left as text.

Picking a row, or tapping 提及, writes `@username` into the post. The 60-second video picker from D-41 stays on this screen. A clip that is still attached is not uploaded, and the text post is not sent in its place. `POST /v1/feed/posts` is unchanged for mentions: the name stays inside `text`. There is no `mentions` or `entities` field on that body, on the card parser, or on the iOS `tellomiDiscoverPublishBody`. The suggestion list is people already in `MessagesController.getUsers()` who have a public username. It does not search the network.

The system banner and the in-app list already show `kind = mention` / `DISCOVER_MENTION` when one arrives. Creating that notice is the content engine's call to teamgram `POST /internal/v1/discover/notify`. This client does not send that request, and it does not add a field the engine document does not list. `content-engine` was not readable from this tree (private). teamgram `docs/api/discover-ai.md` says the 2026-11-04 read of `api/notifications.go` could not see the engine's field names. No `src/main` file changes for this.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/TellomiDiscoverMention.java` | Parse, validate, insert, and filter. Flavor only. |
| `TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/TellomiDiscoverComposeFragment.java` | Hint, 提及, the suggestion list, and the invalid-username line. |
| `TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/TellomiDiscover.java` | The publish-body comment records that mentions stay in `text`. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiPack.java` | `Tellomi.Discover.Mention`, `MentionHint`, `MentionInvalid`, `MentionEmpty`. |
| `TMessagesProj/tellomi/res/values/strings.xml` and `values-zh*` | English and Chinese for those keys. |
| `TMessagesProj/src/test/java/org/telegram/ui/tellomi/feed/TellomiDiscoverMentionTest.java` | The username rules, insert, and the unchanged publish keys. |

## Discover link preview (M3)

The phone fetches Open Graph itself when a Discover post is composed. The request does not go through Chengdu, `account.getWebPagePreview`, port 8801, or `POST /internal/v1/link-preview`. A short process-local map remembers a few results. Nothing is written to disk, and the feed does not fetch the page again.

`POST /v1/feed/posts` gains one optional object, `link_preview`, and only when the URL is public http(s). The object is `url`, plus `title`, `description`, and `image` when those exist. iOS does not send this object yet. content-engine's API doc is not in this repo (private, 404). Cards are read from `card.link_preview`, or the same object beside `card`. A private or missing preview is left off the post and off the card. No `src/main` file changes. The composer still has the @mention list and the 60-second video picker. A city on the post is still `TellomiDiscoverNearby.publishCity`.

## Release-feed update check

Settings → Check for updates, and the automatic check on the shipped App module, called `help.getAppUpdate` and then `SharedConfig.setNewAppVersionAvailable`. That comparison uses `versionName` only. A published build that keeps the same marketing version and raises `versionCode` was treated as already installed. The installer publish writes `android/latest.json` (`versionName` + `versionCode`) and does not update `help.getAppUpdate`. `versions.json` on the download page can lag that file, and `android/test/latest.json` is a different channel. Neither of those is the check.

The App module now reads `https://www.tellomi.cn/dl/android/latest.json` and `https://dl.tellomi.cn/android/latest.json` together. The higher `versionCode` is the release. The APK link is the one on the host that won, so the official URL is not offered when only the backup feed answered. The swipe opens an `.apk` link in the system browser. A feed that cannot be read does not show "already the latest" and does not start the 6-hour delay. Standalone and beta builds still use their previous check. Login and the session are untouched.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/.../TellomiReleaseUpdate.java` | Feed URLs, manifest parse, version-code comparison, and the APK link. |
| `TMessagesProj/src/main/java/.../LaunchActivity.java` | In-app updates use that feed. Manual failure is not "already the latest". |
| `TMessagesProj/src/main/java/.../SharedConfig.java` | A remote version code beats an equal version name. The saved offer keeps that code. |
| `TMessagesProj/src/test/java/.../TellomiReleaseUpdateTest.java` | Same version name with a higher code, the stale page manifest, and the backup APK link. |
| `docs/TELLOMI.md` | The in-app update section matches this source. |

## Built-in languages use the server langpack

`TELLOMI_USE_CLOUD_STRINGS` stays false. A remote or unofficial row was already read from the file `langpack.getLangPack` writes. The language button still lists the built-in codes (en, it, es, de, nl, ar, pt_br, ko, uk, ru, zh_hans, zh_hant). Those rows are created with `pathToFile = null`, so `applyLanguage` never called `applyRemoteLanguage`. `getStringV2` then used `localization_*.bin`.

`values/` and `values-zh/strings.xml` each have 11505 strings. The other bundled folders are a subset: it 1516, es 1298, de 1359, ar 1227, pt-rBR 1234, ko 1121, nl 1068, uk 269, ru 257. Missing keys fall back to English. Russian and Ukrainian are so small that the switch looks like it did nothing. Chinese is not sent through this path: a remote Chinese row with no file used to drop the UI onto the English bin, and `localization_zh.bin` is already complete. English is the default table.

`TellomiDefaults.attachServerLangpack` sets `pathToFile` to `remote` for any other built-in row at the start of `applyLanguage`, before `getPathToFile()`. The existing download then runs, and `cloudStringsEnabled` reads the file because the row is remote. The same object is stored in `remoteLanguages` when it is already in the language dict, so the version is saved with the other remote packs. English and both Chinese codes are unchanged. A failed `langpack.getLangPack` still runs the apply callback and posts `reloadInterface`, so the settings spinner closes and login redraws onto the local bin.

Login used to rebuild itself as soon as `applyLanguage` returned. That return now happens before the pack arrives, and `reloadInterface` does not rebuild the top fragment. The login redraw runs from the apply callback, after the strings are loaded (or the request has failed).

### Check on a device

Install a dev build (`-PTELLOMI_SERVER_PROFILE=dev`) that can reach the server. On the login language list, choose Italiano, then Español, Deutsch, Nederlands, العربية, Português (Brasil), 한국어, Українська, and Русский. After the short download, labels that were English in the bundled bin (Settings, Chats, and the login fields) should follow that language. Repeat from Settings → Language. English should stay fully English with no download. 简体中文 and 繁體中文 should stay Chinese. Turn the network off, pick a language that is not cached, and the screen should still switch to the short local bin instead of hanging.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/messenger/TellomiDefaults.java` | `needsServerLangpack` and `attachServerLangpack`. English and Chinese stay local. |
| `TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java` | `applyLanguage` marks the built-in row remote and keeps it in `remoteLanguages`. A failed `getLangPack` finishes the apply. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` | Login redraws when the apply callback runs, not before the pack is loaded. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiLanguageSheet.java` | Comment: the non-English, non-Chinese bins are a fallback. |
| `TMessagesProj/src/test/java/org/telegram/messenger/TellomiCloudStringsTest.java` | Which codes request a pack, and that apply plus login are wired to it. |

## Discover video length (D-41)

Compose had no video control. A picked clip is still not uploaded and is not dropped into a text post. Image posts later send `media`; this video path does not. The 60-second cap is enforced on the client anyway.

The picker is `ACTION_GET_CONTENT` (`video/*`) from `TellomiDiscoverComposeFragment`. Length comes from `MediaMetadataRetriever`, then `MediaStore.Video.Media.DURATION` if that fails. `TellomiDiscoverVideo.select` keeps a clip only when the length is from 1 ms through 60_000 ms. A longer clip is discarded and the screen says to pick a shorter one. An unreadable length is discarded too. Publish reads the length again and calls `beforeUpload`. Over 60 seconds is still discarded. A clip inside the cap returns `NOT_UPLOADED` and the text post is not sent.

`MediaController`, ffmpeg, and the chat gallery were not edited. `PhotoEntry.duration` is whole seconds (`MediaStore` milliseconds / 1000). `TellomiDiscoverVideo.selectPhotoEntrySeconds` is that coarser check for a later gallery hook. The compose path does not use it.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/TellomiDiscoverVideo.java` | The 60-second gate, the gallery-seconds helper, and the clock label. No Android types. |
| `TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/TellomiDiscoverVideoDuration.java` | Reads milliseconds from the picked uri. |
| `TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/TellomiDiscoverComposeFragment.java` | Hint, pick, reject, and the publish-time check. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiPack.java` | English and Chinese for those lines. |
| `TMessagesProj/tellomi/res/values/strings.xml` and `values-zh*` | The same lines. |
| `TMessagesProj/src/test/java/org/telegram/ui/tellomi/feed/TellomiDiscoverVideoTest.java` | The 60-second boundary, the upload gate, and the Chinese copy. |

## Login send-code SMS failure

Reopen of closed, unmerged #9, rebased onto current master.

`auth.sendCode` can fail with `SMS_CODE_CREATE_FAILED`. The spinner already stops in the RPC callback. That text is not in the phone-screen special cases (`PHONE_NUMBER_INVALID`, `FLOOD_WAIT`, and the rest). The fallback `tellomiShowSendFailure` shows the generic error plus the raw error string, so the dialog said `SMS_CODE_CREATE_FAILED`. `AlertsCreator.processError` still has no `auth.sendCode` branch. The phone screen now uses the same `needShowAlert` as those neighbors, with the fixed sentence 「验证码发送失败，请稍后重试」. The check is `req instanceof TLRPC.TL_auth_sendCode`, so change-phone (`account.sendChangePhoneCode`), resend, and every other error text on that callback still use the existing path. The sentence is hardcoded, like the sign-up agreement: it is not in the cloud langpack, so a new string resource would miss while `zh-hans` / `zh-hant` are active.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/messenger/TellomiLoginErrors.java` | New. Flavor-only copy and the `SMS_CODE_CREATE_FAILED` check. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` | Phone-screen `auth.sendCode` error callback calls `needShowAlert` for that error before `tellomiShowSendFailure`. |
| `TMessagesProj/src/testTellomi/java/org/telegram/ui/TellomiLoginFixesTest.java` | The sentence and the phone-screen branch. |

## Discover multi-image posts (D-10)

Compose can pick up to 10 JPEG, PNG, or WebP photos, each at most 20MB. The bytes go to `POST /v1/feed/media/uploads` as tus 1.0.0 (`Upload-Length`, `Upload-Metadata` filename/filetype, then `PATCH` with `Upload-Offset`). `POST /v1/feed/posts` sends the returned keys as `media_keys` (a string array). `media[]` is only the card response. A picture is `media[].url` when the engine sends one, otherwise `media_base_url` from `GET /v1/feed/config` plus the key. There is no `GET /v1/feed/media/<key>`. Text may be empty when `media_keys` is present. A video still blocks the post and is not uploaded. Free-form `#` topics are not sent.

Field names follow content-engine `docs/api.md`. `link_preview` from the merged link-preview post stays beside `media_keys`. No chat-video crop, iOS, or content-safety change.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/TellomiDiscoverMedia.java` | Limits, sniff, tus session, publish keys, image URL. |
| `TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/TellomiDiscoverComposeFragment.java` | Multi-pick, remove, upload, then publish. |
| `TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/TellomiDiscoverImageRow.java` | Feed and detail image slots. |
| `TMessagesProj/src/test/java/org/telegram/ui/tellomi/feed/TellomiDiscoverMediaTest.java` | Tus headers, the post field, limits, and zh-Hans / zh-TW copy. |

## Document hash before upload (ADR-0082)

Before the bytes of a GIF, a file, or a video that will not be transcoded are uploaded, the client hashes the local file and calls `messages.getDocumentByHash#b1f2061f` (`sha256`, `size` as long, `mime_type`). A returned `document` with id, access hash, and dc is sent as `inputMediaDocument`. `documentEmpty`, an RPC error, a timeout, or a hash failure continues the existing upload. Photos, secret chats, voice notes, live photos, a separate cover, mask documents on the upload, and any video `VideoEditedInfo.needConvert()` still uploads are unchanged. The old `#338e2464` int-size binding in `TLRPC` is left as upstream generated it; the request used here is the current constructor.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/messenger/TellomiDocumentByHash.java` | Which files qualify, the constructor, SHA-256, and what counts as a reusable document. |
| `TMessagesProj/tellomi/java/org/telegram/messenger/TellomiDocumentByHashSend.java` | The RPC, fail-open, and swapping the outgoing media to `inputMediaDocument`. |
| `TMessagesProj/src/main/java/org/telegram/messenger/SendMessagesHelper.java` | Three original-file upload sites (single video, single document/GIF, grouped document/video) call that lookup before `uploadFile`. Transcoding and encrypted uploads do not. |
| `TMessagesProj/src/test/java/org/telegram/messenger/TellomiDocumentByHashTest.java` | Eligibility, the long-size constructor, SHA-256, and that those three sites are the only hooks. |

## Document hash kill switch (ADR-0083 B1 / D4)

Extends the section above. The three upload sites stay as they are. GIF, ordinary files, send-as-file images (`image/jpeg` / `image/png` documents), and grouped document or non-convert video slots already call `messages.getDocumentByHash` before `uploadFile`. This adds a way to turn that lookup off without a rebuild.

Account 0 preferences file `mainconfig`, key `tellomi_get_document_by_hash`, boolean. A missing key or `true` keeps the lookup. `false` makes `lookupBeforeUpload` return immediately, and the caller runs the existing upload. The value is read on each send.

Compressed photos stay on the upload path. Delayed type 0 and album slots with no document upload `FileTypePhoto` as `inputMediaUploadedPhoto`. `getDocumentByHash` returns a `Document`, and those bytes are a JPEG from `generatePhotoSizes`, not the gallery file. Send-as-file and `ImageLoader.shouldSendImageAsDocument` already go through `prepareSendingDocumentInternal` (delayed type 2, or type 4 when grouped) and are covered. Server-side photo reuse after the upload is unchanged. Follow-up: a photo RPC that returns `InputPhoto`, then a type 0 hook is safe.

Non-convert video is hashed on `Utilities.globalQueue`. `FAIL_OPEN_MS` (12s) covers the hash and the RPC together. When the timer fires first, the upload starts and a late hit is dropped. `VideoEditedInfo.needConvert()` stays off this path because the stored bytes would be the transcode. Secret chats, voice notes, live photos, a separate cover, and mask documents stay off it too.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/messenger/TellomiDocumentByHash.java` | `mainconfig` kill switch, default on. Photo follow-up noted on the class. |
| `TMessagesProj/tellomi/java/org/telegram/messenger/TellomiDocumentByHashSend.java` | Disabled switch returns before any hash or RPC. |
| `TMessagesProj/src/test/java/org/telegram/messenger/TellomiDocumentByHashTest.java` | Default on, forced off skips lookup, JPEG/PNG documents are eligible, type 0 is not hooked. |

## Download file hashes (ADR-0083 B2)

After a full plaintext `upload.getFile` download is on disk, and in parallel while those bytes are still arriving, the client calls `upload.getFileHashes#9156982a` (`location:InputFileLocation`, `offset:long`). Parts are SHA-256 of 128 KiB, the same split as `upload.getCdnFileHashes` / teamgram `cdn.PartHashes` (`tellomi/teamgram-server` #215, `59922c63`). `offset` drops parts that start before it. The last part is shorter. A later page is requested when the returned parts do not cover the file.

A miss (empty vector), an RPC error, or a 12s timeout after the download is ready to finish leaves the file in place. A part whose bytes differ, or a confirmed end shorter than the file, deletes the temp file and fails the download. CDN downloads already check ciphertext hashes. Web files, legacy `inputFileLocation` (`INPUT_REQUEST_INVALID` on that server), secret-chat decryption, local cache encryption, preload, a prefix stop, and a file already on disk do not call this method. `messages.getDocumentByHash` is unchanged.

Account 0 `mainconfig` key `tellomi_get_file_hashes`, boolean. Missing or true runs the check. False skips the RPC. The generated `TL_upload_getFileHashes#c7025931` (`offset:int`) stays as upstream wrote it.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/messenger/TellomiFileHashes.java` | Part rules, the long-offset request, fail-open, and the kill switch. |
| `TMessagesProj/src/main/java/org/telegram/messenger/FileLoadOperation.java` | Starts the RPC on the download and holds `onFinishLoadingFile` until the check, the miss, or the timeout. |
| `TMessagesProj/src/test/java/org/telegram/messenger/TellomiFileHashesTest.java` | 128 KiB parts, mismatch versus miss, the switch, and that the upload path is not hooked. |

## Discover personalization switch (D-58)

Settings → Privacy and Security has a Discover section with 「个性化推荐」. The switch defaults on and is stored per account in `mainconfig` (`MessagesController.getMainSettings`), key `tellomi_discover_personalized`, the same file as `tellomi_show_feed_tab` and `tellomi_discover_city`. A missing key is on.

The recommend request reads that switch. On matches the production URL: `GET /v1/feed/recommend` with no `personalized` query. Off reloads the recommend tab immediately with `personalized=false`. Following still ignores the flag. A recommend page that is not on screen is dropped so the next visit refetches.

`personalization_toggle_visible` is still parsed onto `EngineConfig` and does not hide the row. The parser stores false both when the key is omitted and when it is false, so the configs this client already accepts would hide the only control that can send `personalized=false`. The row is always on this screen.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/java/org/telegram/ui/PrivacySettingsActivity.java` | Discover section, switch, and footer. |
| `TMessagesProj/tellomi/java/.../TellomiDiscoverPersonalization.java` | Preference, default on, and the recommend reload signal. |
| `TMessagesProj/tellomi/java/.../TellomiDiscoverScreen.java` | Recommend `Operation.feed` reads the switch and refetches when it changes. |
| `TMessagesProj/tellomi/res/values/strings.xml` and `values-zh*` | English and Chinese copy. |
| `TMessagesProj/src/test/java/.../TellomiDiscoverPersonalizationTest.java` | Off sends `personalized=false`. On omits it. |

## Discover likes and public text comments

Likes and text comments use the discover bearer on the content-engine. `PUT` and `DELETE /v1/feed/posts/{id}/like` send an empty body and `surface` (`recommend` by default, also `following`, `hot`, `search`, `profile`). Success is 204. `POST /v1/feed/posts/{id}/comments` sends `text`, optional `parent_id`, and `client_nonce`. It does not send `media_keys` or `operation_id`. `GET` the same path pages public comments, oldest first, 50 at a time. `DELETE /v1/feed/posts/{commentId}` deletes that comment only.

A card reads `me.liked`, `stats.likes`, and `stats.likes_hidden`. A hidden count is not shown as 0. A pending comment is not added to the public count. One like request is in flight. Its confirmed count changes only when that request succeeds. Taps during the flight stay as the desired state. An uncertain failure of the older request sends one idempotent like or unlike for that latest state, then stops. A 403 or 404 does not send again; the button returns to the confirmed count and the error stays on screen. Leaving the list or the post drops a late response. `401 credential_expired` refreshes the discover token once. `404 feed_hidden` hides the feed. Comment errors stay on the post; they are not sent as a private message. Voting, forwarding, and reporting are unchanged. Stories are unchanged.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/.../TellomiDiscover.java` | Like, comment list, comment create, and comment delete calls. |
| `TMessagesProj/tellomi/java/.../TellomiDiscoverParse.java` | `me.liked`, hidden like counts, comment pages. |
| `TMessagesProj/tellomi/java/.../TellomiDiscoverReact.java` | Like serialization and comment page state. |
| `TMessagesProj/tellomi/java/.../TellomiDiscoverScreen.java` | Like control on a post row when `reactions` is in capabilities. |
| `TMessagesProj/tellomi/java/.../TellomiDiscoverPostFragment.java` | Like, comment field, list, and paging when the capabilities allow them. |
| `TMessagesProj/tellomi/java/.../TellomiPack.java` and discover strings | The like and comment phrases. |
| `TMessagesProj/src/test/java/.../TellomiDiscoverReactTest.java` | Contract, failure restore, paging, and nonce retry. |

## Settings leaves the bottom bar

The bottom bar no longer includes Settings. Profile stays, and its existing settings button still opens `SettingsActivity`. Discover, Chats, Calls, and Contacts keep their order. Chats is still the pager start. The settings glass tab is hidden and removed from accessibility. `tg://settings` and the profile button still present `SettingsActivity` on the stack. The navigation page no longer lists Settings, and its footer no longer says Settings stays in the bar.

The selected page is not stored. `ViewPagerActivity.clearViews` keeps the current pager index only on that live object. `createView` uses it if the object is still alive, and otherwise calls `getStartPosition`, which is Chats. `MainTabsActivity` does not implement `saveSelfArgs`. There is no old Settings selection to move, so nothing maps that tab onto Profile.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiTabs.java` | Settings is not in `visible`. The glass tab stays hidden. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiTabNavigation.java` | The navigation list omits Settings. A post link still opens Discover. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiNavigationSettings.java` | The page comment matches the shorter list. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiPack.java` and the navigation footer strings | Chats is the tab that cannot be turned off. |
| `TMessagesProj/src/test/java/org/telegram/ui/TellomiTabsTest.java` | Orders, the Chats start path, and the profile settings button. |
| `TMessagesProj/src/test/java/org/telegram/ui/TellomiTabNavigationTest.java` | Footer copy and the shorter row list. |
| `TMessagesProj/build.gradle` | Unit-test compile can see `com.sun.net.httpserver` for the existing release-update test. Production compile is unchanged. |

## Sign-in email, login-code email, and login ink

Teamgram PR 227 head `b4a57578` (parent `2f791858`) keeps an email-only login identity in `users.email` and leaves `users.phone` NULL. `account.getPassword.login_email_pattern` is a read-only mask of that address (`u***@tellomi.app`), including when there is no cloud password. `has_recovery` and `email_unconfirmed_pattern` stay on the cloud-password record. `emailVerifyPurposeLoginChange` may confirm the same identity. Any other address, including another account's `users.email`, is `EMAIL_NOT_ALLOWED` and does not change `user.id`. A phone account has no identity mask and can still attach a free login email.

Head `b4a57578` does not rewrite a divergent Redis login-email copy and does not mail it. `auth.requestPasswordRecovery` returns `PASSWORD_RECOVERY_NA` for an email-only account with no cloud password. This client calls that RPC only when `has_recovery` is set. `help.getPromoData` drops `SETUP_LOGIN_EMAIL` and `SETUP_LOGIN_EMAIL_NOSKIP`. The client drops those names from the saved set and does not open the attach form from them.

The account page reads that mask. A matching on-device address may be shown, but the mask is never stored or sent as `phone_number`. Until `getPassword` returns, an account with no phone number waits instead of opening an empty bind form. A phone number still opens the attach form. Confirming the original address uses the existing login-change screen with an empty field. A mask or `EMAIL_NOT_ALLOWED` says the identity was not replaced. Success of that screen still does not write the sign-in cache.

Login fields on the forced black or white page set digit, hint, cursor, and selection together. The old night hint `#8E8E93` on field `#2C2C2E`, and the old day hint `#C7C7CC` on `#F3F4F6`, failed a 4.5 contrast check. Square code boxes and the email code field use the same ink. Social icons are tinted with that ink. The drawable files still contain `#111111`; that color is not the dark-mode icon color.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiEmailRoles.java` | Mask versus full address, account-row action, and recovery staying independent. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiContrast.java` | Digit, hint, field, and selection colors, and the contrast check. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiLoginChrome.java` | `applyInput`. Hint colors come from `TellomiContrast`. Social icons are tinted at creation. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiAuthPage.java` | `updateColors` refreshes the email page with the login page. |
| `TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java` | Drop `SETUP_LOGIN_EMAIL` and `SETUP_LOGIN_EMAIL_NOSKIP` from the saved set. `checkEmailSuggestion` stays off. |
| `TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java` | The unused suggestion success line no longer says the account email changed. |
| `TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` | Remember the shared address. Login-change copy. Do not cache that address as the sign-in email. Code and phone fields use `applyInput`. |
| `TMessagesProj/src/main/java/org/telegram/ui/CodeFieldContainer.java` | Square boxes apply the same ink when the digit color changes. |
| `TMessagesProj/src/main/java/org/telegram/ui/UserInfoActivity.java` | A sign-in address opens an explanation. A phone account opens login-code email. |
| `TMessagesProj/src/main/java/org/telegram/ui/PrivacySettingsActivity.java` | The success line says the login-code email changed and the account did not. |
| `TMessagesProj/tellomi/res/values/strings.xml` and `values-zh*` | The three email roles, in English and Chinese. |
| `TMessagesProj/src/testTellomi/java/org/telegram/ui/TellomiThemeAndEmailTest.java` | Contrast pairs, recovery stays unset, and the login-change cache write is gone. |

## Built-in Night theme, checked pairs

`TMessagesProj/src/main/assets/night.attheme` is the Telegram file from `3de37c8cf` again. These pairs are at least 4.5:1: chat-list name on `dialogBackground`, incoming text on `chat_inBubble`, outgoing text on `chat_outBubble`, input text on `chat_messagePanelBackground`, search text on `dialogBackground`, dialog text on `dialogBackground`, action-bar title and icon on `actionBarDefault`, and page text on `windowBackgroundWhite`. `telegram_color_dialogsLogo` is absent, so the chat-list wordmark uses `windowBackgroundWhiteBlackText`, which this file sets to white. `dialogTextGray3` on `dialogBackground` is about 4.05:1. That secondary gray was left as it is.

No custom theme file and no system night animation was opened. Settings icon plates stay the existing dark neutrals with white glyphs. The intro page stays the fixed white page. Those were not recolored.

| File | Change |
| --- | --- |
| `TMessagesProj/src/testTellomi/java/org/telegram/ui/TellomiNightThemeTest.java` | Reads `night.attheme` and checks the pairs above. |

## Send glyph and the contact paper plane

The chat send button, the photo picker, the attach sheet, share, and the rich editor all draw `R.drawable.send_plane_24` through `ChatActivityEnterView.SendButton`. Pressed, inactive, and the open state reuse that same drawable. Light and dark only change `key_chat_messagePanelSend` and the glyph tint (`SendButtonColors.contrastGlyph`). The appear animation rotates that drawable and then settles. The loading state is an arc. Schedule mode uses `input_schedule`. The voice-record circle uses `attach_send`, which the flavor already draws as an up arrow. `send_outline` stays the dashed ephemeral ring.

`send_plane_24` is now an up arrow with the points written in the path, so a group rotation is not required. `ic_launcher_dr` was still the blue paper plane. Incoming-call UI, notification people, and the contacts account use that name. The flavor file is the existing launcher PNG at each density. `intro_tg_plane` has no runtime reference and was left as it is.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/res/drawable/send_plane_24.xml` | Up arrow, no transform group. |
| `TMessagesProj/tellomi/res/drawable-*/ic_launcher_dr.png` | Same bytes as `mipmap-*/ic_launcher.png`. |
| `TMessagesProj/src/test/java/org/telegram/ui/TellomiSendGlyphTest.java` | Arrow bounds, and the contact icon matches the launcher file. |

## Profile tab label

The bottom-bar profile tab shows 我 when the app language is Chinese. The page title and every other language keep their existing strings. The tab still opens the same profile page. Settings stays off the bar.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiProfileNav.java` | Chinese nav label `我`. Other languages return null. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiTabNavigation.java` | `profileNavLabel` delegates to that class. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiTabs.java` | Applies that label and the same accessibility name on the profile tab. |
| `TMessagesProj/src/test/java/org/telegram/ui/TellomiTabNavigationTest.java` | Chinese codes, other languages, and the shorter label on a 320dp bar. |

## Telegram theme colors, chat themes, and wallpapers

The client overrides that replaced Telegram's palette are reverted to `3de37c8cf`. That snapshot is still the upstream baseline (DrKLO "update to 13.1.1"). Built-in themes are Blue, Dark Blue, Arctic Blue, Day, and Night again, with Telegram's accent rows. The default wallpaper is Telegram's pattern, not the bundled doodle. The doodle catalog class remains only so the open-source license page can still name those assets.

`account.getChatThemes` on the Tellomi server returns themes with an emoticon and accent colors and no wallpaper document (`app/bff/accountassets/chatthemes.go`). The preview used to read `themeSettings.wallpaper.settings` and crashed, so those rows could not be drawn. A missing wallpaper now paints the accent. `account.getWallPapers` returns `wallPaperNoFile` solids, including black `#000000` (`wallcatalog.go`). A color of zero used to be treated as "no color" and the black row was dropped. An explicit background flag, including black, stays in the list. `account.getUniqueGiftChatThemes` failing no longer blocks the emoji list. A failed `account.getWallPapers` leaves the local color presets in place.

The baseline chat-theme stub assigned the theme wallpaper and then replaced it with `ColorDrawable(Color.BLACK)`, so the default stub could not be seen. The stub keeps the theme wallpaper.

| File | Change |
| --- | --- |
| `TMessagesProj/src/main/assets/*.attheme` | Restored to `3de37c8cf`. |
| `TMessagesProj/src/main/java/org/telegram/ui/ActionBar/Theme.java`, `ThemeColors.java`, `MessageDrawable.java` | Restored. Tellomi bubble, accent, and doodle wallpaper overrides are gone. |
| `TMessagesProj/src/main/java/org/telegram/ui/ThemeActivity.java` | Built-in list and accent row follow `TelegramThemeFlow`. |
| `TMessagesProj/src/main/java/org/telegram/ui/WallpapersListActivity.java` | Local color presets are Telegram's. A failed wallpaper RPC does not clear them. Black `wallPaperNoFile` stays visible. |
| `TMessagesProj/src/main/java/org/telegram/ui/Cells/WallpaperCell.java`, `ThemeSmallPreviewView.java` | Default pattern drawing is back. A chat theme with no wallpaper document still draws. |
| `TMessagesProj/src/main/java/org/telegram/ui/ThemePreviewActivity.java` | Default-pattern preview is back. The sample link stays `TellomiBrand`. The preview title stays the app name. |
| `TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java`, `Stories/recorder/PreviewView.java` | Default chat-theme stub uses the theme wallpaper. A missing wallpaper document does not crash the pattern load. |
| `TMessagesProj/src/main/java/org/telegram/messenger/ChatThemeController.java`, `ChatThemeBottomSheet.java` | A failed gift-theme RPC still loads emoji themes. A failed emoji RPC keeps the cache and shows the default row. |
| `TMessagesProj/src/main/java/org/telegram/ui/theme/TelegramThemeFlow.java` | List, preview fill, wallpaper merge, and day/night persistence decisions. |
| `TMessagesProj/src/test/java/org/telegram/ui/theme/TelegramThemeFlowTest.java` | Those decisions, including a missing wallpaper and a failed request. |

## In-app APK download

The website release check already reads `https://www.tellomi.cn/dl/android/latest.json` and `https://dl.tellomi.cn/android/latest.json`. The swipe used to open that APK in the system browser. It now downloads with `DownloadManager` from an allowlisted https host, shows progress, and then opens the system installer. Closing the card cancels a download that is still running. It does not cancel a file that is already waiting on the unknown-sources screen. A second swipe does not enqueue another copy of the same job. After a process restart the saved download id is queried again. The first unknown-sources grant uses the existing settings screen; returning to the app continues with the same file. A document that is already on disk is checked the same way before the installer opens. A bad package, signature, or version deletes that file and the card says why, instead of installing it. The installer is the system one. Nothing installs silently and nothing is uninstalled.

Before that installer opens, the file must be a zip, the package name must be this app's, the signing certificate must equal the certificate of the running install, and `versionCode` must be higher. A sha256 in `latest.json` is checked only as an extra file hash. Two feeds that publish different hashes for the same version are not offered. A hash is never accepted instead of the signature. The website offer stays `can_not_skip = false`. No new force-update rule was added. `help.getAppUpdate` `can_not_skip` is unchanged.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/messenger/TellomiAppUpdate.java` | An allowlisted APK url is `DOWNLOAD_IN_APP`, not a browser. |
| `TMessagesProj/tellomi/java/org/telegram/messenger/TellomiApkUpdate.java` | Download state, zip markers, package, signature, version, and hash disagreement. |
| `TMessagesProj/tellomi/java/org/telegram/messenger/TellomiApkDownload.java` | `DownloadManager`, FileProvider, and the system installer. |
| `TMessagesProj/tellomi/java/org/telegram/messenger/TellomiReleaseUpdate.java` | Optional `sha256`. Conflicting hashes drop the offer. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiSoftUpdateDialog.java` | Progress, retry, and the installer. The card can still be closed. |
| `TMessagesProj/src/main/java/org/telegram/ui/Components/BlockingUpdateView.java` | The same in-app download when a forced server update carries an APK url. |
| `TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java` | Resume continues a finished download or a permission return. Website updates stay skippable. |
| `TMessagesProj/src/main/res/xml/provider_paths.xml` | `external-files-path` `Download/` for the update APK. |
| `TMessagesProj/src/test/java/org/telegram/messenger/TellomiApkUpdateTest.java` | State, a real zip, a bad signature, a bad package, and disagreeing hashes. |

## Secret view and per-chat password

Long-press on a dialog, and the profile menu, can open that chat without read receipts or add a local pin. Existing rows are unchanged. The pin is a salt and PBKDF2 hash in `tellomi_chat_lock_<account>`. It is not uploaded. Forgetting it requires the app passcode; if that passcode is not set, the chat lock stays.

| File | Change |
| --- | --- |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiQuietOpen.java` | Holds one chat so reads, typing, and, for secret view, the online heartbeat are skipped. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiChatLock.java` | Pin rules, hash, preview mask, and the app-passcode clear rule. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiChatLockStore.java` | Per-account local preferences. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiChatLockUi.java` | Set, change, biometric, unlock, and forgotten-pin dialogs. |
| `TMessagesProj/tellomi/java/org/telegram/ui/TellomiChatMenu.java` | Secret view arguments and the profile rows. |
| `TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java` | Two extra long-press rows. |
| `TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java` | The same two rows. |
| `TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java` | Banner, read-only bar, and the lock cover. |
| `TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java` | Quiet-open returns before read, reaction, mention, typing, and online RPCs. |
| `TMessagesProj/src/main/java/org/telegram/messenger/MessageObject.java` | Secret view does not clear reaction unread locally. |
| `TMessagesProj/src/main/java/org/telegram/messenger/NotificationsController.java` | Locked chats use the generic notification text. |
| `TMessagesProj/src/main/java/org/telegram/ui/Cells/DialogCell.java` | Locked chats show 已锁定 in the list and in search. |
