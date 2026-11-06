#!/usr/bin/env python3
"""Launcher, notification, splash, and shortcut-badge checks.

    python3 TMessagesProj/tellomi/brand/test_brand_surfaces.py

The splash lock follows OFFICIAL-README item 9: ink only, light #111113,
night #FFFFFF, painted box about 46% of the canvas, tail inside the
Android 12 safe circle. This tree's cold-start drawable is tg_splash_320
(the start theme already points at it). There is no ic_splash name.
"""

import hashlib
import re
import shutil
import unittest
from pathlib import Path

import render_launcher_icons as brand

ROOT = Path(__file__).resolve().parents[2]
TEL = ROOT / "tellomi/res"
MAIN = ROOT / "src/main/res"
DENSITIES = ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
LOGO = Path(__file__).resolve().parent / "logo.svg"


def _paths(text):
    return re.findall(r'(?:android:pathData|\sd)="([^"]+)"', text)


class BrandSurfaceTest(unittest.TestCase):
    def test_splash_is_ink_on_the_theme_not_a_disc(self):
        light = (TEL / "drawable/tg_splash_320.xml").read_text()
        night = (TEL / "drawable-night/tg_splash_320.xml").read_text()
        logo = LOGO.read_text()
        self.assertEqual(_paths(light), _paths(logo))
        self.assertEqual(_paths(night), _paths(logo))
        self.assertEqual(light.count("<path"), 2)
        self.assertEqual(night.count("<path"), 2)
        light_fills = re.findall(r'android:fillColor="([^"]+)"', light)
        night_fills = re.findall(r'android:fillColor="([^"]+)"', night)
        self.assertEqual(light_fills, ["#111113", "#111113"])
        self.assertEqual(night_fills, ["#FFFFFF", "#FFFFFF"])
        self.assertNotIn("#17171A", light)
        self.assertNotIn("#17171A", night)
        for text in (light, night):
            self.assertNotIn("#2aabee", text.lower())
            self.assertNotIn("M -14.734", text)
            self.assertIn('viewportWidth="320"', text)
            self.assertIn('viewportHeight="320"', text)
        self.assertEqual(
            re.search(r"<group[\s\S]+</group>", light).group(0).replace("#111113", "#FFFFFF"),
            re.search(r"<group[\s\S]+</group>", night).group(0),
        )

    def test_splash_matches_the_generator_and_stays_inside_the_safe_circle(self):
        if shutil.which("rsvg-convert") is None:
            self.skipTest("rsvg-convert is not installed")
        paths = brand.load_paths()
        bbox = brand.measure_bbox(paths)
        light = (TEL / "drawable/tg_splash_320.xml").read_text()
        night = (TEL / "drawable-night/tg_splash_320.xml").read_text()
        self.assertEqual(light, brand.splash_xml(paths, bbox, brand.INK_SPLASH_LIGHT))
        self.assertEqual(night, brand.splash_xml(paths, bbox, brand.INK_SPLASH_DARK))
        longest, farthest = brand.measure_placed(
            paths, bbox, brand.SPLASH_CANVAS, brand.SPLASH_FRACTION
        )
        self.assertGreaterEqual(longest, 0.44)
        self.assertLessEqual(longest, 0.48)
        self.assertLess(farthest, brand.SPLASH_SAFE_RADIUS)

    def test_adaptive_launcher_is_the_tellomi_mark(self):
        adaptive = (TEL / "mipmap-anydpi-v26/ic_launcher.xml").read_text()
        self.assertIn("@color/tellomi_launcher_background", adaptive)
        self.assertIn("@drawable/tellomi_launcher_foreground", adaptive)
        self.assertIn("@drawable/tellomi_launcher_monochrome", adaptive)
        self.assertEqual(
            adaptive,
            (TEL / "mipmap-anydpi-v26/ic_launcher_round.xml").read_text(),
        )
        for folder in ("values", "values-night"):
            colors = (TEL / folder / "tellomi_launcher_colors.xml").read_text()
            self.assertIn("#FFFFFF", colors)
            self.assertIn("#17171A", colors)
        foreground = (TEL / "drawable/tellomi_launcher_foreground.xml").read_text()
        self.assertEqual(_paths(foreground), _paths(LOGO.read_text()))
        self.assertIn("@color/tellomi_launcher_ink", foreground)
        mono = (TEL / "drawable/tellomi_launcher_monochrome.xml").read_text()
        self.assertIn("#FFFFFF", mono)
        self.assertEqual(_paths(mono), _paths(LOGO.read_text()))

    def test_legacy_launcher_and_notification_are_present(self):
        for density in DENSITIES:
            for name in ("ic_launcher.png", "ic_launcher_round.png"):
                day = TEL / f"mipmap-{density}" / name
                night = TEL / f"mipmap-night-{density}" / name
                upstream = MAIN / f"mipmap-{density}" / name
                self.assertTrue(day.is_file(), day)
                self.assertTrue(night.is_file(), night)
                self.assertNotEqual(day.read_bytes(), upstream.read_bytes(), name)
            note = TEL / f"drawable-{density}" / "notification.webp"
            if density == "xxxhdpi":
                self.assertTrue(note.is_file(), note)
                continue
            self.assertTrue(note.is_file(), note)
            upstream_note = MAIN / f"drawable-{density}" / "notification.webp"
            if upstream_note.is_file():
                self.assertNotEqual(note.read_bytes(), upstream_note.read_bytes())

    def test_shortcut_badge_is_not_the_paper_plane(self):
        hashes = set()
        for density in DENSITIES:
            flavor = TEL / f"drawable-{density}" / "book_logo.webp"
            upstream = MAIN / f"drawable-{density}" / "book_logo.webp"
            self.assertTrue(flavor.is_file(), flavor)
            self.assertTrue(upstream.is_file(), upstream)
            self.assertNotEqual(flavor.read_bytes(), upstream.read_bytes(), density)
            hashes.add(hashlib.md5(flavor.read_bytes()).hexdigest())
        self.assertEqual(len(hashes), len(DENSITIES))
        for name in ("shortcut_compose.webp", "shortcut_user.webp"):
            self.assertFalse(list(TEL.rglob(name)), name)


if __name__ == "__main__":
    unittest.main()
