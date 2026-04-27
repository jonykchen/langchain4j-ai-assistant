<script setup lang="ts">
/**
 * EmptyState 空状态组件
 *
 * 友好的空状态页面，支持自定义图标、标题、描述、操作按钮
 */
interface Props {
  title?: string
  description?: string
  icon?: string
  actionText?: string
}

withDefaults(defineProps<Props>(), {
  title: '暂无数据',
  description: '这里还没有任何内容',
  icon: 'Document'
})

const emit = defineEmits<{
  action: []
}>()
</script>

<template>
  <div class="empty-state">
    <div class="empty-state-icon">
      <el-icon :size="32"><component :is="icon" /></el-icon>
    </div>
    <h3 class="empty-state-title">{{ title }}</h3>
    <p class="empty-state-desc">{{ description }}</p>
    <div v-if="actionText" class="empty-state-action">
      <el-button type="primary" @click="emit('action')">
        {{ actionText }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-3xl) var(--space-xl);
  text-align: center;
  min-height: 200px;
}

.empty-state-icon {
  width: 80px;
  height: 80px;
  border-radius: var(--radius-xl);
  background: linear-gradient(135deg, var(--bg-secondary) 0%, var(--bg-tertiary) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--space-xl);
  color: var(--text-tertiary);
  box-shadow: var(--shadow-sm);
}

.empty-state-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 var(--space-sm) 0;
}

.empty-state-desc {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
  margin: 0 0 var(--space-xl) 0;
  max-width: 320px;
  line-height: 1.6;
}

.empty-state-action {
  margin-top: var(--space-md);
}
</style>
