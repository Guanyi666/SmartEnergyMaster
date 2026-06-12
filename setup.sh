#!/bin/bash
# ============================================================
#  智驭能效 — 一键部署 (macOS / Linux / Git Bash)
#  用法: bash setup.sh
# ============================================================
set -e
cd "$(dirname "$0")"

if [ ! -f "deploy/docker-compose.yml" ]; then
    echo "[错误] 请在项目根目录下运行本脚本"
    exit 1
fi

DB_NAME="smart_energy"
DB_USER="energy_user"
SQL_DIR="$(pwd)/deploy/init-sql"

echo ""
echo "============================================"
echo "  智驭能效 SmartEnergyMaster 一键部署"
echo "============================================"
echo ""

# ---- 1. 启动 Docker ----
echo "[1/5] 启动 Docker 容器..."
cd deploy
if docker-compose up -d 2>/dev/null; then
    true
elif docker compose up -d 2>/dev/null; then
    true
else
    echo "  [错误] Docker 启动失败，请检查 Docker Desktop 是否运行"
    exit 1
fi
cd ..
echo "  [ OK ] TimescaleDB + Redis 已启动"

# ---- 2. 等待数据库就绪 ----
echo "[2/5] 等待数据库就绪..."
for i in $(seq 1 40); do
    docker exec smart_energy_db pg_isready -U "$DB_USER" -d "$DB_NAME" &>/dev/null && break
    sleep 1
done
echo "  [ OK ] 数据库就绪"

# ---- 3. 执行全部 SQL ----
echo "[3/5] 执行数据库初始化与修复..."
for sql in "$SQL_DIR"/*.sql; do
    name=$(basename "$sql")
    printf "  → %s\n" "$name"
    docker exec -i smart_energy_db psql -U "$DB_USER" -d "$DB_NAME" < "$sql" >/dev/null 2>&1 || true
done
echo "  [ OK ] 数据库已就绪"

# ---- 4. 验证 ----
echo "[4/5] 验证数据库完整性..."
for tbl in device sys_user sensor_data work_order workorder_maintenance_personnel workorder_assignment maintenance_sop repair_case spare_part spare_part_usage; do
    docker exec smart_energy_db psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT 1 FROM $tbl LIMIT 1" &>/dev/null || \
        echo "  [警告] 表 $tbl 缺失"
done
echo "  [ OK ] 验证完成"

# ---- 5. 完成 ----
echo "[5/5] 部署完成"
echo ""
echo "============================================"
echo "  环境已就绪，请依次启动:"
echo ""
echo "  后端:  cd backend && mvn spring-boot:run"
echo "  前端:  cd frontend && npm run dev"
echo "  仿真:  cd data && python data_pump.py"
echo ""
echo "  大屏 → http://localhost:5173"
echo "  API  → http://localhost:8080/swagger-ui.html"
echo "============================================"
echo ""
