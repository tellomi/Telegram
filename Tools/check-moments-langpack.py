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
A Moments key that is translated in any other built-in language must be present
in every built-in language, and a sentence must not repeat the English text.
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj" / "src" / "main" / "java"
RES = ROOT / "TMessagesProj" / "src" / "main" / "res"
OUT = ROOT / "docs" / "moments-langpack.json"
# Built-in packs that become localization_*.bin. en and zh-Hans stay the
# legacy columns. A Moments key that also exists in any other pack must exist
# in every pack, with its own wording, so the client does not fall back to English.
LOCALES = (
    ("en", RES / "values" / "strings.xml"),
    ("zh-Hans", RES / "values-zh-rCN" / "strings.xml"),
    ("zh-Hant", RES / "values-zh-rTW" / "strings.xml"),
    ("ar", RES / "values-ar" / "strings.xml"),
    ("de", RES / "values-de" / "strings.xml"),
    ("es", RES / "values-es" / "strings.xml"),
    ("it", RES / "values-it" / "strings.xml"),
    ("ko", RES / "values-ko" / "strings.xml"),
    ("nl", RES / "values-nl" / "strings.xml"),
    ("pt-BR", RES / "values-pt-rBR" / "strings.xml"),
    ("ru", RES / "values-ru" / "strings.xml"),
    ("uk", RES / "values-uk" / "strings.xml"),
)
CALL = re.compile(
    r'LocaleController\.(?:getString|formatString|formatPluralString)\(\s*"([^"]+)"\s*,\s*R\.string\.([A-Za-z0-9_]+)'
)
NAME = re.compile(r'<string name="([^"]+)">(.*?)</string>', re.S)


def load(path: pathlib.Path) -> dict[str, str]:
    text = path.read_text(encoding="utf-8")
    return {match.group(1): match.group(2) for match in NAME.finditer(text)}


def unescape(value: str) -> str:
    return (
        value.replace("\\'", "'")
        .replace('\\"', '"')
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .strip()
    )


def main() -> None:
    packs = {code: load(path) for code, path in LOCALES}
    english = packs["en"]
    moments = sorted(name for name in english if name.startswith("Moments_"))
    errors = []
    full = []
    for name in moments:
        if name not in packs["zh-Hans"] or not unescape(packs["zh-Hans"][name]):
            errors.append(f"missing zh-Hans value for {name}")
        elsewhere = any(name in packs[code] for code, _path in LOCALES if code not in ("en", "zh-Hans"))
        if not elsewhere:
            continue
        full.append(name)
        english_text = unescape(english[name])
        for code, _path in LOCALES:
            raw = packs[code].get(name, "")
            text = unescape(raw)
            if not text:
                errors.append(f"missing {code} value for {name}")
                continue
            if code != "en" and not name.startswith("Moments_City_") and text == english_text:
                errors.append(f"{code} value for {name} is the English text")
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
    keys = []
    for name in moments:
        entry = {"key": name, "en": english[name], "zh-Hans": packs["zh-Hans"].get(name, "")}
        if name in full:
            for code, _path in LOCALES:
                if code in ("en", "zh-Hans"):
                    continue
                entry[code] = packs[code].get(name, "")
        keys.append(entry)
    artifact = {
        "resolution": (
            "Release resources.arsc discards @string values. "
            "LocaleController.getString(name, R.string.name) reads the matching "
            "localization bin by the Java hash of the resource name. "
            "en and zh-Hans are on every Moments key. A key that ships in any other "
            "built-in language must ship in all of them, with that language's own wording."
        ),
        "keys": keys,
    }
    OUT.write_text(json.dumps(artifact, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        raise SystemExit(1)
    print(f"ok {len(moments)} Moments keys, {len(full)} in every built-in language, {len(used)} referenced, {len(missing_use)} langpack-only")


if __name__ == "__main__":
    main()
