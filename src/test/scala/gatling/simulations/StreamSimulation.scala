package gatling.simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.util.Random

/**
 * 流式响应 API 性能测试模拟
 */
class StreamSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8082")
    .acceptHeader("text/event-stream")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling-Performance-Test/1.0")

  // 测试用户
  val testUsers = List(
    Map("token" -> "test-jwt-token-1"),
    Map("token" -> "test-jwt-token-2"),
    Map("token" -> "test-jwt-token-3")
  )

  val userFeeder = testUsers.circular

  // 长消息（触发更多 Token）
  val longMessages = List(
    "请详细解释一下微服务架构的优点和缺点，并给出具体的实施建议",
    "写一个完整的用户认证系统，包含注册、登录、密码重置等功能",
    "解释一下 Java 的内存模型，包括堆、栈、方法区等",
    "如何设计一个高并发的秒杀系统？请详细说明架构设计"
  )

  val messageFeeder = Iterator.continually {
    Map("message" -> longMessages(Random.nextInt(longMessages.length)))
  }

  // 流式场景
  val streamScenario = scenario("Stream API Load Test")
    .feed(userFeeder)
    .feed(messageFeeder)
    .exec(
      http("发送流式消息")
        .post("/api/chat/stream")
        .header("Authorization", "Bearer ${token}")
        .header("Accept", "text/event-stream")
        .header("Cache-Control", "no-cache")
        .header("Connection", "keep-alive")
        .body(StringBody("""{"message": "${message}"}""")).asJson
        .check(status.is(200))
        .check(header("Content-Type").exists)
    )
    .pause(3, 8) // 流式响应后需要较长时间处理

  // 高延迟流式场景
  val highLatencyStreamScenario = scenario("High Latency Stream Test")
    .feed(userFeeder)
    .feed(messageFeeder)
    .exec(
      http("发送长消息流式")
        .post("/api/chat/stream")
        .header("Authorization", "Bearer ${token}")
        .header("Accept", "text/event-stream")
        .body(StringBody("""{"message": "${message}", "maxTokens": 2000}""")).asJson
        .check(status.is(200))
        .check(responseTimeInMillis.lt(60000)) // 60s 超时
    )
    .pause(5, 10)

  // 负载配置（流式接口并发应较低）
  val streamLoad = Seq(
    rampUsers(5).during(30.seconds),
    constantUsersPerSec(2).during(2.minutes),
    rampUsersPerSec(2).to(10).during(2.minutes),
    constantUsersPerSec(10).during(1.minute),
    rampUsersPerSec(10).to(2).during(30.seconds)
  )

  setUp(
    streamScenario.inject(streamLoad)
      .protocols(httpProtocol)
      .assertions(
        global.responseTime.max.lt(120000),        // 最大响应时间 < 2 分钟
        global.responseTime.mean.lt(30000),         // 平均响应时间 < 30s
        global.successfulRequests.percent.gt(90)   // 成功率 > 90%
      )
  ).maxDuration(10.minutes)
}
