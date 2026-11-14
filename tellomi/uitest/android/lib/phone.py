"""Dedicated emulator logins.

teamgram-server docs/sms-email.md answers ``99966XYYYY`` before any SMS
provider. The datacenter digit ``X`` is 1 for production, so the code is
``11111``. This suite only dials two numbers inside ``99966170xx``:

- A ``9996617092``
- B ``9996617093``

``7001``–``7091`` belong to other testers. A secret outside ``99966170xx``
is refused. An empty secret uses the dedicated number for that slot.
"""

from __future__ import annotations

import re

DEDICATED = {"A": "9996617092", "B": "9996617093"}
FIXED_CODE = "11111"
# 99966 + DC digit 1 + subscriber 70xx.
SUITE_RANGE = re.compile(r"^99966170\d{2}$")
# Last four digits reserved for other testers.
OTHER_TESTERS = range(7001, 7092)


def national_number(raw: str | None) -> str:
    digits = re.sub(r"\D", "", raw or "")
    if digits.startswith("0086"):
        digits = digits[4:]
    elif digits.startswith("86") and digits[2:].startswith("99966"):
        digits = digits[2:]
    return digits


def resolve_slot(slot: str, raw: str | None) -> tuple[str, str]:
    """Return ``(national, error)``. ``national`` is empty when the value is refused."""
    slot = slot.upper()
    if slot not in DEDICATED:
        return "", f"unknown phone slot {slot}"
    dedicated = DEDICATED[slot]
    text = (raw or "").strip()
    if not text:
        return dedicated, ""
    national = national_number(text)
    if not SUITE_RANGE.fullmatch(national):
        return "", f"TELLOMI_TEST_PHONE_{slot} is outside 99966170xx and was refused"
    suffix = int(national[-4:])
    if suffix in OTHER_TESTERS:
        return "", (
            f"TELLOMI_TEST_PHONE_{slot} is in the shared tester pool 7001-7091; "
            f"this suite only dials {dedicated}"
        )
    if national != dedicated:
        return "", f"TELLOMI_TEST_PHONE_{slot} is not the dedicated number {dedicated} and was refused"
    return national, ""


def suite_code() -> str:
    return FIXED_CODE
