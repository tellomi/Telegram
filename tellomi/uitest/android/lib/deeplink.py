"""Classify deep-link probes. The runner supplies adb text; this module does not call adb."""

from __future__ import annotations

import re

PACKAGE = "app.tellomi.chat"
ACTIVITY = "org.telegram.ui.LaunchActivity"

_FATAL = "FATAL EXCEPTION"


def resolved_to_app(resolve_text: str) -> bool:
    text = resolve_text or ""
    return PACKAGE in text or ACTIVITY in text


def crashed(logcat: str) -> bool:
    return _FATAL in (logcat or "")


def classify_owned_link(resolve_text: str, logcat: str) -> tuple[str, str]:
    """tell.cc and tellomi:// must resolve to this app and not crash."""
    if not resolved_to_app(resolve_text):
        snippet = " ".join((resolve_text or "").split())[:400]
        return "fail", f"VIEW intent did not resolve to {PACKAGE}: {snippet or 'empty resolve'}"
    if crashed(logcat):
        return "fail", "app crashed after the VIEW intent"
    return "pass", ""


def classify_geo(resolve_text: str, logcat: str) -> tuple[str, str]:
    """geo: is an outgoing TellomiMapApps chooser unless the app actually owns the scheme."""
    if crashed(logcat):
        return "fail", "app crashed after geo: intent"
    if resolved_to_app(resolve_text):
        return "pass", ""
    return (
        "blocked",
        "geo: is not an incoming filter on LaunchActivity. "
        "TellomiMapApps opens an external map from a location message, and this emulator "
        "has no map fixture. Not marked pass.",
    )


def classify_profile_mime(resolve_text: str, logcat: str, opened_profile: bool) -> tuple[str, str]:
    if not resolved_to_app(resolve_text):
        snippet = " ".join((resolve_text or "").split())[:400]
        return "fail", f"profile MIME did not resolve to {PACKAGE}: {snippet or 'empty resolve'}"
    if crashed(logcat):
        return "fail", "LaunchActivity crashed on the profile MIME"
    if opened_profile:
        return "pass", ""
    return (
        "blocked",
        "the profile MIME filter resolves to LaunchActivity, which reads a contacts row. "
        "This emulator has no Tellomi contact, so a profile was not opened. Not marked pass.",
    )


def intent_urls(dumpsys: str) -> list[str]:
    text = dumpsys or ""
    found = re.findall(r"https?://[^\s'\"<>\\]+", text)
    cleaned = []
    for url in found:
        url = url.rstrip(".,);")
        if url not in cleaned:
            cleaned.append(url)
    return cleaned


_BAD_HOSTS = ("telegram.org", "telegram.me", "telegram.dog", "://t.me", "://t.me/")


def url_problem(urls: list[str]) -> str | None:
    for url in urls:
        lowered = url.lower()
        for host in _BAD_HOSTS:
            if host in lowered:
                return url
    return None


def classify_help_url(dumpsys: str, ui_text: str, path_hint: str) -> tuple[str, str, str]:
    """Return status, error, and the URL evidence for a help/privacy/terms open."""
    blob = f"{dumpsys or ''}\n{ui_text or ''}"
    urls = intent_urls(blob)
    bad = url_problem(urls)
    if bad:
        return "fail", f"opened a Telegram host: {bad}", bad
    matched = [url for url in urls if "tellomi.cn" in url.lower() and (not path_hint or path_hint in url)]
    if not matched:
        matched = [url for url in urls if "tellomi.cn" in url.lower()]
    if matched:
        return "pass", "", matched[-1]
    if "tellomi.cn" in blob.lower() and path_hint and path_hint in blob:
        return "pass", "", f"tellomi.cn{path_hint}"
    snippet = " ".join(blob.split())[:300]
    return "fail", f"no tellomi.cn URL in the activity or the screen ({path_hint or 'any path'}). {snippet}", ""
