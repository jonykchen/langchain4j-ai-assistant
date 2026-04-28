#!/bin/bash
# ================================================================================
# Nacos 配置自动初始化脚本
# ================================================================================
# 功能：等待 Nacos 启动后，自动导入配置文件
# 用法：在 docker-compose 中作为 init 容器运行
# ================================================================================

set -e

NACOS_HOST="${NACOS_HOST:-nacos}"
NACOS_PORT="${NACOS_PORT:-8848}"
NACOS_URL="http://${NACOS_HOST}:${NACOS_PORT}/nacos"
CONFIG_DIR="${CONFIG_DIR:-/config}"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

# 等待 Nacos 就绪
wait_for_nacos() {
    local max_retries=60
    local retry_interval=5
    local count=0

    log "等待 Nacos 服务就绪..."
    while [ $count -lt $max_retries ]; do
        if curl -sf "${NACOS_URL}/v1/console/health/readiness" > /dev/null 2>&1; then
            log "Nacos 服务已就绪"
            return 0
        fi
        count=$((count + 1))
        log "等待 Nacos 启动... ($count/$max_retries)"
        sleep $retry_interval
    done

    log "错误：Nacos 服务启动超时"
    return 1
}

# 发布单个配置
publish_config() {
    local data_id="$1"
    local group="${2:-DEFAULT_GROUP}"
    local content="$3"
    local tenant="${4:-}"

    log "检查配置: ${data_id}"

    # 检查配置是否存在（Nacos API 返回配置内容，不存在则返回 404 或空）
    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" "${NACOS_URL}/v1/cs/configs?dataId=${data_id}&group=${group}&tenant=${tenant}" 2>/dev/null || echo "000")

    if [ "$http_code" = "200" ]; then
        log "配置 ${data_id} 已存在，跳过"
        return 0
    fi

    log "发布配置: ${data_id}"

    # 发布配置
    local response
    response=$(curl -sf -X POST "${NACOS_URL}/v1/cs/configs" \
        -d "dataId=${data_id}" \
        -d "group=${group}" \
        -d "tenant=${tenant}" \
        --data-urlencode "content=${content}" \
        2>/dev/null || echo "false")

    if [ "$response" = "true" ]; then
        log "配置 ${data_id} 发布成功"
        return 0
    else
        log "警告：配置 ${data_id} 发布失败，响应: ${response}"
        return 1
    fi
}

# 从文件发布配置
publish_config_from_file() {
    local file="$1"
    local data_id=$(basename "$file")
    local group="${2:-DEFAULT_GROUP}"

    if [ -f "$file" ]; then
        local content=$(cat "$file")
        publish_config "$data_id" "$group" "$content"
    else
        log "警告：配置文件不存在: ${file}"
        return 1
    fi
}

# 主流程
main() {
    log "开始初始化 Nacos 配置..."

    # 等待 Nacos 就绪
    wait_for_nacos

    # 导入配置文件
    local config_files=(
        "common.properties"
        "langchain4j-chat.properties"
        "langchain4j-chat-dev.properties"
        "langchain4j-chat-prod.properties"
    )

    local success=0
    local failed=0

    for config_file in "${config_files[@]}"; do
        if publish_config_from_file "${CONFIG_DIR}/${config_file}"; then
            success=$((success + 1))
        else
            failed=$((failed + 1))
        fi
    done

    log "配置初始化完成: 成功 ${success}, 失败 ${failed}"

    if [ $failed -gt 0 ]; then
        exit 1
    fi
}

main "$@"
