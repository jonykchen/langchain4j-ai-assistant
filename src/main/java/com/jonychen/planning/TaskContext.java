package com.jonychen.planning;

import java.util.HashMap;
import java.util.Map;

import com.jonychen.tool.ToolRegistry;

import dev.langchain4j.memory.ChatMemory;
import lombok.Getter;

/**
 * 任务执行上下文
 *
 * @author jonychen
 */
@Getter
public class TaskContext {

    private final String sessionId;
    private final String userId;
    private final Map<String, Object> variables;
    private final Map<String, StepResult> stepResults;
    private final ToolRegistry toolRegistry;
    private final ChatMemory chatMemory;

    public TaskContext(
            String sessionId, String userId, ToolRegistry toolRegistry, ChatMemory chatMemory) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.variables = new HashMap<>();
        this.stepResults = new HashMap<>();
        this.toolRegistry = toolRegistry;
        this.chatMemory = chatMemory;
    }

    /** 创建简单上下文 */
    public static TaskContext create(String sessionId) {
        return new TaskContext(sessionId, null, null, null);
    }

    /** 创建带工具注册中心的上下文 */
    public static TaskContext create(String sessionId, ToolRegistry toolRegistry) {
        return new TaskContext(sessionId, null, toolRegistry, null);
    }

    /** 设置变量 */
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    /** 获取变量 */
    public Object getVariable(String key) {
        return variables.get(key);
    }

    /** 获取变量（带默认值） */
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key, T defaultValue) {
        Object value = variables.get(key);
        if (value == null) {
            return defaultValue;
        }
        return (T) value;
    }

    /** 检查变量是否存在 */
    public boolean hasVariable(String key) {
        return variables.containsKey(key);
    }

    /** 记录步骤结果 */
    public void recordStepResult(String stepId, StepResult result) {
        stepResults.put(stepId, result);
    }

    /** 获取步骤结果 */
    public StepResult getStepResult(String stepId) {
        return stepResults.get(stepId);
    }

    /** 获取所有步骤结果 */
    public Map<String, StepResult> getAllStepResults() {
        return new HashMap<>(stepResults);
    }

    /** 添加聊天记忆 */
    public void addToMemory(String userMessage, String assistantMessage) {
        if (chatMemory != null) {
            chatMemory.add(dev.langchain4j.data.message.UserMessage.from(userMessage));
            if (assistantMessage != null) {
                chatMemory.add(dev.langchain4j.data.message.AiMessage.from(assistantMessage));
            }
        }
    }
}
