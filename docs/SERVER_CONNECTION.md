# Official client, own server

The source tree at `8a606f5b0` matches DrKLO `3de37c8cfb039452be7398909461da6288babf15` (`update to 13.1.1 (7237)`, Arseny271, 2026-10-25). Marketing version 14.0.7 was the shipped Tellomi app, not that upstream revision.

This layer is the only intentional difference from that tree:

- `applicationId` stays `app.tellomi.chat`, the id of the installed 14.0.7 package. Private files stay in that package's directory. The release signature is still the existing CI keystore. `14.0.8` / `APP_VERSION_CODE` `180467` makes the universal afat package `1804679` (`* 10 + 9`). `bundleAfat` is `1804671`. This project has no abi digit 0, so it does not emit `1804670`.
- Datacenters, the server RSA key (`TMessagesProj/config/tellomi-server-public.pem`, bytes from `origin/master` `8cb628f29`), and `api_id` `1` are build config. `TELLOMI_CUSTOM_BACKEND` skips Telegram production DCs and `apv3.stel.com`. A shippable build refuses anything other than `8.137.39.82:10443`. Debug may use `10.0.2.2` only with `-PTELLOMI_SERVER_PROFILE=local`.
- Cloud langpack stays off. SafetyNet and the Google OAuth client are empty so this package does not present Telegram's keys.
- Launcher, splash (`tg_splash_320`), notification, about `book_logo`, and the chat-list wordmark (`telegram_logo` / `telegram_logo_2`) are the bytes from `8cb628f29` `TMessagesProj/tellomi/res`, written onto the resource names the official manifest and screens already load. `AppName` is Tellomi. Functional icons are the official ones. GPL headers and protocol class names are unchanged.

Not claimed:

- Login, chat, and an upgrade from `1804669` have not been run on a device. Compiling is not a safe overwrite.
- Play billing, passkeys, and the Play/AppGallery links still point at Telegram's products.
- If Teamgram rejects api_id 1 or an official RPC, the client reports the server error. It does not invent a replacement API.
