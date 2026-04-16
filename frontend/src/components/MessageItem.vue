<script setup lang="ts">
import { ref, computed } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import type { Message } from '@/types'

const props = defineProps<{
  message: Message
}>()

const md = new MarkdownIt({
  highlight: (str: string, lang: string) => {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(str, { language: lang }).value
      } catch {
        // ignore
      }
    }
    return ''
  },
})

// 思考过程折叠状态
const thinkingExpanded = ref(false)

// 解析内容，提取思考过程和回答内容
const parsedContent = computed(() => {
  const content = props.message.content

  // 查找 <thinking> 标签
  const thinkingStart = content.indexOf('<thinking>')
  const thinkingEnd = content.indexOf('</thinking>')

  if (thinkingStart !== -1 && thinkingEnd !== -1 && thinkingEnd > thinkingStart) {
    // 有完整的思考过程
    const thinkingContent = content.slice(thinkingStart + 10, thinkingEnd).trim()
    const answerContent = content.slice(thinkingEnd + 11).trim()
    return {
      hasThinking: true,
      thinkingComplete: true,
      thinkingContent,
      answerContent
    }
  } else if (thinkingStart !== -1 && thinkingEnd === -1) {
    // 思考过程正在输出（流式响应时）
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

const thinkingRendered = computed(() => {
  if (parsedContent.value.thinkingContent) {
    return md.render(parsedContent.value.thinkingContent)
  }
  return ''
})

const answerRendered = computed(() => {
  if (parsedContent.value.answerContent) {
    return md.render(parsedContent.value.answerContent)
  }
  return ''
})

const isUser = computed(() => props.message.role === 'user')
</script>

<template>
  <div class="message-item" :class="{ 'message-user': isUser, 'message-assistant': !isUser }">
    <div class="message-avatar">
      <div v-if="isUser" class="avatar-user">U</div>
      <div v-else class="avatar-ai">AI</div>
    </div>
    <div class="message-content">
      <!-- 思考过程区域 -->
      <div v-if="!isUser && parsedContent.hasThinking" class="thinking-section">
        <div class="thinking-header" @click="thinkingExpanded = !thinkingExpanded">
          <span class="thinking-icon">
            <svg v-if="thinkingExpanded" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M6 9l6 6 6-6"/>
            </svg>
            <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 18l6-6-6-6"/>
            </svg>
          </span>
          <span class="thinking-label">
            {{ parsedContent.thinkingComplete ? '思考过程' : '思考中...' }}
          </span>
          <span v-if="!parsedContent.thinkingComplete" class="thinking-loading">
            <span class="dot"></span>
            <span class="dot"></span>
            <span class="dot"></span>
          </span>
        </div>
        <div v-show="thinkingExpanded" class="thinking-body">
          <div class="markdown-body thinking-content" v-html="thinkingRendered"></div>
        </div>
      </div>

      <!-- 回答内容 -->
      <div v-if="!isUser">
        <div v-if="answerRendered" class="markdown-body" v-html="answerRendered"></div>
        <span v-if="message.isStreaming && !parsedContent.hasThinking" class="typing-cursor"></span>
      </div>
      <div v-else class="user-text">{{ message.content }}</div>
      <span v-if="message.isStreaming && isUser" class="typing-cursor"></span>
    </div>
  </div>
</template>

<style scoped>
.message-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  max-width: 800px;
  margin: 0 auto;
}

.message-user {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
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

.message-content {
  flex: 1;
  min-width: 0;
}

.message-user .message-content {
  display: flex;
  justify-content: flex-end;
}

.user-text {
  background: #409eff;
  color: white;
  padding: 10px 14px;
  border-radius: 12px 12px 4px 12px;
  max-width: 70%;
  word-break: break-word;
  line-height: 1.5;
}

.message-assistant .message-content {
  background: #f5f7fa;
  padding: 12px 16px;
  border-radius: 12px;
  margin-right: 48px;
}

/* 思考过程样式 */
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

.thinking-loading .dot:nth-child(1) {
  animation-delay: -0.32s;
}

.thinking-loading .dot:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes dotPulse {
  0%, 80%, 100% {
    opacity: 0.3;
  }
  40% {
    opacity: 1;
  }
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
</style>