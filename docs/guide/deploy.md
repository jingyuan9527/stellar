# Deploy & Ops

## 镜像

单镜像多阶段：node22 构建 dist + maven temurin21 构建 jar → jlink 裁剪 musl JRE（`eclipse-temurin:21-jdk-alpine` jlink）→ alpine + Nginx + `entrypoint.sh`。

- `entrypoint.sh` PID1 托管双进程，崩溃自拉起，`docker stop` 信号转发；`JAVA_OPTS` 由 `.env` 注入（默认 `-XX:MaxRAMPercentage=60 -XX:MaxMetaspaceSize=256m`）
- 镜像 ~274MB；前后端构建合并单 stage 串行防 BuildKit 并发峰值；低内存加 swap
- Tomcat max 40 / Hikari 15/5 / Redis 池 4，见 `application.yml`

## CI

`.github/workflows/docker-build-push.yml`：push master / `v*` tag / 手动触发 → `ghcr.io/jingyuan9527/stellar`（latest + 版本 tag，GHA 缓存）。首次推包默认 private，需设 Public。

- 部署：`docker compose pull && up -d`，回滚指定 tag
- Nginx 安全头：`frontend/nginx.conf`（CSP/X-Frame-Options 等），`curl -I` 验收

## 安全部环境变量（.env）

| 变量 | 说明 | 生产建议 |
| --- | --- | --- |
| `CORS_ALLOWED_ORIGINS` | 前端源白名单（逗号分隔，禁通配） | 同源反代不触发 CORS，设为实际域名兜底跨域调试 |
| `TRUSTED_PROXIES` | 可信反代白名单（精确 IP 或 IPv4 CIDR） | 后端经 Docker 网桥访问时追加网段（如 `172.17.0.0/16`），否则限流按网关 IP 计数 |
| `RATE_LIMIT_DAILY` / `RATE_LIMIT_USER_DAILY` | 游客/登录用户单日限流默认配额 | 按需调整 |

配置错误会在启动日志暴露：`[CORS] 白名单源: [...]` 可验证 CORS 生效值。

## 备份

`scripts/backup/backup.sh`：`pg_dump -Fc`，支持 `KEEP / RCLONE_TARGET / VALIDATE`，连接用 `PGHOST...` 或 `DB_URL` 解析，见 `scripts/backup/README.md`。
