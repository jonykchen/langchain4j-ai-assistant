<template>
  <el-dropdown trigger="click" @command="handleCommand">
    <div class="user-info">
      <el-avatar :size="36" :src="user?.avatar" class="avatar">
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
        <el-dropdown-item command="usage">
          <el-icon><DataLine /></el-icon>
          使用统计
        </el-dropdown-item>
        <el-dropdown-item command="settings">
          <el-icon><Setting /></el-icon>
          设置
        </el-dropdown-item>
        <el-dropdown-item divided command="logout">
          <el-icon><SwitchButton /></el-icon>
          退出登录
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { ElMessageBox } from 'element-plus'

const authStore = useAuthStore()

const user = computed(() => authStore.user)

const handleCommand = async (command: string) => {
  switch (command) {
    case 'profile':
      // TODO: 跳转到个人中心
      console.log('个人中心')
      break
    case 'usage':
      // TODO: 跳转到使用统计
      console.log('使用统计')
      break
    case 'settings':
      // TODO: 跳转到设置
      console.log('设置')
      break
    case 'logout':
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      await authStore.logout()
      window.location.href = '/login'
      break
  }
}
</script>

<style scoped>
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 12px;
  border-radius: 8px;
  transition: background 0.2s;
}

.user-info:hover {
  background: rgba(0, 0, 0, 0.05);
}

.avatar {
  background: #409eff;
  color: white;
}

.username {
  font-size: 14px;
  color: #333;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.arrow {
  font-size: 12px;
  color: #999;
}
</style>
