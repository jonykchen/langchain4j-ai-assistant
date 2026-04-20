<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { adminApi, type UserAdminVO } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'
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

const providerLabels: Record<string, string> = {
  GITHUB: 'GitHub',
  GITLAB: 'GitLab',
  CUSTOM: '密码'
}

const formatDate = (date: string) => date ? dayjs(date).format('YYYY-MM-DD HH:mm') : '从未登录'
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
      provider: providerFilter.value || undefined
    })
    users.value = res.data
    total.value = res.total
  } catch (e) {
    console.error('Failed to load users:', e)
  } finally {
    loading.value = false
  }
}

const viewUser = async (user: UserAdminVO) => {
  selectedUser.value = user
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
    // 用户取消
  }
}

const deleteUser = async (user: UserAdminVO) => {
  try {
    await ElMessageBox.confirm(`确定删除用户 ${user.username}？`, '警告', { type: 'warning' })
    await adminApi.deleteUser(user.id)
    ElMessage.success('删除成功')
    loadUsers()
  } catch (e) {
    // 用户取消
  }
}

watch([searchQuery, roleFilter, providerFilter], () => {
  currentPage.value = 1
  loadUsers()
})

onMounted(loadUsers)
</script>

<template>
  <div class="users-view">
    <div class="page-header">
      <h2>用户管理</h2>
    </div>

    <!-- 搜索和筛选 -->
    <div class="filter-bar">
      <el-input
        v-model="searchQuery"
        placeholder="搜索用户名或邮箱"
        prefix-icon="Search"
        clearable
        style="width: 300px"
      />
      <el-select v-model="roleFilter" placeholder="角色" clearable style="width: 120px">
        <el-option label="普通用户" value="USER" />
        <el-option label="管理员" value="ADMIN" />
      </el-select>
      <el-select v-model="providerFilter" placeholder="登录方式" clearable style="width: 120px">
        <el-option label="GitHub" value="GITHUB" />
        <el-option label="GitLab" value="GITLAB" />
        <el-option label="密码" value="CUSTOM" />
      </el-select>
    </div>

    <!-- 用户列表 -->
    <el-table :data="users" stripe v-loading="loading">
      <el-table-column prop="username" label="用户名" width="150" />
      <el-table-column prop="email" label="邮箱" width="200" />
      <el-table-column prop="nickname" label="昵称" width="120" />
      <el-table-column prop="role" label="角色" width="100">
        <template #default="{ row }">
          <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'">
            {{ row.role === 'ADMIN' ? '管理员' : '用户' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="provider" label="登录方式" width="100">
        <template #default="{ row }">
          {{ providerLabels[row.provider] || row.provider }}
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginAt" label="最后登录" width="180">
        <template #default="{ row }">
          {{ formatDate(row.lastLoginAt) }}
        </template>
      </el-table-column>
      <el-table-column label="今日使用" width="120">
        <template #default="{ row }">
          {{ formatNumber(row.todayTokens) }} tokens
        </template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right">
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
      @change="loadUsers"
      style="margin-top: 20px"
    />

    <!-- 用户详情抽屉 -->
    <el-drawer v-model="showUserDrawer" title="用户详情" size="500px">
      <template v-if="selectedUser">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="用户ID">{{ selectedUser.id }}</el-descriptions-item>
          <el-descriptions-item label="用户名">{{ selectedUser.username }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ selectedUser.email }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ selectedUser.nickname }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ selectedUser.role }}</el-descriptions-item>
          <el-descriptions-item label="登录方式">{{ providerLabels[selectedUser.provider] }}</el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ formatDate(selectedUser.createdAt) }}</el-descriptions-item>
        </el-descriptions>

        <h4 style="margin-top: 20px">使用统计</h4>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-statistic title="今日 Token" :value="selectedUser.todayTokens" />
          </el-col>
          <el-col :span="12">
            <el-statistic title="今日费用" :value="selectedUser.todayCost" prefix="$" :precision="4" />
          </el-col>
        </el-row>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.users-view {
  padding: 24px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}
</style>
