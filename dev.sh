#!/bin/bash
# ==================== 开发环境启动脚本 ====================
#
# 用法：
#   ./dev.sh [命令]
#
# 命令：
#   start     - 启动所有开发服务
#   stop      - 停止所有服务
#   restart   - 重启所有服务
#   status    - 查看服务状态
#   logs      - 查看日志
#   reset     - 重置环境（清除所有数据）
#   init      - 初始化环境（创建 .env 文件）

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目根目录
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

# 配置文件
COMPOSE_FILE="docker-compose.dev.yml"
ENV_FILE=".env"

# 打印帮助信息
print_help() {
    echo -e "${BLUE}==================== LangChain4j Agent 开发环境 ====================${NC}"
    echo ""
    echo "用法: ./dev.sh [命令]"
    echo ""
    echo "命令:"
    echo "  start       启动所有开发服务（基础设施）"
    echo "  start-all   启动所有服务（包括可选服务）"
    echo "  stop        停止所有服务"
    echo "  restart     重启所有服务"
    echo "  status      查看服务状态"
    echo "  logs [服务] 查看日志（可指定服务名）"
    echo "  reset       重置环境（清除所有数据）"
    echo "  init        初始化环境（创建 .env 文件）"
    echo "  ps          查看运行中的容器"
    echo "  exec [服务] 进入容器执行命令"
    echo ""
    echo "可选服务profiles:"
    echo "  --profile gpu      启用 GPU 版 Ollama"
    echo "  --profile cpu      启用 CPU 版 Ollama"
    echo "  --profile vector   启用 Qdrant 向量数据库"
    echo "  --profile milvus   启用 Milvus 向量数据库"
    echo "  --profile mq       启用 RabbitMQ 消息队列"
    echo ""
    echo "示例:"
    echo "  ./dev.sh start                    # 启动基础服务"
    echo "  ./dev.sh start --profile cpu      # 启动基础服务 + Ollama CPU 版"
    echo "  ./dev.sh logs redis               # 查看 Redis 日志"
    echo "  ./dev.sh exec postgres bash       # 进入 PostgreSQL 容器"
    echo ""
}

# 检查 Docker 是否运行
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        echo -e "${RED}错误: Docker 未运行，请先启动 Docker${NC}"
        exit 1
    fi
}

# 初始化环境
init_env() {
    if [ ! -f "$ENV_FILE" ]; then
        echo -e "${YELLOW}正在创建 .env 文件...${NC}"
        cp .env.example .env
        echo -e "${GREEN}已创建 .env 文件，请编辑填入你的 API Keys${NC}"
        echo -e "${BLUE}提示: 编辑 .env 文件，设置 DASHSCOPE_API_KEY 等变量${NC}"
    else
        echo -e "${GREEN}.env 文件已存在${NC}"
    fi

    # 创建数据目录
    mkdir -p data/{postgres,redis,nacos,nacos-db,prometheus,grafana,ollama,qdrant,etcd,minio,milvus,rabbitmq}
    echo -e "${GREEN}已创建 data 目录结构${NC}"
}

# 启动服务
start_services() {
    check_docker
    init_env

    local profiles=""
    while [[ $# -gt 0 ]]; do
        case $1 in
            --profile)
                profiles="$profiles --profile $2"
                shift 2
                ;;
            *)
                shift
                ;;
        esac
    done

    echo -e "${BLUE}正在启动开发环境...${NC}"
    docker compose -f $COMPOSE_FILE up -d $profiles

    echo ""
    echo -e "${GREEN}==================== 服务已启动 ====================${NC}"
    echo -e "${BLUE}基础设施:${NC}"
    echo -e "  PostgreSQL:    localhost:5432 (用户: langchain4j, 密码: REDACTED_DB_PASSWORD)"
    echo -e "  Redis:         localhost:6379"
    echo -e "  Nacos:         http://localhost:8848/nacos (用户: nacos, 密码: nacos)"
    echo ""
    echo -e "${BLUE}可观测性:${NC}"
    echo -e "  Zipkin:        http://localhost:9411"
    echo -e "  Prometheus:    http://localhost:9090"
    echo -e "  Grafana:       http://localhost:3001 (用户: admin, 密码: REDACTED_ADMIN_PASSWORD)"
    echo ""
    echo -e "${BLUE}管理工具:${NC}"
    echo -e "  Adminer:       http://localhost:8080 (数据库管理)"
    echo -e "  Redis Commander: http://localhost:8081 (Redis管理)"
    echo ""
    echo -e "${YELLOW}提示: 运行 'mvn spring-boot:run' 启动后端应用${NC}"
}

# 启动所有服务（包括可选）
start_all() {
    echo -e "${BLUE}正在启动所有服务（包括可选服务）...${NC}"
    docker compose -f $COMPOSE_FILE --profile cpu --profile vector --profile mq up -d
}

# 停止服务
stop_services() {
    echo -e "${YELLOW}正在停止所有服务...${NC}"
    docker compose -f $COMPOSE_FILE down
    echo -e "${GREEN}所有服务已停止${NC}"
}

# 重启服务
restart_services() {
    stop_services
    start_services "$@"
}

# 查看状态
show_status() {
    echo -e "${BLUE}==================== 服务状态 ====================${NC}"
    docker compose -f $COMPOSE_FILE ps
}

# 查看日志
show_logs() {
    local service=$1
    if [ -z "$service" ]; then
        docker compose -f $COMPOSE_FILE logs -f --tail=100
    else
        docker compose -f $COMPOSE_FILE logs -f --tail=100 "$service"
    fi
}

# 重置环境
reset_env() {
    echo -e "${RED}警告: 这将删除所有数据！${NC}"
    read -p "确认继续? (y/N): " confirm
    if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
        echo -e "${YELLOW}正在停止并删除所有容器和卷...${NC}"
        docker compose -f $COMPOSE_FILE down -v --remove-orphans
        echo -e "${YELLOW}正在删除数据目录...${NC}"
        rm -rf data/
        echo -e "${GREEN}环境已重置，运行 './dev.sh init' 重新初始化${NC}"
    else
        echo -e "${BLUE}已取消${NC}"
    fi
}

# 进入容器
exec_container() {
    local service=$1
    shift
    docker compose -f $COMPOSE_FILE exec "$service" "$@"
}

# 主命令处理
case "${1:-help}" in
    start)
        shift
        start_services "$@"
        ;;
    start-all)
        start_all
        ;;
    stop)
        stop_services
        ;;
    restart)
        shift
        restart_services "$@"
        ;;
    status|ps)
        show_status
        ;;
    logs)
        shift
        show_logs "$@"
        ;;
    reset)
        reset_env
        ;;
    init)
        init_env
        ;;
    exec)
        shift
        exec_container "$@"
        ;;
    help|--help|-h)
        print_help
        ;;
    *)
        echo -e "${RED}未知命令: $1${NC}"
        print_help
        exit 1
        ;;
esac
