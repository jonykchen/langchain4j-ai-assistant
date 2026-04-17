<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const menuItems = [
  { path: '/admin', icon: 'DataBoard', title: '仪表盘' },
  { path: '/admin/users', icon: 'User', title: '用户管理' },
  { path: '/admin/cost', icon: 'Money', title: '成本监控' }
]

const handleSelect = (path: string) => {
  router.push(path)
}

const goBack = () => {
  router.push('/')
}
</script>

<template>
  <div class="admin-layout">
    <aside class="sidebar">
      <div class="sidebar-header">
        <h3>管理后台</h3>
      </div>
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
      <div class="sidebar-footer">
        <el-button type="primary" link @click="goBack">
          <el-icon><Back /></el-icon>
          返回前台
        </el-button>
      </div>
    </aside>
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: #f5f7fa;
}

.sidebar {
  width: 220px;
  background: #fff;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid #eee;
}

.sidebar-header h3 {
  margin: 0;
  font-size: 18px;
  color: #303133;
}

.sidebar-menu {
  flex: 1;
  border-right: none;
}

.sidebar-footer {
  padding: 20px;
  border-top: 1px solid #eee;
}

.main-content {
  flex: 1;
  overflow: auto;
}
</style>
