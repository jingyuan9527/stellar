# Stellar 数据库备份

`backup.sh` 用 `pg_dump`（自定义格式，原生压缩）备份 PostgreSQL 数据库到本地目录，
支持保留份数清理与可选的 rclone 异地备份。

## 前置条件

- 宿主机安装 `postgresql-client`（>= 13，提供 `pg_dump` / `pg_restore`）
- 异地备份需额外安装 [rclone](https://rclone.org/) 并配置好远程
- 备份机需能访问部署 .env 中的 PostgreSQL 实例（外部实例，非容器内）

## 快速开始

```bash
# 方式一：复用部署 .env 的连接（JDBC URL 自动解析）
set -a && source .env && set +a
BACKUP_DIR=/data/backups bash scripts/backup/backup.sh

# 方式二：标准 PG 环境变量
PGHOST=db.example.com PGPORT=5432 PGUSER=postgres PGPASSWORD='***' \
PGDATABASE=stellar BACKUP_DIR=/data/backups bash scripts/backup/backup.sh
```

输出：`BACKUP_DIR/stellar-YYYYmmdd-HHMMSS.dump`，默认保留最近 **14 份**（`KEEP` 可调），
超出自动清理。

### 异地备份（可选）

配置 rclone 远程后：

```bash
RCLONE_TARGET=backup-oss:stellar/backups bash scripts/backup/backup.sh
```

### 备份完整性校验（可选）

```bash
VALIDATE=1 bash scripts/backup/backup.sh   # 备份后跑 pg_restore --list 深度校验
```

## 定时任务（cron）

```cron
# 每天 03:15 备份，日志追加到 /var/log/stellar-backup.log（cron 邮件告警靠退出码）
15 3 * * * cd /opt/stellar && BACKUP_DIR=/data/backups bash scripts/backup/backup.sh >> /var/log/stellar-backup.log 2>&1
```

## 恢复

```bash
# 自定义格式：可直接 pg_restore 到目标库
pg_restore -h <host> -U <user> -d stellar -c stellar-20260814-031500.dump

# 或先落到 SQL 再 psql 执行
pg_restore -f restore.sql stellar-20260814-031500.dump
psql -h <host> -U <user> -d stellar -f restore.sql
```

## 与 docker-compose 的关系

备份在**宿主机**运行即可（PostgreSQL 本就是外部实例，`pg_dump` 通过网络连接）。
若宿主机不想装客户端，可改用一次性容器：

```bash
docker run --rm \
  -e PGHOST=db.example.com -e PGPORT=5432 -e PGUSER=postgres -e PGPASSWORD='***' \
  -e PGDATABASE=stellar -v /data/backups:/backups \
  postgres:16-alpine pg_dump -Fc -Z 6 -f /backups/stellar-$(date +%Y%m%d-%H%M%S).dump
```