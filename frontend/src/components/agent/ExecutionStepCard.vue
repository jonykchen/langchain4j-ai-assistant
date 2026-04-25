<script setup lang="ts">
/**
 * ExecutionStepCard 组件
 *
 * <p>展示 Agent 执行步骤的卡片组件，支持折叠/展开。
 *
 * <h2>展示内容</h2>
 * <ul>
 *   <li>步骤类型图标和名称（思考/工具调用/工具结果）</li>
 *   <li>执行状态（运行中/成功/失败）</li>
 *   <li>思考过程内容</li>
 *   <li>工具名称、参数、结果</li>
 *   <li>错误信息</li>
 * </ul>
 *
 * @author jonychen
 */
import { ref, computed } from 'vue'
import type { ExecutionStep } from '@/types/agent'
import JsonViewer from '@/components/JsonViewer.vue'

/** Props 定义 */
const props = defineProps<{
  /** 步骤数据 */
  step: ExecutionStep
  /** 是否是最后一步 */
  isLastStep: boolean
}>()

/** Events 定义 */
defineEmits<{
  /** 用户确认/拒绝操作 */
  (e: 'confirm', approved: boolean): void
}>()

/** 是否展开详情 */
const isExpanded = ref(true)

/** 步骤类型图标映射 */
const stepTypeIcons: Record<string, string> = {
  THOUGHT: 'Cpu',
  TOOL_CALL: 'Connection',
  TOOL_RESULT: 'Document',
  LLM_CALL: 'ChatDotRound',
  AGENT_CALL: 'User'
}

/** 步骤类型中文映射 */
const stepTypeLabels: Record<string, string> = {
  THOUGHT: '思考',
  TOOL_CALL: '工具调用',
  TOOL_RESULT: '工具结果',
  LLM_CALL: 'LLM 调用',
  AGENT_CALL: 'Agent 委托'
}

/** 状态颜色 */
const statusColors: Record<string, string> = {
  running: 'primary',
  success: 'success',
  error: 'danger'
}

/** 状态中文 */
const statusLabels: Record<string, string> = {
  running: '执行中',
  success: '成功',
  error: '失败'
}

/** 步骤耗时（毫秒） */
const duration = computed(() => {
  if (!props.step.startTime || !props.step.endTime) return null
  return props.step.endTime.getTime() - props.step.startTime.getTime()
})

/** 格式化耗时 */
function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

/** 切换展开/折叠 */
function toggle() {
  isExpanded.value = !isExpanded.value
}
</script>

<template>
  <div :class="['step-card', `step-${step.status}`]">
    <!-- 步骤头部 -->
    <div class="step-header" @click="toggle">
      <div class="header-left">
        <!-- 步骤图标 -->
        <div :class="['step-icon', step.status]">
          <el-icon :size="16">
            <component :is="stepTypeIcons[step.type] || 'Document'" />
          </el-icon>
        </div>

        <!-- 步骤信息 -->
        <div class="step-info">
          <span class="step-type">{{ stepTypeLabels[step.type] || step.type }}</span>
          <span v-if="step.toolName" class="step-tool">{{ step.toolName }}</span>
        </div>
      </div>

      <div class="header-right">
        <!-- 状态标签 -->
        <el-tag :type="statusColors[step.status]" size="small">
          {{ statusLabels[step.status] }}
        </el-tag>

        <!-- 耗时 -->
        <span v-if="duration" class="step-duration">{{ formatDuration(duration) }}</span>

        <!-- 展开/折叠图标 -->
        <el-icon class="toggle-icon" :class="{ expanded: isExpanded }">
          <ArrowDown />
        </el-icon>
      </div>
    </div>

    <!-- 步骤详情（可折叠） -->
    <div v-show="isExpanded" class="step-body">
      <!-- 思考内容 -->
      <div v-if="step.content && step.type === 'THOUGHT'" class="thought-section">
        <div class="section-label">思考过程</div>
        <pre class="thought-content">{{ step.content }}</pre>
      </div>

      <!-- 工具信息 -->
      <div v-if="step.toolName" class="tool-section">
        <!-- 工具参数 -->
        <div v-if="step.toolParams && Object.keys(step.toolParams).length > 0" class="tool-params">
          <div class="section-label">参数</div>
          <JsonViewer :data="step.toolParams" :default-expanded="false" />
        </div>

        <!-- 工具结果 -->
        <div v-if="step.toolResult !== undefined" class="tool-result">
          <div class="section-label">结果</div>
          <JsonViewer :data="step.toolResult" :default-expanded="true" />
        </div>
      </div>

      <!-- 其他内容（非思考） -->
      <div v-if="step.content && step.type !== 'THOUGHT'" class="content-section">
        <div class="section-label">内容</div>
        <div class="content-text">{{ step.content }}</div>
      </div>

      <!-- 错误信息 -->
      <div v-if="step.error" class="error-section">
        <el-alert type="error" :closable="false" show-icon>
          <template #title>执行失败</template>
          {{ step.error }}
        </el-alert>
      </div>
    </div>

    <!-- 连接线（非最后一步） -->
    <div v-if="!isLastStep" class="step-connector"></div>
  </div>
</template>

<style scoped>
.step-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  overflow: hidden;
  position: relative;
}

.step-card.step-running {
  border-color: #409eff;
}

.step-card.step-error {
  border-color: #f56c6c;
}

/* 步骤头部 */
.step-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.2s;
}

.step-header:hover {
  background: #f5f7fa;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.step-icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #e4e7ed;
  color: #606266;
}

.step-icon.running {
  background: #409eff;
  color: #fff;
  animation: pulse 1.5s infinite;
}

.step-icon.success {
  background: #67c23a;
  color: #fff;
}

.step-icon.error {
  background: #f56c6c;
  color: #fff;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.step-info {
  display: flex;
  flex-direction: column;
}

.step-type {
  font-weight: 600;
  color: #303133;
  font-size: 14px;
}

.step-tool {
  font-size: 12px;
  color: #909399;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.step-duration {
  font-size: 12px;
  color: #909399;
}

.toggle-icon {
  transition: transform 0.3s;
  color: #909399;
}

.toggle-icon.expanded {
  transform: rotate(180deg);
}

/* 步骤详情 */
.step-body {
  padding: 0 16px 16px;
  border-top: 1px solid #e4e7ed;
}

.section-label {
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  margin-bottom: 8px;
  text-transform: uppercase;
}

.thought-section,
.tool-section,
.content-section,
.error-section {
  margin-top: 12px;
}

.thought-content {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
}

.tool-params,
.tool-result {
  margin-top: 8px;
}

.content-text {
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
}

.error-section {
  margin-top: 12px;
}

/* 连接线 */
.step-connector {
  position: absolute;
  left: 27px;
  bottom: -1px;
  width: 2px;
  height: 12px;
  background: #e4e7ed;
  z-index: 1;
}
</style>
