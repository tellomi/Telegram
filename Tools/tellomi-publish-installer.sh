#!/usr/bin/env bash
# Publish a Tellomi installer to the download pipeline.
#
#   Primary download: https://www.tellomi.cn/dl  (official Chengdu server 8.137.39.82, /srv/tellomi-downloads, ICP-filed)
#   Backup line:      https://dl.tellomi.cn  (Cloudflare R2 bucket tellomi-updates; also updates.tellomi.app)
#
# This script uploads to R2 only. The server pulls the new release from R2 within 10 minutes
# (tellomi-dl-sync.sh on the server, sha256-verified), keeps only the current release locally,
# and serves it as the primary URL. R2 keeps the newest 3 releases per platform; test builds
# live under <platform>/test/ and are pruned after 14 days (here and by the server's weekly
# tellomi-dl-prune.sh).
#
# Usage:
#   Tools/tellomi-publish-installer.sh --platform android --version-name 14.0.5 \
#       --version-code 1800989 --channel release|test FILE[:ARCH[:KIND]]...
#
# Env: R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY, R2_ENDPOINT (https://<account>.r2.cloudflarestorage.com)
#      Optional: DL_BUCKET (tellomi-updates), DL_PRIMARY_BASE, DL_FALLBACK_BASE, DL_R2_KEEP (3),
#      DL_TEST_KEEP_DAYS (14), RELEASE_NOTES, DRY_RUN=1 (print only).
# Needs rclone and python3.
set -euo pipefail

platform= version_name= version_code=0 channel=release
files=()
while [ $# -gt 0 ]; do
  case "$1" in
    --platform) platform=$2; shift 2 ;;
    --version-name) version_name=$2; shift 2 ;;
    --version-code) version_code=$2; shift 2 ;;
    --channel) channel=$2; shift 2 ;;
    -h|--help) sed -n '2,25p' "$0"; exit 0 ;;
    *) files+=("$1"); shift ;;
  esac
done
case "$platform" in android|win|mac) ;; *) echo "--platform must be android, win or mac" >&2; exit 2 ;; esac
case "$channel" in release|test) ;; *) echo "--channel must be release or test" >&2; exit 2 ;; esac
[[ "$version_name" =~ ^[0-9A-Za-z._-]+$ ]] || { echo "bad --version-name" >&2; exit 2; }
[[ "$version_code" =~ ^[0-9]+$ ]] || { echo "bad --version-code" >&2; exit 2; }
[ ${#files[@]} -gt 0 ] || { echo "no files given" >&2; exit 2; }
: "${R2_ACCESS_KEY_ID:?}" "${R2_SECRET_ACCESS_KEY:?}" "${R2_ENDPOINT:?}"

BUCKET=${DL_BUCKET:-tellomi-updates}
PRIMARY=${DL_PRIMARY_BASE:-https://www.tellomi.cn/dl}
FALLBACK=${DL_FALLBACK_BASE:-https://dl.tellomi.cn}
KEEP=${DL_R2_KEEP:-3}
TEST_DAYS=${DL_TEST_KEEP_DAYS:-14}

# Release directory: <versionName>-<versionCode> (CI builds can share a versionName).
release=$version_name; [ "$version_code" != 0 ] && release="$version_name-$version_code"
if [ "$channel" = release ]; then prefix="$platform/$release"; else prefix="$platform/test/$release"; fi

export RCLONE_CONFIG_R2_TYPE=s3 RCLONE_CONFIG_R2_PROVIDER=Cloudflare RCLONE_CONFIG_R2_NO_CHECK_BUCKET=true
export RCLONE_CONFIG_R2_ACCESS_KEY_ID="$R2_ACCESS_KEY_ID" RCLONE_CONFIG_R2_SECRET_ACCESS_KEY="$R2_SECRET_ACCESS_KEY"
export RCLONE_CONFIG_R2_ENDPOINT="$R2_ENDPOINT" RCLONE_RETRIES=5 RCLONE_LOW_LEVEL_RETRIES=10
run() { if [ -n "${DRY_RUN:-}" ]; then echo "DRY: $*"; else "$@"; fi; }

work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
specs="$work/files.tsv"; : > "$specs"
for spec in "${files[@]}"; do
  IFS=: read -r path arch kind <<<"$spec"
  [ -f "$path" ] || { echo "missing file $path" >&2; exit 1; }
  name=$(basename "$path")
  [ "$platform" = android ] && [ ${#files[@]} -eq 1 ] && name=Tellomi.apk
  ext=${name##*.}
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$path" "$name" "${arch:-universal}" "${kind:-$ext}" \
    "$(sha256sum "$path" | cut -d' ' -f1)" "$(stat -c %s "$path" 2>/dev/null || stat -f %z "$path")" >> "$specs"
done

# A release must land strictly above the versionCode clients and the download
# page already have. This runs inside the publish lock, after the APK was built,
# so a second overlapping release fails here instead of replacing latest.json
# with the same code.
if [ "$channel" = release ] && [ "$platform" = android ] && [ -z "${DRY_RUN:-}" ]; then
  existing="$work/remote-latest.json"
  if ! rclone cat "r2:$BUCKET/$platform/latest.json" > "$existing"; then
    echo "could not read r2:$BUCKET/$platform/latest.json; refusing to publish a release that might not be newer" >&2
    exit 1
  fi
  script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
  python3 "$script_dir/tellomi-android-version.py" --require-above "$existing" --version-code "$version_code"
fi

# 1. files first, so a manifest never points at a missing object
while IFS=$'\t' read -r path name arch kind sha size; do
  echo "upload $path -> r2:$BUCKET/$prefix/$name ($size bytes, sha256 $sha)"
  run rclone copyto "$path" "r2:$BUCKET/$prefix/$name" --s3-no-check-bucket
done < "$specs"

# 2. manifest
manifest_key="$platform/latest.json"; [ "$channel" = test ] && manifest_key="$platform/test/latest.json"
python3 - "$specs" "$platform" "$version_name" "$version_code" "$prefix" "$PRIMARY" "$FALLBACK" "$channel" > "$work/latest.json" <<'PY'
import json, sys, time, datetime, os
specs, platform, vname, vcode, prefix, primary, fallback, channel = sys.argv[1:]
files = []
for line in open(specs):
    path, name, arch, kind, sha, size = line.rstrip("\n").split("\t")
    key = f"{prefix}/{name}"
    files.append({"path": key, "name": name, "arch": arch, "kind": kind, "sha256": sha, "size": int(size),
                  "url": f"{primary}/{key}", "fallbackUrl": f"{fallback}/{key}"})
now = time.time()
m = {"platform": platform, "channel": channel, "versionName": vname, "versionCode": int(vcode),
     "releaseDate": datetime.datetime.fromtimestamp(now, datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
     "uploadTimestamp": int(now * 1000),
     # Top-level fields kept for the existing Android manifest readers.
     "url": files[0]["url"], "fallbackUrl": files[0]["fallbackUrl"], "sha256sum": files[0]["sha256"], "size": files[0]["size"],
     "feedUrl": f"{primary}/{platform}/latest.json", "fallbackFeedUrl": f"{fallback}/{platform}/latest.json",
     "files": files}
notes = os.environ.get("RELEASE_NOTES", "").strip()
if notes:
    m["notes"] = notes
print(json.dumps(m, ensure_ascii=False, indent=1))
PY
cat "$work/latest.json"
run rclone copyto "$work/latest.json" "r2:$BUCKET/$manifest_key" --s3-no-check-bucket \
  --header-upload "Cache-Control: no-cache" --header-upload "Content-Type: application/json"

echo "published $platform $release ($channel). Primary $PRIMARY/$prefix/ is live on the server within ~10 minutes."
