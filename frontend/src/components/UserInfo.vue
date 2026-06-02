<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessageBox } from 'element-plus'
import { User, Setting, SwitchButton, ArrowDown } from '@element-plus/icons-vue'

const router = useRouter()
const authStore = useAuthStore()

const user = computed(() => authStore.user)
const isAdmin = computed(() => authStore.isAdmin)

const handleCommand = async (command: string) => {
  switch (command) {
    case 'admin':
      router.push('/admin')
      break
    case 'profile':
      router.push('/profile')
      break
    case 'logout':
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      })
      await authStore.logout()
      router.push('/login')
      break
  }
}
</script>

<template>
  <el-dropdown trigger="click" @command="handleCommand">
    <div class="user-info" data-testid="user-info">
      <el-avatar :size="32" :src="user?.avatar" class="avatar">
        {{ user?.username?.charAt(0).toUpperCase() }}
      </el-avatar>
      <span class="username">{{ user?.nickname || user?.username }}</span>
      <el-icon class="arrow"><ArrowDown /></el-icon>
    </div>

    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="profile">
          <el-icon><User /></el-icon>
          个人中心
        </el-dropdown-item>
        <el-dropdown-item v-if="isAdmin" command="admin">
          <el-icon><Setting /></el-icon>
          管理后台
        </el-dropdown-item>
        <el-dropdown-item divided command="logout" data-testid="logout-button">
          <el-icon><SwitchButton /></el-icon>
          退出登录
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 8px;
  transition: background 0.2s;
}

.user-info:hover {
  background: #f5f7fa;
}

.avatar {
  background: #409eff;
  color: white;
  font-size: 14px;
}

.username {
  font-size: 14px;
  color: #374151;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.arrow {
  font-size: 12px;
  color: #9ca3af;
}
</style>
