# syntax=docker/dockerfile:1
# Stellar 前端镜像：多阶段构建，pnpm 源码构建 -> Nginx 托管静态产物
# 构建上下文为 frontend/ 目录：docker build -t stellar-frontend ./frontend

# ---------- 构建阶段 ----------
FROM node:20-alpine AS build
RUN corepack enable && corepack prepare pnpm@latest --activate
WORKDIR /app
# 先拷锁文件利用层缓存恢复依赖
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
# 再拷源码构建（VITE_API_BASE_URL 默认 /api，见 .env；生产由 Nginx 反代）
COPY . .
RUN pnpm build

# ---------- 运行阶段 ----------
FROM nginx:alpine
# 用自定义 Nginx 配置：托管 SPA + 反代 /api /uploads 到后端
RUN rm -f /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/stellar.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
