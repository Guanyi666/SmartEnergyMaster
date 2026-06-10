@echo off
chcp 65001 >nul
:: ============================================================
:: 智驭能效（SmartEnergyMaster）一键部署脚本 (Windows)
:: 用法: setup.bat [start|stop|restart|db-init]
:: ============================================================
setlocal EnableDelayedExpansion
set ROOT=%~dp0
set DB_NAME=smart_energy
set DB_USER=energy_user

if "%~1"=="" set CMD=start
if not "%~1"=="" set CMD=%~1

if /I "%CMD%"=="start"   goto :start
if /I "%CMD%"=="db-init" goto :db_init
if /I "%CMD%"=="stop"    goto :stop
if /I "%CMD%"=="restart" goto :restart
echo 用法: setup.bat [start^|stop^|restart^|db-init]
echo   start   - 启动Docker服务并初始化数据库（默认）
echo   db-init - 仅执行数据库初始化脚本（幂等）
echo   stop    - 停止所有服务
echo   restart - 重启所有服务
goto :eof

:start
    echo [Setup] 启动 Docker 服务...
    cd /d "%ROOT%deploy"
    docker-compose up -d
    echo   √ TimescaleDB + Redis 启动中...

    echo [Setup] 等待数据库就绪...
    for /L %%i in (1,1,30) do (
        docker exec smart_energy_db pg_isready -U %DB_USER% -d %DB_NAME% >nul 2>&1 && goto :db_ready
        timeout /t 1 /nobreak >nul
    )
    :db_ready
    echo   √ 数据库就绪

    call :db_init

    echo.
    echo === 全部就绪 ===
    echo   后端:  cd backend ^&^& mvn spring-boot:run
    echo   前端:  cd frontend ^&^& npm run dev
    echo   大屏:  http://localhost:5173
    echo   Swagger: http://localhost:8080/swagger-ui.html
    goto :eof

:db_init
    echo [Setup] 执行数据库初始化（幂等）...
    cd /d "%ROOT%deploy\init-sql"

    for %%f in (*.sql) do (
        echo   执行 %%f ...
        docker exec -i smart_energy_db psql -U %DB_USER% -d %DB_NAME% < "%%f" >nul 2>&1
    )
    echo   √ 数据库初始化完成
    goto :eof

:stop
    echo [Setup] 停止 Docker 服务...
    cd /d "%ROOT%deploy"
    docker-compose down
    echo   √ 服务已停止
    goto :eof

:restart
    call :stop
    timeout /t 3 /nobreak >nul
    call :start
    goto :eof
