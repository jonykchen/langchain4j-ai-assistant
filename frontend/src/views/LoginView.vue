<template>
  <div class="login-container">
    <div class="login-card">
      <!-- Logo 和标题 -->
      <div class="login-header">
        <div class="logo-wrapper">
          <el-icon :size="48" color="#409eff"><ChatDotRound /></el-icon>
        </div>
        <h1>AI Agent 平台</h1>
        <p class="subtitle">智能助手，随时为您服务</p>
      </div>

      <!-- 第三方登录 -->
      <div class="oauth-section">
        <el-button
          class="oauth-btn github"
          @click="handleOAuthLogin('github')"
          :loading="loading === 'github'"
        >
          <el-icon class="btn-icon"><Platform /></el-icon>
          <span>使用 GitHub 登录</span>
        </el-button>

        <el-button
          class="oauth-btn gitlab"
          @click="handleOAuthLogin('gitlab')"
          :loading="loading === 'gitlab'"
        >
          <el-icon class="btn-icon"><Platform /></el-icon>
          <span>使用 GitLab 登录</span>
        </el-button>
      </div>

      <!-- 提示信息 -->
      <div class="login-tips">
        <el-text type="info" size="small">
          登录即表示您同意我们的服务条款和隐私政策
        </el-text>
      </div>
    </div>

    <!-- 背景装饰 -->
    <div class="login-bg">
      <div class="bg-gradient"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const loading = ref<string | null>(null)

/**
 * 处理 OAuth 登录
 */
const handleOAuthLogin = async (provider: 'github' | 'gitlab') => {
  loading.value = provider

  try {
    await authStore.redirectToOAuth(provider)
  } catch (error: any) {
    console.error('OAuth 登录失败', error)
  } finally {
    loading.value = null
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.login-card {
  width: 400px;
  padding: 40px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.1);
  z-index: 1;
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.logo-wrapper {
  margin-bottom: 16px;
}

.login-header h1 {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 8px 0;
  color: #303133;
}

.subtitle {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.oauth-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.oauth-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.oauth-btn.github {
  background: #24292e;
  color: white;
  border: none;
}

.oauth-btn.github:hover {
  background: #1a1e22;
}

.oauth-btn.gitlab {
  background: #fc6d26;
  color: white;
  border: none;
}

.oauth-btn.gitlab:hover {
  background: #e85d1c;
}

.btn-icon {
  font-size: 18px;
}

.login-tips {
  text-align: center;
  margin-top: 24px;
}

.login-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.bg-gradient {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
</style>
