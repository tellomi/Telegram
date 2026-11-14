# Tellomi Android emulator UI tests

Maestro flows and a GitHub Actions matrix for the 157-row Tellomi backlog in `backlog.csv`. The flows live in `flows/`. One flow covers many backlog ids. The catalog in `lib/catalog.py` is the map from id to flow, and `manifest.json` is the same map in JSON.

This directory does not change the app. Camera, microphone, system call MIME, and push delivery are `device_only` and are not given a passing flow.

## Login

Production answers `99966XYYYY` before SMS. For datacenter digit 1 the code is `11111` (`teamgram-server` `docs/sms-email.md`). This suite only dials:

| Slot | Number | Code |
| --- | --- | --- |
| A | `9996617092` | `11111` |
| B | `9996617093` | `11111` |

The number is the whole phone the server sees. The login flow clears the country code before typing it. Prefixing `+86` makes `869996617092`, and production answers `PHONE_NUMBER_INVALID`.

`7001`–`7091` belong to other testers. A `TELLOMI_TEST_PHONE_A` or `TELLOMI_TEST_PHONE_B` secret that is empty uses the dedicated number above. Any other value is refused, including numbers outside `99966170xx`. `TELLOMI_TEST_PASSWORD` is optional and is only typed if the account has a cloud password.

Logged-in flows share one login per phone so a run does not burn the daily `auth.sendCode` cap. Logout is the last flow on that phone.

## What an emulator runs

Logged out: login terms, `https://tell.cc/`, `tellomi://`, `geo:`, and the profile MIME filter.

Logged in: settings, 设置 → 通用 → 导航栏, 设置 → 工具 → 选单设定, About/version/FAQ/privacy, QR, language (A-LANG-007), Moments feed/composer/audience/city/settings, and logout. A-LANG-007 reads `mainconfig` `language` with `run-as` on the debug build: automatic selection must leave it empty, and choosing 简体中文 must write a `zh` key.

Ids that need a fixture the emulator does not have (a deleted post, a report submission, account deletion, server DC config) are `blocked` with a reason. They are not marked pass.

## Workflow

`.github/workflows/tellomi-ui-tests.yml` is `workflow_dispatch` plus a nightly schedule. It builds one x86_64 debug APK, then runs up to 20 `reactivecircus/android-emulator-runner` shards (API 33 and 34, KVM). Each shard uploads `results.json` (backlog id → pass/fail/blocked/device_only, screenshot, error). The merge job writes `ui-results.json`.

App assertion failures stay in that file. The shard fails the job only when the emulator, APK install, or Maestro itself fails.

## Local checks

```bash
python3 -m unittest discover -s tellomi/uitest/android/tests -v
```
