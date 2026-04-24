package com.jonychen.tool.builtin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.jonychen.admin.service.TokenUsageService;

import dev.langchain4j.agent.tool.Tool;

/**
 * Token 用量工具集
 *
 * <p>提供 Token 使用量查询工具，供 OpsAgent 调用。 读取 TokenUsageService 中的统计数据。
 *
 * @author jonychen
 */
@Component
public class TokenUsageTools {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageTools.class);

    private final TokenUsageService tokenUsageService;

    public TokenUsageTools(TokenUsageService tokenUsageService) {
        this.tokenUsageService = tokenUsageService;
    }

    /**
     * 获取 Token 使用量统计
     *
     * @return Token 使用量报告
     */
    @Tool("获取 Token 使用量统计，包括总用量、各模型用量和费用估算")
    public String getUsage() {
        log.info("[TokenUsageTools] 获取 Token 使用量");

        try {
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalDate weekAgo = today.minusDays(7);

            // 获取最近 7 天的全局统计
            var summary = tokenUsageService.getGlobalSummary(weekAgo, today);

            StringBuilder sb = new StringBuilder();
            sb.append("Token 使用量统计（最近 7 天）:\n");
            sb.append("-".repeat(50)).append("\n");
            sb.append(String.format("总 Token 数: %,d%n", summary.totalTokens()));
            sb.append(String.format("输入 Token: %,d%n", summary.promptTokens()));
            sb.append(String.format("输出 Token: %,d%n", summary.completionTokens()));
            sb.append(String.format("总费用: $%.4f%n", summary.totalCost()));
            sb.append("-".repeat(50));

            return sb.toString();
        } catch (Exception e) {
            log.warn("[TokenUsageTools] 获取 Token 使用量失败: {}", e.getMessage());
            return "获取 Token 使用量失败: " + e.getMessage();
        }
    }
}
