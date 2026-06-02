<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { adminApi, type UserAdminVO } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Download } from '@element-plus/icons-vue'
import dayjs from 'dayjs'

const loading = ref(false)
const users = ref<UserAdminVO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchQuery = ref('')
const roleFilter = ref('')
const providerFilter = ref('')
const showUserDrawer = ref(false)
const selectedUser = ref<UserAdminVO | null>(null)
const quotaLoading = ref(false)

const quotaForm = reactive({
  dailyTokenLimit: 0,
  monthlyTokenLimit: 0,
})

// 防抖定时器
let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null

const providerLabels: Record<string, string> = {
  GITHUB: 'GitHub',
  GITLAB: 'GitLab',
  CUSTOM: '密码',
}

const formatDate = (date: string) => (date ? dayjs(date).format('YYYY-MM-DD HH:mm') : '从未登录')
const formatNumber = (num: number | undefined | null) => {
  if (num === undefined || num === null) return '0'
  return num >= 1000 ? (num / 1000).toFixed(1) + 'K' : num.toString()
}

const loadUsers = async () => {
  loading.value = true
  try {
    const res = await adminApi.getUsers({
      page: currentPage.value,
      size: pageSize.value,
      search: searchQuery.value || undefined,
      role: roleFilter.value || undefined,
      provider: providerFilter.value || undefined,
    })
    users.value = res.data
    total.value = res.total
  } catch (e) {
    console.error('Failed to load users:', e)
    ElMessage.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

const viewUser = async (user: UserAdminVO) => {
  selectedUser.value = user
  quotaForm.dailyTokenLimit = user.dailyTokenLimit || 0
  quotaForm.monthlyTokenLimit = user.monthlyTokenLimit || 0
  showUserDrawer.value = true
}

const toggleAdmin = async (user: UserAdminVO) => {
  const newRole = user.role === 'ADMIN' ? 'USER' : 'ADMIN'
  try {
    await ElMessageBox.confirm(
      `确定将用户 ${user.username} ${newRole === 'ADMIN' ? '设为' : '取消'}管理员？`,
      '提示'
    )
    await adminApi.updateUserRole(user.id, newRole)
    ElMessage.success('操作成功')
    loadUsers()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const deleteUser = async (user: UserAdminVO) => {
  try {
    await ElMessageBox.confirm(`确定删除用户 ${user.username}？`, '警告', { type: 'warning' })
    await adminApi.deleteUser(user.id)
    ElMessage.success('删除成功')
    loadUsers()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const saveQuota = async () => {
  if (!selectedUser.value) return
  quotaLoading.value = true
  try {
    await adminApi.updateUserQuota(selectedUser.value.id, {
      dailyTokenLimit: quotaForm.dailyTokenLimit,
      monthlyTokenLimit: quotaForm.monthlyTokenLimit,
    })
    ElMessage.success('配额更新成功')
    // 更新本地数据
    selectedUser.value.dailyTokenLimit = quotaForm.dailyTokenLimit
    selectedUser.value.monthlyTokenLimit = quotaForm.monthlyTokenLimit
    loadUsers()
  } catch {
    ElMessage.error('配额更新失败')
  } finally {
    quotaLoading.value = false
  }
}

const formatLimit = (limit: number | undefined | null) => {
  if (limit === undefined || limit === null || limit === 0) return '无限制'
  return limit >= 1000 ? (limit / 1000).toFixed(0) + 'K' : limit.toString()
}

// 搜索防抖处理：搜索文本输入延迟 300ms，选择器立即触发
watch([searchQuery], () => {
  if (searchDebounceTimer) {
    clearTimeout(searchDebounceTimer)
  }
  searchDebounceTimer = setTimeout(() => {
    currentPage.value = 1
    loadUsers()
  }, 300)
})

watch([roleFilter, providerFilter], () => {
  currentPage.value = 1
  loadUsers()
})

onMounted(loadUsers)
</script>

<template>
  <div class="user-management" v-loading="loading">
    <!-- 搜索和筛选工具栏 -->
    <el-card class="modern-card filter-card">
      <div class="toolbar">
        <div class="filter-bar">
          <el-input
            v-model="searchQuery"
            placeholder="搜索用户名或邮箱"
            prefix-icon="Search"
            clearable
            class="search-input"
          />
          <el-select v-model="roleFilter" placeholder="角色" clearable class="filter-select">
            <el-option label="普通用户" value="USER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
          <el-select
            v-model="providerFilter"
            placeholder="登录方式"
            clearable
            class="filter-select"
          >
            <el-option label="GitHub" value="GITHUB" />
            <el-option label="GitLab" value="GITLAB" />
            <el-option label="密码" value="CUSTOM" />
          </el-select>
        </div>
        <div class="toolbar-actions">
          <el-tooltip content="刷新" placement="top">
            <el-button @click="loadUsers">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="导出" placement="top">
            <el-button>
              <el-icon><Download /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </div>
    </el-card>

    <!-- 用户列表 -->
    <el-card class="modern-card table-card">
      <el-table :data="users" stripe>
        <el-table-column prop="username" label="用户名" min-width="150">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="32" class="user-avatar">
                {{ row.username?.charAt(0).toUpperCase() }}
              </el-avatar>
              <span class="user-name">{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="200" />
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column prop="role" label="角色" width="100" align="center">
          <template #default="{ row }">
            <span class="status-badge" :class="row.role === 'ADMIN' ? 'warning' : 'info'">
              {{ row.role === 'ADMIN' ? '管理员' : '用户' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="provider" label="登录方式" width="100" align="center">
          <template #default="{ row }">
            {{ providerLabels[row.provider] || row.provider }}
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最后登录" width="180">
          <template #default="{ row }">
            {{ formatDate(row.lastLoginAt) }}
          </template>
        </el-table-column>
        <el-table-column label="今日使用" width="120" align="right">
          <template #default="{ row }">
            <span class="token-count">{{ formatNumber(row.todayTokens) }}</span>
            <span class="token-unit">tokens</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewUser(row)">详情</el-button>
            <el-button
              link
              :type="row.role === 'ADMIN' ? 'warning' : 'primary'"
              @click="toggleAdmin(row)"
            >
              {{ row.role === 'ADMIN' ? '取消管理员' : '设为管理员' }}
            </el-button>
            <el-button link type="danger" @click="deleteUser(row)" :disabled="row.role === 'ADMIN'">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        class="pagination"
        @change="loadUsers"
      />
    </el-card>

    <!-- 用户详情抽屉 -->
    <el-drawer v-model="showUserDrawer" title="用户详情" size="500px">
      <template v-if="selectedUser">
        <el-descriptions :column="1" border class="user-descriptions">
          <el-descriptions-item label="用户ID">{{ selectedUser.id }}</el-descriptions-item>
          <el-descriptions-item label="用户名">{{ selectedUser.username }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ selectedUser.email }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ selectedUser.nickname }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ selectedUser.role }}</el-descriptions-item>
          <el-descriptions-item label="登录方式">
            {{ providerLabels[selectedUser.provider] }}
          </el-descriptions-item>
          <el-descriptions-item label="注册时间">
            {{ formatDate(selectedUser.createdAt) }}
          </el-descriptions-item>
        </el-descriptions>

        <h4 class="section-title">使用统计</h4>
        <el-row :gutter="16">
          <el-col :span="12">
            <div class="stat-card">
              <div class="stat-value">{{ formatNumber(selectedUser.todayTokens) }}</div>
              <div class="stat-label">今日 Token</div>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="stat-card">
              <div class="stat-value">${{ (selectedUser.todayCost ?? 0).toFixed(4) }}</div>
              <div class="stat-label">今日费用</div>
            </div>
          </el-col>
        </el-row>

        <el-divider />

        <h4 class="section-title">配额管理</h4>
        <el-form :model="quotaForm" label-width="120px" class="quota-form">
          <el-form-item label="每日 Token 限制">
            <el-input-number
              v-model="quotaForm.dailyTokenLimit"
              :min="0"
              :max="10000000"
              :step="1000"
              style="width: 100%"
            />
            <div class="form-tip">
              当前限制: {{ formatLimit(selectedUser.dailyTokenLimit) }}，0 表示无限制
            </div>
          </el-form-item>
          <el-form-item label="每月 Token 限制">
            <el-input-number
              v-model="quotaForm.monthlyTokenLimit"
              :min="0"
              :max="100000000"
              :step="10000"
              style="width: 100%"
            />
            <div class="form-tip">
              当前限制: {{ formatLimit(selectedUser.monthlyTokenLimit) }}，0 表示无限制
            </div>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="quotaLoading" @click="saveQuota">
              保存配额
            </el-button>
          </el-form-item>
        </el-form>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.user-management {
  padding: var(--page-padding);
  max-width: var(--max-content-width);
  margin: 0 auto;
}

.filter-card {
  margin-bottom: var(--card-spacing);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-md);
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-md);
}

.toolbar-actions {
  display: flex;
  gap: var(--space-sm);
}

.search-input {
  width: 300px;
}

.filter-select {
  width: 140px;
}

.table-card {
  margin-bottom: var(--card-spacing);
}

.user-cell {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.user-avatar {
  background: var(--gradient-primary);
  color: white;
  font-size: 14px;
  font-weight: 600;
}

.user-name {
  font-weight: 500;
}

.token-count {
  font-weight: 600;
  color: var(--text-primary);
}

.token-unit {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  margin-left: 4px;
}

.pagination {
  margin-top: var(--card-spacing);
  justify-content: flex-end;
}

.user-descriptions {
  margin-bottom: var(--space-xl);
}

.section-title {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--text-primary);
  margin: var(--space-xl) 0 var(--space-lg) 0;
}

.stat-card {
  background: var(--bg-secondary);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
  text-align: center;
}

.stat-value {
  font-size: var(--font-size-xl);
  font-weight: 600;
  color: var(--text-primary);
}

.stat-label {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
  margin-top: 4px;
}

.quota-form {
  margin-top: var(--space-lg);
}

.form-tip {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
  margin-top: 4px;
}

@media (max-width: 768px) {
  .user-management {
    padding: var(--space-lg);
  }

  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar-actions {
    justify-content: flex-end;
  }

  .search-input,
  .filter-select {
    width: 100%;
  }
}
</style>
