#!/usr/bin/env python3
"""Static checks for .github/workflows/android-apk.yml.

Ordinary push and pull_request must not install the release keystore or upload
an installable APK. Run workflow, a v* tag, release/android, and the moments
feed branch still package. The feed branch is debug-only and is not a publish
path. The publish job keeps its existing triggers and does not gain new ones.
"""

import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github" / "workflows" / "android-apk.yml"

PACKAGE_EXPR = (
    "github.event_name == 'workflow_dispatch' || "
    "(github.event_name == 'push' && (startsWith(github.ref, 'refs/tags/v') "
    "|| github.ref == 'refs/heads/release/android' "
    "|| github.ref == 'refs/heads/cursor/android-official-restore-2a44' "
    "|| github.ref == 'refs/heads/cursor/feed-on-master-7033'))"
)

PUBLISH_IF = """needs.apk.outputs.type == 'release' && (
        (github.event_name == 'workflow_dispatch' && inputs.publish != 'none') ||
        (github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v')) ||
        (github.event_name == 'push' && github.ref == 'refs/heads/release/android'))"""


def packages(event, ref):
    return event == "workflow_dispatch" or (
        event == "push" and (
            ref.startswith("refs/tags/v")
            or ref == "refs/heads/release/android"
            or ref == "refs/heads/cursor/android-official-restore-2a44"
            or ref == "refs/heads/cursor/feed-on-master-7033"
        )
    )


def publishes(event, ref, publish, apk_type):
    if apk_type != "release":
        return False
    if event == "workflow_dispatch" and publish not in (None, "", "none"):
        return True
    if event == "push" and ref.startswith("refs/tags/v"):
        return True
    if event == "push" and ref == "refs/heads/release/android":
        return True
    return False


def job_block(text, name):
    marker = f"\n  {name}:\n"
    start = text.index(marker)
    rest = text[start + 1 :]
    lines = rest.splitlines()
    block = [lines[0]]
    for line in lines[1:]:
        if len(line) > 2 and line[0] == " " and line[1] == " " and line[2] != " " and line.strip().endswith(":"):
            break
        block.append(line)
    return "\n".join(block)


def steps(block):
    found = []
    current = None
    for line in block.splitlines():
        if line.startswith("      - name:"):
            if current is not None:
                found.append(current)
            current = [line]
        elif current is not None:
            current.append(line)
    if current is not None:
        found.append(current)
    return found


def step_named(block, name):
    prefix = f"      - name: {name}"
    for step in steps(block):
        if step[0].startswith(prefix):
            return "\n".join(step)
    raise SystemExit(f"missing step {name}")


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def main():
    text = WORKFLOW.read_text()
    require(text.count(PACKAGE_EXPR) == 1, "package expression must be defined once")
    require("\n  push:\n" in text and "\n  pull_request:\n" in text, "push and pull_request must stay")
    require("workflow_dispatch:" in text, "manual run must stay")

    for job in ("workflow", "native", "apk"):
        block = job_block(text, job)
        require("\n    if:" not in block, f"{job} job must not be skipped by a job-level if")

    apk = job_block(text, "apk")
    require("\n    needs: native\n" in apk, "apk job must still need the native job")
    for name in (
        "Download native libraries",
        "Stage native libraries",
        "Assemble APK",
        "Upload APK",
    ):
        step = step_named(apk, name)
        require("if: env.TELLOMI_PACKAGE == 'true'" in step, f"{name} must run only when packaging")
    verify = step_named(apk, "Verify Java and resources")
    require("if: env.TELLOMI_PACKAGE != 'true'" in verify, "Java verification must run when not packaging")
    require("compileDebugJavaWithJavac" in verify, "verification must compile Java")
    require("processAfatDebugResources" in verify, "verification must process resources")
    require("assembleAfat" not in verify, "verification must not assemble an APK")

    native = job_block(text, "native")
    native_upload = step_named(native, "Upload ${{ matrix.abi }} library")
    require("libtmessages.49.so" in native_upload, "native artifact must be the shared library")
    require(".apk" not in native_upload, "native upload must not be an APK")
    require("cannot be installed" in native, "native artifact must be described as not installable")

    secret_lines = [
        line for line in text.splitlines()
        if "secrets.ANDROID_KEYSTORE_" in line or "secrets.ANDROID_KEY_" in line
    ]
    require(len(secret_lines) == 8, "expected four signing secrets in the native job and the package job")
    for line in secret_lines:
        require("env.TELLOMI_PACKAGE == 'true' &&" in line, "keystore secret must be gated on a package run")

    publish = job_block(text, "publish")
    folded = " ".join(publish.split())
    expected = " ".join(PUBLISH_IF.split())
    require(expected in folded, "publish triggers changed")
    require("pull_request" not in publish.split("steps:", 1)[0], "publish must not run on pull_request")
    require("refs/heads/master" not in publish.split("steps:", 1)[0], "publish must not run on master")

    cases = [
        ("pull_request", "refs/heads/master", "none", False, False),
        ("pull_request", "refs/heads/release/android", "none", False, False),
        ("push", "refs/heads/master", None, False, False),
        ("push", "refs/heads/cursor/example-38d3", None, False, False),
        ("push", "refs/heads/cursor/android-official-restore-2a44", None, True, False),
        ("push", "refs/heads/cursor/feed-on-master-7033", None, True, False),
        ("pull_request", "refs/heads/cursor/feed-on-master-7033", "none", False, False),
        ("pull_request", "refs/heads/cursor/android-official-restore-2a44", "none", False, False),
        ("push", "refs/tags/1.2.3", None, False, False),
        ("push", "refs/tags/v1.2.3", None, True, True),
        ("push", "refs/heads/release/android", None, True, True),
        ("workflow_dispatch", "refs/heads/master", "none", True, False),
        ("workflow_dispatch", "refs/heads/master", "test", True, True),
        ("workflow_dispatch", "refs/heads/master", "release", True, True),
    ]
    for event, ref, publish_input, want_package, want_publish in cases:
        got_package = packages(event, ref)
        got_publish = publishes(event, ref, publish_input, "release")
        require(got_package is want_package, f"package {event} {ref} -> {got_package}, want {want_package}")
        require(got_publish is want_publish, f"publish {event} {ref} {publish_input} -> {got_publish}, want {want_publish}")
        require(not publishes(event, ref, publish_input, "debug"), f"debug APK must not publish for {event} {ref}")

    require("Tools/tellomi-android-version.py" in text, "release versionCode must come from tellomi-android-version.py")
    require("dl.tellomi.cn/android/latest.json" in (ROOT / "Tools" / "tellomi-android-version.py").read_text(), "release must read the published feed")
    require("--release" in text, "publish=release must opt into the live versionCode")
    require('expected APP_VERSION_NAME 14.0.10' in text, "package step must require versionName 14.0.10")
    gradle = (ROOT / "gradle.properties").read_text()
    require("APP_VERSION_NAME=14.0.10" in gradle, "gradle.properties must be 14.0.10")
    require("APP_VERSION_CODE=180470" in gradle, "configured base must be the decade above live 14.0.9 afat 1804699")
    publish_script = (ROOT / "Tools" / "tellomi-publish-installer.sh").read_text()
    require("--require-above" in publish_script, "publish must refuse a versionCode that is not above latest.json")

    print(f"ok {WORKFLOW} ({len(cases)} event cases)")


if __name__ == "__main__":
    try:
        main()
    except SystemExit as exc:
        if exc.code not in (0, None):
            print(exc, file=sys.stderr)
        raise
