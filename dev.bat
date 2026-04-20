@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul 2>&1

:: ==================== 开发环境启动脚本 (Windows) ====================
::
:: 用法：
::   dev.bat start              启动所有开发服务（自动检测种子数据）
::   dev.bat stop               停止所有服务
::   dev.bat status             查看服务状态
::   dev.bat reset              重置环境
::

set "PROJECT_ROOT=%~dp0"
cd /d "%PROJECT_ROOT%"

if "%1"=="" goto help
if "%1"=="start" goto start_services
if "%1"=="stop" goto stop_services
if "%1"=="status" goto show_status
if "%1"=="reset" goto reset_env
if "%1"=="help" goto help

echo 未知命令: %1
goto help

:start_services
echo ==> 启动开发环境...

:: 检测种子数据状态
if exist "infra\postgres\init\02-seed-data.sql" (
    for %%A in ("infra\postgres\init\02-seed-data.sql") do set seedsize=%%~zA
    if !seedsize! gtr 0 (
        if not exist "data\postgres" (
            echo [OK] 发现种子数据文件，首次启动将自动导入!
        ) else (
            dir /b "data\postgres" >nul 2>&1
            if errorlevel 1 (
                echo [OK] 发现种子数据文件，首次启动将自动导入!
            )
        )
    )
)

docker compose -f docker-compose.dev.yml up -d
if !errorlevel! equ 0 (
    echo.
    echo ============================================
    echo   服务已启动!
    echo --------------------------------------------
    echo   PostgreSQL:  localhost:5432
    echo   Redis:       localhost:6379
    echo   Nacos:       http://localhost:8848/nacos
    echo   Adminer:     http://localhost:8080
    echo --------------------------------------------
    echo   提示: 运行 mvn spring-boot:run 启动后端
    echo ============================================
)
goto :eof

:stop_services
echo ==> 停止所有服务...
docker compose -f docker-compose.dev.yml down
goto :eof

:show_status
docker compose -f docker-compose.dev.yml ps
goto :eof

:reset_env
echo [!] 警告: 这将删除所有数据!
set /p confirm="确认继续? (y/N): "
if /i "%confirm%"=="y" (
    docker compose -f docker-compose.dev.yml down -v --remove-orphans
    rmdir /s /q data\ 2>nul
    echo 环境已重置，重新运行 dev.bat start 即可
) else (
    echo 已取消
)
goto :eof

:help
echo LangChain4j 开发环境管理 (Windows)
echo.
echo 用法: dev.bat ^<命令^>
echo.
echo 命令:
echo   start    启动开发服务
echo   stop     停止服务
echo   status   查看状态
echo   reset    重置环境（清除数据）
echo.
