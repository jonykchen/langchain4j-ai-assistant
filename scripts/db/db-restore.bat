@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul 2>&1

:: ==================== 数据库恢复脚本 (Windows) ====================
::
:: 用途：
::   db-restore.bat                  恢复 PG + Nacos 种子数据
::   db-restore.bat --pg             仅恢复 PostgreSQL
::   db-restore.bat --nacos          仅恢复 Nacos 配置
::   db-restore.bat --clean          清空后覆盖恢复

set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%..\.."

set PG_CONTAINER=langchain4j-postgres
set MYSQL_CONTAINER=langchain4j-nacos-db
set PG_USER=langchain4j
set PG_DB=langchain4j
set MYSQL_USER=nacos
set MYSQL_PWD=REDACTED_NACOS_PASSWORD

set RESTORE_PG=true
set RESTORE_NACOS=true
set CLEAN=false

:parse_args
if "%1"=="" goto do_restore
if "%1"=="--pg" (
    set RESTORE_NACOS=false
) else if "%1"=="--nacos" (
    set RESTORE_PG=false
) else if "%1"=="--clean" (
    set CLEAN=true
)
shift
goto parse_args

:do_restore
echo ============================================
echo   数据库恢复工具 (Windows)
echo ============================================

:: ========== PostgreSQL ==========
if "%RESTORE_PG%"=="true" (
    set SEED_SQL=%PROJECT_ROOT%\infra\postgres\init\02-seed-data.sql

    if not exist "%SEED_SQL%" (
        echo [SKIP] 未找到 PostgreSQL 种子数据: %SEED_SQL%
        echo   提示: 生成种子 -> scripts\db\db-backup.bat --seed
    ) else (
        echo.
        echo ==> 恢复 PostgreSQL...

        docker ps --format "{{.Names}}" | findstr /r "^%PG_CONTAINER%$" >nul 2>&1
        if !errorlevel! neq 0 (
            echo ERROR: 容器 %PG_CONTAINER% 未运行
            exit /b 1
        )

        if "%CLEAN%"=="true" (
            echo [CLEAN] 清空 app schema 数据...
            docker exec -i %PG_CONTAINER% psql -U %PG_USER% -d %PG_DB% -c "DO $$ DECLARE t RECORD; BEGIN FOR t IN (SELECT tablename FROM pg_tables WHERE schemaname='app') LOOP EXECUTE 'TRUNCATE TABLE app.' || t.tablename || ' CASCADE'; END LOOP; END $$;"
        )

        docker exec -i %PG_CONTAINER% psql -U %PG_USER% -d %PG_DB^ < "%SEED_SQL%"
        if !errorlevel! equ 0 (
            echo     OK! PostgreSQL 恢复完成 ^✅^
        ) else (
            echo     WARN: 恢复可能有警告（duplicate key 通常可忽略）
        )
    )
)

:: ========== Nacos Config ==========
if "%RESTORE_NACOS%"=="true" (
    set SEED_NACOS=%PROJECT_ROOT%\infra\nacos\init\03-nacos-config-seed.sql

    if not exist "%SEED_NACOS%" (
        echo [SKIP] 未找到 Nacos 种子配置: %SEED_NACOS%
        echo   提示: 生成种子 -> scripts\db\db-backup.bat --seed
        echo          （首次启动会使用 01-init.sql 的预置配置）
    ) else (
        echo.
        echo ==> 恢复 Nacos 配置...

        docker ps --format "{{.Names}}" | findstr /r "^%MYSQL_CONTAINER%$" >nul 2>&1
        if !errorlevel! neq 0 (
            echo ERROR: 容器 %MYSQL_CONTAINER% 未运行
            exit /b 1
        )

        docker exec -i %MYSQL_CONTAINER% mysql -u%MYSQL_USER% -p%MYSQL_PWD% nacos < "%SEED_NACOS%"
        if !errorlevel! equ 0 (
            echo     OK! Nacos 配置恢复完成 ^✅^
        ) else (
            echo     WARN: 部分配置可能已存在（INSERT IGNORE 跳过重复项）
        )
    )
)

echo.
echo ============================================
echo   所有恢复操作已完成!
echo ============================================
