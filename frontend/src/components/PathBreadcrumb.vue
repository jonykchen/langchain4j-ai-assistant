<script setup lang="ts">
/**
 * PathBreadcrumb 路径/标识符面包屑组件
 *
 * 支持文件路径、UUID、URL等长字符串的层级展示，过长时自动折叠中间部分
 */
import { computed, ref } from 'vue'

interface Props {
  /** 显示的内容（路径、UUID、长字符串等） */
  value: string
  /** 分隔符类型：path(/)、uuid(-)、dot(.)、自定义 */
  separator?: 'path' | 'uuid' | 'dot' | 'custom'
  /** 自定义分隔符字符 */
  separatorChar?: string
  /** 折叠前最大显示段数 */
  maxItems?: number
  /** 是否可点击展开 */
  expandable?: boolean
  /** 每段最大字符数 */
  maxSegmentWidth?: number
}

const props = withDefaults(defineProps<Props>(), {
  separator: 'path',
  maxItems: 4,
  expandable: true,
  maxSegmentWidth: 100
})

const emit = defineEmits<{
  (e: 'click', segment: string, index: number): void
  (e: 'expand'): void
}>()

const isExpanded = ref(false)

const separatorChar = computed(() => {
  if (props.separatorChar) return props.separatorChar
  switch (props.separator) {
    case 'path': return '/'
    case 'uuid': return '-'
    case 'dot': return '.'
    default: return '/'
  }
})

const segments = computed(() => {
  if (!props.value) return []
  return props.value.split(separatorChar.value).filter(Boolean)
})

const displaySegments = computed(() => {
  const segs = segments.value
  if (isExpanded.value || segs.length <= props.maxItems) {
    return segs.map((s, i) => ({ text: s, index: i, collapsed: false }))
  }

  // 折叠中间部分
  const keep = Math.floor(props.maxItems / 2)
  const start = keep
  const end = segs.length - keep

  if (end <= start) {
    return segs.map((s, i) => ({ text: s, index: i, collapsed: false }))
  }

  return [
    ...segs.slice(0, start).map((s, i) => ({ text: s, index: i, collapsed: false })),
    { text: '...', index: -1, collapsed: true },
    ...segs.slice(end).map((s, i) => ({ text: s, index: end + i, collapsed: false }))
  ]
})

const toggleExpand = () => {
  if (props.expandable) {
    isExpanded.value = !isExpanded.value
    emit('expand')
  }
}

const handleClick = (segment: { text: string; index: number; collapsed: boolean }) => {
  if (segment.collapsed) {
    toggleExpand()
  } else if (segment.index >= 0) {
    emit('click', segment.text, segment.index)
  }
}

const truncateSegment = (text: string) => {
  if (text.length <= props.maxSegmentWidth) return text
  return text.slice(0, props.maxSegmentWidth - 3) + '...'
}
</script>

<template>
  <div class="path-breadcrumb" :class="{ expandable }">
    <template v-for="(segment, i) in displaySegments" :key="i">
      <span v-if="i > 0" class="separator">{{ separatorChar }}</span>
      <span
        class="segment"
        :class="{ collapsed: segment.collapsed, 'is-expandable': segment.collapsed || expandable }"
        :title="segment.collapsed ? '点击展开完整路径' : segment.text"
        @click="handleClick(segment)"
      >
        {{ segment.collapsed ? segment.text : truncateSegment(segment.text) }}
      </span>
    </template>
  </div>
</template>

<style scoped>
.path-breadcrumb {
  display: inline-flex;
  align-items: center;
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--text-secondary);
  max-width: 100%;
  line-height: 1.4;
}

.separator {
  margin: 0 3px;
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.segment {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 150px;
  flex-shrink: 0;
}

.segment.is-expandable {
  cursor: pointer;
  border-radius: 2px;
  padding: 0 4px;
  margin: 0 -4px;
  transition: all 0.15s ease;
}

.segment.is-expandable:hover {
  background: var(--bg-hover);
  color: var(--color-primary);
}

.segment.collapsed {
  font-weight: 500;
  color: var(--text-tertiary);
  letter-spacing: 2px;
}

.path-breadcrumb.expandable .segment:not(.collapsed) {
  cursor: default;
}

/* 暗色模式适配 */
[data-theme='dark'] .segment.is-expandable:hover {
  background: var(--bg-active);
}
</style>
