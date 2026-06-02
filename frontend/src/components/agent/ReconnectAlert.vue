<script setup lang="ts">
/**
 * ReconnectAlert 组件
 *
 * <p>SSE 断线重连提示组件（Inline Banner 模式）。
 * 作为文档流的一部分，不遮挡下方内容。
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    isReconnecting?: boolean
    reconnectAttempts?: number
    maxReconnectAttempts?: number
    isConnected?: boolean
    connectionState?: string
  }>(),
  {
    isReconnecting: false,
    reconnectAttempts: 0,
    maxReconnectAttempts: 5,
    isConnected: true,
    connectionState: 'idle',
  }
)

const emit = defineEmits<{
  (e: 'reconnect'): void
  (e: 'dismiss'): void
}>()

const statusText = computed(() => {
  if (props.isConnected) return '已连接'
  if (props.isReconnecting) return '正在重连...'
  return '连接已断开'
})

const showReconnectAlert = computed(() => {
  if (props.connectionState === 'idle') return false
  return !props.isConnected || props.isReconnecting
})

const reconnectProgress = computed(() => {
  if (props.maxReconnectAttempts === 0) return 0
  return (props.reconnectAttempts / props.maxReconnectAttempts) * 100
})

function handleReconnect() {
  emit('reconnect')
}
</script>

<template>
  <transition name="slide-down">
    <div v-if="showReconnectAlert" class="reconnect-alert">
      <el-alert
        v-if="!isReconnecting"
        type="warning"
        :title="statusText"
        show-icon
        :closable="false"
      >
        <template #default>
          <div class="alert-content">
            <span>与服务器的连接已断开，正在尝试重连...</span>
            <span v-if="reconnectAttempts > 0" class="attempt-info">
              (第 {{ reconnectAttempts }}/{{ maxReconnectAttempts }} 次尝试)
            </span>
          </div>
        </template>
        <template #action>
          <el-button type="primary" size="small" @click="handleReconnect">立即重连</el-button>
        </template>
      </el-alert>

      <el-alert v-else type="info" :title="statusText" show-icon :closable="false">
        <template #default>
          <div class="alert-content reconnecting">
            <span>正在尝试重新连接...</span>
            <el-progress
              :percentage="reconnectProgress"
              :show-text="false"
              :stroke-width="4"
              class="reconnect-progress"
            />
            <span class="attempt-info">{{ reconnectAttempts }}/{{ maxReconnectAttempts }}</span>
          </div>
        </template>
      </el-alert>
    </div>
  </transition>
</template>

<style scoped>
.reconnect-alert {
  flex-shrink: 0;
  padding: 8px 16px;
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-color);
  box-shadow: var(--shadow-sm);
}

.alert-content {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.alert-content.reconnecting {
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.attempt-info {
  font-size: 12px;
  color: var(--text-tertiary);
}

.reconnect-progress {
  width: 100px;
  margin-top: 4px;
}

.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.3s ease;
}

.slide-down-enter-from,
.slide-down-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
