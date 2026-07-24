# syntax=docker/dockerfile:1
# =============================================================================
# Stellar 单镜像：前端(Nginx) + 后端(Spring Boot) 双进程，由 supervisord 托管。
# 构建上下文为项目根目录：docker build -t stellar:latest .
# -----------------------------------------------------------------------------
# 设计要点：
#   - 多阶段构建：node 构建前端 dist → maven 构建后端 jar → 合并到运行镜像
#   - 运行镜像基于 alpine，安装 nginx + supervisor，一个容器跑两个进程
#   - 文件上传存数据库(sys_file)，无磁盘卷依赖；Nginx 反代 /api /file 到本机后端
# =============================================================================

# ---------- 前端构建阶段 ----------
# Node 22+：corepack 拉取的 pnpm 11 要求 Node v22.13+（用到 node:sqlite 内置模块）
FROM node:22-alpine AS frontend-build
RUN corepack enable && corepack prepare pnpm@latest --activate
WORKDIR /app
# 先拷锁文件与 pnpm 配置利用层缓存恢复依赖（pnpm-workspace.yaml 含 allowBuilds 审批，必须同时拷入）
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
# 再拷源码构建（VITE_API_BASE_URL 默认 /api，生产由 Nginx 反代）
COPY frontend/ .
RUN pnpm build

# ---------- 后端构建阶段 ----------
FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /app
# 先拷 pom 利用层缓存下载依赖
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline
# 再拷源码编译打包（跳过测试以加速，本地/CI 应单独跑测试）
COPY backend/src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:21-jre-alpine
# 安装 Nginx + supervisord（进程管理，任一进程崩溃自动重启）
RUN apk add --no-cache nginx supervisor
WORKDIR /app
# 拷后端 jar
COPY --from=backend-build /app/target/*.jar /app/app.jar
# 拷前端静态产物到 Nginx 目录
COPY --from=frontend-build /app/dist /usr/share/nginx/html
# Nginx 配置（覆盖默认 server 块）
COPY frontend/nginx.conf /etc/nginx/http.d/stellar.conf
RUN rm -f /etc/nginx/http.d/default.conf
# supervisord 配置
COPY supervisord.conf /etc/supervisord.conf
# 时区东八区
ENV TZ=Asia/Shanghai
EXPOSE 80
CMD ["supervisord", "-c", "/etc/supervisord.conf"]
