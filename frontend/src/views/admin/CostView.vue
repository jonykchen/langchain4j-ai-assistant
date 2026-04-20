<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { adminApi, type CostStatistics, type UserCostRanking, type BudgetInfo } from '@/api/admin'

const budget = ref<BudgetInfo>({
  dailyUsed: 0,
  dailyTotal: 100,
  dailyPercent: 0,
  monthlyUsed: 0,
  monthlyTotal: 2000,
  monthlyPercent: 0
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
  if (percent >= 100) return '#f56c6c'
  if (percent >= 80) return '#e6a23c'
  return '#67c23a'
}

onMounted(async () => {
  try {
    const [budgetData, costsData, usersData] = await Promise.all([
      adminApi.getBudget(),
      adminApi.getModelCostStatistics(),
      adminApi.getTopUsers({ limit: 10 })
    ])

    budget.value = budgetData
    modelCosts.value = costsData
    topUsers.value = usersData
  } catch (e) {
    console.error('Failed to load cost data:', e)
  }
})
</script>

<template>
  <div class="cost-view">
    <h2>成本监控</h2>

    <!-- 预算概览 -->
    <el-row :gutter="20" class="budget-overview">
      <el-col :span="12">
        <el-card>
          <div class="budget-card">
            <h4>日预算</h4>
            <div class="budget-amount">
              <span class="used">${{ budget.dailyUsed.toFixed(2) }}</span>
              <span class="total">/ ${{ budget.dailyTotal }}</span>
            </div>
            <el-progress
              :percentage="Math.min(budget.dailyPercent, 100)"
              :color="getProgressColor(budget.dailyPercent)"
              :stroke-width="12"
            />
            <div class="budget-status">
              <el-tag v-if="budget.dailyPercent >= 100" type="danger">已超支</el-tag>
              <el-tag v-else-if="budget.dailyPercent >= 80" type="warning">接近上限</el-tag>
              <el-tag v-else type="success">正常</el-tag>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <div class="budget-card">
            <h4>月预算</h4>
            <div class="budget-amount">
              <span class="used">${{ budget.monthlyUsed.toFixed(2) }}</span>
              <span class="total">/ ${{ budget.monthlyTotal }}</span>
            </div>
            <el-progress
              :percentage="Math.min(budget.monthlyPercent, 100)"
              :color="getProgressColor(budget.monthlyPercent)"
              :stroke-width="12"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 模型成本统计 -->
    <el-card style="margin-top: 20px">
      <template #header>
        <span>模型成本统计</span>
      </template>
      <el-table :data="modelCosts" stripe>
        <el-table-column prop="modelName" label="模型" width="150" />
        <el-table-column prop="totalTokens" label="总 Token" width="120">
          <template #default="{ row }">{{ formatNumber(row.totalTokens) }}</template>
        </el-table-column>
        <el-table-column prop="totalCost" label="总费用" width="120">
          <template #default="{ row }">${{ row.totalCost.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="avgTokensPerRequest" label="平均 Token/请求" width="150">
          <template #default="{ row }">{{ row.avgTokensPerRequest.toFixed(0) }}</template>
        </el-table-column>
        <el-table-column prop="requestCount" label="请求数" width="100" />
        <el-table-column label="占比" width="200">
          <template #default="{ row }">
            <el-progress :percentage="row.costPercent" :stroke-width="8" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 用户消费排行 -->
    <el-card style="margin-top: 20px">
      <template #header>
        <span>用户消费排行（今日）</span>
      </template>
      <el-table :data="topUsers" stripe size="small">
        <el-table-column prop="username" label="用户" width="150" />
        <el-table-column prop="tokens" label="Token" width="120">
          <template #default="{ row }">{{ formatNumber(row.tokens) }}</template>
        </el-table-column>
        <el-table-column prop="cost" label="费用">
          <template #default="{ row }">${{ row.cost.toFixed(4) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.cost-view {
  padding: 24px;
}

.budget-card h4 {
  margin-bottom: 12px;
}

.budget-amount {
  margin-bottom: 12px;
}

.budget-amount .used {
  font-size: 24px;
  font-weight: 600;
}

.budget-amount .total {
  font-size: 14px;
  color: #909399;
}

.budget-status {
  margin-top: 12px;
}
</style>
