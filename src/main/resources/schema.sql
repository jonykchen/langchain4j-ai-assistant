-- ================================================================================
-- LangChain4j AI Chat 应用 - 数据库 Schema（PostgreSQL）
-- ================================================================================
-- 说明：此文件由 Spring Boot 在启动时自动执行（defer-datasource-initialization=true）
-- Docker 部署时建议使用 infra/postgres/init/01-init.sql 初始化（更完整，含注释和扩展）
-- ================================================================================

-- 扩展安装
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "vector";

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id              VARCHAR(64)     PRIMARY KEY,
    username        VARCHAR(100)    UNIQUE NOT NULL,
    email           VARCHAR(255)    UNIQUE,
    nickname        VARCHAR(100),
    avatar          VARCHAR(500),
    provider        VARCHAR(20)     NOT NULL,
    provider_id     VARCHAR(100),
    password        VARCHAR(100),
    role            VARCHAR(20)     NOT NULL DEFAULT 'USER',
    created_at      TIMESTAMP       NOT NULL,
    last_login_at   TIMESTAMP,
    version         BIGINT          DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_users_provider ON users(provider, provider_id);

-- Token 黑名单表
CREATE TABLE IF NOT EXISTS token_blacklist (
    id              SERIAL          PRIMARY KEY,
    token_hash      VARCHAR(128)    NOT NULL,
    user_id         VARCHAR(64)     NOT NULL,
    expires_at      TIMESTAMP       NOT NULL,
    created_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_blacklist_token ON token_blacklist(token_hash);
CREATE INDEX IF NOT EXISTS idx_blacklist_expires ON token_blacklist(expires_at);

-- Token 使用记录表
CREATE TABLE IF NOT EXISTS token_usage_logs (
    id                SERIAL          PRIMARY KEY,
    user_id           VARCHAR(64)     NOT NULL,
    session_id        VARCHAR(64),
    model_name        VARCHAR(50)     NOT NULL,
    prompt_tokens     INT             NOT NULL,
    completion_tokens INT             NOT NULL,
    total_tokens      INT             NOT NULL,
    cost              DECIMAL(10, 6)  NOT NULL,
    currency          VARCHAR(10)     DEFAULT 'USD',
    request_type      VARCHAR(20),
    created_at        TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_usage_user_date ON token_usage_logs(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_usage_date ON token_usage_logs(created_at);

-- 用户配额表
CREATE TABLE IF NOT EXISTS user_quotas (
    user_id               VARCHAR(64)     PRIMARY KEY,
    daily_token_limit     INT             DEFAULT 100000,
    monthly_token_limit   INT             DEFAULT 2000000,
    daily_cost_limit      DECIMAL(10, 2)  DEFAULT 10.00,
    monthly_cost_limit    DECIMAL(10, 2)  DEFAULT 200.00,
    created_at            TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

-- 工具执行审计表
CREATE TABLE IF NOT EXISTS tool_execution_audits (
    id                    SERIAL          PRIMARY KEY,
    execution_id          VARCHAR(64)     UNIQUE NOT NULL,
    tool_name             VARCHAR(100)    NOT NULL,
    session_id            VARCHAR(64),
    user_id               VARCHAR(64),
    params                TEXT,
    success               BOOLEAN         NOT NULL DEFAULT FALSE,
    result                TEXT,
    error_message         VARCHAR(1000),
    execution_time_ms     BIGINT          DEFAULT 0,
    risk_level            VARCHAR(20),
    confirmation_required BOOLEAN         DEFAULT FALSE,
    confirmed             BOOLEAN         DEFAULT FALSE,
    confirmed_by          VARCHAR(64),
    executed_at           TIMESTAMP,
    client_ip             VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_tool_audit_tool ON tool_execution_audits(tool_name);
CREATE INDEX IF NOT EXISTS idx_tool_audit_user ON tool_execution_audits(user_id);
CREATE INDEX IF NOT EXISTS idx_tool_audit_session ON tool_execution_audits(session_id);
CREATE INDEX IF NOT EXISTS idx_tool_audit_time ON tool_execution_audits(executed_at);

-- 文档表
CREATE TABLE IF NOT EXISTS documents (
    id            VARCHAR(64)     PRIMARY KEY,
    filename      VARCHAR(255)    NOT NULL,
    file_type     VARCHAR(20)     NOT NULL,
    file_size     BIGINT,
    content_hash  VARCHAR(64),
    metadata      JSONB,
    created_at    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_documents_type ON documents(file_type);
CREATE INDEX IF NOT EXISTS idx_documents_created ON documents(created_at);

-- 文档块表（向量存储）
CREATE TABLE IF NOT EXISTS document_chunks (
    id            VARCHAR(64)     PRIMARY KEY,
    document_id   VARCHAR(64)     NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    content       TEXT            NOT NULL,
    embedding     vector(1536),
    chunk_index   INT             NOT NULL,
    start_index   INT,
    end_index     INT,
    metadata      JSONB,
    created_at    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chunks_document ON document_chunks(document_id);

-- ================================================================================
-- 测试结果持久化表
-- ================================================================================

-- 测试任务表
CREATE TABLE IF NOT EXISTS test_jobs (
    id              VARCHAR(36)     PRIMARY KEY,
    test_type       VARCHAR(20)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    start_time      TIMESTAMPTZ,
    end_time        TIMESTAMPTZ,
    message         TEXT,
    progress        INTEGER         DEFAULT 0,
    triggered_by    VARCHAR(50),
    metadata        JSONB           DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_test_jobs_type ON test_jobs(test_type);
CREATE INDEX IF NOT EXISTS idx_test_jobs_status ON test_jobs(status);
CREATE INDEX IF NOT EXISTS idx_test_jobs_start_time ON test_jobs(start_time DESC);
CREATE INDEX IF NOT EXISTS idx_test_jobs_created ON test_jobs(created_at DESC);

-- E2E 测试结果表
CREATE TABLE IF NOT EXISTS e2e_test_results (
    id              BIGSERIAL       PRIMARY KEY,
    job_id          VARCHAR(36)     NOT NULL REFERENCES test_jobs(id) ON DELETE CASCADE,
    test_name       VARCHAR(255)    NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    duration_ms     BIGINT,
    assertions_passed INTEGER      DEFAULT 0,
    assertions_failed INTEGER      DEFAULT 0,
    error_message   TEXT,
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_e2e_results_job ON e2e_test_results(job_id);
CREATE INDEX IF NOT EXISTS idx_e2e_results_status ON e2e_test_results(status);

-- 性能测试结果表
CREATE TABLE IF NOT EXISTS performance_test_results (
    id              BIGSERIAL       PRIMARY KEY,
    job_id          VARCHAR(36)     NOT NULL REFERENCES test_jobs(id) ON DELETE CASCADE,
    simulation      VARCHAR(100)    NOT NULL,
    requests        BIGINT          DEFAULT 0,
    success_rate    DECIMAL(5,2),
    avg_response_time BIGINT,
    max_response_time BIGINT,
    p95_response_time BIGINT,
    p99_response_time BIGINT,
    start_time      TIMESTAMPTZ,
    end_time        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_perf_results_job ON performance_test_results(job_id);

-- AI 模型测试结果表
CREATE TABLE IF NOT EXISTS ai_model_test_results (
    id              BIGSERIAL       PRIMARY KEY,
    job_id          VARCHAR(36)     NOT NULL REFERENCES test_jobs(id) ON DELETE CASCADE,
    test_case_id    VARCHAR(100)    NOT NULL,
    test_name       VARCHAR(255)    NOT NULL,
    category        VARCHAR(50),
    score           DECIMAL(5,4),
    passed          BOOLEAN         DEFAULT false,
    details         JSONB           DEFAULT '[]'::jsonb,
    response_time   BIGINT,
    actual_output   TEXT,
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_results_job ON ai_model_test_results(job_id);
CREATE INDEX IF NOT EXISTS idx_ai_results_category ON ai_model_test_results(category);
CREATE INDEX IF NOT EXISTS idx_ai_results_passed ON ai_model_test_results(passed);

-- ================================================================================
-- Agent 审计日志表
-- ================================================================================

CREATE TABLE IF NOT EXISTS agent_audit_logs (
    id              BIGSERIAL       PRIMARY KEY,
    event_id        VARCHAR(36)     UNIQUE NOT NULL,
    trace_id        VARCHAR(36),
    user_id         VARCHAR(64),
    agent_name      VARCHAR(50),
    event_type      VARCHAR(30)     NOT NULL,
    timestamp       TIMESTAMPTZ     NOT NULL,
    client_ip       VARCHAR(50),
    user_agent      VARCHAR(500),
    details         JSONB,
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_trace ON agent_audit_logs(trace_id);
CREATE INDEX IF NOT EXISTS idx_audit_user ON agent_audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_type_time ON agent_audit_logs(event_type, timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_created ON agent_audit_logs(created_at);
