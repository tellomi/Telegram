#!/usr/bin/env python3
"""Build the bundled Tellomi wallpaper patterns.

Doodles are Tabler Icons (MIT) and Lucide (ISC), drawn as a seamless 1024 tile.
Dots are Pattern Monster circles-2 (MIT). Waves are Hero Patterns topography
(CC BY 4.0, Steve Schoger). Nothing here is a WhatsApp or Telegram asset.
"""

import io
import os
import random
import urllib.request

import cairosvg
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "assets", "wallpapers")
LICENSES = os.path.join(OUT, "licenses")
SIZE = 1024

TABLER = [
    "message-circle", "coffee", "music", "star", "plant", "plane", "heart",
    "camera", "sun", "moon", "book", "gift", "bell", "cloud", "phone",
    "pencil", "map-pin", "headphones", "balloon", "flower", "cat", "pizza",
    "apple", "ball-football", "device-gamepad-2", "palette", "brush", "clock",
    "compass", "anchor", "umbrella", "snowflake", "feather", "fish", "bug",
    "cactus", "cherry", "bulb", "key", "bike", "leaf", "send", "mug", "notes",
    "confetti", "paw", "ice-cream", "carrot", "mushroom", "crown", "diamond",
    "bolt", "sailboat", "mountain", "glasses", "cookie", "lemon", "bone",
]
LUCIDE = [
    "message-circle", "coffee", "music", "star", "sprout", "plane", "heart",
    "camera", "sun", "moon", "book-open", "gift", "bell", "cloud", "phone",
    "pencil", "map-pin", "headphones", "flower-2", "cat", "pizza", "apple",
    "palette", "clock", "compass", "umbrella", "snowflake", "feather", "fish",
    "bug", "cactus", "lightbulb", "key-round", "bike", "leaf", "send",
]

DOTS_SVG = """<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 40 40">
<path fill="#000" d="M40 45a5 5 0 110-10 5 5 0 010 10zM0 45a5 5 0 110-10 5 5 0 010 10zM0 5A5 5 0 110-5 5 5 0 010 5zm40 0a5 5 0 110-10 5 5 0 010 10z"/>
<path fill="#000" d="M20 25a5 5 0 110-10 5 5 0 010 10z"/>
</svg>"""


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "tellomi-wallpaper-build"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read()


def render_svg(svg_bytes, px):
    if isinstance(svg_bytes, str):
        svg_bytes = svg_bytes.encode("utf-8")
    text = svg_bytes.decode("utf-8")
    text = text.replace("currentColor", "#000000")
    png = cairosvg.svg2png(bytestring=text.encode("utf-8"), output_width=px, output_height=px)
    return Image.open(io.BytesIO(png)).convert("RGBA")


def load_icons(source, names, url_for):
    icons = []
    missing = []
    for name in names:
        try:
            raw = fetch(url_for(name))
        except Exception:
            missing.append(name)
            continue
        icons.append((name, render_svg(raw, 96)))
    print(source, "kept", len(icons), "missing", missing)
    return icons


def doodle(tabler_icons, lucide_icons):
    rng = random.Random(20261104)
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    tabler_pick = tabler_icons[:]
    lucide_pick = lucide_icons[:]
    rng.shuffle(tabler_pick)
    rng.shuffle(lucide_pick)
    deck = [("tabler", icon) for icon in tabler_pick[:44]] + [("lucide", icon) for icon in lucide_pick[:20]]
    rng.shuffle(deck)
    cols = 8
    cell = SIZE // cols
    used = {"tabler": set(), "lucide": set()}
    cursor = 0
    for row in range(cols):
        for col in range(cols):
            source, (name, icon) = deck[cursor]
            cursor += 1
            used[source].add(name)
            angle = rng.choice([-28, -18, -8, 6, 14, 24, 36])
            turned = icon.rotate(angle, expand=True, resample=Image.BICUBIC)
            scale = rng.uniform(0.62, 0.86)
            side = max(24, int(turned.width * scale))
            turned = turned.resize((side, side), Image.LANCZOS)
            cx = col * cell + cell // 2 + rng.randint(-6, 6)
            cy = row * cell + cell // 2 + rng.randint(-6, 6)
            paste_wrapped(canvas, turned, cx - side // 2, cy - side // 2)
    if len(used["tabler"]) < 30 or len(used["lucide"]) < 8:
        raise SystemExit("doodle did not use enough of both icon sets: %s" % {k: len(v) for k, v in used.items()})
    return canvas, used


def paste_wrapped(canvas, icon, x, y):
    for dx in (0, -SIZE, SIZE):
        for dy in (0, -SIZE, SIZE):
            canvas.alpha_composite(icon, (x + dx, y + dy))


def tile_svg(svg, tile_px):
    tile = render_svg(svg, tile_px)
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for y in range(0, SIZE, tile_px):
        for x in range(0, SIZE, tile_px):
            canvas.alpha_composite(tile, (x, y))
    return canvas


def save_png(image, name):
    path = os.path.join(OUT, name)
    image.save(path, "PNG", optimize=True)
    print(name, os.path.getsize(path))


def write_licenses():
    os.makedirs(LICENSES, exist_ok=True)
    files = {
        "tabler-MIT.txt": "https://raw.githubusercontent.com/tabler/tabler-icons/main/LICENSE",
        "lucide-ISC.txt": "https://raw.githubusercontent.com/lucide-icons/lucide/main/LICENSE",
        "pattern-monster-MIT.txt": "https://raw.githubusercontent.com/catchspider2002/svelte-svg-patterns/master/LICENSE.md",
    }
    for name, url in files.items():
        data = fetch(url)
        with open(os.path.join(LICENSES, name), "wb") as handle:
            handle.write(data)
    hero = """Hero Patterns
Copyright Steve Schoger
https://heropatterns.com

The topography pattern shipped as wallpapers/waves.png is from Hero Patterns.
It is used under Creative Commons Attribution 4.0 International (CC BY 4.0):
https://creativecommons.org/licenses/by/4.0/
"""
    with open(os.path.join(LICENSES, "hero-patterns-CC-BY-4.0.txt"), "w", encoding="utf-8") as handle:
        handle.write(hero)


def main():
    os.makedirs(OUT, exist_ok=True)
    tabler = load_icons(
        "tabler",
        TABLER,
        lambda name: "https://raw.githubusercontent.com/tabler/tabler-icons/main/icons/outline/%s.svg" % name,
    )
    lucide = load_icons(
        "lucide",
        LUCIDE,
        lambda name: "https://raw.githubusercontent.com/lucide-icons/lucide/main/icons/%s.svg" % name,
    )
    if len(tabler) < 40 or len(lucide) < 15:
        raise SystemExit("not enough icons")
    image, used = doodle(tabler, lucide)
    save_png(image, "doodle.png")
    save_png(tile_svg(DOTS_SVG, 128), "dots.png")
    topo_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "source", "hero-topography.svg")
    with open(topo_path, "rb") as handle:
        topo = handle.read()
    save_png(render_svg(topo, SIZE), "waves.png")
    write_licenses()
    notice = [
        "Tellomi chat wallpaper patterns",
        "",
        "doodle.png: line icons from Tabler Icons (MIT) and Lucide (ISC),",
        "arranged into one seamless tile. Not WhatsApp or Telegram artwork.",
        "Tabler: " + ", ".join(sorted(used["tabler"])),
        "Lucide: " + ", ".join(sorted(used["lucide"])),
        "",
        "dots.png: Pattern Monster circles-2 (MIT), catchspider2002/svelte-svg-patterns.",
        "waves.png: Hero Patterns topography (CC BY 4.0), Steve Schoger, heropatterns.com.",
        "",
        "Full license texts are in licenses/.",
    ]
    with open(os.path.join(OUT, "NOTICE.txt"), "w", encoding="utf-8") as handle:
        handle.write("\n".join(notice) + "\n")


if __name__ == "__main__":
    main()
