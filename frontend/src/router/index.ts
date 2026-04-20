/**
 * Vue Router 路由配置
 */
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

// 路由配置
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { requiresAuth: false, title: '登录' }
  },
  {
    path: '/auth/github/callback',
    name: 'GitHubCallback',
    component: () => import('@/views/OAuthCallbackView.vue'),
    meta: { requiresAuth: false, title: 'GitHub 登录' }
  },
  {
    path: '/auth/gitlab/callback',
    name: 'GitLabCallback',
    component: () => import('@/views/OAuthCallbackView.vue'),
    meta: { requiresAuth: false, title: 'GitLab 登录' }
  },
  {
    path: '/',
    name: 'Chat',
    component: () => import('@/views/ChatView.vue'),
    meta: { requiresAuth: true, title: 'AI 助手' }
  },
  {
    path: '/admin',
    component: () => import('@/views/admin/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: '',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/DashboardView.vue'),
        meta: { title: '管理后台' }
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/UsersView.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: 'cost',
        name: 'AdminCost',
        component: () => import('@/views/admin/CostView.vue'),
        meta: { title: '成本监控' }
      },
      {
        path: 'test',
        name: 'AdminTest',
        component: () => import('@/views/admin/TestDashboardView.vue'),
        meta: { title: '测试管理' }
      },
      {
        path: 'traces',
        name: 'AdminTraces',
        component: () => import('@/views/admin/AgentTraceView.vue'),
        meta: { title: 'Agent 追踪' }
      },
      {
        path: 'prompts',
        name: 'AdminPrompts',
        component: () => import('@/views/admin/PromptManagementView.vue'),
        meta: { title: 'Prompt 管理' }
      },
      {
        path: 'evaluation',
        name: 'AdminEvaluation',
        component: () => import('@/views/admin/EvaluationView.vue'),
        meta: { title: 'Agent 评测' }
      }
    ]
  }
]

// 创建路由实例
const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 路由守卫
 * 检查认证状态，未登录用户跳转到登录页
 */
router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()

  // 初始化认证状态（首次访问时从 localStorage 恢复）
  if (!authStore.token) {
    authStore.initAuth()
  }

  // 需要认证的页面
  const requiresAuth = to.meta.requiresAuth !== false

  // 公开页面
  const publicPages = ['/login', '/auth/github/callback', '/auth/gitlab/callback']
  const isPublicPage = publicPages.includes(to.path)

  // 如果是公开页面，直接放行
  if (isPublicPage) {
    next()
    return
  }

  // 检查认证状态
  if (!authStore.isAuthenticated) {
    // 未登录，跳转到登录页
    next({
      path: '/login',
      query: { redirect: to.fullPath }
    })
    return
  }

  // 检查管理员权限
  if (to.meta.requiresAdmin && authStore.user?.role !== 'ADMIN') {
    // 不是管理员，跳转到首页
    next({ path: '/' })
    return
  }

  // 已认证，放行
  next()
})

/**
 * 全局后置守卫
 * 设置页面标题
 */
router.afterEach((to) => {
  const title = to.meta.title as string
  document.title = title ? `${title} - AI Agent` : 'AI Agent'
})

export default router
