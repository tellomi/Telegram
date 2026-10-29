# Tellomi Android client

Tellomi is this fork of the official Telegram Android client (DrKLO/Telegram, layer 232). It keeps every Telegram feature, including Stories. Nothing in the UI feature set is removed. The app is meant to talk to a [Teamgram](https://github.com/teamgram/teamgram-server) server upgraded to layer 232 ([tellomi/teamgram-server#1](https://github.com/tellomi/teamgram-server/pull/1)), not to Telegram's datacenters.

Protocol types, Java package `org.telegram.*`, and MTProto class names are unchanged so upstream merges stay small. User-visible product name, application id, launcher icon, API credentials, datacenter list, and server RSA key are isolated in the build config below.

## Configure the server

All of the knobs live in the root `gradle.properties`. They are copied into `org.telegram.messenger.BuildConfig` by `TMessagesProj/build.gradle` and read from `org.telegram.messenger.BuildVars`. Native code picks them up once, in `ConnectionsManager.applyTellomiBackend()`, before the first connection.

| Property | Default | Meaning |
| --- | --- | --- |
| `APP_PACKAGE` | `app.tellomi.chat` | Android `applicationId`. Debug builds of `TMessagesProj_App` do **not** append `.beta`. |
| `TELLOMI_APP_NAME` | `Tellomi` | Launcher name is set in string resources. This value is also the in-app brand rewrite and the on-disk media folder name. |
| `TELLOMI_API_ID` | `1` | `api_id` sent with `auth.sendCode`. Placeholder, not Telegram's app id `4`. |
| `TELLOMI_API_HASH` | `0123456789abcdef0123456789abcdef` | `api_hash` placeholder. Replace before any deployment that checks it. |
| `TELLOMI_SERVER_PROFILE` | unset | `dev` or `local`. When unset, **debug** builds use `dev` and every other build type uses `local`. |
| `TELLOMI_PROFILE_DEV` | `1:8.137.39.82:10443,2:8.137.39.82:10443` | Live dev Teamgram (gnetway TCP). DC 1 and DC 2 are the same IP. `api.tellomi.cn` is the DNS name for that IP; the client cannot use the hostname. |
| `TELLOMI_PROFILE_LOCAL` | `1:10.0.2.2:10443,2:10.0.2.2:10443` | Android emulator alias for a Teamgram docker on the host. |
| `TELLOMI_DC_ADDRESSES` | unset | Optional explicit `dcId:host:port` list. When set, it replaces the selected profile. Bracket IPv6 hosts: `1:[2001:db8::1]:10443`. |
| `TELLOMI_DEFAULT_DC_ID` | `1` | Datacenter used for a fresh login. Must appear in the address list. |
| `TELLOMI_CUSTOM_BACKEND` | `true` | When true, Telegram production and test DC IPs are never installed, and the Telegram DNS bootstrap (`apv3.stel.com` / `tapv3.stel.com`) is skipped. |
| `TELLOMI_LOCK_DC_ADDRESSES` | `true` | When true (and the custom backend is on), `help.getConfig` does not replace the build-config addresses. |
| `TELLOMI_USE_CLOUD_STRINGS` | `false` | Telegram langpack download (`translations.telegram.org` / `langpack.*`). |
| `TELLOMI_CHECK_UPDATES` | `false` | Automatic `help.getAppUpdate` checks. |
| `TELLOMI_BILLING_UNAVAILABLE` | `true` | Skips Google Play Billing for Telegram product ids. |
| `TELLOMI_REBRAND_STRINGS` | `true` | Rewrites the word "Telegram" (and a few translated forms) inside `LocaleController` strings. |
| `TELLOMI_DOWNLOAD_URL` | `https://tellomi.app` | Invite-SMS fallback and the store buttons that used to open Telegram's Play / AppGallery listings. |
| `TELLOMI_SERVER_PUBLIC_KEY` | unset | Optional one-line PKCS#1 (or PKCS#8) PEM. In `gradle.properties`, write newlines as `\\n`. When unset, the build reads `TMessagesProj/config/tellomi-server-public.pem`. |

Set `TELLOMI_CUSTOM_BACKEND=false` only if you intentionally want the official Telegram DCs and RSA keys again.

### Server profiles

```bash
# Debug APK, live dev server (the default):
./gradlew :TMessagesProj_App:assembleAfatDebug

# Debug APK, local docker instead:
./gradlew :TMessagesProj_App:assembleAfatDebug -PTELLOMI_SERVER_PROFILE=local

# Any build type, live dev server:
./gradlew :TMessagesProj_App:assembleAfatRelease -PTELLOMI_SERVER_PROFILE=dev
```

`dev` is `8.137.39.82:10443` TCP (Teamgram gnetway, branch `cursor/channels-supergroups-39cf` at `5f92342`). DC 1 and DC 2 are that same address. The SMS code on that server is **12345**. `local` stays `10.0.2.2:10443` for an emulator talking to docker on the development machine.

`api.tellomi.cn` is a DNS-only name for that server (A record `8.137.39.82`, port 10443). The dev profile keeps the IP. `ConnectionSocket::openConnection` connects a datacenter address with `inet_pton` only: an IPv4 or IPv6 literal is accepted, and anything else is closed as `bad ipv4` / `bad ipv6`. Hostname lookup exists only for a proxy address (`getHostByName` / `gethostbyname`), not for the DC list in `initDatacenters`. If the A record changes, update `TELLOMI_PROFILE_DEV` to the new IP. Do not put `api.tellomi.cn` in the profile.

### Public links (`tell.cc`)

`TELLOMI_LINK_HOST` (`BuildVars.LINK_HOST`, default `tell.cc`) is the only host used when the app builds a public link (`https://tell.cc/username`, `/+hash`, `/joinchat`, `/c/`, `/s/`, `/addstickers`, `/addemoji`, `/proxy`, `/socks`, `/share`, stories, `?start`, and the rest of the t.me path set). `TellomiBrand.rewrite` also shows `t.me`, `telegram.me`, `telegram.dog`, and `telegram.org` as `tell.cc` in strings. Parsing still accepts `t.me`, `telegram.me`, and `telegram.dog`. Help, privacy, and terms use `BuildVars.SITE_HOST` (`tellomi.cn`): `HELP_URL`, `PRIVACY_URL`, and `TOS_URL`. `TELLOMI_LINK_SCHEME` (`BuildVars.LINK_SCHEME`, default `tellomi`) is accepted as the same routes as `tg://` (`tellomi://resolve?domain=name`, and so on). `tg://` itself still works.

`help.getConfig`'s `me_url_prefix` is kept only when it is already `tell.cc` or some other non-Telegram host. `t.me`, `telegram.me`, and `telegram.dog` are rewritten to `tell.cc`. Those three hosts are still recognized inside the app so older messages open, but the manifest does not claim them.

App Links: `AndroidManifest` has an `https`/`http` `tell.cc` intent filter with `android:autoVerify="true"`, plus `tellomi` next to `tg`. The Digital Asset Links statement is `TMessagesProj/tellomi/well-known/assetlinks.json`. Host it at `https://tell.cc/.well-known/assetlinks.json`. How to read the signing SHA256 is in `TMessagesProj/tellomi/well-known/README.md`. The checked-in fingerprint is the release certificate SHA-256 `6E:2B:1B:8C:F1:01:25:D2:64:68:A4:EA:BE:42:F3:48:8C:0A:D3:EF:AE:E6:30:C2:5A:18:F4:0A:7F:9C:08:ED` for `app.tellomi.chat`. Debug builds still sign with the dummy `TMessagesProj/config/release.keystore` (`androidkey`). Replace the fingerprint when the release key changes. The manifest host and scheme are literals; if you change `TELLOMI_LINK_HOST` or `TELLOMI_LINK_SCHEME`, update `AndroidManifest.xml` to match.

### Local Teamgram (docker)

Teamgram's docker compose publishes gnetway on **TCP 10443** (also 5222 and websocket 11443). The client uses 10443. Select it with `-PTELLOMI_SERVER_PROFILE=local`.

| Where the app runs | Host to put in `TELLOMI_DC_ADDRESSES` |
| --- | --- |
| Android emulator | `10.0.2.2` (the emulator's alias for the development machine) |
| Device on the same LAN | The machine's LAN IP, for example `192.168.1.20` |
| `adb reverse tcp:10443 tcp:10443` | `127.0.0.1` |

Both profiles list **DC 1 and DC 2 on the same host and port**. Teamgram's single datacenter is DC 1 (`ThisDC`). The stock client used to assume DC 2 when it had no saved config; registering both ids means a `PHONE_MIGRATE_*` / `USER_MIGRATE_*` to either id still reaches that port.

`TELLOMI_LOCK_DC_ADDRESSES=true` matters for docker. `help.getConfig` often advertises `127.0.0.1` or an in-container address. With the lock on, the client keeps the address from `gradle.properties` after that response. For a real multi-DC deployment, set the lock to `false` and make the server advertise addresses the phone can route to.

The checked-in PEM is the **Teamgram docker dev public key**, the PKCS#1 form of `teamgramd/bin/server_pkcs1.key`. Fingerprint `12240908862933197005` (`0xa9e071c1771060cd`), which is the `KeyFingerprint` in Teamgram's `gnetway.yaml`. Replace it for any server that generated its own key:

```bash
openssl rsa -in server_pkcs1.key -pubout -outform PEM -RSAPublicKey_out \
  -out TMessagesProj/config/tellomi-server-public.pem
```

Dev sign-in on that Teamgram build uses the fixed SMS code **12345**. That is server behavior; the client does not special-case it. The SMS Retriever hash in `BuildVars.getSmsHash()` is still Telegram's and will not match `app.tellomi.chat`, so autofill of the code will not work until you register a hash for this application id.

`api_id` / `api_hash` are sent on the auth requests. Teamgram's dev server does not check them against Telegram. They are obvious placeholders (`1` and `0123456789abcdef0123456789abcdef`). Put your own values in before a deployment that validates them.

### Build

Requirements are the upstream ones: Android SDK 36, build-tools 36.0.0, NDK 27.2.12479018, CMake 3.22.1.

```bash
echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties   # local.properties is gitignored
./gradlew :TMessagesProj_App:assembleAfatDebug
```

`local.properties` is not committed. `assembleDebug` / `assembleAfatDebug` is the variant that installs as `app.tellomi.chat`. To compile one ABI while iterating:

```bash
./gradlew :TMessagesProj_App:assembleAfatDebug -PTELLOMI_ABI_FILTERS=arm64-v8a
```

Omit the property to build `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`. The property is read by both `TMessagesProj` and `TMessagesProj_App`. GitHub Actions builds that same fat APK; the signing secrets and how to download the artifact are in `TMessagesProj/tellomi/ANDROID_APK.md`. Other application modules still append suffixes (`.web` on standalone, `.beta` on HockeyApp). `google-services.json` includes placeholder clients for `app.tellomi.chat`, `app.tellomi.chat.beta`, and `app.tellomi.chat.web` so those variants configure. Push will not be delivered until you replace those files with a Firebase project you control (see below).

## What changed

### Branding

- `APP_PACKAGE` is `app.tellomi.chat`. `TMessagesProj_App` debug no longer adds `.beta`, so the installed id is exactly that.
- `AppName` and `AppNameBeta` are `Tellomi` in every `values*/strings.xml` shipped in this tree (launcher label and package-manager name).
- `TellomiBrand` rewrites `Telegram`, `تيليجرام`, `텔레그램`, `टेलीग्राम`, and `电报` to `TELLOMI_APP_NAME` inside `LocaleController.getString` / `formatString` / `getServerString`. String resource **keys** (`TelegramFaqUrl`, and so on) are not renamed.
- Service-user display names (`id` 333000 and 777000) use the app name.
- Media directories are `Tellomi`, `Tellomi Images`, `Tellomi Video`, `Tellomi Audio`, `Tellomi Documents`, `Tellomi Files`, `Tellomi Stories`. The scoped-storage migration looks for `Tellomi` on external storage, not the official client's `Telegram` folder, so it will not move that folder.
- Contacts account type follows `APP_PACKAGE` (`BuildVars.ACCOUNT_TYPE`). `res/xml/auth.xml`, `auth_menu.xml` (`android:targetPackage`), and `sync_contacts.xml` are hardcoded to `app.tellomi.chat` and must be updated if `APP_PACKAGE` changes.
- Contact shortcut MIME types are `vnd.android.cursor.item/vnd.app.tellomi.chat.profile`, `.call`, and `.call.video` (manifest, `contacts.xml`, `ContactsController`, `LaunchActivity`).
- The system account icon and the default launcher icon are the Tellomi mark (speech bubble with @). See below.
- The last-resort files directory in `ApplicationLoader.getFilesDirFixed()` uses the real package name.

### Launcher icon

The default icon is the Tellomi mark from `docs/brand/logo.svg` in [tellomi/tellomi](https://github.com/tellomi/tellomi) (traced by `scripts/brand/trace-logo.py`; `currentColor`). A copy of that SVG is `TMessagesProj/tellomi/brand/logo.svg`. Light theme draws it in `#111113` on `#FFFFFF`. Dark theme draws it in `#FFFFFF` on `#111113`. Android 13 themed icons use the same mark as a white monochrome alpha mask.

Those assets are the `tellomi` product flavor, directory `TMessagesProj/tellomi/res`, not edits to DrKLO's mipmaps:

- Adaptive icon, API 26+: `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` (background, foreground, monochrome). Day and night colors are `values/tellomi_launcher_colors.xml` and `values-night/tellomi_launcher_colors.xml`.
- Legacy mipmaps, API 23–25: `mipmap-*/ic_launcher.png` and `ic_launcher_round.png`, plus `mipmap-night-*` for night mode.
- Notification small icon: `drawable-*/notification.webp` (white mark, transparent background). `NotificationsController` still references `R.drawable.notification`.

`src/main/res/mipmap-*/ic_launcher*.png` and the upstream adaptive XML are restored DrKLO files. The flavor replaces them at merge time. Regenerate with `python3 TMessagesProj/tellomi/brand/render_launcher_icons.py`.

In-app alternate icons (`LauncherIconController`, `icon_foreground*.png`, `icon_plane.xml`, `ic_launcher_dr.webp`, standalone `ic_launcher_sa`) are still the upstream artwork. They are not the default launcher icon.

### Datacenters and RSA key

Isolated in `TMessagesProj/jni/tgnet/TellomiNetConfig.cpp`, called from three upstream sites:

- `ConnectionsManager::initDatacenters` returns after adding only `TELLOMI_DC_ADDRESSES` when the custom backend is on.
- A fresh `currentDatacenterId` becomes `TELLOMI_DEFAULT_DC_ID` instead of `2`.
- `help.getConfig` skips `replaceAddresses` while the lock is on.
- `Handshake` loads the configured PEM instead of Telegram's production/test keys, and computes the fingerprint the same way as the CDN-key path (SHA1 of the TL-encoded modulus and exponent, low 8 bytes as a little-endian `uint64`). PKCS#1 (`BEGIN RSA PUBLIC KEY`) and PKCS#8 (`BEGIN PUBLIC KEY`) are both accepted.

`ConnectionsManager.onRequestNewServerIpAndPort` returns immediately when the custom backend is on, so a failed connection cannot pull Telegram DC addresses from Google/Mozilla DNS TXT records.

### Credentials and services turned off

In `BuildVars`:

- `APP_ID` / `APP_HASH` come from the properties above.
- `SAFETYNET_KEY` is empty, which is the existing switch that skips Play Integrity / SafetyNet during login.
- `SUPPORTS_PASSKEYS` is false (passkeys are limited to official application ids).
- `IS_BILLING_UNAVAILABLE` defaults to true.
- `USE_CLOUD_STRINGS` and `CHECK_UPDATES` default to false.
- `PLAYSTORE_APP_URL`, `HUAWEI_STORE_URL`, and the invite-SMS fallback (`ContactsController`, `NewContactBottomSheet`) use `TELLOMI_DOWNLOAD_URL` instead of `play.google.com` / `telegram.org/dl`.
- `GOOGLE_AUTH_CLIENT_ID` is a non-functional placeholder (`000000000000-tellomi.apps.googleusercontent.com`). The server can still overwrite it from config. The Google sign-in button is still on the login screen.

`google-services.json` in each app module gained client entries for the Tellomi package names so the Google Services plugin can configure the build. The Firebase project id, API key, and OAuth client inside those entries are still the ones upstream ships for `org.telegram.messenger`. **FCM push will not arrive** until you replace `google-services.json` with one from a project that contains `app.tellomi.chat`. The old `org.telegram.messenger` clients were left in the file so the library module's plugin still matches namespace `org.telegram.messenger`.

## What still points at Telegram

These were left in place on purpose. They are either in-app deep links (resolved over MTProto against **your** server), protocol identifiers, or informational URLs that are not called during login.

**Still works against your server (do not rename):**

- `t.me`, `telegram.me`, and `telegram.dog` link parsing. The app intercepts them and resolves usernames with MTProto. After login, `help.getConfig`'s `me_url_prefix` replaces the displayed link prefix.
- `tg://` links.
- JavaScript bridges `TelegramWebviewProxy` / `TelegramWebviewProxyMessage` / `TelegramWebProxy`. Mini-app pages call those names.
- WebView referer `https://messenger.telegram.org/` on YouTube embeds (`PhotoViewerWebView`, `EmbedBottomSheet`). That header is how the embed is allowed to play; it is not a Tellomi API.
- Contact MIME prefix shape and the intent action `org.telegram.messenger.OPEN_ACCOUNT` (action string, not a package).
- Class names, JNI library `tmessages.49`, preferences file names, and the `org.telegram.messenger` Java package / namespace.

**Left as telegram.org URLs inside strings (not requested automatically):**

FAQ, privacy, terms, mini-app ToS, ads (`ads.telegram.org`), desktop/web client hints (`getdesktop.telegram.org`, `web.telegram.org`), passport FAQ, stars/monetization policy links, emoji suggestion host `translations.telegram.org`, and similar `*Url` string resources. With cloud strings off, the English (and bundled locale) copy is what the UI shows. Opening one of those rows still loads telegram.org in the browser. Search `telegram.org` under `TMessagesProj/src/main/res/values/strings.xml` for the full English set. Other locales keep their own copies of the same links.

**Still compiled, will fail or no-op until you replace keys:**

- FCM, as described above.
- Google Maps key in the debug/release manifests (`com.google.android.maps.v2.API_KEY`) is still Telegram's. Maps tiles may be denied.
- `BuildVars.HUAWEI_APP_ID` is still Telegram's AppGallery id. The Huawei store **URL** was pointed at `TELLOMI_DOWNLOAD_URL`.
- `BuildVars.getSmsHash()` is still the official-app hash.
- Upstream alternate launcher bitmaps (see above). The default `ic_launcher` / `notification` artwork is overridden by the `tellomi` resource flavor.
- Features that need server support beyond core messaging (Stars payments, Premium billing, some business/mini-app endpoints, Telegram-hosted wallpapers and stickers) are still in the client. Teamgram may not implement every layer-232 method. The screens are still there.

## Upstream merges

Prefer editing `gradle.properties`, `TMessagesProj/config/tellomi-server-public.pem`, `BuildVars`, `TellomiBrand`, and `TellomiNetConfig.*`. The touch points in upstream files are marked `TELLOMI` or call `TellomiNetConfig` / `TellomiBrand` / `BuildVars` fields introduced above. Do not rename MTProto classes or the `org.telegram` package when merging.
