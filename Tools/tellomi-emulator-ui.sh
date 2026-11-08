#!/bin/bash
# Install the debug local APK on the emulator this job already booted and
# record the screens that open before login. Media, confirm, and Discover
# need a real session. This script does not invent one.
set -euo pipefail

OUT="${TELLOMI_EMULATOR_OUT:?}"
mkdir -p "$OUT"
RESULT="$OUT/result.txt"
: > "$RESULT"

note() {
  printf '%s\n' "$1" | tee -a "$RESULT"
}

note "sha ${GITHUB_SHA:-unknown}"
note "profile local debug x86_64 (10.0.2.2). Not the dev package job."

mapfile -t apks < <(find TMessagesProj_App/build/outputs/apk/afat/debug -name '*.apk' | sort)
if [ "${#apks[@]}" -ne 1 ]; then
  note "error expected one debug apk, found ${#apks[@]}"
  exit 1
fi
APK="${apks[0]}"
note "apk $APK"

adb wait-for-device
adb install -r -t "$APK"
adb shell am start -n app.tellomi.chat/org.telegram.ui.LaunchActivity
alive=0
for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
  if adb shell pidof app.tellomi.chat >/dev/null 2>&1; then
    alive=1
    break
  fi
  sleep 2
done
if [ "$alive" -ne 1 ]; then
  note "error process app.tellomi.chat did not stay up"
  adb logcat -d -t 200 > "$OUT/logcat.txt" || true
  exit 1
fi
note "process app.tellomi.chat is running"

shot() {
  local name="$1"
  sleep 3
  adb exec-out screencap -p > "$OUT/${name}.png"
  if [ ! -s "$OUT/${name}.png" ]; then
    note "error empty screenshot ${name}"
    exit 1
  fi
  if adb shell uiautomator dump /sdcard/tellomi-ui.xml >/dev/null; then
    adb pull /sdcard/tellomi-ui.xml "$OUT/${name}.xml" >/dev/null || note "uiautomator pull failed for ${name}"
  else
    note "uiautomator dump failed for ${name}"
  fi
}

shot 01-launch

python3 - "$OUT" <<'PY'
import pathlib, re, subprocess, sys
out = pathlib.Path(sys.argv[1])

def nodes(path):
    if not path.is_file():
        return []
    xml = path.read_text(errors="replace")
    found = []
    for node in re.findall(r"<node\b[^>]*>", xml):
        bounds = re.search(r'\bbounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if not bounds:
            continue
        text = re.search(r'\btext="([^"]*)"', node)
        desc = re.search(r'\bcontent-desc="([^"]*)"', node)
        label = text.group(1) if text and text.group(1) else ""
        if not label and desc:
            label = desc.group(1)
        found.append((label, *map(int, bounds.groups())))
    return found

deny = ("Don’t allow", "Don't allow", "Deny", "不允许", "拒絕")
clicked = False
for label, x1, y1, x2, y2 in nodes(out / "01-launch.xml"):
    if label in deny:
        subprocess.check_call(["adb", "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)])
        clicked = True
        break
(out / "permission-dismissed").write_text("yes\n" if clicked else "no\n")
PY

if [ "$(cat "$OUT/permission-dismissed")" = "yes" ]; then
  note "dismissed a system permission dialog"
  shot 02-after-permission
else
  note "no system permission dialog on the first screen"
fi

python3 - "$OUT" <<'PY'
import pathlib, re, subprocess, sys
out = pathlib.Path(sys.argv[1])
dumps = sorted(out.glob("*.xml"))
xml = dumps[-1].read_text(errors="replace") if dumps else ""
want = ("Start meeting", "开始使用", "開始使用")
clicked = "none"
for node in re.findall(r"<node\b[^>]*>", xml):
    bounds = re.search(r'\bbounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
    text = re.search(r'\btext="([^"]*)"', node)
    if not bounds or not text or text.group(1) not in want:
        continue
    x1, y1, x2, y2 = map(int, bounds.groups())
    subprocess.check_call(["adb", "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)])
    clicked = text.group(1)
    break
(out / "start-tapped").write_text(clicked + "\n")
PY

started="$(cat "$OUT/start-tapped")"
note "start control: ${started}"
if [ "$started" != "none" ]; then
  shot 03-after-start
fi

python3 - "$OUT" <<'PY'
import pathlib, sys
out = pathlib.Path(sys.argv[1])
text = "\n".join(p.read_text(errors="replace") for p in out.glob("*.xml"))
markers = ("Send", "Attach", "floating_check", "Post", "Discover", "验证码")
found = [m for m in markers if m in text]
(out / "markers").write_text("\n".join(found) + "\n")
PY
note "screen markers: $(paste -sd, "$OUT/markers" || true)"
note "not reached: photo send arrow, avatar/wallpaper/sticker check, Discover compose, profile_required. Those need a logged-in session."

probe() {
  local repo="$1"
  local code
  code=$(curl -sS -o "$OUT/${repo##*/}.json" -w '%{http_code}' \
    -H "Authorization: Bearer ${GITHUB_TOKEN:-}" \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/${repo}") || code="curl-failed"
  note "github ${repo} HTTP ${code}"
  rm -f "$OUT/${repo##*/}.json"
}

probe tellomi/teamgram
probe tellomi/content-engine
note "server not started. 04d8c134 is not on GitHub. A replay would be ee0b707 plus ed385983 inside an isolated job, and this token cannot read those repos."
note "no TELLOMI_DISCOVER_* variables were set. No production DC was contacted."
adb logcat -d -t 300 > "$OUT/logcat.txt" || true
note "screenshots: $(find "$OUT" -name '*.png' -printf '%f\n' | paste -sd, -)"
