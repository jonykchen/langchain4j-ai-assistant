---
description: 运行 Gatling 性能测试
allowed-tools: Bash(mvn:*)
effort: medium
---

## Task

运行 Gatling 性能测试。

步骤：
1. 确保后端服务正在运行
2. 运行性能测试：`mvn gatling:test`
3. 分析测试结果报告
4. 汇报关键指标：响应时间、吞吐量、错误率