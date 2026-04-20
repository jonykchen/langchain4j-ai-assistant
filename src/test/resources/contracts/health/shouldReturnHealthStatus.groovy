package contracts.health

import org.springframework.cloud.contract.spec.Contract

/**
 * 健康检查接口契约测试
 */
Contract.make {
    request {
        method GET()
        url '/actuator/health'
    }
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body([
            status: "UP"
        ])
    }
}
