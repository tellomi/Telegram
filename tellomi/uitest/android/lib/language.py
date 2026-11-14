"""A-LANG-007: automatic selection must not write mainconfig ``language``."""

from __future__ import annotations

import re

_LANGUAGE = re.compile(r'<string name="language">([^<]*)</string>')


def language_key(xml: str | None) -> str | None:
    if not xml:
        return None
    match = _LANGUAGE.search(xml)
    if not match:
        return None
    value = match.group(1).strip()
    return value or None


def language_status(auto_xml: str | None, manual_xml: str | None, login_ok: bool) -> tuple[str, str]:
    """Return ``(status, error)`` for the combined auto and manual halves."""
    auto = language_key(auto_xml)
    if auto:
        return "fail", f"automatic selection wrote language={auto}"
    if not login_ok:
        return (
            "blocked",
            "automatic selection left language empty; manual selection needs a logged-in settings screen",
        )
    manual = language_key(manual_xml)
    if not manual:
        return "fail", "manual selection did not write mainconfig language"
    lowered = manual.lower()
    if "zh" not in lowered:
        return "fail", f"manual selection wrote {manual}, expected a zh_hans key"
    return "pass", ""
