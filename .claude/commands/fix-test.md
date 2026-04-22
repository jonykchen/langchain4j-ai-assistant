---
description: 运行指定测试并分析失败原因
allowed-tools: Bash(mvn:*), Bash(cat:*), Read
effort: medium
---

## Task

运行测试 $ARGUMENTS，分析失败原因并修复代码。

步骤：
1. 运行 `mvn test -Dtest=$ARGUMENTS`
2. 如果测试失败，查看 `target/surefire-reports/` 下的报告
3. 分析失败原因
4. 定位并修复代码问题
5. 重新运行测试确认通过