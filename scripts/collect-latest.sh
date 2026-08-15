#!/usr/bin/env bash
# LoL Data Hub 自动采集：从官网同步"当前进行中赛段"的最新数据。
# 由 cron 定时调用（默认每周三/四/五/六/日 09:00；crontab -e 可调整时间）。
# 所有凭据仅从服务器 .env 读取；日志只记录状态摘要，绝不输出令牌值。
set -u
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

PROJECT_DIR="${PROJECT_DIR:-/opt/lol-datahub-v2}"
DEPLOY_DIR="$PROJECT_DIR/deploy"
ENV_FILE="$PROJECT_DIR/.env"
LOG_DIR="$PROJECT_DIR/logs"
BACKEND_CONTAINER="lol-datahub-backend"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/collect-$(date +%Y%m%d).log"

log() { printf '[%s] %s\n' "$(date '+%F %T')" "$*" >> "$LOG_FILE"; }
fail() { log "ERROR: $1"; log "采集中止；已有数据保持不变。"; exit 1; }

# 防并发：上一次未跑完时本次直接退出
exec 9>"$LOG_DIR/.collect.lock"
if ! flock -n 9; then log "另一次采集仍在进行，本次退出。"; exit 0; fi

get_env() { grep -E "^$1=" "$ENV_FILE" | head -n1 | cut -d= -f2- | tr -d '\r'; }

BACKEND_PORT="$(get_env BACKEND_PORT)"
[ -n "$BACKEND_PORT" ] || BACKEND_PORT="18080"
API_BASE="http://127.0.0.1:$BACKEND_PORT"
INTERNAL_API_TOKEN="$(get_env INTERNAL_API_TOKEN)"
MYSQL_ROOT_PASSWORD="$(get_env MYSQL_ROOT_PASSWORD)"
MYSQL_DATABASE="$(get_env MYSQL_DATABASE)"
[ -n "$MYSQL_DATABASE" ] || MYSQL_DATABASE="lol_data_hub"

[ -n "$INTERNAL_API_TOKEN" ] || fail ".env 中缺少 INTERNAL_API_TOKEN"
[ -n "$MYSQL_ROOT_PASSWORD" ] || fail ".env 中缺少 MYSQL_ROOT_PASSWORD"

log "===== 采集开始 ====="

# 1) 从官网脚本现场提取最新 Authorization（不打印其值）
# 官网曾多次调整脚本结构，依次尝试多个候选文件，任一命中即使用
NEW_AUTH=""
for AUTH_SOURCE in \
  "https://lpl.qq.com/web202301/js/common.js" \
  "https://lpl.qq.com/web202301/js/rank.js" \
  "https://lpl.qq.com/web202301/js/public.js"
do
  PAGE_JS="$(curl -fsS --connect-timeout 15 --max-time 60 "$AUTH_SOURCE")" || continue
  NEW_AUTH="$(printf '%s' "$PAGE_JS" | grep -oiE "[\"']?authorization[\"']?[[:space:]]*:[[:space:]]*[\"'][^\"']+[\"']" | head -n1 | sed -E "s/.*:[[:space:]]*[\"']([^\"']+)[\"'].*/\1/")"
  if [ -n "$NEW_AUTH" ]; then
    log "Authorization 提取成功（来源 $AUTH_SOURCE，长度 ${#NEW_AUTH}，不记录内容）"
    break
  fi
done
[ -n "$NEW_AUTH" ] || fail "官网脚本中未找到 Authorization（官网页面结构可能已变化）"
case "$NEW_AUTH" in *"|"*|*$'\n'*) fail "提取到的 Authorization 含非法字符" ;; esac

# 2) 更新 .env 并重建 backend 使新凭据生效
if grep -qE '^TJSTATS_AUTHORIZATION=' "$ENV_FILE"; then
  sed -i "s|^TJSTATS_AUTHORIZATION=.*|TJSTATS_AUTHORIZATION=$NEW_AUTH|" "$ENV_FILE" || fail "更新 .env 失败"
else
  printf 'TJSTATS_AUTHORIZATION=%s\n' "$NEW_AUTH" >> "$ENV_FILE" || fail "写入 .env 失败"
fi

cd "$DEPLOY_DIR" || fail "部署目录不存在: $DEPLOY_DIR"
docker compose --env-file ../.env up -d --force-recreate backend >> "$LOG_FILE" 2>&1 || fail "重建 backend 失败"

healthy=""
for _ in $(seq 1 36); do
  status="$(docker inspect -f '{{.State.Health.Status}}' "$BACKEND_CONTAINER" 2>/dev/null || true)"
  if [ "$status" = "healthy" ]; then healthy=1; break; fi
  sleep 5
done
[ -n "$healthy" ] || fail "backend 重建后未就绪（180 秒超时）"
log "backend 已使用新凭据重启并就绪"

# 3) 同步官网赛季/赛段目录
# 不传赛季时自动同步所有"进行中"赛季；再对数据库里最新的赛季带参同步一次，
# 保证新赛季/新赛段开放后目录能自动跟上
query_db() {
  docker compose --env-file ../.env exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B "$MYSQL_DATABASE" -e "$1" 2>/dev/null
}
SYNC_RESP="$(curl -fsS --max-time 300 -X POST "$API_BASE/api/internal/catalog/sync" -H "X-Internal-Token: $INTERNAL_API_TOKEN")" \
  || fail "目录同步请求失败"
printf '%s' "$SYNC_RESP" | grep -q '"success":true' || fail "目录同步返回失败"
SYNC_STAGES="$(printf '%s' "$SYNC_RESP" | grep -oE '"stageCount":[0-9]+' | head -n1 | cut -d: -f2)"
log "目录同步完成（进行中赛季同步赛段数: ${SYNC_STAGES:-0}）"

LATEST_SEASON="$(query_db "SELECT MAX(source_season_id) FROM stage;")"
if [ -n "$LATEST_SEASON" ]; then
  SYNC_LATEST="$(curl -fsS --max-time 300 -X POST "$API_BASE/api/internal/catalog/sync?seasonId=$LATEST_SEASON" -H "X-Internal-Token: $INTERNAL_API_TOKEN")" \
    || fail "最新赛季目录同步请求失败（赛季 $LATEST_SEASON）"
  printf '%s' "$SYNC_LATEST" | grep -q '"success":true' || fail "最新赛季目录同步返回失败（赛季 $LATEST_SEASON）"
  LATEST_STAGES="$(printf '%s' "$SYNC_LATEST" | grep -oE '"stageCount":[0-9]+' | head -n1 | cut -d: -f2)"
  log "最新赛季 $LATEST_SEASON 目录同步完成（赛段数: ${LATEST_STAGES:-0}）"
fi

# 4) 选出当前进行中的赛段；休赛期则退化为最近 14 天内结束的赛段
STAGE_ROWS="$(query_db "SELECT source_season_id, source_stage_id FROM stage WHERE start_time IS NOT NULL AND start_time <= NOW() AND (end_time IS NULL OR end_time >= NOW()) ORDER BY source_season_id, source_stage_id;")"
if [ -z "$STAGE_ROWS" ]; then
  STAGE_ROWS="$(query_db "SELECT source_season_id, source_stage_id FROM stage WHERE end_time IS NOT NULL AND end_time >= NOW() - INTERVAL 14 DAY ORDER BY source_season_id, source_stage_id;")"
  [ -n "$STAGE_ROWS" ] && log "当前无进行中赛段，改为采集最近 14 天内结束的赛段"
fi
if [ -z "$STAGE_ROWS" ]; then
  log "没有需要采集的赛段（休赛期），本次结束。"
  exit 0
fi

# 5) 按赛季分组，依次采集 heroes → teams → players → match-games；任一步失败即中止
# match-games 只消费 players 采集新保存的原始响应，把新增比赛写入对局赛果表，不重复请求官网
collect_one() { # kind seasonId stageIdsCsv
  local kind="$1" sid="$2" ids="$3" body resp status run_id changed
  body="{\"seasonId\":$sid,\"stageIds\":[$ids]}"
  resp="$(curl -sS --max-time 3600 -X POST "$API_BASE/api/internal/collections/$kind" \
    -H "X-Internal-Token: $INTERNAL_API_TOKEN" -H 'Content-Type: application/json' -d "$body")" \
    || fail "$kind 采集请求失败（赛季 $sid）"
  printf '%s' "$resp" | grep -q '"success":true' || fail "$kind 采集返回失败（赛季 $sid）"
  status="$(printf '%s' "$resp" | grep -oE '"status":"[A-Z_]+"' | head -n1 | cut -d'"' -f4)"
  run_id="$(printf '%s' "$resp" | grep -oE '"runId":[0-9]+' | head -n1 | cut -d: -f2)"
  changed="$(printf '%s' "$resp" | grep -oE '"changedRecords":[0-9]+' | head -n1 | cut -d: -f2)"
  log "$kind 赛季 $sid 赛段 [$ids] -> runId=${run_id:-?} status=${status:-?} changed=${changed:-?}"
  case "$status" in SUCCESS|NO_CHANGE) ;; *) fail "$kind 采集状态异常: ${status:-未知}（赛季 $sid）" ;; esac
}

current_season=""
ids=""
flush() {
  [ -n "$current_season" ] || return 0
  local kind
  for kind in heroes teams players match-games; do collect_one "$kind" "$current_season" "$ids"; done
}
while IFS="$(printf '\t')" read -r sid stage_id; do
  [ -n "$sid" ] || continue
  if [ "$sid" != "$current_season" ]; then
    flush
    current_season="$sid"
    ids="$stage_id"
  else
    ids="$ids,$stage_id"
  fi
done <<EOF
$STAGE_ROWS
EOF
flush

# 6) 汇总与日志清理（保留最近 30 天）
DATA_VERSION="$(query_db "SELECT data_version FROM system_state LIMIT 1;")"
log "采集完成，当前 data_version=${DATA_VERSION:-未知}"
find "$LOG_DIR" -name 'collect-*.log' -mtime +30 -delete 2>/dev/null || true
log "===== 采集结束 ====="
