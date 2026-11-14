"""Backlog coverage for the Android emulator suite.

Every id in ``backlog.csv`` is device_only, statically blocked, scored by one
flow, or assembled from named parts. A flow may cover many ids. Device-only
ids are not given a passing flow.
"""

from __future__ import annotations

import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BACKLOG = ROOT / "backlog.csv"

DEVICE_ONLY = {
    "A-LINK-001": "system call MIME vnd.app.tellomi.chat.call needs the contacts provider and CALL_PHONE on a real device",
    "A-LINK-002": "launcher video-call shortcut places a camera call; not faked on an emulator",
    "A-LINK-003": "launcher voice-call shortcut places a microphone call; not faked on an emulator",
    "A-MOMENT-041": "composer voice control records from the microphone",
    "A-PERM-008": "feed voice capture requests the microphone",
    "A-IDX-MOMENT-037": "composer photo and camera capture needs a camera; the photo button itself is A-MOMENT-037",
}

STATIC_BLOCKED = {
    "A-MOMENT-021": "FeedCollectionActivity has no fixture collection to open",
    "A-MOMENT-027": "comment delete confirmation is not opened, so a comment is not deleted",
    "A-MOMENT-028": "comment delete confirmation is not opened, so a comment is not deleted",
    "A-MOMENT-029": "comment delete confirmation is not opened, so a comment is not deleted",
    "A-MOMENT-030": "comment delete confirmation is not opened, so a comment is not deleted",
    "A-MOMENT-044": "mention warning needs a mention of someone who cannot see the post",
    "A-MOMENT-045": "mention warning needs a mention of someone who cannot see the post",
    "A-MOMENT-050": "the generic 好 button is not the draft dialog's action; that dialog is covered by its own buttons",
    "A-MOMENT-052": "video trim dialog needs a clip longer than the limit; no such clip is bundled",
    "A-MOMENT-054": "quote hint 说点什么 needs an existing post to quote; the test does not publish",
    "A-MOMENT-063": "detail delete confirmation is not opened, so a post is not deleted",
    "A-MOMENT-064": "detail delete confirmation is not opened, so a post is not deleted",
    "A-MOMENT-065": "detail delete confirmation is not opened, so a post is not deleted",
    "A-MOMENT-066": "detail delete confirmation is not opened, so a post is not deleted",
    "A-MOMENT-071": "missing-post title needs a deep link to a deleted id",
    "A-MOMENT-072": "media viewer retry needs a post whose image fails to load",
    "A-MOMENT-077": "audience-update failure copy only appears after a failed server write",
    "A-MOMENT-092": "audience-update failure copy only appears after a failed server write",
    "A-MOMENT-103": "report-submitted copy would require sending a report; the sheet is cancelled instead",
    "A-MOMENT-114": "FeedTopicActivity has no topic fixture",
    "A-IDX-NAV-020": "the removed bottom-bar search tab has no control to tap; a header search field still exists, so absence is not recorded as a pass",
    "A-IDX-ACCT-008": "account deletion is not run against the shared test account",
    "A-IDX-UPD-005": "help.getAppUpdate and latest.json are server release data, not a screen on a debug install",
    "A-IDX-UPD-010": "production DC addresses are not a screen; a log line is not treated as a pass",
    "A-IDX-MOMENT-047": "discover.getEngineCredential is a server token; the feed screen is checked separately",
    "A-IDX-MOMENT-049": "tellomi_discover_feed_base_url is server configuration, not a screen",
}

# Ids filled by merge from named parts recorded by several flows.
PARTS = {
    "A-IDX-UPD-007": ["privacy", "faq", "tos"],
    "A-IDX-UPD-009": ["privacy", "faq"],
}

SHARDS = {
    "links-tellcc": {"api": 34, "needs_login": False},
    "links-scheme": {"api": 33, "needs_login": False},
    "links-geo": {"api": 34, "needs_login": False},
    "links-profile": {"api": 33, "needs_login": False},
    "login-screen": {"api": 34, "needs_login": False},
    "account-a": {"api": 34, "needs_login": True},
    "account-b": {"api": 33, "needs_login": True},
}


def chk(*texts, missing="fail", contains=False, needs_post=False, evidence="", mode=None, context=(), missing_error=""):
    item = {
        "any": list(texts),
        "missing": missing,
        "contains": contains,
        "needs_post": needs_post,
    }
    if evidence:
        item["evidence"] = evidence
    if mode:
        item["mode"] = mode
    if context:
        item["context"] = list(context)
    if missing_error:
        item["missing_error"] = missing_error
    return item


_MENU = ("举报|Report", "复制|Copy", "不感兴趣|Not interested", "删除|Delete", "编辑|Edit")

FLOWS = [
    {
        "name": "links-tellcc",
        "file": "flows/links-tellcc.yaml",
        "shard": "links-tellcc",
        "needs_login": False,
        "handler": "deeplink",
        "urls": ["https://tell.cc/", "https://tell.cc/m", "http://tell.cc/"],
        "checks": {"A-LINK-008": {}},
    },
    {
        "name": "links-scheme",
        "file": "flows/links-scheme.yaml",
        "shard": "links-scheme",
        "needs_login": False,
        "handler": "deeplink",
        "urls": ["tellomi://resolve?domain=test", "tg://resolve?domain=test"],
        "checks": {"A-LINK-009": {}},
    },
    {
        "name": "links-geo",
        "file": "flows/links-geo.yaml",
        "shard": "links-geo",
        "needs_login": False,
        "handler": "deeplink",
        "urls": ["geo:39.9042,116.4074"],
        "checks": {"A-LINK-004": {}},
    },
    {
        "name": "links-profile",
        "file": "flows/links-profile.yaml",
        "shard": "links-profile",
        "needs_login": False,
        "handler": "deeplink",
        "mime": "vnd.android.cursor.item/vnd.app.tellomi.chat.profile",
        "checks": {"A-LINK-005": {}},
    },
    {
        "name": "login-screen",
        "file": "flows/login-screen.yaml",
        "shard": "login-screen",
        "needs_login": False,
        "handler": "terms",
        "checks": {
            "A-IDX-LOGIN-018": chk(
                "Terms of Service",
                "服务条款",
                "Terms of Service\\.",
                contains=True,
                missing_error="login screen did not show the terms of service",
            ),
        },
        "emits_parts": {"A-IDX-UPD-007": "tos"},
    },
    {
        "name": "language",
        "files": ["flows/language-auto.yaml", "flows/language-manual.yaml"],
        "shard": "account-a",
        "needs_login": True,
        "handler": "language",
        "checks": {"A-LANG-007": {}},
    },
    {
        "name": "settings-home",
        "file": "flows/settings-home.yaml",
        "shard": "account-a",
        "needs_login": True,
        "handler": "maestro",
        "checks": {
            "A-SET-075": chk("工具", "Tools"),
        },
    },
    {
        "name": "navbar",
        "file": "flows/navbar.yaml",
        "shard": "account-a",
        "needs_login": True,
        "handler": "maestro",
        "checks": {
            "A-SET-114": chk(
                "通话",
                "Calls",
                evidence="Settings → 通用 → 导航栏",
            ),
        },
    },
    {
        "name": "menu",
        "file": "flows/menu.yaml",
        "shard": "account-a",
        "needs_login": True,
        "handler": "maestro",
        "checks": {
            "A-SET-082": chk("选单设定", "Menu settings"),
            "A-SET-110": chk("选单设定", "Menu settings"),
            "A-SET-111": chk("导航栏按钮", "Navigation bar buttons"),
            "A-SET-112": chk("选单设定", "Menu settings"),
            "A-SET-113": chk("隐藏按钮文字", "Hide button labels"),
            "A-IDX-NAV-017": chk("隐藏按钮文字", "Hide button labels"),
            "A-IDX-MENU-006": chk(
                "资料标签",
                missing_error="menu settings has no 资料标签 switch (profile tab is labeled 我 / Me and is locked)",
            ),
            "A-IDX-MENU-010": chk("选单设定", "Menu settings"),
        },
    },
    {
        "name": "about-version",
        "file": "flows/about-version.yaml",
        "shard": "account-a",
        "needs_login": True,
        "handler": "version",
        "checks": {"A-IDX-UPD-006": {}, "A-IDX-UPD-008": {}},
    },
    {
        "name": "about-privacy",
        "file": "flows/about-privacy.yaml",
        "shard": "account-a",
        "needs_login": True,
        "handler": "url",
        "path_hint": "/legal/privacy",
        "checks": {"A-SET-083": {}},
        "emits_parts": {"A-IDX-UPD-007": "privacy", "A-IDX-UPD-009": "privacy"},
    },
    {
        "name": "about-faq",
        "file": "flows/about-faq.yaml",
        "shard": "account-a",
        "needs_login": True,
        "handler": "url",
        "path_hint": "/help",
        "checks": {"A-SET-104": {}},
        "emits_parts": {"A-IDX-UPD-007": "faq", "A-IDX-UPD-009": "faq"},
    },
    {
        "name": "qr",
        "file": "flows/qr.yaml",
        "shard": "account-a",
        "needs_login": True,
        "handler": "maestro",
        "checks": {
            "A-IDX-SET-004": chk(
                "QR Code",
                "二维码",
                "Scan QR",
                "Scan QR Code",
                "Invite by QR Code",
                contains=True,
            ),
        },
    },
    {
        "name": "data",
        "file": "flows/data.yaml",
        "shard": "account-a",
        "needs_login": True,
        "handler": "maestro",
        "checks": {
            "A-IDX-DATA-011": chk(
                "快拍",
                "Stories",
                "Story",
                missing="blocked",
                contains=True,
                missing_error="Data and Storage did not show a 快拍 auto-download row",
            ),
        },
    },
    {
        "name": "logout",
        "file": "flows/logout.yaml",
        "shard": "account-a",
        "needs_login": True,
        "handler": "maestro",
        "checks": {
            "A-IDX-ACCT-006": chk("Your Phone", "Phone number", "电话号码", "你的手机号码", contains=True),
        },
    },
    {
        "name": "feed",
        "file": "flows/feed.yaml",
        "shard": "account-b",
        "needs_login": True,
        "handler": "maestro",
        "feed": True,
        "checks": {
            "A-MAIN-065": chk("发现", "Discover", "发布", "Publish", "好友", "Friends", "关注", "Following", contains=True),
            "A-MOMENT-001": chk("互动", "Interactions"),
            "A-MOMENT-002": chk("发布", "Publish"),
            "A-MOMENT-003": chk("发布", "Publish", "好友", "Friends", "关注", "Following", "推荐", "For you", contains=True),
            "A-MOMENT-004": chk(
                "刷新没有新内容，仍显示这些动态。",
                "Couldn't refresh. Still showing these posts.",
                missing="blocked",
                contains=True,
                missing_error="refresh-kept copy was not shown",
            ),
            "A-MOMENT-005": chk("好友", "Friends"),
            "A-MOMENT-006": chk("关注", "Following"),
            "A-MOMENT-093": chk("评论", "Comment", contains=True, needs_post=True),
            "A-MOMENT-095": chk("点赞", "Like", contains=True, needs_post=True),
            "A-MOMENT-096": chk("转发", "Forward", "Repost", contains=True, needs_post=True),
            "A-MOMENT-097": chk(mode="save", needs_post=True),
            "A-MOMENT-098": chk(mode="saved", needs_post=True),
        },
    },
    {
        "name": "notif",
        "file": "flows/notif.yaml",
        "shard": "account-b",
        "needs_login": True,
        "handler": "maestro",
        "checks": {
            "A-MOMENT-075": chk("互动", "Interactions"),
            "A-MOMENT-076": chk("互动", "Interactions"),
            "A-MOMENT-073": chk(
                "这条动态已经不存在",
                missing="blocked",
                contains=True,
                missing_error="missing-post dialog was not shown",
            ),
            "A-MOMENT-074": chk(
                "好",
                "OK",
                missing="blocked",
                missing_error="missing-post dialog was not shown",
            ),
        },
    },
    {
        "name": "composer",
        "file": "flows/composer.yaml",
        "shard": "account-b",
        "needs_login": True,
        "handler": "maestro",
        "checks": {
            "A-MOMENT-037": chk("图片", "Photo"),
            "A-MOMENT-038": chk("位置", "Place"),
            "A-MOMENT-039": chk("投票", "Poll"),
            "A-MOMENT-040": chk("视频", "Video"),
            "A-MOMENT-051": chk("发布", "Publish"),
            "A-MOMENT-053": chk("写点什么", "What's new\\?"),
            "A-MOMENT-055": chk("新动态", "New post"),
            "A-MOMENT-056": chk("新动态", "New post"),
        },
    },
    {
        "name": "composer-draft",
        "file": "flows/composer-draft.yaml",
        "shard": "account-b",
        "needs_login": True,
        "handler": "maestro",
        "checks": {
            "A-MOMENT-042": chk("这条动态还没有发布", "You have not posted this yet\\.", contains=True),
            "A-MOMENT-043": chk("保存这份草稿？", "Save this draft\\?"),
            "A-MOMENT-046": chk("取消", "Cancel"),
            "A-MOMENT-047": chk("继续编辑", "Keep editing"),
            "A-MOMENT-048": chk("放弃", "Discard"),
            "A-MOMENT-049": chk("保存草稿", "Save draft"),
        },
    },
    {
        "name": "audience",
        "file": "flows/audience.yaml",
        "shard": "account-b",
        "needs_login": True,
        "handler": "maestro",
        "checks": {
            "A-MOMENT-010": chk("密友", "Close friends"),
            "A-MOMENT-011": chk("联系人", "Contacts"),
            "A-MOMENT-012": chk("粉丝", "Followers"),
            "A-MOMENT-013": chk("互关好友", "Mutual followers"),
            "A-MOMENT-014": chk("指定的人", "Selected people"),
            "A-MOMENT-015": chk("仅自己", "Only me"),
            "A-STORY-136": chk(
                "需要你自己选择，不会记住上次",
                "Nothing is selected until you choose\\.",
                contains=True,
            ),
            "A-STORY-137": chk("谁可以看", "Who can view", "Who can see this: choose", contains=True),
            "A-IDX-MOMENT-036": chk(
                "互关好友",
                "Mutual followers",
                evidence="mutual option is on the shared privacy sheet; discover.getAudienceToken is not visible",
            ),
        },
    },
    {
        "name": "city",
        "file": "flows/city.yaml",
        "shard": "account-b",
        "needs_login": True,
        "handler": "maestro",
        "checks": {
            "A-MOMENT-016": chk(
                "改为手动选择城市",
                "Choose a city instead\\.",
                missing="blocked",
                contains=True,
                missing_error="manual-city dialog was not shown",
            ),
            "A-MOMENT-017": chk(
                "好",
                "OK",
                missing="blocked",
                context=("改为手动选择城市", "Choose a city instead"),
                missing_error="city dialog was not shown",
            ),
            "A-MOMENT-018": chk("搜索", "Search"),
            "A-MOMENT-019": chk("切换城市", "Switch city"),
            "A-MOMENT-020": chk("切换城市", "Switch city"),
            "A-PERM-007": chk(
                "位置只用来选择城市，不会保存",
                "Location is only used to pick your city",
                missing="blocked",
                contains=True,
                missing_error="precise-location prompt was not shown; location may already be granted",
            ),
        },
    },
    {
        "name": "feed-settings",
        "file": "flows/feed-settings.yaml",
        "shard": "account-b",
        "needs_login": True,
        "handler": "maestro",
        "checks": {
            "A-SET-035": chk("动态", "Moments", "列表", "Lists"),
            "A-MOMENT-104": chk("动态", "Moments", "列表", "Lists"),
            "A-MOMENT-105": chk("列表", "Lists"),
            "A-MOMENT-106": chk("动态推送", "Push"),
            "A-MOMENT-108": chk("已屏蔽的人", "Muted in feed", contains=True),
            "A-MOMENT-109": chk("动态推送", "Push"),
            "A-MOMENT-110": chk("动态", "Moments"),
            "A-MOMENT-112": chk("谁能看到我的粉丝列表", "Who can see my followers", contains=True),
        },
    },
    {
        "name": "comment-sheet",
        "file": "flows/comment-sheet.yaml",
        "shard": "account-b",
        "needs_login": True,
        "handler": "maestro",
        "feed_followup": True,
        "checks": {
            "A-MOMENT-031": chk("发条评论，说说你的感受", "Say something", contains=True),
            "A-MOMENT-032": chk("评论已关闭", "Comments are turned off", missing="blocked", contains=True),
            "A-MOMENT-033": chk("只有作者关注的人可以评论", "Only people the author follows can comment", missing="blocked", contains=True),
            "A-MOMENT-034": chk("只有被提及的人可以评论", "Only people mentioned", missing="blocked", contains=True),
            "A-MOMENT-035": chk("只有互关好友可以评论", "Only mutual followers can comment", missing="blocked", contains=True),
            "A-MOMENT-036": chk("回复 @", "Reply @", missing="blocked", contains=True),
            "A-MOMENT-022": chk("复制", "Copy", missing="blocked", context=_MENU),
            "A-MOMENT-023": chk("删除", "Delete", missing="blocked", context=_MENU),
            "A-MOMENT-024": chk("转发", "Forward", missing="blocked", context=_MENU),
            "A-MOMENT-025": chk("回复", "Reply", missing="blocked", context=_MENU),
            "A-MOMENT-026": chk("举报", "Report", missing="blocked", context=_MENU),
        },
    },
    {
        "name": "post-menu",
        "file": "flows/post-menu.yaml",
        "shard": "account-b",
        "needs_login": True,
        "handler": "maestro",
        "feed_followup": True,
        "checks": {
            "A-MOMENT-078": chk("复制链接", "Copy link", missing="blocked", context=_MENU),
            "A-MOMENT-079": chk("删除", "Delete", missing="blocked", context=_MENU),
            "A-MOMENT-080": chk("编辑", "Edit", missing="blocked", context=_MENU),
            "A-MOMENT-081": chk("其他应用", "Other", missing="blocked", contains=True, context=_MENU),
            "A-MOMENT-082": chk("隐藏该帖子", "Hide this post", missing="blocked", context=_MENU),
            "A-MOMENT-083": chk("点赞", "Like", missing="blocked", contains=True, context=_MENU),
            "A-MOMENT-084": chk("屏蔽动态", "Mute in feed", missing="blocked", context=_MENU),
            "A-MOMENT-085": chk("不感兴趣", "Not interested", missing="blocked", context=_MENU),
            "A-MOMENT-086": chk("引用", "Quote", missing="blocked", context=_MENU),
            "A-MOMENT-087": chk("举报", "Report", missing="blocked", context=_MENU),
            "A-MOMENT-088": chk("转发", "Forward", missing="blocked", context=_MENU),
            "A-MOMENT-089": chk("收藏", "Save", missing="blocked", contains=True, context=_MENU),
            "A-MOMENT-090": chk("已收藏", "Saved", missing="blocked", context=_MENU),
            "A-MOMENT-091": chk("分享到聊天", "Share to chat", "分享到聊天", missing="blocked", context=_MENU),
            "A-IDX-MOMENT-018": chk(
                "其他应用",
                missing="blocked",
                contains=True,
                context=_MENU,
                missing_error="system share was not offered on this post",
            ),
        },
    },
    {
        "name": "post-detail",
        "file": "flows/post-detail.yaml",
        "shard": "account-b",
        "needs_login": True,
        "handler": "maestro",
        "feed_followup": True,
        "checks": {
            "A-MOMENT-057": chk("复制", "Copy", missing="blocked", context=_MENU),
            "A-MOMENT-058": chk("删除", "Delete", missing="blocked", context=_MENU),
            "A-MOMENT-059": chk("转发", "Forward", missing="blocked", context=_MENU),
            "A-MOMENT-060": chk("点赞", "Like", missing="blocked", contains=True, context=_MENU),
            "A-MOMENT-061": chk("回复", "Reply", missing="blocked", context=_MENU),
            "A-MOMENT-067": chk("回复", "Reply", missing="blocked"),
            "A-MOMENT-068": chk("回复", "Reply", missing="blocked"),
            "A-MOMENT-069": chk("动态", "Post", missing="blocked"),
            "A-MOMENT-070": chk("动态", "Post", missing="blocked"),
            "A-IDX-MOMENT-026": chk(
                "图片",
                "Photo",
                missing="blocked",
                contains=True,
                missing_error="no photo was opened in the viewer",
                evidence="FeedMediaViewer is PhotoViewer; this checks that a photo control was on screen",
            ),
        },
    },
    {
        "name": "post-report",
        "file": "flows/post-report.yaml",
        "shard": "account-b",
        "needs_login": True,
        "handler": "maestro",
        "feed_followup": True,
        "checks": {
            "A-MOMENT-100": chk("取消", "Cancel", missing="blocked"),
            "A-MOMENT-101": chk("举报", "Report"),
            "A-MOMENT-102": chk("补充说明", "Description", missing="blocked"),
        },
    },
    {
        "name": "profile",
        "file": "flows/profile.yaml",
        "shard": "account-b",
        "needs_login": True,
        "handler": "profile",
        "checks": {
            "A-IDX-PROF-029": {},
            "A-PROFILE-113": chk("屏蔽动态", "Mute in feed", missing="blocked", missing_error="mute is on someone else's profile, not the signed-in one"),
            "A-PROFILE-114": chk("赞过", "Liked", missing="blocked"),
            "A-PROFILE-115": chk("动态收藏", "Saved posts", missing="blocked", contains=True),
            "A-PROFILE-295": chk("帖子", "All posts", "Posts", missing="blocked"),
            "A-PROFILE-296": {},
            "A-IDX-STORY-028": {},
            "A-IDX-MEDIA-009": {},
        },
    },
]


def _index():
    by_name = {}
    for flow in FLOWS:
        if flow["name"] in by_name:
            raise RuntimeError(f"duplicate flow {flow['name']}")
        by_name[flow["name"]] = flow
    return by_name


FLOWS_BY_NAME = _index()


def backlog_ids() -> list[str]:
    with BACKLOG.open(newline="", encoding="utf-8") as handle:
        return [row["id"] for row in csv.DictReader(handle)]


def flow_files(flow: dict) -> list[str]:
    if flow.get("files"):
        return list(flow["files"])
    return [flow["file"]]


def covered_ids() -> dict[str, str]:
    """Id → where it is covered. Raises on overlap."""
    found: dict[str, str] = {}

    def claim(backlog_id: str, where: str) -> None:
        if backlog_id in found:
            raise RuntimeError(f"{backlog_id} claimed by {found[backlog_id]} and {where}")
        found[backlog_id] = where

    for backlog_id in DEVICE_ONLY:
        claim(backlog_id, "device_only")
    for backlog_id in STATIC_BLOCKED:
        claim(backlog_id, "blocked")
    for backlog_id in PARTS:
        claim(backlog_id, "parts")
    for flow in FLOWS:
        for backlog_id in flow.get("checks", {}):
            claim(backlog_id, flow["name"])
        for backlog_id in flow.get("emits_parts", {}):
            if backlog_id not in PARTS:
                raise RuntimeError(f"{flow['name']} emits unknown part id {backlog_id}")
            if flow["emits_parts"][backlog_id] not in PARTS[backlog_id]:
                raise RuntimeError(f"{flow['name']} emits unknown part {flow['emits_parts'][backlog_id]}")
    return found


def manifest_document() -> dict:
    covered = covered_ids()
    rows = []
    for backlog_id in backlog_ids():
        where = covered[backlog_id]
        if where == "device_only":
            kind = "device_only"
            detail = DEVICE_ONLY[backlog_id]
        elif where == "blocked":
            kind = "blocked"
            detail = STATIC_BLOCKED[backlog_id]
        elif where == "parts":
            kind = "parts"
            detail = ",".join(PARTS[backlog_id])
        else:
            flow = FLOWS_BY_NAME[where]
            kind = flow["handler"]
            detail = flow.get("file") or ",".join(flow.get("files") or [])
        rows.append({
            "id": backlog_id,
            "kind": kind,
            "where": where,
            "detail": detail,
            "shard": "" if where in ("device_only", "blocked", "parts") else FLOWS_BY_NAME[where]["shard"],
        })
    return {
        "schema": 1,
        "device_only": DEVICE_ONLY,
        "blocked": STATIC_BLOCKED,
        "parts": PARTS,
        "push_delivery": "device_only. This backlog extract has no push-delivery id; settings toggles are UI checks.",
        "ids": rows,
    }
