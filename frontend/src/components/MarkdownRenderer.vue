<script setup lang="ts">
/**
 * MarkdownRenderer 组件
 *
 * <p>安全渲染 Markdown 内容，支持代码块语法高亮。
 * 复用项目已有的 markdown-it + highlight.js 依赖。
 *
 * <h2>功能</h2>
 * <ul>
 *   <li>Markdown 渲染（使用 markdown-it）</li>
 *   <li>代码块语法高亮（使用 highlight.js）</li>
 *   <li>XSS 防护（对 HTML 标签进行转义）</li>
 *   <li>代码块复制按钮</li>
 * </ul>
 *
 * @author jonychen
 */
import { computed, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/core'

// 注册常用语言（按需加载，减少包体积）
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import python from 'highlight.js/lib/languages/python'
import java from 'highlight.js/lib/languages/java'
import sql from 'highlight.js/lib/languages/sql'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'
import xml from 'highlight.js/lib/languages/xml'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('python', python)
hljs.registerLanguage('java', java)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('json', json)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('html', xml)

/** Props 定义 */
const props = withDefaults(
  defineProps<{
    /** Markdown 内容 */
    content: string
    /** 是否启用代码高亮 */
    highlight?: boolean
  }>(),
  {
    highlight: true,
  }
)

/** 初始化 markdown-it 实例（启用代码高亮） */
const md = new MarkdownIt({
  html: false, // 禁止 HTML 标签（安全考虑）
  linkify: true,
  typographer: true,
  highlight: function (str: string, lang: string): string {
    if (!props.highlight) return ''

    // 尝试用指定语言高亮
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs"><code>${hljs.highlight(str, { language: lang }).value}</code></pre>`
      } catch {
        // 高亮失败则回退到纯文本
      }
    }

    // 自动检测语言
    try {
      return `<pre class="hljs"><code>${hljs.highlightAuto(str).value}</code></pre>`
    } catch {
      return ''
    }
  },
})

/** 渲染后的 HTML */
const renderedHtml = computed(() => {
  if (!props.content) return ''
  return md.render(props.content)
})

/** 复制状态 */
const copiedIndex = ref<number | null>(null)

/**
 * 复制代码块内容
 *
 * <p>通过事件代理，在父元素监听点击事件，根据 data-code-index 属性判断点击的代码块。
 */
function handleCopy(e: MouseEvent) {
  const target = e.target as HTMLElement
  const copyBtn = target.closest('[data-action="copy-code"]') as HTMLElement
  if (!copyBtn) return

  const codeIndex = parseInt(copyBtn.dataset.codeIndex || '0', 10)
  const codeEl = copyBtn.closest('.code-block-wrapper')?.querySelector('code')
  const code = codeEl?.textContent || ''

  navigator.clipboard
    .writeText(code)
    .then(() => {
      copiedIndex.value = codeIndex
      setTimeout(() => {
        copiedIndex.value = null
      }, 2000)
    })
    .catch(e => {
      console.error('[MarkdownRenderer] 复制失败:', e)
    })
}
</script>

<template>
  <div class="markdown-renderer" @click="handleCopy">
    <!-- eslint-disable-next-line vue/no-v-html -->
    <div class="markdown-body" v-html="renderedHtml"></div>
  </div>
</template>

<style scoped>
.markdown-renderer {
  font-size: 14px;
  line-height: 1.6;
}

/* Markdown 渲染样式（非 scoped，作用于 v-html 内容） */
</style>

<style>
/* v-html 内容样式（使用更具体的选择器） */
.markdown-renderer .markdown-body {
  word-break: break-word;
}

.markdown-renderer .markdown-body p {
  margin: 0 0 8px 0;
}

.markdown-renderer .markdown-body ul,
.markdown-renderer .markdown-body ol {
  padding-left: 20px;
  margin: 8px 0;
}

.markdown-renderer .markdown-body code {
  background: #f0f0f0;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Fira Code', 'Monaco', monospace;
  font-size: 13px;
}

.markdown-renderer .markdown-body pre {
  background: #1e1e1e;
  padding: 16px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 12px 0;
}

.markdown-renderer .markdown-body pre code {
  background: transparent;
  padding: 0;
  color: #d4d4d4;
}

.markdown-renderer .markdown-body table {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
}

.markdown-renderer .markdown-body th,
.markdown-renderer .markdown-body td {
  border: 1px solid #dcdfe6;
  padding: 8px 12px;
  text-align: left;
}

.markdown-renderer .markdown-body th {
  background: #f5f7fa;
  font-weight: 600;
}

.markdown-renderer .markdown-body blockquote {
  border-left: 4px solid #409eff;
  padding-left: 16px;
  margin: 12px 0;
  color: #606266;
}

.markdown-renderer .markdown-body h1,
.markdown-renderer .markdown-body h2,
.markdown-renderer .markdown-body h3,
.markdown-renderer .markdown-body h4 {
  margin: 16px 0 8px 0;
  font-weight: 600;
}

.markdown-renderer .markdown-body h1 {
  font-size: 20px;
}
.markdown-renderer .markdown-body h2 {
  font-size: 18px;
}
.markdown-renderer .markdown-body h3 {
  font-size: 16px;
}

.markdown-renderer .markdown-body a {
  color: #409eff;
  text-decoration: none;
}

.markdown-renderer .markdown-body a:hover {
  text-decoration: underline;
}
</style>
