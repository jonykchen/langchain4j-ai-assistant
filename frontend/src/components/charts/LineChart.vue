<script setup lang="ts">
/**
 * LineChart 折线图组件
 *
 * 基于 ECharts 的折线图，支持多系列、响应式
 */
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import * as echarts from 'echarts'
import { useThemeStore } from '@/stores/theme'

interface DataItem {
  name: string
  value: number
}

interface Series {
  name: string
  data: DataItem[]
  color?: string
}

interface Props {
  series: Series[]
  xAxisData?: string[]
  height?: string
  smooth?: boolean
  showArea?: boolean
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  height: '300px',
  smooth: true,
  showArea: true,
  loading: false
})

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null
const themeStore = useThemeStore()

const isDark = computed(() => themeStore.isDark)

const getOption = (): echarts.EChartsOption => {
  const colors = [
    '#667eea', '#11998e', '#fc4a1a', '#ee0979', '#4facfe', '#43e97b'
  ]

  const series = props.series.map((s, index) => ({
    name: s.name,
    type: 'line' as const,
    data: s.data.map(d => d.value),
    smooth: props.smooth,
    symbol: 'circle',
    symbolSize: 6,
    itemStyle: {
      color: s.color || colors[index % colors.length]
    },
    lineStyle: {
      width: 2
    },
    areaStyle: props.showArea ? {
      opacity: 0.15
    } : undefined
  }))

  const xAxisData = props.xAxisData || props.series[0]?.data.map(d => d.name) || []

  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: isDark.value ? 'rgba(30, 30, 30, 0.9)' : 'rgba(255, 255, 255, 0.9)',
      borderColor: isDark.value ? '#434343' : '#e4e7ed',
      textStyle: {
        color: isDark.value ? '#e0e0e0' : '#303133'
      }
    },
    legend: {
      data: props.series.map(s => s.name),
      textStyle: {
        color: isDark.value ? '#b0b0b0' : '#606266'
      },
      top: 0
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '40px',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: xAxisData,
      axisLine: {
        lineStyle: {
          color: isDark.value ? '#434343' : '#e4e7ed'
        }
      },
      axisLabel: {
        color: isDark.value ? '#808080' : '#909399'
      }
    },
    yAxis: {
      type: 'value',
      axisLine: {
        show: false
      },
      axisLabel: {
        color: isDark.value ? '#808080' : '#909399'
      },
      splitLine: {
        lineStyle: {
          color: isDark.value ? '#303030' : '#ebeef5'
        }
      }
    },
    series
  }
}

const initChart = () => {
  if (!chartRef.value) return

  chart = echarts.init(chartRef.value)
  chart.setOption(getOption())
}

const updateChart = () => {
  if (!chart) return
  chart.setOption(getOption())
}

const resizeChart = () => {
  chart?.resize()
}

watch(() => props.series, updateChart, { deep: true })
watch(isDark, updateChart)

onMounted(() => {
  initChart()
  window.addEventListener('resize', resizeChart)
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
})
</script>

<template>
  <div class="chart-wrapper">
    <div ref="chartRef" class="chart" :style="{ height }"></div>
    <div v-if="loading" class="chart-loading">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
    </div>
  </div>
</template>

<style scoped>
.chart-wrapper {
  position: relative;
  width: 100%;
}

.chart {
  width: 100%;
}

.chart-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-primary);
  opacity: 0.8;
}
</style>
