"""Merge shard JSON into one backlog report."""

from __future__ import annotations

import json
from datetime import datetime, timezone

from .catalog import DEVICE_ONLY, PARTS, STATIC_BLOCKED, backlog_ids

STATUSES = ("pass", "fail", "blocked", "device_only")


def _blank(status: str, error: str, **extra) -> dict:
    row = {
        "status": status,
        "flow": extra.get("flow", ""),
        "screenshot": extra.get("screenshot", ""),
        "error": error,
        "evidence": extra.get("evidence", ""),
        "shard": extra.get("shard", ""),
        "api_level": extra.get("api_level", ""),
    }
    return row


def combine_parts(backlog_id: str, parts: dict[str, dict]) -> dict:
    expected = PARTS[backlog_id]
    rows = []
    missing = []
    for name in expected:
        row = parts.get(name)
        if not row:
            missing.append(name)
        else:
            rows.append((name, row))
    if any(row.get("status") == "fail" for _, row in rows):
        errors = [f"{name}: {row.get('error') or 'failed'}" for name, row in rows if row.get("status") == "fail"]
        screenshot = next((row.get("screenshot") or "" for _, row in rows if row.get("screenshot")), "")
        return _blank("fail", "; ".join(errors), screenshot=screenshot, evidence=_evidence(rows))
    if missing:
        return _blank("blocked", "missing " + ", ".join(missing), evidence=_evidence(rows))
    if any(row.get("status") == "blocked" for _, row in rows):
        errors = [f"{name}: {row.get('error') or 'blocked'}" for name, row in rows if row.get("status") != "pass"]
        return _blank("blocked", "; ".join(errors), evidence=_evidence(rows))
    if rows and all(row.get("status") == "pass" for _, row in rows):
        return _blank("pass", "", evidence=_evidence(rows), screenshot=rows[0][1].get("screenshot") or "")
    return _blank("blocked", "no parts recorded")


def _evidence(rows: list[tuple[str, dict]]) -> str:
    bits = []
    for name, row in rows:
        evidence = row.get("evidence") or ""
        if evidence:
            bits.append(f"{name}={evidence}")
    return "; ".join(bits)


def merge_reports(shard_reports: list[dict], skipped: list[dict] | None = None) -> dict:
    results: dict[str, dict] = {}
    part_bucket: dict[str, dict] = {backlog_id: {} for backlog_id in PARTS}

    for backlog_id, reason in DEVICE_ONLY.items():
        results[backlog_id] = _blank("device_only", reason, evidence="not run on an emulator")
    for backlog_id, reason in STATIC_BLOCKED.items():
        results[backlog_id] = _blank("blocked", reason)

    for entry in skipped or []:
        for backlog_id in entry.get("ids") or []:
            results[backlog_id] = _blank("blocked", entry.get("error") or "skipped", shard=entry.get("phone") or "")

    for report in shard_reports:
        shard = report.get("shard") or ""
        api = report.get("api_level") or ""
        for backlog_id, row in (report.get("results") or {}).items():
            merged = dict(row)
            merged.setdefault("flow", "")
            merged.setdefault("screenshot", "")
            merged.setdefault("error", "")
            merged.setdefault("evidence", "")
            merged["shard"] = shard
            merged["api_level"] = api
            results[backlog_id] = merged
        for backlog_id, parts in (report.get("parts") or {}).items():
            if backlog_id not in part_bucket:
                part_bucket[backlog_id] = {}
            for name, row in parts.items():
                stored = dict(row)
                stored["shard"] = shard
                stored["api_level"] = api
                part_bucket[backlog_id][name] = stored

    direct_ids = set()
    for report in shard_reports:
        direct_ids.update(report.get("results") or {})
    for backlog_id in PARTS:
        if backlog_id in direct_ids:
            continue
        if part_bucket.get(backlog_id):
            results[backlog_id] = combine_parts(backlog_id, part_bucket[backlog_id])

    for backlog_id in backlog_ids():
        results.setdefault(backlog_id, _blank("blocked", "no shard recorded this id"))

    summary = {status: 0 for status in STATUSES}
    for row in results.values():
        status = row.get("status") or "blocked"
        if status not in summary:
            status = "blocked"
            row["status"] = status
        summary[status] += 1
    summary["total"] = len(results)
    return {
        "schema": 1,
        "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "summary": summary,
        "results": dict(sorted(results.items())),
    }


def load_report(path: str) -> dict:
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)
