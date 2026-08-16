#!/bin/sh
# =============================================================================
# Stellar 容器 PID1 进程托管脚本（替代 supervisor）
# 单容器双进程：Nginx(前端SPA+反代) + Spring Boot(后端)。
# - 任一进程崩溃单独拉起，等价 supervisord 的 autorestart=true
# - 崩溃重启策略等价 supervisord 的 startsecs+startretries：
#   进程存活 >=START_SECS 视为启动成功并清零失败计数；
#   连续 MAX_RETRIES 次 <START_SECS 快速崩溃则容器整体退出（exit 1），
#   交给 docker restart 策略拉起整个容器，避免配置错误时无限重启风暴
# - 信号转发：docker stop / SIGTERM 时同时终止两个子进程，优雅退出
# 子进程 stdout/stderr 直接进容器，docker logs 可见。
# 不依赖 supervisor 的 python 运行时，镜像更小。
# =============================================================================

START_SECS=10
MAX_RETRIES=5

NGINX_FAILURES=0
BACKEND_FAILURES=0

start_nginx() {
    nginx -g 'daemon off;' &
    NGINX_PID=$!
    NGINX_START_TIME=$(date +%s)
    echo "[entrypoint] nginx started pid=$NGINX_PID"
}

start_backend() {
    # JAVA_OPTS 由部署 .env 注入（见 .env.example），引号展开让 sh 按空格分词
    java $JAVA_OPTS -jar /app/app.jar &
    BACKEND_PID=$!
    BACKEND_START_TIME=$(date +%s)
    echo "[entrypoint] backend started pid=$BACKEND_PID"
}

shutdown() {
    echo "[entrypoint] receiving signal, shutting down (nginx=$NGINX_PID backend=$BACKEND_PID)"
    kill -TERM "$NGINX_PID" 2>/dev/null
    kill -TERM "$BACKEND_PID" 2>/dev/null
    wait "$NGINX_PID" 2>/dev/null
    wait "$BACKEND_PID" 2>/dev/null
    echo "[entrypoint] exited cleanly"
    exit 0
}

trap shutdown TERM INT

start_nginx
start_backend

while true; do
    sleep 1
    if ! kill -0 "$NGINX_PID" 2>/dev/null; then
        if [ $(( $(date +%s) - NGINX_START_TIME )) -lt "$START_SECS" ]; then
            NGINX_FAILURES=$((NGINX_FAILURES + 1))
        else
            NGINX_FAILURES=0
        fi
        if [ "$NGINX_FAILURES" -ge "$MAX_RETRIES" ]; then
            echo "[entrypoint] nginx crashed ${MAX_RETRIES}x within ${START_SECS}s, exiting container"
            exit 1
        fi
        echo "[entrypoint] nginx exited (failures=$NGINX_FAILURES), restarting..."
        sleep 2
        start_nginx
    fi
    if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
        if [ $(( $(date +%s) - BACKEND_START_TIME )) -lt "$START_SECS" ]; then
            BACKEND_FAILURES=$((BACKEND_FAILURES + 1))
        else
            BACKEND_FAILURES=0
        fi
        if [ "$BACKEND_FAILURES" -ge "$MAX_RETRIES" ]; then
            echo "[entrypoint] backend crashed ${MAX_RETRIES}x within ${START_SECS}s, exiting container"
            exit 1
        fi
        echo "[entrypoint] backend exited (failures=$BACKEND_FAILURES), restarting..."
        sleep 2
        start_backend
    fi
done