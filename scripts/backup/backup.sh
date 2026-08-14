#!/usr/bin/env bash
# =============================================================================
# Stellar 数据库自动化备份脚本
# -----------------------------------------------------------------------------
# 依赖：pg_dump / pg_restore（postgresql-client >= 13，支持 -Fc 自定义格式）
#       可选：rclone（启用异地备份时）
#
# 用法：
#   bash scripts/backup/backup.sh
#
# 连接配置（二选一）：
#   1) 标准 PG 环境变量：PGHOST PGPORT PGUSER PGPASSWORD PGDATABASE
#   2) 直接复用部署 .env 的 DB_URL（jdbc:postgresql://host:port/dbname），
#      配合 DB_USERNAME / DB_PASSWORD
#
# 可选环境变量：
#   BACKUP_DIR     备份输出目录（默认 ./backups）
#   KEEP           本地保留的最新份数，超出自动清理（默认 14）
#   RCLONE_TARGET  rclone 远程目标（如 remote:stellar-backups），非空则备份后复制异地
#   VALIDATE       非空启用备份完整性校验（pg_restore --list，默认关闭）
#
# 退出码：0 成功；非 0 失败（便于 crontab 邮件告警）
# =============================================================================
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-./backups}"
KEEP="${KEEP:-14}"
DB_URL="${DB_URL:-}"
RCLONE_TARGET="${RCLONE_TARGET:-}"
VALIDATE="${VALIDATE:-}"
STAMP="$(date +%Y%m%d-%H%M%S)"
DUMP_FILE="$BACKUP_DIR/stellar-${STAMP}.dump"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

# ---- 解析连接参数 ----------------------------------------------------------
if [ -z "${PGHOST:-}" ] && [ -n "$DB_URL" ]; then
  # jdbc:postgresql://host:port/dbname  → 标准 PG 环境变量
  export PGHOST="$(printf '%s' "$DB_URL" | sed -E 's#^jdbc:postgresql://([^:/]+).*#\1#')"
  if printf '%s' "$DB_URL" | grep -qE '^jdbc:postgresql://[^/:]+:[0-9]+/'; then
    export PGPORT="${PGPORT:-$(printf '%s' "$DB_URL" | sed -E 's#^jdbc:postgresql://[^/:]+:([0-9]+)/.*#\1#')}"
  fi
  export PGPORT="${PGPORT:-5432}"
  export PGDATABASE="$(printf '%s' "$DB_URL" | sed -E 's#^jdbc:postgresql://[^?/]+/([^?]+).*#\1#')"
fi
export PGPASSWORD="${PGPASSWORD:-${DB_PASSWORD:-}}"
export PGUSER="${PGUSER:-${DB_USERNAME:-}}"
: "${PGHOST:?需要 PGHOST 或 DB_URL}"
: "${PGDATABASE:?需要 PGDATABASE 或 DB_URL}"
: "${PGUSER:?需要 PGUSER 或 DB_USERNAME}"

# ---- 执行备份 --------------------------------------------------------------
mkdir -p "$BACKUP_DIR"
log "开始备份 ${PGDATABASE}@${PGHOST}:${PGPORT:-5432} → ${DUMP_FILE}"
pg_dump -Fc -Z 6 --no-owner --no-privileges -f "$DUMP_FILE"

# 简单完整性：文件非空
if [ ! -s "$DUMP_FILE" ]; then
  log "ERROR：备份文件为空"
  rm -f "$DUMP_FILE"
  exit 1
fi

# 可选深度校验
if [ -n "$VALIDATE" ]; then
  log "校验备份文件…"
  pg_restore --list "$DUMP_FILE" > /dev/null
fi

# ---- 异地备份（可选）-------------------------------------------------------
if [ -n "$RCLONE_TARGET" ]; then
  log "复制到 rclone 远程 ${RCLONE_TARGET}/"
  rclone copy "$DUMP_FILE" "$RCLONE_TARGET/"
fi

# ---- 清理旧备份（本地保留 KEEP 份）----------------------------------------
COUNT="$(ls -1t "$BACKUP_DIR"/stellar-*.dump 2>/dev/null | wc -l)"
if [ "$COUNT" -gt "$KEEP" ]; then
  OLDEST="$(ls -1t "$BACKUP_DIR"/stellar-*.dump | tail -n +$((KEEP + 1)))"
  for f in $OLDEST; do
    rm -f "$f"
    log "清理旧备份：$f"
  done
fi

log "备份完成：$(du -h "$DUMP_FILE" | cut -f1)"