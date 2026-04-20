#!/usr/bin/env bash
# ==================== 数据库恢复脚本 ====================
#
# 用途：从 SQL 文件恢复业务数据和 Nacos 配置到开发环境
# 用法：
#   ./scripts/db/db-restore.sh                  # 从种子自动恢复 PG + Nacos
#   ./scripts/db/db-restore.sh --pg             # 仅恢复 PostgreSQL
#   ./scripts/db/db-restore.sh --nacos          # 仅恢复 Nacos 配置
#   ./scripts/db/db-restore.sh --clean          # 清空后覆盖恢复
#   ./scripts/db/db-restore.sh ./dump.sql       # 从指定文件恢复
#
# 配合 db-backup.sh --seed 使用：
#   A环境: ./scripts/db/db-backup.sh --seed && git commit && push
#   B环境: git pull && ./scripts/db/db-restore.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

PG_CONTAINER="langchain4j-postgres"
MYSQL_CONTAINER="langchain4j-nacos-db"
PG_USER="${POSTGRES_USER:-langchain4j}"
PG_DB="${POSTGRES_DB:-langchain4j}"
MYSQL_USER="${NACOS_DB_USER:-nacos}"
MYSQL_PWD="${NACOS_DB_PASSWORD:-REDACTED_NACOS_PASSWORD}"

CLEAN=false
RESTORE_PG=true
RESTORE_NACOS=true
CUSTOM_FILE=""

for arg in "$@"; do
    case "$arg" in
        --clean)       CLEAN=true ;;
        --pg)          RESTORE_NACOS=false ;;
        --nacos)       RESTORE_PG=false ;;
        --help|-h)     echo "用法: $0 [--pg|--nacos|--clean] [SQL文件]"; exit 0 ;;
        *)             if [ -f "$arg" ]; then CUSTOM_FILE="$arg"; fi ;;
    esac
done

check_container() {
    local name="$1"
    if ! docker ps --format '{{.Names}}' | grep -q "^${name}$"; then
        echo "ERROR: 容器 '$name' 未运行。请先启动: docker compose -f docker-compose.dev.yml up -d"
        exit 1
    fi
}

echo "============================================"
echo "  数据库恢复工具 (Linux/Mac)"
echo "============================================"

# ==================== PostgreSQL 恢复 ====================
if [ "$RESTORE_PG" = true ]; then
    SEED_SQL="$PROJECT_ROOT/infra/postgres/init/02-seed-data.sql"
    TARGET="${CUSTOM_FILE:-$SEED_SQL}"

    if [ ! -f "$TARGET" ] || [ ! -s "$TARGET" ]; then
        echo "[SKIP] 未找到 PostgreSQL 种子数据文件: $TARGET"
        echo "  提示: 生成种子 -> make db-backup  或  ./scripts/db/db-backup.sh --seed"
    else
        echo ""
        echo "==> 恢复 PostgreSQL 业务数据..."
        echo "    来源: $TARGET ($(du -h "$TARGET" | cut -f1))"
        check_container "$PG_CONTAINER"

        if [ "$CLEAN" = true ]; then
            echo "    [CLEAN] 清空 app schema 数据..."
            docker exec -i "$PG_CONTAINER" psql \
                -U "$PG_USER" -d "$PG_DB" \
                -c "DO \$$ DECLARE t RECORD; BEGIN FOR t IN (SELECT tablename FROM pg_tables WHERE schemaname='app') LOOP EXECUTE 'TRUNCATE TABLE app.' || t.tablename || ' CASCADE'; END LOOP; END \$$;"
        fi

        docker exec -i "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" < "$TARGET" 2>/tmp/pg-restore.log
        if [ $? -eq 0 ] || grep -q "duplicate key\|already exists" /tmp/pg-restore.log 2>/dev/null; then
            echo "    OK! PostgreSQL 恢复完成 ✅"
        else
            echo "    WARN: 恢复可能有问题，检查 /tmp/pg-restore.log"
        fi
    fi
fi

# ==================== Nacos 配置恢复 ====================
if [ "$RESTORE_NACOS" = true ]; then
    SEED_NACOS="$PROJECT_ROOT/infra/nacos/init/03-nacos-config-seed.sql"

    if [ ! -f "$SEED_NACOS" ] || [ ! -s "$SEED_NACOS" ]; then
        echo "[SKIP] 未找到 Nacos 种子配置文件: $SEED_NACOS"
        echo "  提示: 生成种子 -> make db-backup  或  ./scripts/db/db-backup.sh --seed"
        echo "         （首次启动会使用 01-init.sql 的预置配置）"
    else
        echo ""
        echo "==> 恢复 Nacos 配置..."
        echo "    来源: $SEED_NACOS ($(du -h "$SEED_NACOS" | cut -f1))"
        check_container "$MYSQL_CONTAINER"

        # INSERT IGNORE 语法，已存在的配置不会报错或重复插入
        docker exec -i "$MYSQL_CONTAINER" mysql \
            -u"$MYSQL_USER" -p"$MYSQL_PWD" \
            nacos \
            < "$SEED_NACOS" 2>/tmp/nacos-restore.log

        COUNT=$(grep -c '^INSERT' "$SEED_NACOS" 2>/dev/null || echo 0)
        echo "    OK! Nacos 配置恢复完成 ($COUNT 条配置记录) ✅"
    fi
fi

echo ""
echo "============================================"
echo "  所有恢复操作已完成!"
echo "============================================"
