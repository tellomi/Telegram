#!/usr/bin/env python3
"""Merge shard results.json files into ui-results.json."""

from __future__ import annotations

import argparse
import json
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from lib.merge import merge_reports  # noqa: E402


def _rewrite(report: dict, prefix: str) -> dict:
    def fix(row: dict) -> dict:
        copied = dict(row)
        shot = copied.get("screenshot") or ""
        if shot and not shot.startswith("shards/"):
            copied["screenshot"] = f"{prefix}/{shot}"
        return copied

    report = dict(report)
    report["results"] = {key: fix(row) for key, row in (report.get("results") or {}).items()}
    parts = {}
    for backlog_id, named in (report.get("parts") or {}).items():
        parts[backlog_id] = {name: fix(row) for name, row in named.items()}
    report["parts"] = parts
    return report


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--plan", type=Path)
    parser.add_argument("--shards", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args(argv)
    skipped = []
    if args.plan and args.plan.is_file():
        skipped = json.loads(args.plan.read_text(encoding="utf-8")).get("skipped") or []
    reports = []
    args.out.mkdir(parents=True, exist_ok=True)
    for result_path in sorted(args.shards.glob("*/results.json")):
        report = json.loads(result_path.read_text(encoding="utf-8"))
        shard = report.get("shard") or result_path.parent.name
        prefix = f"shards/{shard}"
        destination = args.out / "shards" / shard
        if destination.exists():
            shutil.rmtree(destination)
        shutil.copytree(result_path.parent, destination, ignore=shutil.ignore_patterns("maestro", "*.tmp"))
        reports.append(_rewrite(report, prefix))
    merged = merge_reports(reports, skipped)
    target = args.out / "ui-results.json"
    target.write_text(json.dumps(merged, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    summary = merged["summary"]
    line = (
        f"pass={summary['pass']} fail={summary['fail']} "
        f"blocked={summary['blocked']} device_only={summary['device_only']} total={summary['total']}"
    )
    print(line)
    step = os_summary()
    if step:
        text = Path(step).read_text(encoding="utf-8") if Path(step).exists() else ""
        Path(step).write_text(text + "\n## UI results\n\n`" + line + "`\n", encoding="utf-8")
    return 0


def os_summary() -> str:
    import os
    return os.environ.get("GITHUB_STEP_SUMMARY", "")


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
