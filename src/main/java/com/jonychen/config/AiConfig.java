package com.jonychen.config;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import com.jonychen.assistant.ChatAssistant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 配置类
 *
 * 职责：
 * 1. 配置 ChatAssistant Bean（核心 AI 服务）
 * 2. 设置对话记忆（ChatMemory）
 * 3. 注入 LLM 模型（ChatModel、StreamingChatModel）
 *
 * 依赖注入说明：
 * - ChatModel 和 StreamingChatModel 由 LangChain4j Spring Boot Starter 自动配置
 * - 配置来源：application.properties 中的 langchain4j.open-ai.* 配置项
 * - 支持的模型：OpenAI、Azure OpenAI、阿里云 DashScope、智谱 AI 等
 *
 * @author 30240
 */
@Configuration
public class AiConfig {

    /**
     * 构建 ChatAssistant Bean
     *
     * AiServices 是 LangChain4j 的核心构建器：
     * - 类似于 MyBatis 的 SqlSessionFactory
     * - 动态生成接口的实现类（动态代理）
     * - 处理 Prompt 构建、API 调用、响应解析
     *
     * @param chatModel            同步聊天模型（阻塞式）
     * @param streamingChatModel   流式聊天模型（响应式）
     * @return ChatAssistant 实例（动态代理对象）
     */
    @Bean
    public ChatAssistant chatAssistant(ChatModel chatModel,
                                        StreamingChatModel streamingChatModel) {
        return AiServices.builder(ChatAssistant.class)
                // 同步模型：用于 chat() 方法
                .chatModel(chatModel)
                // 流式模型：用于 chatFlux() 方法
                .streamingChatModel(streamingChatModel)
                /*
                 * 对话记忆配置
                 *
                 * MessageWindowChatMemory：滑动窗口记忆
                 * - 保留最近 N 条消息（用户消息 + AI 回复各算一条）
                 * - 防止上下文过长超出模型 token 限制
                 * - 超出窗口的旧消息会被丢弃
                 *
                 * 为什么需要对话记忆？
                 * - LLM 本身是无状态的，每次调用独立
                 * - 需要将历史消息作为上下文发送给 LLM
                 * - ChatMemory 自动管理消息的添加和窗口滑动
                 *
                 * 其他记忆类型：
                 * - TokenWindowChatMemory: 按 token 数量限制
                 * - 持久化记忆：将消息保存到数据库/Redis
                 */
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                /*
                 * ==================== 更多配置选项 ====================
                 *
                 * 1. 工具调用（Function Calling）
                 *    .tools(new WeatherTool(), new SearchTool())
                 *    - AI 会自动判断何时调用工具
                 *    - 工具执行结果会反馈给 AI
                 *
                 * 2. RAG 检索增强
                 *    .contentRetriever(contentRetriever)
                 *    - 从向量数据库检索相关文档
                 *    - AI 基于检索结果生成回答
                 *
                 * 3. 摄取器（用于 RAG）
                 *    .augmentor(augmentor)
                 *    - 自定义 Prompt 增强逻辑
                 *
                 * 4. 模型输出结构化
                 *    .outputParser(new JsonOutputParser())
                 *    - 强制 AI 输出 JSON 格式
                 *    - 自动解析为 Java 对象
                 *
                 * 5. 日志/监控
                 *    .listeners(new ChatMemoryListener())
                 *    - 监听每次对话过程
                 *    - 用于调试、审计、统计
                 */
                .build();
    }

    /*
     * ==================== 扩展：自定义 ChatModel ====================
     *
     * 如果需要更细粒度的模型配置，可以手动创建 Bean：
     *
     * @Bean
     * public ChatModel chatModel() {
     *     return OpenAiChatModel.builder()
     *         .apiKey("your-api-key")
     *         .baseUrl("https://api.openai.com/v1")
     *         .modelName("gpt-4")
     *         .temperature(0.7)           // 创造性：0-2，越高越随机
     *         .maxTokens(4096)            // 最大输出 token 数
     *         .topP(0.9)                  // 核采样
     *         .frequencyPenalty(0.0)      // 频率惩罚：减少重复
     *         .presencePenalty(0.0)       // 存在惩罚：鼓励话题多样性
     *         .timeout(Duration.ofSeconds(60))
     *         .logRequests(true)          // 日志：调试用
     *         .logResponses(true)
     *         .build();
     * }
     *
     * 参数说明：
     * - temperature: 控制输出随机性
     *   - 0: 确定性输出（适合代码、事实查询）
     *   - 0.7: 平衡（适合一般对话）
     *   - 1.5+: 创意性输出（适合写作、头脑风暴）
     *
     * - maxTokens: 限制回复长度
     *   - 1 token ≈ 0.75 个英文单词
     *   - 中文：1 token ≈ 1-2 个汉字
     */
}
