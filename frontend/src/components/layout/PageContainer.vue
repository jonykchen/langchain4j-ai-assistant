<script setup lang="ts">
/**
 * PageContainer 页面容器组件
 *
 * 统一页面布局，包含标题、描述、操作区域
 */
import { useSlots, computed } from 'vue'

interface Props {
  title?: string
  subtitle?: string
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false
})

const slots = useSlots()

const hasHeader = computed(() => {
  return props.title || slots.actions || slots.header
})
</script>

<template>
  <div class="page-container" v-loading="loading">
    <!-- 页面头部 -->
    <div v-if="hasHeader" class="page-header">
      <div class="page-header-left">
        <h1 v-if="title" class="page-title">{{ title }}</h1>
        <p v-if="subtitle" class="page-subtitle">{{ subtitle }}</p>
        <slot name="header" />
      </div>
      <div v-if="$slots.actions" class="page-header-actions">
        <slot name="actions" />
      </div>
    </div>

    <!-- 页面内容 -->
    <div class="page-content">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.page-container {
  padding: var(--page-padding);
  max-width: var(--max-content-width);
  margin: 0 auto;
  min-height: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--section-spacing);
  gap: var(--space-xl);
}

.page-header-left {
  flex: 1;
  min-width: 0;
}

.page-title {
  font-size: var(--font-size-2xl);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 var(--space-sm) 0;
}

.page-subtitle {
  font-size: var(--font-size-base);
  color: var(--text-tertiary);
  margin: 0;
  line-height: 1.5;
}

.page-header-actions {
  display: flex;
  gap: var(--space-md);
  flex-shrink: 0;
}

.page-content {
  /* 内容区域样式 */
}

@media (max-width: 768px) {
  .page-container {
    padding: var(--space-lg);
  }

  .page-header {
    flex-direction: column;
    gap: var(--space-md);
  }

  .page-header-actions {
    width: 100%;
  }

  .page-header-actions :deep(.el-button) {
    flex: 1;
  }
}
</style>
