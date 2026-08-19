<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import type { ECharts, EChartsCoreOption } from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { TooltipComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useThemeStore } from '@/store/theme'

// 通用柱状图容器：按需注册，canvas 色需在暗色切换时由父组件重算 option 触发重绘
echarts.use([BarChart, TooltipComponent, GridComponent, CanvasRenderer])

const props = withDefaults(defineProps<{
  option: EChartsCoreOption
  height?: string
}>(), {
  height: '260px',
})

const el = ref<HTMLDivElement>()
let chart: ECharts | null = null

const themeStore = useThemeStore()

function render() {
  if (!chart) return
  chart.setOption(props.option, true)
}

onMounted(() => {
  if (!el.value) return
  chart = echarts.init(el.value)
  render()
  window.addEventListener('resize', handleResize)
})

// 深色切换时父组件 option 已重算（依赖 darkMode），此处仅确保 setOption
watch(() => themeStore.darkMode, render)
watch(() => props.option, render, { deep: true })

function handleResize() {
  chart?.resize()
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="el" class="chart-box" :style="{ height }" />
</template>

<style scoped>
.chart-box {
  width: 100%;
}
</style>