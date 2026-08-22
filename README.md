# Stellar

> 个人知识/实验沉淀池 —— 落地页 + 关于我 + 工具箱，游客免登录体验、登录后见管理后台。

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](./LICENSE)

Stellar 是一个前后端分离的个人作品站。游客可浏览落地页与关于我、体验所有工具（受 IP 单日限流保护）；登录后则进入管理后台。公开页与管理后台共用同一套布局，按登录态切换可见菜单。

## 功能特性

- **落地页 `/home`**：精简门户，hero 简介 + 公开工具导航 + "关于我"入口，登录/游客皆可见。
- **关于我 `/about`**：简历式个人主页（公开），Hero + 富文本"关于我" + 技能 + 联系方式。
- **工具箱**：TTS 语音合成、AI 文案生成、封面画布编辑等。
- **AI 模块**：内置模板（B站/抖音/小红书）、流式 SSE 对话、自带 AI 配置、token 消费统计与首页看板。
- **备忘同步**：全量拉取 Memos 笔记备份到本地（远端删除仅标记不删数据）、AI 打标签（笔记含链接时自动抓取网页内容辅助打标）、标签写回远端（`#标签` 追加到 content 末尾）。
- **鉴权**：Sa-Token 统一拦截，`@PublicAccess` 注解放行游客方法，公开接口内按登录态差异化处理。
- **IP 单日限流**：基于 Redis 计数（多实例共享），耗资源接口超限返回 429。
- **操作日志**：AOP + `@Log` 注解采集，支持 Excel 导出。
- **可配置公开菜单**：后台勾选哪些路由对游客可见，前端拉取配置动态过滤。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 前端 | Vue 3 · Vite · TypeScript · Pinia · UnoCSS · Naive UI |
| 后端 | Spring Boot 3.3 · Java 21 · Maven · MyBatis-Plus · Sa-Token |
| 数据库 | PostgreSQL |
| 缓存/会话 | Redis（Sa-Token 持久化 + IP 限流 + Spring Cache） |
| 部署 | Docker · Docker Compose · Nginx |

## 项目结构

```
stellar/
├─ frontend/            # Vue3 前端（pnpm）
│  ├─ nginx.conf        # SPA 托管 + /api /file 反代后端
│  └─ src/
├─ backend/             # Spring Boot 后端（Maven）
│  ├─ src/main/resources/
│  │  ├─ application.yml        # 主配置（环境变量占位）
│  │  ├─ schema.sql            # 全量建表（幂等 IF NOT EXISTS）
│  │  └─ db/init.sql           # 最小初始化脚本
│  └─ pom.xml
├─ Dockerfile           # 单镜像多阶段构建：前端 dist + 后端 jar -> alpine(nginx+entrypoint.sh, jlink 裁剪 JRE)
├─ entrypoint.sh        # 双进程托管：nginx + java（替代 supervisor，自动重启+优雅退出）
├─ docker-compose.yml   # 单容器编排（PostgreSQL 外置）
├─ .env.example         # 环境变量模板
└─ LICENSE              # Apache-2.0
```

## 快速部署（Docker Compose）

从源码构建单镜像（Nginx + Spring Boot 双进程）并一键启动，PostgreSQL 使用外部实例。

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

# 执行全量建表（schema.sql 路径见下方「部署」：方式 A 用仓库内文件，方式 B/C 需先下载）
psql -h <host> -U <user> -d soybean -f <schema.sql 路径>
```

> 管理员账号由 `DataInitializer` 在应用首次启动时自动播种，无需手动插入。

### 3. 部署

三种方式任选其一：

| 方式 | 适用人群 | 特点 |
| --- | --- | --- |
| **A. 源码构建** | 想改代码/自定义配置 | 服务器需能访问 GitHub、Docker Hub |
| **B. 官方镜像**（推荐） | 直接部署 | 零构建负担，服务器无需源码与工具链 |
| **C. Fork 自建镜像** | 想维护自己的版本 | 镜像推送到你自己的 ghcr 空间 |

#### 方式 A：拉取源码，服务器本地构建

```bash
git clone https://github.com/jingyuan9527/stellar.git
cd stellar
cp .env.example .env        # 填写 .env 中数据库连接等（见「环境变量」）
docker compose up -d --build
```

- 数据库初始化：建库 + 执行仓库内 `backend/src/main/resources/schema.sql`（见上）
- 低内存服务器（≤2G）构建前请阅读 `Dockerfile` 头部注释（内存上限与 swap 建议）
- 日常更新：`git pull && docker compose up -d --build`

#### 方式 B：直接使用官方镜像（推荐）

服务器无需源码与构建工具链，镜像由 GitHub Actions 构建并推送到 ghcr.io：

```bash
# 1. 拉取部署文件（compose + env 模板 + 建表脚本），无需克隆仓库
curl -O https://raw.githubusercontent.com/jingyuan9527/stellar/master/docker-compose.yml
curl -O https://raw.githubusercontent.com/jingyuan9527/stellar/master/.env.example
curl -O https://raw.githubusercontent.com/jingyuan9527/stellar/master/backend/src/main/resources/schema.sql
cp .env.example .env

# 2. 初始化数据库（建库 + 执行上一步下载的 schema.sql，见上）

# 3. 拉取镜像并启动
docker compose pull && docker compose up -d
```

- **首次使用**需到 GitHub Packages（`ghcr.io/jingyuan9527/stellar`）确认包可见性为 **Public**，否则 pull 报 401
- 日常更新：`docker compose pull && docker compose up -d`
- 回滚：`docker-compose.yml` 中 `image: ghcr.io/jingyuan9527/stellar:latest` 改为 `:sha`（每次 CI 构建打 sha tag）再 `docker compose up -d`

#### 方式 C：Fork 仓库，构建自己的镜像

维护自己的版本与镜像空间：

1. Fork 本仓库
2. 修改 `.github/workflows/docker-build-push.yml` 中镜像名 `ghcr.io/jingyuan9527/stellar` → `ghcr.io/<你的用户名>/stellar`
3. 仓库 Settings → Actions → General → **Workflow permissions** 勾选 *Read and write permissions*（推送镜像到 ghcr 需要）
4. push master 触发 CI：自动构建镜像并推送到你的 ghcr
5. 到 GitHub Packages 确认你的包可见性为 **Public**（或服务器 `docker login ghcr.io` 使用私有包）
6. 服务器按方式 B 部署，但把 `docker-compose.yml` 的 image 改为 `ghcr.io/<你的用户名>/stellar:latest`

---

部署完成后访问：`http://<服务器IP>:${FRONTEND_PORT:-80}`（Nginx 托管前端 + 反代后端，单容器内双进程）。

### 4. 默认账号

> **⚠️ 首次部署后请立即修改默认密码！**
>
> 管理员账号由 `DataInitializer` 在应用首次启动时自动播种（已存在则跳过）：
>
> | 用户名 | 密码 |
> | --- | --- |
> | `admin` | `123456` |
>
> 登录后点击右上角头像 → **修改密码** 即可更改。默认密码仅用于首次部署，切勿在生产环境保留。

## 环境变量

`stellar` 容器通过 `env_file: .env` 加载下列变量（定义见 `.env.example`）：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/soybean` | PostgreSQL JDBC 连接串 |
| `DB_USERNAME` | `postgres` | 数据库用户名 |
| `DB_PASSWORD` | `postgres` | 数据库密码 |
| `REDIS_URL` | （空） | Redis 连接串，如 `redis://:pwd@host:6379`（不含末段 `/db`，db 由 `REDIS_DATABASE` 指定）；空则不启用 Redis |
| `REDIS_DATABASE` | `0` | Redis db（隔离应用数据，本项目用 1）。url 末段 `/db` 在某些 Spring Boot 版本不被 Lettuce 解析，必须用此变量显式指定 |
| `SPRING_PROFILES_ACTIVE` | `default` | Spring profile（默认不自动执行建表脚本） |
| `RATE_LIMIT_DAILY` | `50` | IP 单日限流阈值 |
| `FRONTEND_PORT` | `80` | 容器对外端口（Nginx） |

## 本地开发

前后端作为两个独立进程运行：

**后端**（`backend/`）

```bash
mvn -q compile -DskipTests          # 编译检查
$env:SPRING_PROFILES_ACTIVE="local"; mvn spring-boot:run   # 启动于 :8080
```

> `local` profile 用于连接真实数据库与 Redis，其凭证保存在 gitignored 的 `application-local.yml` 中。

**前端**（`frontend/`，包管理器为 pnpm）

```bash
pnpm install
pnpm dev          # 开发服务器 :5173，/api 代理到 :8080
pnpm typecheck    # 类型检查
pnpm build        # 生产构建（typecheck 门禁）
```

## 持久化

- 上传文件：二进制存数据库 `sys_file` 表（BYTEA），无磁盘卷依赖；Nginx 经 `/file/` 反代到后端 `GET /file/{id}` 读取。
- 数据库：由外部 PostgreSQL 管理，本仓库不负责其持久化。
- 缓存/会话：Redis 管理 Sa-Token 会话（重启不丢登录态）、IP 限流计数、Spring Cache 业务缓存；key 统一以 `stellar:` 前缀隔离。

## 开源协议

[Apache License 2.0](./LICENSE) © Stellar Contributors
