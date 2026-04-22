package com.jonychen.planning.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jonychen.planning.TaskContext;
import com.jonychen.tool.ToolRegistry;
import com.jonychen.tool.ToolResult;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * ReAct Agent：推理-行动循环
 *
 * <p>工作流程： 1. Thought: LLM 思考下一步该做什么 2. Action: 选择并执行工具 3. Observation: 观察执行结果 4. 循环直到得出最终答案
 *
 * @author jonychen
 */
@Slf4j
@Component
public class ReActAgent {

    private static final String REACT_PROMPT_TEMPLATE =
            """
            你是一个智能助手，使用 ReAct 模式解决问题。

            遵循以下格式：

            Thought: 思考当前情况，分析下一步该做什么
            Action: 工具名称
            Action Input: JSON 格式的参数，如 {"param": "value"}
            Observation: 工具返回的结果
            ... (重复 Thought/Action/Observation 直到可以回答)
            Thought: 我现在知道最终答案了
            Final Answer: 最终回答

            可用工具：
            {tools}

            规则：
            1. 每次只能执行一个工具
            2. Action 必须是可用工具之一
            3. Action Input 必须是有效的 JSON
            4. 如果已经有足够信息，直接给出 Final Answer

            开始！

            用户问题：{question}
            {history}
            """;

    private static final Pattern THOUGHT_PATTERN =
            Pattern.compile("Thought:\\s*(.+?)(?=Action:|Final Answer:|$)", Pattern.DOTALL);
    private static final Pattern ACTION_PATTERN = Pattern.compile("Action:\\s*(\\w+)");
    private static final Pattern ACTION_INPUT_PATTERN =
            Pattern.compile("Action Input:\\s*(\\{[^}]*\\}|\\S+)", Pattern.DOTALL);
    private static final Pattern FINAL_ANSWER_PATTERN =
            Pattern.compile("Final Answer:\\s*(.+)$", Pattern.DOTALL);

    private static final int DEFAULT_MAX_ITERATIONS = 10;

    private final ChatModel chatModel;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final int maxIterations;

    public ReActAgent(ChatModel chatModel, ToolRegistry toolRegistry) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
        this.objectMapper = new ObjectMapper();
        this.maxIterations = DEFAULT_MAX_ITERATIONS;
    }

    /**
     * 执行 ReAct 循环
     *
     * @param question 用户问题
     * @param context 任务上下文
     * @return 执行结果
     */
    public ReActResult execute(String question, TaskContext context) {
        List<ReActStep> steps = new ArrayList<>();
        StringBuilder history = new StringBuilder();

        String toolsDescription = buildToolsDescription();

        for (int i = 0; i < maxIterations; i++) {
            // 1. 构建 Prompt
            String prompt =
                    REACT_PROMPT_TEMPLATE
                            .replace("{tools}", toolsDescription)
                            .replace("{question}", question)
                            .replace("{history}", history.toString());

            // 2. LLM 思考
            String response =
                    chatModel
                            .chat(ChatRequest.builder().messages(UserMessage.from(prompt)).build())
                            .aiMessage()
                            .text();
            log.trace(
                    "ReAct iteration {}: responseLength={}",
                    i + 1,
                    response != null ? response.length() : 0);

            // 3. 解析响应
            ReActStep step = parseResponse(response);
            steps.add(step);

            // 4. 检查是否是最终答案
            if (step.isFinalAnswer()) {
                log.info("ReAct completed after {} iterations", i + 1);
                return ReActResult.success(step.finalAnswer(), steps, i + 1);
            }

            // 5. 执行工具
            if (step.hasAction()) {
                ToolResult toolResult = executeTool(step.action(), step.actionInput(), context);
                String observation = formatObservation(toolResult);

                step = step.withObservation(observation);
                steps.set(steps.size() - 1, step); // 更新步骤

                // 6. 更新历史
                history.append("\n").append(response);
                history.append("\nObservation: ").append(observation).append("\n");
            } else {
                // 没有行动也没有最终答案，可能是解析失败
                history.append("\n").append(response);
            }
        }

        // 达到最大迭代次数
        log.warn("ReAct reached max iterations: {}", maxIterations);
        return ReActResult.failure("达到最大迭代次数，未能得出答案", steps, maxIterations);
    }

    /** 解析 LLM 响应 */
    private ReActStep parseResponse(String response) {
        String thought = null;
        String action = null;
        String actionInput = null;
        String finalAnswer = null;

        // 提取 Thought
        Matcher thoughtMatcher = THOUGHT_PATTERN.matcher(response);
        if (thoughtMatcher.find()) {
            thought = thoughtMatcher.group(1).trim();
        }

        // 检查 Final Answer
        Matcher finalAnswerMatcher = FINAL_ANSWER_PATTERN.matcher(response);
        if (finalAnswerMatcher.find()) {
            finalAnswer = finalAnswerMatcher.group(1).trim();
            return ReActStep.finalAnswer(thought, finalAnswer);
        }

        // 提取 Action
        Matcher actionMatcher = ACTION_PATTERN.matcher(response);
        if (actionMatcher.find()) {
            action = actionMatcher.group(1).trim();
        }

        // 提取 Action Input
        Matcher actionInputMatcher = ACTION_INPUT_PATTERN.matcher(response);
        if (actionInputMatcher.find()) {
            actionInput = actionInputMatcher.group(1).trim();
        }

        if (action != null) {
            return ReActStep.action(thought, action, actionInput);
        }

        return ReActStep.thought(thought != null ? thought : response);
    }

    /** 执行工具 */
    @SuppressWarnings("unchecked")
    private ToolResult executeTool(String toolName, String actionInput, TaskContext context) {
        try {
            Map<String, Object> params = parseActionInput(actionInput);

            // 添加上下文信息
            if (context != null && context.getSessionId() != null) {
                params.put("_sessionId", context.getSessionId());
            }

            return toolRegistry.execute(toolName, params);
        } catch (Exception e) {
            log.error("Tool execution failed: {} - {}", toolName, e.getMessage());
            return ToolResult.failure("工具执行失败: " + e.getMessage());
        }
    }

    /** 解析 Action Input */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseActionInput(String actionInput) {
        if (actionInput == null || actionInput.isBlank()) {
            return Map.of();
        }

        try {
            // 尝试解析 JSON
            return objectMapper.readValue(actionInput, Map.class);
        } catch (JsonProcessingException e) {
            // 如果不是 JSON，作为简单字符串处理
            return Map.of("input", actionInput);
        }
    }

    /** 格式化观察结果 */
    private String formatObservation(ToolResult result) {
        if (result.success()) {
            if (result.data() != null) {
                try {
                    return objectMapper.writeValueAsString(result.data());
                } catch (JsonProcessingException e) {
                    return String.valueOf(result.data());
                }
            }
            return "执行成功";
        } else {
            return "执行失败: " + result.error();
        }
    }

    /** 构建工具描述 */
    private String buildToolsDescription() {
        StringBuilder sb = new StringBuilder();
        for (var tool : toolRegistry.getAllTools()) {
            sb.append("- ")
                    .append(tool.name())
                    .append(": ")
                    .append(tool.description())
                    .append("\n");
        }
        return sb.toString();
    }
}
