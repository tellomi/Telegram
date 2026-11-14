"""Score a UI dump against catalog checks. No device access."""

from __future__ import annotations

import re

_STORY_IDS = ("A-PROFILE-296", "A-IDX-STORY-028", "A-IDX-MEDIA-009")

EMPTY_FEED = (
    "还没有内容，来发第一条吧",
    "你关注的人还没有发动态",
    "这座城市还没有动态",
    "互相关注后就是好友",
    "Nothing here yet",
    "have not posted yet",
    "No posts in this city",
    "Follow each other",
)


def nodes_from_dump(xml: str | None) -> list[str]:
    if not xml:
        return []
    found = re.findall(r'(?:text|content-desc)="([^"]*)"', xml)
    nodes = []
    for raw in found:
        text = raw.replace("&#10;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").strip()
        if text:
            nodes.append(text)
    return nodes


def feed_is_empty(nodes: list[str]) -> bool:
    blob = "\n".join(nodes)
    return any(pattern in blob for pattern in EMPTY_FEED)


def _matches(nodes: list[str], patterns: list[str], contains: bool) -> bool:
    for node in nodes:
        for pattern in patterns:
            if contains:
                if re.search(pattern, node):
                    return True
            elif re.fullmatch(pattern, node):
                return True
    return False


def _save_state(nodes: list[str]) -> str:
    saved = any(re.search(r"已收藏|(^| )Saved($| )", node) for node in nodes)
    save = any(re.search(r"收藏|(^| )Save($| )", node) for node in nodes)
    if saved:
        return "saved"
    if save:
        return "save"
    return ""


def score_checks(nodes: list[str], checks: dict, *, empty: bool = False, dump_ok: bool = True) -> dict[str, tuple[str, str]]:
    """Map backlog id → (status, error). Evidence is appended after a blank line in error when set? No.

    The returned error is the failure or blocked reason. Pass errors are empty.
    Callers copy ``evidence`` from the check spec themselves.
    """
    results = {}
    if not dump_ok:
        for backlog_id in checks:
            results[backlog_id] = ("fail", "uiautomator dump was empty")
        return results
    save_state = _save_state(nodes)
    for backlog_id, spec in checks.items():
        mode = spec.get("mode")
        needs_post = spec.get("needs_post", False)
        if needs_post and empty:
            results[backlog_id] = ("blocked", "feed showed the empty state, so this control was not on screen")
            continue
        if mode == "save":
            if not save_state and needs_post:
                results[backlog_id] = ("fail", "save control not found on a post")
            elif save_state in ("save", "saved"):
                results[backlog_id] = ("pass", "")
            else:
                results[backlog_id] = ("fail", "save control not found")
            continue
        if mode == "saved":
            if save_state == "saved":
                results[backlog_id] = ("pass", "")
            elif save_state == "save" or (needs_post and not empty):
                results[backlog_id] = ("blocked", "save control is not in the saved state")
            elif needs_post and empty:
                results[backlog_id] = ("blocked", "feed showed the empty state, so this control was not on screen")
            else:
                results[backlog_id] = ("blocked", "saved state was not shown")
            continue
        patterns = list(spec.get("any") or [])
        contains = bool(spec.get("contains"))
        if patterns and _matches(nodes, patterns, contains):
            context = list(spec.get("context") or [])
            if context and not _matches(nodes, context, True):
                results[backlog_id] = (
                    "blocked",
                    "the label is on screen but the screen that owns it was not open",
                )
            else:
                results[backlog_id] = ("pass", "")
            continue
        missing = spec.get("missing", "fail")
        reason = spec.get("missing_error") or f"none of {patterns} was on screen"
        results[backlog_id] = (missing, reason)
    return results


def classify_version(ui_text: str, package_dump: str) -> dict[str, tuple[str, str, str]]:
    """A-IDX-UPD-006 and A-IDX-UPD-008. Returns id → (status, error, evidence)."""
    text = ui_text or ""
    plain = re.findall(r"application-label:'([^']*)'", package_dump or "")
    label = plain[0] if plain else ""
    version = re.search(r"\d+\.\d+(?:\.\d+)?", text)
    if version:
        upd006 = ("pass", "", version.group(0))
    else:
        upd006 = ("fail", "settings did not show a version number", "")
    if label.lower() == "tellomi" and "Tellomi" in text and "Telegram for Android" not in text:
        upd008 = ("pass", "", f"label={label}")
    elif "telegram" in label.lower() or "Telegram for Android" in text:
        upd008 = ("fail", f"brand is {label or 'Telegram'} on the version line", text[:180])
    else:
        upd008 = ("fail", f"application label {label or 'missing'} is not Tellomi", "")
    return {"A-IDX-UPD-006": upd006, "A-IDX-UPD-008": upd008}


def classify_profile(nodes: list[str]) -> dict[str, tuple[str, str]]:
    """Own-profile tab. Story tabs stay blocked when they are absent."""
    blob = "\n".join(nodes)
    logged_out = ("Your Phone" in blob or "电话号码" in blob) and "发现" not in blob and "Discover" not in blob
    story_hit = any(node == "快拍" or node == "Stories" or node.startswith("快拍") for node in nodes)
    identity = any(
        token in blob
        for token in ("编辑", "Edit", "用户名", "Username", "手机", "动态", "Moments", "快拍", "Stories", "帖子", "QR", "二维码")
    )
    results = {}
    if logged_out:
        results["A-IDX-PROF-029"] = ("fail", "profile tab is still the login screen")
    elif identity:
        results["A-IDX-PROF-029"] = ("pass", "")
    else:
        results["A-IDX-PROF-029"] = ("fail", "profile tab did not render an identity or a posts section")
    if logged_out:
        reason = "profile tab is still the login screen"
        status = "fail"
    elif story_hit:
        reason = ""
        status = "pass"
    else:
        reason = "stories tab is absent; it is shown only when pinned stories exist, so absence is not a pass"
        status = "blocked"
    for backlog_id in _STORY_IDS:
        results[backlog_id] = (status, reason)
    return results
