package contracts.chat

import org.springframework.cloud.contract.spec.Contract

/**
 * 聊天接口参数校验契约测试
 * 验证空消息被拒绝
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
            message: ""
        ])
    }
    response {
        status BAD_REQUEST()
        headers {
            contentType(applicationJson())
        }
        body([
            code: 400,
            message: anyNonEmptyString()
        ])
    }
}
