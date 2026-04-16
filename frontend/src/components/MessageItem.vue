<script setup lang="ts">
import { computed } from 'vue'
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

const renderedContent = computed(() => {
  return md.render(props.message.content)
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
      <div
        v-if="!isUser"
        class="markdown-body"
        v-html="renderedContent"
      />
      <div v-else class="user-text">{{ message.content }}</div>
      <span v-if="message.isStreaming" class="typing-cursor"></span>
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
</style>
