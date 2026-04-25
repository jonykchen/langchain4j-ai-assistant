<script setup lang="ts">
/**
 * 确认对话框组件
 *
 * 展示需要用户确认的敏感操作，提供批准/拒绝按钮。
 *
 * @author jonychen
 */
import { computed } from 'vue'
import { DangerFilled } from '@element-plus/icons-vue'
import type { RiskLevel } from '@/types/agent'

const props = defineProps<{
  confirmationId: string
  operation: string
  description: string
  riskLevel: RiskLevel
}>()

const emit = defineEmits<{
  (e: 'approve', confirmationId: string): void
  (e: 'reject', confirmationId: string): void
}>()

const riskLevelColors: Record<RiskLevel, string> = {
  LOW: 'success',
  MEDIUM: 'warning',
  HIGH: 'danger',
  CRITICAL: 'danger'
}

const riskLevelLabels: Record<RiskLevel, string> = {
  LOW: '低风险',
  MEDIUM: '中风险',
  HIGH: '高风险',
  CRITICAL: '极高风险'
}

const riskColor = computed(() => riskLevelColors[props.riskLevel])
const riskLabel = computed(() => riskLevelLabels[props.riskLevel])
const isCritical = computed(() => props.riskLevel === 'CRITICAL')

function handleApprove() {
  emit('approve', props.confirmationId)
}

function handleReject() {
  emit('reject', props.confirmationId)
}
</script>

<template>
  <div class="confirmation-dialog" :class="{ 'is-critical': isCritical }">
    <div class="confirmation-header">
      <el-icon :size="24" :color="isCritical ? '#f56c6c' : '#e6a23c'">
        <DangerFilled />
      </el-icon>
      <div class="header-text">
        <h3>需要确认操作</h3>
        <el-tag :type="riskColor" size="small">{{ riskLabel }}</el-tag>
      </div>
    </div>

    <div class="confirmation-body">
      <div class="operation-name">
        <span class="label">操作：</span>
        <span class="value">{{ operation }}</span>
      </div>
      <div class="operation-desc">
        <span class="label">说明：</span>
        <span class="value">{{ description }}</span>
      </div>
    </div>

    <div class="confirmation-footer">
      <el-button @click="handleReject">
        <el-icon><Close /></el-icon>
        取消
      </el-button>
      <el-button
        :type="isCritical ? 'danger' : 'primary'"
        @click="handleApprove"
      >
        <el-icon><Check /></el-icon>
        确认执行
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.confirmation-dialog {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  margin: 16px 0;
  border: 1px solid #e4e7ed;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.confirmation-dialog.is-critical {
  border-color: #f56c6c;
  background: #fef0f0;
}

.confirmation-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.header-text {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-text h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.confirmation-body {
  margin-bottom: 20px;
}

.operation-name,
.operation-desc {
  margin-bottom: 8px;
}

.label {
  color: #909399;
  font-size: 13px;
}

.value {
  color: #303133;
  font-size: 14px;
}

.confirmation-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
