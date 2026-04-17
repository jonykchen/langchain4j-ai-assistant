package com.jonychen.tool.security;

import com.jonychen.tool.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 幂等工具执行器
 *
 * 对写操作工具自动添加幂等性控制
 *
 * @author jonychen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotentToolExecutor {

    private final ToolRegistry toolRegistry;
    private final IdempotencyManager idempotencyManager;
    private final ToolParameterValidator parameterValidator;

    /**
     * 需要幂等性控制工具分类
     */
    private static final Set<ToolCategory> IDEMPOTENT_CATEGORIES = Set.of(
            ToolCategory.DATABASE,   // 数据库写操作
            ToolCategory.FILE,       // 文件写操作
            ToolCategory.EXTERNAL    // 外部服务调用
    );

    /**
     * 执行工具（自动处理幂等性）
     *
     * @param toolName  工具名称
     * @param params    参数
     * @param sessionId 会话ID
     * @return 执行结果
     */
    public ToolResult execute(String toolName, Map<String, Object> params, String sessionId) {
        ToolDefinition tool = toolRegistry.getTool(toolName)
                .orElseThrow(() -> new ToolNotFoundException("Tool not found: " + toolName));

        // 1. 参数校验
        ValidationResult validation = parameterValidator.validate(params, tool);
        if (!validation.valid()) {
            return ToolResult.failure("参数校验失败: " + validation.getErrorMessage());
        }

        // 2. 检查是否需要幂等性控制
        if (!needsIdempotency(tool)) {
            return toolRegistry.execute(toolName, params);
        }

        // 3. 幂等性执行
        return executeWithIdempotency(tool, params, sessionId);
    }

    /**
     * 判断工具是否需要幂等性控制
     */
    private boolean needsIdempotency(ToolDefinition tool) {
        // 根据分类判断
        if (IDEMPOTENT_CATEGORIES.contains(tool.category())) {
            return true;
        }

        // 根据工具名称判断（约定命名）
        String name = tool.name().toLowerCase();
        return name.contains("write") || name.contains("create") ||
                name.contains("update") || name.contains("delete") ||
                name.contains("send") || name.contains("submit");
    }

    /**
     * 带幂等性控制的执行
     */
    private ToolResult executeWithIdempotency(ToolDefinition tool,
                                               Map<String, Object> params,
                                               String sessionId) {
        String idempotencyKey = idempotencyManager.generateIdempotencyKey(
                tool.name(), params, sessionId
        );

        // 检查是否已执行
        if (!idempotencyManager.checkAndSet(idempotencyKey)) {
            // 尝试获取缓存结果
            return idempotencyManager.getCachedResult(idempotencyKey)
                    .orElseGet(() -> ToolResult.failure("请求处理中，请稍后查询结果"));
        }

        try {
            // 执行工具
            long startTime = System.currentTimeMillis();
            ToolResult result = toolRegistry.execute(tool.name(), params);
            result = result.withExecutionTime(System.currentTimeMillis() - startTime);

            // 缓存成功结果
            if (result.success()) {
                idempotencyManager.cacheResult(idempotencyKey, result);
            } else {
                // 失败时释放幂等键，允许重试
                idempotencyManager.release(idempotencyKey);
            }

            return result;
        } catch (Exception e) {
            // 异常时释放幂等键
            idempotencyManager.release(idempotencyKey);
            log.error("Tool '{}' execution failed: {}", tool.name(), e.getMessage(), e);
            return ToolResult.failure("执行失败: " + e.getMessage());
        }
    }

    /**
     * 强制重新执行（忽略幂等性）
     *
     * @param toolName  工具名称
     * @param params    参数
     * @param sessionId 会话ID
     * @return 执行结果
     */
    public ToolResult forceExecute(String toolName, Map<String, Object> params, String sessionId) {
        ToolDefinition tool = toolRegistry.getTool(toolName)
                .orElseThrow(() -> new ToolNotFoundException("Tool not found: " + toolName));

        // 参数校验
        ValidationResult validation = parameterValidator.validate(params, tool);
        if (!validation.valid()) {
            return ToolResult.failure("参数校验失败: " + validation.getErrorMessage());
        }

        // 释放幂等键后执行
        String idempotencyKey = idempotencyManager.generateIdempotencyKey(toolName, params, sessionId);
        idempotencyManager.release(idempotencyKey);

        return toolRegistry.execute(toolName, params);
    }
}