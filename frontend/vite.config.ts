/**
 * Vite 配置文件
 *
 * Vite 是 Vue 3 官方推荐的构建工具：
 * - 开发服务器：极速热更新（HMR）
 * - 生产构建：基于 Rollup 的优化打包
 * - 原生 ES 模块：开发时无需打包
 *
 * 对比 Webpack：
 * - 开发启动更快（无需打包）
 * - 配置更简洁
 * - HMR 更快
 */

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  /**
   * 插件配置
   *
   * @vitejs/plugin-vue:
   * - 提供 Vue 3 单文件组件（.vue）支持
   * - 编译 <template>、<script>、<style>
   */
  plugins: [vue()],

  /**
   * 路径解析配置
   *
   * 别名设置：
   * - '@' → 'src' 目录
   * - 使用：import X from '@/components/X.vue'
   *
   * 好处：
   * - 避免相对路径地狱（../../../）
   * - 重构时路径不用改
   */
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },

  /**
   * 开发服务器配置
   */
  server: {
    /** 端口号，默认 5173 */
    port: 3000,

    /**
     * 代理配置
     *
     * 为什么需要代理？
     * - 前端运行在 localhost:3000
     * - 后端运行在 localhost:8082
     * - 浏览器同源策略阻止跨域请求
     *
     * 代理作用：
     * - /api/* 请求被转发到 localhost:8082
     * - 浏览器以为是同源请求，不会触发 CORS
     *
     * 注意：只用于开发环境
     * 生产环境需要在 Nginx 或后端配置 CORS
     */
    proxy: {
      '/api': {
        // 后端服务地址
        target: 'http://localhost:8082',
        // 修改请求头的 Origin 为目标地址
        // 后端看到的是 localhost:8082 而不是 localhost:3000
        changeOrigin: true,
        // 可选：重写路径
        // rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }

  /*
   * ==================== 更多配置选项 ====================
   *
   * build: {
   *   // 输出目录
   *   outDir: 'dist',
   *   // 静态资源目录名
   *   assetsDir: 'assets',
   *   // 代码分割策略
   *   rollupOptions: {
   *     output: {
   *       manualChunks: {
   *         vendor: ['vue', 'vue-router', 'pinia'],
   *         elementPlus: ['element-plus']
   *       }
   *     }
   *   },
   *   // 压缩配置
   *   minify: 'terser',
   *   terserOptions: {
   *     compress: { drop_console: true }
   *   }
   * },
   *
   * css: {
   *   // CSS 预处理器
   *   preprocessorOptions: {
   *     scss: { additionalData: `@import "@/styles/variables.scss";` }
   *   }
   * },
   *
   * // 环境变量
   * define: {
   *   __DEV__: JSON.stringify(process.env.NODE_ENV === 'development')
   * }
   */
})
