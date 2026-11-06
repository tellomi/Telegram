#!/usr/bin/env python3
"""Lock the main settings-row icons to the iOS MingCute mapping.

The old substring rule mapped every name containing "settings" to one
gear. These rows stay on the iOS glyphs, except Business, which keeps
the upstream Android briefcase. The flavor drawables must not be copies
of each other.

    python3 TMessagesProj/tellomi/icons/test_settings_rows.py
"""

import csv
import hashlib
import unittest
from pathlib import Path

import generate_icons

# Android resource, glyph, style, iOS Item List/Icons name.
# Privacy Policy has no iOS settings row. Stars is semantic/star-coin
# (Item List/Icons/Stars), not the Premium line star.
EXPECTED = (
    ("settings_account", "user-3", "line", "Profile"),
    ("settings_ask", "message-circle-question-mark", "line", "Support"),
    ("settings_chat", "chat-1", "line", "Chat"),
    ("settings_data", "cylinder", "line", "Data"),
    ("settings_devices", "device", "line", "Devices"),
    ("settings_faq", "question", "line", "Faq"),
    ("settings_features", "bulb", "line", "Tips"),
    ("settings_folders", "folder", "line", "Folder"),
    ("settings_gift", "gift", "line", "Gift"),
    ("settings_language", "globe", "line", "Language"),
    ("settings_policy", "shield", "line", None),
    ("settings_power", "battery", "line", "PowerSaving"),
    ("settings_premium", "star", "line", "Premium"),
    ("settings_privacy", "lock", "line", "Privacy"),
    ("settings_sounds", "bell-ringing", "line", "Notifications"),
    ("settings_stars", "semantic/star-coin", "line", "Stars"),
)

ROOT = Path(__file__).resolve().parents[2]
DRAWABLE = ROOT / "tellomi/res/drawable"
UPSTREAM = ROOT / "src/main/res/drawable"
MAPPING = Path(__file__).resolve().parent / "mapping.csv"


class SettingsRowIconsTest(unittest.TestCase):
    def test_rows_match_ios_glyphs(self):
        self.assertEqual(
            generate_icons.SETTINGS_ROWS,
            {name: (icon, style) for name, icon, style, _ios in EXPECTED},
        )

    def test_match_does_not_paint_the_gear(self):
        for name, icon, style, _ios in EXPECTED:
            self.assertEqual(generate_icons.match(name), (icon, style), name)
            self.assertNotEqual((icon, style), ("settings-3", "line"), name)
        # The substring rule is still there. Names outside this list keep it.
        self.assertEqual(generate_icons.match("msg_settings_old"), ("settings-3", "line"))
        self.assertEqual(generate_icons.match("settings_gram_24"), ("tellomi-mark", "line"))
        self.assertIsNone(generate_icons.match("settings_business"))

    def test_mapping_csv_lists_each_row(self):
        rows = {}
        with MAPPING.open(newline="") as f:
            for row in csv.DictReader(f):
                rows[row["telegram_resource"]] = row
        self.assertEqual(rows["settings_arrow"]["mingcute_name"], "chevron-right")
        for name, icon, style, _ios in EXPECTED:
            self.assertIn(name, rows, name)
            self.assertEqual(rows[name]["mingcute_name"], icon, name)
            self.assertEqual(rows[name]["style"], style, name)
            self.assertEqual(rows[name]["kind"], "vector", name)

    def test_drawables_are_the_generated_glyphs_and_differ(self):
        gear_svg = generate_icons.vendored_svg("settings-3", "line")
        gear_body, gear_paths = generate_icons.android_vector(gear_svg, "24", "24")
        self.assertGreater(gear_paths, 0)
        bodies = {}
        hashes = {}
        for name, _icon, _style, _ios in EXPECTED:
            path = DRAWABLE / f"{name}.xml"
            self.assertTrue(path.is_file(), path)
            body = path.read_text()
            self.assertEqual(body, generate_icons.settings_row_vector(name), name)
            self.assertNotEqual(body, gear_body, name)
            upstream = UPSTREAM / f"{name}.xml"
            self.assertTrue(upstream.is_file(), upstream)
            self.assertNotEqual(body, upstream.read_text(), name)
            bodies[name] = body
            hashes[name] = hashlib.md5(body.encode()).hexdigest()
        self.assertEqual(len(set(hashes.values())), len(EXPECTED))
        self.assertNotEqual(bodies["settings_premium"], bodies["settings_stars"])
        self.assertFalse((DRAWABLE / "settings_wallet.xml").exists())
        self.assertFalse((DRAWABLE / "settings_wallet_dark.xml").exists())
        self.assertFalse((DRAWABLE / "settings_business.xml").exists())


if __name__ == "__main__":
    unittest.main()
