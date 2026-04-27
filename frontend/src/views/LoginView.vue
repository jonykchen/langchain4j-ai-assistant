<template>
  <div class="login-container">
    <!-- 左侧品牌区域 -->
    <div class="login-brand">
      <div class="brand-content">
        <!-- Logo -->
        <div class="brand-logo">
          <el-icon :size="64" color="#fff"><ChatDotRound /></el-icon>
        </div>

        <!-- 品牌标语 -->
        <h1 class="brand-title">AI Agent 平台</h1>
        <p class="brand-subtitle">新一代智能对话助手，让工作更高效</p>

        <!-- 特性列表 -->
        <div class="brand-features">
          <div class="feature-item">
            <el-icon :size="20"><CircleCheckFilled /></el-icon>
            <span>多模型负载均衡，智能故障转移</span>
          </div>
          <div class="feature-item">
            <el-icon :size="20"><CircleCheckFilled /></el-icon>
            <span>安全可靠的对话数据加密存储</span>
          </div>
          <div class="feature-item">
            <el-icon :size="20"><CircleCheckFilled /></el-icon>
            <span>支持多种 AI 模型灵活切换</span>
          </div>
        </div>
      </div>

      <!-- 装饰元素 -->
      <div class="brand-decoration">
        <div class="decoration-circle circle-1"></div>
        <div class="decoration-circle circle-2"></div>
        <div class="decoration-circle circle-3"></div>
      </div>
    </div>

    <!-- 右侧登录区域 -->
    <div class="login-form-section">
      <div class="login-form-wrapper">
        <!-- 表单头部 -->
        <div class="form-header">
          <h2 class="form-title">欢迎回来</h2>
          <p class="form-subtitle">请登录您的账户继续使用</p>
        </div>

        <!-- 登录表单 -->
        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          class="login-form"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              placeholder="用户名"
              :prefix-icon="User"
              size="large"
              clearable
              data-testid="username-input"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="密码"
              :prefix-icon="Lock"
              size="large"
              show-password
              data-testid="password-input"
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <!-- 记住我 & 忘记密码 -->
          <div class="form-options">
            <el-checkbox v-model="rememberMe">记住我</el-checkbox>
            <el-link type="primary" underline="never" class="forgot-link">
              忘记密码？
            </el-link>
          </div>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              class="login-btn"
              :loading="isLoggingIn"
              data-testid="login-button"
              @click="handleLogin"
            >
              {{ isLoggingIn ? '登录中...' : '登 录' }}
            </el-button>
          </el-form-item>
        </el-form>

        <!-- 分割线 -->
        <div class="divider">
          <span class="divider-text">其他登录方式</span>
        </div>

        <!-- 第三方登录 -->
        <div class="oauth-buttons">
          <el-tooltip content="使用 GitHub 登录" placement="top">
            <button
              type="button"
              class="oauth-btn github"
              :disabled="loading !== null"
              data-testid="github-login"
              aria-label="使用 GitHub 登录"
              @click="handleOAuthLogin('github')"
            >
              <svg class="oauth-icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
              </svg>
            </button>
          </el-tooltip>

          <el-tooltip content="使用 GitLab 登录" placement="top">
            <button
              type="button"
              class="oauth-btn gitlab"
              :disabled="loading !== null"
              data-testid="gitlab-login"
              aria-label="使用 GitLab 登录"
              @click="handleOAuthLogin('gitlab')"
            >
              <svg class="oauth-icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M22.65 14.39L12 22.13 1.35 14.39a.84.84 0 0 1-.3-.94l1.22-3.78 2.44-7.51A.42.42 0 0 1 4.82 2a.43.43 0 0 1 .58 0 .42.42 0 0 1 .11.18l2.44 7.49h8.1l2.44-7.51A.42.42 0 0 1 18.6 2a.43.43 0 0 1 .58 0 .42.42 0 0 1 .11.18l2.44 7.51L22.94 13.45a.84.84 0 0 1-.29.94z"/>
              </svg>
            </button>
          </el-tooltip>
        </div>

        <!-- 底部链接 -->
        <div class="form-footer">
          <span class="footer-text">还没有账户？</span>
          <el-link type="primary" underline="never" class="contact-link" @click="showContactDialog = true">联系管理员</el-link>
        </div>
      </div>

      <!-- 版权信息 -->
      <div class="copyright">
        <span>© 2024 AI Agent Platform. All rights reserved.</span>
      </div>
    </div>

    <!-- 联系管理员对话框 -->
    <el-dialog
      v-model="showContactDialog"
      title="联系管理员"
      width="400px"
      :close-on-click-modal="true"
    >
      <div class="contact-content">
        <div class="contact-item">
          <el-icon :size="20" color="#409eff"><Message /></el-icon>
          <span>邮箱：admin@example.com</span>
        </div>
        <div class="contact-item">
          <el-icon :size="20" color="#409eff"><Phone /></el-icon>
          <span>电话：400-xxx-xxxx</span>
        </div>
        <el-divider />
        <p class="contact-tip">工作时间：周一至周五 9:00-18:00</p>
      </div>
      <template #footer>
        <el-button type="primary" @click="showContactDialog = false">我知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, CircleCheckFilled, Message, Phone, ChatDotRound } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()

const loginFormRef = ref<FormInstance>()
const loading = ref<string | null>(null)
const isLoggingIn = ref(false)
const rememberMe = ref(false)
const showContactDialog = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为 3-20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6-32 个字符', trigger: 'blur' }
  ]
}

/**
 * 处理用户名密码登录
 */
const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return

    isLoggingIn.value = true
    try {
      await authStore.login(loginForm.username, loginForm.password)
      ElMessage.success('登录成功')
      router.push('/')
    } catch (error) {
      const errorMsg = error instanceof Error ? error.message : '登录失败，请检查用户名和密码'
      ElMessage.error(errorMsg)
    } finally {
      isLoggingIn.value = false
    }
  })
}

/**
 * 处理 OAuth 登录
 */
const handleOAuthLogin = async (provider: 'github' | 'gitlab') => {
  loading.value = provider

  try {
    await authStore.redirectToOAuth(provider)
  } catch {
    ElMessage.error(`${provider === 'github' ? 'GitHub' : 'GitLab'} 登录失败`)
  } finally {
    loading.value = null
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  background: var(--bg-secondary);
}

/* ===== 左侧品牌区域 ===== */
.login-brand {
  flex: 1;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  padding: 48px;
}

.brand-content {
  position: relative;
  z-index: 2;
  text-align: center;
  color: #fff;
  max-width: 420px;
}

.brand-logo {
  width: 96px;
  height: 96px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 24px;
  backdrop-filter: blur(10px);
}

.brand-title {
  font-size: 32px;
  font-weight: 700;
  margin: 0 0 12px;
  letter-spacing: -0.5px;
}

.brand-subtitle {
  font-size: 16px;
  opacity: 0.9;
  margin: 0 0 40px;
  line-height: 1.6;
}

.brand-features {
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  opacity: 0.95;
}

.feature-item .el-icon {
  opacity: 0.9;
}

/* 装饰圆圈 */
.brand-decoration {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.decoration-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
}

.circle-1 {
  width: 400px;
  height: 400px;
  top: -100px;
  left: -100px;
}

.circle-2 {
  width: 300px;
  height: 300px;
  bottom: -50px;
  right: -50px;
}

.circle-3 {
  width: 150px;
  height: 150px;
  top: 50%;
  right: 20%;
}

/* ===== 右侧登录区域 ===== */
.login-form-section {
  width: 480px;
  min-width: 480px;
  display: flex;
  flex-direction: column;
  background: var(--bg-primary);
  position: relative;
}

.login-form-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 60px 64px;
}

.form-header {
  margin-bottom: 40px;
}

.form-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 8px;
}

.form-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}

/* ===== 表单样式 ===== */
.login-form {
  margin-bottom: 24px;
}

.login-form :deep(.el-input__wrapper) {
  padding: 4px 15px;
  border-radius: var(--radius-md);
}

.login-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.forgot-link {
  font-size: 13px;
  color: var(--color-primary);
}

/* 覆盖 Element Plus 默认颜色以提高对比度 */
.form-options :deep(.el-link--primary) {
  color: var(--color-primary);
}

.contact-link {
  color: var(--color-primary);
}

.form-footer :deep(.el-link--primary) {
  color: var(--color-primary);
}

/* 登录按钮 */
.login-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 500;
  border-radius: var(--radius-md);
}

/* ===== 分割线 ===== */
.divider {
  display: flex;
  align-items: center;
  margin: 32px 0;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--border-color);
}

.divider-text {
  padding: 0 16px;
  font-size: 13px;
  color: var(--text-tertiary);
}

/* ===== OAuth 按钮 ===== */
.oauth-buttons {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-bottom: 32px;
}

.oauth-btn {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--bg-primary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.oauth-btn:hover {
  border-color: var(--border-hover);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.oauth-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.oauth-btn.github:hover {
  border-color: #24292e;
  background: #f6f8fa;
}

.oauth-btn.gitlab:hover {
  border-color: #fc6d26;
  background: #fff4eb;
}

.oauth-icon {
  width: 24px;
  height: 24px;
}

.oauth-btn.github .oauth-icon {
  color: #24292e;
}

.oauth-btn.gitlab .oauth-icon {
  color: #fc6d26;
}

/* ===== 底部 ===== */
.form-footer {
  text-align: center;
  font-size: 14px;
}

.footer-text {
  color: var(--text-tertiary);
  margin-right: 4px;
}

.copyright {
  padding: 20px;
  text-align: center;
  font-size: 12px;
  color: var(--text-tertiary);
  border-top: 1px solid var(--border-color);
}

/* ===== 联系管理员对话框 ===== */
.contact-content {
  padding: 8px 0;
}

.contact-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: var(--text-primary);
  padding: 12px 0;
}

.contact-tip {
  font-size: 13px;
  color: var(--text-tertiary);
  margin: 0;
}

/* ===== 响应式 ===== */
@media (max-width: 1024px) {
  .login-brand {
    display: none;
  }

  .login-form-section {
    width: 100%;
    min-width: auto;
  }
}

@media (max-width: 768px) {
  .login-form-wrapper {
    padding: 48px 40px;
  }

  .form-title {
    font-size: 26px;
  }
}

@media (max-width: 480px) {
  .login-form-wrapper {
    padding: 40px 24px;
  }

  .form-title {
    font-size: 24px;
  }
}
</style>
