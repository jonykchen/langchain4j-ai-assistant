-- ================================================================================
-- LangChain4j AI Chat 应用 - 数据库 Schema（PostgreSQL）
-- ================================================================================
-- 说明：此文件由 Spring Boot 在启动时自动执行
-- 完整的表结构定义请使用 Docker 初始化脚本：infra/postgres/init/01-init.sql
--
-- 四层 Schema 架构：
--   public  → PostgreSQL 扩展 (uuid-ossp, pgcrypto, vector, pg_trgm)
--   app     → 核心业务数据 (users, conversations, messages, documents, test_jobs, agent_traces)
--   audit   → 审计日志 (token_usage_logs, tool_execution_audits, agent_audit_logs)
--   archive → 历史归档 (conversations_archive, messages_archive, audit_logs_archive)
-- ================================================================================

-- ==================== 扩展安装 (public schema) ====================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ==================== Schema 创建 ====================

-- 创建应用 schema（核心业务数据）
CREATE SCHEMA IF NOT EXISTS app;

-- 创建审计 schema（审计日志）
CREATE SCHEMA IF NOT EXISTS audit;

-- 创建归档 schema（历史数据）
CREATE SCHEMA IF NOT EXISTS archive;

-- 设置默认搜索路径
-- 注意：Docker 环境会通过 ALTER DATABASE 覆盖此设置
-- 本地开发时通过此配置确保正确查找表

-- ==================== 说明 ====================
--
-- 表结构由以下方式管理：
-- 1. Docker 部署：使用 infra/postgres/init/01-init.sql 初始化
-- 2. 本地开发：由 Hibernate ddl-auto=update 自动创建（基于实体类）
--
-- 实体类的 schema 属性：
--   - app schema: User, TestJob, E2ETestResult, PerformanceTestResult,
--                 AIModelTestResultEntity, AgentTrace, AgentTraceSpan,
--                 AgentStateSnapshot, PromptTemplateEntity
--   - audit schema: TokenUsageLog, ToolExecutionAudit, AgentAuditLog
--
-- 归档表（archive schema）：
--   - conversations_archive: 已删除的会话归档
--   - messages_archive: 已删除的消息归档
--   - audit_logs_archive: 超过保留期的审计日志归档
--
-- 数据归档策略：
--   - 审计日志：保留 90 天，超期归档
--   - 已删除会话：保留 365 天，超期归档
--   - 归档函数：archive_old_audit_logs(), archive_deleted_conversations()
--
-- 权限配置：
--   - app schema: 应用用户拥有全部权限
--   - audit schema: 应用用户只能 SELECT, INSERT（不能 DELETE）
--   - archive schema: 应用用户只能 SELECT, INSERT
--
-- ================================================================================
