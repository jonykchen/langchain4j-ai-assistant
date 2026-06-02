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
// 可选主题：github, atom-one-light,stackoverflow-light 等
// 查看: https://highlightjs.org/static/demo/
import 'highlight.js/styles/github.css'
import type { Message } from '@/types'
import MessageAvatar from '@/components/MessageAvatar.vue'
import ThinkingBlock from '@/components/ThinkingBlock.vue'
import MessageToolbar from '@/components/MessageToolbar.vue'

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
 * 转义 HTML 属性值中的特殊字符
 * 用于 data-xxx 属性中，确保引号不会逃逸出属性
 */
function escapeAttr(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
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
  html: false, // 安全：禁用原始 HTML 标签
  breaks: true, // GitHub 风格：单个换行符转为 <br>
  linkify: true, // 自动链接：识别 URL 并转为可点击链接
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
    // 注意：属性值需要转义引号，防止 XSS 攻击逃逸出属性
    const langLabel = language || 'text'
    const langLabelAttr = escapeAttr(langLabel)
    const rawDataAttr = escapeAttr(str)

    // 返回完整的代码块 HTML
    // 包含：语言标签 + 操作按钮（复制/编辑/主题/折叠）+ 代码内容
    return `<pre class="code-block-wrapper" data-testid="code-block" data-lang="${langLabelAttr}" data-raw="${rawDataAttr}">
      <div class="code-block-header">
        <span class="code-lang">${langLabel}</span>
        <div class="code-actions">
          <button class="code-action-btn" data-action="copy" data-testid="copy-button" title="复制代码">
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
      highlighted = hljs.highlight(editCodeContent.value, {
        language: lang,
        ignoreIllegals: true,
      }).value
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
  navigator.clipboard
    .writeText(text)
    .then(() => {
      ElMessage.success('已复制到剪贴板')
    })
    .catch(() => {
      ElMessage.error('复制失败')
    })
}

/** 用户反馈状态：点赞/点踩/无 */
const feedback = ref<'like' | 'dislike' | null>(null)

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
      answerContent,
    }
  } else if (thinkingStart !== -1 && thinkingEnd === -1) {
    // 思考过程正在输出（流式响应）
    const thinkingContent = content.slice(thinkingStart + 10).trim()
    return {
      hasThinking: true,
      thinkingComplete: false,
      thinkingContent,
      answerContent: '',
    }
  }

  // 没有思考过程
  return {
    hasThinking: false,
    thinkingComplete: false,
    thinkingContent: '',
    answerContent: content,
  }
})

/**
 * 监听思考过程状态
 * 流式响应开始时自动展开思考区域
 */
watch(
  () => parsedContent.value.hasThinking,
  val => {
    if (val && props.message.isStreaming) {
      thinkingExpanded.value = true
    }
  }
)

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
    <MessageAvatar :is-user="isUser" />

    <!-- 消息内容区域 -->
    <div class="message-content">
      <ThinkingBlock
        v-if="!isUser && parsedContent.hasThinking"
        v-model="thinkingExpanded"
        :content="thinkingRendered"
        :complete="parsedContent.thinkingComplete"
      />

      <!-- 回答内容 -->
      <div v-if="!isUser" class="answer-wrapper" @click="handleCodeAction">
        <!-- Markdown 渲染的内容 -->
        <div v-if="answerRendered" class="markdown-body" v-html="answerRendered"></div>
        <!-- 流式响应时的光标 -->
        <span v-if="message.isStreaming && !parsedContent.hasThinking" class="typing-cursor"></span>

        <MessageToolbar
          v-if="!message.isStreaming && answerRendered"
          v-model="feedback"
          @copy="handleCopyMessage"
          @regenerate="emit('regenerate')"
        />
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
  gap: 16px;
  padding: 24px 0;
  max-width: 800px;
  margin: 0 auto;
}

/* 用户消息：头像在右侧 */
.message-user {
  flex-direction: row-reverse;
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
  background: var(--color-primary);
  color: #fff;
  padding: 12px 18px;
  border-radius: 12px 12px 4px 12px; /* 右下角尖角 */
  max-width: 70%;
  word-break: break-word;
  line-height: 1.6;
}

/* AI 消息容器 */
.answer-wrapper {
  position: relative;
}

.message-assistant .answer-wrapper,
.message-assistant .message-content {
  background: transparent;
  padding: 12px 20px;
  border-radius: 12px;
  margin-right: 48px;
}

/* ==================== 代码编辑弹窗 ==================== */

.code-edit-wrapper {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.code-edit-lang-bar {
  display: flex;
  align-items: center;
  padding: 8px 14px;
  background: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-color);
}

.lang-badge {
  font-size: 12px;
  font-family: 'Fira Code', Consolas, monospace;
  color: var(--text-secondary);
  background: var(--bg-hover);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
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
