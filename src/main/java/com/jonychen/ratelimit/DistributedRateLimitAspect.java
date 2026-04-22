package com.jonychen.ratelimit;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletRequest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.jonychen.config.RateLimitProperties;
import com.jonychen.exception.BusinessException;
import com.jonychen.model.ErrorCode;

/**
 * 分布式限流切面
 *
 * <p>基于 Redis 实现全局限流，多实例共享配额 支持从 Nacos 配置中心动态更新限流参数
 */
@Aspect
@Component
public class DistributedRateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(DistributedRateLimitAspect.class);

    private final DistributedRateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    public DistributedRateLimitAspect(
            DistributedRateLimiter rateLimiter, RateLimitProperties rateLimitProperties) {
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
    }

    /** 拦截 Controller 方法，执行分布式限流检查 */
    @Around("@annotation(io.github.resilience4j.ratelimiter.annotation.RateLimiter)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();

        io.github.resilience4j.ratelimiter.annotation.RateLimiter rateLimiterAnnotation =
                method.getAnnotation(
                        io.github.resilience4j.ratelimiter.annotation.RateLimiter.class);

        if (rateLimiterAnnotation == null) {
            return point.proceed();
        }

        String limiterName = rateLimiterAnnotation.name();
        String identifier = getClientIp();
        String key = DistributedRateLimiter.buildKey(limiterName, identifier);

        // 从 Nacos 配置获取动态限流参数
        int limit = getLimitForName(limiterName);
        int period = getPeriodForName(limiterName);

        boolean allowed = rateLimiter.tryAcquireSlidingWindow(key, limit, period, TimeUnit.SECONDS);

        if (!allowed) {
            log.warn(
                    "分布式限流拒绝: method={}, key={}, limit={}, period={}s",
                    method.getName(),
                    key,
                    limit,
                    period);
            throw new BusinessException(
                    ErrorCode.RATE_LIMITED, String.format("请求过于频繁，请在 %d 秒后重试", period));
        }

        return point.proceed();
    }

    /** 从配置中心获取限流数（支持动态更新） */
    private int getLimitForName(String name) {
        return switch (name) {
            case "chat" -> rateLimitProperties.getChatLimit();
            case "chatStream" -> rateLimitProperties.getChatStreamLimit();
            default -> 50;
        };
    }

    /** 从配置中心获取限流周期（支持动态更新） */
    private int getPeriodForName(String name) {
        return switch (name) {
            case "chat" -> rateLimitProperties.getChatPeriod();
            case "chatStream" -> rateLimitProperties.getChatStreamPeriod();
            default -> 60;
        };
    }

    /** 获取客户端 IP */
    private String getClientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            return "unknown";
        }

        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip != null ? ip : "unknown";
    }
}
