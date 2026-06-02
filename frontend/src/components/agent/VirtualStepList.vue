<script setup lang="ts">
/**
 * 虚拟滚动步骤列表组件
 *
 * 当步骤数量超过阈值时，使用虚拟滚动优化性能。
 * 基于 @vueuse/core 的 useVirtualList 实现。
 *
 * @author jonychen
 */
import { computed } from 'vue'
import { useVirtualList } from '@vueuse/core'
import type { ExecutionStep } from '@/types/agent'
import ExecutionStepCard from './ExecutionStepCard.vue'

const props = withDefaults(
  defineProps<{
    steps: ExecutionStep[]
    itemHeight?: number
    overscan?: number
  }>(),
  {
    itemHeight: 120,
    overscan: 5,
  }
)

// 虚拟滚动
const { list, containerProps, wrapperProps } = useVirtualList(
  computed(() => props.steps),
  {
    itemHeight: props.itemHeight,
    overscan: props.overscan,
  }
)

// 判断是否最后一步
const isLastStep = (index: number): boolean => {
  return index === props.steps.length - 1
}
</script>

<template>
  <div v-bind="containerProps" class="virtual-steps-container">
    <div v-bind="wrapperProps" class="virtual-steps-wrapper">
      <div
        v-for="{ data: step, index } in list"
        :key="step.id"
        class="virtual-step-item"
        :style="{ height: `${itemHeight}px` }"
      >
        <ExecutionStepCard :step="step" :is-last-step="isLastStep(index)" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.virtual-steps-container {
  height: 100%;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.virtual-steps-wrapper {
  position: relative;
}

.virtual-step-item {
  padding-bottom: 8px;
}
</style>
