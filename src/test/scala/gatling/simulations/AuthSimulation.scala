package gatling.simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * 认证 API 性能测试模拟
 */
class AuthSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8082")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling-Performance-Test/1.0")

  // 登录场景
  val loginScenario = scenario("Login API Load Test")
    .exec(
      http("用户登录")
        .post("/auth/login")
        .body(StringBody("""{"username": "testuser", "password": "testpassword"}""")).asJson
        .check(status.is(200))
        .check(jsonPath("$.code").is("200"))
        .check(jsonPath("$.data.token").exists)
    )
    .pause(1, 2)

  // Token 刷新场景
  val refreshTokenScenario = scenario("Token Refresh Load Test")
    .feed(csv("test-tokens.csv").circular)
    .exec(
      http("刷新 Token")
        .post("/auth/refresh")
        .header("Authorization", "Bearer ${token}")
        .check(status.is(200))
        .check(jsonPath("$.data.token").exists)
    )
    .pause(2, 4)

  // 用户信息查询场景
  val userInfoScenario = scenario("User Info Load Test")
    .feed(csv("test-tokens.csv").circular)
    .exec(
      http("获取用户信息")
        .get("/auth/me")
        .header("Authorization", "Bearer ${token}")
        .check(status.is(200))
        .check(jsonPath("$.data.username").exists)
    )
    .pause(1, 3)

  // 负载配置
  val authLoad = Seq(
    rampUsers(20).during(20.seconds),
    constantUsersPerSec(10).during(1.minute),
    rampUsersPerSec(10).to(50).during(2.minutes),
    constantUsersPerSec(50).during(1.minute),
    rampUsersPerSec(50).to(10).during(30.seconds)
  )

  setUp(
    loginScenario.inject(rampUsers(100).during(2.minutes))
      .protocols(httpProtocol),

    refreshTokenScenario.inject(rampUsers(50).during(1.minute))
      .protocols(httpProtocol),

    userInfoScenario.inject(constantUsersPerSec(20).during(1.minute))
      .protocols(httpProtocol)
  ).assertions(
    global.responseTime.max.lt(5000),
    global.responseTime.mean.lt(1000),
    global.successfulRequests.percent.gt(98)
  ).maxDuration(5.minutes)
}
