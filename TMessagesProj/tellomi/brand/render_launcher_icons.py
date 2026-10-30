#!/usr/bin/env python3
"""Build Tellomi launcher and notification icons from logo.svg.

logo.svg is the official mark: currentColor, fill-rule evenodd, traced from
logo-source-light.png by scripts/brand/trace-logo.py. Do not hand-edit path
data. Light ink is #111113 on #FFFFFF. Dark ink is #FFFFFF on #111113.

Regenerate (requires rsvg-convert, Pillow, cwebp):

    python3 TMessagesProj/tellomi/brand/render_launcher_icons.py
"""

from __future__ import annotations

import math
import re
import subprocess
import tempfile
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
LOGO = HERE / "logo.svg"
RES = HERE.parent / "res"

# Official usage (Telegram-iOS tellomi-brand-assets, docs/brand/README.md):
# in-app ink is #111113 on light and #FFFFFF on dark. The app icon background
# is #17171A, so the launcher mark is white on that ground in both themes.
INK_LIGHT = (0xFF, 0xFF, 0xFF)
INK_DARK = (0xFF, 0xFF, 0xFF)
BG_LIGHT = (0x17, 0x17, 0x1A)
BG_DARK = (0x17, 0x17, 0x1A)
QR_FRACTION = 0.70

# Legacy mipmaps are the whole icon the user sees. The mark's painted box is
# 77% of that frame (inside the 75–80% acceptance band) so the tail and the
# @ stay off the edges. Adaptive layers are 108dp; the center 72dp stays
# visible after the launcher mask, so the same optical size is 72/108 of the
# legacy fraction.
LEGACY_FRACTION = 0.77
ADAPTIVE_FRACTION = LEGACY_FRACTION * (72 / 108)
NOTIFICATION_FRACTION = 0.90
# Sister Tellomi Android legacy icon corner, 38px on a 192px bitmap.
CORNER_FRACTION = 38 / 192

LEGACY_DP = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
NOTIFICATION_DP = {"mdpi": 24, "hdpi": 36, "xhdpi": 48, "xxhdpi": 72}


def hex_rgb(rgb: tuple[int, int, int]) -> str:
    return "#{:02X}{:02X}{:02X}".format(*rgb)


def load_paths() -> list[str]:
    text = LOGO.read_text(encoding="utf-8")
    if "currentColor" not in text:
        raise SystemExit("logo.svg must keep fill=currentColor")
    paths = re.findall(r'<path\s+d="([^"]+)"', text)
    if len(paths) != 2:
        raise SystemExit(f"expected 2 paths in logo.svg, found {len(paths)}")
    return paths


def measure_bbox(paths: list[str]) -> tuple[float, float, float, float]:
    svg = svg_document(paths, 1024, "#FFFFFF", None, 1, 0, 0, rounded=False, circular=False)
    with tempfile.TemporaryDirectory() as tmp:
        png = Path(tmp) / "mark.png"
        render_svg(svg, png, 1024)
        image = Image.open(png).convert("RGBA")
    pixels = image.load()
    width, height = image.size
    minx, miny, maxx, maxy = width, height, -1, -1
    for y in range(height):
        for x in range(width):
            if pixels[x, y][3] > 0:
                minx = min(minx, x)
                miny = min(miny, y)
                maxx = max(maxx, x)
                maxy = max(maxy, y)
    if maxx < 0:
        raise SystemExit("logo.svg rendered empty")
    return float(minx), float(miny), float(maxx), float(maxy)


def placement(bbox: tuple[float, float, float, float], canvas: float, fraction: float) -> tuple[float, float, float]:
    minx, miny, maxx, maxy = bbox
    painted = max(maxx - minx + 1, maxy - miny + 1)
    scale = (fraction * canvas) / painted
    center_x = (minx + maxx) / 2
    center_y = (miny + maxy) / 2
    translate_x = canvas / 2 - center_x * scale
    translate_y = canvas / 2 - center_y * scale
    return scale, translate_x, translate_y


def svg_document(
    paths: list[str],
    size: int,
    fill: str,
    background: str | None,
    scale: float,
    translate_x: float,
    translate_y: float,
    rounded: bool,
    circular: bool,
) -> str:
    body = [
        f'<g fill="{fill}" fill-rule="evenodd" transform="translate({translate_x:.6f} {translate_y:.6f}) scale({scale:.8f})">'
    ]
    for path in paths:
        body.append(f'<path d="{path}"/>')
    body.append("</g>")
    inner = "\n".join(body)
    if background:
        radius = CORNER_FRACTION * size if rounded else 0
        shape = f'<rect width="{size}" height="{size}" rx="{radius:.4f}" fill="{background}"/>'
        inner = shape + "\n" + inner
    if circular:
        inner = (
            f'<defs><clipPath id="tellomi-round"><circle cx="{size / 2}" cy="{size / 2}" r="{size / 2}"/></clipPath></defs>'
            f'<g clip-path="url(#tellomi-round)">{inner}</g>'
        )
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}">'
        f"{inner}</svg>"
    )


def render_svg(svg: str, png: Path, size: int) -> None:
    png.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", suffix=".svg", encoding="utf-8", delete=False) as handle:
        handle.write(svg)
        src = handle.name
    subprocess.run(
        ["rsvg-convert", "-w", str(size), "-h", str(size), "-o", str(png), src],
        check=True,
    )
    Path(src).unlink()


def write_png(paths: list[str], bbox, dest: Path, size: int, fill: str, background: str | None, fraction: float, rounded: bool, circular: bool) -> None:
    scale, tx, ty = placement(bbox, size, fraction)
    svg = svg_document(paths, size, fill, background, scale, tx, ty, rounded, circular)
    render_svg(svg, dest, size)


def write_webp(png: Path, webp: Path) -> None:
    webp.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["cwebp", "-lossless", "-q", "100", str(png), "-o", str(webp)], check=True, capture_output=True)


def vector_xml(paths: list[str], bbox, fill: str, canvas: float, fraction: float, comment: str) -> str:
    scale, tx, ty = placement(bbox, canvas, fraction)
    path_xml = []
    for path in paths:
        path_xml.append(
            "        <path\n"
            f'            android:fillColor="{fill}"\n'
            '            android:fillType="evenOdd"\n'
            f'            android:pathData="{path}" />'
        )
    paths_joined = "\n".join(path_xml)
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{canvas:.0f}dp"
    android:height="{canvas:.0f}dp"
    android:viewportWidth="{canvas:.0f}"
    android:viewportHeight="{canvas:.0f}">
    <group
        android:translateX="{tx:.6f}"
        android:translateY="{ty:.6f}"
        android:scaleX="{scale:.8f}"
        android:scaleY="{scale:.8f}">
{paths_joined}
    </group>
</vector>
"""


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def colors_xml(ink: str, background: str) -> str:
    return f"""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="tellomi_launcher_ink">{ink}</color>
    <color name="tellomi_launcher_background">{background}</color>
</resources>
"""


def adaptive_xml() -> str:
    return """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/tellomi_launcher_background" />
    <foreground android:drawable="@drawable/tellomi_launcher_foreground" />
    <monochrome android:drawable="@drawable/tellomi_launcher_monochrome" />
</adaptive-icon>
"""


def write_qr(paths: list[str], bbox) -> None:
    """Badge and center mark. Center by the painted box, not the viewBox."""
    scale, tx, ty = placement(bbox, 150, QR_FRACTION)
    mark = [
        f'<g fill="#FFFFFF" fill-rule="evenodd" transform="translate({tx:.6f} {ty:.6f}) scale({scale:.8f})">'
    ]
    for path in paths:
        mark.append(f'<path d="{path}"/>')
    mark.append("</g>")
    svg = (
        '<svg xmlns="http://www.w3.org/2000/svg" width="150" height="150" viewBox="0 0 150 150">'
        '<circle cx="75" cy="75" r="75" fill="#17171A"/>'
        + "\n".join(mark)
        + "</svg>\n"
    )
    write_text(RES / "raw/qr_logo.svg", svg)
    comment = (
        "Tellomi mark from the official logo.svg, centered on its painted bounds. "
        "Regenerate with render_launcher_icons.py."
    )
    write_text(
        RES / "drawable/tellomi_qr_badge.xml",
        vector_xml(paths, bbox, "#FFFFFF", 150, QR_FRACTION, comment).replace(
            '    <group',
            '    <path\n        android:fillColor="#17171A"\n        android:pathData="M75,0a75,75 0,1 0,0 150a75,75 0,1 0,0 -150z"/>\n    <group',
            1,
        ),
    )
    write_text(
        RES / "drawable/tellomi_qr_mark.xml",
        vector_xml(paths, bbox, "#FFFFFF", 108, QR_FRACTION, comment),
    )


def main() -> None:
    paths = load_paths()
    bbox = measure_bbox(paths)
    print("painted bbox", tuple(round(v, 2) for v in bbox))

    comment = (
        "Tellomi mark from TMessagesProj/tellomi/brand/logo.svg. "
        "Paths come from scripts/brand/trace-logo.py. Do not hand-edit path data; "
        "regenerate with render_launcher_icons.py."
    )
    write_text(
        RES / "drawable/tellomi_launcher_foreground.xml",
        vector_xml(paths, bbox, "@color/tellomi_launcher_ink", 108, ADAPTIVE_FRACTION, comment),
    )
    write_text(
        RES / "drawable/tellomi_launcher_monochrome.xml",
        vector_xml(paths, bbox, "#FFFFFF", 108, ADAPTIVE_FRACTION, comment + " Monochrome is an alpha mask for Android 13 themed icons."),
    )
    write_text(RES / "values/tellomi_launcher_colors.xml", colors_xml(hex_rgb(INK_LIGHT), hex_rgb(BG_LIGHT)))
    write_text(RES / "values-night/tellomi_launcher_colors.xml", colors_xml(hex_rgb(INK_DARK), hex_rgb(BG_DARK)))
    write_text(RES / "mipmap-anydpi-v26/ic_launcher.xml", adaptive_xml())
    write_text(RES / "mipmap-anydpi-v26/ic_launcher_round.xml", adaptive_xml())

    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        for density, size in LEGACY_DP.items():
            for night, fill, background in (
                (False, hex_rgb(INK_LIGHT), hex_rgb(BG_LIGHT)),
                (True, hex_rgb(INK_DARK), hex_rgb(BG_DARK)),
            ):
                folder = f"mipmap-night-{density}" if night else f"mipmap-{density}"
                write_png(paths, bbox, RES / folder / "ic_launcher.png", size, fill, background, LEGACY_FRACTION, True, False)
                write_png(paths, bbox, RES / folder / "ic_launcher_round.png", size, fill, background, LEGACY_FRACTION, False, True)
        for density, size in NOTIFICATION_DP.items():
            png = tmp_path / f"notification-{density}.png"
            write_png(paths, bbox, png, size, "#FFFFFF", None, NOTIFICATION_FRACTION, False, False)
            write_webp(png, RES / f"drawable-{density}/notification.webp")

    write_qr(paths, bbox)
    write_default_previews(paths, bbox)
    write_variants(paths, bbox)
    print("wrote", RES)


def write_default_previews(paths, bbox) -> None:
    """After still of the default icon. default-before*.png is the previous art and is not rewritten."""
    preview = HERE / "preview"
    preview.mkdir(parents=True, exist_ok=True)
    after = preview / "default-after.png"
    write_png(paths, bbox, after, 512, hex_rgb(INK_LIGHT), hex_rgb(BG_LIGHT), LEGACY_FRACTION, True, False)
    before = preview / "default-before-512.png"
    if before.exists():
        left = Image.open(before).convert("RGBA")
        right = Image.open(after).convert("RGBA")
        sheet = Image.new("RGBA", (512 * 2 + 32, 512), (255, 255, 255, 255))
        sheet.paste(left, (0, 0))
        sheet.paste(right, (512 + 32, 0))
        sheet.save(preview / "default-compare.png")


# Alternate icons. The painted subject stays inside 75–80% of the icon frame.
# Filled uses the top of that band. The premium star's points are the outer
# subject at 80%; the bubble sits inside the star so the points stay visible.
FILLED_FRACTION = 0.80
PREMIUM_BUBBLE_FRACTION = 0.62
PREMIUM_STAR_FRACTION = 0.80
CLASSIC_DISC_FRACTION = 0.90
# Lightest stop stays dark enough that a white mark keeps about 3:1 contrast.
WATER_STOPS = ("#115E59", "#0F766E", "#0891B2")
SUNSET_STOPS = ("#EA580C", "#E11D48", "#7C3AED")
# Deep jewel ground. The star itself is gold, so the ground does not fade to pale gold.
PREMIUM_STOPS = ("#1E1B4B", "#5B21B6", "#6D28D9")
PREMIUM_STAR = "#FBBF24"
FILLED_BLUE = "#1D4ED8"
CLASSIC_OUTER = "#CBD5E1"
CLASSIC_DISC = "#FFFFFF"
CLASSIC_BLACK_OUTER = "#334155"
STARRY_BG = "#0B1220"
# Edge stars sit in the margin outside the 77% mark. (x, y, radius) are fractions of the frame.
STARRY_STARS = (
    (0.50, 0.055, 0.030, "#FFFFFF"),
    (0.26, 0.058, 0.016, "#7DD3FC"),
    (0.74, 0.052, 0.018, "#FFFFFF"),
    (0.055, 0.36, 0.022, "#FFFFFF"),
    (0.058, 0.64, 0.015, "#7DD3FC"),
    (0.946, 0.32, 0.024, "#FFFFFF"),
    (0.942, 0.58, 0.016, "#E0F2FE"),
    (0.948, 0.78, 0.013, "#FFFFFF"),
    (0.30, 0.948, 0.016, "#7DD3FC"),
    (0.52, 0.942, 0.026, "#FFFFFF"),
    (0.76, 0.950, 0.014, "#FFFFFF"),
    (0.16, 0.075, 0.012, "#FFFFFF"),
    (0.86, 0.10, 0.014, "#7DD3FC"),
)


def outer_subpath(path: str) -> str:
    parts = re.findall(r"[^Zz]+[Zz]", path)
    if len(parts) < 2:
        raise SystemExit("bubble path needs an outer subpath and a hole")
    return parts[0].strip()


def star_path(cx: float, cy: float, outer_r: float, inner_r: float, points: int = 4) -> str:
    cmds = []
    for i in range(points * 2):
        angle = math.radians(-90 + i * (180 / points))
        radius = outer_r if i % 2 == 0 else inner_r
        x = cx + radius * math.cos(angle)
        y = cy + radius * math.sin(angle)
        cmds.append(("M" if i == 0 else "L") + f"{x:.3f},{y:.3f}")
    cmds.append("Z")
    return " ".join(cmds)


def mark_group(paths, bbox, canvas: float, fraction: float, fill: str) -> str:
    scale, tx, ty = placement(bbox, canvas, fraction)
    body = [
        f'<g fill="{fill}" fill-rule="evenodd" transform="translate({tx:.6f} {ty:.6f}) scale({scale:.8f})">'
    ]
    for path in paths:
        body.append(f'<path d="{path}"/>')
    body.append("</g>")
    return "\n".join(body)


def filled_group(outer: str, at: str, bbox, canvas: float, fraction: float, bubble: str, at_color: str) -> str:
    scale, tx, ty = placement(bbox, canvas, fraction)
    return (
        f'<g transform="translate({tx:.6f} {ty:.6f}) scale({scale:.8f})">'
        f'<path fill="{bubble}" d="{outer}"/>'
        f'<path fill="{at_color}" fill-rule="evenodd" d="{at}"/>'
        "</g>"
    )


def gradient_def(stops: tuple[str, str, str], gid: str = "g") -> str:
    a, b, c = stops
    return (
        f'<linearGradient id="{gid}" x1="0" y1="1" x2="1" y2="0">'
        f'<stop offset="0" stop-color="{a}"/>'
        f'<stop offset="0.48" stop-color="{b}"/>'
        f'<stop offset="1" stop-color="{c}"/>'
        "</linearGradient>"
    )


def plate(size: int, fill: str, rounded: bool, circular: bool) -> str:
    if circular:
        return f'<circle cx="{size / 2}" cy="{size / 2}" r="{size / 2}" fill="{fill}"/>'
    radius = CORNER_FRACTION * size if rounded else 0
    return f'<rect width="{size}" height="{size}" rx="{radius:.4f}" fill="{fill}"/>'


def stars_svg(size: float, visible: float) -> str:
    """Stars in the margin. visible is the frame the fractions refer to; size is the canvas."""
    origin = (size - visible) / 2
    parts = []
    for x, y, radius, color in STARRY_STARS:
        cx = origin + x * visible
        cy = origin + y * visible
        rr = radius * visible
        parts.append(f'<path fill="{color}" d="{star_path(cx, cy, rr, rr * 0.38)}"/>')
    return "\n".join(parts)


def svg_wrap(size: int, body: str, defs: str = "", rounded: bool = False, circular: bool = False) -> str:
    clip = ""
    if circular:
        clip = f'<clipPath id="tellomi-frame"><circle cx="{size / 2}" cy="{size / 2}" r="{size / 2}"/></clipPath>'
        body = f'<g clip-path="url(#tellomi-frame)">{body}</g>'
    elif rounded:
        radius = CORNER_FRACTION * size
        clip = f'<clipPath id="tellomi-frame"><rect width="{size}" height="{size}" rx="{radius:.4f}"/></clipPath>'
        body = f'<g clip-path="url(#tellomi-frame)">{body}</g>'
    extra = f"<defs>{defs}{clip}</defs>" if (defs or clip) else ""
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}">'
        f"{extra}{body}</svg>"
    )


def shape_gradient(stops: tuple[str, str, str]) -> str:
    a, b, c = stops
    return f'''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient
        android:type="linear"
        android:angle="45"
        android:startColor="{a}"
        android:centerColor="{b}"
        android:endColor="{c}" />
</shape>
'''


def shape_solid(color: str) -> str:
    return f'''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="{color}" />
</shape>
'''


def disc_vector(outer: str, disc: str) -> str:
    # 90% of the 72dp visible icon, centered on the 108dp adaptive layer.
    radius = CLASSIC_DISC_FRACTION * 72 / 2
    top = 54 - radius
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Round base. Disc is {CLASSIC_DISC_FRACTION:.0%} of the visible icon. Generated by render_launcher_icons.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="{outer}"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:fillColor="{disc}"
        android:pathData="M54,{top:.3f}a{radius:.3f},{radius:.3f} 0,1 0,0 {radius * 2:.3f}a{radius:.3f},{radius:.3f} 0,1 0,0 -{radius * 2:.3f}z" />
</vector>
'''


def starry_vector() -> str:
    body = ['    <path android:fillColor="%s" android:pathData="M0,0h108v108h-108z" />' % STARRY_BG]
    origin = (108 - 72) / 2
    for x, y, radius, color in STARRY_STARS:
        cx = origin + x * 72
        cy = origin + y * 72
        rr = radius * 72
        body.append(
            f'    <path android:fillColor="{color}" android:pathData="{star_path(cx, cy, rr, rr * 0.38)}" />'
        )
    inner = "\n".join(body)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Star field in the margin around the mark. Generated by render_launcher_icons.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{inner}
</vector>
'''


def filled_vector(outer: str, at: str, bbox, bubble: str, at_color: str, comment: str) -> str:
    scale, tx, ty = placement(bbox, 108, FILLED_FRACTION * (72 / 108))
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:translateX="{tx:.6f}"
        android:translateY="{ty:.6f}"
        android:scaleX="{scale:.8f}"
        android:scaleY="{scale:.8f}">
        <path
            android:fillColor="{bubble}"
            android:pathData="{outer}" />
        <path
            android:fillColor="{at_color}"
            android:fillType="evenOdd"
            android:pathData="{at}" />
    </group>
</vector>
'''


def premium_vector(paths, bbox) -> str:
    outer_r = PREMIUM_STAR_FRACTION * 72 / 2
    inner_r = outer_r * 0.38
    star = star_path(54, 54, outer_r, inner_r)
    scale, tx, ty = placement(bbox, 108, PREMIUM_BUBBLE_FRACTION * (72 / 108))
    path_xml = []
    for path in paths:
        path_xml.append(
            "        <path\n"
            '            android:fillColor="#1E1B4B"\n'
            '            android:fillType="evenOdd"\n'
            f'            android:pathData="{path}" />'
        )
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- White premium star with the Tellomi bubble and @ in navy on top. Star points are 80% of the visible icon. Generated by render_launcher_icons.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="{PREMIUM_STAR}"
        android:pathData="{star}" />
    <group
        android:translateX="{tx:.6f}"
        android:translateY="{ty:.6f}"
        android:scaleX="{scale:.8f}"
        android:scaleY="{scale:.8f}">
{chr(10).join(path_xml)}
    </group>
</vector>
'''


def adaptive_named(background: str, foreground: str) -> str:
    return f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="{background}" />
    <foreground android:drawable="{foreground}" />
    <monochrome android:drawable="@drawable/tellomi_launcher_monochrome" />
</adaptive-icon>
'''


def write_legacy(name: str, paint) -> None:
    for density, size in LEGACY_DP.items():
        folder = RES / f"mipmap-{density}"
        body, defs = paint(size, True, False)
        render_svg(svg_wrap(size, body, defs, rounded=True), folder / f"{name}.png", size)
        body, defs = paint(size, False, True)
        render_svg(svg_wrap(size, body, defs, circular=True), folder / f"{name}_round.png", size)


def write_variants(paths, bbox) -> None:
    outer = outer_subpath(paths[0])
    at = paths[1]
    filled_bbox = measure_bbox([outer])
    mark_comment = "Tellomi mark. Generated by render_launcher_icons.py."
    write_text(
        RES / "drawable/tellomi_fg_mark_dark.xml",
        vector_xml(paths, bbox, "#17171A", 108, ADAPTIVE_FRACTION, mark_comment + " Dark ink for the light classic disc."),
    )
    write_text(
        RES / "drawable/tellomi_fg_filled.xml",
        filled_vector(outer, at, filled_bbox, "#FFFFFF", FILLED_BLUE, "Filled bubble at 80% of the visible icon. The @ is punched in the background color."),
    )
    write_text(
        RES / "drawable/tellomi_fg_filled_black.xml",
        filled_vector(outer, at, filled_bbox, "#FFFFFF", "#17171A", "Filled bubble at 80% of the visible icon on black. The @ is punched in the background color."),
    )
    write_text(RES / "drawable/tellomi_fg_premium.xml", premium_vector(paths, bbox))
    write_text(RES / "drawable/tellomi_bg_water.xml", shape_gradient(WATER_STOPS))
    write_text(RES / "drawable/tellomi_bg_sunset.xml", shape_gradient(SUNSET_STOPS))
    write_text(RES / "drawable/tellomi_bg_premium.xml", shape_gradient(PREMIUM_STOPS))
    write_text(RES / "drawable/tellomi_bg_filled.xml", shape_solid(FILLED_BLUE))
    write_text(RES / "drawable/tellomi_bg_filled_black.xml", shape_solid("#17171A"))
    write_text(RES / "drawable/tellomi_bg_classic.xml", disc_vector(CLASSIC_OUTER, CLASSIC_DISC))
    write_text(RES / "drawable/tellomi_bg_classic_black.xml", disc_vector(CLASSIC_BLACK_OUTER, "#17171A"))
    write_text(RES / "drawable/tellomi_bg_starry.xml", starry_vector())

    def gradient_paint(stops):
        def paint(size, rounded, circular):
            body = plate(size, "url(#g)", rounded, circular) + mark_group(paths, bbox, size, LEGACY_FRACTION, "#FFFFFF")
            return body, gradient_def(stops)
        return paint

    def solid_mark(color, fraction, filled=False, ink="#FFFFFF"):
        def paint(size, rounded, circular):
            if filled:
                glyph = filled_group(outer, at, filled_bbox, size, fraction, "#FFFFFF", color)
            else:
                glyph = mark_group(paths, bbox, size, fraction, ink)
            return plate(size, color, rounded, circular) + glyph, ""
        return paint

    def classic_paint(outer_color, disc_color, ink):
        def paint(size, rounded, circular):
            radius = CLASSIC_DISC_FRACTION * size / 2
            disc = f'<circle cx="{size / 2}" cy="{size / 2}" r="{radius:.3f}" fill="{disc_color}"/>'
            body = plate(size, outer_color, rounded, circular) + disc + mark_group(paths, bbox, size, LEGACY_FRACTION, ink)
            return body, ""
        return paint

    def starry_paint(size, rounded, circular):
        body = plate(size, STARRY_BG, rounded, circular) + stars_svg(size, size) + mark_group(paths, bbox, size, LEGACY_FRACTION, "#FFFFFF")
        return body, ""

    def premium_paint(size, rounded, circular):
        outer_r = PREMIUM_STAR_FRACTION * size / 2
        star = f'<path fill="{PREMIUM_STAR}" d="{star_path(size / 2, size / 2, outer_r, outer_r * 0.38)}"/>'
        bubble = mark_group(paths, bbox, size, PREMIUM_BUBBLE_FRACTION, "#1E1B4B")
        return plate(size, "url(#g)", rounded, circular) + star + bubble, gradient_def(PREMIUM_STOPS)

    jobs = (
        ("icon_4_launcher", "@drawable/tellomi_bg_water", "@drawable/tellomi_launcher_foreground", gradient_paint(WATER_STOPS)),
        ("icon_6_launcher", "@drawable/tellomi_bg_sunset", "@drawable/tellomi_launcher_foreground", gradient_paint(SUNSET_STOPS)),
        ("tellomi_classic_launcher", "@drawable/tellomi_bg_classic", "@drawable/tellomi_fg_mark_dark", classic_paint(CLASSIC_OUTER, CLASSIC_DISC, "#17171A")),
        ("tellomi_classic_black_launcher", "@drawable/tellomi_bg_classic_black", "@drawable/tellomi_launcher_foreground", classic_paint(CLASSIC_BLACK_OUTER, "#17171A", "#FFFFFF")),
        ("icon_5_launcher", "@drawable/tellomi_bg_filled", "@drawable/tellomi_fg_filled", solid_mark(FILLED_BLUE, FILLED_FRACTION, filled=True)),
        ("tellomi_filled_black_launcher", "@drawable/tellomi_bg_filled_black", "@drawable/tellomi_fg_filled_black", solid_mark("#17171A", FILLED_FRACTION, filled=True)),
        ("icon_2_launcher", "@drawable/tellomi_bg_starry", "@drawable/tellomi_launcher_foreground", starry_paint),
        ("icon_3_launcher", "@drawable/tellomi_bg_premium", "@drawable/tellomi_fg_premium", premium_paint),
    )
    for name, background, foreground, paint in jobs:
        xml = adaptive_named(background, foreground)
        write_text(RES / "mipmap-anydpi-v26" / f"{name}.xml", xml)
        write_text(RES / "mipmap-anydpi-v26" / f"{name}_round.xml", xml)
        write_legacy(name, paint)

    write_variant_sheet()


def write_variant_sheet() -> None:
    from PIL import ImageDraw, ImageFont

    names = (
        "ic_launcher",
        "icon_4_launcher",
        "icon_6_launcher",
        "tellomi_classic_launcher",
        "tellomi_classic_black_launcher",
        "icon_5_launcher",
        "tellomi_filled_black_launcher",
        "icon_2_launcher",
        "icon_3_launcher",
    )
    labels = ("Default", "Water", "Sunset", "Classic", "Classic black", "Filled", "Filled black", "Starry", "Premium")
    tile = 192
    pad = 16
    label_h = 28
    cols = 3
    rows = 3
    sheet = Image.new("RGBA", (cols * (tile + pad) + pad, rows * (tile + label_h + pad) + pad), (244, 244, 245, 255))
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 16)
    except OSError:
        font = ImageFont.load_default()
    src = RES / "mipmap-xxxhdpi"
    for i, (name, label) in enumerate(zip(names, labels)):
        col, row = i % cols, i // cols
        x = pad + col * (tile + pad)
        y = pad + row * (tile + label_h + pad)
        icon = Image.open(src / f"{name}.png").convert("RGBA")
        sheet.paste(icon, (x, y), icon)
        draw.text((x, y + tile + 4), label, fill=(23, 23, 26, 255), font=font)
    out = HERE / "preview" / "variants.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    print("variant sheet", out)


if __name__ == "__main__":
    main()
