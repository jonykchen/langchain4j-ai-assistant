package com.jonychen.agent.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolExecutor;

/**
 * Agent 执行器
 *
 * <p>封装 LangChain4j ChatModel 调用，支持原生 ToolCalling 能力。 使用结构化输出替代正则解析，确保响应解析可靠。
 *
 * <p>核心特性：
 *
 * <ul>
 *   <li>原生 ToolCalling：完全依赖 AiMessage.hasToolExecutionRequests()，移除正则解析
 *   <li>滑动窗口：保留最近 20 条消息，防止长对话导致内存无限增长和 Token 爆炸
 *   <li>详细日志：记录每次 LLM 请求/响应耗时和工具调用参数
 * </ul>
 *
 * @author jonychen
 */
public class AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutor.class);

    /** 滑动窗口：保留最近 20 条消息（约 10 轮对话） */
    private static final int MAX_HISTORY_MESSAGES = 20;

    private static final ObjectMapper TOOL_OBJECT_MAPPER = new ObjectMapper();

    private final ChatModel chatModel;
    private final List<ToolSpecification> tools;
    private final Map<String, ToolExecutor> toolExecutors;
    private final String systemPrompt;
    private final List<ChatMessage> chatHistory;

    /**
     * 创建 Agent 执行器
     *
     * @param chatModel LLM 模型
     * @param systemPrompt 系统提示词
     * @param tools 工具规范列表
     * @param toolExecutors 工具执行器映射
     */
    public AgentExecutor(
            ChatModel chatModel,
            String systemPrompt,
            List<ToolSpecification> tools,
            Map<String, ToolExecutor> toolExecutors) {
        this.chatModel = chatModel;
        this.systemPrompt = systemPrompt;
        this.tools = tools;
        this.toolExecutors = toolExecutors;
        this.chatHistory = new CopyOnWriteArrayList<>();
    }

    /**
     * 调用 LLM
     *
     * <p>构建消息列表（系统提示词 + 滑动窗口历史 + 当前输入）， 调用 ChatModel.chat() 获取响应，并记录到历史中。
     *
     * @param userMessage 用户输入
     * @return LLM 响应
     */
    public LLMResponse invoke(String userMessage) {
        long startTime = System.currentTimeMillis();

        // 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(chatHistory);
        messages.add(new UserMessage(userMessage));

        log.debug(
                "[AgentExecutor] 调用 LLM: 消息数={}, 历史消息数={}, 工具数={}",
                messages.size(),
                chatHistory.size(),
                tools.size());

        // 构建 ChatRequest，包含工具规范
        ChatRequest.Builder requestBuilder = ChatRequest.builder().messages(messages);

        if (tools != null && !tools.isEmpty()) {
            requestBuilder.toolSpecifications(tools);
        }

        ChatRequest request = requestBuilder.build();

        // 调用 LLM
        ChatResponse response = chatModel.chat(request);
        AiMessage aiMessage = response.aiMessage();

        long durationMs = System.currentTimeMillis() - startTime;
        log.info(
                "[AgentExecutor] LLM 响应: 耗时={}ms, hasToolCall={}, text长度={}",
                durationMs,
                aiMessage.hasToolExecutionRequests(),
                aiMessage.text() != null ? aiMessage.text().length() : 0);

        // 记录到历史（滑动窗口：超过上限时移除最老的消息）
        chatHistory.add(new UserMessage(userMessage));
        chatHistory.add(aiMessage);
        if (chatHistory.size() > MAX_HISTORY_MESSAGES) {
            int removeCount = chatHistory.size() - MAX_HISTORY_MESSAGES;
            chatHistory.subList(0, removeCount).clear();
            log.debug("[AgentExecutor] 滑动窗口裁剪: 移除 {} 条旧消息", removeCount);
        }

        // 解析响应
        return parseResponse(aiMessage);
    }

    /**
     * 解析 LLM 响应
     *
     * <p>完全依赖 LangChain4j 原生 ToolCalling 能力，不再手动正则解析 thought。 thought 直接透传 message.text()（模型原生支持
     * tool calling 时通常已包含 reasoning）。
     *
     * @param message LLM 返回的 AiMessage
     * @return 解析后的 LLM 响应
     */
    private LLMResponse parseResponse(AiMessage message) {
        String text = message.text();

        // 检查是否有工具调用请求
        if (message.hasToolExecutionRequests()) {
            var toolRequest = message.toolExecutionRequests().get(0);
            Map<String, Object> params = parseJson(toolRequest.arguments());

            log.info("[AgentExecutor] 工具调用请求: tool={}, params={}", toolRequest.name(), params);

            // text() 即为模型的思考过程，无需正则提取
            return new LLMResponse(text, new ToolCallRequest(toolRequest.name(), params), null);
        }

        // 无工具调用，text() 即为最终答案
        String output = text != null ? text.trim() : "";
        log.debug("[AgentExecutor] 最终输出: 长度={}", output.length());
        return new LLMResponse(null, null, output);
    }

    /**
     * 解析 JSON 字符串为 Map
     *
     * @param json JSON 字符串
     * @return 解析后的 Map，解析失败返回空 Map
     */
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return TOOL_OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[AgentExecutor] JSON 解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 获取工具执行器
     *
     * @param toolName 工具名称
     * @return 工具执行器
     */
    public ToolExecutor getToolExecutor(String toolName) {
        return toolExecutors.get(toolName);
    }

    /**
     * 获取当前聊天历史长度
     *
     * @return 历史消息数
     */
    public int getHistorySize() {
        return chatHistory.size();
    }

    /** 清空聊天历史 */
    public void clearHistory() {
        chatHistory.clear();
        log.debug("[AgentExecutor] 聊天历史已清空");
    }
}
