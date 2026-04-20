@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul 2>&1

:: ==================== 数据库备份脚本 (Windows) ====================
::
:: 用途：
::   db-backup.bat --seed       备份 PG + Nacos 为种子文件（Git 共享）
::   db-backup.bat              备份到 backup/
::   db-backup.bat --pg         仅备份 PostgreSQL
::   db-backup.bat --nacos      仅备份 Nacos 配置

set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%..\.."
set "TIMESTAMP=%date:~0,4%%date:~5,2%%time:~0,2%%time:~3,2%"

set PG_CONTAINER=langchain4j-postgres
set MYSQL_CONTAINER=langchain4j-nacos-db
set PG_USER=langchain4j
set PG_DB=langchain4j
set MYSQL_USER=nacos
set MYSQL_PWD=REDACTED_NACOS_PASSWORD

if "%1"=="--seed" (
    goto backup_seed
) else if "%1"=="--pg" (
    goto backup_pg_only
) else if "%1"=="--nacos" (
    goto backup_nacos_only
) else if "%1"=="" (
    goto backup_full
) else if "%1"=="--help" (
    echo 用法: %0 [--seed^|--pg^|--nacos]
    exit /b 0
)

echo 未知参数: %1
exit /b 1

:backup_seed
echo ==> 备份种子数据...

:: --- PostgreSQL ---
echo [1/2] 备份 PostgreSQL 种子数据...
docker exec %PG_CONTAINER% pg_dump -U %PG_USER% -d %PG_DB% --data-only --schema=app --no-owner --no-privileges > "%PROJECT_ROOT%\infra\postgres\init\02-seed-data.sql"
if !errorlevel! neq 0 (
    echo ERROR: PostgreSQL 备份失败，确认容器运行中
    exit /b 1
)
for %%A in ("%PROJECT_ROOT%\infra\postgres\init\02-seed-data.sql") do echo     OK! ^(%%~zA bytes^)

:: --- Nacos Config ---
echo [2/2] 备份 Nacos 配置种子...
(
    echo -- Nacos config seed ^^(auto-generated^)
    echo SET NAMES utf8mb4;
    docker exec %MYSQL_CONTAINER% mysqldump -u%MYSQL_USER% -p%MYSQL_PWD% nacos config_info --no-create-info --complete-insert --extended-insert=false --where="tenant_id='' OR tenant_id IS NULL" 2>nul
) > "%PROJECT_ROOT%\infra\nacos\init\03-nacos-config-seed.sql"
if !errorlevel! equ 0 (
    for %%A in ("%PROJECT_ROOT%\infra\nacos\init\03-nacos-config-seed.sql") do echo     OK! ^(%%~zA bytes^)
) else (
    echo WARN: Nacos 备份可能失败（检查容器是否运行）
)

echo.
echo ============================================
echo   种子数据备份完成!
echo --------------------------------------------
echo   提交共享:
echo     git add infra/*/init/*seed*
echo     git commit -m "sync: update seed data"
echo     git push
echo ============================================
goto :eof

:backup_pg_only
set "TARGET_DIR=%PROJECT_ROOT%\backup\%TIMESTAMP%"
if not exist "%TARGET_DIR%" mkdir "%TARGET_DIR%"
echo ==> 备份 PostgreSQL -> %TARGET_DIR%
docker exec %PG_CONTAINER% pg_dump -U %PG_USER% -d %PG_DB% --create --schema=app --no-owner --no-privileges > "%TARGET_DIR%\postgres_dump.sql"
if !errorlevel! equ 0 (echo OK!) else (echo ERROR!)
goto :eof

:backup_nacos_only
set "TARGET_DIR=%PROJECT_ROOT%\backup\%TIMESTAMP%"
if not exist "%TARGET_DIR%" mkdir "%TARGET_DIR%"
echo ==> 备份 Nacos -> %TARGET_DIR%
docker exec %MYSQL_CONTAINER% mysqldump -u%MYSQL_USER% -p%MYSQL_PWD% nacos --single-transaction --routines > "%TARGET_DIR%\nacos_dump.sql" 2>nul
if !errorlevel! equ 0 (echo OK!) else (echo ERROR!)
goto :eof

:backup_full
call :backup_pg_only
call :backup_nacos_only
echo.
echo 备份目录: %PROJECT_ROOT%\backup\%TIMESTAMP%\
goto :eof
