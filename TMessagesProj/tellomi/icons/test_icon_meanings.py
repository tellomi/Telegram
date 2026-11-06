#!/usr/bin/env python3
"""The icon matcher must not recreate the meaning bugs from the MingCute pass.

    python3 TMessagesProj/tellomi/icons/test_icon_meanings.py
"""

import hashlib
import unittest
from pathlib import Path

from PIL import Image

import generate_icons
import icon_fixes

ROOT = Path(__file__).resolve().parents[2]
TEL = ROOT / "tellomi/res"
MAIN = ROOT / "src/main/res"


def _raster(name):
    return TEL / "drawable-xxhdpi" / f"{name}.webp"


def _ink(path):
    im = Image.open(path).convert("RGBA")
    w, h = im.size
    mask = [[0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            if im.getpixel((x, y))[3] > 40:
                mask[y][x] = 1
    seen = [[0] * w for _ in range(h)]
    comps = 0
    for y in range(h):
        for x in range(w):
            if not mask[y][x] or seen[y][x]:
                continue
            stack = [(x, y)]
            seen[y][x] = 1
            area = 0
            while stack:
                cx, cy = stack.pop()
                area += 1
                for nx, ny in ((cx - 1, cy), (cx + 1, cy), (cx, cy - 1), (cx, cy + 1)):
                    if 0 <= nx < w and 0 <= ny < h and mask[ny][nx] and not seen[ny][nx]:
                        seen[ny][nx] = 1
                        stack.append((nx, ny))
            if area >= 8:
                comps += 1
    return comps, hashlib.sha1(im.tobytes()).hexdigest()


class IconMeaningTest(unittest.TestCase):
    def test_exact_names_win_over_short_tokens(self):
        self.assertEqual(generate_icons.match("list_check"), ("check", "line"))
        self.assertEqual(generate_icons.match("list_halfcheck"), ("check-half", "line"))
        self.assertEqual(generate_icons.match("msg_gift_premium"), ("gift", "line"))
        self.assertNotEqual(generate_icons.match("msg_gift_premium"), ("film", "line"))
        self.assertEqual(generate_icons.match("search_files_filled"), ("file", "line"))
        self.assertEqual(generate_icons.match("search_voice_filled"), ("mic", "line"))
        self.assertEqual(generate_icons.match("photo_star"), ("star", "line"))
        self.assertEqual(generate_icons.match("input_video"), ("camera-lens", "line"))
        self.assertEqual(generate_icons.match("msg_gif"), ("gif-label", "line"))
        self.assertEqual(generate_icons.match("msg_language"), ("globe", "line"))
        self.assertNotEqual(generate_icons.match("menu_2sv"), ("more-2", "line"))
        self.assertNotEqual(generate_icons.match("menu_2sv_on"), ("more-2", "line"))
        self.assertNotEqual(generate_icons.match("menu_feature_paid"), ("more-2", "line"))
        self.assertEqual(generate_icons.match("menu_feature_paid"), ("coin", "line"))

    def test_unmatched_names_do_not_become_overflow(self):
        for name in (
            "menu_not_a_real_icon",
            "totally_unknown_glyph",
            "widget_photo_extra",
        ):
            self.assertIsNone(generate_icons.match(name), name)
        self.assertIsNone(generate_icons.match("send_outline"))
        self.assertIsNone(generate_icons.match("settings_business"))
        self.assertIsNone(generate_icons.match("premium_ai_editor"))

    def test_reverted_overlays_are_gone(self):
        for name in (
            "send_outline",
            "settings_business",
            "premium_ai_editor",
            "star_premium",
            "msg_premium_limits",
            "menu_premium_seen",
            "community_cards",
            "msg_speed",
            "msg_voicechat",
            "filled_gift_premium",
        ):
            hits = [
                p for p in TEL.rglob(name + ".*")
                if p.parent.name == "drawable" or p.parent.name.startswith("drawable-")
            ]
            self.assertEqual(hits, [], name)
            upstream = list(MAIN.rglob(name + ".*"))
            self.assertTrue(upstream, name)

    def test_send_product_glyphs_stay(self):
        text = (TEL / "drawable" / "send_plane_24.xml").read_text()
        self.assertIn("arrow-up", text)
        card = (TEL / "drawable" / "send_plane_26.xml").read_text()
        self.assertIn("M485.73", card)
        self.assertTrue((TEL / "drawable-xxhdpi" / "msg_send.webp").is_file())
        self.assertTrue((TEL / "drawable-xxhdpi" / "ic_send.webp").is_file())

    def test_pairs_are_not_the_same_bitmap(self):
        pairs = (
            ("list_check", "list_halfcheck"),
            ("msg_fave", "msg_unfave"),
            ("msg_pin", "msg_unpin"),
            ("menu_share_on_24", "menu_share_off_24"),
            ("menu_2sv", "menu_2sv_on"),
            ("msg_addfolder", "msg_removefolder"),
            ("search_date_filled", "search_files_filled"),
            ("search_media_filled", "search_music_filled"),
            ("search_users_filled", "search_voice_filled"),
            ("input_video", "input_video_pressed"),
        )
        for a, b in pairs:
            pa, pb = _raster(a), _raster(b)
            if a.startswith("menu_2sv") or a.startswith("menu_share"):
                pa = TEL / "drawable" / f"{a}.xml"
                pb = TEL / "drawable" / f"{b}.xml"
                self.assertNotEqual(pa.read_bytes(), pb.read_bytes(), (a, b))
                continue
            self.assertNotEqual(_ink(pa)[1], _ink(pb)[1], (a, b))

    def test_list_check_is_one_tick_and_half_is_not_a_full_tick(self):
        comps, _ = _ink(_raster("list_check"))
        self.assertEqual(comps, 1)
        half = Image.open(_raster("list_halfcheck")).convert("RGBA")
        w, h = half.size
        left = sum(1 for y in range(h) for x in range(w // 3) if half.getpixel((x, y))[3] > 40)
        right = sum(1 for y in range(h) for x in range(w * 2 // 3, w) if half.getpixel((x, y))[3] > 40)
        self.assertLess(left, right)

    def test_search_filters_are_seven_drawings(self):
        names = [
            "search_date_filled", "search_files_filled", "search_links_filled",
            "search_media_filled", "search_music_filled", "search_users_filled",
            "search_voice_filled",
        ]
        hashes = {_ink(_raster(n))[1] for n in names}
        self.assertEqual(len(hashes), 7)

    def test_gram_mark_is_white_and_complete(self):
        for name in icon_fixes.GRAM_MARKS:
            text = (TEL / "drawable" / f"{name}.xml").read_text()
            self.assertNotIn("111111", text, name)
            self.assertIn("#FFFFFF", text, name)
            self.assertIn("M485.73", text, name)
            self.assertNotIn("A25.2,25.2", text, name)

    def test_tag_actions_differ(self):
        files = [
            TEL / "drawable-xxhdpi" / f"{name}.webp"
            for name in (
                "menu_tag_plus", "menu_tag_delete", "menu_tag_edit",
                "menu_tag_filter", "menu_tag_rename",
            )
        ]
        hashes = {_ink(p)[1] for p in files}
        self.assertEqual(len(hashes), 5)


if __name__ == "__main__":
    unittest.main()
