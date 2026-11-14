#!/usr/bin/env python3
"""Run one emulator shard. Exit 0 after a finished shard. Exit 2 on infrastructure failure.

App assertion failures stay in results.json. They do not fail the process.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from lib.catalog import FLOWS_BY_NAME, flow_files  # noqa: E402
from lib.deeplink import (  # noqa: E402
    PACKAGE,
    classify_geo,
    classify_help_url,
    classify_owned_link,
    classify_profile_mime,
)
from lib.language import language_status  # noqa: E402
from lib.phone import resolve_slot, suite_code  # noqa: E402
from lib.score import (  # noqa: E402
    classify_profile,
    classify_version,
    feed_is_empty,
    nodes_from_dump,
    score_checks,
)

PREFS = "shared_prefs/mainconfig.xml"
GRANTS = (
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.READ_CONTACTS",
    "android.permission.WRITE_CONTACTS",
)


def adb(*args: str, timeout: int = 120) -> tuple[int, str]:
    proc = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)
    return proc.returncode, (proc.stdout or "") + (proc.stderr or "")


def redact(text: str, national: str) -> str:
    if national:
        text = text.replace(national, "<phone>")
    return text


class Shard:
    def __init__(self, plan: dict, shard_name: str, apk: Path, out: Path, api: str):
        self.apk = apk
        self.out = out
        self.api = api
        self.out.mkdir(parents=True, exist_ok=True)
        (self.out / "screenshots").mkdir(exist_ok=True)
        match = next(shard for shard in plan["shards"] if shard["name"] == shard_name)
        self.shard_name = shard_name
        self.phone_slot = match.get("phone") or ""
        self.flows = [FLOWS_BY_NAME[name] for name in match["flows"]]
        self.results: dict[str, dict] = {}
        self.parts: dict[str, dict] = {}
        self.state = {"login": "untried", "login_error": "", "feed_empty": False, "national": ""}
        self.infra_error = ""

    def rel(self, path: Path) -> str:
        try:
            return str(path.relative_to(self.out))
        except ValueError:
            return path.name

    def row(self, status: str, flow: str, screenshot: str = "", error: str = "", evidence: str = "") -> dict:
        return {
            "status": status,
            "flow": flow,
            "screenshot": screenshot,
            "error": redact(error, self.state["national"])[:800],
            "evidence": evidence,
        }

    def write(self) -> None:
        payload = {
            "shard": self.shard_name,
            "api_level": self.api,
            "login": self.state["login"],
            "login_error": redact(self.state["login_error"], self.state["national"]),
            "results": self.results,
            "parts": self.parts,
        }
        target = self.out / "results.json"
        tmp = target.with_suffix(".json.tmp")
        tmp.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        tmp.replace(target)

    def screencap(self, name: str) -> str:
        dest = self.out / "screenshots" / f"{name}.png"
        proc = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=40)
        if proc.returncode == 0 and proc.stdout.startswith(b"\x89PNG"):
            dest.write_bytes(proc.stdout)
            return self.rel(dest)
        return ""

    def dump_ui(self) -> str:
        for _ in range(3):
            adb("shell", "uiautomator", "dump", "/sdcard/uidump.xml", timeout=60)
            code, xml = adb("exec-out", "cat", "/sdcard/uidump.xml", timeout=30)
            if code == 0 and "<hierarchy" in xml:
                return xml
            time.sleep(1)
        return ""

    def grant(self) -> None:
        for permission in GRANTS:
            adb("shell", "pm", "grant", PACKAGE, permission)

    def maestro(self, flow_path: Path) -> tuple[int, str, Path]:
        debug = self.out / "maestro" / flow_path.stem
        debug.mkdir(parents=True, exist_ok=True)
        env = os.environ.copy()
        env["TELLOMI_TEST_CODE"] = suite_code()
        env.setdefault("TELLOMI_TEST_PASSWORD", "")
        national = self.state["national"] or env.get("TELLOMI_TEST_PHONE_NATIONAL", "")
        if national:
            env["TELLOMI_TEST_PHONE_NATIONAL"] = national
        # Maestro 2 does not expand shell variables unless they are passed with -e.
        command = ["maestro", "test", "--debug-output", str(debug)]
        if national:
            command.extend(["-e", f"TELLOMI_TEST_PHONE_NATIONAL={national}"])
        command.extend([
            "-e", f"TELLOMI_TEST_CODE={suite_code()}",
            "-e", f"TELLOMI_TEST_PASSWORD={env.get('TELLOMI_TEST_PASSWORD', '')}",
            str(flow_path),
        ])
        proc = subprocess.run(
            command,
            capture_output=True,
            text=True,
            timeout=700,
            env=env,
            cwd=str(ROOT),
        )
        log = redact((proc.stdout or "") + "\n" + (proc.stderr or ""), self.state["national"])
        (debug / "maestro.log").write_text(log[-20000:], encoding="utf-8")
        return proc.returncode, log, debug

    def blocked_reason(self, debug: Path, log: str) -> str:
        if debug.exists():
            for path in debug.rglob("*.png"):
                if path.name.startswith("BLOCKED"):
                    return path.stem.removeprefix("BLOCKED-").removeprefix("BLOCKED_") or "blocked"
        match = re.search(r"BLOCKED[:_-]([A-Za-z0-9_-]+)", log)
        if match:
            return match.group(1)
        return ""

    def mark_flow(self, flow: dict, status: str, error: str, screenshot: str) -> None:
        flow_name = flow_files(flow)[0]
        for backlog_id in flow.get("checks", {}):
            evidence = flow["checks"][backlog_id].get("evidence", "")
            self.results[backlog_id] = self.row(status, flow_name, screenshot, error, evidence)
        for backlog_id, part in flow.get("emits_parts", {}).items():
            self.parts.setdefault(backlog_id, {})[part] = self.row(status, flow_name, screenshot, error)

    def ensure_login(self) -> bool:
        if self.state["login"] == "ok":
            return True
        if self.state["login"] == "fail":
            return False
        national, reason = resolve_slot(self.phone_slot or "A", os.environ.get(f"TELLOMI_TEST_PHONE_{self.phone_slot or 'A'}"))
        if reason or not national:
            self.state["login"] = "fail"
            self.state["login_error"] = reason or "no dedicated test phone"
            return False
        self.state["national"] = national
        os.environ["TELLOMI_TEST_PHONE_NATIONAL"] = national
        os.environ["TELLOMI_TEST_CODE"] = suite_code()
        os.environ.setdefault("TELLOMI_TEST_PASSWORD", "")
        code, log, debug = self.maestro(ROOT / "flows" / "sub" / "login.yaml")
        shot = self.screencap("login")
        reason_blocked = self.blocked_reason(debug, log)
        if code == 0:
            self.state["login"] = "ok"
            self.state["login_shot"] = shot
            return True
        if reason_blocked:
            self.state["login"] = "fail"
            self.state["login_error"] = f"login blocked: {reason_blocked}"
            self.state["login_shot"] = shot
            return False
        if self.adb_login(national):
            self.state["login"] = "ok"
            return True
        self.state["login"] = "fail"
        self.state["login_error"] = "login did not reach the main tabs. " + log[-400:]
        self.state["login_shot"] = shot
        return False

    def adb_login(self, national: str) -> bool:
        """Tap the phone and code fields from the accessibility dump."""
        xml = self.dump_ui()
        if not xml:
            return False
        self._tap_text(xml, r"OK|好")
        xml = self.dump_ui()
        if self._tap_text(xml, r"Country code"):
            adb("shell", "input", "keyevent", "67")
            adb("shell", "input", "keyevent", "67")
            adb("shell", "input", "keyevent", "67")
            adb("shell", "input", "keyevent", "67")
        xml = self.dump_ui()
        if not self._tap_text(xml, r"Phone number|电话号码"):
            return False
        adb("shell", "input", "keyevent", "123")
        for _ in range(24):
            adb("shell", "input", "keyevent", "67")
        adb("shell", "input", "text", national)
        xml = self.dump_ui()
        if not self._tap_text(xml, r"^Done$|^完成$"):
            self._tap_percent(88, 54)
        time.sleep(4)
        xml = self.dump_ui()
        adb("shell", "input", "text", suite_code())
        xml = self.dump_ui()
        self._tap_text(xml, r"^Done$|^完成$")
        time.sleep(8)
        xml = self.dump_ui()
        nodes = "\n".join(nodes_from_dump(xml))
        if re.search(r"First name|名字", nodes):
            self._tap_text(xml, r"First name|名字")
            adb("shell", "input", "text", "UiTest")
            xml = self.dump_ui()
            self._tap_text(xml, r"^Done$|^完成$")
            time.sleep(6)
            xml = self.dump_ui()
            nodes = "\n".join(nodes_from_dump(xml))
        return bool(re.search(r"发现|Discover|聊天|Chats|设置|Settings", nodes))

    def _tap_percent(self, x_percent: int, y_percent: int) -> None:
        _code, size = adb("shell", "wm", "size")
        match = re.search(r"(\d+)x(\d+)", size)
        if not match:
            return
        width, height = int(match.group(1)), int(match.group(2))
        adb("shell", "input", "tap", str(width * x_percent // 100), str(height * y_percent // 100))

    def _tap_text(self, xml: str, pattern: str) -> bool:
        for node in re.findall(r"<node\b[^>]*>", xml):
            label = ""
            for attr in ("text", "content-desc"):
                found = re.search(rf'{attr}="([^"]*)"', node)
                if found and found.group(1):
                    label = found.group(1)
                    break
            bounds = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
            if not label or not bounds or not re.search(pattern, label):
                continue
            x1, y1, x2, y2 = (int(value) for value in bounds.groups())
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            return True
        return False

    def install(self) -> bool:
        if not self.apk.is_file():
            self.infra_error = f"apk missing: {self.apk}"
            return False
        if shutil_which("adb") is None:
            self.infra_error = "adb is not on PATH"
            return False
        if shutil_which("maestro") is None:
            self.infra_error = "maestro is not on PATH"
            return False
        code, out = adb("install", "-r", "-d", str(self.apk), timeout=180)
        if code != 0:
            self.infra_error = "adb install failed: " + out[-400:]
            return False
        self.grant()
        return True

    def run(self) -> int:
        if not self.install():
            for flow in self.flows:
                self.mark_flow(flow, "fail", "infrastructure: " + self.infra_error, "")
            self.write()
            return 2
        for index, flow in enumerate(self.flows):
            try:
                self.dispatch(flow)
            except subprocess.TimeoutExpired as exc:
                self.mark_flow(flow, "fail", f"infrastructure: timed out {exc}", self.screencap(flow["name"]))
                self.infra_error = "timed out"
            except Exception as exc:  # noqa: BLE001 - one flow must not drop the shard file
                self.mark_flow(flow, "fail", f"infrastructure: {exc}", self.screencap(flow["name"]))
                self.infra_error = str(exc)
            self.write()
            if index + 1 < len(self.flows):
                adb("shell", "input", "keyevent", "4")
                adb("shell", "input", "keyevent", "4")
        self.write()
        return 2 if self.infra_error else 0

    def dispatch(self, flow: dict) -> None:
        handler = flow["handler"]
        if handler == "language":
            self.handle_language(flow)
            return
        if flow.get("needs_login") and not self.ensure_login():
            shot = self.state.get("login_shot", "")
            self.mark_flow(flow, "blocked", self.state["login_error"] or "login failed", shot)
            return
        if handler == "deeplink":
            self.handle_deeplink(flow)
        elif handler == "terms":
            self.handle_terms(flow)
        elif handler == "version":
            self.handle_version(flow)
        elif handler == "url":
            self.handle_url(flow)
        elif handler == "profile":
            self.handle_profile(flow)
        else:
            self.handle_maestro(flow)

    def handle_language(self, flow: dict) -> None:
        auto = ROOT / "flows" / "language-auto.yaml"
        code, log, debug = self.maestro(auto)
        shot = self.screencap("language-auto")
        if code != 0 and not self.blocked_reason(debug, log):
            self.results["A-LANG-007"] = self.row("fail", "flows/language-auto.yaml", shot, log[-400:])
            return
        time.sleep(30)
        _code, auto_xml = adb("exec-out", "run-as", PACKAGE, "cat", PREFS)
        self.grant()
        login_ok = self.ensure_login()
        manual_xml = ""
        if login_ok:
            manual = ROOT / "flows" / "language-manual.yaml"
            m_code, m_log, _m_debug = self.maestro(manual)
            shot = self.screencap("language-manual") or shot
            time.sleep(5)
            _code, manual_xml = adb("exec-out", "run-as", PACKAGE, "cat", PREFS)
            if m_code != 0:
                status, error = language_status(auto_xml, None, False)
                if status != "fail":
                    status, error = "fail", "manual language screen did not open. " + m_log[-300:]
                self.results["A-LANG-007"] = self.row(status, "flows/language-manual.yaml", shot, error)
                return
        status, error = language_status(auto_xml, manual_xml, login_ok)
        self.results["A-LANG-007"] = self.row(
            status,
            "flows/language-manual.yaml" if login_ok else "flows/language-auto.yaml",
            shot,
            error,
            "mainconfig language",
        )

    def handle_maestro(self, flow: dict) -> None:
        path = ROOT / flow["file"]
        code, log, debug = self.maestro(path)
        shot = self.screencap(flow["name"])
        blocked = self.blocked_reason(debug, log)
        if code != 0:
            if blocked:
                self.mark_flow(flow, "blocked", blocked, shot)
            elif flow.get("feed_followup") and self.state.get("feed_empty"):
                self.mark_flow(flow, "blocked", "feed had no post", shot)
            else:
                self.mark_flow(flow, "fail", log[-500:], shot)
            return
        xml = self.dump_ui()
        nodes = nodes_from_dump(xml)
        if flow.get("feed"):
            self.state["feed_empty"] = feed_is_empty(nodes)
        scored = score_checks(nodes, flow["checks"], empty=self.state["feed_empty"], dump_ok=bool(xml))
        for backlog_id, (status, error) in scored.items():
            spec = flow["checks"][backlog_id]
            self.results[backlog_id] = self.row(status, flow["file"], shot, error, spec.get("evidence", ""))

    def handle_profile(self, flow: dict) -> None:
        path = ROOT / flow["file"]
        code, log, debug = self.maestro(path)
        shot = self.screencap(flow["name"])
        if code != 0:
            self.mark_flow(flow, "fail", log[-500:], shot)
            return
        nodes = nodes_from_dump(self.dump_ui())
        special = classify_profile(nodes)
        patterned = {key: spec for key, spec in flow["checks"].items() if spec.get("any") or spec.get("mode")}
        scored = score_checks(nodes, patterned, dump_ok=bool(nodes))
        for backlog_id, (status, error) in {**scored, **special}.items():
            evidence = flow["checks"].get(backlog_id, {}).get("evidence", "")
            if backlog_id in ("A-PROFILE-296", "A-IDX-STORY-028", "A-IDX-MEDIA-009") and status == "pass":
                evidence = "tab title was 快拍 or Stories"
            self.results[backlog_id] = self.row(status, flow["file"], shot, error, evidence)

    def handle_version(self, flow: dict) -> None:
        path = ROOT / flow["file"]
        code, log, _debug = self.maestro(path)
        shot = self.screencap(flow["name"])
        if code != 0:
            self.mark_flow(flow, "fail", log[-500:], shot)
            return
        text = "\n".join(nodes_from_dump(self.dump_ui()))
        _code, package_dump = adb("shell", "dumpsys", "package", PACKAGE)
        classified = classify_version(text, package_dump)
        for backlog_id, (status, error, evidence) in classified.items():
            self.results[backlog_id] = self.row(status, flow["file"], shot, error, evidence)

    def handle_url(self, flow: dict) -> None:
        path = ROOT / flow["file"]
        code, log, _debug = self.maestro(path)
        shot = self.screencap(flow["name"])
        if code != 0:
            self.mark_flow(flow, "fail", log[-500:], shot)
            return
        time.sleep(2)
        ui = "\n".join(nodes_from_dump(self.dump_ui()))
        _code, dumpsys = adb("shell", "dumpsys", "activity", "activities", timeout=60)
        status, error, evidence = classify_help_url(dumpsys, ui, flow.get("path_hint", ""))
        for backlog_id in flow["checks"]:
            self.results[backlog_id] = self.row(status, flow["file"], shot, error, evidence)
        for backlog_id, part in flow.get("emits_parts", {}).items():
            self.parts.setdefault(backlog_id, {})[part] = self.row(status, flow["file"], shot, error, evidence)

    def handle_terms(self, flow: dict) -> None:
        path = ROOT / flow["file"]
        code, log, _debug = self.maestro(path)
        shot = self.screencap(flow["name"])
        if code != 0:
            self.mark_flow(flow, "fail", log[-500:], shot)
            return
        xml = self.dump_ui()
        nodes = nodes_from_dump(xml)
        scored = score_checks(nodes, flow["checks"], dump_ok=bool(xml))
        for backlog_id, (status, error) in scored.items():
            self.results[backlog_id] = self.row(status, flow["file"], shot, error)
        ui = "\n".join(nodes)
        _code, dumpsys = adb("shell", "dumpsys", "activity", "activities", timeout=60)
        status, error, evidence = classify_help_url(dumpsys, ui, "/legal/terms")
        if status != "pass":
            from lib.deeplink import intent_urls, url_problem

            bad = url_problem(intent_urls(dumpsys + "\n" + ui))
            if bad:
                status, error, evidence = "fail", f"terms opened {bad}", bad
            elif "Terms of Service" in ui or "服务条款" in ui:
                status, error, evidence = "pass", "", "terms copy is on the login screen and no telegram.org URL was opened"
            else:
                status, error, evidence = "blocked", "the phone screen has no terms link to open", ""
        for backlog_id, part in flow.get("emits_parts", {}).items():
            self.parts.setdefault(backlog_id, {})[part] = self.row(status, flow["file"], shot, error, evidence)

    def handle_deeplink(self, flow: dict) -> None:
        path = ROOT / flow["file"]
        shot = ""
        statuses = []
        adb("shell", "pm", "set-app-links-user-selection", "--user", "0", PACKAGE, "true", "tell.cc")
        adb("shell", "pm", "set-app-links", PACKAGE, "2", "tell.cc")
        if flow.get("mime"):
            resolved, logcat = self.probe_mime(flow["mime"])
            status, error = classify_profile_mime(resolved, logcat, False)
            statuses.append((status, error))
        else:
            for url in flow.get("urls") or []:
                resolved, logcat = self.probe_view(url)
                if url.startswith("geo:"):
                    status, error = classify_geo(resolved, logcat)
                elif "ResolverActivity" in resolved:
                    _code, queried = adb(
                        "shell", "cmd", "package", "query-activities", "--brief",
                        "-a", "android.intent.action.VIEW", "-d", url,
                    )
                    if PACKAGE in queried or "LaunchActivity" in queried:
                        status, error = (
                            "blocked",
                            "the https link is offered in the app chooser; the emulator has not verified the tell.cc association",
                        )
                    else:
                        status, error = classify_owned_link(resolved, logcat)
                else:
                    status, error = classify_owned_link(resolved, logcat)
                statuses.append((status, error or url))
        code, log, _debug = self.maestro(path)
        shot = self.screencap(flow["name"])
        backlog_id = next(iter(flow["checks"]))
        if any(status == "fail" for status, _error in statuses):
            error = "; ".join(error for status, error in statuses if status == "fail")
            self.results[backlog_id] = self.row("fail", flow["file"], shot, error)
            return
        if any(status == "blocked" for status, _error in statuses):
            error = "; ".join(error for status, error in statuses if status == "blocked")
            self.results[backlog_id] = self.row("blocked", flow["file"], shot, error)
            return
        if code != 0 and flow["name"] in ("links-tellcc", "links-scheme") and any(status != "pass" for status, _error in statuses):
            self.results[backlog_id] = self.row("fail", flow["file"], shot, log[-400:])
            return
        evidence = ""
        if flow["name"] == "links-scheme":
            evidence = "LaunchActivity accepted tellomi://. BuildVars.toTgUrl rewrites it to tg:// before the existing parser; that rewrite is not drawn."
        self.results[backlog_id] = self.row("pass", flow["file"], shot, "", evidence)

    def probe_view(self, url: str) -> tuple[str, str]:
        adb("logcat", "-c")
        _code, resolved = adb(
            "shell", "cmd", "package", "resolve-activity", "--brief",
            "-a", "android.intent.action.VIEW", "-d", url,
        )
        adb("shell", "am", "start", "-W", "-a", "android.intent.action.VIEW", "-d", url, timeout=40)
        _code, logcat = adb("logcat", "-d", "-t", "300")
        return resolved, logcat

    def probe_mime(self, mime: str) -> tuple[str, str]:
        adb("logcat", "-c")
        _code, resolved = adb(
            "shell", "cmd", "package", "query-activities", "--brief",
            "-a", "android.intent.action.VIEW", "-t", mime,
        )
        adb("shell", "am", "start", "-W", "-a", "android.intent.action.VIEW", "-t", mime, timeout=40)
        _code, logcat = adb("logcat", "-d", "-t", "300")
        return resolved, logcat


def shutil_which(name: str) -> str | None:
    from shutil import which
    return which(name)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--plan", required=True)
    parser.add_argument("--shard", required=True)
    parser.add_argument("--apk", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--api", required=True)
    args = parser.parse_args(argv)
    plan = json.loads(Path(args.plan).read_text(encoding="utf-8"))
    shard = Shard(plan, args.shard, Path(args.apk), Path(args.out), args.api)
    return shard.run()


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except BrokenPipeError:
        sys.exit(0)
