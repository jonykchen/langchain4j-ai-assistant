<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { adminApi, type DashboardMetrics, type ModelHealthInfo, type BudgetInfo, type TrendData, type ModelDistribution } from '@/api/admin'

const loading = ref(true)
const metrics = ref<DashboardMetrics>({
  totalUsers: 0,
  activeUsers: 0,
  todayTokens: 0,
  todayCost: 0,
  todayRequests: 0,
  modelHealth: [],
  budget: {
    dailyUsed: 0,
    dailyTotal: 100,
    dailyPercent: 0,
    monthlyUsed: 0,
    monthlyTotal: 2000,
    monthlyPercent: 0
  }
})

const formatNumber = (num: number) => {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toString()
}

const getStatusType = (status: string) => {
  return status === 'UP' ? 'success' : 'danger'
}

const getCircuitBreakerType = (state: string) => {
  if (state === 'CLOSED') return 'success'
  if (state === 'OPEN') return 'danger'
  return 'warning'
}

const getProgressColor = (percent: number) => {
  if (percent >= 100) return '#f56c6c'
  if (percent >= 80) return '#e6a23c'
  return '#67c23a'
}

onMounted(async () => {
  try {
    metrics.value = await adminApi.getMetrics()
  } catch (e) {
    console.error('Failed to load metrics:', e)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="admin-dashboard">
    <h2>系统概览</h2>

    <!-- 核心指标 -->
    <el-row :gutter="20" class="metrics-row">
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-icon users">
            <el-icon :size="32"><User /></el-icon>
          </div>
          <div class="metric-content">
            <div class="metric-value">{{ metrics.totalUsers }}</div>
            <div class="metric-label">总用户数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-icon active">
            <el-icon :size="32"><Connection /></el-icon>
          </div>
          <div class="metric-content">
            <div class="metric-value">{{ metrics.activeUsers }}</div>
            <div class="metric-label">今日活跃</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-icon tokens">
            <el-icon :size="32"><DataLine /></el-icon>
          </div>
          <div class="metric-content">
            <div class="metric-value">{{ formatNumber(metrics.todayTokens) }}</div>
            <div class="metric-label">今日 Token</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="metric-card">
          <div class="metric-icon cost">
            <el-icon :size="32"><Money /></el-icon>
          </div>
          <div class="metric-content">
            <div class="metric-value">${{ metrics.todayCost.toFixed(2) }}</div>
            <div class="metric-label">今日费用</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 系统状态 -->
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>模型健康状态</span>
          </template>
          <el-table :data="metrics.modelHealth" stripe size="small">
            <el-table-column prop="name" label="模型" width="150" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="circuitBreaker" label="熔断器" width="100">
              <template #default="{ row }">
                <el-tag :type="getCircuitBreakerType(row.circuitBreaker)">
                  {{ row.circuitBreaker }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="avgLatency" label="平均延迟" width="100">
              <template #default="{ row }">
                {{ row.avgLatency }}ms
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>预算使用情况</span>
          </template>
          <div class="budget-section">
            <div class="budget-item">
              <span>日预算</span>
              <el-progress
                :percentage="Math.min(metrics.budget.dailyPercent, 100)"
                :color="getProgressColor(metrics.budget.dailyPercent)"
              >
                <template #default>
                  ${{ metrics.budget.dailyUsed.toFixed(2) }} / ${{ metrics.budget.dailyTotal }}
                </template>
              </el-progress>
            </div>
            <div class="budget-item">
              <span>月预算</span>
              <el-progress
                :percentage="Math.min(metrics.budget.monthlyPercent, 100)"
                :color="getProgressColor(metrics.budget.monthlyPercent)"
              >
                <template #default>
                  ${{ metrics.budget.monthlyUsed.toFixed(2) }} / ${{ metrics.budget.monthlyTotal }}
                </template>
              </el-progress>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.admin-dashboard {
  padding: 24px;
}

.metrics-row {
  margin-bottom: 20px;
}

.metric-card {
  display: flex;
  align-items: center;
}

.metric-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  width: 100%;
}

.metric-icon {
  width: 64px;
  height: 64px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
}

.metric-icon.users { background: #e6f7ff; color: #1890ff; }
.metric-icon.active { background: #f6ffed; color: #52c41a; }
.metric-icon.tokens { background: #fff7e6; color: #fa8c16; }
.metric-icon.cost { background: #fff1f0; color: #f5222d; }

.metric-value {
  font-size: 24px;
  font-weight: 600;
}

.metric-label {
  font-size: 14px;
  color: #909399;
}

.budget-item {
  margin-bottom: 20px;
}

.budget-item span {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
}
</style>
