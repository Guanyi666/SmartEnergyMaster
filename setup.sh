#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if ! docker info >/dev/null 2>&1; then
  echo "[错误] Docker 未运行，请先启动 Docker Desktop 或 Colima。"
  exit 1
fi

echo "正在构建并启动 SmartEnergyMaster（含数据模拟器）..."
docker compose -f deploy/docker-compose.yml --profile simulator up -d --build

echo
docker compose -f deploy/docker-compose.yml --profile simulator ps
echo
echo "前端:    http://localhost:${FRONTEND_PORT:-5173}"
echo "Swagger: http://localhost:${BACKEND_PORT:-8080}/swagger-ui.html"
echo "账号:    admin / admin123"
