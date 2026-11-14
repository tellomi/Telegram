#!/usr/bin/env python3
"""Write the shard plan. Phone numbers are not printed."""

from __future__ import annotations

import json
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from lib.plan import plan_run  # noqa: E402


def main() -> int:
    plan = plan_run(
        max_shards=int(os.environ.get("MAX_SHARDS", "20")),
        scope=os.environ.get("SCOPE", "all"),
        phone_a=os.environ.get("TELLOMI_TEST_PHONE_A"),
        phone_b=os.environ.get("TELLOMI_TEST_PHONE_B"),
    )
    text = json.dumps(plan, ensure_ascii=False, indent=2) + "\n"
    destination = os.environ.get("PLAN_OUT")
    if destination:
        Path(destination).write_text(text, encoding="utf-8")
    else:
        sys.stdout.write(text)
    names = [shard["name"] + "@" + str(shard["api"]) for shard in plan["shards"]]
    print("shards: " + ", ".join(names), file=sys.stderr)
    print(f"skipped groups: {len(plan['skipped'])}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
