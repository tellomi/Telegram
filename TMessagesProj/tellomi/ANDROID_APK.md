# Tellomi Android APK

GitHub Actions workflow: [`.github/workflows/android-apk.yml`](../../.github/workflows/android-apk.yml).

It builds the `tellomi` flavor of `TMessagesProj_App` (the `afat` variant, application id `app.tellomi.chat`) and the native library `libtmessages.49.so` for `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`. That is the same APK as a local `./gradlew :TMessagesProj_App:assembleAfatDebug` (or `assembleAfatRelease` when the signing secrets below are set).

The workflow runs on:

- every `push`
- every `pull_request`
- `workflow_dispatch` (Actions → **Android APK** → **Run workflow**)

Toolchain, matching `TMessagesProj/build.gradle`: JDK 17, Android SDK 36, build-tools 36.0.0, NDK **27.2.12479018**, CMake **3.22.1**. Each ABI is compiled on its own runner (full LTO does not fit four ABIs on one GitHub-hosted disk), then a package job assembles the fat APK. Gradle dependencies, ccache, and an unchanged `libtmessages.49.so` are cached between runs.

## Debug and release

Debug is the default. The APK is signed with the checked-in dummy keystore `TMessagesProj/config/release.keystore` (alias `androidkey`, store and key password `android`), the same key a local debug build uses.

The workflow switches to `assembleAfatRelease` only when **all four** repository Actions secrets are set. If any one is missing, the run stays on debug.

| Secret | What to put in it |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | The keystore file, base64, one line. `base64 -w0 your-release.keystore` |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore store password. The runner writes it to `RELEASE_STORE_PASSWORD`. |
| `ANDROID_KEY_ALIAS` | Key alias inside that keystore. The runner writes it to `RELEASE_KEY_ALIAS`. |
| `ANDROID_KEY_PASSWORD` | Key password. The runner writes it to `RELEASE_KEY_PASSWORD`. |

Add them in GitHub: **Settings → Secrets and variables → Actions → New repository secret**.

On a release run the runner decodes the keystore over `TMessagesProj/config/release.keystore` in the job workspace and rewrites the three `RELEASE_*` lines in that job's `gradle.properties`. Nothing is committed. Passwords should be a single ASCII line; the properties file is not a place for newlines.

The checked-in dummy key stays in git. Release signing on your laptop is still the steps in the root `README.md`: copy your keystore to `TMessagesProj/config/release.keystore` and set `RELEASE_KEY_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_STORE_PASSWORD`.

## Download the APK

1. Open the repository **Actions** tab.
2. Open **Android APK** and select the run (a push, a pull request, or a manual run).
3. Wait until the **Package Tellomi APK** job is green.
4. In the run summary, download the artifact named `tellomi-debug` or `tellomi-release`.
5. Unzip it. The file inside is `tellomi-debug.apk` or `tellomi-release.apk`.

The workflow page is `https://github.com/tellomi/Telegram/actions/workflows/android-apk.yml`. Each run's URL looks like `https://github.com/tellomi/Telegram/actions/runs/<run-id>`. The artifact is on that page; it is not attached to the pull request.

**Run workflow** is listed only when this filename exists on the default branch (`master`). The copy on `master` is a registration stub. Choosing another branch runs that branch's `android-apk.yml`.

Install a debug build with `adb install -r tellomi-debug.apk`. The package id is `app.tellomi.chat`.

## What CI changes in the build

Local builds do not set these.

- `TELLOMI_CI_NATIVE` (environment) includes `TMessagesProj/tellomi/ci/native-ci.cmake`, which turns on ccache and limits ninja to two compile jobs and one link job.
- `-PTELLOMI_PREBUILT_JNI=<dir>` on the package job skips CMake and packs `<dir>/<abi>/libtmessages.49.so`. The native jobs produced those libraries with the normal CMake build.
- `-PAPP_VERSION_CODE=$((7238 + github.run_number))` on the package job. The afat manifest `versionCode` is `APP_VERSION_CODE * 10 + 9`, so the APK is `72389 + github.run_number * 10`. That stays above the shipped 72379 and increases on every run. The ones digit stays 9 (universal). Checked-in `APP_VERSION_CODE=7237` is left alone, so a local assemble still packages 72379. The job fails if `aapt dump badging` does not report the computed code.
