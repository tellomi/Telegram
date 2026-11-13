#!/usr/bin/env python3
"""Choose the Android version codes baked into a Tellomi APK.

afat (the universal package that is installed and published) is
APP_VERSION_CODE * 10 + 9. bundleAfat is APP_VERSION_CODE * 10 + 1.
Digit 0 is unused.

A publish=release must be strictly newer than the versionCode already in
android/latest.json. The whole decade of the chosen APP_VERSION_CODE
(digit 0 through digit 9) has to sit above that published code, so an older
ABI of this build cannot look newer than the release that is already live.

versionName stays APP_VERSION_NAME from gradle.properties.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import sys
import urllib.request

FEEDS = (
    "https://dl.tellomi.cn/android/latest.json",
    "https://www.tellomi.cn/dl/android/latest.json",
)
# 14.0.11 afat. Checked-in builds must clear this even before a fetch.
KNOWN_PUBLISHED_AFAT = 1804719


def read_gradle(path: pathlib.Path) -> tuple[int, str]:
    code = None
    name = None
    for line in path.read_text().splitlines():
        if line.startswith("APP_VERSION_CODE="):
            code = line.split("=", 1)[1].strip()
        elif line.startswith("APP_VERSION_NAME="):
            name = line.split("=", 1)[1].strip()
    if code is None or not code.isdigit():
        raise SystemExit(f"APP_VERSION_CODE is not an integer in {path}")
    if not name:
        raise SystemExit(f"APP_VERSION_NAME is missing in {path}")
    return int(code), name


def version_code_of(payload: dict) -> int | None:
    if payload.get("platform") not in (None, "android"):
        return None
    if payload.get("channel") not in (None, "release"):
        return None
    raw = payload.get("versionCode")
    if isinstance(raw, bool):
        return None
    if isinstance(raw, int):
        code = raw
    elif isinstance(raw, str) and raw.isdigit():
        code = int(raw)
    else:
        return None
    if code <= 0:
        return None
    return code


def fetch_published(urls: tuple[str, ...], timeout: float = 20.0) -> int:
    found = []
    errors = []
    for url in urls:
        try:
            request = urllib.request.Request(
                url,
                headers={"Accept": "application/json", "Cache-Control": "no-cache", "User-Agent": "TellomiAndroidCI"},
            )
            with urllib.request.urlopen(request, timeout=timeout) as response:
                body = response.read(256 * 1024)
            code = version_code_of(json.loads(body.decode("utf-8")))
        except Exception as exc:  # noqa: BLE001 - one bad feed must not hide the other
            errors.append(f"{url}: {exc}")
            continue
        if code is None:
            errors.append(f"{url}: no android release versionCode")
            continue
        found.append(code)
    if not found:
        detail = "; ".join(errors) or "no feeds"
        raise SystemExit(f"could not read a published Android versionCode ({detail})")
    return max(found)


def published_from_manifest(path: pathlib.Path) -> int:
    code = version_code_of(json.loads(path.read_text()))
    if code is None:
        raise SystemExit(f"{path} has no android release versionCode")
    return code


def next_app_version_code(published: int, configured: int) -> int:
    """Smallest APP_VERSION_CODE whose decade is strictly above ``published``.

    ``published`` is the afat versionCode in latest.json (ones digit 9 today).
    The returned base is at least ``configured``. afat of the result is
    base * 10 + 9, and base * 10 (the unused digit 0) is still above
    ``published``.
    """
    if published < 0:
        raise SystemExit(f"published versionCode must be >= 0, got {published}")
    if configured < 1:
        raise SystemExit(f"APP_VERSION_CODE must be >= 1, got {configured}")
    return max(configured, published // 10 + 1)


def afat_code(app_version_code: int) -> int:
    return app_version_code * 10 + 9


def bundle_code(app_version_code: int) -> int:
    return app_version_code * 10 + 1


def assert_release_above(manifest: int, published: int) -> None:
    if manifest <= published or manifest % 10 != 9:
        raise SystemExit(f"refusing afat versionCode {manifest} (published {published})")
    lowest = manifest - 9
    if lowest <= published:
        raise SystemExit(f"lowest ABI versionCode {lowest} is not above {published}")


def emit(app_version_code: int, version_name: str, published: int) -> None:
    manifest = afat_code(app_version_code)
    print(f"app_version_code={app_version_code}")
    print(f"manifest={manifest}")
    print(f"bundle={bundle_code(app_version_code)}")
    print(f"version_name={version_name}")
    print(f"published={published}")


def self_test() -> None:
    # Live 14.0.8 afat is 1804679. Configured 14.0.9 base 180468 -> afat 1804689.
    assert next_app_version_code(1804679, 180468) == 180468
    assert afat_code(180468) == 1804689
    assert bundle_code(180468) == 1804681
    # 14.0.9 afat is 1804689. The next base 180469 -> afat 1804699.
    assert next_app_version_code(1804689, 180469) == 180469
    assert afat_code(180469) == 1804699
    assert bundle_code(180469) == 1804691
    # Live 14.0.9 afat is 1804699. Configured 14.0.10 base 180470 -> afat 1804709.
    assert next_app_version_code(1804699, 180470) == 180470
    assert afat_code(180470) == 1804709
    assert bundle_code(180470) == 1804701
    # 14.0.11 afat is 1804719. Configured 14.0.12 base 180472 -> afat 1804729.
    assert next_app_version_code(1804719, 180472) == 180472
    assert afat_code(180472) == 1804729
    assert bundle_code(180472) == 1804721
    # A second release with the same gradle base moves one decade up.
    assert next_app_version_code(1804689, 180468) == 180469
    assert afat_code(180469) == 1804699
    # A higher checked-in base wins over a feed that is still behind it.
    assert next_app_version_code(1804679, 180470) == 180470
    assert afat_code(180470) == 1804709
    # A published code that does not end in 9 still pushes the whole decade up.
    assert next_app_version_code(1804700, 180470) == 180471
    assert (180471 * 10) > 1804700
    # Equal codes are not an update. max(current+1's decade, configured).
    assert next_app_version_code(1804679, 180467) == 180468
    for published, configured in (
        (1804679, 180468),
        (1804689, 180468),
        (1804679, 180470),
        (1804700, 180470),
        (0, 180468),
    ):
        app = next_app_version_code(published, configured)
        assert_release_above(afat_code(app), published)
    sample = {
        "platform": "android",
        "channel": "release",
        "versionName": "14.0.8",
        "versionCode": 1804679,
    }
    assert version_code_of(sample) == 1804679
    assert version_code_of({"platform": "android", "channel": "test", "versionCode": 9}) is None
    assert version_code_of({"platform": "win", "channel": "release", "versionCode": 9}) is None
    print("ok tellomi-android-version")


def main(argv: list[str]) -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--gradle", type=pathlib.Path)
    parser.add_argument("--release", action="store_true", help="read android/latest.json and beat it")
    parser.add_argument("--published", type=int, help="published afat versionCode, instead of fetching")
    parser.add_argument("--manifest", type=pathlib.Path, help="latest.json file whose versionCode must be beaten")
    parser.add_argument("--require-above", type=pathlib.Path, help="exit 1 unless --version-code is above this manifest")
    parser.add_argument("--version-code", type=int)
    parser.add_argument("--floor", type=int, default=KNOWN_PUBLISHED_AFAT)
    parser.add_argument("--expect-version-name")
    parser.add_argument("--feed-url", action="append", default=[])
    args = parser.parse_args(argv)
    if args.self_test:
        self_test()
        return
    if args.require_above:
        if args.version_code is None:
            raise SystemExit("--require-above needs --version-code")
        published = published_from_manifest(args.require_above)
        if args.version_code <= published:
            raise SystemExit(
                f"refusing to publish versionCode {args.version_code}; "
                f"android/latest.json is already {published}"
            )
        print(f"versionCode {args.version_code} is above published {published}")
        return
    if args.gradle is None:
        raise SystemExit("--gradle is required")
    configured, version_name = read_gradle(args.gradle)
    if args.expect_version_name and version_name != args.expect_version_name:
        raise SystemExit(f"expected APP_VERSION_NAME {args.expect_version_name}, found {version_name}")
    published = args.floor
    if args.manifest:
        published = max(published, published_from_manifest(args.manifest))
    if args.published is not None:
        published = max(published, args.published)
    if args.release:
        urls = tuple(args.feed_url) if args.feed_url else FEEDS
        published = max(published, fetch_published(urls))
    if args.release or args.published is not None or args.manifest:
        app = next_app_version_code(published, configured)
    else:
        app = configured
        if afat_code(app) <= published:
            raise SystemExit(
                f"afat versionCode {afat_code(app)} is not above floor {published}"
            )
    assert_release_above(afat_code(app), published if (args.release or args.published is not None or args.manifest) else args.floor)
    emit(app, version_name, published)


if __name__ == "__main__":
    try:
        main(sys.argv[1:])
    except BrokenPipeError:
        sys.exit(0)
