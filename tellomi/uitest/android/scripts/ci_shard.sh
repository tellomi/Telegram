#!/bin/bash
# Runs inside reactivecircus/android-emulator-runner, with adb already pointed at the booted emulator.
set -u
if [ -n "${JAVA_HOME_17_X64:-}" ]; then
  export JAVA_HOME="$JAVA_HOME_17_X64"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
export PATH="${PATH}:${HOME}/.maestro/bin"
export TELLOMI_TEST_PASSWORD="${TELLOMI_TEST_PASSWORD:-}"
cd "${GITHUB_WORKSPACE:?}"
python3 tellomi/uitest/android/scripts/run_shard.py \
  --plan "${PLAN:?}" \
  --shard "${SHARD:?}" \
  --apk "${APK:?}" \
  --out "${OUT:?}" \
  --api "${API_LEVEL:?}"
