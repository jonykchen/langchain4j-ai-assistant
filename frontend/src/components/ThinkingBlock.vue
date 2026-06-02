<script setup lang="ts">
/**
 * ThinkingBlock.vue - 思考过程展示组件
 *
 * 功能：
 * - 展开/折叠思考过程
 * - 流式响应时显示加载动画
 */

const props = defineProps<{
  content: string
  complete: boolean
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [val: boolean]
}>()

function toggle() {
  emit('update:modelValue', !props.modelValue)
}
</script>

<template>
  <div class="thinking-section" data-testid="thinking-section">
    <!-- 可点击的头部，展开/折叠思考过程 -->
    <div class="thinking-header" data-testid="thinking-toggle" @click="toggle">
      <span class="thinking-icon">
        <svg
          v-if="modelValue"
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <path d="M6 9l6 6 6-6" />
        </svg>
        <svg
          v-else
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <path d="M9 18l6-6-6-6" />
        </svg>
      </span>
      <span class="thinking-label">
        {{ complete ? '思考过程' : '思考中...' }}
      </span>
      <!-- 流式响应时的加载动画 -->
      <span v-if="!complete" class="thinking-loading">
        <span class="dot"></span>
        <span class="dot"></span>
        <span class="dot"></span>
      </span>
    </div>
    <!-- 思考内容（可折叠） -->
    <div v-show="modelValue" class="thinking-body">
      <div class="markdown-body thinking-content" v-html="content"></div>
    </div>
  </div>
</template>

<style scoped>
.thinking-section {
  margin-bottom: 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-primary);
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  user-select: none;
  border-bottom: 1px solid var(--border-color);
  transition: background 0.2s;
}

.thinking-header:hover {
  background: var(--bg-hover);
}

.thinking-header:last-child {
  border-bottom: none;
}

.thinking-icon {
  color: var(--text-tertiary);
}

.thinking-label {
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 500;
}

/* 加载动画：三个点依次闪烁 */
.thinking-loading {
  display: flex;
  gap: 4px;
}

.thinking-loading .dot {
  width: 6px;
  height: 6px;
  background: var(--text-tertiary);
  border-radius: 50%;
  animation: dotPulse 1.4s infinite ease-in-out both;
}

.thinking-loading .dot:nth-child(1) {
  animation-delay: -0.32s;
}
.thinking-loading .dot:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes dotPulse {
  0%,
  80%,
  100% {
    opacity: 0.3;
  }
  40% {
    opacity: 1;
  }
}

.thinking-body {
  padding: 12px;
  background: var(--bg-tertiary);
}

.thinking-content {
  font-size: 13px;
  color: var(--text-secondary);
  opacity: 0.9;
}
</style>
