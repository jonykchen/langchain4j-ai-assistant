package com.jonychen.controller;

import com.jonychen.model.ChatRequest;
import com.jonychen.model.ChatResponse;
import com.jonychen.service.AiService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 聊天控制器
 *
 * 提供 REST API 接口供前端调用：
 * - POST /api/chat        同步对话，返回完整回复
 * - POST /api/chat/stream 流式对话，逐字返回（SSE）
 *
 * 架构说明：
 * Controller → Service → ChatAssistant（LangChain4j AiServices）
 *
 * @author 30240
 */
@RestController
@RequestMapping("/api/chat")
/*
 * 跨域配置
 *
 * @CrossOrigin 允许前端从不同域名/端口访问此 API
 * - origins = "*": 允许所有来源（开发环境用）
 * - 生产环境应指定具体域名：origins = "https://your-domain.com"
 *
 * 为什么需要跨域？
 * - 前端运行在 localhost:3000
 * - 后端运行在 localhost:8082
 * - 浏览器安全策略阻止跨域请求，需要后端声明允许
 *
 * 其他配置方式：
 * 1. 全局配置：在 CorsConfig.java 中配置 CorsFilter
 * 2. 网关配置：在 Nginx/API Gateway 层面处理
 */
@CrossOrigin(origins = "*")
public class ChatController {

    private final AiService aiService;

    /**
     * 构造函数注入
     *
     * 推荐使用构造函数注入而非 @Autowired 字段注入：
     * - 便于单元测试（可传入 Mock 对象）
     * - 明确依赖关系
     * - 字段不可变（final）
     */
    public ChatController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * 同步对话接口
     *
     * @param request 请求体，包含用户消息
     * @return 完整的 AI 回复
     *
     * 特点：
     * - 阻塞等待 AI 完整响应
     * - 适合短回复场景
     * - 长回复可能导致请求超时
     *
     * 请求示例：
     * POST /api/chat
     * Content-Type: application/json
     * {"message": "你好"}
     *
     * 响应示例：
     * {"reply": "你好！有什么可以帮助你的？"}
     */
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String reply = aiService.chat(request.message());
        return new ChatResponse(reply);
    }

    /**
     * 流式对话接口（SSE - Server-Sent Events）
     *
     * @param request 请求体，包含用户消息
     * @return SSE 事件流，每个事件包含一个 token
     *
     * SSE 说明：
     * - Server-Sent Events：服务器向客户端单向推送
     * - 基于 HTTP 长连接
     * - 相比 WebSocket 更简单，适合服务器推送场景
     *
     * 响应格式：
     * event: token
     * data: 你
     *
     * event: token
     * data: 好
     *
     * event: done
     * data: [DONE]
     *
     * produces = MediaType.TEXT_EVENT_STREAM_VALUE
     * - 设置响应 Content-Type: text/event-stream
     * - 告诉浏览器这是 SSE 流
     *
     * 前端接收方式：
     * 1. EventSource API（只能 GET 请求）
     * 2. fetch() + ReadableStream（支持 POST）
     * 3. 本项目使用 async generator 解析
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody ChatRequest request) {
        /*
         * Flux 是 Reactor 的响应式流：
         * - 每个元素是一个 token
         * - map() 将每个 token 包装成 SSE 事件
         * - concatWith() 在末尾追加 [DONE] 事件
         *
         * ServerSentEvent 结构：
         * - event: 事件类型（token/done）
         * - data: 事件数据（实际内容）
         *
         * 为什么用 Flux 而不是 List？
         * - Flux 是惰性的，不会阻塞
         * - 每个 token 到达时立即发送
         * - 背压支持：客户端可控制流速
         */
        return aiService.chatFlux(request.message())
                .map(token -> ServerSentEvent.<String>builder()
                        .event("token")      // 事件类型
                        .data(token)         // 事件数据
                        .build())
                // 追加结束标记
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("done")       // 结束事件
                                .data("[DONE]")      // 结束标记
                                .build()
                ));
    }

    /*
     * ==================== 扩展知识点 ====================
     *
     * 1. 对话 ID（多轮对话隔离）
     *    - 添加 sessionId/conversationId 参数
     *    - 每个会话独立的 ChatMemory
     *    - 实现：Map<String, ChatMemory> sessionMemories
     *
     * 2. 用户认证
     *    - 添加 @RequestHeader("Authorization") String token
     *    - 验证 JWT Token，获取用户信息
     *    - 按用户隔离对话历史
     *
     * 3. 流式取消
     *    - 添加 cancel() 接口
     *    - 使用 Flux.takeUntilOther() 实现取消
     *
     * 4. 错误处理
     *    - @ExceptionHandler 处理异常
     *    - 返回友好的错误信息
     *    - 记录错误日志
     *
     * 5. 限流
     *    - 使用 @RateLimiter 注解
     *    - 或 Redis + Lua 脚本实现
     *    - 防止 API 被滥用
     */
}
