# Shared settings for the installer download pipeline (sourced by tellomi-dl-*.sh).
# Layout (same on the server and in R2, so www.tellomi.cn/dl/ and dl.tellomi.cn mirror each other):
#   <platform>/latest.json                 release manifest (platform = android | win | mac)
#   <platform>/<version>/<file>            release files
#   <platform>/test/<version>/<file>       test / pre-release builds (R2 only, pruned after 14 days)
DL_ROOT=${DL_ROOT:-/srv/tellomi-downloads}
DL_BUCKET=${DL_BUCKET:-tellomi-updates}
DL_PLATFORMS=${DL_PLATFORMS:-"android win mac"}
DL_PRIMARY_BASE=${DL_PRIMARY_BASE:-https://www.tellomi.cn/dl}
DL_FALLBACK_BASE=${DL_FALLBACK_BASE:-https://dl.tellomi.cn}
DL_R2_KEEP=${DL_R2_KEEP:-3}          # release versions kept per platform in R2
DL_TEST_KEEP_DAYS=${DL_TEST_KEEP_DAYS:-14}
dl_log(){ echo "$(date -Is) $*"; }
# R2 credentials: /opt/tellomi/secrets/updates-r2.env (R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY, R2_ENDPOINT)
# if present, else the teamgram container's TELLOMI_S3_* key (same key the media service uses).
dl_r2_env(){
  if [ -r /opt/tellomi/secrets/updates-r2.env ]; then . /opt/tellomi/secrets/updates-r2.env
  else local E; E=$(docker exec tellomi-teamgram-1 env) || return 1
    R2_ACCESS_KEY_ID=$(echo "$E" | sed -n 's/^TELLOMI_S3_ACCESS_KEY_ID=//p')
    R2_SECRET_ACCESS_KEY=$(echo "$E" | sed -n 's/^TELLOMI_S3_SECRET_ACCESS_KEY=//p')
    R2_ENDPOINT=$(echo "$E" | sed -n 's/^TELLOMI_S3_ENDPOINT=//p'); fi
  export RCLONE_CONFIG_R2_TYPE=s3 RCLONE_CONFIG_R2_PROVIDER=Cloudflare RCLONE_CONFIG_R2_NO_CHECK_BUCKET=true
  export RCLONE_CONFIG_R2_ACCESS_KEY_ID="$R2_ACCESS_KEY_ID" RCLONE_CONFIG_R2_SECRET_ACCESS_KEY="$R2_SECRET_ACCESS_KEY" RCLONE_CONFIG_R2_ENDPOINT="$R2_ENDPOINT"
  export RCLONE_RETRIES=5 RCLONE_LOW_LEVEL_RETRIES=10
}
