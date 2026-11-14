"""Turn the catalog into emulator shards. Logged-in flows share at most two phones."""

from __future__ import annotations

from .catalog import FLOWS, FLOWS_BY_NAME, SHARDS
from .phone import resolve_slot


def _logged_out_names() -> list[str]:
    return [name for name, spec in SHARDS.items() if not spec["needs_login"]]


def _login_flow_names() -> list[str]:
    names = []
    for shard in ("account-a", "account-b"):
        for flow in FLOWS:
            if flow["shard"] == shard:
                names.append(flow["name"])
    return names


def plan_run(
    *,
    max_shards: int = 20,
    scope: str = "all",
    phone_a: str | None = None,
    phone_b: str | None = None,
) -> dict:
    if max_shards < 1:
        raise ValueError("max_shards must be at least 1")
    if max_shards > 20:
        raise ValueError("max_shards cannot exceed 20")
    scope = scope or "all"
    if scope not in ("all", "logged-out"):
        raise ValueError(f"unknown scope {scope}")

    skipped = []
    login_flows = _login_flow_names()
    usable = []
    refused = []
    for key, raw in (("A", phone_a), ("B", phone_b)):
        _national, reason = resolve_slot(key, raw)
        if not reason:
            usable.append(key)
        else:
            refused.append(reason)

    shards = []
    if scope == "all" and usable:
        if len(usable) >= 2:
            shards.append(_shard("account-a", usable[0], "account-a"))
            shards.append(_shard("account-b", usable[1], "account-b"))
        else:
            shards.append(
                {
                    "name": "account-a",
                    "api": SHARDS["account-a"]["api"],
                    "phone": usable[0],
                    "flows": login_flows,
                }
            )
    elif scope == "all":
        skipped.append(
            {
                "phone": "",
                "error": "; ".join(refused) or "no dedicated test phone could be resolved",
                "ids": _ids_for_flows(login_flows),
            }
        )
    else:
        skipped.append(
            {
                "phone": "",
                "error": "scope=logged-out skips flows that need a session",
                "ids": _ids_for_flows(login_flows),
            }
        )

    logged_out = []
    for name in _logged_out_names():
        logged_out.append(_shard(name, "", name))

    room = max_shards - len(shards)
    if room < 1:
        # Keep the login shards. Logged-out work still has to run: fold it into the last shard.
        if shards:
            shards[-1]["flows"].extend(flow for shard in logged_out for flow in shard["flows"])
        else:
            shards.append(
                {
                    "name": "logged-out",
                    "api": 34,
                    "phone": "",
                    "flows": [flow for shard in logged_out for flow in shard["flows"]],
                }
            )
    elif len(logged_out) <= room:
        shards.extend(logged_out)
    else:
        groups = [[] for _ in range(room)]
        for index, shard in enumerate(logged_out):
            groups[index % room].append(shard)
        for index, group in enumerate(groups):
            flows = [flow for shard in group for flow in shard["flows"]]
            shards.append(
                {
                    "name": group[0]["name"] if len(group) == 1 else f"logged-out-{index + 1}",
                    "api": group[0]["api"],
                    "phone": "",
                    "flows": flows,
                }
            )

    if len(shards) > max_shards:
        raise RuntimeError(f"planned {len(shards)} shards, cap is {max_shards}")

    # A skipped phone that was merged away should not list ids twice.
    claimed = set()
    for shard in shards:
        claimed.update(_ids_for_flows(shard["flows"]))
    for entry in skipped:
        if not entry["ids"]:
            continue
        entry["ids"] = [backlog_id for backlog_id in entry["ids"] if backlog_id not in claimed]
    skipped = [entry for entry in skipped if entry["ids"]]

    return {"shards": shards, "skipped": skipped}


def _shard(name: str, phone: str, catalog_shard: str) -> dict:
    spec = SHARDS[catalog_shard]
    return {
        "name": name,
        "api": spec["api"],
        "phone": phone,
        "flows": [flow["name"] for flow in FLOWS if flow["shard"] == catalog_shard],
    }


def _ids_for_flows(names: list[str]) -> list[str]:
    found = []
    for name in names:
        flow = FLOWS_BY_NAME[name]
        found.extend(flow.get("checks", {}))
        for backlog_id in flow.get("emits_parts", {}):
            if backlog_id not in found:
                found.append(backlog_id)
    return found
