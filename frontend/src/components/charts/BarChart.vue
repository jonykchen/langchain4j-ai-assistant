<script setup lang="ts">
/**
 * BarChart 柱状图组件
 *
 * 基于 ECharts 的柱状图，支持多系列、响应式
 */
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import * as echarts from 'echarts'
import { useThemeStore } from '@/stores/theme'

interface Series {
  name: string
  data: number[]
  color?: string
}

interface Props {
  series: Series[]
  xAxisData: string[]
  height?: string
  horizontal?: boolean
  showBackground?: boolean
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  height: '300px',
  horizontal: false,
  showBackground: false,
  loading: false
})

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null
const themeStore = useThemeStore()

const isDark = computed(() => themeStore.isDark)

const colors = [
  '#667eea', '#11998e', '#fc4a1a', '#ee0979', '#4facfe', '#43e97b'
]

const getOption = (): echarts.EChartsOption => {
  const series = props.series.map((s, index) => ({
    name: s.name,
    type: 'bar' as const,
    data: s.data,
    itemStyle: {
      color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: s.color || colors[index % colors.length] },
        { offset: 1, color: echarts.color.lift(s.color || colors[index % colors.length], 0.3) }
      ]),
      borderRadius: [4, 4, 0, 0]
    },
    barMaxWidth: 40,
    showBackground: props.showBackground,
    backgroundStyle: {
      color: isDark.value ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.02)',
      borderRadius: 4
    }
  }))

  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: isDark.value ? 'rgba(30, 30, 30, 0.9)' : 'rgba(255, 255, 255, 0.9)',
      borderColor: isDark.value ? '#434343' : '#e4e7ed',
      textStyle: {
        color: isDark.value ? '#e0e0e0' : '#303133'
      },
      axisPointer: {
        type: 'shadow'
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
      type: props.horizontal ? 'value' : 'category',
      data: props.horizontal ? undefined : props.xAxisData,
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
      type: props.horizontal ? 'category' : 'value',
      data: props.horizontal ? props.xAxisData : undefined,
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
