# Overview

> 定位与结构总览。细节见 `frontend.md / backend.md`。

## 定位

Stellar 是**个人知识/实验沉淀池**，非纯后台。游客免登录浏览 `/home` 落地页 + `/about` + 工具（受 IP 限流）；管理功能登录后可见。公开页与管理后台共用 `BasicLayout`，按登录态切换菜单/入口。

- `/home`：hero + 公开工具卡片 + 关于我入口，`/` 指向此处
- `/about`：简历式主页（Hero/富文本/技能/项目卡片/联系方式），后台 `system/profile` 编辑
- 公开菜单可配置：`sys_menu_visibility` + `GET /public/menu-config` 动态过滤
- `@PublicAccess` 放行游客接口，方法内 `StpUtil.isLogin()` 区分游客/登录
- IP 限流：Redis INCR + 当日过期，429，阈值配置化，多实例共享
- 计费/统计：`sys_ai_usage`（account/ip + provider_id/model_type）+ `@RateLimit`，仪表盘展示环比
- 游戏/海螺/TTS/备忘等见 `backend.md`

## Repo Shape

```
stellar/
├─ frontend/  Vue3 + Vite + TS + Pinia + UnoCSS + Naive UI (pnpm)
├─ backend/   Spring Boot 3.3 Java21 Maven MyBatis-Plus PG + Sa-Token + Redis
├─ docs/      分层文档（本目录）
├─ work/      AI 中间态（gitignored，不提交）
├─ scripts/backup/  pg_dump 备份
├─ Dockerfile + entrypoint.sh + docker-compose.yml + nginx.conf
└─ README.md / RULES.md / AGENTS.md
```

- 后端包：`com.stellar.{ai,tts,game,system,memos}` + `infra` + 公共 `common/config/interceptor/aspect`
- Mappers 扫描：`com.stellar.{ai,tts,game,system,memos}.mapper`
- 前后端独立进程，无根构建

## 导航结构（阶段12后）

- `AI创作 /ai` (3)：`/ai/create`（聊天/文案/图片/视频/TTS → redirect `/ai/chat`）+ `/ai/manage`（模板/会话/记忆/知识库/人设/RAG评估/AI配置 → redirect `/ai/template`），子路由绝对路径
- `实用工具 /tools` (5)：封面工具/JSON 格式化
- `游戏 /game` (6)：数学游戏/神奇海螺/海螺管理
- `Memos 管理 /memos` (7)：备份 + AI 打标签 + 写回
- `系统管理 /system` (99)：账号安全/日志/游客访问配置/个人主页/文件管理/系统监控

历史归档：[`archive/STAGES.md`](../archive/STAGES.md)，行为变更同步 `docs/`（铁律 8）。
