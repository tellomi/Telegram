#!/usr/bin/env python3
"""The sheet ledger stays, and it does not override the merge gate."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def main() -> int:
    agents = read("AGENTS.md")
    rules = read("docs/TELLOMI_RULES.md")
    cursor = read(".cursor/rules/telegram-native-first.mdc")
    template = read(".github/pull_request_template.md")
    errors = []

    def need(text: str, needle: str, where: str) -> None:
        if needle not in text:
            errors.append(f"{where} missing {needle!r}")

    def forbid(text: str, needle: str, where: str) -> None:
        if needle in text:
            errors.append(f"{where} still contains {needle!r}")

    need(agents, "## 谷歌表格台账规则（强制）", "AGENTS.md")
    need(agents, "AE「备注」不是 S「备注/证据」", "AGENTS.md")
    need(agents, "L「状态」（禁改）", "AGENTS.md")
    need(agents, "T「修复跟踪状态」", "AGENTS.md")
    need(agents, "合并仍须用户在该 PR 里明确批准。", "AGENTS.md")
    need(agents, "没有这份批准不得合并。", "AGENTS.md")
    need(agents, "机器人的 review、CI 绿、自动复审 PASS 都不算这份批准。", "AGENTS.md")
    forbid(
        agents,
        "合并仍须独立审查 PASS、本仓 CI 全绿，且 head 等于审查 SHA。",
        "AGENTS.md",
    )
    need(rules, "谷歌表格台账规则（强制）", "docs/TELLOMI_RULES.md")
    need(cursor, "谷歌表格台账规则（强制）", ".cursor/rules")
    need(template, "7. **这次有没有改界面？**", "pull_request_template")
    need(template, "已在谷歌表格「问题清单」回写", "pull_request_template")

    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    print("ledger rules follow the current merge gate")
    return 0


if __name__ == "__main__":
    sys.exit(main())
