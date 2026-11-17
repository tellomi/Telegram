#!/bin/bash
# Runs inside reactivecircus/android-emulator-runner, with adb already pointed at the booted emulator.
set -euo pipefail
if [ -n "${JAVA_HOME_17_X64:-}" ]; then
  export JAVA_HOME="$JAVA_HOME_17_X64"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
export PATH="${PATH}:${HOME}/.maestro/bin"
export TELLOMI_TEST_PASSWORD="${TELLOMI_TEST_PASSWORD:-}"
cd "${GITHUB_WORKSPACE:?}"

# Emulator -prop accepts only qemu.* keys, so persist.sys.locale on the
# emulator command line is ignored and the image stays en-US. Set the
# locale after boot and restart the framework so the login screen and
# A-LANG-007 see zh-Hans-CN.
adb wait-for-device
adb shell settings put system system_locales zh-Hans-CN
adb shell setprop persist.sys.locale zh-Hans-CN
adb shell setprop persist.sys.language zh
adb shell setprop persist.sys.country CN
if ! adb shell stop; then
  echo "adb shell stop failed; rebooting the emulator so the locale is read" >&2
  adb reboot
else
  adb shell start
fi
adb wait-for-device
ready=0
for _ in $(seq 1 90); do
  boot=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  if [ "$boot" = "1" ]; then
    ready=1
    break
  fi
  sleep 2
done
if [ "$ready" != "1" ]; then
  echo "emulator did not finish booting after the locale change" >&2
  exit 1
fi
got=$(adb shell getprop persist.sys.locale | tr -d '\r')
echo "persist.sys.locale=${got}"
echo "system_locales=$(adb shell settings get system system_locales | tr -d '\r')"
case "$got" in
  zh-Hans-CN|zh-CN|zh-Hans) ;;
  *)
    echo "zh-Hans-CN was not applied (persist.sys.locale='${got}')" >&2
    exit 1
    ;;
esac

python3 tellomi/uitest/android/scripts/run_shard.py \
  --plan "${PLAN:?}" \
  --shard "${SHARD:?}" \
  --apk "${APK:?}" \
  --out "${OUT:?}" \
  --api "${API_LEVEL:?}"
