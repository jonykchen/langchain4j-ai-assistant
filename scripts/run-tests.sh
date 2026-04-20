#!/bin/bash
# 测试执行脚本
# 用法: ./scripts/run-tests.sh [type]
# type: unit | integration | e2e | performance | ai | all

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

echo "======================================"
echo " 测试执行脚本"
echo "======================================"

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

run_unit_tests() {
    echo -e "${YELLOW}运行单元测试...${NC}"
    mvn test -Dtest="!*IntegrationTest,!*ContractTest,!*AIModelTest*"
    echo -e "${GREEN}单元测试完成${NC}"
}

run_integration_tests() {
    echo -e "${YELLOW}运行集成测试...${NC}"
    mvn test -Dtest="*IntegrationTest"
    echo -e "${GREEN}集成测试完成${NC}"
}

run_e2e_tests() {
    echo -e "${YELLOW}运行 E2E 测试...${NC}"
    cd frontend
    npm ci
    npx playwright install
    npx playwright test
    cd ..
    echo -e "${GREEN}E2E 测试完成${NC}"
}

run_performance_tests() {
    echo -e "${YELLOW}运行性能测试...${NC}"
    mvn gatling:test
    echo -e "${GREEN}性能测试完成${NC}"
    echo "报告位置: target/gatling-results/"
}

run_ai_tests() {
    echo -e "${YELLOW}运行 AI 模型测试...${NC}"
    mvn test -Dtest="*AIModelTest*"
    echo -e "${GREEN}AI 模型测试完成${NC}"
}

run_all_tests() {
    echo -e "${YELLOW}运行全部测试...${NC}"
    run_unit_tests
    run_integration_tests
    run_e2e_tests
    run_performance_tests
    run_ai_tests
    echo -e "${GREEN}全部测试完成${NC}"
}

# 参数处理
TYPE=${1:-all}

case "$TYPE" in
    unit)
        run_unit_tests
        ;;
    integration)
        run_integration_tests
        ;;
    e2e)
        run_e2e_tests
        ;;
    performance)
        run_performance_tests
        ;;
    ai)
        run_ai_tests
        ;;
    all)
        run_all_tests
        ;;
    *)
        echo "未知的测试类型: $TYPE"
        echo "用法: $0 [unit|integration|e2e|performance|ai|all]"
        exit 1
        ;;
esac

echo "======================================"
echo " 测试执行完成"
echo "======================================"
