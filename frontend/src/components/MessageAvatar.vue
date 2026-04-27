<template>
  <div class="message-avatar">
    <div v-if="isUser" class="avatar-user">U</div>
    <div v-else class="avatar-ai">AI</div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  isUser: boolean
}>()
</script>

<style scoped>
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
  background: var(--color-primary);
  color: #fff;
}

.avatar-ai {
  background: var(--color-success);
  color: #fff;
}
</style>
