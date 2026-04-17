package com.jonychen.tool.audit;

import com.jonychen.tool.ToolDefinition;
import com.jonychen.tool.ToolRegistry;
import com.jonychen.tool.ToolResult;
import com.jonychen.tool.confirmation.ToolRiskEvaluator;
import com.jonychen.tool.confirmation.ToolRiskLevel;
import com.jonychen.tool.security.SensitiveDataMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 工具执行审计切面
 *
 * @author jonychen
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ToolExecutionAuditAspect {

    private final ToolExecutionAuditRepository auditRepository;
    private final ToolRegistry toolRegistry;
    private final ToolRiskEvaluator riskEvaluator;
    private final SensitiveDataMasker dataMasker;

    /**
     * 拦截工具执行，记录审计日志
     */
    @Around("execution(* com.jonychen.tool.ToolRegistry.execute(..))")
    public Object auditExecution(ProceedingJoinPoint pjp) throws Throwable {
        String toolName = (String) pjp.getArgs()[0];
        @SuppressWarnings("unchecked")
        Map<String, Object> params = pjp.getArgs().length > 1
                ? (Map<String, Object>) pjp.getArgs()[1]
                : Map.of();

        // 生成执行ID
        String executionId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        // 获取工具定义
        Optional<ToolDefinition> toolOpt = toolRegistry.getTool(toolName);

        // 评估风险等级
        ToolRiskLevel riskLevel = toolOpt
                .map(t -> riskEvaluator.evaluateRisk(t, params))
                .orElse(ToolRiskLevel.MEDIUM);

        // 创建审计记录
        ToolExecutionAudit audit = ToolExecutionAudit.create(executionId, toolName, null, null);
        audit.setParams(dataMasker.mask(params));
        audit.setRiskLevel(riskLevel);

        try {
            // 执行工具
            ToolResult result = (ToolResult) pjp.proceed();

            // 记录结果
            audit.setSuccess(result.success());
            if (result.data() != null) {
                audit.setResult(dataMasker.mask(result.data()));
            }
            if (result.error() != null) {
                audit.setErrorMessage(result.error());
            }
            audit.setExecutionTimeMs(System.currentTimeMillis() - startTime);

            return result;

        } catch (Throwable e) {
            // 记录异常
            audit.setSuccess(false);
            audit.setErrorMessage(e.getMessage());
            audit.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            throw e;

        } finally {
            // 保存审计记录
            try {
                auditRepository.save(audit);
                log.debug("Saved tool execution audit: executionId={}, tool={}, success={}, time={}ms",
                        audit.getExecutionId(), toolName, audit.isSuccess(), audit.getExecutionTimeMs());
            } catch (Exception e) {
                log.error("Failed to save tool execution audit: {}", e.getMessage());
            }
        }
    }
}