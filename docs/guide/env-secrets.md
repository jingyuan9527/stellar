# Env & Secrets

- `application-local.yml` gitignored，存真实 DB/Redis 凭证，勿拷入 `application.yml`（占位符）
- DBX MCP 凭证在 DBX 存储 `%APPDATA%\com.dbx.app\dbx.db`，用 `dbx_add_connection` 添加，不写入 `opencode.json`
- 部署 `.env` gitignored，模板 `.env.example`；`REDIS_DATABASE` 必须显式
- 日志文件 `*.log` gitignored
