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

# google_apis API 33/34 images are userdebug, but adbd starts as the shell
# user. persist.sys.locale is system_locale_prop, and the shell domain is not
# allowed to set it. `setprop` then exits 1 with:
#   Failed to set property 'persist.sys.locale' to 'zh-Hans-CN'.
#   See dmesg for error reason.
# Emulator -prop only accepts qemu.* keys, so it cannot set this either.
# `adb root` restarts adbd as root on these images; that domain can set the
# property. The shard still fails unless the property and the live
# configuration are Simplified Chinese after the framework reads them.
is_simplified_chinese() {
  case "$1" in
    zh-Hans-CN|zh-CN|zh-Hans|zh_Hans_CN|zh_CN|zh_Hans) return 0 ;;
    *) return 1 ;;
  esac
}

adb wait-for-device
echo "ro.build.type=$(adb shell getprop ro.build.type | tr -d '\r')"
echo "ro.debuggable=$(adb shell getprop ro.debuggable | tr -d '\r')"
echo "ro.build.version.sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
root_out=$(adb root 2>&1 || true)
echo "adb root: ${root_out}"
adb wait-for-device
root_uid=""
for _ in $(seq 1 30); do
  root_uid=$(adb shell id -u 2>/dev/null | tr -d '\r' || true)
  if [ "$root_uid" = "0" ]; then
    break
  fi
  sleep 1
done
if [ "$root_uid" != "0" ]; then
  echo "cannot set persist.sys.locale: adb root did not yield uid 0 (uid='${root_uid}')" >&2
  exit 1
fi

adb shell settings put system system_locales zh-Hans-CN
adb shell setprop persist.sys.locale zh-Hans-CN
set_got=$(adb shell getprop persist.sys.locale | tr -d '\r')
echo "persist.sys.locale immediately after set=${set_got}"
if ! is_simplified_chinese "$set_got"; then
  echo "zh-Hans-CN was not applied (persist.sys.locale='${set_got}')" >&2
  exit 1
fi

# stop/start does not clear sys.boot_completed. Clear it so a stale "1"
# cannot pass the wait below, then confirm the framework actually stopped
# before starting it again. The cleared property is not proof of a stop;
# only a failed package manager query is.
adb shell setprop sys.boot_completed 0 || true
if ! adb shell stop; then
  echo "adb shell stop failed; rebooting the emulator so the locale is read" >&2
  adb reboot
else
  down=0
  for _ in $(seq 1 30); do
    pm=$(timeout 15 adb shell pm path android 2>/dev/null | tr -d '\r' || true)
    if [ -z "$pm" ]; then
      down=1
      break
    fi
    sleep 1
  done
  if [ "$down" != "1" ]; then
    echo "adb shell stop did not stop the framework; rebooting the emulator so the locale is read" >&2
    adb reboot
  else
    adb shell start || {
      echo "adb shell start failed; rebooting the emulator so the locale is read" >&2
      adb reboot
    }
  fi
fi
adb wait-for-device
ready=0
for _ in $(seq 1 90); do
  boot=$(timeout 15 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  pm=$(timeout 15 adb shell pm path android 2>/dev/null | tr -d '\r' || true)
  if [ "$boot" = "1" ] && [ -n "$pm" ]; then
    ready=1
    break
  fi
  sleep 2
done
if [ "$ready" != "1" ]; then
  echo "emulator did not finish booting after the locale change" >&2
  exit 1
fi

# A-LANG-007 uses run-as, which refuses uid 0. The locale properties stay set.
unroot_out=$(adb unroot 2>&1 || true)
echo "adb unroot: ${unroot_out}"
adb wait-for-device
shell_uid=""
for _ in $(seq 1 30); do
  shell_uid=$(adb shell id -u 2>/dev/null | tr -d '\r' || true)
  if [ "$shell_uid" = "2000" ]; then
    break
  fi
  sleep 1
done
if [ "$shell_uid" != "2000" ]; then
  echo "adb unroot did not return to the shell user (uid='${shell_uid}')" >&2
  exit 1
fi

got=$(adb shell getprop persist.sys.locale | tr -d '\r')
sysloc=$(adb shell settings get system system_locales | tr -d '\r')
echo "persist.sys.locale=${got}"
echo "system_locales=${sysloc}"
if ! is_simplified_chinese "$got"; then
  echo "zh-Hans-CN was not applied (persist.sys.locale='${got}')" >&2
  exit 1
fi
first_sys="${sysloc%%,*}"
case "$first_sys" in
  null|"") ;;
  *)
    if ! is_simplified_chinese "$first_sys"; then
      echo "zh-Hans-CN was not applied (system_locales='${sysloc}')" >&2
      exit 1
    fi
    ;;
esac

runtime=""
for _ in $(seq 1 15); do
  line=$(timeout 20 adb shell "dumpsys window | grep -m 1 mGlobalConfiguration" 2>/dev/null | tr -d '\r' || true)
  if [ -z "$line" ]; then
    line=$(timeout 20 adb shell "dumpsys activity | grep -m 1 mGlobalConfiguration" 2>/dev/null | tr -d '\r' || true)
  fi
  runtime=$(printf '%s\n' "$line" | sed -n 's/^[^[]*\[\([^],]*\).*/\1/p')
  echo "runtime_locale=${runtime:-<empty>}"
  if is_simplified_chinese "$runtime"; then
    break
  fi
  sleep 2
done
if ! is_simplified_chinese "$runtime"; then
  echo "zh-Hans-CN was not applied (runtime locale='${runtime}')" >&2
  exit 1
fi

python3 tellomi/uitest/android/scripts/run_shard.py \
  --plan "${PLAN:?}" \
  --shard "${SHARD:?}" \
  --apk "${APK:?}" \
  --out "${OUT:?}" \
  --api "${API_LEVEL:?}"
