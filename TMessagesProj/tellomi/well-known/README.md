# App Links for tell.cc

Host this file at `https://tell.cc/.well-known/assetlinks.json` so Android can verify
`https://tell.cc/...` links for `com.tellomi.app` (`android:autoVerify="true"`).

The fingerprint in `assetlinks.json` is the SHA256 of the dummy
`TMessagesProj/config/release.keystore` (`alias` `androidkey`) that this tree uses for
debug and release signing. Replace it when you ship your own key.

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
adb shell pm get-app-links com.tellomi.app
```

`tell.cc` should be `verified` after the statement is live and the cert matches.
