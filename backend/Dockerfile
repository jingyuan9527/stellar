# syntax=docker/dockerfile:1
# Stellar 后端镜像：多阶段构建，从源码产出可运行 jar
# 构建上下文为 backend/ 目录：docker build -t stellar-backend ./backend

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# 先拷 pom 利用层缓存下载依赖
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
# 再拷源码编译打包（跳过测试以加速，本地/CI 应单独跑测试）
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# 非 root 运行，上传目录需可写
RUN addgroup -S stellar && adduser -S stellar -G stellar && \
    mkdir -p /app/uploads && chown -R stellar:stellar /app
COPY --from=build /app/target/*.jar app.jar
USER stellar
EXPOSE 8080
# UPLOAD_DIR 由 compose 注入，固定指向卷挂载目录；时区设为东八区
ENV TZ=Asia/Shanghai \
    UPLOAD_DIR=/app/uploads
ENTRYPOINT ["java","-jar","/app/app.jar"]
