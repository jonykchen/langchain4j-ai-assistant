#!/usr/bin/env bash
# ==================== 数据库备份脚本 ====================
#
# 用途：将当前开发环境的业务数据和 Nacos 配置导出为 SQL，提交 Git 后其他环境可直接恢复
# 用法：
#   ./scripts/db/db-backup.sh                # 备份 PG + Nacos 到 backup/
#   ./scripts/db/db-backup.sh --seed         # 备份为种子文件（Git 共享）
#   ./scripts/db/db-backup.sh --pg           # 仅备份 PostgreSQL 业务数据
#   ./scripts/db/db-backup.sh --nacos        # 仅备份 Nacos 配置变更
#
# 流程：执行备份 -> git commit -> push -> 同事 pull -> docker compose up -> 自动恢复

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

# 默认值
PG_CONTAINER="langchain4j-postgres"
MYSQL_CONTAINER="langchain4j-nacos-db"
PG_USER="${POSTGRES_USER:-langchain4j}"
PG_DB="${POSTGRES_DB:-langchain4j}"
MYSQL_USER="${NACOS_DB_USER:-nacos}"
MYSQL_PWD="${NACOS_DB_PASSWORD:-REDACTED_NACOS_PASSWORD}"

MODE_SEED=false
BACKUP_PG=true
BACKUP_NACOS=true

for arg in "$@"; do
    case "$arg" in
        --seed)     MODE_SEED=true ;;
        --pg)       BACKUP_NACOS=false ;;
        --nacos)    BACKUP_PG=false ;;
        --help|-h)  echo "用法: $0 [--seed|--pg|--nacos]"; exit 0 ;;
    esac
done

check_container() {
    local name="$1"
    if ! docker ps --format '{{.Names}}' | grep -q "^${name}$"; then
        echo "ERROR: 容器 '$name' 未运行。请先启动: docker compose -f docker-compose.dev.yml up -d"
        exit 1
    fi
}

# ==================== PostgreSQL 备份 ====================
if [ "$BACKUP_PG" = true ]; then
    check_container "$PG_CONTAINER"

    if [ "$MODE_SEED" = true ]; then
        TARGET="$PROJECT_ROOT/infra/postgres/init/02-seed-data.sql"
        echo "==> [1/2] 备份 PostgreSQL 种子数据 -> $TARGET"
        docker exec "$PG_CONTAINER" pg_dump \
            -U "$PG_USER" -d "$PG_DB" \
            --data-only \
            --schema=app \
            --no-owner \
            --no-privileges \
            > "$TARGET"
        echo "      OK! 种子数据: $TARGET ($(du -h "$TARGET" | cut -f1))"
    else
        TARGET_DIR="$PROJECT_ROOT/backup/$TIMESTAMP"
        mkdir -p "$TARGET_DIR"
        echo "==> [1/2] 备份 PostgreSQL 全量 -> $TARGET_DIR/"
        docker exec "$PG_CONTAINER" pg_dump \
            -U "$PG_USER" -d "$PG_DB" \
            --create \
            --schema=app \
            --no-owner \
            --no-privileges \
            > "$TARGET_DIR/postgres_dump.sql"
        echo "      OK! $TARGET_DIR/postgres_dump.sql ($(du -h "$TARGET_DIR/postgres_dump.sql" | cut -f1))"
    fi
fi

# ==================== Nacos 配置备份 ====================
if [ "$BACKUP_NACOS" = true ]; then
    check_container "$MYSQL_CONTAINER"

    if [ "$MODE_SEED" = true ]; then
        # 导出 Nacos config_info 表中的业务配置（INSERT IGNORE，不冲突、可重复执行）
        TARGET="$PROJECT_ROOT/infra/nacos/init/03-nacos-config-seed.sql"
        echo "==> [2/2] 备份 Nacos 配置种子 -> $TARGET"

        cat > "$TARGET" << 'HEADER'
-- ================================================================================
-- Nacos 配置种子数据（由 db-backup.sh --seed 自动生成）
--
-- 说明：
--   此文件包含从运行中环境导出的 config_info 配置。
--   使用 INSERT IGNORE 语法，支持重复执行而不报错。
--   首次启动或 reset 后会自动导入此配置。
--
-- 更新方式：
--   make db-backup  或  ./scripts/db/db-backup.sh --seed
-- ================================================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

HEADER

        # 导出 config_info 表数据为 INSERT IGNORE（幂等）
        docker exec "$MYSQL_CONTAINER" mysqldump \
            -u"$MYSQL_USER" -p"$MYSQL_PWD" \
            nacos config_info \
            --no-create-info \
            --complete-insert \
            --extended-insert=false \
            --where="tenant_id='' OR tenant_id IS NULL" \
            2>/dev/null \
            | sed 's/^INSERT INTO/INSERT IGNORE INTO/g' \
            >> "$TARGET"

        INSERT_COUNT=$(grep -c '^INSERT IGNORE INTO' "$TARGET" 2>/dev/null || echo 0)
        echo "      OK! Nacos 种子: $TARGET ($(du -h "$TARGET" | cut -f1), $INSERT_COUNT 条配置记录)"
    else
        TARGET_DIR="${TARGET_DIR:-$PROJECT_ROOT/backup/$TIMESTAMP}"
        mkdir -p "$TARGET_DIR"
        echo "==> [2/2] 备份 Nacos MySQL 全量 -> $TARGET_DIR/"
        docker exec "$MYSQL_CONTAINER" mysqldump \
            -u"$MYSQL_USER" -p"$MYSQL_PWD" nacos \
            --single-transaction \
            --routines \
            > "$TARGET_DIR/nacos_mysql_full.sql" 2>/dev/null
        echo "      OK! $TARGET_DIR/nacos_mysql_full.sql ($(du -h "$TARGET_DIR/nacos_mysql_full.sql" | cut -f1))"
    fi
fi

# ==================== 汇总 ====================
if [ "$MODE_SEED" = true ]; then
    echo ""
    echo "============================================"
    echo "  种子数据备份完成!"
    echo "--------------------------------------------"
    echo "  提交并共享给团队:"
    echo "    make db-sync"
    echo "  Windows 用户:"
    echo "    scripts\\db\\db-backup.bat --seed && git commit ..."
    echo "============================================"
else
    echo ""
    echo "  备份目录: ${TARGET_DIR:-$PROJECT_ROOT/backup/$TIMESTAMP}/"
fi
