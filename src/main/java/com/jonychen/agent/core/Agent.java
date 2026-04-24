package com.jonychen.agent.core;

import java.util.Collections;
import java.util.List;

import com.jonychen.tool.ToolDefinition;

import reactor.core.publisher.Flux;

/**
 * Agent 基础接口
 *
 * <p>所有业务 Agent 需实现此接口，提供同步和流式两种执行模式。 推荐使用流式执行（executeStream），支持实时推送执行步骤到前端。
 *
 * <p>实现类可以继承 {@link AbstractAgent} 获得通用的执行框架， 只需实现
 * getMetadata()、buildSystemPrompt()、buildExecutor() 三个抽象方法。
 *
 * @author jonychen
 */
public interface Agent {

    /**
     * 获取 Agent 元信息
     *
     * @return Agent 元数据，包含名称、类型、能力描述等
     */
    AgentMetadata getMetadata();

    /**
     * 同步执行（适用于简单场景）
     *
     * <p>阻塞式执行，等待完成后返回结果。 对于需要实时反馈的场景，建议使用 {@link #executeStream}。
     *
     * @param request 执行请求
     * @param context 执行上下文
     * @return 执行结果
     */
    AgentResult execute(AgentRequest request, AgentContext context);

    /**
     * 流式执行（推荐）
     *
     * <p>返回 Flux&lt;AgentEvent&gt;，支持实时推送执行步骤到前端。 每个步骤（思考、工具调用、工具结果等）都会生成对应事件， 前端通过 SSE 接收并实时渲染。
     *
     * @param request 执行请求
     * @param context 执行上下文
     * @return 事件流，按执行顺序发送 StepStart、Thought、ToolCall、ToolResult、AgentDone 等事件
     */
    Flux<AgentEvent> executeStream(AgentRequest request, AgentContext context);

    /**
     * 是否支持该任务
     *
     * <p>返回 0-1 置信度分数，用于 Router Agent 选择合适的 Agent。 默认返回 0.0，表示不支持任何任务。
     *
     * <p>建议实现类根据用户输入关键词、语义等判断：
     *
     * <ul>
     *   <li>0.9+ : 非常确定应该由该 Agent 处理
     *   <li>0.7-0.9 : 比较确定，可以作为候选
     *   <li>0.5-0.7 : 有一定相关性，但不推荐
     *   <li>&lt;0.5 : 不应该由该 Agent 处理
     * </ul>
     *
     * @param request 执行请求
     * @return 0-1 置信度分数
     */
    default double canHandle(AgentRequest request) {
        return 0.0;
    }

    /**
     * 获取可用工具列表
     *
     * <p>返回该 Agent 可用的工具定义列表， 用于前端展示工具列表或权限控制。
     *
     * @return 工具定义列表，默认返回空列表
     */
    default List<ToolDefinition> getAvailableTools() {
        return Collections.emptyList();
    }
}
