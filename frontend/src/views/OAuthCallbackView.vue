<template>
  <div class="callback-container">
    <div class="callback-content">
      <el-icon v-if="!error" class="loading-icon" :size="48">
        <Loading />
      </el-icon>
      <el-icon v-else class="error-icon" :size="48">
        <CircleCloseFilled />
      </el-icon>
      <h2>{{ statusText }}</h2>
      <p v-if="error" class="error-message">{{ error }}</p>
      <el-button v-if="error" type="primary" @click="goToLogin" style="margin-top: 16px">
        返回登录
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const authStore = useAuthStore()

const statusText = ref('正在处理登录...')
const error = ref<string | null>(null)

onMounted(async () => {
  // 从 URL 路径解析 provider
  const path = route.path
  const providerMatch = path.match(/\/auth\/(github|gitlab)\/callback/)
  const provider = providerMatch ? (providerMatch[1] as 'github' | 'gitlab') : null

  if (!provider) {
    error.value = '无效的回调路径'
    statusText.value = '登录失败'
    return
  }

  const code = route.query.code as string
  const state = route.query.state as string

  if (!code) {
    error.value = '缺少授权码'
    statusText.value = '登录失败'
    return
  }

  // 验证 state 防止 CSRF
  const savedState = sessionStorage.getItem('oauth_state')
  if (state !== savedState) {
    error.value = '安全验证失败，请重新登录'
    statusText.value = '登录失败'
    return
  }

  try {
    statusText.value = '正在获取用户信息...'

    await authStore.handleOAuthCallback(provider, code, state)

    statusText.value = '登录成功！'

    // 清理临时数据
    sessionStorage.removeItem('oauth_state')
    sessionStorage.removeItem('oauth_provider')
    sessionStorage.removeItem('oauth_redirect_uri')

    // 跳转到首页
    setTimeout(() => {
      window.location.href = '/'
    }, 500)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '登录失败，请重试'
    statusText.value = '登录失败'
  }
})

const goToLogin = () => {
  window.location.href = '/login'
}
</script>

<style scoped>
.callback-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.callback-content {
  text-align: center;
  background: var(--bg-primary);
  padding: 48px;
  border-radius: 16px;
  box-shadow: var(--shadow-lg);
}

.loading-icon {
  animation: spin 1s linear infinite;
  color: var(--color-primary);
}

.error-icon {
  color: var(--color-danger);
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.callback-content h2 {
  margin: 16px 0 8px;
  color: var(--text-primary);
}

.error-message {
  color: var(--color-danger);
  margin: 0;
}
</style>
