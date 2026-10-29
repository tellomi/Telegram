# App Links for tell.cc

Host this file at `https://tell.cc/.well-known/assetlinks.json` so Android can verify
`https://tell.cc/...` links for `app.tellomi.chat` (`android:autoVerify="true"`).

The fingerprint in `assetlinks.json` is the SHA-256 of the release signing
certificate for `app.tellomi.chat`:

`6E:2B:1B:8C:F1:01:25:D2:64:68:A4:EA:BE:42:F3:48:8C:0A:D3:EF:AE:E6:30:C2:5A:18:F4:0A:7F:9C:08:ED`

Local debug builds still sign with the dummy `TMessagesProj/config/release.keystore`
(`alias` `androidkey`). The release APK from GitHub Actions uses the keystore in
`ANDROID_KEYSTORE_BASE64`. Replace the fingerprint if that release key changes.

## Get the release signing SHA256

From the repo root, using the alias and store password in `gradle.properties`
(`RELEASE_KEY_ALIAS`, `RELEASE_STORE_PASSWORD`):

```bash
keytool -list -v \
  -keystore TMessagesProj/config/release.keystore \
  -alias "$RELEASE_KEY_ALIAS"
```

Copy the `SHA256:` line into `sha256_cert_fingerprints`. Colon-separated hex is the form
Digital Asset Links expects. If debug and release use different keystores, list both
fingerprints.

Confirm the installed app:

```bash
adb shell pm get-app-links app.tellomi.chat
```

`tell.cc` should be `verified` after the statement is live and the cert matches.
