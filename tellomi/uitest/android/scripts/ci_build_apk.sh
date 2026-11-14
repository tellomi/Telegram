#!/bin/bash
# Build one x86_64 debug APK for the emulator. Does not read the release keystore.
set -euo pipefail
cd "${GITHUB_WORKSPACE:?}"

if [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  echo "ANDROID_SDK_ROOT is not set" >&2
  exit 1
fi
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties

SO="tellomi-native-out/x86_64/libtmessages.49.so"
if [ ! -f "$SO" ]; then
  echo "Native cache miss. Building libtmessages.49.so for x86_64."
  mkdir -p "$HOME/.gradle"
  cat > "$HOME/.gradle/gradle.properties" <<'EOF'
org.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=768m
org.gradle.parallel=false
org.gradle.workers.max=2
org.gradle.vfs.watch=false
org.gradle.daemon=true
EOF
  ./gradlew :TMessagesProj:externalNativeBuildDebug \
    -PTELLOMI_ABI_FILTERS=x86_64 \
    -Dorg.gradle.jvmargs="-Xmx3g -XX:MaxMetaspaceSize=768m" \
    -Dorg.gradle.parallel=false \
    -Dorg.gradle.workers.max=2 \
    --stacktrace \
    --console=plain
  built=$(find TMessagesProj/build TMessagesProj/.cxx -path '*/x86_64/*' -name 'libtmessages.49.so' -type f -printf '%s\t%p\n' 2>/dev/null | sort -nr | head -1 | cut -f2- || true)
  if [ -z "${built:-}" ]; then
    echo "libtmessages.49.so was not produced for x86_64" >&2
    exit 1
  fi
  mkdir -p tellomi-native-out/x86_64
  cp -f "$built" "$SO"
  file "$SO"
fi

mkdir -p tellomi-jni/x86_64
cp -f "$SO" tellomi-jni/x86_64/libtmessages.49.so

version_name=$(sed -n 's/^APP_VERSION_NAME=//p' gradle.properties)
app_version_code=
while IFS='=' read -r key value; do
  case "$key" in
    app_version_code) app_version_code=$value ;;
  esac
done < <(python3 Tools/tellomi-android-version.py \
  --gradle gradle.properties \
  --expect-version-name "$version_name")
if [ -z "$app_version_code" ]; then
  echo "APP_VERSION_CODE was not resolved" >&2
  exit 1
fi
echo "Assembling afat debug APK at APP_VERSION_CODE=$app_version_code ($version_name), x86_64 only."

mkdir -p "$HOME/.gradle"
cat > "$HOME/.gradle/gradle.properties" <<'EOF'
org.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=1g
org.gradle.parallel=true
org.gradle.workers.max=4
org.gradle.vfs.watch=false
org.gradle.daemon=true
EOF
./gradlew :TMessagesProj_App:assembleAfatDebug \
  -PAPP_VERSION_CODE="$app_version_code" \
  -PTELLOMI_SERVER_PROFILE=dev \
  -PTELLOMI_PREBUILT_JNI="$GITHUB_WORKSPACE/tellomi-jni" \
  -PTELLOMI_ABI_FILTERS=x86_64 \
  -Dorg.gradle.jvmargs="-Xmx6g -XX:MaxMetaspaceSize=1g" \
  --stacktrace \
  --console=plain

shopt -s nullglob
apks=(TMessagesProj_App/build/outputs/apk/afat/debug/*.apk)
if [ "${#apks[@]}" -ne 1 ]; then
  echo "Expected one afat debug APK, found ${#apks[@]}" >&2
  find TMessagesProj_App/build/outputs/apk -name '*.apk' -print || true
  exit 1
fi
if ! unzip -l "${apks[0]}" | grep -q 'lib/x86_64/libtmessages.49.so'; then
  echo "APK is missing lib/x86_64/libtmessages.49.so" >&2
  unzip -l "${apks[0]}" | grep libtmessages || true
  exit 1
fi
cp -f "${apks[0]}" tellomi-uitest.apk
ls -lh tellomi-uitest.apk
