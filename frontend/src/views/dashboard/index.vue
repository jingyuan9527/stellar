<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import type { EChartsCoreOption } from 'echarts/core'
import { NCard, NGrid, NGridItem, NIcon, NStatistic, NTag } from 'naive-ui'
import Chart from '@/components/Chart.vue'
import StateError from '@/components/StateError.vue'
import BrandEmpty from '@/components/BrandEmpty.vue'
import { useAuthStore } from '@/store/auth'
import { useThemeStore } from '@/store/theme'
import { getChartColors } from '@/utils/chartColors'
import { iconMap } from '@/utils/icons'
import { getDashboardStats } from '@/api/dashboard'
import type { DashboardStats, DashboardTaskStat } from '@/types/api'

const authStore = useAuthStore()
const themeStore = useThemeStore()
const stats = ref<DashboardStats | null>(null)
// 首屏整页加载骨架 / 失败占位（消除数据未回空白）
const loading = ref(false)
const loadError = ref(false)

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

/** KPI 卡配色：brand/info 两色交替（跟随主题色 + token 信息色） */
const kpiPalette = computed(() => {
  void themeStore.darkMode
  return [themeStore.primaryColor, getChartColors().info]
})

/** 环比涨跌幅：prev 为 0 返回 null（无基准不渲染），返回上升/下降 + 绝对百分比 */
interface KpiDelta {
  up: boolean
  pct: number
}

interface AiKpiCard {
  label: string
  value: number
  icon: string
  color: string
  delta?: KpiDelta | null
}

function deltaPct(curr: number, prev: number): KpiDelta | null {
  if (!prev) return null
  const pct = Math.round(((curr - prev) / prev) * 100)
  return { up: pct >= 0, pct: Math.abs(pct) }
}

const aiKpiCards = computed<AiKpiCard[]>(() => {
  const u = stats.value?.aiUsage
  const cur = {
    tokens: u?.periodTokens ?? 0,
    calls: u?.periodCalls ?? 0,
  }
  const prev = {
    tokens: u?.prevPeriodTokens ?? 0,
    calls: u?.prevPeriodCalls ?? 0,
  }
  return [
    { label: '总 Token 消耗', value: u?.totalTokens ?? 0, icon: 'sparkles', color: kpiPalette.value[0] },
    { label: '近7日 Token', value: cur.tokens, icon: 'pulse', color: kpiPalette.value[0], delta: deltaPct(cur.tokens, prev.tokens) },
    { label: '今日 Token', value: u?.todayTokens ?? 0, icon: 'info', color: kpiPalette.value[1] },
    { label: '总调用次数', value: u?.totalCalls ?? 0, icon: 'grid', color: kpiPalette.value[0] },
    { label: '近7日调用', value: cur.calls, icon: 'bulb', color: kpiPalette.value[1], delta: deltaPct(cur.calls, prev.calls) },
    { label: '今日调用', value: u?.todayCalls ?? 0, icon: 'list', color: kpiPalette.value[1] },
  ]
})

const todayKpiCards = computed(() => [
  { label: '今日文案生成', value: stats.value?.textGen?.today ?? 0, icon: 'log', color: kpiPalette.value[0], sub: todayShare.value },
  { label: '今日图片生成', value: stats.value?.imageTask?.today ?? 0, icon: 'image', color: kpiPalette.value[1], sub: todayShare.value },
  { label: '今日视频生成', value: stats.value?.videoTask?.today ?? 0, icon: 'film', color: kpiPalette.value[0], sub: todayShare.value },
  { label: '今日 TTS 合成', value: stats.value?.tts?.today ?? 0, icon: 'volume', color: kpiPalette.value[1], sub: todayShare.value },
])

/** 今日生成占近 7 日比例（今日调用 / 近 7 日调用总和） */
const todayShare = computed<number | null>(() => {
  const trend = stats.value?.aiUsage?.dailyTrend ?? []
  const total = trend.reduce((s, d) => s + d.calls, 0)
  const today = stats.value?.aiUsage?.todayCalls ?? 0
  if (!total || !today) return null
  return Math.round((today / total) * 100)
})

function renderStatIcon(name: string, color: string) {
  const Icon = iconMap[name]
  if (!Icon) return null
  return h(NIcon, { size: 24, color }, { default: () => h(Icon) })
}

// ===== 近 7 日趋势（双轴柱状：Token / 调用） =====
const trendOption = computed<EChartsCoreOption>(() => {
  void themeStore.darkMode
  const c = getChartColors()
  const trend = stats.value?.aiUsage?.dailyTrend ?? []
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 8, right: 8, top: 32, bottom: 4, containLabel: true },
    xAxis: {
      type: 'category',
      data: trend.map((d) => d.date.slice(5)),
      axisLabel: { color: c.text3 },
      axisLine: { lineStyle: { color: c.border } },
    },
    yAxis: [
      { type: 'value', axisLabel: { color: c.text3 }, splitLine: { lineStyle: { color: c.border } } },
      { type: 'value', axisLabel: { color: c.text3 }, splitLine: { show: false } },
    ],
    series: [
      { name: 'Token', type: 'bar', yAxisIndex: 0, data: trend.map((d) => d.tokens), itemStyle: { color: c.brand }, barMaxWidth: 16 },
      { name: '调用', type: 'bar', yAxisIndex: 1, data: trend.map((d) => d.calls), itemStyle: { color: c.info }, barMaxWidth: 16 },
    ],
  }
})

// ===== 近 7 日趋势解读（后半周 vs 前半周 + 峰值，纯前端推导） =====
const trendInsight = computed<{ total: number; up: boolean; pct: number; peakDate: string; peakCalls: number } | null>(() => {
  const t = stats.value?.aiUsage?.dailyTrend ?? []
  if (t.length < 2) return null
  const mid = Math.ceil(t.length / 2)
  const firstHalf = t.slice(0, mid).reduce((s, d) => s + d.calls, 0)
  const secondHalf = t.slice(mid).reduce((s, d) => s + d.calls, 0)
  const delta = secondHalf - firstHalf
  const pct = firstHalf ? Math.round((Math.abs(delta) / firstHalf) * 100) : 0
  const peak = t.reduce((a, b) => (b.calls > a.calls ? b : a), t[0])
  return {
    total: t.reduce((s, d) => s + d.calls, 0),
    up: delta >= 0,
    pct,
    peakDate: peak.date,
    peakCalls: peak.calls,
  }
})

// ===== 模型类型 / 供应商 =====
const modelTypeLabels: Record<string, string> = {
  TEXT: '文本对话',
  IMAGE: '图片生成',
  AUDIO: '语音合成',
  EMBEDDING: '向量嵌入',
  VIDEO: '视频生成',
}
function typeLabel(t: string): string {
  return modelTypeLabels[t] ?? t
}

const modelTypeOption = computed<EChartsCoreOption>(() => {
  void themeStore.darkMode
  const c = getChartColors()
  const byType = stats.value?.aiUsage?.byType ?? []
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 8, right: 8, top: 28, bottom: 4, containLabel: true },
    xAxis: {
      type: 'category',
      data: byType.map((t) => typeLabel(t.modelType)),
      axisLabel: { color: c.text3 },
      axisLine: { lineStyle: { color: c.border } },
    },
    yAxis: { type: 'value', axisLabel: { color: c.text3 }, splitLine: { lineStyle: { color: c.border } } },
    series: [
      { name: 'Token', type: 'bar', data: byType.map((t) => t.tokens), itemStyle: { color: c.brand }, barMaxWidth: 28 },
    ],
  }
})

const providerOption = computed<EChartsCoreOption>(() => {
  void themeStore.darkMode
  const c = getChartColors()
  const byProvider = stats.value?.aiUsage?.byProvider ?? []
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 8, right: 8, top: 28, bottom: 4, containLabel: true },
    xAxis: {
      type: 'category',
      data: byProvider.map((p) => p.providerName || '未知'),
      axisLabel: { color: c.text3, interval: 0, rotate: byProvider.length > 4 ? 30 : 0 },
      axisLine: { lineStyle: { color: c.border } },
    },
    yAxis: { type: 'value', axisLabel: { color: c.text3 }, splitLine: { lineStyle: { color: c.border } } },
    series: [
      { name: 'Token', type: 'bar', data: byProvider.map((p) => p.tokens), itemStyle: { color: c.info }, barMaxWidth: 28 },
    ],
  }
})

// ===== AI 生成质量（文案/图片/视频）=====
const taskQualityCards = computed(() => [
  { title: '文案生成', icon: 'log', color: kpiPalette.value[0], stat: stats.value?.textGen, unit: 'ms' as const },
  { title: '图片生成', icon: 'image', color: kpiPalette.value[1], stat: stats.value?.imageTask, unit: 's' as const },
  { title: '视频生成', icon: 'film', color: kpiPalette.value[0], stat: stats.value?.videoTask, unit: 's' as const },
])

// ===== 文件存储 =====
const fileTypeOption = computed<EChartsCoreOption>(() => {
  void themeStore.darkMode
  const c = getChartColors()
  const byType = stats.value?.file?.byType ?? []
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 8, right: 8, top: 28, bottom: 4, containLabel: true },
    xAxis: {
      type: 'category',
      data: byType.map((t) => fileTypeLabels[t.type] ?? t.type),
      axisLabel: { color: c.text3 },
      axisLine: { lineStyle: { color: c.border } },
    },
    yAxis: { type: 'value', axisLabel: { color: c.text3 }, splitLine: { lineStyle: { color: c.border } } },
    series: [
      { name: '数量', type: 'bar', data: byType.map((t) => t.count), itemStyle: { color: c.brand }, barMaxWidth: 28 },
    ],
  }
})
const fileTypeLabels: Record<string, string> = { image: '图片', audio: '音频', other: '其他' }

// ===== 格式化辅助 =====
function formatNumber(n: number | undefined | null): string {
  return (n ?? 0).toLocaleString('zh-CN')
}

function formatBytes(n: number | undefined | null): string {
  const v = n ?? 0
  if (v < 1024) return v + ' B'
  if (v < 1024 * 1024) return (v / 1024).toFixed(1) + ' KB'
  if (v < 1024 * 1024 * 1024) return (v / 1024 / 1024).toFixed(1) + ' MB'
  return (v / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

function formatDuration(stat: DashboardTaskStat | null | undefined, unit: 'ms' | 's'): string {
  if (!stat || !stat.avgDuration) return '-'
  if (unit === 'ms') {
    if (stat.avgDuration < 1000) return stat.avgDuration + ' ms'
    const s = stat.avgDuration / 1000
    if (s < 60) return s.toFixed(1) + ' 秒'
    const m = Math.floor(s / 60)
    const sec = Math.round(s % 60)
    return `${m} 分 ${sec} 秒`
  }
  const s = stat.avgDuration
  if (s < 60) return s + ' 秒'
  const m = Math.floor(s / 60)
  const sec = Math.round(s % 60)
  return `${m} 分 ${sec} 秒`
}

function formatRate(rate: number | undefined | null): string {
  return (rate ?? 0) + '%'
}

async function loadStats() {
  loading.value = true
  loadError.value = false
  try {
    stats.value = await getDashboardStats()
  } catch {
    loadError.value = true
    // 错误已由拦截器提示，页面下方展示 StateError 占位
  } finally {
    loading.value = false
  }
}

onMounted(loadStats)
</script>

<template>
  <div class="dashboard">
    <!-- 欢迎卡 -->
    <NCard class="welcome-card" :bordered="false">
      <div class="welcome">
        <div class="welcome-text">
          <h2>{{ greeting }}，{{ authStore.userInfo?.nickname || '用户' }} 👋</h2>
          <p>Stellar 个人实验沉淀池 · 仪表盘总览</p>
        </div>
      </div>
    </NCard>

    <StateError v-if="loadError" @retry="loadStats" />

    <!-- AI 调用概览 -->
    <div class="section-title">AI 调用概览</div>
    <NGrid :x-gap="16" :y-gap="16" :cols="4" responsive="screen" item-responsive>
      <NGridItem v-for="(item, idx) in aiKpiCards" :key="item.label" span="4 m:2 l:1">
        <NCard :bordered="false" class="stat-card" :loading="loading" :style="{ animationDelay: idx * 40 + 'ms' }">
          <div class="stat">
            <div class="stat-icon" :style="{ background: item.color + '1a' }">
              <component :is="renderStatIcon(item.icon, item.color)" />
            </div>
            <NStatistic :label="item.label" :value="formatNumber(item.value)" />
            <span
              v-if="item.delta"
              class="kpi-delta"
              :class="item.delta.up ? 'up' : 'down'"
              :title="`较前 7 日${item.delta.up ? '上升' : '下降'} ${item.delta.pct}%`"
            >
              <NIcon size="14"><component :is="item.delta.up ? iconMap.arrowUp : iconMap.arrowDown" /></NIcon>
              {{ item.delta.pct }}%
            </span>
          </div>
        </NCard>
      </NGridItem>
    </NGrid>

    <!-- 今日生成 -->
    <div class="section-title">今日生成</div>
    <NGrid :x-gap="16" :y-gap="16" :cols="4" responsive="screen" item-responsive>
      <NGridItem v-for="(item, idx) in todayKpiCards" :key="item.label" span="4 m:2 l:1">
        <NCard :bordered="false" class="stat-card" :loading="loading" :style="{ animationDelay: idx * 40 + 'ms' }">
          <div class="stat">
            <div class="stat-icon" :style="{ background: item.color + '1a' }">
              <component :is="renderStatIcon(item.icon, item.color)" />
            </div>
            <NStatistic :label="item.label" :value="formatNumber(item.value)" />
          </div>
          <div v-if="item.sub != null" class="stat-sub">今日占近 7 日 {{ item.sub }}%</div>
        </NCard>
      </NGridItem>
    </NGrid>

    <!-- 近 7 日趋势 -->
    <NCard title="近 7 日 AI 调用趋势" :bordered="false" :loading="loading">
      <div v-if="stats?.aiUsage?.dailyTrend?.length" class="trend">
        <Chart :option="trendOption" height="240px" />
        <div v-if="trendInsight" class="trend-caption">
          <NIcon size="15" :class="trendInsight.up ? 'up' : 'down'">
            <component :is="trendInsight.up ? iconMap.arrowUp : iconMap.arrowDown" />
          </NIcon>
          <span>近 7 日共 {{ formatNumber(trendInsight.total) }} 次调用，后半周较前半周
            <b :class="trendInsight.up ? 'up' : 'down'">{{ trendInsight.up ? '上升' : '下降' }} {{ trendInsight.pct }}%</b>；
            峰值 {{ trendInsight.peakDate }}（{{ formatNumber(trendInsight.peakCalls) }} 次）</span>
        </div>
      </div>
      <BrandEmpty size="small" description="暂无数据" v-else />
    </NCard>

    <!-- AI 生成质量 -->
    <div class="section-title">AI 生成质量</div>
    <NGrid :x-gap="16" :y-gap="16" :cols="3" responsive="screen" item-responsive>
      <NGridItem v-for="(item, idx) in taskQualityCards" :key="item.title" span="3 m:1">
        <NCard :bordered="false" class="quality-card" :loading="loading" :style="{ animationDelay: idx * 40 + 'ms' }">
          <div class="quality-head">
            <div class="stat-icon" :style="{ background: item.color + '1a' }">
              <component :is="renderStatIcon(item.icon, item.color)" />
            </div>
            <span class="quality-title">{{ item.title }}</span>
            <NTag size="small" :bordered="false" type="success" round>
              {{ formatRate(item.stat?.successRate) }}
            </NTag>
          </div>
          <div class="quality-grid">
            <div class="quality-item">
              <div class="quality-label">总数</div>
              <div class="quality-value">{{ formatNumber(item.stat?.total) }}</div>
            </div>
            <div class="quality-item">
              <div class="quality-label">今日</div>
              <div class="quality-value">{{ formatNumber(item.stat?.today) }}</div>
            </div>
            <div class="quality-item">
              <div class="quality-label">成功</div>
              <div class="quality-value">{{ formatNumber(item.stat?.successCount) }}</div>
            </div>
            <div class="quality-item">
              <div class="quality-label">平均耗时</div>
              <div class="quality-value">{{ formatDuration(item.stat, item.unit) }}</div>
            </div>
          </div>
        </NCard>
      </NGridItem>
    </NGrid>

    <!-- 文件 + TTS -->
    <NGrid :x-gap="16" :y-gap="16" :cols="2" responsive="screen" item-responsive>
      <NGridItem span="2 m:1">
        <NCard title="文件存储" :bordered="false" :loading="loading">
          <div v-if="stats?.file" class="file-section">
            <div class="file-kpi">
              <div class="file-kpi-item">
                <div class="file-kpi-label">总文件数</div>
                <div class="file-kpi-value">{{ formatNumber(stats.file.total) }}</div>
              </div>
              <div class="file-kpi-item">
                <div class="file-kpi-label">今日上传</div>
                <div class="file-kpi-value">{{ formatNumber(stats.file.todayUpload) }}</div>
              </div>
              <div class="file-kpi-item">
                <div class="file-kpi-label">总占用</div>
                <div class="file-kpi-value">{{ formatBytes(stats.file.totalSize) }}</div>
              </div>
            </div>
            <Chart v-if="stats.file.byType?.length" :option="fileTypeOption" height="200px" />
            <BrandEmpty size="small" description="暂无数据" v-else />
          </div>
        </NCard>
      </NGridItem>
      <NGridItem span="2 m:1">
        <NCard title="TTS 语音合成" :bordered="false" :loading="loading">
          <div v-if="stats?.tts" class="file-section">
            <div class="file-kpi">
              <div class="file-kpi-item">
                <div class="file-kpi-label">总合成数</div>
                <div class="file-kpi-value">{{ formatNumber(stats.tts.total) }}</div>
              </div>
              <div class="file-kpi-item">
                <div class="file-kpi-label">今日合成</div>
                <div class="file-kpi-value">{{ formatNumber(stats.tts.today) }}</div>
              </div>
              <div class="file-kpi-item">
                <div class="file-kpi-label">总音频大小</div>
                <div class="file-kpi-value">{{ formatBytes(stats.tts.totalSize) }}</div>
              </div>
            </div>
          </div>
          <BrandEmpty size="small" description="暂无数据" v-else />
        </NCard>
      </NGridItem>
    </NGrid>

    <!-- 按模型类型 + 按供应商 -->
    <NGrid :x-gap="16" :y-gap="16" :cols="2" responsive="screen" item-responsive>
      <NGridItem span="2 m:1">
        <NCard title="按模型类型" :bordered="false" :loading="loading">
          <Chart v-if="stats?.aiUsage?.byType?.length" :option="modelTypeOption" height="200px" />
          <BrandEmpty size="small" description="暂无数据" v-else />
        </NCard>
      </NGridItem>
      <NGridItem span="2 m:1">
        <NCard title="按供应商" :bordered="false" :loading="loading">
          <Chart v-if="stats?.aiUsage?.byProvider?.length" :option="providerOption" height="200px" />
          <BrandEmpty size="small" description="暂无数据" v-else />
        </NCard>
      </NGridItem>
    </NGrid>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.welcome-text h2 {
  margin: 0 0 8px;
  font-size: 22px;
}

.welcome-text p {
  margin: 0;
  color: var(--c-text-3);
}

.section-title {
  margin-top: 4px;
}

.stat-card {
  animation: list-in 0.3s ease both;
}

.stat {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-sub {
  margin-top: 8px;
  font-size: 12px;
  color: var(--c-text-3);
}

.kpi-delta {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px 8px;
  border-radius: var(--r-sm);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  margin-left: auto;
}

.kpi-delta.up {
  color: var(--c-success);
  background: var(--c-success-bg);
}

.kpi-delta.down {
  color: var(--c-error);
  background: var(--c-error-bg);
}

.trend-caption {
  margin-top: 10px;
  font-size: 13px;
  color: var(--c-text-2);
  display: flex;
  align-items: center;
  gap: 6px;
}

.trend-caption .up {
  color: var(--c-success);
}

.trend-caption .down {
  color: var(--c-error);
}

.trend-caption b.up {
  color: var(--c-success);
}

.trend-caption b.down {
  color: var(--c-error);
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: var(--r-lg);
  background: var(--c-fill-2);
  flex-shrink: 0;
}

/* AI 生成质量卡 */
.quality-card {
  height: 100%;
  animation: list-in 0.3s ease both;
}

.quality-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.quality-title {
  font-size: 15px;
  font-weight: 600;
  flex: 1;
}

.quality-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.quality-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  background: var(--c-fill-2);
  border-radius: var(--r-md);
}

.quality-label {
  font-size: 12px;
  color: var(--c-text-3);
}

.quality-value {
  font-size: 18px;
  font-weight: 600;
}

/* 文件 + TTS */
.file-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.file-kpi {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.file-kpi-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  background: var(--c-fill-2);
  border-radius: var(--r-md);
}

.file-kpi-label {
  font-size: 12px;
  color: var(--c-text-3);
}

.file-kpi-value {
  font-size: 20px;
  font-weight: 600;
}

@media (max-width: 768px) {
  .file-kpi {
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
  }
  .file-kpi-item {
    padding: 8px;
  }
  .file-kpi-value {
    font-size: 16px;
  }
  .quality-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
  }
  .quality-item {
    padding: 8px 10px;
  }
  .quality-value {
    font-size: 16px;
  }
}

/* 卡片 hover 轻盈提升（尊重减少动效偏好） */
:deep(.n-card) {
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

:deep(.n-card:hover) {
  box-shadow: var(--sh-card);
  transform: translateY(-2px);
}

@media (prefers-reduced-motion: reduce) {
  :deep(.n-card) {
    transition: none;
    transform: none;
  }
}
</style>
