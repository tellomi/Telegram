#!/bin/bash
# Pulls the current release of each platform from R2 (tellomi-updates/<platform>/latest.json)
# onto this server, which is the PRIMARY download (served as https://www.tellomi.cn/dl/ by nginx).
# Keeps only the current release locally; R2 keeps the last 3 as the backup line.
# Also writes $DL_ROOT/versions.json for the www.tellomi.cn/download page.
# Cron: */10 * * * *  (see /etc/cron.d/tellomi-dl)
set -uo pipefail
. /usr/local/lib/tellomi-dl-common.sh
exec 9>/run/tellomi-dl.lock; flock -n 9 || exit 0
dl_r2_env || { dl_log "ERROR no R2 credentials"; exit 1; }
mkdir -p "$DL_ROOT"; rc=0
for p in $DL_PLATFORMS; do
  tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
  if ! rclone copyto "r2:$DL_BUCKET/$p/latest.json" "$tmp/latest.json" -q 2>/dev/null || [ ! -s "$tmp/latest.json" ]; then
    rm -rf "$tmp"; continue; fi            # platform not published yet
  if cmp -s "$tmp/latest.json" "$DL_ROOT/$p/latest.json" && [ -z "${FORCE:-}" ]; then
    # same release: make sure files are still there
    ok=1; while IFS=$'\t' read -r path sha size; do [ -s "$DL_ROOT/$path" ] || ok=0; done < <(python3 /usr/local/lib/tellomi-dl-manifest.py files "$tmp/latest.json")
    [ $ok = 1 ] && { rm -rf "$tmp"; continue; }
  fi
  ver=$(python3 /usr/local/lib/tellomi-dl-manifest.py release "$tmp/latest.json") || { dl_log "ERROR $p: bad manifest"; rc=1; rm -rf "$tmp"; continue; }
  dl_log "$p: syncing release $ver"
  stage="$DL_ROOT/.stage-$p"; rm -rf "$stage"; mkdir -p "$stage"
  good=1
  while IFS=$'\t' read -r path sha size; do
    mkdir -p "$stage/$(dirname "$path")"
    if [ -s "$DL_ROOT/$path" ] && [ "$(sha256sum "$DL_ROOT/$path" | cut -d' ' -f1)" = "$sha" ]; then
      cp -l "$DL_ROOT/$path" "$stage/$path" 2>/dev/null || cp "$DL_ROOT/$path" "$stage/$path"
    else
      rclone copyto "r2:$DL_BUCKET/$path" "$stage/$path" -q --transfers 1 || { dl_log "ERROR $p: download $path failed"; good=0; break; }
    fi
    got=$(sha256sum "$stage/$path" | cut -d' ' -f1)
    [ "$got" = "$sha" ] || { dl_log "ERROR $p: sha256 mismatch for $path ($got != $sha)"; good=0; break; }
  done < <(python3 /usr/local/lib/tellomi-dl-manifest.py files "$tmp/latest.json")
  if [ $good = 1 ]; then
    mkdir -p "$DL_ROOT/$p"
    for d in "$stage/$p"/*/; do n=$(basename "$d"); rm -rf "$DL_ROOT/$p/$n.new"; mv "$d" "$DL_ROOT/$p/$n.new"; rm -rf "$DL_ROOT/$p/$n"; mv "$DL_ROOT/$p/$n.new" "$DL_ROOT/$p/$n"; done
    install -m 644 "$tmp/latest.json" "$DL_ROOT/$p/latest.json.new" && mv "$DL_ROOT/$p/latest.json.new" "$DL_ROOT/$p/latest.json"
    # keep only the current release on the server
    for d in "$DL_ROOT/$p"/*/; do [ -d "$d" ] || continue; n=$(basename "$d"); [ "$n" = "$ver" ] || { rm -rf "$d"; dl_log "$p: removed local $n"; }; done
    dl_log "$p: now serving $ver"
  else rc=1; fi
  rm -rf "$stage" "$tmp"
done
python3 /usr/local/lib/tellomi-dl-manifest.py website "$DL_ROOT" > "$DL_ROOT/versions.json.new" && mv "$DL_ROOT/versions.json.new" "$DL_ROOT/versions.json"
chmod -R a+rX "$DL_ROOT"
exit $rc
