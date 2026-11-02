# Installer download pipeline (server side)

Installed on the Chengdu server (8.137.39.82) on 2026-11-02:

| file | installed at |
|---|---|
| `tellomi-dl-common.sh` | `/usr/local/lib/tellomi-dl-common.sh` |
| `tellomi-dl-manifest.py` | `/usr/local/lib/tellomi-dl-manifest.py` |
| `tellomi-dl-sync.sh` | `/usr/local/bin/tellomi-dl-sync.sh` (every 10 min) |
| `tellomi-dl-prune.sh` | `/usr/local/bin/tellomi-dl-prune.sh` (Mondays 04:30) |
| `tellomi-dl.cron` | `/etc/cron.d/tellomi-dl` |

Flow:

1. `Tools/tellomi-publish-installer.sh` (the `publish` job in `.github/workflows/android-apk.yml`) uploads
   `<platform>/<versionName>-<versionCode>/<file>` and then `<platform>/latest.json` to R2 `tellomi-updates`.
2. `tellomi-dl-sync.sh` on the server sees the new `latest.json`, downloads the files, checks sha256 and serves
   them from `/srv/tellomi-downloads` at **https://www.tellomi.cn/dl/** (primary, ICP-filed domain). Only the
   current release stays on the server. It also writes `/srv/tellomi-downloads/versions.json`, which nginx
   serves as `https://www.tellomi.cn/versions.json` for the download page.
3. R2 is the backup line: **https://dl.tellomi.cn/** (also `updates.tellomi.app`), same paths. The newest
   3 releases per platform are kept; `<platform>/test/` builds are deleted after 14 days.

nginx: `/etc/nginx/snippets/tellomi-cn-www.conf` has `location = /versions.json` (local file) and
`location ^~ /dl/ { alias /srv/tellomi-downloads/; }`.

Manifest fields: `versionName`, `versionCode`, `url` (primary), `fallbackUrl` (R2), `sha256sum`, `size`,
`feedUrl` / `fallbackFeedUrl`, and `files[]` (one entry per file, for multi-file Mac/Windows releases).
