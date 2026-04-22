package com.jonychen.observability.trace;

import java.util.ArrayDeque;
import java.util.Deque;

import org.springframework.stereotype.Component;

/**
 * 追踪上下文（ThreadLocal 存储） 用于在方法调用链中传递 Trace ID 和 Span ID
 *
 * @author jonychen
 */
@Component
public class TraceContext {

    private static final ThreadLocal<String> CURRENT_TRACE = new ThreadLocal<>();
    private static final ThreadLocal<Deque<String>> SPAN_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    /** 获取当前 Trace ID */
    public String getCurrentTraceId() {
        return CURRENT_TRACE.get();
    }

    /** 设置当前 Trace ID */
    public void setCurrentTraceId(String traceId) {
        CURRENT_TRACE.set(traceId);
    }

    /** 获取当前 Span ID */
    public String getCurrentSpanId() {
        Deque<String> stack = SPAN_STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    /** 推入新的 Span ID */
    public void pushSpanId(String spanId) {
        SPAN_STACK.get().push(spanId);
    }

    /** 弹出当前 Span ID */
    public String popSpanId() {
        Deque<String> stack = SPAN_STACK.get();
        return stack.isEmpty() ? null : stack.pop();
    }

    /** 获取父 Span ID（当前 Span 的下一个） */
    public String getParentSpanId() {
        Deque<String> stack = SPAN_STACK.get();
        if (stack.size() < 2) {
            return null;
        }
        // 跳过当前 Span，获取父级
        String current = stack.pop();
        String parent = stack.peek();
        stack.push(current);
        return parent;
    }

    /** 清除当前上下文 */
    public void clear() {
        CURRENT_TRACE.remove();
        SPAN_STACK.remove();
    }

    /** 检查是否有活跃的追踪 */
    public boolean hasActiveTrace() {
        return CURRENT_TRACE.get() != null;
    }

    /** 获取当前 Span 栈深度 */
    public int getSpanDepth() {
        return SPAN_STACK.get().size();
    }
}
