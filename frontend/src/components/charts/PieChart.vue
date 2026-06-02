<script setup lang="ts">
/**
 * PieChart 饼图组件
 *
 * 基于 ECharts 的饼图，支持响应式、暗色模式
 */
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import * as echarts from 'echarts'
import { useThemeStore } from '@/stores/theme'

interface DataItem {
  name: string
  value: number
}

interface Props {
  data: DataItem[]
  height?: string
  showLegend?: boolean
  radius?: string[]
  center?: string[]
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  height: '300px',
  showLegend: true,
  radius: () => ['40%', '70%'],
  center: () => ['50%', '50%'],
  loading: false,
})

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null
const themeStore = useThemeStore()

const isDark = computed(() => themeStore.isDark)

const colors = [
  '#667eea',
  '#11998e',
  '#fc4a1a',
  '#ee0979',
  '#4facfe',
  '#43e97b',
  '#f093fb',
  '#fee140',
]

const getOption = (): echarts.EChartsOption => {
  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: isDark.value ? 'rgba(30, 30, 30, 0.9)' : 'rgba(255, 255, 255, 0.9)',
      borderColor: isDark.value ? '#434343' : '#e4e7ed',
      textStyle: {
        color: isDark.value ? '#e0e0e0' : '#303133',
      },
      formatter: '{b}: {c} ({d}%)',
    },
    legend: props.showLegend
      ? {
          orient: 'vertical',
          right: '5%',
          top: 'center',
          textStyle: {
            color: isDark.value ? '#b0b0b0' : '#606266',
          },
        }
      : undefined,
    color: colors,
    series: [
      {
        type: 'pie',
        radius: props.radius,
        center: props.center,
        avoidLabelOverlap: true,
        itemStyle: {
          borderRadius: 6,
          borderColor: isDark.value ? '#1d1d1d' : '#fff',
          borderWidth: 2,
        },
        label: {
          show: false,
          position: 'center',
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold',
            color: isDark.value ? '#e0e0e0' : '#303133',
          },
        },
        labelLine: {
          show: false,
        },
        data: props.data,
      },
    ],
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

watch(() => props.data, updateChart, { deep: true })
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
