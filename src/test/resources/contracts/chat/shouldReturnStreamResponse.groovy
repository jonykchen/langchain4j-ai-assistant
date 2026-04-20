package contracts.chat

import org.springframework.cloud.contract.spec.Contract

/**
 * 流式聊天接口契约测试
 */
Contract.make {
    request {
        method POST()
        url '/api/chat/stream'
        headers {
            contentType(applicationJson())
            header("Accept", "text/event-stream")
            header("Authorization", "Bearer test-jwt-token")
        }
        body([
            message: anyNonEmptyString()
        ])
    }
    response {
        status OK()
        headers {
            contentType("text/event-stream;charset=UTF-8")
        }
        // SSE 响应体格式
        body("event:token\ndata:测试\n\nevent:done\ndata:[DONE]\n\n")
    }
}
