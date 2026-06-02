<script setup lang="ts">
/**
 * 个人中心页面
 *
 * 显示和编辑当前用户的基本信息
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import { User, Edit, Check, Close, ArrowLeft } from '@element-plus/icons-vue'
import http from '@/utils/http'
import type { UserInfo } from '@/types'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const editing = ref(false)
const userInfo = ref<UserInfo | null>(null)

const editForm = ref({
  nickname: '',
  avatar: '',
})

const providerLabels: Record<string, string> = {
  GITHUB: 'GitHub',
  GITLAB: 'GitLab',
  CUSTOM: '密码登录',
}

const displayUser = computed(() => userInfo.value || authStore.user)

onMounted(async () => {
  await loadUserInfo()
})

async function loadUserInfo() {
  loading.value = true
  try {
    userInfo.value = await http.get<UserInfo>('/api/users/me')
  } catch {
    // 如果获取失败，使用 store 中的用户信息
    userInfo.value = authStore.user
  } finally {
    loading.value = false
  }
}

function startEdit() {
  if (displayUser.value) {
    editForm.value.nickname = displayUser.value.nickname || ''
    editForm.value.avatar = displayUser.value.avatar || ''
    editing.value = true
  }
}

function cancelEdit() {
  editing.value = false
}

async function saveEdit() {
  loading.value = true
  try {
    const updated = await http.put<UserInfo>('/api/users/me', {
      nickname: editForm.value.nickname,
      avatar: editForm.value.avatar,
    })
    userInfo.value = updated
    authStore.user = updated
    editing.value = false
    ElMessage.success('保存成功')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    loading.value = false
  }
}

function formatDate(date?: string) {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

function goBack() {
  router.push('/')
}
</script>

<template>
  <div class="profile-view">
    <div class="profile-header">
      <el-button text @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
      <h1>个人中心</h1>
      <div class="header-spacer"></div>
    </div>

    <div class="profile-content" v-loading="loading">
      <el-card class="profile-card">
        <template #header>
          <div class="card-header">
            <span>基本信息</span>
            <el-button v-if="!editing" type="primary" text @click="startEdit">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
          </div>
        </template>

        <div class="user-avatar-section">
          <el-avatar :size="80" :src="displayUser?.avatar" class="avatar">
            <el-icon :size="40"><User /></el-icon>
          </el-avatar>
          <div v-if="editing" class="avatar-edit">
            <el-input v-model="editForm.avatar" placeholder="头像 URL" style="width: 300px" />
          </div>
        </div>

        <el-descriptions :column="1" border class="user-info-table">
          <el-descriptions-item label="用户名">
            {{ displayUser?.username || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="昵称">
            <div v-if="editing" class="edit-field">
              <el-input v-model="editForm.nickname" placeholder="请输入昵称" />
            </div>
            <span v-else>{{ displayUser?.nickname || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="邮箱">
            {{ displayUser?.email || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="角色">
            <el-tag :type="displayUser?.role === 'ADMIN' ? 'danger' : 'info'">
              {{ displayUser?.role === 'ADMIN' ? '管理员' : '普通用户' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="登录方式">
            {{ providerLabels[displayUser?.provider || ''] || displayUser?.provider || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="注册时间">
            {{ formatDate(displayUser?.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="最后登录">
            {{ formatDate(displayUser?.lastLoginAt) }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="editing" class="edit-actions">
          <el-button @click="cancelEdit">
            <el-icon><Close /></el-icon>
            取消
          </el-button>
          <el-button type="primary" @click="saveEdit" :loading="loading">
            <el-icon><Check /></el-icon>
            保存
          </el-button>
        </div>
      </el-card>

      <el-card class="profile-card">
        <template #header>
          <span>安全设置</span>
        </template>
        <div class="security-section">
          <div class="security-item">
            <div class="security-info">
              <h4>登录方式</h4>
              <p class="text-gray-500">
                {{ providerLabels[displayUser?.provider || ''] || displayUser?.provider || '未知' }}
              </p>
            </div>
            <el-button v-if="displayUser?.provider === 'CUSTOM'" text type="primary">
              修改密码
            </el-button>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.profile-view {
  min-height: 100vh;
  background: var(--bg-secondary);
}

.profile-header {
  display: flex;
  align-items: center;
  padding: 16px 24px;
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-color);
}

.profile-header h1 {
  flex: 1;
  text-align: center;
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.header-spacer {
  width: 80px;
}

.profile-content {
  max-width: 800px;
  margin: 24px auto;
  padding: 0 24px;
}

.profile-card {
  margin-bottom: 24px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.user-avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 24px;
}

.avatar {
  background: var(--color-primary);
  color: white;
  margin-bottom: 16px;
}

.avatar-edit {
  margin-top: 8px;
}

.user-info-table {
  margin-top: 16px;
}

.edit-field {
  width: 100%;
}

.edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--border-color);
}

.security-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.security-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid var(--border-color);
}

.security-item:last-child {
  border-bottom: none;
}

.security-info h4 {
  margin: 0 0 4px 0;
  font-size: 14px;
  font-weight: 500;
}

.security-info p {
  margin: 0;
  font-size: 13px;
}

.text-gray-500 {
  color: var(--text-tertiary);
}
</style>
