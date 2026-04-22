<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessageBox } from 'element-plus'
import { Back, SwitchButton } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const user = computed(() => authStore.user)

const menuItems = [
  { path: '/admin', icon: 'DataBoard', title: '仪表盘' },
  { path: '/admin/users', icon: 'User', title: '用户管理' },
  { path: '/admin/cost', icon: 'Money', title: '成本监控' },
  { path: '/admin/planning', icon: 'Aim', title: '任务规划' },
  { path: '/admin/test', icon: 'Checked', title: '测试管理' },
  { path: '/admin/test-history', icon: 'Timer', title: '测试历史' },
  { path: '/admin/traces', icon: 'View', title: 'Agent 追踪' },
  { path: '/admin/prompts', icon: 'Document', title: 'Prompt 管理' },
  { path: '/admin/evaluation', icon: 'DataAnalysis', title: 'Agent 评测' }
]

const handleSelect = (path: string) => {
  router.push(path)
}

const goBack = () => {
  router.push('/')
}

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await authStore.logout()
    router.push('/login')
  } catch {
    // 用户取消
  }
}
</script>

<template>
  <div class="admin-layout">
    <!-- 顶部导航栏 -->
    <header class="admin-header">
      <div class="header-left">
        <h1 class="header-title">管理后台</h1>
      </div>
      <div class="header-right">
        <el-button text @click="goBack" class="back-btn">
          <el-icon><Back /></el-icon>
          返回前台
        </el-button>
        <el-divider direction="vertical" />
        <div class="user-dropdown">
          <el-dropdown trigger="click">
            <div class="user-info">
              <el-avatar :size="32" :src="user?.avatar" class="avatar">
                {{ user?.username?.charAt(0).toUpperCase() }}
              </el-avatar>
              <span class="username">{{ user?.nickname || user?.username }}</span>
              <el-icon class="arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="goBack">
                  <el-icon><Back /></el-icon>
                  返回前台
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <div class="admin-body">
      <!-- 侧边栏 -->
      <aside class="sidebar">
        <el-menu
          :default-active="route.path"
          class="sidebar-menu"
          @select="handleSelect"
        >
          <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </el-menu>
      </aside>

      <!-- 主内容区 -->
      <main class="main-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #f5f7fa;
}

/* 顶部导航栏 */
.admin-header {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.back-btn {
  color: #606266;
}

.back-btn:hover {
  color: #409eff;
}

.user-dropdown {
  margin-left: 8px;
}

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
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.arrow {
  font-size: 12px;
  color: #9ca3af;
}

/* 主体区域 */
.admin-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.sidebar {
  width: 200px;
  background: #fff;
  border-right: 1px solid #e5e7eb;
  flex-shrink: 0;
}

.sidebar-menu {
  border-right: none;
  height: 100%;
}

.main-content {
  flex: 1;
  overflow: auto;
  padding: 0;
}
</style>
