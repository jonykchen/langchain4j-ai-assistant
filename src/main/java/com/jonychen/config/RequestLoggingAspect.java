package com.jonychen.config;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 请求日志切面
 *
 * <p>记录所有 Controller 方法的请求和响应信息
 */
@Aspect
@Component
public class RequestLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingAspect.class);
    private static final String TRACE_ID = "traceId";

    @Around("execution(* com.jonychen.controller..*.*(..))")
    public Object logRequest(ProceedingJoinPoint point) throws Throwable {
        long startTime = System.currentTimeMillis();
        String traceId = generateTraceId();

        // 设置追踪 ID
        MDC.put(TRACE_ID, traceId);

        HttpServletRequest request = getCurrentRequest();
        String method = request != null ? request.getMethod() : "UNKNOWN";
        String uri = request != null ? request.getRequestURI() : "UNKNOWN";
        String clientIp = request != null ? getClientIp(request) : "UNKNOWN";

        // 请求日志
        log.info("[{}] >>> {} {} from {}", traceId, method, uri, clientIp);

        try {
            Object result = point.proceed();
            long duration = System.currentTimeMillis() - startTime;

            // 成功日志
            log.info("[{}] <<< {} {} completed in {}ms", traceId, method, uri, duration);

            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            // 异常日志
            log.error(
                    "[{}] <<< {} {} failed in {}ms: {}",
                    traceId,
                    method,
                    uri,
                    duration,
                    e.getMessage());

            throw e;
        } finally {
            MDC.remove(TRACE_ID);
        }
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private String getClientIp(HttpServletRequest request) {
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
