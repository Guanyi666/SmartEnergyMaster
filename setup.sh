#!/bin/bash
# ============================================================
# 智驭能效（SmartEnergyMaster）一键部署脚本
# 用法: bash setup.sh [start|stop|restart|db-init]
# ============================================================
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
DB_NAME="smart_energy"
DB_USER="energy_user"

log() { echo -e "\n\033[1;36m[Setup]\033[0m $1"; }
ok()  { echo -e "  \033[1;32m✓\033[0m $1"; }
warn(){ echo -e "  \033[1;33m⚠\033[0m $1"; }

# ---- 启动 Docker 服务 ----
start_services() {
    log "启动 Docker 服务..."
    cd "$ROOT/deploy"
    docker-compose up -d
    ok "TimescaleDB + Redis 容器已启动"

    log "等待数据库就绪..."
    for i in $(seq 1 30); do
        if docker exec smart_energy_db pg_isready -U "$DB_USER" -d "$DB_NAME" &>/dev/null; then
            ok "数据库就绪 (耗时 ${i}s)"
            break
        fi
        sleep 1
    done
}

# ---- 数据库初始化（幂等，可重复执行）----
init_database() {
    log "执行数据库初始化脚本..."
    cd "$ROOT/deploy/init-sql"

    # 按文件名排序执行所有 .sql 文件
    for sql in $(ls -1 *.sql 2>/dev/null | sort); do
        echo -n "  执行 $sql ... "
        docker exec -i smart_energy_db psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 < "$sql" &>/dev/null \
            && echo "✓" || echo "⚠ (部分语句可能已执行过，检查日志)"
    done

    # 单独执行种子数据
    for seed in seed_sop.sql seed_cases.sql 2>/dev/null; do
        if [ -f "$seed" ]; then
            echo -n "  种子数据 $seed ... "
            docker exec -i smart_energy_db psql -U "$DB_USER" -d "$DB_NAME" < "$seed" &>/dev/null \
                && echo "✓" || echo "⚠"
        fi
    done

    ok "数据库初始化完成"
}

# ---- 启动后端 ----
start_backend() {
    log "启动后端 (Spring Boot :8080)..."
    cd "$ROOT/backend"
    mvn spring-boot:run &
    BACKEND_PID=$!
    ok "后端启动中 (PID=$BACKEND_PID)，首次编译可能需要 1-2 分钟"
}

# ---- 启动前端 ----
start_frontend() {
    log "启动前端 (Vue 3 Vite :5173)..."
    cd "$ROOT/frontend"
    npm run dev &
    FRONTEND_PID=$!
    ok "前端启动中 (PID=$FRONTEND_PID)"
}

# ---- 停止服务 ----
stop_services() {
    log "停止所有服务..."
    cd "$ROOT/deploy"
    docker-compose down 2>/dev/null || true
    pkill -f "spring-boot:run" 2>/dev/null || true
    pkill -f "vite" 2>/dev/null || true
    ok "服务已停止"
}

# ---- 主流程 ----
case "${1:-start}" in
    start)
        start_services
        init_database
        echo ""
        log "=== 全部就绪 ==="
        echo "  后端:  cd backend && mvn spring-boot:run"
        echo "  前端:  cd frontend && npm run dev"
        echo "  大屏:  http://localhost:5173"
        echo "  Swagger: http://localhost:8080/swagger-ui.html"
        echo "  仿真器: cd data && python data_pump.py"
        ;;
    db-init)
        # 仅初始化数据库（适合已有运行中的服务）
        init_database
        ;;
    stop)
        stop_services
        ;;
    restart)
        stop_services
        sleep 2
        start_services
        init_database
        ;;
    *)
        echo "用法: bash setup.sh [start|stop|restart|db-init]"
        echo "  start   - 启动所有服务并初始化数据库（默认）"
        echo "  db-init - 仅执行数据库初始化脚本（幂等）"
        echo "  stop    - 停止所有服务"
        echo "  restart - 重启所有服务"
        ;;
esac
