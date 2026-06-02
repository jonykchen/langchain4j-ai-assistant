<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi, type CostStatistics, type UserCostRanking, type BudgetInfo } from '@/api/admin'
import { PageContainer } from '@/components/layout'
import { BarChart } from '@/components/charts'

const loading = ref(true)
const budget = ref<BudgetInfo>({
  dailyUsed: 0,
  dailyTotal: 100,
  dailyPercent: 0,
  monthlyUsed: 0,
  monthlyTotal: 2000,
  monthlyPercent: 0,
})

const modelCosts = ref<CostStatistics[]>([])
const topUsers = ref<UserCostRanking[]>([])

const formatNumber = (num: number | undefined | null) => {
  if (num === undefined || num === null) return '0'
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toString()
}

const getProgressColor = (percent: number) => {
  if (percent >= 100) return 'var(--color-danger)'
  if (percent >= 80) return 'var(--color-warning)'
  return 'var(--color-success)'
}

// 图表数据
const modelCostChartData = computed(() => [
  {
    name: '费用',
    data: modelCosts.value.map(m => m.totalCost || 0),
    color: '#667eea',
  },
])

const chartXAxisData = computed(() => modelCosts.value.map(m => m.modelName || ''))

onMounted(async () => {
  loading.value = true

  // 独立加载每个数据，避免单个失败影响全部
  const [budgetResult, costsResult, usersResult] = await Promise.allSettled([
    adminApi.getBudget(),
    adminApi.getModelCostStatistics(),
    adminApi.getTopUsers({ limit: 10 }),
  ])

  // 处理预算数据
  if (budgetResult.status === 'fulfilled') {
    budget.value = budgetResult.value
  } else {
    console.error('加载预算数据失败:', budgetResult.reason)
  }

  // 处理模型成本数据
  if (costsResult.status === 'fulfilled') {
    modelCosts.value = costsResult.value
  } else {
    console.error('加载模型成本数据失败:', costsResult.reason)
  }

  // 处理用户排行数据
  if (usersResult.status === 'fulfilled') {
    topUsers.value = usersResult.value
  } else {
    console.error('加载用户排行数据失败:', usersResult.reason)
  }

  // 如果全部失败，显示提示
  if (
    budgetResult.status === 'rejected' &&
    costsResult.status === 'rejected' &&
    usersResult.status === 'rejected'
  ) {
    ElMessage.warning('暂无成本数据，请先使用 AI 对话功能')
  }

  loading.value = false
})
</script>

<template>
  <PageContainer :loading="loading">
    <!-- 预算概览 -->
    <div class="budget-grid">
      <el-card class="modern-card budget-card">
        <div class="budget-content">
          <div class="budget-header">
            <h4 class="budget-title">日预算</h4>
            <el-tag
              :type="
                budget.dailyPercent >= 100
                  ? 'danger'
                  : budget.dailyPercent >= 80
                    ? 'warning'
                    : 'success'
              "
              size="small"
            >
              {{
                budget.dailyPercent >= 100
                  ? '已超支'
                  : budget.dailyPercent >= 80
                    ? '接近上限'
                    : '正常'
              }}
            </el-tag>
          </div>
          <div class="budget-amount">
            <span class="used">${{ (budget.dailyUsed ?? 0).toFixed(2) }}</span>
            <span class="separator">/</span>
            <span class="total">${{ budget.dailyTotal ?? 0 }}</span>
          </div>
          <el-progress
            :percentage="Math.min(budget.dailyPercent, 100)"
            :color="getProgressColor(budget.dailyPercent)"
            :stroke-width="12"
          />
        </div>
      </el-card>

      <el-card class="modern-card budget-card">
        <div class="budget-content">
          <div class="budget-header">
            <h4 class="budget-title">月预算</h4>
            <el-tag
              :type="
                budget.monthlyPercent >= 100
                  ? 'danger'
                  : budget.monthlyPercent >= 80
                    ? 'warning'
                    : 'success'
              "
              size="small"
            >
              {{
                budget.monthlyPercent >= 100
                  ? '已超支'
                  : budget.monthlyPercent >= 80
                    ? '接近上限'
                    : '正常'
              }}
            </el-tag>
          </div>
          <div class="budget-amount">
            <span class="used">${{ (budget.monthlyUsed ?? 0).toFixed(2) }}</span>
            <span class="separator">/</span>
            <span class="total">${{ budget.monthlyTotal ?? 0 }}</span>
          </div>
          <el-progress
            :percentage="Math.min(budget.monthlyPercent, 100)"
            :color="getProgressColor(budget.monthlyPercent)"
            :stroke-width="12"
          />
        </div>
      </el-card>
    </div>

    <!-- 图表展示 -->
    <el-card class="modern-card chart-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">模型费用对比</span>
        </div>
      </template>
      <BarChart
        v-if="modelCosts.length > 0"
        :series="modelCostChartData"
        :x-axis-data="chartXAxisData"
        height="300px"
      />
      <div v-else class="empty-chart">
        <el-icon :size="48" color="var(--text-tertiary)"><DataLine /></el-icon>
        <p>暂无数据</p>
      </div>
    </el-card>

    <!-- 模型成本统计 -->
    <el-card class="modern-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">模型成本统计</span>
        </div>
      </template>
      <el-table :data="modelCosts" stripe>
        <el-table-column prop="modelName" label="模型" min-width="150" />
        <el-table-column prop="totalTokens" label="总 Token" width="120" align="right">
          <template #default="{ row }">
            <span class="value-number">{{ formatNumber(row.totalTokens) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="totalCost" label="总费用" width="120" align="right">
          <template #default="{ row }">
            <span class="value-money">${{ (row.totalCost ?? 0).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="avgTokensPerRequest"
          label="平均 Token/请求"
          width="150"
          align="right"
        >
          <template #default="{ row }">
            <span class="value-number">{{ (row.avgTokensPerRequest ?? 0).toFixed(0) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="requestCount" label="请求数" width="100" align="right">
          <template #default="{ row }">
            <span class="value-number">{{ row.requestCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="占比" width="200">
          <template #default="{ row }">
            <el-progress :percentage="row.costPercent" :stroke-width="8" />
          </template>
        </el-table-column>
      </el-table>
      <div v-if="modelCosts.length === 0" class="table-empty">
        <el-empty description="暂无数据" :image-size="80" />
      </div>
    </el-card>

    <!-- 用户消费排行 -->
    <el-card class="modern-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">用户消费排行（今日）</span>
        </div>
      </template>
      <el-table :data="topUsers" stripe size="small">
        <el-table-column prop="username" label="用户" min-width="150">
          <template #default="{ row, $index }">
            <div class="rank-cell">
              <span class="rank-badge" :class="{ top3: $index < 3 }">{{ $index + 1 }}</span>
              <span>{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="tokens" label="Token" width="120" align="right">
          <template #default="{ row }">
            <span class="value-number">{{ formatNumber(row.tokens) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="cost" label="费用" align="right">
          <template #default="{ row }">
            <span class="value-money">${{ (row.cost ?? 0).toFixed(4) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="topUsers.length === 0" class="table-empty">
        <el-empty description="暂无数据" :image-size="80" />
      </div>
    </el-card>
  </PageContainer>
</template>

<style scoped>
.budget-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: var(--card-spacing);
  margin-bottom: var(--section-spacing);
}

.budget-card {
  overflow: hidden;
}

.budget-content {
  padding: var(--space-lg);
}

.budget-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-lg);
}

.budget-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.budget-amount {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
  margin-bottom: var(--space-lg);
}

.budget-amount .used {
  font-size: var(--font-size-3xl);
  font-weight: 700;
  color: var(--text-primary);
}

.budget-amount .separator {
  font-size: var(--font-size-lg);
  color: var(--text-tertiary);
}

.budget-amount .total {
  font-size: var(--font-size-lg);
  color: var(--text-secondary);
}

.chart-card {
  margin-bottom: var(--card-spacing);
}

.empty-chart {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--text-tertiary);
}

.empty-chart p {
  margin-top: var(--space-md);
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

.value-number {
  font-weight: 600;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.value-money {
  font-weight: 600;
  color: var(--color-warning);
  font-variant-numeric: tabular-nums;
}

.table-empty {
  padding: var(--space-xl);
}

.rank-cell {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.rank-badge {
  width: 24px;
  height: 24px;
  border-radius: var(--radius-full);
  background: var(--bg-secondary);
  color: var(--text-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.rank-badge.top3 {
  background: var(--gradient-warning);
  color: white;
}

@media (max-width: 768px) {
  .budget-grid {
    grid-template-columns: 1fr;
  }
}
</style>
