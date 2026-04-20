package contracts.chat

import org.springframework.cloud.contract.spec.Contract

/**
 * 聊天接口契约测试
 */
Contract.make {
    request {
        method POST()
        url '/api/chat'
        headers {
            contentType(applicationJson())
            header("Authorization", "Bearer test-jwt-token")
        }
        body([
            message: "你好"
        ])
    }
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body([
            code: 200,
            message: "success",
            data: [
                reply: anyNonEmptyString()
            ]
        ])
    }
}
