<script setup lang="ts">
/**
 * StatsCard 统计卡片组件
 *
 * 现代化统计卡片，支持渐变图标、趋势标签、悬停动画
 */
import { computed } from 'vue'

interface Props {
  title: string
  value: string | number
  icon?: string
  iconType?: 'users' | 'active' | 'tokens' | 'cost' | 'requests' | 'success' | 'errors'
  trend?: 'up' | 'down' | 'flat'
  trendValue?: string
  suffix?: string
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  iconType: 'users',
  trend: 'flat',
  loading: false
})

const formatValue = computed(() => {
  if (props.loading) return '---'
  const val = props.value
  if (typeof val === 'number') {
    if (val >= 1000000) return (val / 1000000).toFixed(1) + 'M'
    if (val >= 1000) return (val / 1000).toFixed(1) + 'K'
    return val.toString()
  }
  return val
})

const trendIcon = computed(() => {
  switch (props.trend) {
    case 'up': return 'Top'
    case 'down': return 'Bottom'
    default: return 'Minus'
  }
})
</script>

<template>
  <div class="stats-card">
    <div class="stats-card-header">
      <div v-if="icon" :class="['stats-card-icon', iconType]">
        <el-icon :size="24"><component :is="icon" /></el-icon>
      </div>
      <div class="stats-card-info">
        <div class="stats-card-value">
          {{ formatValue }}<span v-if="suffix" class="suffix">{{ suffix }}</span>
        </div>
        <div class="stats-card-label">{{ title }}</div>
      </div>
    </div>
    <div v-if="trend !== 'flat' && trendValue" class="stats-card-trend" :class="trend">
      <el-icon><component :is="trendIcon" /></el-icon>
      <span>{{ trendValue }}</span>
    </div>

    <!-- Loading Skeleton -->
    <div v-if="loading" class="stats-card-skeleton">
      <div class="skeleton skeleton-text" style="width: 60%; height: 28px;"></div>
      <div class="skeleton skeleton-text" style="width: 40%; height: 16px;"></div>
    </div>
  </div>
</template>

<style scoped>
.stats-card {
  background: var(--bg-primary);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-card);
  padding: var(--space-xl);
  border: 1px solid var(--border-light);
  transition: all var(--duration-normal) var(--ease-out);
  position: relative;
  overflow: hidden;
}

.stats-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-4px);
}

.stats-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--gradient-primary);
  opacity: 0;
  transition: opacity var(--duration-normal) var(--ease-out);
}

.stats-card:hover::before {
  opacity: 1;
}

.stats-card-header {
  display: flex;
  align-items: flex-start;
  gap: var(--space-lg);
}

.stats-card-icon {
  width: 56px;
  height: 56px;
  min-width: 56px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  box-shadow: var(--shadow-md);
}

.stats-card-icon.users { background: var(--gradient-stats-users); }
.stats-card-icon.active { background: var(--gradient-stats-active); }
.stats-card-icon.tokens { background: var(--gradient-stats-tokens); }
.stats-card-icon.cost { background: var(--gradient-stats-cost); }
.stats-card-icon.requests { background: var(--gradient-stats-requests); }
.stats-card-icon.success { background: var(--gradient-stats-success); }
.stats-card-icon.errors { background: var(--gradient-stats-errors); }

.stats-card-info {
  flex: 1;
  min-width: 0;
}

.stats-card-value {
  font-size: var(--font-size-3xl);
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
}

.stats-card-value .suffix {
  font-size: var(--font-size-lg);
  font-weight: 500;
  color: var(--text-secondary);
  margin-left: 4px;
}

.stats-card-label {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
  margin-top: 4px;
}

.stats-card-trend {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--font-size-xs);
  font-weight: 500;
  padding: 4px 10px;
  border-radius: var(--radius-full);
  margin-top: var(--space-md);
}

.stats-card-trend.up {
  color: var(--color-success);
  background: var(--color-success-light);
}

.stats-card-trend.down {
  color: var(--color-danger);
  background: var(--color-danger-light);
}

.stats-card-skeleton {
  position: absolute;
  inset: 0;
  background: var(--bg-primary);
  padding: var(--space-xl);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

@media (max-width: 768px) {
  .stats-card-value {
    font-size: var(--font-size-2xl);
  }

  .stats-card-icon {
    width: 48px;
    height: 48px;
    min-width: 48px;
  }
}
</style>
