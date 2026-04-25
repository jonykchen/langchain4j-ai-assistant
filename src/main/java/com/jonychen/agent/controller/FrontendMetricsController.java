package com.jonychen.agent.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jonychen.agent.core.AgentMetricsService;

import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;

/**
 * 前端可靠性指标接收控制器
 *
 * <p>接收前端上报的可靠性指标，转换为 Prometheus 指标：
 *
 * <ul>
 *   <li>agent_frontend_reconnects - SSE 重连次数
 *   <li>agent_frontend_event_gaps - SSE 事件序号间隙次数
 *   <li>agent_frontend_state_restore_failures - 状态恢复失败次数
 * </ul>
 *
 * @author jonychen
 */
@RestController
@RequestMapping("/api/agent/metrics")
@RequiredArgsConstructor
public class FrontendMetricsController {

    private static final Logger log = LoggerFactory.getLogger(FrontendMetricsController.class);

    private final AgentMetricsService metricsService;

    /**
     * 接收前端指标上报
     *
     * @param request 指标上报请求
     */
    @PostMapping("/frontend")
    public ResponseEntity<Void> reportMetrics(@RequestBody MetricsReportRequest request) {
        if (request.metrics() == null || request.metrics().isEmpty()) {
            return ResponseEntity.ok().build();
        }

        for (MetricReport report : request.metrics()) {
            try {
                processMetricReport(report);
            } catch (Exception e) {
                log.warn("[FrontendMetrics] 处理指标失败: type={}, error={}", report.type(), e.getMessage());
            }
        }

        return ResponseEntity.ok().build();
    }

    private void processMetricReport(MetricReport report) {
        switch (report.type()) {
            case "reconnect" -> {
                Counter counter = metricsService.getOrCreateCounter(
                        "agent_frontend_reconnects",
                        "Frontend SSE reconnect count");
                counter.increment(report.count());
                log.debug("[FrontendMetrics] reconnect: traceId={}, attempt={}",
                        report.traceId(),
                        report.details() != null ? report.details().get("attempt") : null);
            }
            case "event_gap" -> {
                Counter counter = metricsService.getOrCreateCounter(
                        "agent_frontend_event_gaps",
                        "Frontend SSE event sequence gap count");
                counter.increment(report.count());
                log.debug("[FrontendMetrics] event_gap: traceId={}, gap={}",
                        report.traceId(),
                        report.details() != null ? report.details().get("gap") : null);
            }
            case "state_restore_failure" -> {
                Counter counter = metricsService.getOrCreateCounter(
                        "agent_frontend_state_restore_failures",
                        "Frontend state restore failure count");
                counter.increment(report.count());
                log.debug("[FrontendMetrics] state_restore_failure: sessionId={}, reason={}",
                        report.sessionId(),
                        report.details() != null ? report.details().get("reason") : null);
            }
            default -> log.warn("[FrontendMetrics] 未知指标类型: {}", report.type());
        }
    }

    /** 指标上报请求 */
    public record MetricsReportRequest(List<MetricReport> metrics) {}

    /** 单个指标报告 */
    public record MetricReport(
            String type, int count, String traceId, String sessionId, java.util.Map<String, Object> details) {}
}
