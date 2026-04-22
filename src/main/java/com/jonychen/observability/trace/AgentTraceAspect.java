package com.jonychen.observability.trace;

import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import com.jonychen.planning.TaskContext;
import com.jonychen.planning.agent.ReActResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent 执行追踪切面 自动追踪 ReActAgent 和 PlanExecuteAgent 的执行过程
 *
 * @author jonychen
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AgentTraceAspect {

    private final AgentTraceService traceService;
    private final TraceContext traceContext;

    /** 追踪 ReAct Agent 执行 */
    @Around("execution(* com.jonychen.planning.agent.ReActAgent.execute(..))")
    public Object traceReActExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String question = (String) args[0];
        TaskContext context = args.length > 1 ? (TaskContext) args[1] : null;

        // 开始追踪
        String sessionId = context != null ? context.getSessionId() : null;
        String userId = context != null ? context.getUserId() : "system";

        AgentTrace trace = traceService.startTrace(sessionId, userId, "REACT", question);

        // 设置追踪上下文
        traceContext.setCurrentTraceId(trace.getTraceId());

        try {
            // 执行原方法
            Object result = joinPoint.proceed();

            // 记录成功
            if (result instanceof ReActResult reactResult) {
                String output =
                        reactResult.answer() != null
                                ? reactResult.answer()
                                : String.valueOf(reactResult);
                traceService.endTraceSuccess(
                        trace.getTraceId(), output, 0, 0 // Token 信息需要从实际响应中提取
                        );
            } else {
                traceService.endTraceSuccess(trace.getTraceId(), String.valueOf(result), 0, 0);
            }

            return result;
        } catch (Throwable e) {
            traceService.endTraceFailed(trace.getTraceId(), e.getMessage());
            throw e;
        } finally {
            traceContext.clear();
        }
    }

    /** 追踪 PlanExecute Agent 执行 */
    @Around("execution(* com.jonychen.planning.agent.PlanExecuteAgent.execute(..))")
    public Object tracePlanExecuteExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String goal = (String) args[0];
        TaskContext context = args.length > 1 ? (TaskContext) args[1] : null;

        // 开始追踪
        String sessionId = context != null ? context.getSessionId() : null;
        String userId = context != null ? context.getUserId() : "system";

        AgentTrace trace = traceService.startTrace(sessionId, userId, "PLAN_EXECUTE", goal);

        // 设置追踪上下文
        traceContext.setCurrentTraceId(trace.getTraceId());

        try {
            // 执行原方法
            Object result = joinPoint.proceed();

            // 记录成功
            traceService.endTraceSuccess(trace.getTraceId(), String.valueOf(result), 0, 0);

            return result;
        } catch (Throwable e) {
            traceService.endTraceFailed(trace.getTraceId(), e.getMessage());
            throw e;
        } finally {
            traceContext.clear();
        }
    }

    /** 追踪工具执行 */
    @Around("execution(* com.jonychen.tool.ToolRegistry.execute(..))")
    public Object traceToolExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String traceId = traceContext.getCurrentTraceId();
        if (traceId == null) {
            // 没有活跃的追踪，直接执行
            return joinPoint.proceed();
        }

        Object[] args = joinPoint.getArgs();
        String toolName = (String) args[0];
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) args[1];

        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            // 记录成功的工具调用
            traceService.recordToolCall(
                    traceId, toolName, params, String.valueOf(result), duration, true, null);

            return result;
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - startTime;

            // 记录失败的工具调用
            traceService.recordToolCall(
                    traceId, toolName, params, null, duration, false, e.getMessage());

            throw e;
        }
    }
}
