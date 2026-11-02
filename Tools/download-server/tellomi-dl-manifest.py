#!/usr/bin/env python3
"""Helpers for tellomi installer manifests (<platform>/latest.json).

Manifest (written by the publisher, Tools/tellomi-publish-installer.sh in tellomi/Telegram):
{
  "platform": "android", "versionName": "14.0.4", "versionCode": 1800979,
  "releaseDate": "...Z", "uploadTimestamp": <ms>,
  "url": "https://www.tellomi.cn/dl/android/14.0.4/Tellomi.apk", # primary (Chengdu server)
  "fallbackUrl": "https://dl.tellomi.cn/android/14.0.4/Tellomi.apk", # backup (R2)
  "sha256sum": "...", "size": 123,
  "files": [{"path": "android/14.0.4/Tellomi.apk", "arch": "universal", "kind": "apk",
             "sha256": "...", "size": 123, "url": "...", "fallbackUrl": "..."}]
}
Old manifests without "files" are read through url/sha256sum.
"""
import json, os, sys, datetime
from urllib.parse import urlparse

def load(p):
    return json.load(sys.stdin if p == "-" else open(p))

def files(m):
    fs = m.get("files")
    if not fs:
        fs = [{"path": urlparse(m["url"]).path.lstrip("/"), "sha256": m.get("sha256sum", ""), "size": m.get("size", 0)}]
    for f in fs:
        path = f["path"]
        if path.startswith("/") or ".." in path.split("/"):
            raise SystemExit("unsafe path " + path)
        yield path, f.get("sha256", ""), f.get("size", 0)

def website(root):
    """versions.json for the www.tellomi.cn/download page (same schema the page already reads)."""
    out = {}
    def rd(p):
        fp = os.path.join(root, p, "latest.json")
        return json.load(open(fp)) if os.path.exists(fp) else None
    a = rd("android")
    if a:
        apks = []
        for f in a.get("files") or [{"arch": "universal", "url": a["url"], "sha256": a.get("sha256sum"), "size": a.get("size")}]:
            size = f.get("size") or 0
            if not size:
                lp = os.path.join(root, urlparse(f["url"]).path.lstrip("/"))
                size = os.path.getsize(lp) if os.path.exists(lp) else 0
            apks.append({"arch": f.get("arch", "universal"), "url": f["url"], "sha256": f.get("sha256"), "size": size})
        rd_ = a.get("releaseDate") or (datetime.datetime.fromtimestamp(a["uploadTimestamp"] / 1000, datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ") if a.get("uploadTimestamp") else "")
        out["android"] = {"version": a.get("versionName"), "versionCode": a.get("versionCode"),
                          "releaseDate": rd_, "apk": apks,
                          "updateFeed": a.get("feedUrl", "https://www.tellomi.cn/dl/android/latest.json")}
    mac, win = rd("mac"), rd("win")
    if mac or win:
        d = {}
        if mac:
            d.update(version=mac.get("versionName"), releaseDate=mac.get("releaseDate", ""))
            d["mac"] = [{"arch": f.get("arch"), "kind": f.get("kind"), "url": f["url"], "sha256": f.get("sha256"), "size": f.get("size")} for f in mac.get("files", [])]
        if win:
            d["win"] = [{"arch": f.get("arch"), "kind": f.get("kind"), "url": f["url"], "sha256": f.get("sha256"), "size": f.get("size")} for f in win.get("files", [])]
            d.setdefault("version", win.get("versionName")); d.setdefault("releaseDate", win.get("releaseDate", ""))
        out["desktop"] = d
    return out

cmd = sys.argv[1]
if cmd == "files":
    for path, sha, size in files(load(sys.argv[2])):
        print(f"{path}\t{sha}\t{size}")
elif cmd == "release":
    # release directory name: <platform>/<release>/<file>, e.g. android/14.0.4-1800989
    path = next(files(load(sys.argv[2])))[0]
    parts = path.split("/")
    if len(parts) != 3:
        raise SystemExit("release files must be <platform>/<release>/<file>: " + path)
    print(parts[1])
elif cmd == "website":
    print(json.dumps(website(sys.argv[2]), ensure_ascii=False, indent=1))
