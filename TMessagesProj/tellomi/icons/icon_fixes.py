#!/usr/bin/env python3
"""Redraw Tellomi icons whose MingCute mapping changed the meaning.

Exact overrides win over the token rules in generate_icons.py. Names in
REVERT_NAMES lose their flavor overlay so the upstream drawable is used.
Composites (slash, plus, arrow, half-check, GIF, sticker) are drawn here
because MingCute has no single glyph for them.

    python3 TMessagesProj/tellomi/icons/icon_fixes.py
"""

import csv
import io
import math
import re
from pathlib import Path

import cairosvg
from PIL import Image, ImageDraw, ImageFont

import generate_icons as gen

ICONS = Path(__file__).resolve().parent
ROOT = gen.ROOT
RES = gen.RES_OUT
MAIN = gen.RES_MAIN
MAPPING = ICONS / "mapping.csv"

# Flavor file removed. Upstream art is the meaning we could not redraw well.
REVERT_NAMES = {
    # Premium / star illustrations are Telegram drawings (x2, footprints, Ai).
    # The flavor copies were one black bubble or a black disc.
    "filled_giveaway_premium", "filled_premium_away", "filled_premium_bots",
    "filled_premium_business", "filled_premium_chatlink", "filled_premium_hours",
    "filled_premium_intro", "filled_premium_status2", "filled_gift_premium",
    "menu_feature_premium", "menu_premium_away", "menu_premium_chatbot",
    "menu_premium_chatlink", "menu_premium_clock", "menu_premium_clock_add",
    "menu_premium_effects", "menu_premium_location", "menu_premium_seen",
    "menu_premium_tools", "msg_premium_ads", "msg_premium_avatar",
    "msg_premium_badge", "msg_premium_icons", "msg_premium_limits",
    "msg_premium_lock2", "msg_premium_normal", "msg_premium_reactions",
    "msg_premium_speed", "msg_premium_stickers", "msg_premium_translate",
    "msg_premium_uploads", "msg_premium_voice", "msg_mini_premiumlock",
    "premium_colors", "premium_status", "premium_tags", "premium_wallpaper",
    "premium_ai_editor", "premium_rich_editor", "star_premium", "star_premium_cutout",
    # Android briefcase, not the iOS megaphone.
    "settings_business",
    # Dashed ring on the ephemeral send button. Not the send arrow.
    "send_outline",
    # Stacked community cards, speedometer, voice-chat bars, saved-story ring,
    # cookie, SD card. No MingCute glyph keeps that drawing.
    "community_cards", "msg_speed", "msg_voicechat", "msg_voicechat2",
    "msg_stories_saved", "menu_clear_cookies", "msg_filled_sdcard",
    "filled_requests_24",
}

# resource -> (mingcute name, style). Plain one-glyph replacements.
PLAIN = {
    "msg_gift_premium": ("gift", "line"),
    "search_date_filled": ("calendar", "line"),
    "search_files_filled": ("file", "line"),
    "search_links_filled": ("link", "line"),
    "search_media_filled": ("gallery", "line"),
    "search_music_filled": ("music", "line"),
    "search_users_filled": ("user-3", "line"),
    "search_voice_filled": ("mic", "line"),
    "menu_feature_paid": ("coin", "line"),
    "photo_star": ("star", "line"),
    "msg_clear": ("broom", "line"),
    "msg_gallery_locked2": ("lock", "line"),
    "permission_locked": ("lock", "line"),
    "menu_2sv": ("lock", "line"),
    "menu_2sv_on": ("check-circle", "line"),
    "group_edit": ("edit-2", "line"),
    "msg_background": ("gallery", "line"),
    "input_schedule": ("clock", "line"),
    "menu_edit_price": ("tag", "line"),
    "msg_language": ("globe", "line"),
    "dialog_media_checklist_20": ("list-check", "line"),
    "msg_viewreplies": ("corner-up-left", "line"),
    "msg_shareout": ("share-2", "line"),
    "menu_factcheck": ("check-circle", "line"),
    "outline_fab_story_24": ("camera-2", "line"),
    "profile_video": ("camera-2", "line"),
    "list_check": ("check", "line"),
    "settings_arrow": ("chevron-right", "line"),
}

# resource -> (base mingcute, style, badge)
# badge: slash, plus, minus, check, arrow-down, arrow-up, dot, lines, arcs, dots
BADGES = {
    "msg_unpin": ("pin", "line", "slash"),
    "chats_unpin": ("pin", "line", "slash"),
    "msg_unfave": ("star", "line", "slash"),
    "menu_share_off_24": ("share-2", "line", "slash"),
    "menu_share_on_24": ("share-2", "line", "check"),
    "menu_paid_off": ("coin", "line", "slash"),
    "msg_pollstop": ("chart-bar", "line", "slash"),
    "msg_stealth_locked": ("eye", "line", "slash"),
    "msg_stories_stealth2": ("eye", "line", "slash"),
    "msg_delete_auto": ("delete-2", "line", "clock"),
    "msg_addfolder": ("folder", "line", "plus"),
    "msg_removefolder": ("folder", "line", "minus"),
    "menu_tag_plus": ("tag", "line", "plus"),
    "menu_tag_delete": ("tag", "line", "slash"),
    "menu_tag_edit": ("tag", "line", "pencil"),
    "menu_tag_filter": ("tag", "line", "lines"),
    "menu_tag_rename": ("tag", "line", "letter"),
    "msg_user_remove": ("user-3", "line", "slash"),
    "msg_groups_create": ("group", "line", "plus"),
    "msg_tone_add": ("music", "line", "plus"),
    "msg_pinnedlist": ("pin", "line", "lines"),
    "msg_contact_add": ("user-3", "line", "plus"),
    "msg_addcontact": ("user-3", "line", "plus"),
    "msg_requests": ("user-3", "line", "plus"),
    "msg_online": ("user-3", "line", "dot"),
    "msg_markread": ("message-3", "line", "check"),
    "msg_markunread": ("message-3", "line", "dot"),
    "msg_viewintopic": ("message-3", "line", "arrow-right"),
    "dialog_media_incoming_call_20": ("phone", "line", "arrow-down"),
    "dialog_media_outgoing_call_20": ("phone", "line", "arrow-up"),
    "msg_calls_regular": ("phone", "line", "dots"),
    "msg2_call_earpiece": ("phone", "line", "arcs"),
}

GRAM_MARKS = {
    "menu_gram_24", "mini_gram_72", "mini_gram_72_white", "outline_gram_24",
    "settings_gram_24", "dialog_gram_transfer", "filled_gram_earnings",
}

HAND_DRAWN = {
    "list_check", "list_halfcheck", "input_video", "input_video_pressed",
    "msg_gif", "msg2_gif", "msg_sticker", "msg2_sticker",
    "dialog_media_incoming_video_call_20", "dialog_media_outgoing_video_call_20",
    "msg_topics", "msg_expand", "arrow_newchat", "minithumb_play",
    "msg_menu_stories", "msg_reactions",
}


def glyph_override(name):
    if name in PLAIN:
        return PLAIN[name]
    if name in BADGES:
        base, style, badge = BADGES[name]
        return (f"{base}+{badge}", style)
    if name == "list_halfcheck":
        return ("check-half", "line")
    if name in ("input_video", "input_video_pressed", "profile_video"):
        return ("camera-lens", "line" if name != "input_video_pressed" else "fill")
    if name in ("msg_gif", "msg2_gif"):
        return ("gif-label", "line")
    if name in ("msg_sticker", "msg2_sticker"):
        return ("sticker-face", "line")
    if name in ("dialog_media_incoming_video_call_20",):
        return ("camera-lens+arrow-down", "line")
    if name in ("dialog_media_outgoing_video_call_20",):
        return ("camera-lens+arrow-up", "line")
    if name == "msg_topics":
        return ("topic-list", "line")
    if name == "msg_expand":
        return ("chevron-down", "line")
    if name == "arrow_newchat":
        return ("chevron-right", "line")
    if name == "minithumb_play":
        return ("play-fill", "fill")
    if name == "msg_menu_stories":
        return ("story-play", "line")
    if name == "msg_reactions":
        return ("heart", "line")
    return None


def is_hand_drawn(name):
    return name in HAND_DRAWN or name in BADGES or name in GRAM_MARKS or name in PLAIN


def _svg_paths(icon, style):
    svg = gen.vendored_svg(icon, style)
    if svg is None:
        raise FileNotFoundError(f"{style}/{icon}.svg")
    text = svg.read_text()
    return re.findall(r'\bd="([^"]+)"', text)


def _wrap(paths, transform=None):
    body = "".join(f'<path fill="#FFFFFF" d="{d}"/>' for d in paths)
    if transform:
        return f'<g transform="{transform}">{body}</g>'
    return body


def _line(x1, y1, x2, y2, width):
    dx, dy = x2 - x1, y2 - y1
    length = math.hypot(dx, dy) or 1
    nx, ny = -dy / length * width / 2, dx / length * width / 2
    return (
        f"M{x1+nx:.2f} {y1+ny:.2f} L{x2+nx:.2f} {y2+ny:.2f} "
        f"L{x2-nx:.2f} {y2-ny:.2f} L{x1-nx:.2f} {y1-ny:.2f} Z"
    )


def _badge(kind):
    if kind == "slash":
        return f'<path fill="#FFFFFF" d="{_line(4.2, 19.2, 19.8, 4.6, 2.15)}"/>'
    if kind == "plus":
        return (
            f'<path fill="#FFFFFF" d="{_line(17.2, 14.6, 17.2, 22.2, 1.7)}"/>'
            f'<path fill="#FFFFFF" d="{_line(13.4, 18.4, 21.0, 18.4, 1.7)}"/>'
        )
    if kind == "minus":
        return f'<path fill="#FFFFFF" d="{_line(13.2, 18.6, 21.2, 18.6, 1.8)}"/>'
    if kind == "check":
        return (
            f'<path fill="#FFFFFF" d="{_line(14.2, 18.2, 16.6, 20.6, 1.7)}"/>'
            f'<path fill="#FFFFFF" d="{_line(16.3, 20.6, 21.4, 14.4, 1.7)}"/>'
        )
    if kind == "arrow-down":
        return '<path fill="#FFFFFF" d="M15.6 16.4 H22.6 L19.1 22.5 Z"/>'
    if kind == "arrow-up":
        return '<path fill="#FFFFFF" d="M15.6 7.4 H22.6 L19.1 1.3 Z"/>'
    if kind == "arrow-right":
        return '<path fill="#FFFFFF" d="M13.2 15.2 V21.6 L19.6 18.4 Z"/>'
    if kind == "dot":
        return '<circle cx="18.6" cy="6.2" r="2.15" fill="#FFFFFF"/>'
    if kind == "lines":
        return "".join(
            f'<path fill="#FFFFFF" d="{_line(13.4, y, 21.2, y, 1.35)}"/>'
            for y in (15.2, 18.0, 20.8)
        )
    if kind == "pencil":
        return f'<path fill="#FFFFFF" d="{_line(14.0, 20.6, 20.8, 13.8, 1.7)}"/>'
    if kind == "letter":
        return (
            f'<path fill="#FFFFFF" d="{_line(14.6, 20.4, 17.4, 14.6, 1.35)}"/>'
            f'<path fill="#FFFFFF" d="{_line(17.4, 14.6, 20.4, 20.4, 1.35)}"/>'
            f'<path fill="#FFFFFF" d="{_line(15.6, 18.2, 19.2, 18.2, 1.15)}"/>'
        )
    if kind == "dots":
        return "".join(
            f'<circle cx="{x}" cy="{y}" r="0.85" fill="#FFFFFF"/>'
            for y in (8.2, 11.2, 14.2) for x in (16.6, 19.4)
        )
    if kind == "arcs":
        return (
            '<path fill="none" stroke="#FFFFFF" stroke-width="1.5" stroke-linecap="round" '
            'd="M15.2 8.2c2.4 1.5 2.4 6.1 0 7.6"/>'
            '<path fill="none" stroke="#FFFFFF" stroke-width="1.5" stroke-linecap="round" '
            'd="M17.4 6.2c3.6 2.4 3.6 9.2 0 11.6"/>'
        )
    if kind == "clock":
        return (
            '<circle cx="18.2" cy="17.6" r="3.3" fill="none" stroke="#FFFFFF" stroke-width="1.45"/>'
            '<path fill="none" stroke="#FFFFFF" stroke-width="1.3" stroke-linecap="round" '
            'd="M18.2 15.6 V17.6 L19.8 18.6"/>'
        )
    raise KeyError(kind)


def compose_svg(base, style, badge=None, shrink=False):
    paths = _svg_paths(base, style)
    transform = "translate(1.1 1.1) scale(0.82)" if shrink or badge in {
        "plus", "minus", "check", "arrow-down", "arrow-up", "arrow-right",
        "dot", "lines", "pencil", "letter", "dots", "arcs", "clock",
    } else None
    body = _wrap(paths, transform)
    extra = _badge(badge) if badge else ""
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">'
        + body + extra + "</svg>"
    )


def camera_svg(filled=False, compact=False):
    if compact:
        # Leave the top-right free so a call-direction arrow is not a play button.
        return (
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">'
            '<rect x="1.6" y="6.4" width="13.4" height="11.2" rx="2.4" '
            'fill="none" stroke="#FFFFFF" stroke-width="1.6"/>'
            '<circle cx="8.3" cy="12" r="2.15" fill="none" stroke="#FFFFFF" stroke-width="1.6"/>'
            "</svg>"
        )
    if filled:
        # Squircle with a lens hole. Even-odd fill stays white under tint.
        d = (
            "M5.2 6.4 h13.6 a3.2 3.2 0 0 1 3.2 3.2 v6.4 a3.2 3.2 0 0 1 -3.2 3.2 "
            "h-13.6 a3.2 3.2 0 0 1 -3.2 -3.2 v-6.4 a3.2 3.2 0 0 1 3.2 -3.2 z "
            "M12 9.15 a2.85 2.85 0 1 0 0.01 0 z"
        )
        return (
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">'
            f'<path fill="#FFFFFF" fill-rule="evenodd" d="{d}"/></svg>'
        )
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">'
        '<rect x="3.3" y="6.2" width="17.4" height="12.2" rx="3.2" '
        'fill="none" stroke="#FFFFFF" stroke-width="1.7"/>'
        '<circle cx="12" cy="12.3" r="2.7" fill="none" stroke="#FFFFFF" stroke-width="1.7"/>'
        "</svg>"
    )


def gif_svg():
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">'
        '<rect x="2.4" y="5.6" width="19.2" height="12.8" rx="2.6" '
        'fill="none" stroke="#FFFFFF" stroke-width="1.7"/>'
        '<text x="12" y="15.15" text-anchor="middle" fill="#FFFFFF" '
        'font-family="sans-serif" font-size="6.4" font-weight="700">GIF</text>'
        "</svg>"
    )


def _hole(cx, cy, r):
    return (
        f"M{cx-r:.2f} {cy:.2f} a{r:.2f} {r:.2f} 0 1 0 {2*r:.2f} 0 "
        f"a{r:.2f} {r:.2f} 0 1 0 {-2*r:.2f} 0"
    )


def sticker_svg():
    # Eyes and mouth are holes, otherwise they disappear on a white sticker.
    body = (
        "M5 4.2 h9.2 a2 2 0 0 1 2 2 V14.2 L12.4 19.4 H5 a2 2 0 0 1 -2 -2 V6.2 a2 2 0 0 1 2 -2 z"
    )
    d = " ".join([body, _hole(8.3, 9.4, 0.95), _hole(12.2, 9.4, 0.95), _hole(10.25, 13.15, 1.15)])
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">'
        f'<path fill="#FFFFFF" fill-rule="evenodd" d="{d}"/></svg>'
    )


def topic_svg():
    rows = []
    for y in (6.2, 12.0, 17.8):
        rows.append(f'<circle cx="5.2" cy="{y}" r="1.35" fill="#FFFFFF"/>')
        rows.append(f'<path fill="#FFFFFF" d="{_line(8.6, y, 19.4, y, 1.7)}"/>')
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">' + "".join(rows) + "</svg>"


def chevron_svg(direction):
    if direction == "down":
        d = "M4.8 8.2 L12 15.6 L19.2 8.2 L16.6 8.2 L12 12.6 L7.4 8.2 Z"
    else:
        # Filled so the 6px mdpi settings arrow still reads as a chevron.
        d = "M7.2 4.4 L16.8 12 L7.2 19.6 L7.2 15.2 L11.2 12 L7.2 8.8 Z"
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">'
        f'<path fill="#FFFFFF" d="{d}"/></svg>'
    )


def play_svg():
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">'
        '<path fill="#FFFFFF" d="M8.2 5.4 L19.2 12 L8.2 18.6 Z"/></svg>'
    )


def heart_svg():
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">'
        '<path fill="#FFFFFF" d="M12 19.4C7.1 15.9 4.2 13.4 4.2 9.9 '
        '4.2 7.4 6.1 5.6 8.4 5.6c1.5 0 2.7.8 3.6 2.1C13 6.4 14.2 5.6 15.6 5.6 '
        'c2.3 0 4.2 1.8 4.2 4.3 0 3.5-2.9 6-7.8 9.5z"/></svg>'
    )


def story_svg():
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">'
        '<circle cx="12" cy="12" r="8.2" fill="none" stroke="#FFFFFF" stroke-width="1.7" '
        'stroke-dasharray="3.2 2.1"/>'
        '<path fill="#FFFFFF" d="M10.3 8.4 L16.2 12 L10.3 15.6 Z"/></svg>'
    )


def svg_for(name):
    if name in PLAIN and name not in HAND_DRAWN:
        icon, style = PLAIN[name]
        return compose_svg(icon, style)
    if name in BADGES:
        base, style, badge = BADGES[name]
        return compose_svg(base, style, badge)
    if name == "input_video":
        return camera_svg(False)
    if name == "input_video_pressed":
        return camera_svg(True)
    if name in ("msg_gif", "msg2_gif"):
        return gif_svg()
    if name in ("msg_sticker", "msg2_sticker"):
        return sticker_svg()
    if name == "dialog_media_incoming_video_call_20":
        return camera_svg(compact=True).replace("</svg>", _badge("arrow-down") + "</svg>")
    if name == "dialog_media_outgoing_video_call_20":
        return camera_svg(compact=True).replace("</svg>", _badge("arrow-up") + "</svg>")
    if name == "msg_topics":
        return topic_svg()
    if name == "msg_expand":
        return chevron_svg("down")
    if name == "arrow_newchat":
        return chevron_svg("right")
    if name == "minithumb_play":
        return play_svg()
    if name == "msg_menu_stories":
        return story_svg()
    if name == "msg_reactions":
        return heart_svg()
    if name == "list_check":
        return compose_svg("check", "line")
    if name == "settings_arrow":
        return chevron_svg("right")
    return None


def _pixel_chevron(w, h):
    """A blocky right chevron. Vector strokes vanish in a 6x8 mdpi cell."""
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    thick = 1 if w < 10 else 2
    for y in range(h):
        t = y / (h - 1) if h > 1 else 0.5
        edge = abs(t - 0.5) * 2
        tip = int(round((1 - edge) * (w - 1)))
        back = max(0, tip - thick)
        for x in range(back, tip + 1):
            px[x, y] = (255, 255, 255, 255)
    return im


def _paint_white(im):
    pixels = []
    for r, g, b, a in im.getdata():
        if a == 0:
            pixels.append((0, 0, 0, 0))
        else:
            pixels.append((255, 255, 255, a))
    im = im.copy()
    im.putdata(pixels)
    return im


def render_svg(svg, width, height):
    scale = 4 if min(width, height) < 28 else 2
    png = cairosvg.svg2png(
        bytestring=svg.encode(),
        output_width=width * scale,
        output_height=height * scale,
    )
    im = Image.open(io.BytesIO(png)).convert("RGBA")
    if scale != 1:
        im = im.resize((width, height), Image.Resampling.LANCZOS)
    return _paint_white(im)


def _ink_bbox(im):
    w, h = im.size
    minx, miny, maxx, maxy = w, h, -1, -1
    for y in range(h):
        for x in range(w):
            if im.getpixel((x, y))[3] > 30:
                minx = min(minx, x)
                miny = min(miny, y)
                maxx = max(maxx, x)
                maxy = max(maxy, y)
    if maxx < 0:
        return None
    return minx, miny, maxx, maxy


def _place_like(upstream, glyph):
    """Fit glyph's ink into the same box the upstream bitmap uses."""
    ub = _ink_bbox(upstream)
    gb = _ink_bbox(glyph)
    canvas = Image.new("RGBA", upstream.size, (0, 0, 0, 0))
    if not ub or not gb:
        return canvas
    uw = ub[2] - ub[0] + 1
    uh = ub[3] - ub[1] + 1
    gw = gb[2] - gb[0] + 1
    gh = gb[3] - gb[1] + 1
    cropped = glyph.crop(gb)
    scale = min(uw / gw, uh / gh)
    nw, nh = max(1, int(gw * scale)), max(1, int(gh * scale))
    cropped = cropped.resize((nw, nh), Image.Resampling.LANCZOS)
    x = ub[0] + (uw - nw) // 2
    y = ub[1] + (uh - nh) // 2
    canvas.paste(cropped, (x, y), cropped)
    return _paint_white(canvas)


def _half_arm(glyph):
    """Keep the rising arm of a check (the right-hand stroke)."""
    bbox = _ink_bbox(glyph)
    if not bbox:
        return glyph
    out = glyph.copy()
    cut = bbox[0] + int((bbox[2] - bbox[0]) * 0.46)
    px = out.load()
    for y in range(out.size[1]):
        for x in range(cut):
            px[x, y] = (0, 0, 0, 0)
    return out


def write_checks():
    for name, half in (("list_check", False), ("list_halfcheck", True)):
        for folder in ("drawable-mdpi", "drawable-hdpi", "drawable-xhdpi", "drawable-xxhdpi"):
            src = MAIN / folder / f"{name}.webp"
            if not src.exists():
                continue
            up = Image.open(src).convert("RGBA")
            big = render_svg(compose_svg("check", "line"), max(up.size) * 4, max(up.size) * 4)
            if half:
                big = _half_arm(big)
            placed = _place_like(up, big)
            dest = RES / folder / f"{name}.webp"
            dest.parent.mkdir(parents=True, exist_ok=True)
            placed.save(dest, "WEBP", lossless=True, quality=100, method=4)


def _vector_dp(path):
    text = path.read_text(errors="ignore")
    w = re.search(r'android:width="([0-9.]+)dp"', text)
    h = re.search(r'android:height="([0-9.]+)dp"', text)
    return (w.group(1) if w else "24", h.group(1) if h else "24")


def svg_to_vector(svg, width, height):
    """Turn a 24px SVG into a white VectorDrawable of the given dp size."""
    # Parse with ElementTree via a fake file.
    import xml.etree.ElementTree as ET
    root = ET.fromstring(svg)
    def rect_d(el):
        x = float(el.attrib.get("x", "0"))
        y = float(el.attrib.get("y", "0"))
        w = float(el.attrib.get("width", "0"))
        h = float(el.attrib.get("height", "0"))
        rx = float(el.attrib.get("rx", "0") or 0)
        rx = min(rx, w / 2, h / 2)
        return (
            f"M{x+rx:.2f} {y:.2f} H{x+w-rx:.2f} A{rx:.2f} {rx:.2f} 0 0 1 {x+w:.2f} {y+rx:.2f} "
            f"V{y+h-rx:.2f} A{rx:.2f} {rx:.2f} 0 0 1 {x+w-rx:.2f} {y+h:.2f} H{x+rx:.2f} "
            f"A{rx:.2f} {rx:.2f} 0 0 1 {x:.2f} {y+h-rx:.2f} V{y+rx:.2f} "
            f"A{rx:.2f} {rx:.2f} 0 0 1 {x+rx:.2f} {y:.2f} Z"
        )

    def circle_d(el):
        cx = float(el.attrib.get("cx", "0"))
        cy = float(el.attrib.get("cy", "0"))
        r = float(el.attrib.get("r", "0"))
        return (
            f"M{cx-r:.2f} {cy:.2f} a{r:.2f} {r:.2f} 0 1 0 {2*r:.2f} 0 "
            f"a{r:.2f} {r:.2f} 0 1 0 {-2*r:.2f} 0"
        )

    paths = []
    for el in root.iter():
        tag = el.tag.split("}")[-1]
        if tag == "rect":
            d = rect_d(el)
        elif tag == "circle":
            d = circle_d(el)
        elif tag == "path":
            d = el.attrib.get("d")
        else:
            continue
        if not d:
            continue
        fill = el.attrib.get("fill", "#FFFFFF")
        stroke = el.attrib.get("stroke")
        sw = el.attrib.get("stroke-width")
        even = el.attrib.get("fill-rule") == "evenodd"
        attrs = [f'android:pathData="{d}"']
        if fill and fill != "none":
            attrs.append('android:fillColor="#FFFFFF"')
        else:
            attrs.append('android:fillColor="#00000000"')
        if stroke and stroke != "none":
            attrs.append('android:strokeColor="#FFFFFF"')
            attrs.append(f'android:strokeWidth="{sw or "1.6"}"')
            cap = el.attrib.get("stroke-linecap")
            if cap:
                attrs.append(f'android:strokeLineCap="{cap}"')
            join = el.attrib.get("stroke-linejoin")
            if join:
                attrs.append(f'android:strokeLineJoin="{join}"')
        if even:
            attrs.append('android:fillType="evenOdd"')
        paths.append("    <path " + " ".join(attrs) + " />")
    # Text (GIF) becomes a filled word via a tiny bitmap? Vectors can't hold
    # <text>. Draw the letters as a note in path form below if present.
    if root.find(".//{http://www.w3.org/2000/svg}text") is not None or any(
        el.tag.split("}")[-1] == "text" for el in root.iter()
    ):
        paths.append(
            '    <path android:fillColor="#FFFFFF" android:pathData="'
            "M5.15 15.35 V9.15 H6.55 V12.05 H8.55 V9.15 H9.95 V15.35 H8.55 V13.15 H6.55 V15.35 Z "
            "M11.05 15.35 V9.15 H14.55 V10.25 H12.45 V11.65 H14.25 V12.75 H12.45 V14.25 H14.65 V15.35 Z "
            "M15.55 15.35 V9.15 H19.15 V10.25 H16.95 V11.6 H18.85 V12.7 H16.95 V15.35 Z"
            '" />'
        )
    body = "\n".join([
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{width}dp"',
        f'    android:height="{height}dp"',
        '    android:viewportWidth="24"',
        '    android:viewportHeight="24">',
        *paths,
        "</vector>",
        "",
    ])
    return body


def targets(name):
    """(kind, path, width, height) for each flavor file to write."""
    out = []
    for base in (RES, MAIN):
        for p in base.rglob(name + ".*"):
            folder = p.parent.name
            if "night" in folder:
                continue
            if folder != "drawable" and not folder.startswith("drawable-"):
                continue
            if p.suffix.lower() not in {".webp", ".png", ".xml"}:
                continue
            key = (folder, p.suffix.lower())
            if any(k == key for k, *_ in (( (folder, p.suffix.lower()),) for _ in [0])):
                pass
            if p.suffix.lower() == ".xml" and folder == "drawable":
                w, h = _vector_dp(p)
                out.append(("vector", RES / "drawable" / f"{name}.xml", w, h))
            elif p.suffix.lower() in {".webp", ".png"}:
                im = Image.open(p)
                out.append(("raster", RES / folder / f"{name}.webp", im.size[0], im.size[1]))
        if out:
            break
    # de-dup
    seen = set()
    uniq = []
    for item in out:
        if item[1] in seen:
            continue
        seen.add(item[1])
        uniq.append(item)
    return uniq


def write_svg_resource(name, svg):
    wrote = []
    for kind, dest, w, h in targets(name):
        dest.parent.mkdir(parents=True, exist_ok=True)
        if kind == "vector":
            dest.write_text(svg_to_vector(svg, w, h))
        else:
            if name == "settings_arrow" and min(w, h) <= 12:
                im = _pixel_chevron(w, h)
            else:
                im = render_svg(svg, w, h)
            im.save(dest, "WEBP", lossless=True, quality=100, method=4)
        wrote.append(dest)
    return wrote


def write_gram():
    mark = (RES / "drawable" / "send_plane_26.xml").read_text()
    group = mark[mark.index("<group"):mark.rindex("</group>") + len("</group>")]
    group = group.replace("#FFFFFF", "#FFFFFF")
    wrote = []
    for name in sorted(GRAM_MARKS):
        src = RES / "drawable" / f"{name}.xml"
        if not src.exists():
            src = MAIN / "drawable" / f"{name}.xml"
        w, h = _vector_dp(src)
        body = (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{w}dp"\n'
            f'    android:height="{h}dp"\n'
            '    android:viewportWidth="108"\n'
            '    android:viewportHeight="108">\n'
            f"{group}\n"
            "</vector>\n"
        )
        # mini_gram_72_white is a white mark. The others are white too so a
        # tint can recolor them. None of them keep the baked #111111 disc.
        if name == "mini_gram_72_white":
            body = body.replace("#FFFFFF", "#FFFFFF")
        dest = RES / "drawable" / f"{name}.xml"
        dest.write_text(body)
        if "#111111" in body or "#FF111111" in body:
            raise SystemExit(f"{name} still has a fixed black fill")
        wrote.append(dest)
    return wrote


def delete_overlays(name):
    removed = []
    for p in RES.rglob(name + ".*"):
        folder = p.parent.name
        if folder == "drawable" or folder.startswith("drawable-"):
            if "night" in folder:
                continue
            p.unlink()
            removed.append(p)
    return removed


def update_mapping():
    rows = []
    with MAPPING.open(newline="") as f:
        for row in csv.DictReader(f):
            rows.append(row)
    by_name = {r["telegram_resource"]: r for r in rows}
    for name in REVERT_NAMES:
        by_name.pop(name, None)
    def put(name, icon, style):
        kind = "vector"
        sample = list(RES.rglob(name + ".webp"))
        xml = RES / "drawable" / f"{name}.xml"
        if sample and not (name in GRAM_MARKS):
            kind = "raster"
        if xml.exists() and not sample:
            kind = "vector"
        if sample and xml.exists():
            kind = "raster"
        by_name[name] = {
            "telegram_resource": name,
            "mingcute_name": icon,
            "style": style,
            "kind": kind,
        }
    for name, (icon, style) in PLAIN.items():
        put(name, icon, style)
    for name, (base, style, badge) in BADGES.items():
        put(name, f"{base}+{badge}", style)
    for name in HAND_DRAWN:
        glyph = glyph_override(name)
        if glyph and name not in PLAIN and name not in BADGES:
            put(name, glyph[0], glyph[1])
    for name in GRAM_MARKS:
        put(name, "tellomi-mark", "line")
    ordered = [by_name[k] for k in sorted(by_name)]
    with MAPPING.open("w", newline="") as f:
        w = csv.DictWriter(f, ["telegram_resource", "mingcute_name", "style", "kind"])
        w.writeheader()
        w.writerows(ordered)


def apply():
    removed = []
    for name in sorted(REVERT_NAMES):
        removed.extend(delete_overlays(name))
    write_checks()
    for name in sorted(PLAIN):
        if name in ("list_check", "settings_arrow"):
            continue
        write_svg_resource(name, svg_for(name))
    # Upstream settings_arrow is a thick chevron. A hairline arrow
    # collapses in the 6px mdpi cell.
    write_svg_resource("settings_arrow", chevron_svg("right"))
    for name in sorted(BADGES):
        write_svg_resource(name, svg_for(name))
    for name in sorted(HAND_DRAWN):
        if name in ("list_check", "list_halfcheck"):
            continue
        write_svg_resource(name, svg_for(name))
    write_gram()
    update_mapping()
    print(f"removed {len(removed)} reverted overlays")
    print(f"plain {len(PLAIN)} badges {len(BADGES)} hand {len(HAND_DRAWN)} gram {len(GRAM_MARKS)}")


if __name__ == "__main__":
    apply()
