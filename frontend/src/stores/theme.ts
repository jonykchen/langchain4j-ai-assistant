/**
 * 主题状态管理
 * 支持亮色/暗色模式切换，持久化到 localStorage
 */
import { defineStore } from 'pinia'
import { ref, watch, computed } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'auto'

export const useThemeStore = defineStore('theme', () => {
  const stored = localStorage.getItem('theme_mode') as ThemeMode | null
  const theme = ref<ThemeMode>(stored || 'auto')

  /** 当前实际生效的主题（auto 时根据系统偏好） */
  const effectiveTheme = ref<'light' | 'dark'>('light')

  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')

  function resolveEffective(): 'light' | 'dark' {
    if (theme.value === 'auto') {
      return mediaQuery.matches ? 'dark' : 'light'
    }
    return theme.value
  }

  function apply() {
    effectiveTheme.value = resolveEffective()
    const root = document.documentElement
    if (effectiveTheme.value === 'dark') {
      root.setAttribute('data-theme', 'dark')
    } else {
      root.removeAttribute('data-theme')
    }
  }

  function setMode(mode: ThemeMode) {
    theme.value = mode
    localStorage.setItem('theme_mode', mode)
    apply()
  }

  function toggle() {
    setMode(effectiveTheme.value === 'dark' ? 'light' : 'dark')
  }

  // 初始化
  apply()

  // 监听系统主题变化
  mediaQuery.addEventListener('change', () => {
    if (theme.value === 'auto') apply()
  })

  // 监听 store 变化（跨 Tab 同步可选）
  watch(theme, apply)

  /** 是否为暗色模式 */
  const isDark = computed(() => effectiveTheme.value === 'dark')

  return {
    theme,
    effectiveTheme,
    isDark,
    setMode,
    toggle,
    apply,
  }
})
