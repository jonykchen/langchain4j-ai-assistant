<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessageBox } from 'element-plus'
import {
  Back,
  SwitchButton,
  Fold,
  Expand,
  ArrowDown,
  DataBoard,
  User,
  Money,
  Aim,
  DocumentChecked,
  Checked,
  Timer,
  View,
  Document,
  DataAnalysis,
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const user = computed(() => authStore.user)

const sidebarCollapsed = ref(false)

const menuItems = [
  // 概览与基础管理
  { path: '/admin', icon: DataBoard, title: '仪表盘' },
  { path: '/admin/users', icon: User, title: '用户管理' },
  { path: '/admin/cost', icon: Money, title: '成本监控' },
  // Agent 功能链（规划 → 追踪 → 评测 → 审计 → Prompt）
  { path: '/admin/planning', icon: Aim, title: '任务规划' },
  { path: '/admin/traces', icon: View, title: 'Agent 追踪' },
  { path: '/admin/evaluation', icon: DataAnalysis, title: 'Agent 评测' },
  { path: '/admin/agent-audit', icon: DocumentChecked, title: 'Agent 审计' },
  { path: '/admin/prompts', icon: Document, title: 'Prompt 管理' },
  // 测试体系
  { path: '/admin/test', icon: Checked, title: '测试管理' },
  { path: '/admin/test-history', icon: Timer, title: '测试历史' },
]

// 面包屑导航
const breadcrumbs = computed(() => {
  const crumbs = [{ title: '首页', path: '/' }]
  route.matched.forEach(r => {
    if (r.meta?.title) {
      crumbs.push({ title: r.meta.title as string, path: r.path })
    }
  })
  return crumbs
})

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
      type: 'warning',
    })
    await authStore.logout()
    router.push('/login')
  } catch {
    // 用户取消
  }
}

onMounted(() => {
  if (window.innerWidth < 768) {
    sidebarCollapsed.value = true
  }
})
</script>

<template>
  <div class="admin-layout">
    <!-- 顶部导航栏 -->
    <header class="admin-header">
      <div class="header-left">
        <el-button text class="collapse-btn" @click="sidebarCollapsed = !sidebarCollapsed">
          <el-icon><component :is="sidebarCollapsed ? Expand : Fold" /></el-icon>
        </el-button>
        <h1 class="header-title">管理后台</h1>
      </div>
      <div class="header-right">
        <el-button text @click="goBack" class="back-btn">
          <el-icon><Back /></el-icon>
          <span class="hide-on-mobile">返回前台</span>
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
      <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
        <el-menu
          :default-active="route.path"
          :collapse="sidebarCollapsed"
          :collapse-transition="true"
          class="sidebar-menu"
          @select="handleSelect"
        >
          <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>
              <span>{{ item.title }}</span>
            </template>
          </el-menu-item>
        </el-menu>
      </aside>

      <!-- 主内容区 -->
      <main class="main-content">
        <!-- 面包屑 -->
        <div class="breadcrumb-bar">
          <el-breadcrumb>
            <el-breadcrumb-item v-for="(crumb, index) in breadcrumbs" :key="crumb.path">
              <router-link
                v-if="index < breadcrumbs.length - 1"
                :to="crumb.path"
                class="breadcrumb-link"
              >
                {{ crumb.title }}
              </router-link>
              <span v-else class="breadcrumb-current">{{ crumb.title }}</span>
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
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
  background: var(--bg-secondary);
}

/* 顶部导航栏 */
.admin-header {
  height: var(--header-height);
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-xl);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.collapse-btn {
  color: var(--text-secondary);
}

.header-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.back-btn {
  color: var(--text-secondary);
}

.back-btn:hover {
  color: var(--color-primary);
}

.user-dropdown {
  margin-left: var(--space-sm);
}

.user-info {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  cursor: pointer;
  padding: 6px var(--space-md);
  border-radius: var(--radius-md);
  transition: background 0.2s;
}

.user-info:hover {
  background: var(--bg-secondary);
}

.avatar {
  background: var(--color-primary);
  color: white;
  font-size: 14px;
}

.username {
  font-size: 14px;
  color: var(--text-primary);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.arrow {
  font-size: 12px;
  color: var(--text-tertiary);
}

/* 主体区域 */
.admin-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.sidebar {
  width: var(--sidebar-width-sm);
  background: var(--bg-primary);
  border-right: 1px solid var(--border-color);
  flex-shrink: 0;
  transition: width 0.3s ease;
}

.sidebar.collapsed {
  width: 64px;
}

.sidebar-menu {
  border-right: none;
  height: 100%;
}

.main-content {
  flex: 1;
  overflow: auto;
  padding: var(--page-padding);
  background: var(--bg-secondary);
}

.breadcrumb-bar {
  margin-bottom: var(--card-spacing);
}

.breadcrumb-link {
  color: var(--color-primary);
  text-decoration: none;
}

.breadcrumb-link:hover {
  text-decoration: underline;
}

.breadcrumb-current {
  color: var(--text-secondary);
}

.breadcrumb-link {
  color: var(--color-primary);
  text-decoration: none;
}

.breadcrumb-link:hover {
  text-decoration: underline;
}

.breadcrumb-current {
  color: var(--text-secondary);
}

/* 移动端适配 */
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: var(--header-height);
    bottom: 0;
    z-index: var(--z-drawer);
    box-shadow: var(--shadow-lg);
  }

  .sidebar.collapsed {
    transform: translateX(-100%);
    width: var(--sidebar-width-sm);
  }

  .hide-on-mobile {
    display: none;
  }

  .breadcrumb-bar {
    padding: var(--space-md) var(--space-lg);
  }
}
</style>
