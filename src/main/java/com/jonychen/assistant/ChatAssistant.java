package com.jonychen.assistant;

import dev.langchain4j.service.SystemMessage;
import reactor.core.publisher.Flux;

/**
 * AI 对话助手接口
 *
 * <p>核心概念： - 这是一个 Java 接口，没有任何实现代码 - LangChain4j 的 AiServices 会在运行时自动生成实现类 - 类似于 MyBatis 的 Mapper
 * 接口或 Spring Data JPA 的 Repository 接口
 *
 * <p>工作原理： 1. Spring 容器启动时，AiServices.builder() 扫描此接口 2. 根据方法签名和注解，动态生成代理类 3. 代理类负责：构造 Prompt → 调用
 * LLM API → 解析响应 → 返回结果
 *
 * @author 30240
 */
public interface ChatAssistant {

    /**
     * 系统提示词（System Prompt）
     *
     * <p>作用： - 定义 AI 的角色、行为和能力边界 - 在每次对话中都会发送给 LLM - 位置：在用户消息之前，作为系统级指令
     *
     * <p>思考过程标签说明： - <thinking></thinking>: 前端会解析此标签，将思考过程展示为可折叠区域 - 类似于 OpenAI o1 模型的 Chain of
     * Thought 机制
     *
     * <p>最佳实践： 1. 明确角色定义（你是...） 2. 提供输出格式要求 3. 给出示例（Few-shot Prompting） 4. 设置边界（什么不该做）
     */
    String SYSTEM_PROMPT =
            """
            你是一个友好的 AI 助手，专门帮助 Java 开发者学习 AI Agent 开发。

            ## 输出格式要求
            在回答问题之前，请先用 <thinking></thinking> 标签展示你的思考过程。
            思考过程应该简洁明了，展示你分析问题的思路。
            然后再给出最终回答。

            ## 示例
            <thinking>
            用户问的是...，我需要...
            首先考虑...，然后...
            </thinking>

            你的回答内容...

            ## 回答风格
            - 使用 Markdown 格式，代码块指定语言
            - 分点阐述，条理清晰
            - 提供可运行的代码示例
            """;

    /**
     * 同步对话方法
     *
     * @param userMessage 用户输入的消息
     * @return AI 的完整回复（阻塞等待）
     *     <p>注解说明： @SystemMessage - 将 SYSTEM_PROMPT 作为系统消息发送 @UserMessage -
     *     将参数作为用户消息发送（此处省略，使用默认行为）
     *     <p>使用场景： - 对响应时间要求不高 - 需要完整响应后再处理
     */
    @SystemMessage(SYSTEM_PROMPT)
    String chat(String userMessage);

    /**
     * 流式响应方法（推荐）
     *
     * @param userMessage 用户输入的消息
     * @return 响应式流，逐个 token 返回
     *     <p>返回类型说明： - Flux<String>: Reactor 响应式流，每个元素是一个 token（字或词） - 配合 Spring WebFlux 实现 SSE
     *     (Server-Sent Events)
     *     <p>流式响应的优势： 1. 用户体验好：看到逐字输出，不用等待 2. 降低超时风险：长回复不会阻塞 3. 支持取消：用户可随时停止
     *     <p>前端配合： - EventSource API 接收 SSE - 逐字追加到消息内容
     */
    @SystemMessage(SYSTEM_PROMPT)
    Flux<String> chatFlux(String userMessage);

    /*
     * ==================== 扩展知识点 ====================
     *
     * 1. 多轮对话记忆
     *    - 配置 MessageWindowChatMemory 可自动保存历史消息
     *    - 每次调用会自动带上之前的对话上下文
     *
     * 2. 工具调用（Function Calling）
     *    - 添加 @Tool 注解的方法，AI 可自动调用
     *    - 例如：查询天气、搜索文档、执行代码等
     *
     * 3. 结构化输出
     *    - 返回类型可以是 POJO，AI 自动生成 JSON 并解析
     *    - 例如：UserSummary chat(String message);
     *
     * 4. RAG（检索增强生成）
     *    - 配合 ContentRetriever 从知识库检索相关内容
     *    - AI 基于检索结果生成回答
     *
     * 5. 多模态支持
     *    - 方法参数可以是 Image、Audio 等类型
     *    - 支持 GPT-4V 等多模态模型
     */
}
