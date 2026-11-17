"""Login-screen decisions that do not call adb or Maestro."""

from __future__ import annotations

import re

_NODE = re.compile(r"<node\b[^>]*>")
_ATTR = re.compile(r'(\w[\w:-]*)="([^"]*)"')


def status_when_login_failed(error: str) -> str:
    """A session that never reached the main tabs is a failure.

    A password gate with no test password stays blocked. Recording the
    missed main screen as blocked kept the merge job green.
    """
    if "login did not reach the main tabs" in (error or ""):
        return "fail"
    return "blocked"


def phone_delete_count(phone_text: str) -> int:
    """DEL count while the phone field is focused.

    TellomiLoginCountry opens a fresh login on +86 with an empty number.
    PhoneView sends a delete on an empty phone field into the calling-code
    field, so a fixed erase of 40 characters wipes +86. Delete only the
    digits that are actually in the phone field.
    """
    return sum(ch.isdigit() for ch in (phone_text or ""))


def _attrs(node: str) -> dict[str, str]:
    return {name: value for name, value in _ATTR.findall(node)}


def phone_field_text(xml: str) -> str:
    """Text of the phone-number field, ignoring the calling-code field."""
    for node in _NODE.findall(xml or ""):
        attrs = _attrs(node)
        label = f"{attrs.get('content-desc', '')}\n{attrs.get('text', '')}"
        if re.search(r"Phone number|电话号码", label):
            return attrs.get("text", "")
    return ""


def calling_code_is_china(xml: str) -> bool:
    """True when the calling-code field is already 86. Do not erase it."""
    return bool(re.search(r'text="\+?86"', xml or ""))
