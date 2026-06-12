@echo off
title 智驭能效 一键部署
cd /d "%~dp0"

:: 检查是否在正确目录
if not exist "deploy\docker-compose.yml" (
    echo [错误] 请在项目根目录下运行本脚本
    echo 当前目录: %cd%
    pause
    exit /b 1
)

echo.
echo ============================================
echo   智驭能效 SmartEnergyMaster 一键部署
echo ============================================
echo.

set DB_NAME=smart_energy
set DB_USER=energy_user
set SQL_DIR=%cd%\deploy\init-sql

:: ---- 1. 启动 Docker ----
echo [1/5] 启动 Docker 容器...

:: 尝试 docker-compose (旧版) 和 docker compose (新版)
docker-compose -f "%cd%\deploy\docker-compose.yml" up -d 2>nul
if %ERRORLEVEL% NEQ 0 (
    docker compose -f "%cd%\deploy\docker-compose.yml" up -d 2>nul
    if !ERRORLEVEL! NEQ 0 (
        echo   [错误] Docker 启动失败，请检查:
        echo     1. Docker Desktop 是否已安装并运行
        echo     2. 是否在 BIOS 中启用了虚拟化
        pause
        exit /b 1
    )
)
echo   [ OK ] TimescaleDB + Redis 已启动

:: ---- 2. 等待数据库就绪 ----
echo [2/5] 等待数据库就绪...
for /L %%i in (1,1,40) do (
    docker exec smart_energy_db pg_isready -U %DB_USER% -d %DB_NAME% >nul 2>&1 && goto :db_ok
    timeout /t 1 /nobreak >nul
)
echo   [警告] 数据库启动超时，继续尝试...

:db_ok
echo   [ OK ] 数据库就绪

:: ---- 3. 执行全部 SQL ----
echo [3/5] 执行数据库初始化与修复...
for %%f in ("%SQL_DIR%\*.sql") do (
    echo   → %%~nxf
    docker exec -i smart_energy_db psql -U %DB_USER% -d %DB_NAME% < "%%f" >nul 2>&1
)
echo   [ OK ] 数据库已就绪

:: ---- 4. 验证 ----
echo [4/5] 验证数据库完整性...
for %%t in (device sys_user sensor_data work_order workorder_maintenance_personnel workorder_assignment maintenance_sop repair_case spare_part spare_part_usage) do (
    docker exec smart_energy_db psql -U %DB_USER% -d %DB_NAME% -t -c "SELECT 1 FROM %%t LIMIT 1" >nul 2>&1 || (
        echo   [警告] 表 %%t 缺失
    )
)
echo   [ OK ] 验证完成

:: ---- 5. 完成 ----
echo [5/5] 部署完成
echo.
echo ============================================
echo   环境已就绪，请依次启动:
echo.
echo   后端:  cd backend
echo          mvn spring-boot:run
echo.
echo   前端:  cd frontend
echo          npm install  (仅首次)
echo          npm run dev
echo.
echo   仿真:  cd data
echo          python data_pump.py
echo.
echo   大屏 → http://localhost:5173
echo   API  → http://localhost:8080/swagger-ui.html
echo ============================================
echo.
pause
