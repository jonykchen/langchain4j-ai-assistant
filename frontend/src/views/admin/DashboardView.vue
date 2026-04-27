<script setup lang="ts">
/**
 * DashboardView 仪表盘页面（现代化设计版）
 *
 * 使用 StatsCard、图表组件展示系统指标
 */
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi, type DashboardMetrics } from '@/api/admin'
import { StatsCard, PageContainer, EmptyState } from '@/components/layout'
import { LineChart, PieChart } from '@/components/charts'

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

// 图表数据
const tokenTrendData = computed(() => [
  { name: 'Token 使用趋势', data: [
    { name: '00:00', value: 1200 },
    { name: '04:00', value: 800 },
    { name: '08:00', value: 2400 },
    { name: '12:00', value: 3600 },
    { name: '16:00', value: 2800 },
    { name: '20:00', value: 1800 },
    { name: '24:00', value: 1200 }
  ]}
])

const modelCostData = computed(() =>
  metrics.value.modelHealth.map(m => ({
    name: m.name,
    value: m.avgLatency || 0
  }))
)

const getProgressColor = (percent: number) => {
  if (percent >= 100) return 'var(--color-danger)'
  if (percent >= 80) return 'var(--color-warning)'
  return 'var(--color-success)'
}

onMounted(async () => {
  try {
    metrics.value = await adminApi.getMetrics()
  } catch (e) {
    console.error('Failed to load metrics:', e)
    ElMessage.error('加载仪表盘数据失败，请刷新重试')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <PageContainer :loading="loading">
    <!-- 核心指标卡片 -->
    <div class="stats-grid">
      <StatsCard
        title="总用户数"
        :value="metrics.totalUsers"
        icon="User"
        icon-type="users"
        trend="up"
        trend-value="12%"
      />
      <StatsCard
        title="今日活跃"
        :value="metrics.activeUsers"
        icon="Connection"
        icon-type="active"
        trend="up"
        trend-value="8%"
      />
      <StatsCard
        title="今日 Token"
        :value="metrics.todayTokens"
        icon="DataLine"
        icon-type="tokens"
        trend="up"
        trend-value="24%"
      />
      <StatsCard
        title="今日费用"
        :value="'$' + (metrics.todayCost ?? 0).toFixed(2)"
        icon="Money"
        icon-type="cost"
        trend="down"
        trend-value="3%"
      />
    </div>

    <!-- 图表区域 -->
    <div class="charts-grid">
      <el-card class="modern-card chart-card">
        <template #header>
          <div class="card-header">
            <span class="card-title">Token 使用趋势</span>
            <el-tag type="success" size="small">实时</el-tag>
          </div>
        </template>
        <LineChart :series="tokenTrendData" height="280px" />
      </el-card>

      <el-card class="modern-card chart-card">
        <template #header>
          <div class="card-header">
            <span class="card-title">模型响应延迟分布</span>
          </div>
        </template>
        <PieChart v-if="modelCostData.length > 0" :data="modelCostData" height="280px" />
        <EmptyState v-else title="暂无数据" description="模型健康数据加载中..." />
      </el-card>
    </div>

    <!-- 系统状态 -->
    <div class="content-grid">
      <el-card class="modern-card">
        <template #header>
          <div class="card-header">
            <span class="card-title">模型健康状态</span>
            <el-tag type="success" size="small">运行正常</el-tag>
          </div>
        </template>
        <el-table :data="metrics.modelHealth" class="modern-table">
          <el-table-column prop="name" label="模型" min-width="120" />
          <el-table-column prop="status" label="状态" width="100" align="center">
            <template #default="{ row }">
              <span class="status-badge" :class="row.status.toLowerCase()">
                {{ row.status }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="circuitBreaker" label="熔断器" width="100" align="center">
            <template #default="{ row }">
              <span class="status-badge" :class="row.circuitBreaker.toLowerCase()">
                {{ row.circuitBreaker }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="avgLatency" label="平均延迟" width="100" align="right">
            <template #default="{ row }">
              <span class="latency-value">{{ row.avgLatency ?? 0 }}ms</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card class="modern-card">
        <template #header>
          <div class="card-header">
            <span class="card-title">预算使用情况</span>
            <el-button text type="primary" size="small">详情</el-button>
          </div>
        </template>
        <div class="budget-section">
          <div class="budget-item">
            <div class="budget-header">
              <span class="budget-label">日预算</span>
              <span class="budget-amount">
                ${{ (metrics.budget.dailyUsed ?? 0).toFixed(2) }} / ${{ metrics.budget.dailyTotal ?? 0 }}
              </span>
            </div>
            <el-progress
              :percentage="Math.min(metrics.budget.dailyPercent ?? 0, 100)"
              :color="getProgressColor(metrics.budget.dailyPercent ?? 0)"
              :stroke-width="8"
              :show-text="false"
            />
          </div>
          <div class="budget-item">
            <div class="budget-header">
              <span class="budget-label">月预算</span>
              <span class="budget-amount">
                ${{ (metrics.budget.monthlyUsed ?? 0).toFixed(2) }} / ${{ metrics.budget.monthlyTotal ?? 0 }}
              </span>
            </div>
            <el-progress
              :percentage="Math.min(metrics.budget.monthlyPercent ?? 0, 100)"
              :color="getProgressColor(metrics.budget.monthlyPercent ?? 0)"
              :stroke-width="8"
              :show-text="false"
            />
          </div>
        </div>
      </el-card>
    </div>
  </PageContainer>
</template>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--card-spacing);
  margin-bottom: var(--section-spacing);
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: var(--card-spacing);
  margin-bottom: var(--section-spacing);
}

.chart-card {
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--text-primary);
}

.content-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: var(--card-spacing);
}

.modern-card {
  border-radius: var(--radius-xl);
  border: 1px solid var(--border-light);
  box-shadow: var(--shadow-card);
  transition: all var(--duration-normal) var(--ease-out);
}

.modern-card:hover {
  box-shadow: var(--shadow-card-hover);
}

.budget-section {
  padding: var(--space-md) 0;
}

.budget-item {
  margin-bottom: var(--space-xl);
}

.budget-item:last-child {
  margin-bottom: 0;
}

.budget-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-md);
}

.budget-label {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--text-secondary);
}

.budget-amount {
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  font-weight: 600;
}

.latency-value {
  font-variant-numeric: tabular-nums;
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .charts-grid,
  .content-grid {
    grid-template-columns: 1fr;
  }
}
</style>