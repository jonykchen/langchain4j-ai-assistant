<template>
  <el-card shadow="hover" class="execution-history-panel">
    <template #header>
      <div class="flex justify-between items-center">
        <span>执行历史</span>
        <el-button
          v-if="history.length > 0"
          type="danger"
          size="small"
          text
          @click="handleClear"
        >
          清空
        </el-button>
      </div>
    </template>

    <div v-if="history.length === 0" class="empty-hint">
      <el-empty description="暂无执行记录" :image-size="60" />
    </div>

    <div v-else class="history-list">
      <div
        v-for="item in history"
        :key="item.id"
        class="history-item"
        :class="{ active: selectedId === item.id }"
        @click="handleSelect(item)"
      >
        <div class="history-header">
          <el-tag :type="getStrategyTagType(item.strategy)" size="small" effect="plain">
            {{ getStrategyLabel(item.strategy) }}
          </el-tag>
          <el-tag :type="item.result.success ? 'success' : 'danger'" size="small">
            {{ item.result.success ? '成功' : '失败' }}
          </el-tag>
        </div>
        <div class="history-goal" :title="item.goal">
          {{ item.goal || '-' }}
        </div>
        <div class="history-meta">
          <span class="history-time">{{ formatTime(item.timestamp) }}</span>
          <span v-if="item.result.executionTimeMs" class="history-duration">
            {{ item.result.executionTimeMs }}ms
          </span>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ExecutionHistoryItem } from '@/api/planning'

defineProps<{
  history: ExecutionHistoryItem[]
}>()

const emit = defineEmits<{
  select: [item: ExecutionHistoryItem]
  clear: []
}>()

const selectedId = defineModel<string>('selectedId', { default: '' })

const getStrategyLabel = (strategy: ExecutionHistoryItem['strategy']) => {
  const labels: Record<string, string> = {
    auto: '自动',
    react: 'ReAct',
    'plan-execute': 'Plan-Exec',
    predefined: '预定义'
  }
  return labels[strategy] || strategy
}

const getStrategyTagType = (strategy: ExecutionHistoryItem['strategy']) => {
  const types: Record<string, string> = {
    auto: '',
    react: 'warning',
    'plan-execute': 'success',
    predefined: 'info'
  }
  return types[strategy] || ''
}

const formatTime = (timestamp: string) => {
  if (!timestamp) return '-'
  const date = new Date(timestamp)
  const now = new Date()
  const isToday = date.toDateString() === now.toDateString()

  if (isToday) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  }
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }) + ' ' +
    date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const handleSelect = (item: ExecutionHistoryItem) => {
  selectedId.value = item.id
  emit('select', item)
}

const handleClear = async () => {
  try {
    await ElMessageBox.confirm('确定清空所有执行历史？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    emit('clear')
    ElMessage.success('已清空')
  } catch {
    // User cancelled
  }
}
</script>

<style scoped>
.execution-history-panel {
  height: 100%;
}

.flex {
  display: flex;
}

.justify-between {
  justify-content: space-between;
}

.items-center {
  align-items: center;
}

.empty-hint {
  padding: 20px 0;
}

.history-list {
  max-height: 600px;
  overflow-y: auto;
}

.history-item {
  padding: 10px 12px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background 0.2s;
  border-radius: 6px;
  margin-bottom: 4px;
}

.history-item:hover {
  background: #f5f7fa;
}

.history-item.active {
  background: #ecf5ff;
  border-color: #409eff;
}

.history-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.history-goal {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.history-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
}

.history-duration {
  color: #67C23A;
}
</style>
