-- ===========================================
-- LangChain4j AI Agent 数据库初始化脚本
-- 支持 PostgreSQL 和 H2 数据库
-- ===========================================

-- ==================== 用户认证相关 ====================

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE,
    nickname VARCHAR(100),
    avatar VARCHAR(500),
    provider VARCHAR(20) NOT NULL,
    provider_id VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL,
    last_login_at TIMESTAMP,
    version BIGINT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_users_provider ON users(provider, provider_id);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- Token 黑名单（用于登出时使 Token 失效）
CREATE TABLE IF NOT EXISTS token_blacklist (
    id SERIAL PRIMARY KEY,
    token_hash VARCHAR(128) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_blacklist_token ON token_blacklist(token_hash);
CREATE INDEX IF NOT EXISTS idx_blacklist_expires ON token_blacklist(expires_at);

-- ==================== Token 使用记录 ====================

CREATE TABLE IF NOT EXISTS token_usage_logs (
    id SERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64),
    model_name VARCHAR(50) NOT NULL,
    prompt_tokens INT NOT NULL,
    completion_tokens INT NOT NULL,
    total_tokens INT NOT NULL,
    cost DECIMAL(10, 6) NOT NULL,
    currency VARCHAR(10) DEFAULT 'USD',
    request_type VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_usage_user_date ON token_usage_logs(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_usage_date ON token_usage_logs(created_at);

-- ==================== 用户配额 ====================

CREATE TABLE IF NOT EXISTS user_quotas (
    user_id VARCHAR(64) PRIMARY KEY,
    daily_token_limit INT DEFAULT 100000,
    monthly_token_limit INT DEFAULT 2000000,
    daily_cost_limit DECIMAL(10, 2) DEFAULT 10.00,
    monthly_cost_limit DECIMAL(10, 2) DEFAULT 200.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==================== 工具执行审计 ====================

CREATE TABLE IF NOT EXISTS tool_execution_audits (
    id SERIAL PRIMARY KEY,
    execution_id VARCHAR(64) UNIQUE NOT NULL,
    tool_name VARCHAR(100) NOT NULL,
    session_id VARCHAR(64),
    user_id VARCHAR(64),
    params TEXT,
    success BOOLEAN NOT NULL DEFAULT FALSE,
    result TEXT,
    error_message VARCHAR(1000),
    execution_time_ms BIGINT DEFAULT 0,
    risk_level VARCHAR(20),
    confirmation_required BOOLEAN DEFAULT FALSE,
    confirmed BOOLEAN DEFAULT FALSE,
    confirmed_by VARCHAR(64),
    executed_at TIMESTAMP,
    client_ip VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_tool_audit_tool ON tool_execution_audits(tool_name);
CREATE INDEX IF NOT EXISTS idx_tool_audit_user ON tool_execution_audits(user_id);
CREATE INDEX IF NOT EXISTS idx_tool_audit_session ON tool_execution_audits(session_id);
CREATE INDEX IF NOT EXISTS idx_tool_audit_time ON tool_execution_audits(executed_at);

-- ==================== 文档与向量存储 ====================

-- 文档表
CREATE TABLE IF NOT EXISTS documents (
    id VARCHAR(64) PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    file_size BIGINT,
    content_hash VARCHAR(64),
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_documents_type ON documents(file_type);
CREATE INDEX IF NOT EXISTS idx_documents_created ON documents(created_at);

-- 文档块表（向量存储）
CREATE TABLE IF NOT EXISTS document_chunks (
    id VARCHAR(64) PRIMARY KEY,
    document_id VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536),
    chunk_index INT NOT NULL,
    start_index INT,
    end_index INT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_document FOREIGN KEY (document_id)
        REFERENCES documents(id) ON DELETE CASCADE
);

-- 向量索引（使用 ivfflat 索引）
CREATE INDEX IF NOT EXISTS idx_chunks_embedding ON document_chunks
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

CREATE INDEX IF NOT EXISTS idx_chunks_document ON document_chunks(document_id);

-- ==================== 插入默认管理员（首次部署时）====================

-- 注意：实际部署时应通过 OAuth 登录创建管理员
-- INSERT INTO users (id, username, email, nickname, provider, provider_id, role, created_at)
-- VALUES ('admin-001', 'admin', 'admin@example.com', '系统管理员', 'CUSTOM', 'admin', 'ADMIN', CURRENT_TIMESTAMP);
