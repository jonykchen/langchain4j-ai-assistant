package com.jonychen.observability.trace;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * 请求追踪服务
 *
 * <p>记录完整的请求处理链路，使用 MDC traceId 统一追踪。
 *
 * <p>追踪流程： REQUEST_START → PROMPT_BUILD → MODEL_SELECT → API_CALL_START → API_CALL_END →
 * TOKEN_USAGE → REQUEST_END
 *
 * <p>日志示例：
 *
 * <pre>
 * 12:34:56.789 [abc12345] DEBUG REQUEST_TRACE - [REQUEST_START] userId=admin, message=如何实现 Agent？
 * 12:34:56.791 [abc12345] DEBUG REQUEST_TRACE - [PROMPT_BUILD] systemPrompt=512字符, history=5条
 * 12:34:56.793 [abc12345] DEBUG REQUEST_TRACE - [MODEL_SELECT] selected=dashscope(weight=50)
 * 12:34:56.795 [abc12345] DEBUG REQUEST_TRACE - [API_CALL_START] model=qwen-plus
 * 12:34:57.890 [abc12345] INFO  REQUEST_TRACE - [API_CALL_END] model=qwen-plus, duration=1095ms
 * 12:34:57.892 [abc12345] INFO  REQUEST_TRACE - [TOKEN_USAGE] prompt=120, completion=256
 * 12:34:57.894 [abc12345] INFO  REQUEST_TRACE - [REQUEST_END] total=1105ms
 * </pre>
 */
@Service
public class RequestTraceService {

    private static final Logger LOG = LoggerFactory.getLogger("REQUEST_TRACE");

    // 阶段标记
    private static final String REQUEST_START = "REQUEST_START";
    private static final String PROMPT_BUILD = "PROMPT_BUILD";
    private static final String MODEL_SELECT = "MODEL_SELECT";
    private static final String API_CALL_START = "API_CALL_START";
    private static final String API_CALL_END = "API_CALL_END";
    private static final String TOKEN_USAGE = "TOKEN_USAGE";
    private static final String REQUEST_END = "REQUEST_END";
    private static final String FAILOVER = "FAILOVER";

    // ThreadLocal 存储请求开始时间和选中的模型
    private final ThreadLocal<Long> requestStartTime = new ThreadLocal<>();
    private final ThreadLocal<String> selectedModel = new ThreadLocal<>();
    private final ThreadLocal<Long> apiCallStartTime = new ThreadLocal<>();

    // 日志截断长度
    private static final int MAX_MESSAGE_LENGTH = 100;

    /**
     * 记录请求开始
     *
     * @param userId 用户 ID
     * @param clientIp 客户端 IP
     * @param message 用户消息
     */
    public void logRequestStart(String userId, String clientIp, String message) {
        requestStartTime.set(System.currentTimeMillis());
        LOG.debug(
                "[{}] userId={}, ip={}, message={}",
                REQUEST_START,
                userId,
                clientIp != null ? clientIp : "unknown",
                truncate(message));
    }

    /**
     * 记录提示词构建
     *
     * @param systemPromptLength 系统提示词长度
     * @param historyCount 历史消息数
     * @param totalMessages 总消息数
     */
    public void logPromptBuild(int systemPromptLength, int historyCount, int totalMessages) {
        LOG.debug(
                "[{}] systemPrompt={}字符, history={}条, total={}条消息",
                PROMPT_BUILD,
                systemPromptLength,
                historyCount,
                totalMessages);
    }

    /**
     * 记录模型选择
     *
     * @param candidates 候选模型列表
     * @param selected 选中的模型
     * @param weight 选中模型的权重
     * @param strategy 选择策略
     */
    public void logModelSelect(
            List<String> candidates, String selected, int weight, String strategy) {
        selectedModel.set(selected);
        LOG.debug(
                "[{}] candidates={}, strategy={}, selected={}(weight={})",
                MODEL_SELECT,
                candidates,
                strategy,
                selected,
                weight);
    }

    /**
     * 记录 API 调用开始
     *
     * @param modelName 模型名称
     * @param endpoint API 端点
     * @param estimatedTokens 预估 Token 数
     */
    public void logApiCallStart(String modelName, String endpoint, int estimatedTokens) {
        apiCallStartTime.set(System.currentTimeMillis());
        LOG.debug(
                "[{}] model={}, endpoint={}, estimatedTokens={}",
                API_CALL_START,
                modelName,
                maskEndpoint(endpoint),
                estimatedTokens);
    }

    /**
     * 记录 API 调用结束
     *
     * @param modelName 模型名称
     * @param success 是否成功
     * @param error 错误信息（如果有）
     */
    public void logApiCallEnd(String modelName, boolean success, String error) {
        long startTime = apiCallStartTime.get();
        long duration = startTime > 0 ? System.currentTimeMillis() - startTime : 0;

        if (success) {
            LOG.info(
                    "[{}] model={}, duration={}ms, status=SUCCESS",
                    API_CALL_END,
                    modelName,
                    duration);
        } else {
            LOG.warn(
                    "[{}] model={}, duration={}ms, status=FAILED, error={}",
                    API_CALL_END,
                    modelName,
                    duration,
                    error != null ? error : "unknown");
        }
    }

    /**
     * 记录 Token 使用
     *
     * @param modelName 模型名称
     * @param promptTokens 输入 Token 数
     * @param completionTokens 输出 Token 数
     * @param cost 成本（美元）
     */
    public void logTokenUsage(
            String modelName, int promptTokens, int completionTokens, double cost) {
        int totalTokens = promptTokens + completionTokens;
        LOG.info(
                "[{}] model={}, prompt={}, completion={}, total={}, cost=${}",
                TOKEN_USAGE,
                modelName,
                promptTokens,
                completionTokens,
                totalTokens,
                String.format("%.6f", cost));
    }

    /**
     * 记录请求结束
     *
     * @param responseLength 响应长度
     * @param success 是否成功
     */
    public void logRequestEnd(int responseLength, boolean success) {
        long startTime = requestStartTime.get();
        long totalDuration = startTime > 0 ? System.currentTimeMillis() - startTime : 0;

        LOG.info(
                "[{}] total={}ms, responseLength={}字符, status={}",
                REQUEST_END,
                totalDuration,
                responseLength,
                success ? "SUCCESS" : "FAILED");

        // 清理 ThreadLocal
        requestStartTime.remove();
        selectedModel.remove();
        apiCallStartTime.remove();
    }

    /**
     * 记录故障转移
     *
     * @param failedModel 失败的模型
     * @param fallbackModel 备用模型
     * @param reason 原因
     */
    public void logFailover(String failedModel, String fallbackModel, String reason) {
        LOG.info("[{}] {} → {}, reason={}", FAILOVER, failedModel, fallbackModel, reason);
    }

    /** 获取当前 traceId */
    public String getTraceId() {
        return MDC.get("traceId");
    }

    /** 获取选中的模型 */
    public String getSelectedModel() {
        return selectedModel.get();
    }

    // ==================== 工具方法 ====================

    /** 截断消息，避免日志过长 */
    private String truncate(String text) {
        if (text == null) {
            return "null";
        }
        if (text.length() <= MAX_MESSAGE_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_MESSAGE_LENGTH) + "...";
    }

    /** 脱敏 API 端点，隐藏敏感信息 */
    private String maskEndpoint(String endpoint) {
        if (endpoint == null) {
            return "unknown";
        }
        // 只显示域名和路径，不显示 API Key 等参数
        int queryIndex = endpoint.indexOf('?');
        if (queryIndex > 0) {
            return endpoint.substring(0, queryIndex);
        }
        return endpoint;
    }
}
