# syntax=docker/dockerfile:1
# =============================================================================
# Stellar 单镜像：前端(Nginx) + 后端(Spring Boot) 双进程，由 supervisord 托管。
# 构建上下文为项目根目录：
#
#   低内存服务器（≤2G 内存）构建要点：
#   - 前后端构建合并为单 stage **串行**执行——多 stage 会被 BuildKit 并发跑，
#     node(3G) + maven(2G) 内存峰值叠加，2G 机器必被 OOM killer 打挂
#   - 内存上限已压到 1152M(node) / 768M(maven)，串行峰值 ≈ max(1.3G, 1G)
#   - 强烈建议服务器加 swap 兜底（防构建进程挤爆宿主）：
#       fallocate -l 1G /swapfile && chmod 600 /swapfile && mkswap /swapfile
#       && swapon /swapfile   # 并写入 /etc/fstab 持久化
#   - 若仍想再保险，可带 --memory 限制（构建失败而非挂宿主）：
#       docker build --memory=1536m --memory-swap=1536m -t stellar:latest .
# -----------------------------------------------------------------------------
# 设计要点：
#   - 构建：maven 镜像 + node tarball 合体单 stage，先 pnpm 构建前端再 mvn 打包后端
#   - 运行镜像基于 alpine，安装 nginx + supervisor，一个容器跑两个进程
#   - 文件上传存数据库(sys_file)，无磁盘卷依赖；Nginx 反代 /api /file 到本机后端
# =============================================================================

# ---------- 构建阶段（串行：前端 → 后端） ----------
# maven 镜像（Ubuntu 底）提供 JDK21 + Maven，另装 Node 22 官方 tarball 提供前端工具链。
# 两个工具链共存于同一 stage，构建进程同时只跑一个，内存峰值不叠加。
FROM maven:3.9-eclipse-temurin-21 AS build
# NODE_OPTIONS 压到 1152M / MAVEN_OPTS 压到 768M：2G 机器构建峰值 ≈ max(1.3G, 1G)
ENV NODE_VERSION=v22.12.0 \
    PATH="/opt/node-${NODE_VERSION}-linux-x64/bin:${PATH}" \
    NODE_OPTIONS="--max-old-space-size=1152" \
    MAVEN_OPTS="-Xmx768m -Xms384m -XX:+UseContainerSupport"
# Node 22 官方 linux-x64 tarball（.tar.gz 54MB，解压到 /opt）
# 注意必须用 .tar.gz 而非 .tar.xz：maven 镜像（Ubuntu 底）无 xz 工具，tar 解压 .xz 会失败
# node bin 目录直接进 PATH：npm -g 默认 prefix 是 node 真实安装目录，软链到 /usr/local/bin 会导致全局包（如 pnpm）落盘后不在 PATH
RUN curl -fsSL https://nodejs.org/dist/${NODE_VERSION}/node-${NODE_VERSION}-linux-x64.tar.gz \
        | tar -xz -C /opt
# pnpm 用 npm 全局安装（corepack 在 tarball 环境的 shim 机制不稳定，CI 上 enable/prepare 报错）
RUN npm install -g pnpm@10.12.4
WORKDIR /app

# ---- 前端：先拷锁文件与 pnpm 配置利用层缓存恢复依赖（pnpm-workspace.yaml 含 allowBuilds 审批，必须同时拷入）----
# 挂 pnpm store 缓存卷，依赖包跨构建复用，避免每次全部重下
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml ./
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile
# 再拷源码构建（VITE_API_BASE_URL 默认 /api，生产由 Nginx 反代）
COPY frontend/ .
RUN pnpm build

# ---- 后端：先拷 pom 利用层缓存下载依赖（挂 ~/.m2 缓存卷，Maven 依赖跨构建复用）----
COPY backend/pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline
# 再拷源码编译打包（跳过测试以加速，本地/CI 应单独跑测试）
COPY backend/src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:21-jre-alpine
# 安装 Nginx + supervisord（进程管理，任一进程崩溃自动重启）
RUN apk add --no-cache nginx supervisor
WORKDIR /app
# 拷后端 jar
COPY --from=build /app/target/*.jar /app/app.jar
# 拷前端静态产物到 Nginx 目录
COPY --from=build /app/dist /usr/share/nginx/html
# Nginx 配置（直接覆盖主配置，不依赖 http.d include，确保反代生效）
COPY frontend/nginx.conf /etc/nginx/nginx.conf
# supervisord 配置
COPY supervisord.conf /etc/supervisord.conf
# 时区东八区
ENV TZ=Asia/Shanghai
EXPOSE 80
CMD ["supervisord", "-c", "/etc/supervisord.conf"]