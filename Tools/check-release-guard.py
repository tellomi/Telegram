#!/usr/bin/env python3
"""Release guard for pull requests into master or release/android.

The check fails when any of the following is true. A head that is master,
or a normal fast-forward from master onto release/android, is allowed:
release/android currently matches master, and moving it forward with master
is not a rollback.

1. A 动态 code directory that still has files on the base is removed on
   the head. The directories are:

   - TMessagesProj/src/main/java/org/telegram/ui/Feed
   - TMessagesProj/src/main/java/org/telegram/messenger/feed
   - TMessagesProj/src/test/java/org/telegram/ui/Feed
   - TMessagesProj/src/test/java/org/telegram/messenger/feed

2. APP_VERSION_CODE or APP_VERSION_NAME in gradle.properties is lower
   than the same field on the base. An equal value is allowed.

3. The pull request replaces the base tree with an older state and drops
   changes accepted after the full UI restore (#111, 87f8c3f99, 14.0.8).
   That is either a head that is a strict ancestor of the base with a
   different tree, or a rewritten tree (including a merge that keeps the
   old tree) that deletes a marker the base still has:

   - TMessagesProj/src/main/java/org/telegram/messenger/TellomiMainTabs.java
     （发现导航、去掉搜索、隐藏导航栏）
   - TMessagesProj/src/main/java/org/telegram/ui/TellomiNavBarActivity.java
     （设置里的导航栏）
   - TMessagesProj/src/main/java/org/telegram/messenger/TellomiIntro.java
     （跳过欢迎页）
   - TMessagesProj/src/main/java/org/telegram/ui/TellomiLoginCountry.java
     （默认 +86）

Run `python3 Tools/check-release-guard.py --self-test` before using it.
"""

from __future__ import annotations

import argparse
import subprocess
import sys
import tempfile
from pathlib import Path

MOMENTS_DIRS = (
    "TMessagesProj/src/main/java/org/telegram/ui/Feed",
    "TMessagesProj/src/main/java/org/telegram/messenger/feed",
    "TMessagesProj/src/test/java/org/telegram/ui/Feed",
    "TMessagesProj/src/test/java/org/telegram/messenger/feed",
)

# Files added after #111 for changes the user accepted. If the base still
# has one and the head does not, the tree was replaced with an older state.
MARKERS = (
    "TMessagesProj/src/main/java/org/telegram/messenger/TellomiMainTabs.java",
    "TMessagesProj/src/main/java/org/telegram/ui/TellomiNavBarActivity.java",
    "TMessagesProj/src/main/java/org/telegram/messenger/TellomiIntro.java",
    "TMessagesProj/src/main/java/org/telegram/ui/TellomiLoginCountry.java",
)


def git(repo: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        ["git", *args],
        cwd=repo,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if check and result.returncode != 0:
        detail = (result.stderr or result.stdout).strip()
        raise SystemExit(f"git {' '.join(args)} failed: {detail}")
    return result


def rev_parse(repo: Path, rev: str) -> str:
    return git(repo, "rev-parse", "--verify", f"{rev}^{{commit}}").stdout.strip()


def tree_of(repo: Path, rev: str) -> str:
    return git(repo, "rev-parse", "--verify", f"{rev}^{{tree}}").stdout.strip()


def list_files(repo: Path, rev: str) -> list[str]:
    output = git(repo, "ls-tree", "-r", "--name-only", rev).stdout
    return [line for line in output.splitlines() if line]


def has_prefix(files: list[str], prefix: str) -> bool:
    needle = prefix.rstrip("/") + "/"
    return any(path.startswith(needle) for path in files)


def show_file(repo: Path, rev: str, path: str) -> str | None:
    result = git(repo, "show", f"{rev}:{path}", check=False)
    if result.returncode != 0:
        return None
    return result.stdout


def read_version(text: str, label: str) -> tuple[int, tuple[int, ...]]:
    code = None
    name = None
    for line in text.splitlines():
        if line.startswith("APP_VERSION_CODE="):
            code = line.split("=", 1)[1].strip()
        elif line.startswith("APP_VERSION_NAME="):
            name = line.split("=", 1)[1].strip()
    if code is None or not code.isdigit():
        raise SystemExit(f"APP_VERSION_CODE is not an integer on {label}")
    if not name:
        raise SystemExit(f"APP_VERSION_NAME is missing on {label}")
    parts = name.split(".")
    if not parts or any(not part.isdigit() for part in parts):
        raise SystemExit(f"APP_VERSION_NAME is not a numeric dotted version on {label}: {name}")
    return int(code), tuple(int(part) for part in parts)


def version_name_less(head: tuple[int, ...], base: tuple[int, ...]) -> bool:
    width = max(len(head), len(base))
    head_padded = head + (0,) * (width - len(head))
    base_padded = base + (0,) * (width - len(base))
    return head_padded < base_padded


def format_name(parts: tuple[int, ...]) -> str:
    return ".".join(str(part) for part in parts)


def is_ancestor(repo: Path, ancestor: str, descendant: str) -> bool:
    result = git(repo, "merge-base", "--is-ancestor", ancestor, descendant, check=False)
    if result.returncode == 0:
        return True
    if result.returncode == 1:
        return False
    detail = (result.stderr or result.stdout).strip()
    raise SystemExit(f"git merge-base --is-ancestor failed: {detail}")


def evaluate(repo: Path, base: str, head: str) -> list[str]:
    base_sha = rev_parse(repo, base)
    head_sha = rev_parse(repo, head)
    base_files = list_files(repo, base_sha)
    head_files = list_files(repo, head_sha)
    problems: list[str] = []

    for directory in MOMENTS_DIRS:
        if has_prefix(base_files, directory) and not has_prefix(head_files, directory):
            problems.append(f"动态代码目录被删除: {directory}")

    base_gradle = show_file(repo, base_sha, "gradle.properties")
    head_gradle = show_file(repo, head_sha, "gradle.properties")
    if base_gradle is None:
        raise SystemExit("gradle.properties is missing on the base")
    if head_gradle is None:
        problems.append("gradle.properties 在 head 上被删除，版本无法核对")
    else:
        base_code, base_name = read_version(base_gradle, "base")
        head_code, head_name = read_version(head_gradle, "head")
        if head_code < base_code:
            problems.append(
                f"APP_VERSION_CODE 倒退: head {head_code} < base {base_code}"
            )
        if version_name_less(head_name, base_name):
            problems.append(
                "APP_VERSION_NAME 倒退: "
                f"head {format_name(head_name)} < base {format_name(base_name)}"
            )

    for path in MARKERS:
        on_base = show_file(repo, base_sha, path) is not None
        on_head = show_file(repo, head_sha, path) is not None
        if on_base and not on_head:
            problems.append(f"#111 之后已认可的修改丢失: {path}")

    if (
        base_sha != head_sha
        and tree_of(repo, base_sha) != tree_of(repo, head_sha)
        and is_ancestor(repo, head_sha, base_sha)
    ):
        problems.append(
            "head 是 base 的旧提交，整棵树被换成更早的状态，"
            "#111（87f8c3f99，14.0.8）之后已认可的修改会丢失。"
            "从 master 正常向前推进不受这条影响。"
        )
    return problems


def write_tree(repo: Path, files: dict[str, str]) -> None:
    for relative, body in files.items():
        path = repo / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body)
        git(repo, "add", "--", relative)


def commit_all(repo: Path, message: str) -> str:
    git(repo, "commit", "-m", message)
    return rev_parse(repo, "HEAD")


def self_test() -> int:
    with tempfile.TemporaryDirectory() as tmp:
        repo = Path(tmp) / "repo"
        repo.mkdir()
        git(repo, "init", "-b", "main")
        git(repo, "config", "user.email", "release-guard@example.com")
        git(repo, "config", "user.name", "release-guard")

        def gradle(code: int, name: str) -> str:
            return f"APP_VERSION_CODE={code}\nAPP_VERSION_NAME={name}\n"

        old_files = {
            "gradle.properties": gradle(180467, "14.0.8"),
            "README": "old\n",
        }
        write_tree(repo, old_files)
        old = commit_all(repo, "old tree before accepted changes")

        base_files = {
            "gradle.properties": gradle(180476, "14.0.14"),
            "TMessagesProj/src/main/java/org/telegram/ui/Feed/FeedActivity.java": "class FeedActivity {}\n",
            "TMessagesProj/src/main/java/org/telegram/messenger/feed/FeedApi.java": "class FeedApi {}\n",
            "TMessagesProj/src/test/java/org/telegram/ui/Feed/FeedProfileTabsTest.java": "class T {}\n",
            "TMessagesProj/src/test/java/org/telegram/messenger/feed/FeedContractTest.java": "class T {}\n",
            "TMessagesProj/src/main/java/org/telegram/messenger/TellomiMainTabs.java": "class TellomiMainTabs {}\n",
            "TMessagesProj/src/main/java/org/telegram/ui/TellomiNavBarActivity.java": "class TellomiNavBarActivity {}\n",
            "TMessagesProj/src/main/java/org/telegram/messenger/TellomiIntro.java": "class TellomiIntro {}\n",
            "TMessagesProj/src/main/java/org/telegram/ui/TellomiLoginCountry.java": "class TellomiLoginCountry {}\n",
        }
        write_tree(repo, base_files)
        base = commit_all(repo, "base with accepted changes")

        kept = dict(base_files)
        kept["Notes.txt"] = "a normal change\n"
        write_tree(repo, kept)
        ahead = commit_all(repo, "normal change from master")
        assert evaluate(repo, base, ahead) == [], evaluate(repo, base, ahead)
        assert evaluate(repo, base, base) == []

        git(repo, "checkout", "-b", "drop-feed", base)
        git(repo, "rm", "-r", "TMessagesProj/src/main/java/org/telegram/ui/Feed")
        dropped = commit_all(repo, "delete the feed ui directory")
        drop_problems = evaluate(repo, base, dropped)
        assert any("动态代码目录被删除" in item and item.endswith("ui/Feed") for item in drop_problems), drop_problems

        git(repo, "checkout", "-b", "lower-version", base)
        write_tree(repo, {"gradle.properties": gradle(180475, "14.0.13")})
        lowered = commit_all(repo, "roll the version back")
        low_problems = evaluate(repo, base, lowered)
        assert any("APP_VERSION_CODE 倒退" in item for item in low_problems), low_problems
        assert any("APP_VERSION_NAME 倒退" in item for item in low_problems), low_problems

        git(repo, "checkout", "-b", "drop-marker", base)
        git(repo, "rm", "TMessagesProj/src/main/java/org/telegram/ui/TellomiLoginCountry.java")
        missing = commit_all(repo, "drop the +86 marker")
        marker_problems = evaluate(repo, base, missing)
        assert any("TellomiLoginCountry.java" in item for item in marker_problems), marker_problems

        old_problems = evaluate(repo, base, old)
        assert any("旧提交" in item for item in old_problems), old_problems
        assert any("APP_VERSION_CODE 倒退" in item for item in old_problems), old_problems
        assert any("动态代码目录被删除" in item for item in old_problems), old_problems

        git(repo, "checkout", "-b", "ours", old)
        git(repo, "merge", "-s", "ours", base, "-m", "replace the tree with the old one")
        ours = rev_parse(repo, "HEAD")
        ours_problems = evaluate(repo, base, ours)
        assert any("动态代码目录被删除" in item for item in ours_problems), ours_problems
        assert any("APP_VERSION_CODE 倒退" in item for item in ours_problems), ours_problems
        assert not any("旧提交" in item for item in ours_problems), ours_problems

        git(repo, "checkout", "-b", "same-version", base)
        same = dict(base_files)
        same["gradle.properties"] = gradle(180476, "14.0.14")
        same["Extra.java"] = "class Extra {}\n"
        write_tree(repo, same)
        same_head = commit_all(repo, "keep the version")
        assert evaluate(repo, base, same_head) == []

        higher = dict(base_files)
        higher["gradle.properties"] = gradle(180477, "14.0.15")
        write_tree(repo, higher)
        higher_head = commit_all(repo, "bump the version")
        assert evaluate(repo, base, higher_head) == []

        one_file = dict(base_files)
        del one_file["TMessagesProj/src/main/java/org/telegram/ui/Feed/FeedActivity.java"]
        one_file["TMessagesProj/src/main/java/org/telegram/ui/Feed/FeedListView.java"] = "class FeedListView {}\n"
        git(repo, "rm", "TMessagesProj/src/main/java/org/telegram/ui/Feed/FeedActivity.java")
        write_tree(repo, one_file)
        edited = commit_all(repo, "edit inside the feed directory")
        assert evaluate(repo, base, edited) == [], evaluate(repo, base, edited)

    print("release-guard self-test: ok")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Fail PRs that roll the Android release tree back.")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--base")
    parser.add_argument("--head")
    args = parser.parse_args(argv)
    if args.self_test:
        return self_test()
    if not args.base or not args.head:
        raise SystemExit("pass --base and --head, or --self-test")
    problems = evaluate(args.repo.resolve(), args.base, args.head)
    if problems:
        print("release-guard: fail", file=sys.stderr)
        for item in problems:
            print(f"- {item}", file=sys.stderr)
        return 1
    print("release-guard: ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
