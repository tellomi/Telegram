#!/usr/bin/env python3
"""Build Tellomi launcher and notification icons from logo.svg.

logo.svg is the official mark: currentColor, fill-rule evenodd, traced from
logo-source-light.png by scripts/brand/trace-logo.py. Do not hand-edit path
data. Light ink is #111113 on #FFFFFF. Dark ink is #FFFFFF on #111113.

Regenerate (requires rsvg-convert, Pillow, cwebp):

    python3 TMessagesProj/tellomi/brand/render_launcher_icons.py
"""

from __future__ import annotations

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

# Legacy mipmaps are the whole icon. Adaptive layers are 108dp, of which the
# center 72dp stays visible after the launcher mask, so the same optical size
# is 72/108 of the fraction used on the legacy bitmap. Measured against the
# Tellomi mark on the sister Android icon (about 68.75% of the visible icon).
LEGACY_FRACTION = 132 / 192
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
    print("wrote", RES)


if __name__ == "__main__":
    main()
