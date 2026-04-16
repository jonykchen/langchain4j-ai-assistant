<script setup lang="ts">
/**
 * MessageItem.vue - 消息渲染组件
 *
 * 功能说明：
 * 1. 渲染用户消息和 AI 助手消息
 * 2. 支持 Markdown 渲染（标题、列表、代码块、表格等）
 * 3. 代码块支持：语法高亮、复制、编辑、主题切换、折叠
 * 4. 思考过程展示（解析 <thinking> 标签）
 * 5. 消息操作：复制、点赞/点踩、重新生成
 *
 * 技术要点：
 * - markdown-it: Markdown 解析器
 * - highlight.js: 代码语法高亮
 * - 事件代理: 处理动态生成的代码块按钮点击
 * - v-html: 渲染 HTML 内容（注意 XSS 风险，已禁用原始 HTML）
 */

import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
// 导入 highlight.js 的主题样式
// 可选主题：github-dark, atom-one-dark, monokai, nord 等
// 查看: https://highlightjs.org/static/demo/
import 'highlight.js/styles/github-dark.css'
import type { Message } from '@/types'

// ==================== Props & Emits ====================

/**
 * 组件属性定义
 * - message: 消息对象，包含角色、内容、流式状态等
 */
const props = defineProps<{
  message: Message
}>()

/**
 * 组件事件定义
 * - regenerate: 用户点击重新生成按钮时触发
 */
const emit = defineEmits<{
  (e: 'regenerate'): void
}>()

// ==================== Markdown 配置 ====================

/**
 * HTML 转义函数
 *
 * 为什么需要这个函数？
 * - markdown-it 的 utils.escapeHtml 在 highlight 回调中会导致循环引用
 * - 手动实现可以避免这个问题
 *
 * 转义规则：
 * & → &amp;  (必须第一个处理，否则会重复转义)
 * < → &lt;
 * > → &gt;
 * " → &quot;
 * ' → &#039;
 */
function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

/**
 * MarkdownIt 实例配置
 *
 * 配置项说明：
 * - html: false        禁止原始 HTML（安全考虑，防止 XSS）
 * - breaks: true       将换行符转换为 <br>（GitHub 风格）
 * - linkify: true      自动识别 URL 并转为链接
 * - typographer: true  启用智能标点（如 -- → —）
 *
 * highlight 回调：
 * - 用于代码块语法高亮
 * - 返回完整的 HTML 字符串（包含代码块头部工具栏）
 */
const md = new MarkdownIt({
  html: false,       // 安全：禁用原始 HTML 标签
  breaks: true,      // GitHub 风格：单个换行符转为 <br>
  linkify: true,     // 自动链接：识别 URL 并转为可点击链接
  typographer: true, // 智能标点：-- → —，引号美化等
  highlight: (str: string, lang: string): string => {
    const language = lang || ''
    let highlighted: string

    // 使用 highlight.js 进行语法高亮
    if (language && hljs.getLanguage(language)) {
      try {
        // ignoreIllegals: true 忽略非法语法，避免高亮失败
        highlighted = hljs.highlight(str, { language, ignoreIllegals: true }).value
      } catch {
        highlighted = escapeHtml(str)
      }
    } else {
      // 没有指定语言或不支持的语言，直接转义
      highlighted = escapeHtml(str)
    }

    // 构建代码块 HTML
    // data-lang: 语言标签，用于显示和编辑
    // data-raw: 原始代码（转义后），用于复制和编辑
    const langLabel = language || 'text'
    const rawData = escapeHtml(str)

    // 返回完整的代码块 HTML
    // 包含：语言标签 + 操作按钮（复制/编辑/主题/折叠）+ 代码内容
    return `<pre class="code-block-wrapper" data-lang="${langLabel}" data-raw="${rawData}">
      <div class="code-block-header">
        <span class="code-lang">${langLabel}</span>
        <div class="code-actions">
          <button class="code-action-btn" data-action="copy" title="复制代码">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="14" height="14" x="8" y="8" rx="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg>复制
          </button>
          <button class="code-action-btn" data-action="edit" title="编辑代码">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/><path d="m15 5 4 4"/></svg>编辑
          </button>
          <button class="code-action-btn" data-action="theme" title="切换浅色/深色模式">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="4"/><path d="M12 2v2"/><path d="M12 20v2"/><path d="m4.93 4.93 1.41 1.41"/><path d="m17.66 17.66 1.41 1.41"/><path d="M2 12h2"/><path d="M20 12h2"/><path d="m6.34 17.66-1.41 1.41"/><path d="m19.07 4.93-1.41 1.41"/></svg>浅色
          </button>
          <button class="code-action-btn" data-action="fold" title="折叠/展开代码">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m7 15 5 5 5-5"/><path d="m7 9 5-5 5 5"/></svg>折叠
          </button>
        </div>
      </div>
      <pre class="hljs code-content"><code class="hljs ${language ? `language-${language}` : ''}">${highlighted}</code></pre>
    </pre>`
  },
})

// ==================== 代码编辑功能 ====================

/** 编辑弹窗是否可见 */
const editDialogVisible = ref(false)
/** 编辑器中的代码内容 */
const editCodeContent = ref('')
/** 当前编辑的代码语言 */
const editCodeLang = ref('')
/** 当前编辑的代码块 DOM 引用 */
const editWrapperRef = ref<HTMLElement | null>(null)

/**
 * 打开代码编辑弹窗
 * @param wrapper 代码块容器元素
 */
function openEditDialog(wrapper: HTMLElement) {
  // 从 data-raw 属性读取原始代码（已转义）
  const rawAttr = wrapper.dataset.raw
  // 解码 HTML 实体，还原原始代码
  editCodeContent.value = rawAttr ? decodeHTMLEntities(rawAttr) : ''
  editCodeLang.value = wrapper.dataset.lang || 'text'
  editWrapperRef.value = wrapper
  editDialogVisible.value = true
}

/**
 * 保存编辑后的代码
 * 重新进行语法高亮并更新 DOM
 */
function saveEditedCode() {
  if (!editWrapperRef.value) return
  const wrapper = editWrapperRef.value
  const codeEl = wrapper.querySelector('code.hljs')
  if (!codeEl) return

  const lang = editCodeLang.value
  let highlighted: string

  // 重新进行语法高亮
  if (lang && lang !== 'text' && hljs.getLanguage(lang)) {
    try {
      highlighted = hljs.highlight(editCodeContent.value, { language: lang, ignoreIllegals: true }).value
    } catch {
      highlighted = escapeHtml(editCodeContent.value)
    }
  } else {
    highlighted = escapeHtml(editCodeContent.value)
  }

  // 更新 DOM
  codeEl.innerHTML = highlighted
  wrapper.dataset.raw = escapeHtml(editCodeContent.value)
  editDialogVisible.value = false
  ElMessage.success('代码已更新')
}

/**
 * 解码 HTML 实体
 * 例如：&lt; → <, &gt; → >, &amp; → &
 *
 * 原理：利用 textarea 的 innerHTML 自动解码特性
 */
function decodeHTMLEntities(text: string): string {
  const textarea = document.createElement('textarea')
  textarea.innerHTML = text
  return textarea.value
}

// ==================== 代码块交互（事件代理） ====================

/**
 * 代码块操作事件处理
 *
 * 为什么使用事件代理？
 * - 代码块是动态生成的（v-html）
 * - 无法直接在模板中绑定事件
 * - 在父元素上监听，通过 event.target 判断点击源
 *
 * @param event 点击事件
 */
function handleCodeAction(event: Event) {
  // 查找最近的 .code-action-btn 按钮
  const target = (event.target as HTMLElement)?.closest('.code-action-btn') as HTMLElement | null
  if (!target) return

  // 获取操作类型
  const action = target.dataset.action
  // 查找代码块容器
  const wrapper = target.closest('.code-block-wrapper') as HTMLElement | null
  if (!wrapper || !action) return

  // 根据操作类型分发处理
  switch (action) {
    case 'copy':
      handleCodeCopy(wrapper, target)
      break
    case 'edit':
      openEditDialog(wrapper)
      break
    case 'theme':
      handleCodeTheme(wrapper, target)
      break
    case 'fold':
      handleCodeFold(wrapper, target)
      break
  }
}

/**
 * 复制代码到剪贴板
 *
 * 使用 Clipboard API：
 * - navigator.clipboard.writeText()
 * - 仅在安全上下文（HTTPS）中可用
 */
async function handleCodeCopy(wrapper: HTMLElement, btn: HTMLElement) {
  const codeEl = wrapper.querySelector('code.hljs')
  if (!codeEl?.textContent) return

  try {
    await navigator.clipboard.writeText(codeEl.textContent)
    // 显示复制成功状态
    const originalHTML = btn.innerHTML
    btn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>已复制`
    btn.classList.add('copied')
    // 2秒后恢复原状
    setTimeout(() => {
      btn.innerHTML = originalHTML
      btn.classList.remove('copied')
    }, 2000)
  } catch {
    ElMessage.error('复制失败')
  }
}

/**
 * 切换代码块主题（深色/浅色）
 *
 * 通过添加/移除 .light-theme 类实现
 * CSS 中定义了两套主题样式
 */
function handleCodeTheme(wrapper: HTMLElement, btn: HTMLElement) {
  const isLight = wrapper.classList.toggle('light-theme')
  if (isLight) {
    btn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>深色`
  } else {
    btn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="4"/><path d="M12 2v2"/><path d="M12 20v2"/><path d="m4.93 4.93 1.41 1.41"/><path d="m17.66 17.66 1.41 1.41"/><path d="M2 12h2"/><path d="M20 12h2"/><path d="m6.34 17.66-1.41 1.41"/><path d="m19.07 4.93-1.41 1.41"/></svg>浅色`
  }
}

/**
 * 折叠/展开代码块
 *
 * 通过添加/移除 .folded 类实现
 * CSS 中使用 max-height 过渡动画
 */
function handleCodeFold(wrapper: HTMLElement, btn: HTMLElement) {
  const isFolded = wrapper.classList.toggle('folded')
  if (isFolded) {
    btn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m7 9 5 5 5-5"/></svg>展开`
  } else {
    btn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m7 15 5 5 5-5"/><path d="m7 9 5-5 5 5"/></svg>折叠`
  }
}

// ==================== 消息操作 ====================

/**
 * 复制整条消息内容
 * 使用正则移除 HTML 标签，只保留纯文本
 */
function handleCopyMessage() {
  // 移除 HTML 标签，获取纯文本
  const text = parsedContent.value.answerContent.replace(/<[^>]+>/g, '')
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

/** 用户反馈状态：点赞/点踩/无 */
const feedback = ref<'like' | 'dislike' | null>(null)

/**
 * 处理反馈点击
 * 再次点击同一按钮会取消反馈
 */
function handleFeedback(type: 'like' | 'dislike') {
  feedback.value = feedback.value === type ? null : type
}

// ==================== 思考过程解析 ====================

/**
 * 思考过程折叠状态
 * 流式响应时自动展开，完成后默认折叠
 */
const thinkingExpanded = ref(false)

/**
 * 解析消息内容，提取思考过程和回答内容
 *
 * AI 返回格式：
 * <thinking>
 *   这里是思考过程...
 * </thinking>
 * 这里是正式回答...
 *
 * 返回对象：
 * - hasThinking: 是否包含思考过程
 * - thinkingComplete: 思考过程是否完整（有闭合标签）
 * - thinkingContent: 思考过程内容
 * - answerContent: 正式回答内容
 */
const parsedContent = computed(() => {
  const content = props.message.content

  // 查找 <thinking> 标签位置
  const thinkingStart = content.indexOf('<thinking>')
  const thinkingEnd = content.indexOf('</thinking>')

  if (thinkingStart !== -1 && thinkingEnd !== -1 && thinkingEnd > thinkingStart) {
    // 完整的思考过程
    const thinkingContent = content.slice(thinkingStart + 10, thinkingEnd).trim()
    const answerContent = content.slice(thinkingEnd + 11).trim()
    return {
      hasThinking: true,
      thinkingComplete: true,
      thinkingContent,
      answerContent
    }
  } else if (thinkingStart !== -1 && thinkingEnd === -1) {
    // 思考过程正在输出（流式响应）
    const thinkingContent = content.slice(thinkingStart + 10).trim()
    return {
      hasThinking: true,
      thinkingComplete: false,
      thinkingContent,
      answerContent: ''
    }
  }

  // 没有思考过程
  return {
    hasThinking: false,
    thinkingComplete: false,
    thinkingContent: '',
    answerContent: content
  }
})

/**
 * 监听思考过程状态
 * 流式响应开始时自动展开思考区域
 */
watch(() => parsedContent.value.hasThinking, (val) => {
  if (val && props.message.isStreaming) {
    thinkingExpanded.value = true
  }
})

// ==================== Markdown 渲染 ====================

/**
 * 渲染思考过程的 Markdown
 */
const thinkingRendered = computed(() => {
  if (parsedContent.value.thinkingContent) {
    const content = preprocessStreamingMarkdown(parsedContent.value.thinkingContent)
    return md.render(content)
  }
  return ''
})

/**
 * 流式内容渲染预处理
 *
 * 问题背景：
 * 流式响应时，Markdown 可能不完整，导致渲染错误：
 * 1. ###标题（#后没有空格）→ 无法识别为标题
 * 2. **加粗（未闭合）→ 后续内容全部加粗
 *
 * 解决方案：
 * 1. 自动补全 # 后的空格
 * 2. 自动闭合未完成的格式标记
 */
function preprocessStreamingMarkdown(content: string): string {
  // 非流式模式直接返回
  if (!props.message.isStreaming) return content

  // 修复标题格式：###标题 → ### 标题
  // 正则说明：^(#{1,6}) 匹配行首1-6个#，(\S.*)? 匹配后面的非空格内容
  content = content.replace(/^(#{1,6})(\S.*)?$/gm, (_match, hashes, rest) => {
    if (rest) {
      return hashes + ' ' + rest
    }
    return hashes + ' '
  })

  // 修复未闭合的加粗标记
  // 统计 ** 出现次数，奇数则补一个
  const doubleStarCount = (content.match(/\*\*/g) || []).length
  if (doubleStarCount % 2 !== 0) {
    content += '**'
  }

  return content
}

/**
 * 渲染回答内容的 Markdown
 */
const answerRendered = computed(() => {
  if (parsedContent.value.answerContent) {
    const content = preprocessStreamingMarkdown(parsedContent.value.answerContent)
    return md.render(content)
  }
  return ''
})

/** 是否为用户消息 */
const isUser = computed(() => props.message.role === 'user')
</script>

<template>
  <div class="message-item" :class="{ 'message-user': isUser, 'message-assistant': !isUser }">
    <!-- 头像区域 -->
    <div class="message-avatar">
      <div v-if="isUser" class="avatar-user">U</div>
      <div v-else class="avatar-ai">AI</div>
    </div>

    <!-- 消息内容区域 -->
    <div class="message-content">
      <!-- 思考过程区域（仅 AI 消息显示） -->
      <div v-if="!isUser && parsedContent.hasThinking" class="thinking-section">
        <!-- 可点击的头部，展开/折叠思考过程 -->
        <div class="thinking-header" @click="thinkingExpanded = !thinkingExpanded">
          <span class="thinking-icon">
            <!-- 展开状态图标 -->
            <svg v-if="thinkingExpanded" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M6 9l6 6 6-6"/>
            </svg>
            <!-- 折叠状态图标 -->
            <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 18l6-6-6-6"/>
            </svg>
          </span>
          <span class="thinking-label">
            {{ parsedContent.thinkingComplete ? '思考过程' : '思考中...' }}
          </span>
          <!-- 流式响应时的加载动画 -->
          <span v-if="!parsedContent.thinkingComplete" class="thinking-loading">
            <span class="dot"></span>
            <span class="dot"></span>
            <span class="dot"></span>
          </span>
        </div>
        <!-- 思考内容（可折叠） -->
        <div v-show="thinkingExpanded" class="thinking-body">
          <div class="markdown-body thinking-content" v-html="thinkingRendered"></div>
        </div>
      </div>

      <!-- 回答内容 -->
      <div v-if="!isUser" class="answer-wrapper" @click="handleCodeAction">
        <!-- Markdown 渲染的内容 -->
        <div v-if="answerRendered" class="markdown-body" v-html="answerRendered"></div>
        <!-- 流式响应时的光标 -->
        <span v-if="message.isStreaming && !parsedContent.hasThinking" class="typing-cursor"></span>

        <!-- 消息底部工具栏（仅非流式时显示） -->
        <div v-if="!message.isStreaming && answerRendered" class="message-actions" @click.stop>
          <!-- 点赞按钮 -->
          <button
            class="action-btn"
            :class="{ active: feedback === 'like' }"
            title="有帮助"
            @click="handleFeedback('like')"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M7 10v12"/><path d="M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z"/></svg>
          </button>
          <!-- 点踩按钮 -->
          <button
            class="action-btn"
            :class="{ active: feedback === 'dislike' }"
            title="没帮助"
            @click="handleFeedback('dislike')"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 14V2"/><path d="M9 18.12 10 14H4.17a2 2 0 0 1-1.92-2.56l2.33-8A2 2 0 0 1 6.5 2H20a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2.76a2 2 0 0 0-1.79 1.11L12 22a3.13 3.13 0 0 1-3-3.88Z"/></svg>
          </button>
          <!-- 复制按钮 -->
          <button class="action-btn" title="复制" @click="handleCopyMessage">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="14" height="14" x="8" y="8" rx="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg>
          </button>
          <!-- 重新生成按钮 -->
          <button class="action-btn" title="重新生成" @click="emit('regenerate')">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/><path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/><path d="M16 21h5v-5"/></svg>
          </button>
        </div>
      </div>

      <!-- 用户消息 -->
      <div v-else class="user-text">{{ message.content }}</div>
      <span v-if="message.isStreaming && isUser" class="typing-cursor"></span>
    </div>

    <!-- 代码编辑弹窗 -->
    <el-dialog
      v-model="editDialogVisible"
      :title="`编辑代码 - ${editCodeLang}`"
      width="720px"
      :close-on-click-modal="false"
      destroy-on-close
      class="code-edit-dialog"
    >
      <div class="code-edit-wrapper">
        <div class="code-edit-lang-bar">
          <span class="lang-badge">{{ editCodeLang }}</span>
        </div>
        <!-- 代码编辑文本框 -->
        <textarea
          v-model="editCodeContent"
          class="code-edit-textarea"
          spellcheck="false"
          placeholder="请输入代码..."
        />
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveEditedCode">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* ==================== 消息布局 ==================== */

.message-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  max-width: 800px;
  margin: 0 auto;
}

/* 用户消息：头像在右侧 */
.message-user {
  flex-direction: row-reverse;
}

/* ==================== 头像样式 ==================== */

.message-avatar {
  flex-shrink: 0; /* 防止被压缩 */
}

.avatar-user,
.avatar-ai {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

.avatar-user {
  background: #409eff;
  color: white;
}

.avatar-ai {
  background: #67c23a;
  color: white;
}

/* ==================== 消息内容区域 ==================== */

.message-content {
  flex: 1;
  min-width: 0; /* 允许收缩 */
}

/* 用户消息内容靠右 */
.message-user .message-content {
  display: flex;
  justify-content: flex-end;
}

/* 用户消息气泡 */
.user-text {
  background: #409eff;
  color: white;
  padding: 10px 14px;
  border-radius: 12px 12px 4px 12px; /* 右下角尖角 */
  max-width: 70%;
  word-break: break-word;
  line-height: 1.5;
}

/* AI 消息容器 */
.answer-wrapper {
  position: relative;
}

.message-assistant .answer-wrapper,
.message-assistant .message-content {
  background: transparent;
  padding: 8px 16px;
  border-radius: 12px;
  margin-right: 48px;
}

/* ==================== 消息操作工具栏 ==================== */

.message-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 12px;
  padding-top: 8px;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: #909399;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  padding: 0;
}

.action-btn:hover {
  background: rgba(0, 0, 0, 0.05);
  color: #333;
}

.action-btn.active {
  color: #409eff;
  background: rgba(64, 158, 255, 0.08);
}

/* ==================== 思考过程样式 ==================== */

.thinking-section {
  margin-bottom: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  user-select: none;
  border-bottom: 1px solid #e5e7eb;
  transition: background 0.2s;
}

.thinking-header:hover {
  background: #f5f5f5;
}

.thinking-header:last-child {
  border-bottom: none;
}

.thinking-icon {
  color: #909399;
}

.thinking-label {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}

/* 加载动画：三个点依次闪烁 */
.thinking-loading {
  display: flex;
  gap: 4px;
}

.thinking-loading .dot {
  width: 6px;
  height: 6px;
  background: #909399;
  border-radius: 50%;
  animation: dotPulse 1.4s infinite ease-in-out both;
}

.thinking-loading .dot:nth-child(1) { animation-delay: -0.32s; }
.thinking-loading .dot:nth-child(2) { animation-delay: -0.16s; }

@keyframes dotPulse {
  0%, 80%, 100% { opacity: 0.3; }
  40% { opacity: 1; }
}

.thinking-body {
  padding: 12px;
  background: #fafafa;
}

.thinking-content {
  font-size: 13px;
  color: #606266;
  opacity: 0.9;
}

/* ==================== 代码编辑弹窗 ==================== */

.code-edit-wrapper {
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  overflow: hidden;
}

.code-edit-lang-bar {
  display: flex;
  align-items: center;
  padding: 8px 14px;
  background: #f5f5f5;
  border-bottom: 1px solid #e0e0e0;
}

.lang-badge {
  font-size: 12px;
  font-family: 'Fira Code', Consolas, monospace;
  color: #666;
  background: #e8e8e8;
  padding: 2px 8px;
  border-radius: 4px;
  text-transform: lowercase;
}

.code-edit-textarea {
  width: 100%;
  min-height: 400px;
  padding: 14px 16px;
  background: #1e1e1e;
  color: #e6e6e6;
  border: none;
  font-family: 'Fira Code', Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  resize: vertical;
  outline: none;
  tab-size: 2; /* Tab 缩进 2 空格 */
  display: block;
  box-sizing: border-box;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>

<!-- ==================== Markdown 渲染样式（非 scoped） ==================== -->
<!--
  为什么需要非 scoped 样式？
  - scoped 样式会添加 data-v-xxx 属性选择器
  - v-html 渲染的内容不会有这个属性
  - 所以 scoped 样式无法作用于 v-html 内容

  解决方案：
  - 添加一个不带 scoped 的 <style> 块
  - 使用更具体的选择器避免样式污染
-->
<style>
/* === Markdown 基础样式 === */
.message-assistant .message-content .markdown-body {
  line-height: 1.7 !important;
  color: #333 !important;
  word-wrap: break-word;
}

/* 首尾元素无多余间距 */
.message-assistant .message-content .markdown-body > *:first-child {
  margin-top: 0 !important;
}
.message-assistant .message-content .markdown-body > *:last-child {
  margin-bottom: 0 !important;
}

/* === 标题样式 === */
.message-assistant .message-content .markdown-body h1,
.message-assistant .message-content .markdown-body h2,
.message-assistant .message-content .markdown-body h3,
.message-assistant .message-content .markdown-body h4,
.message-assistant .message-content .markdown-body h5,
.message-assistant .message-content .markdown-body h6 {
  margin-top: 16px !important;
  margin-bottom: 10px !important;
  font-weight: 600 !important;
  line-height: 1.4 !important;
  clear: both;
}

.message-assistant .message-content .markdown-body h1 {
  font-size: 1.4em !important;
  border-bottom: 1px solid #eaecef;
  padding-bottom: 6px;
}
.message-assistant .message-content .markdown-body h2 {
  font-size: 1.25em !important;
  border-bottom: 1px solid #eaecef;
  padding-bottom: 4px;
}
.message-assistant .message-content .markdown-body h3 { font-size: 1.1em !important; }
.message-assistant .message-content .markdown-body h4 { font-size: 1em !important; }

/* === 段落 === */
.message-assistant .message-content .markdown-body p {
  margin: 8px 0 !important;
}

/* === 列表样式 === */
/*
  关键：强制显示列表样式
  Tailwind CSS 的 preflight 会移除默认列表样式
  需要用 !important 强制覆盖
*/
.message-assistant .message-content .markdown-body ul,
.message-assistant .message-content .markdown-body ol {
  padding-left: 2em !important;
  margin: 8px 0 !important;
  display: block !important;
}

.message-assistant .message-content .markdown-body li {
  margin: 4px 0 !important;
  line-height: 1.6 !important;
  display: list-item !important; /* 强制显示为列表项 */
}

/* 无序列表：一级 disc，二级 circle，三级 square */
.message-assistant .message-content .markdown-body ul {
  list-style-type: disc !important;
  list-style-position: outside !important;
}
.message-assistant .message-content .markdown-body ul ul {
  list-style-type: circle !important;
  margin: 4px 0 !important;
}
.message-assistant .message-content .markdown-body ul ul ul {
  list-style-type: square !important;
}

/* 有序列表 */
.message-assistant .message-content .markdown-body ol {
  list-style-type: decimal !important;
  list-style-position: outside !important;
}

/* === 代码块样式 === */

/* 代码块容器 */
.message-assistant .message-content .markdown-body pre.code-block-wrapper {
  position: relative !important;
  background: #1e1e1e !important;
  border-radius: 8px !important;
  margin: 12px 0 !important;
  overflow: hidden !important;
  border: 1px solid #333 !important;
}

/* 代码块头部：语言标签 + 操作按钮 */
.message-assistant .message-content .markdown-body .code-block-header {
  display: flex !important;
  align-items: center !important;
  justify-content: space-between !important;
  padding: 8px 14px !important;
  background: #2d2d2d !important;
  border-bottom: 1px solid #3a3a3a !important;
  user-select: none !important;
}

/* 语言标签 */
.message-assistant .message-content .markdown-body .code-lang {
  font-size: 12px !important;
  font-family: 'SF Mono', 'Fira Code', Consolas, monospace !important;
  color: #999 !important;
  text-transform: lowercase !important;
}

/* 操作按钮容器 */
.message-assistant .message-content .markdown-body .code-actions {
  display: flex !important;
  align-items: center !important;
  gap: 4px !important;
}

/* 操作按钮 */
.message-assistant .message-content .markdown-body .code-action-btn {
  display: inline-flex !important;
  align-items: center !important;
  gap: 3px !important;
  padding: 3px 8px !important;
  border: none !important;
  border-radius: 4px !important;
  background: transparent !important;
  color: #aaa !important;
  font-size: 12px !important;
  cursor: pointer !important;
  transition: all 0.15s ease !important;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif !important;
  white-space: nowrap !important;
}

.message-assistant .message-content .markdown-body .code-action-btn:hover {
  background: rgba(255, 255, 255, 0.08) !important;
  color: #fff !important;
}

.message-assistant .message-content .markdown-body .code-action-btn.copied {
  color: #67c23a !important;
}

/* 代码内容区域 */
.message-assistant .message-content .markdown-body pre.code-content {
  margin: 0 !important;
  padding: 14px 16px !important;
  border-radius: 0 !important;
  overflow-x: auto !important;
  white-space: pre !important;
  background: transparent !important;
  max-height: 600px !important;
  transition: max-height 0.3s ease !important;
}

.message-assistant .message-content .markdown-body pre.code-content code {
  background: transparent !important;
  padding: 0 !important;
  color: #e6e6e6 !important;
  font-size: 13px !important;
  white-space: pre !important;
  line-height: 1.6 !important;
}

/* === 浅色主题 === */
.message-assistant .message-content .markdown-body pre.code-block-wrapper.light-theme {
  background: #fafafa !important;
  border-color: #e0e0e0 !important;
}

.message-assistant .message-content .markdown-body pre.code-block-wrapper.light-theme .code-block-header {
  background: #f0f0f0 !important;
  border-bottom-color: #e0e0e0 !important;
}

.message-assistant .message-content .markdown-body pre.code-block-wrapper.light-theme .code-lang {
  color: #666 !important;
}

.message-assistant .message-content .markdown-body pre.code-block-wrapper.light-theme .code-action-btn {
  color: #666 !important;
}

.message-assistant .message-content .markdown-body pre.code-block-wrapper.light-theme .code-action-btn:hover {
  background: rgba(0, 0, 0, 0.05) !important;
  color: #333 !important;
}

.message-assistant .message-content .markdown-body pre.code-block-wrapper.light-theme .code-content code {
  color: #333 !important;
}

/* === 折叠状态 === */
.message-assistant .message-content .markdown-body pre.code-block-wrapper.folded .code-content {
  max-height: 0 !important;
  padding: 0 16px !important;
  overflow: hidden !important;
}

/* === 行内代码 === */
.message-assistant .message-content .markdown-body code:not(.hljs) {
  font-family: 'Fira Code', Consolas, Monaco, 'Courier New', monospace !important;
  font-size: 13px !important;
}

.message-assistant .message-content .markdown-body p code,
.message-assistant .message-content .markdown-body li code,
.message-assistant .message-content .markdown-body td code {
  background: rgba(64, 158, 255, 0.12) !important;
  color: #409eff !important;
  padding: 2px 6px !important;
  border-radius: 4px !important;
  font-size: 0.9em !important;
  word-break: break-word !important;
}

/* === 引用块 === */
.message-assistant .message-content .markdown-body blockquote {
  border-left: 4px solid #409eff !important;
  padding: 6px 14px !important;
  color: #666 !important;
  margin: 10px 0 !important;
  background: #f5f7fa !important;
  border-radius: 0 4px 4px 0 !important;
}

.message-assistant .message-content .markdown-body blockquote p {
  margin: 4px 0 !important;
}

/* === 表格 === */
.message-assistant .message-content .markdown-body table {
  border-collapse: collapse !important;
  width: 100% !important;
  margin: 10px 0 !important;
}

.message-assistant .message-content .markdown-body th,
.message-assistant .message-content .markdown-body td {
  border: 1px solid #dfe2e5 !important;
  padding: 6px 10px !important;
  text-align: left !important;
}

.message-assistant .message-content .markdown-body th {
  background: #f6f8fa !important;
  font-weight: 600 !important;
}

/* === 链接 === */
.message-assistant .message-content .markdown-body a {
  color: #409eff !important;
  text-decoration: none !important;
}

.message-assistant .message-content .markdown-body a:hover {
  text-decoration: underline !important;
}

/* === 强调 === */
.message-assistant .message-content .markdown-body strong {
  font-weight: 600 !important;
}

.message-assistant .message-content .markdown-body em {
  font-style: italic !important;
}

/* === 分隔线 === */
.message-assistant .message-content .markdown-body hr {
  border: none !important;
  border-top: 1px solid #eaecef !important;
  margin: 14px 0 !important;
  height: 0 !important;
}
</style>
