"""Harness tests. No emulator and no network."""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[2]
sys.path.insert(0, str(ROOT))

from lib.catalog import (  # noqa: E402
    DEVICE_ONLY,
    FLOWS,
    PARTS,
    STATIC_BLOCKED,
    backlog_ids,
    covered_ids,
    flow_files,
)
from lib.deeplink import classify_geo, classify_help_url, classify_owned_link, classify_profile_mime  # noqa: E402
from lib.language import language_status  # noqa: E402
from lib.merge import combine_parts, merge_reports  # noqa: E402
from lib.phone import DEDICATED, FIXED_CODE, resolve_slot, suite_code  # noqa: E402
from lib.plan import plan_run  # noqa: E402
from lib.score import classify_profile, classify_version, nodes_from_dump, score_checks  # noqa: E402


class PhoneTests(unittest.TestCase):
    def test_defaults_are_the_dedicated_pair(self):
        self.assertEqual(DEDICATED["A"], "9996617092")
        self.assertEqual(DEDICATED["B"], "9996617093")
        self.assertEqual(resolve_slot("A", ""), ("9996617092", ""))
        self.assertEqual(resolve_slot("B", None), ("9996617093", ""))
        self.assertEqual(resolve_slot("A", "+869996617092"), ("9996617092", ""))
        self.assertEqual(suite_code(), "11111")
        self.assertEqual(FIXED_CODE, "11111")

    def test_refuses_outside_range_and_other_testers(self):
        national, error = resolve_slot("A", "13800138000")
        self.assertEqual(national, "")
        self.assertIn("99966170xx", error)
        national, error = resolve_slot("A", "9996617001")
        self.assertEqual(national, "")
        self.assertIn("7001-7091", error)
        national, error = resolve_slot("B", "9996617091")
        self.assertEqual(national, "")
        self.assertIn("7001-7091", error)
        national, error = resolve_slot("A", "9996617094")
        self.assertEqual(national, "")
        self.assertIn("9996617092", error)
        national, error = resolve_slot("A", "9996617093")
        self.assertEqual(national, "")
        self.assertIn("refused", error)


class LanguageTests(unittest.TestCase):
    def test_auto_write_is_the_known_failure(self):
        xml = '<map><string name="language">en</string></map>'
        status, error = language_status(xml, None, False)
        self.assertEqual(status, "fail")
        self.assertIn("automatic", error)

    def test_manual_zh_passes_only_after_an_empty_auto_value(self):
        status, error = language_status("", '<string name="language">zh_hans</string>', True)
        self.assertEqual(status, "pass")
        self.assertEqual(error, "")
        status, error = language_status("", "", False)
        self.assertEqual(status, "blocked")
        status, error = language_status("", '<string name="language">en</string>', True)
        self.assertEqual(status, "fail")


class ScoreTests(unittest.TestCase):
    def test_dump_nodes_and_empty_feed(self):
        xml = '<hierarchy><node text="好友" content-desc="点赞 2" bounds="[0,0][10,10]" /></hierarchy>'
        nodes = nodes_from_dump(xml)
        self.assertIn("好友", nodes)
        self.assertIn("点赞 2", nodes)
        scored = score_checks(
            nodes,
            {"A-MOMENT-005": {"any": ["好友"], "missing": "fail", "contains": False, "needs_post": False}},
        )
        self.assertEqual(scored["A-MOMENT-005"][0], "pass")

    def test_version_brand(self):
        ui = "Tellomi for Android v14.0.14 (180476)\nx86_64"
        package = "application-label:'Tellomi'\napplication-label-zh-CN:'Tellomi'\n"
        result = classify_version(ui, package)
        self.assertEqual(result["A-IDX-UPD-006"][0], "pass")
        self.assertEqual(result["A-IDX-UPD-008"][0], "pass")
        leaked = classify_version("Telegram for Android 1.0", "application-label:'Telegram'\n")
        self.assertEqual(leaked["A-IDX-UPD-008"][0], "fail")

    def test_profile_stories_absent_is_not_a_pass(self):
        result = classify_profile(["我", "编辑", "动态"])
        self.assertEqual(result["A-IDX-PROF-029"][0], "pass")
        self.assertEqual(result["A-PROFILE-296"][0], "blocked")
        shown = classify_profile(["我", "编辑", "快拍"])
        self.assertEqual(shown["A-IDX-STORY-028"][0], "pass")
        self.assertEqual(shown["A-IDX-MEDIA-009"][0], "pass")


class DeeplinkTests(unittest.TestCase):
    def test_tellcc_and_scheme_and_geo_and_mime(self):
        ok, _error = classify_owned_link("app.tellomi.chat/org.telegram.ui.LaunchActivity", "")
        self.assertEqual(ok, "pass")
        failed, _error = classify_owned_link("com.android.chrome", "")
        self.assertEqual(failed, "fail")
        crashed, _error = classify_owned_link("app.tellomi.chat", "FATAL EXCEPTION: main")
        self.assertEqual(crashed, "fail")
        geo, error = classify_geo("com.android.chrome", "")
        self.assertEqual(geo, "blocked")
        self.assertIn("TellomiMapApps", error)
        mime, error = classify_profile_mime("org.telegram.ui.LaunchActivity", "", False)
        self.assertEqual(mime, "blocked")
        self.assertIn("contacts", error)
        missing, _error = classify_profile_mime("", "", False)
        self.assertEqual(missing, "fail")

    def test_help_url_rejects_telegram_hosts(self):
        status, error, evidence = classify_help_url(
            "dat=https://tellomi.cn/legal/privacy/ cmp=org.telegram.ui.LaunchActivity",
            "",
            "/legal/privacy",
        )
        self.assertEqual(status, "pass")
        self.assertIn("tellomi.cn", evidence)
        self.assertEqual(error, "")
        status, error, _evidence = classify_help_url("dat=https://telegram.org/privacy", "", "/legal/privacy")
        self.assertEqual(status, "fail")
        self.assertIn("telegram.org", error)


class PlanTests(unittest.TestCase):
    def test_empty_secrets_use_both_dedicated_slots_under_the_cap(self):
        plan = plan_run(max_shards=20, scope="all", phone_a="", phone_b="")
        names = [shard["name"] for shard in plan["shards"]]
        self.assertIn("account-a", names)
        self.assertIn("account-b", names)
        self.assertLessEqual(len(plan["shards"]), 20)
        self.assertEqual(plan["skipped"], [])
        phones = {shard["name"]: shard["phone"] for shard in plan["shards"]}
        self.assertEqual(phones["account-a"], "A")
        self.assertEqual(phones["account-b"], "B")

    def test_other_tester_number_is_not_scheduled(self):
        plan = plan_run(max_shards=20, scope="all", phone_a="9996617001", phone_b="")
        self.assertTrue(all(shard["phone"] != "A" for shard in plan["shards"]))
        account = next(shard for shard in plan["shards"] if shard["phone"] == "B")
        self.assertIn("language", account["flows"])
        self.assertIn("feed", account["flows"])

    def test_logged_out_scope_blocks_session_ids(self):
        plan = plan_run(max_shards=20, scope="logged-out", phone_a="", phone_b="")
        self.assertTrue(plan["skipped"])
        self.assertIn("A-LANG-007", plan["skipped"][0]["ids"])
        self.assertTrue(all(not shard["phone"] for shard in plan["shards"]))


class CatalogTests(unittest.TestCase):
    def test_every_backlog_id_is_covered_once(self):
        ids = backlog_ids()
        self.assertEqual(len(ids), 157)
        self.assertEqual(len(ids), len(set(ids)))
        covered = covered_ids()
        self.assertEqual(set(covered), set(ids))

    def test_device_only_is_not_executed(self):
        executed = set()
        for flow in FLOWS:
            executed.update(flow.get("checks", {}))
        self.assertTrue(DEVICE_ONLY.keys().isdisjoint(executed))
        self.assertTrue(DEVICE_ONLY.keys().isdisjoint(STATIC_BLOCKED))
        self.assertTrue(set(PARTS).isdisjoint(executed))
        for reason in DEVICE_ONLY.values():
            self.assertTrue(reason)

    def test_flow_files_tag_their_ids(self):
        for flow in FLOWS:
            files = [ROOT / path for path in flow_files(flow)]
            for path in files:
                self.assertTrue(path.is_file(), path)
            text = "\n".join(path.read_text(encoding="utf-8") for path in files)
            self.assertIn("appId: app.tellomi.chat", text)
            for backlog_id in list(flow.get("checks", {})) + list(flow.get("emits_parts", {})):
                self.assertIn(backlog_id, text, flow["name"])


class MergeTests(unittest.TestCase):
    def test_parts_and_device_only(self):
        combined = combine_parts(
            "A-IDX-UPD-007",
            {
                "privacy": {"status": "pass", "error": "", "screenshot": "p.png", "evidence": "https://tellomi.cn/legal/privacy/"},
                "faq": {"status": "pass", "error": "", "screenshot": "", "evidence": "https://tellomi.cn/help"},
                "tos": {"status": "fail", "error": "telegram.org", "screenshot": "t.png", "evidence": ""},
            },
        )
        self.assertEqual(combined["status"], "fail")
        report = merge_reports(
            [
                {
                    "shard": "login-screen",
                    "api_level": 34,
                    "results": {"A-IDX-LOGIN-018": {"status": "pass", "flow": "flows/login-screen.yaml", "screenshot": "s.png", "error": "", "evidence": ""}},
                    "parts": {},
                }
            ],
            [{"ids": ["A-LANG-007"], "error": "skipped", "phone": ""}],
        )
        self.assertEqual(report["results"]["A-LINK-001"]["status"], "device_only")
        self.assertEqual(report["results"]["A-IDX-LOGIN-018"]["status"], "pass")
        self.assertEqual(report["results"]["A-LANG-007"]["status"], "blocked")
        self.assertEqual(report["summary"]["total"], 157)
        self.assertEqual(sum(report["summary"][key] for key in ("pass", "fail", "blocked", "device_only")), 157)

    def test_merge_script_rewrites_screenshot_paths(self):
        from scripts.merge_results import main as merge_main

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            shard = root / "in" / "ui-shard-login-screen"
            (shard / "screenshots").mkdir(parents=True)
            (shard / "screenshots" / "login-screen.png").write_bytes(b"png")
            (shard / "results.json").write_text(
                json.dumps(
                    {
                        "shard": "login-screen",
                        "api_level": 34,
                        "results": {
                            "A-IDX-LOGIN-018": {
                                "status": "pass",
                                "flow": "flows/login-screen.yaml",
                                "screenshot": "screenshots/login-screen.png",
                                "error": "",
                                "evidence": "",
                            }
                        },
                        "parts": {},
                    }
                ),
                encoding="utf-8",
            )
            out = root / "out"
            code = merge_main(["--shards", str(root / "in"), "--out", str(out)])
            self.assertEqual(code, 0)
            merged = json.loads((out / "ui-results.json").read_text(encoding="utf-8"))
            self.assertEqual(
                merged["results"]["A-IDX-LOGIN-018"]["screenshot"],
                "shards/login-screen/screenshots/login-screen.png",
            )
            self.assertTrue((out / "shards" / "login-screen" / "screenshots" / "login-screen.png").is_file())


class WorkflowTests(unittest.TestCase):
    def test_workflow_is_dispatch_nightly_and_sharded(self):
        text = (REPO / ".github" / "workflows" / "tellomi-ui-tests.yml").read_text(encoding="utf-8")
        for needle in (
            "workflow_dispatch",
            "schedule:",
            "reactivecircus/android-emulator-runner",
            "x86_64",
            "ui-results.json",
            "TELLOMI_TEST_PHONE_A",
            "TELLOMI_TEST_PHONE_B",
            "max_shards",
            "assembleAfatDebug",
        ):
            self.assertIn(needle, text)
        self.assertNotIn("release/android", text)
        self.assertNotIn("ANDROID_KEYSTORE", text)


if __name__ == "__main__":
    unittest.main()
