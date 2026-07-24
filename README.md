# Stellar

> 个人知识/实验沉淀池 —— 落地页 + 案例库 + 工具箱，游客免登录体验、登录后见管理后台。

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](./LICENSE)

Stellar 是一个前后端分离的个人作品站。游客可浏览落地页与案例库、体验所有工具（受 IP 单日限流保护）；登录后则进入管理后台。公开页与管理后台共用同一套布局，按登录态切换可见菜单。

## 功能特性

- **落地页 `/home`**：个人介绍 + 项目卡片导航 + 案例库入口，登录/游客皆可见。
- **案例库 `/showcase`**：统一作品表，响应式 CSS Grid + 类型化卡片 + 弹窗详情（移动端友好，不使用瀑布流）。
- **工具箱**：TTS 语音合成、AI 文案生成、封面画布编辑等；工具页可"存入橱窗"。
- **AI 模块**：内置模板（B站/抖音/小红书）、流式 SSE 对话、自带 AI 配置、token 消费统计与首页看板。
- **鉴权**：Sa-Token 统一拦截，`@PublicAccess` 注解放行游客方法，公开接口内按登录态差异化处理。
- **IP 单日限流**：纯 JDK 内存计数（无 Redis 依赖），耗资源接口超限返回 429。
- **操作日志**：AOP + `@Log` 注解采集，支持 Excel 导出。
- **可配置公开菜单**：后台勾选哪些路由对游客可见，前端拉取配置动态过滤。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 前端 | Vue 3 · Vite · TypeScript · Pinia · UnoCSS · Naive UI |
| 后端 | Spring Boot 3.3 · Java 21 · Maven · MyBatis-Plus · Sa-Token |
| 数据库 | PostgreSQL |
| 部署 | Docker · Docker Compose · Nginx |

## 项目结构

```
stellar/
├─ frontend/            # Vue3 前端（pnpm）
│  ├─ Dockerfile        # 多阶段构建：pnpm build -> nginx
│  ├─ nginx.conf        # SPA 托管 + /api /uploads 反代后端
│  └─ src/
├─ backend/             # Spring Boot 后端（Maven）
│  ├─ Dockerfile        # 多阶段构建：mvn package -> JRE
│  ├─ src/main/resources/
│  │  ├─ application.yml        # 主配置（环境变量占位）
│  │  ├─ schema.sql            # 全量建表（幂等 IF NOT EXISTS）
│  │  └─ db/init.sql           # 最小初始化脚本
│  └─ pom.xml
├─ docker-compose.yml   # 前后端编排（PostgreSQL 外置）
├─ .env.example         # 环境变量模板
└─ LICENSE              # Apache-2.0
```

## 快速部署（Docker Compose）

从源码构建镜像并一键启动前后端，PostgreSQL 使用外部实例。

### 1. 准备环境变量

```bash
cp .env.example .env
# 按实际填写 .env 中的数据库连接、限流阈值、前端端口
```

`.env` 已被 `.gitignore` 忽略，**切勿提交真实凭证**。

### 2. 初始化数据库（首次部署）

外部 PostgreSQL 需提前建库并执行建表脚本（脚本幂等，可重复执行）：

```bash
# 创建数据库（数据库名需与 DB_URL 中一致）
psql -h <host> -U <user> -c "CREATE DATABASE soybean;"

# 执行全量建表
psql -h <host> -U <user> -d soybean -f backend/src/main/resources/schema.sql
```

> 管理员账号由 `DataInitializer` 在应用首次启动时自动播种，无需手动插入。

### 3. 构建并启动

```bash
docker compose up -d --build
```

- 前端访问：`http://localhost:${FRONTEND_PORT:-80}`
- 后端默认仅容器内网可达（经前端 Nginx 代理）；如需直连调试，在 `docker-compose.yml` 的 `backend` 服务中取消注释 `ports`。

### 4. 默认账号

| 用户名 | 密码 |
| --- | --- |
| `admin` | `123456` |

首次登录后请及时修改。

## 环境变量

`backend` 容器通过 `env_file: .env` 加载下列变量（定义见 `.env.example`）：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/soybean` | PostgreSQL JDBC 连接串 |
| `DB_USERNAME` | `postgres` | 数据库用户名 |
| `DB_PASSWORD` | `postgres` | 数据库密码 |
| `SPRING_PROFILES_ACTIVE` | `default` | Spring profile（默认不自动执行建表脚本） |
| `RATE_LIMIT_DAILY` | `50` | IP 单日限流阈值 |
| `FRONTEND_PORT` | `80` | 前端 Nginx 宿主机端口 |
| `UPLOAD_DIR` | `/app/uploads` | 上传目录（由 compose 固定为卷挂载路径） |

## 本地开发

前后端作为两个独立进程运行：

**后端**（`backend/`）

```bash
mvn -q compile -DskipTests          # 编译检查
$env:SPRING_PROFILES_ACTIVE="local"; mvn spring-boot:run   # 启动于 :8080
```

> `local` profile 用于连接真实数据库，其凭证保存在 gitignored 的 `application-local.yml` 中。

**前端**（`frontend/`，包管理器为 pnpm）

```bash
pnpm install
pnpm dev          # 开发服务器 :5173，/api 代理到 :8080
pnpm typecheck    # 类型检查
pnpm build        # 生产构建（typecheck 门禁）
```

## 持久化

- `uploads` 命名卷：后端上传文件持久化，位于容器内 `/app/uploads`，Nginx 经 `/uploads/` 反代读取。
- 数据库：由外部 PostgreSQL 管理，本仓库不负责其持久化。

## 开源协议

[Apache License 2.0](./LICENSE) © Stellar Contributors
