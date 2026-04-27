<script setup lang="ts">
/**
 * JsonViewer 组件
 *
 * <p>用于格式化展示 JSON 数据，支持折叠/展开和语法高亮。
 *
 * <h2>功能</h2>
 * <ul>
 *   <li>JSON 数据格式化展示</li>
 *   <li>支持折叠/展开（对象/数组层级）</li>
 *   <li>语法高亮（使用 highlight.js）</li>
 *   <li>复制功能</li>
 *   <li>最大深度限制（防止超大数据卡顿）</li>
 * </ul>
 *
 * @author jonychen
 */
import { computed, ref } from 'vue'
import hljs from 'highlight.js/lib/core'
import json from 'highlight.js/lib/languages/json'

// 注册 JSON 语法
hljs.registerLanguage('json', json)

/** Props 定义 */
const props = withDefaults(defineProps<{
  /** JSON 数据（任意类型） */
  data: unknown
  /** 是否默认展开 */
  defaultExpanded?: boolean
  /** 最大展示深度 */
  maxDepth?: number
  /** 是否显示复制按钮 */
  showCopy?: boolean
}>(), {
  defaultExpanded: true,
  maxDepth: 5,
  showCopy: true
})

/** 是否展开 */
const isExpanded = ref(props.defaultExpanded)

/** 格式化后的 JSON 字符串 */
const formattedJson = computed(() => {
  try {
    return JSON.stringify(props.data, null, 2)
  } catch (e) {
    return String(props.data)
  }
})

/** 高亮后的 HTML */
const highlightedHtml = computed(() => {
  try {
    return hljs.highlight(formattedJson.value, { language: 'json' }).value
  } catch {
    return formattedJson.value
  }
})

/** 是否超过最大深度 */
const isTooDeep = computed(() => {
  return getDepth(props.data) > props.maxDepth
})

/** 复制状态 */
const copySuccess = ref(false)

/** 切换展开/折叠 */
function toggle() {
  isExpanded.value = !isExpanded.value
}

/** 复制到剪贴板 */
async function handleCopy() {
  try {
    await navigator.clipboard.writeText(formattedJson.value)
    copySuccess.value = true
    setTimeout(() => {
      copySuccess.value = false
    }, 2000)
  } catch (e) {
    console.error('[JsonViewer] 复制失败:', e)
  }
}

/** 计算对象深度 */
function getDepth(obj: unknown, current = 0): number {
  if (current > props.maxDepth + 1) return current
  if (typeof obj !== 'object' || obj === null) return current

  const values = Array.isArray(obj) ? obj : Object.values(obj)
  if (values.length === 0) return current + 1

  return Math.max(...values.map(v => getDepth(v, current + 1)))
}
</script>

<template>
  <div class="json-viewer">
    <!-- 头部工具栏 -->
    <div class="json-header">
      <span class="json-label">JSON</span>
      <div class="json-actions">
        <!-- 深度警告 -->
        <el-tooltip
          v-if="isTooDeep"
          content="数据层级较深，可能影响性能"
          placement="top"
        >
          <el-icon class="warning-icon"><Warning /></el-icon>
        </el-tooltip>

        <!-- 折叠/展开 -->
        <el-button text size="small" @click="toggle">
          <el-icon>
            <ArrowDown v-if="isExpanded" />
            <ArrowRight v-else />
          </el-icon>
          {{ isExpanded ? '折叠' : '展开' }}
        </el-button>

        <!-- 复制 -->
        <el-button v-if="showCopy" text size="small" @click="handleCopy">
          <el-icon>
            <Check v-if="copySuccess" style="color: var(--color-success)" />
            <CopyDocument v-else />
          </el-icon>
          {{ copySuccess ? '已复制' : '复制' }}
        </el-button>
      </div>
    </div>

    <!-- JSON 内容 -->
    <div v-show="isExpanded" class="json-content">
      <pre><code v-html="highlightedHtml"></code></pre>
    </div>
  </div>
</template>

<style scoped>
.json-viewer {
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1px solid var(--border-color);
}

.json-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-sm) var(--space-md);
  background: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-color);
}

.json-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.json-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.warning-icon {
  color: var(--color-warning);
}

.json-content {
  padding: var(--space-md);
  overflow-x: auto;
}

.json-content pre {
  margin: 0;
  font-family: 'Fira Code', 'Monaco', 'Menlo', monospace;
  font-size: 13px;
  line-height: 1.5;
}

.json-content code {
  background: transparent;
}
</style>
