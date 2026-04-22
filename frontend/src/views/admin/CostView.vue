<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi, type CostStatistics, type UserCostRanking, type BudgetInfo } from '@/api/admin'

const loading = ref(true)
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
  loading.value = true

  // 独立加载每个数据，避免单个失败影响全部
  const [budgetResult, costsResult, usersResult] = await Promise.allSettled([
    adminApi.getBudget(),
    adminApi.getModelCostStatistics(),
    adminApi.getTopUsers({ limit: 10 })
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
  if (budgetResult.status === 'rejected' &&
      costsResult.status === 'rejected' &&
      usersResult.status === 'rejected') {
    ElMessage.warning('暂无成本数据，请先使用 AI 对话功能')
  }

  loading.value = false
})
</script>

<template>
  <div class="cost-view" v-loading="loading">
    <h2>成本监控</h2>

    <!-- 预算概览 -->
    <el-row :gutter="20" class="budget-overview">
      <el-col :span="12">
        <el-card>
          <div class="budget-card">
            <h4>日预算</h4>
            <div class="budget-amount">
              <span class="used">${{ (budget.dailyUsed ?? 0).toFixed(2) }}</span>
              <span class="total">/ ${{ budget.dailyTotal ?? 0 }}</span>
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
              <span class="used">${{ (budget.monthlyUsed ?? 0).toFixed(2) }}</span>
              <span class="total">/ ${{ budget.monthlyTotal ?? 0 }}</span>
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
      <el-empty v-if="modelCosts.length === 0" description="暂无数据" :image-size="80" />
      <el-table v-else :data="modelCosts" stripe>
        <el-table-column prop="modelName" label="模型" width="150" />
        <el-table-column prop="totalTokens" label="总 Token" width="120">
          <template #default="{ row }">{{ formatNumber(row.totalTokens) }}</template>
        </el-table-column>
        <el-table-column prop="totalCost" label="总费用" width="120">
          <template #default="{ row }">${{ (row.totalCost ?? 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="avgTokensPerRequest" label="平均 Token/请求" width="150">
          <template #default="{ row }">{{ (row.avgTokensPerRequest ?? 0).toFixed(0) }}</template>
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
      <el-empty v-if="topUsers.length === 0" description="暂无数据" :image-size="80" />
      <el-table v-else :data="topUsers" stripe size="small">
        <el-table-column prop="username" label="用户" width="150" />
        <el-table-column prop="tokens" label="Token" width="120">
          <template #default="{ row }">{{ formatNumber(row.tokens) }}</template>
        </el-table-column>
        <el-table-column prop="cost" label="费用">
          <template #default="{ row }">${{ (row.cost ?? 0).toFixed(4) }}</template>
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
