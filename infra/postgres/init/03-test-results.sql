-- ============================================================
-- 测试结果持久化 Schema
-- 创建时间: 2024
-- 说明: 包含测试任务、E2E测试结果、性能测试结果、AI模型测试结果相关表
-- ============================================================

-- 1. 测试任务表
-- 存储每次测试执行的任务信息
CREATE TABLE IF NOT EXISTS app.test_jobs (
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

COMMENT ON TABLE app.test_jobs IS '测试任务表 - 跟踪测试执行作业';
COMMENT ON COLUMN app.test_jobs.test_type IS '测试类型: E2E, PERFORMANCE, AI_MODEL';
COMMENT ON COLUMN app.test_jobs.status IS '状态: PENDING, RUNNING, COMPLETED, FAILED, CANCELLED';
COMMENT ON COLUMN app.test_jobs.progress IS '执行进度 0-100';
COMMENT ON COLUMN app.test_jobs.triggered_by IS '触发者（用户ID或system）';

-- 索引
CREATE INDEX IF NOT EXISTS idx_test_jobs_type ON app.test_jobs(test_type);
CREATE INDEX IF NOT EXISTS idx_test_jobs_status ON app.test_jobs(status);
CREATE INDEX IF NOT EXISTS idx_test_jobs_start_time ON app.test_jobs(start_time DESC);
CREATE INDEX IF NOT EXISTS idx_test_jobs_triggered_by ON app.test_jobs(triggered_by);

-- 2. E2E 测试结果表
-- 存储 Playwright E2E 测试结果
CREATE TABLE IF NOT EXISTS app.e2e_test_results (
    id              BIGSERIAL       PRIMARY KEY,
    job_id          VARCHAR(36)     NOT NULL,
    test_name       VARCHAR(255)    NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    duration_ms     BIGINT,
    assertions_passed INTEGER      DEFAULT 0,
    assertions_failed INTEGER      DEFAULT 0,
    error_message   TEXT,
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_e2e_job FOREIGN KEY (job_id)
        REFERENCES app.test_jobs(id) ON DELETE CASCADE
);

COMMENT ON TABLE app.e2e_test_results IS 'E2E测试结果表';
COMMENT ON COLUMN app.e2e_test_results.test_name IS '测试用例名称';
COMMENT ON COLUMN app.e2e_test_results.status IS '测试状态: passed, failed, running, pending';
COMMENT ON COLUMN app.e2e_test_results.assertions_passed IS '通过的断言数';
COMMENT ON COLUMN app.e2e_test_results.assertions_failed IS '失败的断言数';

-- 索引
CREATE INDEX IF NOT EXISTS idx_e2e_results_job ON app.e2e_test_results(job_id);
CREATE INDEX IF NOT EXISTS idx_e2e_results_status ON app.e2e_test_results(status);
CREATE INDEX IF NOT EXISTS idx_e2e_results_created ON app.e2e_test_results(created_at DESC);

-- 3. 性能测试结果表
-- 存储 Gatling 性能测试结果
CREATE TABLE IF NOT EXISTS app.performance_test_results (
    id              BIGSERIAL       PRIMARY KEY,
    job_id          VARCHAR(36)     NOT NULL,
    simulation      VARCHAR(100)    NOT NULL,
    requests        BIGINT          DEFAULT 0,
    success_rate    DECIMAL(5,2),
    avg_response_time BIGINT,
    max_response_time BIGINT,
    p95_response_time BIGINT,
    p99_response_time BIGINT,
    start_time      TIMESTAMPTZ,
    end_time        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_perf_job FOREIGN KEY (job_id)
        REFERENCES app.test_jobs(id) ON DELETE CASCADE
);

COMMENT ON TABLE app.performance_test_results IS '性能测试结果表';
COMMENT ON COLUMN app.performance_test_results.simulation IS '模拟场景名称';
COMMENT ON COLUMN app.performance_test_results.success_rate IS '成功率（百分比）';
COMMENT ON COLUMN app.performance_test_results.avg_response_time IS '平均响应时间（毫秒）';
COMMENT ON COLUMN app.performance_test_results.p95_response_time IS 'P95响应时间（毫秒）';
COMMENT ON COLUMN app.performance_test_results.p99_response_time IS 'P99响应时间（毫秒）';

-- 索引
CREATE INDEX IF NOT EXISTS idx_perf_results_job ON app.performance_test_results(job_id);
CREATE INDEX IF NOT EXISTS idx_perf_results_simulation ON app.performance_test_results(simulation);
CREATE INDEX IF NOT EXISTS idx_perf_results_created ON app.performance_test_results(created_at DESC);

-- 4. AI 模型测试结果表
-- 存储 AI 模型质量测试结果
CREATE TABLE IF NOT EXISTS app.ai_model_test_results (
    id              BIGSERIAL       PRIMARY KEY,
    job_id          VARCHAR(36)     NOT NULL,
    test_case_id    VARCHAR(100)    NOT NULL,
    test_name       VARCHAR(255)    NOT NULL,
    category        VARCHAR(50),
    score           DECIMAL(5,4),
    passed          BOOLEAN         DEFAULT false,
    details         JSONB           DEFAULT '[]'::jsonb,
    response_time   BIGINT,
    actual_output   TEXT,
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ai_job FOREIGN KEY (job_id)
        REFERENCES app.test_jobs(id) ON DELETE CASCADE
);

COMMENT ON TABLE app.ai_model_test_results IS 'AI模型测试结果表';
COMMENT ON COLUMN app.ai_model_test_results.test_case_id IS '测试用例ID';
COMMENT ON COLUMN app.ai_model_test_results.category IS '测试分类: basic, code-generation, tool-calling, conversation';
COMMENT ON COLUMN app.ai_model_test_results.score IS '测试得分（0-1）';
COMMENT ON COLUMN app.ai_model_test_results.passed IS '是否通过';
COMMENT ON COLUMN app.ai_model_test_results.details IS '断言详情（JSON数组）';
COMMENT ON COLUMN app.ai_model_test_results.response_time IS '响应时间（毫秒）';

-- 索引
CREATE INDEX IF NOT EXISTS idx_ai_results_job ON app.ai_model_test_results(job_id);
CREATE INDEX IF NOT EXISTS idx_ai_results_category ON app.ai_model_test_results(category);
CREATE INDEX IF NOT EXISTS idx_ai_results_passed ON app.ai_model_test_results(passed);
CREATE INDEX IF NOT EXISTS idx_ai_results_created ON app.ai_model_test_results(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_results_test_case ON app.ai_model_test_results(test_case_id);

-- 5. 为 test_jobs 表添加清理索引
CREATE INDEX IF NOT EXISTS idx_test_jobs_created ON app.test_jobs(created_at DESC);