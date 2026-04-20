@echo off
REM 测试执行脚本 (Windows)
REM 用法: scripts\run-tests.bat [type]
REM type: unit | integration | e2e | performance | ai | all

setlocal enabledelayedexpansion

echo ======================================
echo  测试执行脚本
echo ======================================

REM 参数处理
set TYPE=%1
if "%TYPE%"=="" set TYPE=all

REM 切换到项目根目录
cd /d "%~dp0.."

if "%TYPE%"=="unit" goto unit
if "%TYPE%"=="integration" goto integration
if "%TYPE%"=="e2e" goto e2e
if "%TYPE%"=="performance" goto performance
if "%TYPE%"=="ai" goto ai
if "%TYPE%"=="all" goto all
goto usage

:unit
echo 运行单元测试...
call mvn test -Dtest="!*IntegrationTest,!*ContractTest,!*AIModelTest*"
echo 单元测试完成
goto end

:integration
echo 运行集成测试...
call mvn test -Dtest="*IntegrationTest"
echo 集成测试完成
goto end

:e2e
echo 运行 E2E 测试...
cd frontend
call npm ci
call npx playwright install
call npx playwright test
cd ..
echo E2E 测试完成
goto end

:performance
echo 运行性能测试...
call mvn gatling:test
echo 性能测试完成
echo 报告位置: target\gatling-results\
goto end

:ai
echo 运行 AI 模型测试...
call mvn test -Dtest="*AIModelTest*"
echo AI 模型测试完成
goto end

:all
echo 运行全部测试...
call mvn test -Dtest="!*IntegrationTest,!*ContractTest,!*AIModelTest*"
call mvn test -Dtest="*IntegrationTest"
cd frontend
call npm ci
call npx playwright install
call npx playwright test
cd ..
call mvn gatling:test
call mvn test -Dtest="*AIModelTest*"
echo 全部测试完成
goto end

:usage
echo 未知的测试类型: %TYPE%
echo 用法: %0 [unit^|integration^|e2e^|performance^|ai^|all]
exit /b 1

:end
echo ======================================
echo  测试执行完成
echo ======================================
endlocal
