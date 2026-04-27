<script setup lang="ts">
/**
 * ExecutionStats 组件
 *
 * <p>展示 Agent 执行的统计信息。
 *
 * <h2>统计指标</h2>
 * <ul>
 *   <li>总步骤数</li>
 *   <li>成功/失败步骤数</li>
 *   <li>工具调用次数</li>
 *   <li>执行耗时</li>
 *   <li>Token 消耗</li>
 * </ul>
 *
 * @author jonychen
 */
import { computed } from 'vue'
import type { ExecutionRecord } from '@/types/agent'

/** Props 定义 */
const props = defineProps<{
  /** 执行记录 */
  execution: ExecutionRecord
}>()

/** 总步骤数 */
const totalSteps = computed(() => props.execution.steps.length)

/** 成功步骤数 */
const successSteps = computed(() =>
  props.execution.steps.filter(s => s.status === 'success').length
)

/** 失败步骤数 */
const errorSteps = computed(() =>
  props.execution.steps.filter(s => s.status === 'error').length
)

/** 工具调用次数 */
const toolCalls = computed(() =>
  props.execution.steps.filter(s => s.type === 'TOOL_CALL').length
)

/** 执行耗时（毫秒） */
const duration = computed(() => props.execution.durationMs || 0)

/** Token 使用量 */
const tokenUsage = computed(() => props.execution.tokenUsage)

/** 格式化耗时 */
function formatDuration(ms: number): string {
  if (ms === 0) return '-'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  return `${Math.floor(ms / 60000)}m ${((ms % 60000) / 1000).toFixed(0)}s`
}

/** 格式化数字 */
function formatNumber(n: number | undefined): string {
  if (n === undefined || n === 0) return '-'
  if (n >= 1000000) return `${(n / 1000000).toFixed(1)}M`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}K`
  return n.toString()
}
</script>

<template>
  <div class="execution-stats">
    <!-- 步骤统计 -->
    <div class="stat-item">
      <div class="stat-icon">
        <el-icon><List /></el-icon>
      </div>
      <div class="stat-content">
        <span class="stat-value">{{ totalSteps }}</span>
        <span class="stat-label">步骤</span>
      </div>
    </div>

    <!-- 成功/失败 -->
    <div class="stat-item">
      <div class="stat-icon success">
        <el-icon><Check /></el-icon>
      </div>
      <div class="stat-content">
        <span class="stat-value success">{{ successSteps }}</span>
        <span class="stat-label">成功</span>
      </div>
    </div>

    <div v-if="errorSteps > 0" class="stat-item">
      <div class="stat-icon error">
        <el-icon><Close /></el-icon>
      </div>
      <div class="stat-content">
        <span class="stat-value error">{{ errorSteps }}</span>
        <span class="stat-label">失败</span>
      </div>
    </div>

    <!-- 工具调用 -->
    <div class="stat-item">
      <div class="stat-icon tool">
        <el-icon><Connection /></el-icon>
      </div>
      <div class="stat-content">
        <span class="stat-value">{{ toolCalls }}</span>
        <span class="stat-label">工具调用</span>
      </div>
    </div>

    <!-- 耗时 -->
    <div class="stat-item">
      <div class="stat-icon time">
        <el-icon><Timer /></el-icon>
      </div>
      <div class="stat-content">
        <span class="stat-value">{{ formatDuration(duration) }}</span>
        <span class="stat-label">耗时</span>
      </div>
    </div>

    <!-- Token 消耗 -->
    <div v-if="tokenUsage" class="stat-item tokens">
      <div class="stat-icon token">
        <el-icon><Coin /></el-icon>
      </div>
      <div class="stat-content">
        <span class="stat-value">{{ formatNumber(tokenUsage.totalTokens) }}</span>
        <span class="stat-label">
          Tokens
          <el-tooltip placement="top">
            <template #content>
              <div>Prompt: {{ formatNumber(tokenUsage.promptTokens) }}</div>
              <div>Completion: {{ formatNumber(tokenUsage.completionTokens) }}</div>
            </template>
            <el-icon class="info-icon"><InfoFilled /></el-icon>
          </el-tooltip>
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.execution-stats {
  display: flex;
  gap: var(--space-2xl);
  padding: var(--space-xl) var(--space-2xl);
  background: var(--bg-secondary);
  border-radius: var(--radius-lg);
  flex-wrap: wrap;
  border: 1px solid var(--border-light);
}

.stat-item {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.stat-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-icon.success {
  background: var(--color-success-light);
  color: var(--color-success);
}

.stat-icon.error {
  background: var(--color-danger-light);
  color: var(--color-danger);
}

.stat-icon.tool {
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.stat-icon.time {
  background: var(--color-warning-light);
  color: var(--color-warning);
}

.stat-icon.token {
  background: rgba(155, 89, 182, 0.12);
  color: #9b59b6;
}

[data-theme="dark"] .stat-icon.token {
  background: rgba(155, 89, 182, 0.2);
}

.stat-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.stat-value.success {
  color: var(--color-success);
}

.stat-value.error {
  color: var(--color-danger);
}

.stat-label {
  font-size: 11px;
  color: var(--text-tertiary);
  display: flex;
  align-items: center;
  gap: 2px;
}

.info-icon {
  font-size: 12px;
  cursor: help;
}

.tokens .stat-value {
  color: #9b59b6;
}

[data-theme="dark"] .tokens .stat-value {
  color: #b37feb;
}
</style>
