<script setup lang="ts">
/**
 * ExecutionStepCard 组件（生产级重构版）
 *
 * <p>展示 Agent 执行步骤的时间线卡片组件，采用现代设计语言。
 *
 * <h2>设计特点</h2>
 * <ul>
 *   <li>清晰的步骤类型可视化（图标 + 颜色编码）</li>
 *   <li>时间线连接器设计</li>
 *   <li>可折叠的详情区域</li>
 *   <li>状态指示器（运行中动画/成功/失败）</li>
 *   <li>JSON 参数和结果的结构化展示</li>
 * </ul>
 *
 * @author jonychen
 */
import { ref, computed } from 'vue'
import {
  Cpu,
  Connection,
  Document,
  ChatDotRound,
  User,
  ArrowDown,
  Loading,
  CircleCheck,
  CircleClose,
  CopyDocument,
  CaretRight,
} from '@element-plus/icons-vue'
import type { ExecutionStep } from '@/types/agent'
import JsonViewer from '@/components/JsonViewer.vue'

/** Props 定义 */
const props = defineProps<{
  /** 步骤数据 */
  step: ExecutionStep
  /** 是否是最后一步 */
  isLastStep: boolean
  /** 步骤序号（从 1 开始） */
  stepIndex?: number
}>()

/** Events 定义 */
defineEmits<{
  /** 用户确认/拒绝操作 */
  (e: 'confirm', approved: boolean): void
}>()

/** 是否展开详情 */
const isExpanded = ref(true)

/** 步骤类型配置 */
const stepTypeConfig = computed(() => {
  const configs: Record<
    string,
    { icon: typeof Cpu; label: string; color: string; bgColor: string; description: string }
  > = {
    THOUGHT: {
      icon: Cpu,
      label: '思考',
      color: '#60a5fa',
      bgColor: 'rgba(96, 165, 250, 0.12)',
      description: 'Agent 正在分析问题并规划下一步',
    },
    TOOL_CALL: {
      icon: Connection,
      label: '工具调用',
      color: '#f472b6',
      bgColor: 'rgba(244, 114, 182, 0.12)',
      description: '调用外部工具获取信息或执行操作',
    },
    TOOL_RESULT: {
      icon: Document,
      label: '工具结果',
      color: '#a78bfa',
      bgColor: 'rgba(167, 139, 250, 0.12)',
      description: '工具执行返回的结果数据',
    },
    LLM_CALL: {
      icon: ChatDotRound,
      label: 'LLM 响应',
      color: '#34d399',
      bgColor: 'rgba(52, 211, 153, 0.12)',
      description: '大语言模型生成的响应内容',
    },
    AGENT_CALL: {
      icon: User,
      label: '子 Agent',
      color: '#fbbf24',
      bgColor: 'rgba(251, 191, 36, 0.12)',
      description: '委托给专门的子 Agent 处理',
    },
  }
  return (
    configs[props.step.type] || {
      icon: Document,
      label: props.step.type,
      color: '#9ca3af',
      bgColor: 'rgba(156, 163, 175, 0.12)',
      description: '未知步骤类型',
    }
  )
})

/** 状态图标 */
const statusIcon = computed(() => {
  switch (props.step.status) {
    case 'running':
      return Loading
    case 'success':
      return CircleCheck
    case 'error':
      return CircleClose
    default:
      return null
  }
})

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

/** 复制到剪贴板 */
async function copyToClipboard(text: string) {
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    // 静默失败
  }
}
</script>

<template>
  <div :class="['step-timeline-item', `step-${step.status}`]">
    <!-- 时间线左侧 -->
    <div class="timeline-left">
      <!-- 步骤序号 -->
      <div v-if="stepIndex" class="step-number">
        {{ stepIndex }}
      </div>

      <!-- 连接线 -->
      <div class="timeline-line" :class="{ last: isLastStep }"></div>

      <!-- 节点图标 -->
      <div
        class="timeline-node"
        :style="{
          backgroundColor: stepTypeConfig.bgColor,
          borderColor: stepTypeConfig.color,
        }"
      >
        <el-icon :size="16" :style="{ color: stepTypeConfig.color }">
          <component :is="stepTypeConfig.icon" />
        </el-icon>

        <!-- 状态小图标 -->
        <div v-if="statusIcon" class="status-badge" :class="step.status">
          <el-icon :size="10">
            <component :is="statusIcon" />
          </el-icon>
        </div>
      </div>
    </div>

    <!-- 时间线右侧：步骤卡片 -->
    <div class="timeline-content">
      <div class="step-card">
        <!-- 卡片头部 -->
        <div class="card-header" @click="toggle">
          <div class="header-main">
            <div class="header-row">
              <!-- 类型标签 -->
              <span
                class="type-tag"
                :style="{
                  backgroundColor: stepTypeConfig.bgColor,
                  color: stepTypeConfig.color,
                }"
              >
                {{ stepTypeConfig.label }}
              </span>

              <!-- 工具名称 -->
              <span v-if="step.toolName" class="tool-name">{{ step.toolName }}</span>
            </div>

            <!-- 步骤描述 -->
            <p class="step-description">{{ stepTypeConfig.description }}</p>
          </div>

          <div class="header-meta">
            <!-- 状态 -->
            <el-tag
              :type="
                step.status === 'success'
                  ? 'success'
                  : step.status === 'error'
                    ? 'danger'
                    : 'primary'
              "
              size="small"
              effect="light"
            >
              <template v-if="step.status === 'running'">
                <el-icon class="is-loading"><Loading /></el-icon>
                执行中
              </template>
              <template v-else-if="step.status === 'success'">成功</template>
              <template v-else-if="step.status === 'error'">失败</template>
            </el-tag>

            <!-- 耗时 -->
            <span v-if="duration" class="duration">{{ formatDuration(duration) }}</span>

            <!-- 展开/折叠按钮 -->
            <el-button
              :icon="isExpanded ? ArrowDown : CaretRight"
              text
              size="small"
              class="toggle-btn"
              @click.stop="toggle"
            />
          </div>
        </div>

        <!-- 卡片内容（可折叠） -->
        <el-collapse-transition>
          <div v-show="isExpanded" class="card-body">
            <!-- 思考过程 -->
            <div v-if="step.content && step.type === 'THOUGHT'" class="detail-section">
              <div class="section-header">
                <span class="section-label">思考过程</span>
                <el-button
                  text
                  size="small"
                  :icon="CopyDocument"
                  @click="copyToClipboard(step.content || '')"
                >
                  复制
                </el-button>
              </div>
              <pre class="thought-content">{{ step.content }}</pre>
            </div>

            <!-- 工具参数 -->
            <div
              v-if="step.toolParams && Object.keys(step.toolParams).length > 0"
              class="detail-section"
            >
              <div class="section-header">
                <span class="section-label">调用参数</span>
              </div>
              <JsonViewer :data="step.toolParams" :default-expanded="false" />
            </div>

            <!-- 工具结果 -->
            <div v-if="step.toolResult !== undefined" class="detail-section">
              <div class="section-header">
                <span class="section-label">返回结果</span>
              </div>
              <JsonViewer :data="step.toolResult" :default-expanded="true" />
            </div>

            <!-- 其他内容 -->
            <div v-if="step.content && step.type !== 'THOUGHT'" class="detail-section">
              <div class="section-header">
                <span class="section-label">内容</span>
                <el-button
                  text
                  size="small"
                  :icon="CopyDocument"
                  @click="copyToClipboard(step.content || '')"
                >
                  复制
                </el-button>
              </div>
              <div class="content-box">{{ step.content }}</div>
            </div>

            <!-- 错误信息 -->
            <div v-if="step.error" class="detail-section error-section">
              <el-alert type="error" :closable="false" show-icon>
                <template #title>
                  <span class="error-title">执行失败</span>
                </template>
                <div class="error-message">{{ step.error }}</div>
              </el-alert>
            </div>
          </div>
        </el-collapse-transition>
      </div>
    </div>
  </div>
</template>

<style scoped>
.step-timeline-item {
  display: flex;
  gap: 16px;
  position: relative;
}

/* ===== 时间线左侧 ===== */
.timeline-left {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 36px;
  flex-shrink: 0;
  position: relative;
}

.step-number {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--bg-tertiary);
  color: var(--text-tertiary);
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
}

.timeline-line {
  width: 2px;
  flex: 1;
  background: linear-gradient(to bottom, var(--border-color), var(--border-light));
  margin: 4px 0;
}

.timeline-line.last {
  display: none;
}

.timeline-node {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 2px solid;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  transition: transform 0.2s ease;
}

.step-timeline-item:hover .timeline-node {
  transform: scale(1.1);
}

/* 运行状态动画 */
.step-running .timeline-node {
  animation: pulse-ring 2s ease-out infinite;
}

@keyframes pulse-ring {
  0% {
    box-shadow: 0 0 0 0 currentColor;
  }
  70% {
    box-shadow: 0 0 0 10px transparent;
  }
  100% {
    box-shadow: 0 0 0 0 transparent;
  }
}

/* 状态徽章 */
.status-badge {
  position: absolute;
  right: -4px;
  bottom: -4px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--bg-primary);
}

.status-badge.running {
  background: var(--color-primary);
  color: white;
}

.status-badge.success {
  background: var(--color-success);
  color: white;
}

.status-badge.error {
  background: var(--color-danger);
  color: white;
}

/* ===== 时间线右侧 ===== */
.timeline-content {
  flex: 1;
  min-width: 0;
  padding-bottom: 24px;
}

.step-card {
  background: var(--bg-primary);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-color);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
  transition: all 0.2s ease;
}

.step-card:hover {
  box-shadow: var(--shadow-md);
}

.step-running .step-card {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary-light);
}

.step-error .step-card {
  border-color: var(--color-danger);
}

/* ===== 卡片头部 ===== */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 16px 20px;
  cursor: pointer;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-light);
  transition: background 0.2s;
}

.card-header:hover {
  background: var(--bg-tertiary);
}

.header-main {
  flex: 1;
  min-width: 0;
}

.header-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.type-tag {
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 600;
}

.tool-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.step-description {
  margin: 0;
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 1.5;
}

.header-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.header-meta .el-tag {
  display: flex;
  align-items: center;
  gap: 4px;
}

.duration {
  font-size: 12px;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

.toggle-btn {
  padding: 4px;
}

/* ===== 卡片内容 ===== */
.card-body {
  padding: 16px 20px;
}

.detail-section {
  margin-bottom: 16px;
}

.detail-section:last-child {
  margin-bottom: 0;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.section-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.thought-content {
  background: var(--bg-tertiary);
  padding: 12px 16px;
  border-radius: var(--radius-md);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  color: var(--text-primary);
  border-left: 3px solid var(--color-primary);
}

.content-box {
  background: var(--bg-tertiary);
  padding: 12px 16px;
  border-radius: var(--radius-md);
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-primary);
}

.error-section {
  margin-top: 16px;
}

.error-title {
  font-weight: 600;
}

.error-message {
  font-size: 12px;
  margin-top: 4px;
}

/* ===== 响应式 ===== */
@media (max-width: 640px) {
  .timeline-left {
    width: 28px;
  }

  .timeline-node {
    width: 28px;
    height: 28px;
  }

  .step-number {
    display: none;
  }

  .card-header {
    padding: 12px 16px;
  }

  .card-body {
    padding: 12px 16px;
  }

  .header-row {
    flex-wrap: wrap;
  }

  .header-meta {
    flex-wrap: wrap;
  }
}
</style>
