<script setup lang="ts">
/**
 * 全局应用根组件
 *
 * 提供统一的主题容器和全局样式作用域。
 */
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()
</script>

<template>
  <div class="app-root" :data-theme="themeStore.effectiveTheme">
    <router-view />
  </div>
</template>

<style scoped>
.app-root {
  height: 100%;
  background-color: var(--bg-secondary);
  color: var(--text-primary);
}
</style>
