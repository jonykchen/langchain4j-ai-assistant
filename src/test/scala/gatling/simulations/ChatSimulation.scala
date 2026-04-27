package gatling.simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.util.Random

/**
 * 聊天 API 性能测试模拟
 */
class ChatSimulation extends Simulation {

  // HTTP 协议配置
  val httpProtocol = http
    .baseUrl("http://localhost:8082")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling-Performance-Test/1.0")

  // 测试用户 Feeder（轮询）
  val userFeeder = csv("test-users.csv").circular

  // 测试消息 Feeder
  val messages = List(
    "你好",
    "今天天气怎么样？",
    "写一个冒泡排序算法",
    "解释一下 Spring Boot 的特性",
    "用 Python 实现一个简单的 HTTP 服务器",
    "什么是微服务架构？",
    "如何优化数据库查询？",
    "解释一下 WebSocket 的工作原理"
  )

  val messageFeeder = Iterator.continually {
    Map("message" -> messages(Random.nextInt(messages.length)))
  }

  // 聊天场景
  val chatScenario = scenario("Chat API Load Test")
    .feed(userFeeder)
    .feed(messageFeeder)
    .exec(
      http("发送聊天消息")
        .post("/api/chat")
        .header("Authorization", "Bearer ${token}")
        .body(StringBody("""{"message": "${message}"}""")).asJson
        .check(status.is(200))
        .check(jsonPath("$.code").is("200"))
        .check(jsonPath("$.data.reply").exists)
        .check(responseTimeInMillis.lt(10000)) // 响应时间 < 10s
    )
    .pause(1, 3) // 用户思考时间 1-3 秒

  // 流式聊天场景
  val streamScenario = scenario("Stream API Load Test")
    .feed(userFeeder)
    .feed(messageFeeder)
    .exec(
      http("发送流式消息")
        .post("/api/chat/stream")
        .header("Authorization", "Bearer ${token}")
        .header("Accept", "text/event-stream")
        .body(StringBody("""{"message": "${message}"}""")).asJson
        .check(status.is(200))
        .check(header("Content-Type").is("text/event-stream;charset=UTF-8"))
    )
    .pause(2, 5)

  // 健康检查场景
  val healthScenario = scenario("Health Check")
    .exec(
      http("健康检查")
        .get("/actuator/health")
        .check(status.is(200))
    )

  // 标准负载配置
  val standardLoad = Seq(
    rampUsers(10).during(10.seconds),              // 10 秒内启动 10 用户
    constantUsersPerSec(5).during(1.minute),       // 持续 5 用户/秒 1 分钟
    rampUsersPerSec(5).to(20).during(2.minutes),   // 5->20 用户/秒
    constantUsersPerSec(20).during(2.minutes),     // 持续 20 用户/秒 2 分钟
    rampUsersPerSec(20).to(5).during(30.seconds)   // 降温
  )

  // 压力测试配置
  val stressLoad = Seq(
    rampUsersPerSec(10).to(100).during(5.minutes),  // 5 分钟内从 10 升到 100
    constantUsersPerSec(100).during(3.minutes),     // 持续 100 用户/秒 3 分钟
    rampUsersPerSec(100).to(10).during(1.minute)    // 降温
  )

  // 执行配置
  setUp(
    // 聊天场景
    chatScenario.inject(standardLoad)
      .protocols(httpProtocol),

    // 流式场景（较少并发）
    streamScenario.inject(rampUsers(5).during(30.seconds))
      .protocols(httpProtocol)
  ).assertions(
    global.responseTime.max.lt(30000),        // 最大响应时间 < 30s
    global.responseTime.mean.lt(5000),         // 平均响应时间 < 5s
    global.successfulRequests.percent.gt(95), // 成功率 > 95%
    global.responseTime.percentile3.lt(10000)  // P95 < 10s
  ).maxDuration(10.minutes)
}
