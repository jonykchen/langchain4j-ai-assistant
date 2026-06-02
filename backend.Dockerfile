# 后端 Dockerfile - 多阶段构建
# syntax=docker/dockerfile:1

# ==================== 构建阶段 ====================
FROM maven:3-eclipse-temurin-26-alpine AS builder

WORKDIR /build

# 先复制 pom.xml 利用 Docker 缓存
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并构建
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==================== 运行阶段 ====================
FROM eclipse-temurin:17-jre-alpine

# 安全: 使用非 root 用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# 从构建阶段复制 jar 文件
COPY --from=builder --chown=appuser:appgroup /build/target/*.jar app.jar

# 暴露端口
EXPOSE 8082

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1

# JVM 参数优化
ENV JAVA_OPTS="-Xms256m -Xmx768m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UseStringDeduplication \
  -Djava.security.egd=file:/dev/./urandom"

# 切换到非 root 用户
USER appuser

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]