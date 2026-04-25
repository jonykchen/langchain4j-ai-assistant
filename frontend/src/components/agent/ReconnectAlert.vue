<script setup lang="ts">
/**
 * ReconnectAlert 组件
 *
 * <p>SSE 断线重连提示组件。
 *
 * <h2>功能</h2>
 * <ul>
 *   <li>显示连接状态（已连接/断线中/重连中）</li>
 *   <li>断线时显示警告提示</li>
 *   <li>显示重连次数和进度</li>
 *   <li>手动重连按钮</li>
 * </ul>
 *
 * @author jonychen
 */
import { computed } from 'vue'

/** Props 定义 */
const props = withDefaults(defineProps<{
  /** 是否正在重连 */
  isReconnecting?: boolean
  /** 当前重连次数 */
  reconnectAttempts?: number
  /** 最大重连次数 */
  maxReconnectAttempts?: number
  /** 是否已连接 */
  isConnected?: boolean
}>(), {
  isReconnecting: false,
  reconnectAttempts: 0,
  maxReconnectAttempts: 5,
  isConnected: true
})

/** Events 定义 */
const emit = defineEmits<{
  (e: 'reconnect'): void
  (e: 'dismiss'): void
}>()

/** 连接状态文本 */
const statusText = computed(() => {
  if (props.isConnected) return '已连接'
  if (props.isReconnecting) return '正在重连...'
  return '连接已断开'
})

/** 是否显示重连提示 */
const showReconnectAlert = computed(() => {
  return !props.isConnected || props.isReconnecting
})

/** 重连进度百分比 */
const reconnectProgress = computed(() => {
  if (props.maxReconnectAttempts === 0) return 0
  return (props.reconnectAttempts / props.maxReconnectAttempts) * 100
})

/** 手动重连 */
function handleReconnect() {
  emit('reconnect')
}

/** 关闭提示（预留，未使用） */
// @ts-expect-error Reserved for future use
const _handleDismiss = () => {
  emit('dismiss')
}
</script>

<template>
  <transition name="slide-down">
    <div v-if="showReconnectAlert" class="reconnect-alert">
      <!-- 已断线状态 -->
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
          <el-button type="primary" size="small" @click="handleReconnect">
            立即重连
          </el-button>
        </template>
      </el-alert>

      <!-- 重连中状态 -->
      <el-alert
        v-else
        type="info"
        :title="statusText"
        show-icon
        :closable="false"
      >
        <template #default>
          <div class="alert-content reconnecting">
            <span>正在尝试重新连接...</span>
            <el-progress
              :percentage="reconnectProgress"
              :show-text="false"
              :stroke-width="4"
              class="reconnect-progress"
            />
            <span class="attempt-info">
              {{ reconnectAttempts }}/{{ maxReconnectAttempts }}
            </span>
          </div>
        </template>
      </el-alert>
    </div>
  </transition>
</template>

<style scoped>
.reconnect-alert {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(4px);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
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
  color: #909399;
}

.reconnect-progress {
  width: 100px;
  margin-top: 4px;
}

/* 过渡动画 */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.3s ease;
}

.slide-down-enter-from,
.slide-down-leave-to {
  opacity: 0;
  transform: translateY(-100%);
}
</style>
