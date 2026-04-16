# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供指导。

## 构建和运行命令

```bash
# 构建项目
mvn clean package

# 运行应用
mvn spring-boot:run

# 运行测试
mvn test

# 运行单个测试类
mvn test -Dtest=ClassName

# 运行单个测试方法
mvn test -Dtest=ClassName#methodName
```

## 环境配置

运行前需要设置 `OPENAI_API_KEY` 环境变量。

## 架构概述

这是一个基于 LangChain4j 的 Spring Boot 应用，用于构建 AI 服务。

### 技术栈
- Java 17
- Spring Boot 3.3.6
- LangChain4j 1.13.0（集成 OpenAI）

### 核心依赖

| 依赖 | 用途 |
|------|------|
| `langchain4j-spring-boot-starter` | LangChain4j 核心自动配置 |
| `langchain4j-open-ai-spring-boot-starter` | OpenAI API 集成（GPT 模型） |
| `langchain4j-reactor` | 响应式支持，用于 AI 流式响应（SSE） |
| `spring-boot-starter-webflux` | 响应式 Web 框架，支持非阻塞流式响应 |
| `spring-boot-starter-actuator` | 健康检查和指标端点 |
| `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-zipkin` | 分布式链路追踪 |

### 配置说明

- 服务端口：8082
- 模型：gpt-4o-mini（通过 `application.properties` 配置）
- 已启用 `dev.langchain4j` 包的 DEBUG 日志
- Actuator 端点暴露于 `/actuator/*`

### 流式响应支持

项目同时配置了同步（`chat-model`）和流式（`streaming-chat-model`）两种 OpenAI 客户端。结合 `langchain4j-reactor` 与 WebFlux 端点可实现 Server-Sent Events (SSE) 实时 AI 流式响应。