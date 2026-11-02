#!/usr/bin/env python3
"""Regenerate Tellomi phase-1 icons from MingCute (Apache-2.0).

Reads nothing but this file's tables. Writes:

  tellomi/icons/mapping.csv
  tellomi/icons/mingcute/LICENSE
  tellomi/icons/mingcute/NOTICE
  tellomi/icons/mingcute/svg/<line|fill>/*.svg   (only the icons used)
  TMessagesProj/tellomi/res/   overlays with the same resource names

Raster overlays are white-on-transparent WebP at the source pixel size.
Vector overlays are VectorDrawable. Swipe Lotties become a one-frame
shape of the same glyph so RLottieDrawable still plays. Bottom tabs are
new line/fill drawables; GlassTabView crossfades them.

MingCute checkout (not committed):

  git clone --depth 1 --filter=blob:none --sparse \\
      https://github.com/mingcute-design/mingcute-icons.git
  git sparse-checkout set packages/svg

Point MINGCUTE_SVG at packages/svg. Default: /tmp/mingcute/packages/svg.
"""

import csv
import os
import re
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image
from svgpathtools import parse_path, Arc, CubicBezier, Line, QuadraticBezier

ROOT = Path(__file__).resolve().parents[2]
RES_MAIN = ROOT / "src/main/res"
RES_OUT = ROOT / "tellomi/res"
ICONS = Path(__file__).resolve().parent
SVG_ROOT = Path(os.environ.get("MINGCUTE_SVG", "/tmp/mingcute/packages/svg"))
NS = {"android": "http://schemas.android.com/apk/res/android", "svg": "http://www.w3.org/2000/svg"}

# Not phase 1: brand marks, layered composites, shadows, status dots.
SKIP = {
    "telegram_logo_2", "ic_gram", "outline_gram_24", "settings_gram_24",
    "dialog_gram_transfer", "redcircle", "tooltip_arrow", "floating_shadow_profile",
    "star_small_inner", "star_small_outline", "input_calendar1", "input_calendar2",
    "avd_speed", "poll_add_circle", "poll_add_plus", "verified_area", "verified_check",
    "chats_archive_arrow", "chats_archive_box", "chats_archive_muted", "chats_archive_pin",
    "tellomi_ic_apple", "tellomi_ic_mail", "tellomi_ic_more", "tellomi_ic_eye",
    "tellomi_ic_eye_off", "tellomi_ic_swap", "login_arrow1", "login_phone1",
    "notification",
}

# (substring, mingcute name, style). First match wins. Longer keys first.
RULES = [
    ("tab_chats_fill", "chat-1", "fill"),
    ("tab_contacts_fill", "contacts", "fill"),
    ("tab_calls_fill", "phone", "fill"),
    ("tab_settings_fill", "settings-3", "fill"),
    ("tab_chats", "chat-1", "line"),
    ("tab_contacts", "contacts", "line"),
    ("tab_calls", "phone", "line"),
    ("tab_settings", "settings-3", "line"),
    ("swipe_community", "group", "line"),
    ("swipe_unmute", "volume", "line"),
    ("swipe_unread", "mail", "line"),
    ("swipe_unpin", "pin-2", "line"),
    ("swipe_mute", "volume-mute", "line"),
    ("swipe_delete", "delete-2", "line"),
    ("swipe_read", "checks", "line"),
    ("swipe_pin", "pin", "line"),
    ("chats_unarchive", "archive-restore", "line"),
    ("chats_archiveavatar", "archive", "line"),
    ("chats_swipearchive", "archive", "line"),
    ("chats_archived", "archive", "line"),
    ("chats_unhide", "eye", "line"),
    ("chats_archive", "archive", "line"),
    ("chats_hide", "eye-close", "line"),
    ("chats_unpin", "pin-2", "line"),
    ("chats_pin", "pin", "line"),
    ("mic_pressed", "mic", "fill"),
    ("video_pressed", "video", "fill"),
    ("input_mic", "mic", "line"),
    ("input_video", "video", "line"),
    ("input_smile", "emoji", "line"),
    ("input_keyboard", "keyboard", "line"),
    ("input_sticker", "sticker", "line"),
    ("msg_gif", "film", "line"),
    ("send_plane", "send", "line"),
    ("send_outline", "send", "line"),
    ("attach_send", "send", "fill"),
    ("msg_send", "send", "line"),
    ("inputarrow", "arrow-right", "line"),
    ("input_attach", "attachment-2", "line"),
    ("mini_attach", "attachment-2", "line"),
    ("msg_input_attach", "attachment-2", "line"),
    ("input_schedule", "calendar", "line"),
    ("input_done", "check", "line"),
    ("input_notify_off", "notification-off", "line"),
    ("input_notify_on", "notification", "line"),
    ("input_suggest", "coin", "line"),
    ("input_bot", "bot-message-square", "line"),
    ("input_clear", "close", "line"),
    ("outline_attach_camera", "camera-2", "line"),
    ("unarchive", "archive-restore", "line"),
    ("archive", "archive", "line"),
    ("unmute", "notification", "line"),
    ("markunread", "mail", "line"),
    ("markread", "checks", "line"),
    ("unpin", "pin-2", "line"),
    ("pin", "pin", "line"),
    ("mute", "notification-off", "line"),
    ("silent", "volume-mute", "line"),
    ("delete", "delete-2", "line"),
    ("clear_cache", "broom", "line"),
    ("clear_cookie", "broom", "line"),
    ("clear_recent", "history-anticlockwise", "line"),
    ("clear", "delete-2", "line"),
    ("copy", "copy-2", "line"),
    ("edit", "edit-2", "line"),
    ("forward", "forward", "line"),
    ("reply", "corner-up-left", "line"),
    ("shareout", "share-3", "line"),
    ("share_off", "share-2", "line"),
    ("share_on", "share-2", "line"),
    ("share", "share-2", "line"),
    ("search", "search-2", "line"),
    ("qr", "qrcode", "line"),
    ("translate", "translate", "line"),
    ("calendar", "calendar", "line"),
    ("download", "download-2", "line"),
    ("upload", "upload", "line"),
    ("lock", "lock", "line"),
    ("fingerprint", "fingerprint", "line"),
    ("eye", "eye", "line"),
    ("sticker", "sticker", "line"),
    ("keyboard", "keyboard", "line"),
    ("emoji", "emoji", "line"),
    ("smile", "emoji", "line"),
    ("gif", "film", "line"),
    ("customize", "settings-3", "line"),
    ("expand", "fullscreen", "line"),
    ("reaction", "emoji", "line"),
    ("stealth", "eye-close", "line"),
    ("viewreplies", "message-3", "line"),
    ("viewintopic", "message-3", "line"),
    ("addbot", "bot-message-square", "line"),
    ("addfolder", "folder", "line"),
    ("addphoto", "gallery", "line"),
    ("removefolder", "folder", "line"),
    ("callback", "phone", "line"),
    ("photoeditor", "brush", "line"),
    ("pinnedlist", "pin", "line"),
    ("pollstop", "chart-bar", "line"),
    ("qrcode", "qrcode", "line"),
    ("usersearch", "search-2", "line"),
    ("voicechat", "mic", "line"),
    ("invit", "share-2", "line"),
    ("camera", "camera-2", "line"),
    ("videocall", "video", "line"),
    ("video", "video", "line"),
    ("voice", "mic", "line"),
    ("mic", "mic", "line"),
    ("phone", "phone", "line"),
    ("call", "phone", "line"),
    ("contact", "contacts", "line"),
    ("group", "group", "line"),
    ("channel", "announcement", "line"),
    ("discussion", "message-3", "line"),
    ("message", "message-3", "line"),
    ("chat", "chat-1", "line"),
    ("folder", "folder", "line"),
    ("gift", "gift", "line"),
    ("link", "link", "line"),
    ("user", "user-3", "line"),
    ("profile", "user-3", "line"),
    ("admin", "user-3", "line"),
    ("mention", "at", "line"),
    ("language", "translate", "line"),
    ("notification", "notification", "line"),
    ("policy", "shield", "line"),
    ("privacy", "shield", "line"),
    ("permission", "shield", "line"),
    ("help", "question", "line"),
    ("question", "question", "line"),
    ("device", "device", "line"),
    ("storage", "storage", "line"),
    ("data", "chart-bar", "line"),
    ("battery", "battery", "line"),
    ("secret", "lock", "line"),
    ("premium", "star", "line"),
    ("star", "star", "line"),
    ("fave", "star", "line"),
    ("unfave", "star", "line"),
    ("settings", "settings-3", "line"),
    ("theme", "palette", "line"),
    ("color", "palette", "line"),
    ("night", "moon", "line"),
    ("day_mode", "sun", "line"),
    ("stats", "chart-bar", "line"),
    ("poll", "chart-bar", "line"),
    ("bot", "bot-message-square", "line"),
    ("code", "code", "line"),
    ("info", "information", "line"),
    ("report", "alert", "line"),
    ("block", "forbid-circle", "line"),
    ("ban", "forbid-circle", "line"),
    ("leave", "exit-door", "line"),
    ("home", "home-2", "line"),
    ("gallery", "gallery", "line"),
    ("photo", "gallery", "line"),
    ("image", "gallery", "line"),
    ("file", "file", "line"),
    ("music", "music", "line"),
    ("tone", "music", "line"),
    ("saved", "bookmark", "line"),
    ("bookmark", "bookmark", "line"),
    ("tag", "tag", "line"),
    ("wallet", "wallet", "line"),
    ("payment", "bank-card", "line"),
    ("card", "bank-card", "line"),
    ("coin", "coin", "line"),
    ("shop", "store", "line"),
    ("refund", "history-anticlockwise", "line"),
    ("map", "map-pin", "line"),
    ("location", "location", "line"),
    ("username", "at", "line"),
    ("request", "inbox", "line"),
    ("topic", "message-3", "line"),
    ("background", "palette", "line"),
    ("autodelete", "clock", "line"),
    ("timer", "clock", "line"),
    ("online", "check-circle", "line"),
    ("speed", "lightning", "line"),
    ("openin", "external-link", "line"),
    ("open", "external-link", "line"),
    ("add", "add", "line"),
    ("remove", "close", "line"),
    ("cancel", "close", "line"),
    ("close", "close", "line"),
    ("back", "arrow-left", "line"),
    ("newchat", "edit-2", "line"),
    ("arrowright", "arrow-right", "line"),
    ("arrow", "arrow-right", "line"),
    ("check", "check", "line"),
    ("done", "check", "line"),
    ("reorder", "menu", "line"),
    ("more", "more-2", "line"),
    ("other", "more-2", "line"),
    ("menu", "more-2", "line"),
    ("filter", "filter-2", "line"),
    ("website", "globe", "line"),
    ("instant", "lightning", "line"),
    ("fact", "shield", "line"),
    ("retry", "clockwise", "line"),
    ("replace", "refresh-2", "line"),
    ("fullscreen", "fullscreen", "line"),
    ("rate_up", "thumb-up-2", "line"),
    ("rate_down", "thumb-down-2", "line"),
    ("unvote", "close", "line"),
    ("revert", "history-anticlockwise", "line"),
    ("sleeping", "moon", "line"),
    ("affiliate", "link", "line"),
    ("rating", "star", "line"),
    ("crown", "award", "line"),
    ("boost", "lightning", "line"),
    ("limit", "lightning", "line"),
    ("paid", "coin", "line"),
    ("price", "coin", "line"),
    ("username", "at", "line"),
    ("header_search", "search-2", "line"),
    ("outline_saved", "bookmark", "line"),
    ("outline_groups", "group", "line"),
    ("outline_header_lock", "lock", "line"),
    ("outline_fab", "add", "line"),
    ("filled_requests", "inbox", "line"),
    ("filled_profile", "user-3", "fill"),
    ("filled_username", "at", "fill"),
    ("filled_access_location", "location", "fill"),
    ("filled_access_fingerprint", "fingerprint", "fill"),
    ("filled_access_sleeping", "moon", "fill"),
    ("filled_affiliate", "link", "fill"),
    ("filled_rating", "star", "fill"),
    ("filled_add_album", "gallery", "fill"),
    ("filled_paid", "coin", "fill"),
    ("filled_reply", "corner-up-left", "fill"),
    ("filled_forward", "forward", "fill"),
    ("filled_link", "link", "fill"),
    ("filled_limit", "lightning", "fill"),
    ("filled_fab", "edit-2", "fill"),
    ("premium_camera", "camera-2", "line"),
    ("filled_rating_crown", "award", "fill"),
    ("sdcard", "storage", "line"),
    ("data_photos", "gallery", "line"),
    ("data_videos", "video", "line"),
    ("datausage", "chart-bar", "line"),
    ("list_mute", "notification-off", "line"),
    ("list_unmute", "notification", "line"),
    ("list_pin", "pin", "line"),
    ("list_check", "checks", "line"),
    ("list_halfcheck", "check", "line"),
    ("list_reorder", "menu", "line"),
    ("list_warning", "alert", "line"),
    ("list_secret", "lock", "line"),
    ("msg_pin_mini", "pin", "line"),
    ("mini_forwarded", "forward", "line"),
    ("mini_gift", "gift", "line"),
    ("minithumb_play", "play", "line"),
    ("community_cards", "group", "line"),
    ("filled_chatlist_mention", "at", "fill"),
    ("filled_chatlist_reaction", "emoji", "fill"),
    ("filled_chatlist_poll", "chart-bar", "fill"),
    ("mini_ephemeral", "eye-close", "line"),
    ("mini_switch_lock", "lock", "line"),
    ("msg_mini_lock", "lock", "line"),
    ("msg_mini_forum", "arrow-right", "line"),
    ("msg_mini_reply", "corner-up-left", "line"),
    ("msg_mini_close", "close", "line"),
    ("msg_mini_autodelete", "clock", "line"),
    ("round_warn", "alert", "line"),
    ("floating_check", "check", "line"),
    ("ic_ab_back", "arrow-left", "line"),
    ("ic_ab_reply", "corner-up-left", "line"),
    ("ic_ab_reply2", "corner-up-left", "line"),
    ("ic_ab_other", "more-2", "line"),
    ("ic_ab_search", "search-2", "line"),
    ("ic_ab_done", "check", "line"),
    ("ic_close", "close", "line"),
    ("settings_arrow", "arrow-right", "line"),
    ("navbar_search", "search-2", "line"),
    ("tabs_reorder", "menu", "line"),
    ("nav_edit", "edit-2", "line"),
    ("miniplayer_close", "close", "line"),
    ("tooltip_sound", "volume", "line"),
    ("dialog_media_poll", "chart-bar", "line"),
    ("dialog_media_checklist", "list-check", "line"),
    ("dialog_media_game", "game-2", "line"),
    ("dialog_media_outgoing_video", "video", "line"),
    ("dialog_media_incoming_video", "video", "line"),
    ("dialog_media_outgoing_call", "phone", "line"),
    ("dialog_media_incoming_call", "phone", "line"),
    ("forward_to_stories", "forward", "line"),
    ("profile_video", "video", "line"),
    ("profile_newmsg", "message-3", "line"),
    ("profile_discuss", "message-3", "line"),
    ("verified_profile", "certificate", "line"),
    ("managed_bot", "bot-message-square", "line"),
    ("menu_download_round", "download-2", "line"),
    ("menu_topic_add", "add", "line"),
    ("menu_paid_off", "coin", "line"),
    ("menu_delete_paid", "delete-2", "line"),
    ("menu_refund", "history-anticlockwise", "line"),
    ("menu_shop", "store", "line"),
    ("menu_stars", "star", "line"),
    ("menu_storage", "storage", "line"),
    ("menu_reaction", "emoji", "line"),
    ("menu_premium", "star", "line"),
    ("menu_invit", "share-2", "line"),
    ("menu_contact", "contacts", "line"),
    ("menu_instant", "lightning", "line"),
    ("menu_fact", "shield", "line"),
    ("menu_clear", "broom", "line"),
    ("menu_tag", "tag", "line"),
    ("menu_profile", "palette", "line"),
    ("menu_username", "at", "line"),
    ("menu_privacy", "shield", "line"),
    ("menu_website", "globe", "line"),
    ("menu_share", "share-2", "line"),
    ("menu_day", "sun", "line"),
    ("menu_night", "moon", "line"),
    ("menu_gift", "gift", "line"),
    ("menu_edit", "edit-2", "line"),
    ("menu_delete", "delete-2", "line"),
    ("menu_reply", "corner-up-left", "line"),
    ("msg2_data", "chart-bar", "line"),
    ("msg2_discussion", "message-3", "line"),
    ("msg2_battery", "battery", "line"),
    ("msg2_secret", "lock", "line"),
    ("msg2_language", "translate", "line"),
    ("msg2_help", "question", "line"),
    ("msg2_devices", "device", "line"),
    ("msg2_folder", "folder", "line"),
    ("msg2_notifications", "notification", "line"),
    ("msg2_ask", "question", "line"),
    ("msg2_policy", "shield", "line"),
    ("msg2_sticker", "sticker", "line"),
    ("call", "phone", "line"),
]

PHASE1_FILES = [
    "DialogsActivity.java",
    "DialogCell.java",
    "ChatActivity.java",
    "ChatActivityEnterView.java",
    "ChatActivityEnterViewAnimatedIconView.java",
    "ProfileActivity.java",
    "ActionBar.java",
    "ActionBarMenuItem.java",
    "ArchiveHintInnerCell.java",
    "FiltersView.java",
    "SwipeGestureSettingsView.java",
]

# Names that must exist even if no Java file in the inventory scan references them.
EXTRAS = [
    "list_secret", "list_check", "list_halfcheck", "list_warning_sign", "list_reorder",
    "list_mute", "list_unmute", "list_pin", "community_cards", "minithumb_play",
    "filled_chatlist_mention", "filled_chatlist_reaction", "filled_chatlist_poll",
    "msg_pin_mini",
    "msg_inputarrow",
    "tab_chats", "tab_chats_fill", "tab_contacts", "tab_contacts_fill",
    "tab_calls", "tab_calls_fill", "tab_settings", "tab_settings_fill",
    "swipe_mute", "swipe_unmute", "swipe_delete", "swipe_read", "swipe_unread",
    "swipe_pin", "swipe_unpin", "swipe_community_ungroup",
    "chats_archive", "chats_unarchive", "chats_hide", "chats_unhide",
    "chats_archiveavatar", "chats_archived", "chats_swipearchive",
]


def match(name):
    low = name.lower()
    tokens = low.split("_")
    if "gram" in tokens or low.startswith("tellomi_ic_") or low.startswith("login_"):
        return None
    rules = sorted(RULES, key=lambda r: len(r[0]), reverse=True)
    for key, icon, style in rules:
        if "_" in key or len(key) >= 7:
            if key in low:
                return icon, style
            continue
        for token in tokens:
            if token == key or (token.startswith(key) and token[len(key):] in {"s", "es", "ed", "ing", "d", "2"}):
                return icon, style
    return None


def resources_on_disk():
    found = {}
    if not RES_MAIN.exists():
        return found
    for dirpath, _, files in os.walk(RES_MAIN):
        folder = Path(dirpath).name
        for f in files:
            if f.endswith(".9.png"):
                continue
            stem, ext = os.path.splitext(f)
            if ext not in {".webp", ".png", ".xml", ".jpg"}:
                continue
            found.setdefault(stem, []).append((folder, ext, Path(dirpath) / f))
    return found


def raw_on_disk():
    raw = RES_MAIN / "raw"
    out = {}
    if raw.exists():
        for p in raw.glob("*.json"):
            out[p.stem] = p
    return out


def kind_for(name, files, raws):
    if name.startswith("tab_"):
        return "tab"
    if name.startswith("swipe_") or name in {
        "chats_archiveavatar", "chats_archived", "chats_swipearchive",
        "chats_unarchive", "chats_hide", "chats_unhide",
    }:
        if name in raws:
            return "raw"
    if name == "chats_archive" and name in raws:
        # The swipe animation is the raw file. The drawable is replaced too.
        return "both"
    folders = {folder for folder, ext, _ in files.get(name, [])}
    if any(f.startswith("drawable-") for f in folders):
        return "raster"
    if "drawable" in folders:
        return "vector"
    if name in raws:
        return "raw"
    return None


def is_monochrome(path):
    try:
        im = Image.open(path).convert("RGBA")
    except Exception:
        return True
    im.thumbnail((48, 48))
    colored = 0
    opaque = 0
    for r, g, b, a in im.getdata():
        if a < 40:
            continue
        opaque += 1
        if max(r, g, b) - min(r, g, b) > 28:
            colored += 1
    if opaque < 8:
        return True
    return colored / opaque < 0.08


def vector_is_tinted(path):
    text = path.read_text(errors="ignore")
    colors = set(re.findall(r"#([0-9A-Fa-f]{6,8})", text))
    chromatic = []
    for c in colors:
        rgb = c[-6:]
        r, g, b = int(rgb[0:2], 16), int(rgb[2:4], 16), int(rgb[4:6], 16)
        if max(r, g, b) - min(r, g, b) > 28 and not (r > 230 and g > 230 and b > 230):
            chromatic.append(c)
    return len(chromatic) == 0


def svg_path(icon, style):
    folder = "core-regular" if style == "line" else "core-filled"
    path = SVG_ROOT / folder / f"{icon}.svg"
    if not path.exists():
        # a few names in the rules are guesses; try without a trailing variant
        return None
    return path


def render_webp(svg, width, height, dest):
    import subprocess
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(".png")
    svg_text = svg.read_text().replace("currentColor", "#FFFFFF")
    painted = tmp.with_suffix(".src.svg")
    painted.write_text(svg_text)
    subprocess.check_call(["rsvg-convert", "-w", str(width), "-h", str(height), "-o", str(tmp), str(painted)])
    painted.unlink(missing_ok=True)
    im = Image.open(tmp).convert("RGBA")
    # Force white RGB, keep coverage in alpha. rsvg paints #fff already;
    # re-stamp so a black fallback still tints.
    pixels = []
    for r, g, b, a in im.getdata():
        if a == 0:
            pixels.append((0, 0, 0, 0))
        else:
            cover = a if (r + g + b) > 30 else max(a, 255 - max(r, g, b))
            pixels.append((255, 255, 255, cover))
    im.putdata(pixels)
    im.save(dest, "WEBP", lossless=True, quality=100, method=4)
    tmp.unlink(missing_ok=True)


def android_vector(svg, width_dp, height_dp):
    tree = ET.parse(svg)
    root = tree.getroot()
    vb = root.attrib.get("viewBox", "0 0 24 24").split()
    vw, vh = vb[-2], vb[-1]
    paths = []
    for el in root.iter():
        tag = el.tag.split("}")[-1]
        if tag != "path":
            continue
        d = el.attrib.get("d")
        if not d:
            continue
        fill = el.attrib.get("fill", "currentColor")
        stroke = el.attrib.get("stroke")
        sw = el.attrib.get("stroke-width")
        item = {"d": d}
        if fill and fill != "none":
            item["fill"] = "#FFFFFF"
        if stroke and stroke != "none":
            item["stroke"] = "#FFFFFF"
            if sw:
                item["sw"] = sw
        if el.attrib.get("fill-rule") == "evenodd":
            item["even"] = True
        paths.append(item)
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{width_dp}dp"',
        f'    android:height="{height_dp}dp"',
        f'    android:viewportWidth="{vw}"',
        f'    android:viewportHeight="{vh}">',
    ]
    for p in paths:
        attrs = [f'android:pathData="{p["d"]}"']
        if p.get("fill"):
            attrs.append('android:fillColor="#FFFFFF"')
        if p.get("stroke"):
            attrs.append('android:strokeColor="#FFFFFF"')
            attrs.append(f'android:strokeWidth="{p.get("sw", "1")}"')
        if p.get("even"):
            attrs.append('android:fillType="evenOdd"')
        lines.append("    <path " + " ".join(attrs) + " />")
    lines.append("</vector>")
    return "\n".join(lines) + "\n", len(paths)


def vector_dp(path):
    text = path.read_text(errors="ignore")
    w = re.search(r'android:width="([0-9.]+)dp"', text)
    h = re.search(r'android:height="([0-9.]+)dp"', text)
    return (w.group(1) if w else "24", h.group(1) if h else "24")


def _cpx(z):
    return (float(z.real), float(z.imag))


def _as_cubic(seg):
    if isinstance(seg, Arc):
        return list(seg.as_cubic_curves(curves=4))
    if isinstance(seg, QuadraticBezier):
        # quadratic to cubic
        p0, p1, p2 = seg.start, seg.control, seg.end
        return [CubicBezier(p0, p0 + (p1 - p0) * (2 / 3), p2 + (p1 - p2) * (2 / 3), p2)]
    if isinstance(seg, Line):
        return [CubicBezier(seg.start, seg.start, seg.end, seg.end)]
    if isinstance(seg, CubicBezier):
        return [seg]
    return []


def path_to_contours(d):
    path = parse_path(d)
    contours = []
    for sub in path.continuous_subpaths():
        cubics = []
        for seg in sub:
            cubics.extend(_as_cubic(seg))
        if not cubics:
            continue
        v = [_cpx(cubics[0].start)]
        o = []
        i = [(0.0, 0.0)]
        for c in cubics:
            c1, end, c2 = _cpx(c.control1), _cpx(c.end), _cpx(c.control2)
            start = v[-1]
            o.append((c1[0] - start[0], c1[1] - start[1]))
            v.append(end)
            i.append((c2[0] - end[0], c2[1] - end[1]))
        closed = abs(v[-1][0] - v[0][0]) < 0.05 and abs(v[-1][1] - v[0][1]) < 0.05
        if closed and len(v) > 1:
            i[0] = i[-1]
            v = v[:-1]
            i = i[:-1]
        if len(o) != len(v):
            # open path: out tangents are one per segment, vertices are n+1
            if len(v) == len(o) + 1:
                o.append((0.0, 0.0))
            else:
                continue
        contours.append((v, i, o, closed))
    return contours


def lottie_json(svg, name):
    tree = ET.parse(svg)
    root = tree.getroot()
    shapes = []
    for el in root.iter():
        if el.tag.split("}")[-1] != "path":
            continue
        d = el.attrib.get("d")
        if not d:
            continue
        for v, i, o, closed in path_to_contours(d):
            shapes.append({
                "ty": "gr",
                "nm": "g",
                "it": [
                    {
                        "ty": "sh",
                        "ks": {"a": 0, "k": {"c": closed, "v": v, "i": i, "o": o}},
                    },
                    {"ty": "fl", "c": {"a": 0, "k": [1, 1, 1, 1]}, "o": {"a": 0, "k": 100}},
                    {
                        "ty": "tr",
                        "p": {"a": 0, "k": [0, 0]},
                        "a": {"a": 0, "k": [0, 0]},
                        "s": {"a": 0, "k": [100, 100]},
                        "r": {"a": 0, "k": 0},
                        "o": {"a": 0, "k": 100},
                    },
                ],
            })
    doc = {
        "v": "5.5.7",
        "fr": 30,
        "ip": 0,
        "op": 2,
        "w": 24,
        "h": 24,
        "nm": name,
        "ddd": 0,
        "assets": [],
        "layers": [{
            "ddd": 0, "ind": 1, "ty": 4, "nm": name, "sr": 1,
            "ks": {
                "o": {"a": 0, "k": 100},
                "r": {"a": 0, "k": 0},
                "p": {"a": 0, "k": [0, 0, 0]},
                "a": {"a": 0, "k": [0, 0, 0]},
                "s": {"a": 0, "k": [100, 100, 100]},
            },
            "ao": 0,
            "shapes": shapes,
            "ip": 0, "op": 2, "st": 0, "bm": 0,
        }],
    }
    return doc, len(shapes)


def copy_license():
    lic_src = SVG_ROOT / "LICENSE"
    if not lic_src.exists():
        lic_src = SVG_ROOT.parent.parent / "LICENSE"
    dest = ICONS / "mingcute"
    dest.mkdir(parents=True, exist_ok=True)
    shutil.copy(lic_src, dest / "LICENSE")
    (dest / "NOTICE").write_text(
        "MingCute icon library\n"
        "https://github.com/Richard9394/MingCute\n"
        "Current tree: https://github.com/mingcute-design/mingcute-icons\n"
        "\n"
        "The icons under this directory and the drawables generated from them\n"
        "are licensed under the Apache License, Version 2.0. See LICENSE.\n"
        "Tellomi does not ship an in-app open-source licenses screen; this\n"
        "NOTICE is the attribution record for the MingCute subset used here.\n"
    )


def phase1_names():
    import re
    root = ROOT / "src/main/java"
    pat = re.compile(r"R\.drawable\.([A-Za-z0-9_]+)")
    names = set(EXTRAS)
    for dirpath, _, fs in os.walk(root):
        for f in fs:
            if not f.endswith(".java"):
                continue
            if f in PHASE1_FILES or ("Settings" in f and "/ui/" in (dirpath + "/").replace("\\", "/")):
                text = (Path(dirpath) / f).read_text(errors="ignore")
                names.update(pat.findall(text))
    # Theme.java chat-list glyphs are referenced from there, not the activities.
    theme = root / "org/telegram/ui/ActionBar/Theme.java"
    if theme.exists():
        text = theme.read_text(errors="ignore")
        for n in re.findall(r"R\.drawable\.(list_[A-Za-z0-9_]+|filled_chatlist_[A-Za-z0-9_]+|msg_pin_mini|community_cards|minithumb_play|mini_ephemeral_[A-Za-z0-9_]+)", text):
            names.add(n)
    return names


def main():
    files = resources_on_disk()
    raws = raw_on_disk()
    allow = phase1_names()
    names = (set(files) | set(raws) | set(EXTRAS)) & (allow | set(EXTRAS))
    rows = []
    remaining = []
    used_svg = []
    for name in sorted(names):
        if name in SKIP or name.startswith("tellomi_ic_") or name.startswith("login_"):
            remaining.append((name, "skipped"))
            continue
        mapped = match(name)
        if not mapped:
            if name in EXTRAS or name in files or (name in raws and name.startswith(("swipe_", "chats_"))):
                remaining.append((name, "no mingcute match"))
            continue
        # only emit resources that are in scope: extras, or present in the
        # phase-1 inventory (anything we matched that exists on disk and is
        # a UI icon we intentionally listed via rules). Rules match broadly,
        # so require the resource to exist.
        if name not in files and name not in raws and not name.startswith("tab_"):
            continue
        icon, style = mapped
        svg = svg_path(icon, style)
        if svg is None:
            remaining.append((name, f"missing mingcute {icon} {style}"))
            continue
        kind = kind_for(name, files, raws)
        if kind is None:
            remaining.append((name, "no resource file"))
            continue
        if kind in ("raster", "both"):
            bitmaps = [(folder, ext, path) for folder, ext, path in files.get(name, []) if folder.startswith("drawable-")]
            if bitmaps:
                sample = bitmaps[0][2]
                if sample.suffix.lower() in {".webp", ".png", ".jpg"} and not is_monochrome(sample):
                    remaining.append((name, "colored raster, left for later"))
                    if kind == "raster":
                        continue
                    kind = "raw"
                else:
                    for folder, ext, path in bitmaps:
                        im = Image.open(path)
                        w, h = im.size
                        if max(w, h) > 256:
                            remaining.append((name, f"large bitmap {w}x{h}"))
                            bitmaps = []
                            break
                    if not bitmaps:
                        if kind == "raster":
                            continue
                    else:
                        for folder, ext, path in bitmaps:
                            im = Image.open(path)
                            dest = RES_OUT / folder / f"{name}.webp"
                            render_webp(svg, im.size[0], im.size[1], dest)
                        rows.append((name, icon, style, "raster"))
                        used_svg.append((svg, style, icon))
                        if kind == "raster":
                            continue
        if kind in ("vector",):
            xmls = [path for folder, ext, path in files.get(name, []) if folder == "drawable" and ext == ".xml"]
            if not xmls:
                remaining.append((name, "vector missing"))
                continue
            if not vector_is_tinted(xmls[0]):
                remaining.append((name, "colored vector, left for later"))
                continue
            w, h = vector_dp(xmls[0])
            body, npaths = android_vector(svg, w, h)
            if npaths == 0:
                remaining.append((name, "svg has no path"))
                continue
            dest = RES_OUT / "drawable" / f"{name}.xml"
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(body)
            rows.append((name, icon, style, "vector"))
            used_svg.append((svg, style, icon))
            continue
        if kind in ("tab",):
            for folder, px in (("drawable-mdpi", 24), ("drawable-hdpi", 36), ("drawable-xhdpi", 48), ("drawable-xxhdpi", 72)):
                render_webp(svg, px, px, RES_OUT / folder / f"{name}.webp")
            rows.append((name, icon, style, "tab"))
            used_svg.append((svg, style, icon))
            continue
        if kind in ("raw", "both"):
            import json
            doc, nshapes = lottie_json(svg, name)
            if nshapes == 0:
                remaining.append((name, "lottie has no shape"))
                continue
            dest = RES_OUT / "raw" / f"{name}.json"
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_text(json.dumps(doc, separators=(",", ":")))
            rows.append((name, icon, style, "raw"))
            used_svg.append((svg, style, icon))

    copy_license()
    seen = set()
    for svg, style, icon in used_svg:
        key = (style, icon)
        if key in seen:
            continue
        seen.add(key)
        bucket = "line" if style == "line" else "fill"
        dest = ICONS / "mingcute" / "svg" / bucket / f"{icon}.svg"
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(svg, dest)

    csv_path = ICONS / "mapping.csv"
    with csv_path.open("w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["telegram_resource", "mingcute_name", "style", "kind"])
        for row in rows:
            w.writerow(row)

    rem_path = ICONS / "remaining.txt"
    # only list candidates we considered (extras + matched names that failed, and inventory-like existing that we skipped)
    lines = [f"{name}\t{why}" for name, why in remaining if name in files or name in raws or name in EXTRAS or name in SKIP]
    # phase-1 inventory that never matched
    rem_path.write_text("\n".join(lines) + "\n")
    print(f"replaced {len(rows)}")
    print(f"mapping {csv_path}")
    from collections import Counter
    print(Counter(r[3] for r in rows))
    print(f"remaining lines {len(lines)}")


if __name__ == "__main__":
    main()
