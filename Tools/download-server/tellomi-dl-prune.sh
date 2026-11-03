#!/bin/bash
# Weekly R2 (backup line) retention for tellomi-updates:
#   <platform>/<version>/   keep the newest $DL_R2_KEEP versions (and always the one in latest.json)
#   <platform>/test/        delete objects older than $DL_TEST_KEEP_DAYS days
# Cron: 30 4 * * 1  (see /etc/cron.d/tellomi-dl). DRY_RUN=1 only prints.
set -uo pipefail
. /usr/local/lib/tellomi-dl-common.sh
dl_r2_env || { dl_log "ERROR no R2 credentials"; exit 1; }
run(){ if [ -n "${DRY_RUN:-}" ]; then dl_log "DRY: $*"; else "$@"; fi; }
for p in $DL_PLATFORMS; do
  cur=$(rclone cat "r2:$DL_BUCKET/$p/latest.json" 2>/dev/null | python3 /usr/local/lib/tellomi-dl-manifest.py release - 2>/dev/null || true)
  mapfile -t vers < <(rclone lsf --dirs-only "r2:$DL_BUCKET/$p/" 2>/dev/null | sed 's#/$##' | grep -vx test | sort -V)
  n=${#vers[@]}; drop=$(( n - DL_R2_KEEP ))
  for ((i=0; i<drop; i++)); do v=${vers[$i]}; [ "$v" = "$cur" ] && continue
    dl_log "$p: pruning release $v from R2"; run rclone purge "r2:$DL_BUCKET/$p/$v" -q; done
  if rclone lsf --dirs-only "r2:$DL_BUCKET/$p/" 2>/dev/null | grep -qx 'test/'; then
    dl_log "$p: pruning test builds older than ${DL_TEST_KEEP_DAYS}d"
    run rclone delete "r2:$DL_BUCKET/$p/test" --min-age "${DL_TEST_KEEP_DAYS}d" --rmdirs -q
  fi
done
dl_log "prune done"
