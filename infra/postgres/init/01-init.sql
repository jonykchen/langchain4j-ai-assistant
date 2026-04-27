-- ==================== PostgreSQL 初始化脚本 ====================
--
-- LangChain4j Agent 工程数据库初始化
-- 四层 Schema 架构：public(扩展) → app(业务) → audit(审计) → archive(归档)
--
-- 版本: 2.0.0
-- 更新时间: 2025-04

-- ==================== 扩展安装 (public schema) ====================

-- UUID 生成
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- 密码加密
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
-- 向量存储（RAG）
CREATE EXTENSION IF NOT EXISTS "vector";
-- 全文搜索
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ==================== Schema 配置 ====================

-- 创建应用 schema（核心业务数据）
CREATE SCHEMA IF NOT EXISTS app;

-- 创建审计 schema（审计日志，敏感操作记录）
CREATE SCHEMA IF NOT EXISTS audit;

-- 创建归档 schema（历史数据归档，冷存储）
CREATE SCHEMA IF NOT EXISTS archive;

-- 设置默认搜索路径：app 优先，依次查找 audit, archive, public
ALTER DATABASE langchain4j SET search_path TO app, audit, archive, public;

-- Schema 注释
COMMENT ON SCHEMA app IS '应用业务 Schema - 核心业务数据（用户、会话、消息、文档、测试任务）';
COMMENT ON SCHEMA audit IS '审计 Schema - 审计日志（Token用量、工具执行、Agent操作）';
COMMENT ON SCHEMA archive IS '归档 Schema - 历史数据（超过保留期的审计日志、已删除的会话）';

-- ==================== app Schema：用户表 ====================

CREATE TABLE IF NOT EXISTS app.users (
    id              VARCHAR(64)    PRIMARY KEY,
    username        VARCHAR(100)   UNIQUE NOT NULL,
    email           VARCHAR(255)   UNIQUE,
    nickname        VARCHAR(100),
    avatar          VARCHAR(500),
    provider        VARCHAR(20)    NOT NULL,
    provider_id     VARCHAR(100),
    password        VARCHAR(100),
    role            VARCHAR(20)    NOT NULL DEFAULT 'USER',
    status          VARCHAR(20)    DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP,
    last_login_at   TIMESTAMPTZ,
    version         BIGINT         DEFAULT 0
);

COMMENT ON TABLE app.users IS '用户表 - 存储系统用户信息';
COMMENT ON COLUMN app.users.id IS '用户ID，UUID格式';
COMMENT ON COLUMN app.users.username IS '用户名，唯一，用于登录';
COMMENT ON COLUMN app.users.email IS '邮箱地址，唯一，用于通知和找回密码';
COMMENT ON COLUMN app.users.password IS '密码，BCrypt加密存储，OAuth用户可为空';
COMMENT ON COLUMN app.users.role IS '用户角色：USER, ADMIN, VIP';
COMMENT ON COLUMN app.users.status IS '账户状态：ACTIVE-正常, DISABLED-禁用, LOCKED-锁定';

CREATE INDEX IF NOT EXISTS idx_users_provider ON app.users(provider, provider_id);
CREATE INDEX IF NOT EXISTS idx_users_email ON app.users(email);
CREATE INDEX IF NOT EXISTS idx_users_status ON app.users(status);

-- ==================== app Schema：会话表 ====================

CREATE TABLE IF NOT EXISTS app.conversations (
    id              VARCHAR(36)    PRIMARY KEY,
    user_id         VARCHAR(64)    NOT NULL REFERENCES app.users(id) ON DELETE CASCADE,
    title           VARCHAR(255),
    model           VARCHAR(100),
    system_prompt   TEXT,
    temperature     DECIMAL(3,2)  DEFAULT 0.7,
    max_tokens      INTEGER       DEFAULT 4096,
    metadata        JSONB,
    status          VARCHAR(20)   DEFAULT 'active',
    created_at      TIMESTAMPTZ   DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ   DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE app.conversations IS '会话表 - 存储用户对话会话';
COMMENT ON COLUMN app.conversations.id IS '会话ID，UUID格式';
COMMENT ON COLUMN app.conversations.user_id IS '所属用户ID，外键关联users表';
COMMENT ON COLUMN app.conversations.title IS '会话标题，自动生成或用户设置';
COMMENT ON COLUMN app.conversations.model IS '使用的AI模型名称';
COMMENT ON COLUMN app.conversations.status IS '状态：active-活跃, archived-已归档, deleted-已删除';

CREATE INDEX IF NOT EXISTS idx_conversations_user ON app.conversations(user_id);
CREATE INDEX IF NOT EXISTS idx_conversations_status ON app.conversations(status);
CREATE INDEX IF NOT EXISTS idx_conversations_created ON app.conversations(created_at DESC);

-- ==================== app Schema：消息表 ====================

CREATE TABLE IF NOT EXISTS app.messages (
    id              BIGSERIAL      PRIMARY KEY,
    conversation_id VARCHAR(36)    NOT NULL REFERENCES app.conversations(id) ON DELETE CASCADE,
    role            VARCHAR(20)    NOT NULL,
    content         TEXT,
    token_count     INTEGER        DEFAULT 0,
    model           VARCHAR(100),
    finish_reason   VARCHAR(50),
    metadata        JSONB,
    created_at      TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE app.messages IS '消息表 - 存储会话中的每条消息';
COMMENT ON COLUMN app.messages.role IS '消息角色：system-系统, user-用户, assistant-助手, tool-工具';
COMMENT ON COLUMN app.messages.token_count IS 'token数量，用于计费统计';

CREATE INDEX IF NOT EXISTS idx_messages_conversation ON app.messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON app.messages(created_at);

-- ==================== app Schema：RAG文档表 ====================

CREATE TABLE IF NOT EXISTS app.documents (
    id              VARCHAR(36)    PRIMARY KEY,
    user_id         VARCHAR(64)    REFERENCES app.users(id) ON DELETE SET NULL,
    filename        VARCHAR(255)  NOT NULL,
    file_type       VARCHAR(20)    NOT NULL,
    file_size       BIGINT,
    content_hash    VARCHAR(64),
    status          VARCHAR(20)    DEFAULT 'active',
    metadata        JSONB,
    embedding       vector(1536),
    created_at      TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE app.documents IS 'RAG文档表 - 存储用于检索增强生成的文档';

CREATE INDEX IF NOT EXISTS idx_documents_user ON app.documents(user_id);
CREATE INDEX IF NOT EXISTS idx_documents_status ON app.documents(status);
CREATE INDEX IF NOT EXISTS idx_documents_type ON app.documents(file_type);

-- ==================== app Schema：文档分块表 ====================

CREATE TABLE IF NOT EXISTS app.document_chunks (
    id              VARCHAR(36)    PRIMARY KEY,
    document_id     VARCHAR(36)    NOT NULL REFERENCES app.documents(id) ON DELETE CASCADE,
    content         TEXT           NOT NULL,
    chunk_index     INTEGER        NOT NULL,
    token_count     INTEGER        DEFAULT 0,
    embedding       vector(1536),
    metadata        JSONB,
    created_at      TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE app.document_chunks IS 'RAG文档分块表 - 存储文档分割后的文本块及向量';

CREATE INDEX IF NOT EXISTS idx_chunks_document ON app.document_chunks(document_id);
CREATE INDEX IF NOT EXISTS idx_chunks_index ON app.document_chunks(document_id, chunk_index);

-- 向量索引（HNSW算法）
CREATE INDEX IF NOT EXISTS idx_chunks_embedding
    ON app.document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- ==================== app Schema：测试任务表 ====================

CREATE TABLE IF NOT EXISTS app.test_jobs (
    id              VARCHAR(36)    PRIMARY KEY,
    test_type       VARCHAR(20)    NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    start_time      TIMESTAMPTZ,
    end_time        TIMESTAMPTZ,
    message         TEXT,
    progress        INTEGER        DEFAULT 0,
    triggered_by    VARCHAR(50),
    metadata        JSONB          DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE app.test_jobs IS '测试任务表 - 跟踪每次测试执行';
COMMENT ON COLUMN app.test_jobs.test_type IS '测试类型：E2E, PERFORMANCE, AI_MODEL';
COMMENT ON COLUMN app.test_jobs.status IS '状态：PENDING, RUNNING, COMPLETED, FAILED, CANCELLED';

CREATE INDEX IF NOT EXISTS idx_test_jobs_type ON app.test_jobs(test_type);
CREATE INDEX IF NOT EXISTS idx_test_jobs_status ON app.test_jobs(status);
CREATE INDEX IF NOT EXISTS idx_test_jobs_created ON app.test_jobs(created_at DESC);

-- ==================== app Schema：E2E测试结果表 ====================

CREATE TABLE IF NOT EXISTS app.e2e_test_results (
    id               BIGSERIAL      PRIMARY KEY,
    job_id           VARCHAR(36)    NOT NULL REFERENCES app.test_jobs(id) ON DELETE CASCADE,
    test_name        VARCHAR(255)   NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    duration_ms      BIGINT,
    assertions_passed INTEGER       DEFAULT 0,
    assertions_failed INTEGER       DEFAULT 0,
    error_message    TEXT,
    created_at       TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE app.e2e_test_results IS 'E2E测试结果表';

CREATE INDEX IF NOT EXISTS idx_e2e_results_job ON app.e2e_test_results(job_id);
CREATE INDEX IF NOT EXISTS idx_e2e_results_status ON app.e2e_test_results(status);

-- ==================== app Schema：性能测试结果表 ====================

CREATE TABLE IF NOT EXISTS app.performance_test_results (
    id                BIGSERIAL      PRIMARY KEY,
    job_id            VARCHAR(36)    NOT NULL REFERENCES app.test_jobs(id) ON DELETE CASCADE,
    simulation        VARCHAR(100)   NOT NULL,
    requests          BIGINT         DEFAULT 0,
    success_rate      DECIMAL(5,2),
    avg_response_time BIGINT,
    max_response_time BIGINT,
    p95_response_time BIGINT,
    p99_response_time BIGINT,
    start_time        TIMESTAMPTZ,
    end_time          TIMESTAMPTZ,
    created_at        TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE app.performance_test_results IS '性能测试结果表';

CREATE INDEX IF NOT EXISTS idx_perf_results_job ON app.performance_test_results(job_id);
CREATE INDEX IF NOT EXISTS idx_perf_results_simulation ON app.performance_test_results(simulation);

-- ==================== app Schema：AI模型测试结果表 ====================

CREATE TABLE IF NOT EXISTS app.ai_model_test_results (
    id              BIGSERIAL      PRIMARY KEY,
    job_id          VARCHAR(36)    NOT NULL REFERENCES app.test_jobs(id) ON DELETE CASCADE,
    test_case_id    VARCHAR(100)   NOT NULL,
    test_name       VARCHAR(255)   NOT NULL,
    category        VARCHAR(50),
    score           DECIMAL(5,4),
    passed          BOOLEAN        DEFAULT false,
    details         JSONB          DEFAULT '[]'::jsonb,
    response_time   BIGINT,
    actual_output   TEXT,
    created_at      TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE app.ai_model_test_results IS 'AI模型测试结果表';

CREATE INDEX IF NOT EXISTS idx_ai_results_job ON app.ai_model_test_results(job_id);
CREATE INDEX IF NOT EXISTS idx_ai_results_category ON app.ai_model_test_results(category);
CREATE INDEX IF NOT EXISTS idx_ai_results_passed ON app.ai_model_test_results(passed);

-- ==================== app Schema：Agent追踪表 ====================

CREATE TABLE IF NOT EXISTS app.agent_traces (
    id              BIGSERIAL      PRIMARY KEY,
    trace_id        VARCHAR(36)    UNIQUE NOT NULL,
    session_id      VARCHAR(36),
    user_id         VARCHAR(64),
    agent_type      VARCHAR(50),
    question        TEXT,
    answer          TEXT,
    status          VARCHAR(20)    DEFAULT 'running',
    total_tokens    INTEGER        DEFAULT 0,
    total_steps     INTEGER        DEFAULT 0,
    start_time      TIMESTAMPTZ,
    end_time        TIMESTAMPTZ,
    metadata        JSONB,
    created_at      TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE app.agent_traces IS 'Agent执行追踪记录';

CREATE INDEX IF NOT EXISTS idx_trace_session ON app.agent_traces(session_id);
CREATE INDEX IF NOT EXISTS idx_trace_status ON app.agent_traces(status);
CREATE INDEX IF NOT EXISTS idx_trace_user ON app.agent_traces(user_id);
CREATE INDEX IF NOT EXISTS idx_trace_start_time ON app.agent_traces(start_time DESC);

-- ==================== app Schema：Agent追踪Span表 ====================

CREATE TABLE IF NOT EXISTS app.agent_trace_spans (
    id              BIGSERIAL      PRIMARY KEY,
    trace_id        VARCHAR(36)    NOT NULL REFERENCES app.agent_traces(trace_id) ON DELETE CASCADE,
    span_id         VARCHAR(36)    NOT NULL,
    parent_span_id  VARCHAR(36),
    name            VARCHAR(200)   NOT NULL,
    span_type       VARCHAR(50),
    status          VARCHAR(20)    DEFAULT 'running',
    input           TEXT,
    output          TEXT,
    tokens_used     INTEGER        DEFAULT 0,
    duration_ms     BIGINT,
    metadata        JSONB,
    start_time      TIMESTAMPTZ,
    end_time        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE app.agent_trace_spans IS 'Agent执行步骤Span';

CREATE INDEX IF NOT EXISTS idx_span_trace ON app.agent_trace_spans(trace_id);
CREATE INDEX IF NOT EXISTS idx_span_parent ON app.agent_trace_spans(parent_span_id);

-- ==================== app Schema：Agent状态快照表 ====================

CREATE TABLE IF NOT EXISTS app.agent_state_snapshots (
    id              BIGSERIAL      PRIMARY KEY,
    snapshot_id     VARCHAR(36)    UNIQUE NOT NULL,
    trace_id        VARCHAR(36),
    session_id      VARCHAR(36),
    agent_type      VARCHAR(50),
    step_number     INTEGER,
    total_steps     INTEGER,
    state_data      JSONB,
    resumable       BOOLEAN        DEFAULT true,
    status          VARCHAR(20)    DEFAULT 'active',
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE app.agent_state_snapshots IS 'Agent状态快照（断点续传）';

CREATE INDEX IF NOT EXISTS idx_snapshot_session ON app.agent_state_snapshots(session_id);
CREATE INDEX IF NOT EXISTS idx_snapshot_trace ON app.agent_state_snapshots(trace_id);
CREATE INDEX IF NOT EXISTS idx_snapshot_expires ON app.agent_state_snapshots(expires_at);

-- ==================== app Schema：Prompt模板表 ====================

CREATE TABLE IF NOT EXISTS app.prompt_templates (
    id              BIGSERIAL      PRIMARY KEY,
    name            VARCHAR(100)   NOT NULL,
    version         VARCHAR(20)    NOT NULL,
    content         TEXT           NOT NULL,
    description     TEXT,
    variables       JSONB,
    active          BOOLEAN        DEFAULT false,
    ab_test_enabled BOOLEAN        DEFAULT false,
    ab_test_weight  DECIMAL(3,2)   DEFAULT 0.5,
    created_by      VARCHAR(64),
    created_at      TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(name, version)
);

COMMENT ON TABLE app.prompt_templates IS 'Prompt模板版本管理';

CREATE INDEX IF NOT EXISTS idx_prompt_name ON app.prompt_templates(name);
CREATE INDEX IF NOT EXISTS idx_prompt_active ON app.prompt_templates(active);

-- ==================== audit Schema：Token使用日志表 ====================

CREATE TABLE IF NOT EXISTS audit.token_usage_logs (
    id              BIGSERIAL      PRIMARY KEY,
    user_id         VARCHAR(64)    NOT NULL,
    session_id      VARCHAR(36),
    conversation_id VARCHAR(36),
    message_id      BIGINT,
    model_name      VARCHAR(100)   NOT NULL,
    model_provider  VARCHAR(50),
    prompt_tokens   INTEGER        DEFAULT 0,
    completion_tokens INTEGER      DEFAULT 0,
    total_tokens    INTEGER        DEFAULT 0,
    cost            DECIMAL(10,6)  DEFAULT 0,
    currency        VARCHAR(10)    DEFAULT 'USD',
    request_type    VARCHAR(20),
    latency_ms      BIGINT,
    status          VARCHAR(20)    DEFAULT 'success',
    error_message   TEXT,
    trace_id        VARCHAR(36),
    client_ip       VARCHAR(50),
    request_time    TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE audit.token_usage_logs IS 'Token使用日志 - 记录每次API调用的token消耗和费用';
COMMENT ON COLUMN audit.token_usage_logs.status IS '状态：success-成功, error-失败, timeout-超时';

CREATE INDEX IF NOT EXISTS idx_token_usage_user ON audit.token_usage_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_token_usage_time ON audit.token_usage_logs(request_time DESC);
CREATE INDEX IF NOT EXISTS idx_token_usage_model ON audit.token_usage_logs(model_provider);
CREATE INDEX IF NOT EXISTS idx_token_usage_conversation ON audit.token_usage_logs(conversation_id);
CREATE INDEX IF NOT EXISTS idx_token_usage_trace ON audit.token_usage_logs(trace_id);

-- ==================== audit Schema：工具执行审计表 ====================

CREATE TABLE IF NOT EXISTS audit.tool_execution_audits (
    id                  BIGSERIAL      PRIMARY KEY,
    execution_id        VARCHAR(64)    UNIQUE NOT NULL,
    tool_name           VARCHAR(100)   NOT NULL,
    tool_category       VARCHAR(50),
    user_id             VARCHAR(64),
    session_id          VARCHAR(36),
    conversation_id     VARCHAR(36),
    message_id          BIGINT,
    params              TEXT,
    params_hash         VARCHAR(64),
    result              TEXT,
    result_hash         VARCHAR(64),
    success             BOOLEAN        DEFAULT false,
    error_message       VARCHAR(1000),
    execution_time_ms   BIGINT,
    retry_count         INTEGER        DEFAULT 0,
    risk_level          VARCHAR(20),
    confirmation_required BOOLEAN      DEFAULT false,
    confirmed           BOOLEAN        DEFAULT false,
    confirmed_by        VARCHAR(64),
    client_ip           VARCHAR(50),
    executed_at         TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP,
    created_at          TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE audit.tool_execution_audits IS '工具执行审计 - 记录AI工具调用的完整信息';
COMMENT ON COLUMN audit.tool_execution_audits.risk_level IS '风险等级：LOW, MEDIUM, HIGH, CRITICAL';

CREATE INDEX IF NOT EXISTS idx_tool_audit_tool ON audit.tool_execution_audits(tool_name);
CREATE INDEX IF NOT EXISTS idx_tool_audit_user ON audit.tool_execution_audits(user_id);
CREATE INDEX IF NOT EXISTS idx_tool_audit_session ON audit.tool_execution_audits(session_id);
CREATE INDEX IF NOT EXISTS idx_tool_audit_time ON audit.tool_execution_audits(executed_at DESC);
CREATE INDEX IF NOT EXISTS idx_tool_audit_status ON audit.tool_execution_audits(success);

-- ==================== audit Schema：Agent审计日志表 ====================

CREATE TABLE IF NOT EXISTS audit.agent_audit_logs (
    id              BIGSERIAL      PRIMARY KEY,
    event_id        VARCHAR(36)    UNIQUE NOT NULL,
    trace_id        VARCHAR(36),
    user_id         VARCHAR(64),
    agent_name      VARCHAR(50),
    event_type      VARCHAR(30)    NOT NULL,
    event_data      JSONB,
    client_ip       VARCHAR(50),
    user_agent      VARCHAR(500),
    timestamp       TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE audit.agent_audit_logs IS 'Agent审计日志 - 记录Agent执行的完整审计信息';
COMMENT ON COLUMN audit.agent_audit_logs.event_type IS '事件类型：EXECUTION_START, EXECUTION_END, TOOL_CALL, CONFIRMATION_REQUIRED';

CREATE INDEX IF NOT EXISTS idx_audit_trace ON audit.agent_audit_logs(trace_id);
CREATE INDEX IF NOT EXISTS idx_audit_user ON audit.agent_audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_type_time ON audit.agent_audit_logs(event_type, timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_created ON audit.agent_audit_logs(created_at DESC);

-- ==================== archive Schema：归档表 ====================

-- 归档会话（已删除或超过保留期）
CREATE TABLE IF NOT EXISTS archive.conversations_archive (
    id              VARCHAR(36)    PRIMARY KEY,
    user_id         VARCHAR(64),
    title           VARCHAR(255),
    model           VARCHAR(100),
    system_prompt   TEXT,
    metadata        JSONB,
    original_created_at TIMESTAMPTZ,
    archived_at     TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP,
    retention_days  INTEGER        DEFAULT 365
);

COMMENT ON TABLE archive.conversations_archive IS '已归档会话表';

CREATE INDEX IF NOT EXISTS idx_archive_conv_user ON archive.conversations_archive(user_id);
CREATE INDEX IF NOT EXISTS idx_archive_conv_time ON archive.conversations_archive(archived_at);

-- 归档消息
CREATE TABLE IF NOT EXISTS archive.messages_archive (
    id              BIGSERIAL      PRIMARY KEY,
    conversation_id VARCHAR(36),
    role            VARCHAR(20),
    content         TEXT,
    token_count     INTEGER,
    model           VARCHAR(100),
    metadata        JSONB,
    original_created_at TIMESTAMPTZ,
    archived_at     TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE archive.messages_archive IS '已归档消息表';

CREATE INDEX IF NOT EXISTS idx_archive_msg_conv ON archive.messages_archive(conversation_id);

-- 归档审计日志（超过保留期）
CREATE TABLE IF NOT EXISTS audit.token_usage_logs_archive (
    id              BIGSERIAL      PRIMARY KEY,
    original_id     BIGINT,
    user_id         VARCHAR(64),
    model_name      VARCHAR(100),
    total_tokens    INTEGER,
    cost            DECIMAL(10,6),
    request_time    TIMESTAMPTZ,
    archived_at     TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE audit.token_usage_logs_archive IS 'Token使用日志归档表';

CREATE INDEX IF NOT EXISTS idx_archive_token_time ON audit.token_usage_logs_archive(archived_at);

-- 归档Agent审计日志
CREATE TABLE IF NOT EXISTS audit.agent_audit_logs_archive (
    id              BIGSERIAL      PRIMARY KEY,
    original_id     BIGINT,
    event_id        VARCHAR(36),
    trace_id        VARCHAR(36),
    user_id         VARCHAR(64),
    event_type      VARCHAR(30),
    event_data      JSONB,
    timestamp       TIMESTAMPTZ,
    archived_at     TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE audit.agent_audit_logs_archive IS 'Agent审计日志归档表';

CREATE INDEX IF NOT EXISTS idx_archive_agent_time ON audit.agent_audit_logs_archive(archived_at);

-- ==================== 触发器 ====================

-- 更新时间触发器函数
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 为需要的表创建触发器
DROP TRIGGER IF EXISTS trigger_users_updated_at ON app.users;
CREATE TRIGGER trigger_users_updated_at
    BEFORE UPDATE ON app.users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

DROP TRIGGER IF EXISTS trigger_conversations_updated_at ON app.conversations;
CREATE TRIGGER trigger_conversations_updated_at
    BEFORE UPDATE ON app.conversations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

DROP TRIGGER IF EXISTS trigger_documents_updated_at ON app.documents;
CREATE TRIGGER trigger_documents_updated_at
    BEFORE UPDATE ON app.documents
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- ==================== 权限授权 ====================

-- 授予 app schema 权限
GRANT ALL PRIVILEGES ON SCHEMA app TO langchain4j;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA app TO langchain4j;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA app TO langchain4j;

-- 授予 audit schema 权限（应用只能插入和查询，不能删除）
GRANT USAGE ON SCHEMA audit TO langchain4j;
GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA audit TO langchain4j;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA audit TO langchain4j;

-- 授予 archive schema 权限（应用只能插入和查询）
GRANT USAGE ON SCHEMA archive TO langchain4j;
GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA archive TO langchain4j;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA archive TO langchain4j;

-- 设置默认权限（未来创建的表）
ALTER DEFAULT PRIVILEGES IN SCHEMA app GRANT ALL ON TABLES TO langchain4j;
ALTER DEFAULT PRIVILEGES IN SCHEMA app GRANT ALL ON SEQUENCES TO langchain4j;
ALTER DEFAULT PRIVILEGES IN SCHEMA audit GRANT SELECT, INSERT ON TABLES TO langchain4j;
ALTER DEFAULT PRIVILEGES IN SCHEMA audit GRANT USAGE, SELECT ON SEQUENCES TO langchain4j;
ALTER DEFAULT PRIVILEGES IN SCHEMA archive GRANT SELECT, INSERT ON TABLES TO langchain4j;
ALTER DEFAULT PRIVILEGES IN SCHEMA archive GRANT USAGE, SELECT ON SEQUENCES TO langchain4j;

-- ==================== 初始数据 ====================

-- 插入默认管理员用户（密码：REDACTED_ADMIN_PASSWORD，BCrypt加密）
INSERT INTO app.users (id, username, email, password, nickname, role, status, provider)
VALUES ('admin-001', 'admin', 'admin@example.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'Administrator', 'ADMIN', 'ACTIVE', 'CUSTOM')
ON CONFLICT (id) DO NOTHING;

-- ==================== 数据归档函数 ====================

-- 归档过期的审计日志（保留90天）
CREATE OR REPLACE FUNCTION archive_old_audit_logs(days_to_keep INTEGER DEFAULT 90)
RETURNS INTEGER AS $$
DECLARE
    archived_count INTEGER := 0;
    cutoff_date TIMESTAMPTZ;
BEGIN
    cutoff_date := CURRENT_TIMESTAMP - (days_to_keep || ' days')::INTERVAL;

    -- 归档 Token 使用日志
    INSERT INTO audit.token_usage_logs_archive (original_id, user_id, model_name, total_tokens, cost, request_time)
    SELECT id, user_id, model_name, total_tokens, cost, request_time
    FROM audit.token_usage_logs
    WHERE request_time < cutoff_date;

    GET DIAGNOSTICS archived_count = ROW_COUNT;

    -- 删除已归档的记录
    DELETE FROM audit.token_usage_logs WHERE request_time < cutoff_date;

    RETURN archived_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION archive_old_audit_logs IS '归档超过指定天数的审计日志';

-- 归档已删除的会话（保留365天）
CREATE OR REPLACE FUNCTION archive_deleted_conversations(days_to_keep INTEGER DEFAULT 365)
RETURNS INTEGER AS $$
DECLARE
    archived_count INTEGER := 0;
    cutoff_date TIMESTAMPTZ;
BEGIN
    cutoff_date := CURRENT_TIMESTAMP - (days_to_keep || ' days')::INTERVAL;

    -- 归档已删除的会话
    INSERT INTO archive.conversations_archive (id, user_id, title, model, system_prompt, metadata, original_created_at)
    SELECT id, user_id, title, model, system_prompt, metadata, created_at
    FROM app.conversations
    WHERE status = 'deleted' AND updated_at < cutoff_date;

    GET DIAGNOSTICS archived_count = ROW_COUNT;

    -- 归档相关消息
    INSERT INTO archive.messages_archive (conversation_id, role, content, token_count, model, metadata, original_created_at)
    SELECT m.conversation_id, m.role, m.content, m.token_count, m.model, m.metadata, m.created_at
    FROM app.messages m
    INNER JOIN app.conversations c ON c.id = m.conversation_id
    WHERE c.status = 'deleted' AND c.updated_at < cutoff_date;

    -- 删除已归档的会话和消息
    DELETE FROM app.messages WHERE conversation_id IN (
        SELECT id FROM app.conversations WHERE status = 'deleted' AND updated_at < cutoff_date
    );
    DELETE FROM app.conversations WHERE status = 'deleted' AND updated_at < cutoff_date;

    RETURN archived_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION archive_deleted_conversations IS '归档已删除的会话';
