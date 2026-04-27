<script setup lang="ts">
/**
 * MessageToolbar.vue - 消息操作工具栏
 *
 * 功能：
 * - 点赞 / 点踩
 * - 复制消息
 * - 重新生成
 */

const props = defineProps<{
  modelValue: 'like' | 'dislike' | null
}>()

const emit = defineEmits<{
  'update:modelValue': [val: 'like' | 'dislike' | null]
  copy: []
  regenerate: []
}>()

function handleFeedback(type: 'like' | 'dislike') {
  emit('update:modelValue', props.modelValue === type ? null : type)
}
</script>

<template>
  <div class="message-actions" @click.stop>
    <!-- 点赞按钮 -->
    <button
      class="action-btn"
      :class="{ active: modelValue === 'like' }"
      title="有帮助"
      @click="handleFeedback('like')"
    >
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M7 10v12"/><path d="M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z"/></svg>
    </button>
    <!-- 点踩按钮 -->
    <button
      class="action-btn"
      :class="{ active: modelValue === 'dislike' }"
      title="没帮助"
      @click="handleFeedback('dislike')"
    >
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 14V2"/><path d="M9 18.12 10 14H4.17a2 2 0 0 1-1.92-2.56l2.33-8A2 2 0 0 1 6.5 2H20a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2.76a2 2 0 0 0-1.79 1.11L12 22a3.13 3.13 0 0 1-3-3.88Z"/></svg>
    </button>
    <!-- 复制按钮 -->
    <button class="action-btn" title="复制" @click="emit('copy')">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="14" height="14" x="8" y="8" rx="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg>
    </button>
    <!-- 重新生成按钮 -->
    <button class="action-btn" title="重新生成" @click="emit('regenerate')">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/><path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/><path d="M16 21h5v-5"/></svg>
    </button>
  </div>
</template>

<style scoped>
.message-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 12px;
  padding-top: 8px;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  padding: 0;
}

.action-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.action-btn.active {
  color: var(--color-primary);
  background: rgba(64, 158, 255, 0.08);
}
</style>
