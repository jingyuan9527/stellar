# =============================================================================
# Stellar 单镜像：前端(Nginx) + 后端(Spring Boot) 双进程，由 entrypoint.sh 托管。
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
#   - jlink 阶段：alpine JDK 按模块清单裁剪出 musl 版最小 JRE（约 1/3 体积）
#   - 运行镜像：纯 alpine + jlink JRE + nginx + entrypoint.sh（无 supervisor/python）
#   - 文件上传存数据库(sys_file)，无磁盘卷依赖；Nginx 反代 /api /file 到本机后端
# =============================================================================

# ---------- 构建阶段（串行：前端 → 后端） ----------
# maven 镜像（Ubuntu 底）提供 JDK21 + Maven，另装 Node 22 官方 tarball 提供前端工具链。
# 两个工具链共存于同一 stage，构建进程同时只跑一个，内存峰值不叠加。
FROM maven:3.9-eclipse-temurin-21 AS build
# NODE_OPTIONS 压到 1152M / MAVEN_OPTS 压到 768M：2G 机器构建峰值 ≈ max(1.3G, 1G)
# NODE_VERSION 用 ARG + 独立 ENV：同一 ENV 指令内跨行引用变量 buildkit 不识别（UndefinedVar）
# TARGETARCH 由 buildx 注入（amd64/arm64）；node tarball 命名是 linux-x64/linux-arm64，需映射
ARG NODE_VERSION=v22.12.0
ARG TARGETARCH
ENV NODE_VERSION=${NODE_VERSION}
ENV TARGETARCH=${TARGETARCH}
ENV PATH=/opt/node-${NODE_VERSION}/bin:${PATH}
ENV NODE_OPTIONS="--max-old-space-size=1152"
ENV MAVEN_OPTS="-Xmx768m -Xms384m -XX:+UseContainerSupport"
# Node 22 官方 linux tarball（.tar.gz，解压到 /opt 后重命名固定目录）
# 平台映射：先试 linux-${TARGETARCH}（arm64 命中），amd64 不存在则 || 兜底 linux-x64
# 避免 shell 计算变量（buildkit 的 $$ 转义/未定义变量替换行为不可靠），全部用 Docker 侧 ENV 替换 + glob
# .tar.gz 而非 .tar.xz：maven 镜像（Ubuntu 底）无 xz 工具；node bin 目录直接进 PATH（npm -g 全局包落盘到 node 真实目录）
RUN curl -fsSL -o /tmp/node.tar.gz "https://nodejs.org/dist/${NODE_VERSION}/node-${NODE_VERSION}-linux-${TARGETARCH}.tar.gz" \
    || curl -fsSL -o /tmp/node.tar.gz "https://nodejs.org/dist/${NODE_VERSION}/node-${NODE_VERSION}-linux-x64.tar.gz" \
    && tar -xz -C /opt -f /tmp/node.tar.gz \
    && mv /opt/node-${NODE_VERSION}-linux-* /opt/node-${NODE_VERSION} \
    && rm /tmp/node.tar.gz
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

# ---------- JRE 裁剪阶段 ----------
# 运行镜像换纯 alpine（musl libc），JRE 必须同为 musl 版，故用 alpine JDK 做 jlink。
# 模块清单 = jdeps 静态分析基线 + 反射/隐式依赖补足（本应用技术栈相关）：
#   jdeps 基线: java.compiler java.desktop java.instrument java.net.http java.prefs
#               java.rmi java.scripting java.security.jgss java.sql.rowset
#               java.xml.crypto jdk.attach jdk.jdi jdk.jfr jdk.management jdk.net
#               jdk.unsupported（java.base 自动含）
#   补足: java.management(Actuator/Micrometer/Tomcat JMX) java.naming(JNDI)
#         java.sql(JDBC/PG驱动) java.transaction.xa(HikariCP) java.xml(JAXB/POI)
#         java.security.sasl(认证库间接引用) jdk.crypto.ec(HTTPS/TLS 必需)
#         jdk.charsets(GBK 等扩展字符集) jdk.localedata(中文 Locale，仅保留 en/zh)
# 若日后新增依赖出现 NoClassDefFoundError，用 jdeps 复查补模块即可。
FROM eclipse-temurin:21-jdk-alpine AS jlink
RUN jlink \
    --add-modules java.base,java.compiler,java.desktop,java.instrument,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.attach,jdk.charsets,jdk.crypto.ec,jdk.jdi,jdk.jfr,jdk.localedata,jdk.management,jdk.net,jdk.unsupported \
    --no-man-pages \
    --no-header-files \
    --compress=zip-6 \
    --include-locales=en,zh \
    --output /jre

# ---------- 运行阶段 ----------
FROM alpine:3.20
# nginx + JVM 动态库依赖（libstdc++/libgcc）+ tzdata（系统时区文件，供 Nginx 日志时间）
RUN apk add --no-cache nginx tzdata libstdc++ libgcc \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone
WORKDIR /app
ENV JAVA_HOME=/jre
ENV PATH=${JAVA_HOME}/bin:${PATH}
ENV TZ=Asia/Shanghai
# 拷后端 jar + jlink 裁剪 JRE（musl 版，与 alpine 兼容）
COPY --from=jlink /jre /jre
COPY --from=build /app/target/*.jar /app/app.jar
# 拷前端静态产物到 Nginx 目录
COPY --from=build /app/dist /usr/share/nginx/html
# Nginx 配置（直接覆盖主配置，不依赖 http.d include，确保反代生效）
COPY frontend/nginx.conf /etc/nginx/nginx.conf
# 进程托管脚本（替代 supervisor，无 python 依赖，行为等价 autorestart）
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
EXPOSE 80
CMD ["/entrypoint.sh"]