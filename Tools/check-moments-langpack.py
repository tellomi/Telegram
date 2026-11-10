#!/usr/bin/env python3
"""Moments strings must resolve like stock Telegram strings in a release build.

Release packages set localeFilters to ["zz"] and shrinkResources true.
TelegramStringsTask then:
  * writes every strings.xml name into assets/localization_<lang>.bin,
    keyed by the Java hash of the resource name;
  * emits tools:discard="@string/..." so those values are removed from
    resources.arsc.
LocaleController.getString("Moments_Title", R.string.Moments_Title) reads the
bin by that name. resources.getString(R.string.Moments_Title) throws
Resources.NotFoundException because the entry was discarded.

This check fails if a Moments call site does not use that pair, or if the
English or Simplified Chinese value is missing from the xml that becomes the bin.
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj" / "src" / "main" / "java"
EN = ROOT / "TMessagesProj" / "src" / "main" / "res" / "values" / "strings.xml"
ZH = ROOT / "TMessagesProj" / "src" / "main" / "res" / "values-zh-rCN" / "strings.xml"
OUT = ROOT / "docs" / "moments-langpack.json"
CALL = re.compile(
    r'LocaleController\.(?:getString|formatString|formatPluralString)\(\s*"([^"]+)"\s*,\s*R\.string\.([A-Za-z0-9_]+)'
)
NAME = re.compile(r'<string name="([^"]+)">(.*?)</string>', re.S)


def load(path: pathlib.Path) -> dict[str, str]:
    text = path.read_text(encoding="utf-8")
    return {match.group(1): match.group(2) for match in NAME.finditer(text)}


def main() -> None:
    english = load(EN)
    chinese = load(ZH)
    moments = sorted(name for name in english if name.startswith("Moments_"))
    errors = []
    for name in moments:
        if name not in chinese:
            errors.append(f"missing zh-Hans value for {name}")
    used = set()
    for path in JAVA.rglob("*.java"):
        text = path.read_text(encoding="utf-8", errors="replace")
        if "createConfigurationContext" in text and "FeedUi" in path.name:
            errors.append(f"{path} still creates a configuration context")
        for match in CALL.finditer(text):
            key, resource = match.group(1), match.group(2)
            if not key.startswith("Moments_") and not resource.startswith("Moments_"):
                continue
            used.add(key)
            if key != resource:
                errors.append(f"{path.name}: key {key} != R.string.{resource}")
            if key not in english:
                errors.append(f"{path.name}: {key} is not in values/strings.xml")
    missing_use = sorted(set(moments) - used)
    # Unused copy may stay in the bin for the server. Used keys must exist.
    if not used:
        errors.append("no Moments_ call sites found")
    artifact = {
        "resolution": (
            "Release resources.arsc discards @string values. "
            "LocaleController.getString(name, R.string.name) reads "
            "localization_en.bin or localization_zh_cn.bin by the Java hash of the resource name. "
            "The server langpack should use these same Moments_* keys for en and zh-Hans."
        ),
        "keys": [
            {"key": name, "en": english[name], "zh-Hans": chinese.get(name, "")}
            for name in moments
        ],
    }
    OUT.write_text(json.dumps(artifact, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        raise SystemExit(1)
    print(f"ok {len(moments)} Moments keys, {len(used)} referenced, {len(missing_use)} langpack-only")


if __name__ == "__main__":
    main()
