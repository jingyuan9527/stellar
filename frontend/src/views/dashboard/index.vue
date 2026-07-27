<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { NCard, NGrid, NGridItem, NIcon, NStatistic, NEmpty, NTag } from 'naive-ui'
import { useAuthStore } from '@/store/auth'
import { iconMap } from '@/utils/icons'
import { getDashboardStats } from '@/api/dashboard'
import type { DashboardStats, DashboardTaskStat } from '@/types/api'

const authStore = useAuthStore()
const stats = ref<DashboardStats | null>(null)

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const aiKpiCards = computed(() => [
  { label: '总 Token 消耗', value: stats.value?.aiUsage?.totalTokens ?? 0, icon: 'sparkles', color: '#18a058' },
  { label: '今日 Token', value: stats.value?.aiUsage?.todayTokens ?? 0, icon: 'info', color: '#2080f0' },
  { label: '总调用次数', value: stats.value?.aiUsage?.totalCalls ?? 0, icon: 'grid', color: '#f0a020' },
  { label: '今日调用', value: stats.value?.aiUsage?.todayCalls ?? 0, icon: 'list', color: '#d03050' },
])

const todayKpiCards = computed(() => [
  { label: '今日文案生成', value: stats.value?.textGen?.today ?? 0, icon: 'log', color: '#18a058' },
  { label: '今日图片生成', value: stats.value?.imageTask?.today ?? 0, icon: 'image', color: '#2080f0' },
  { label: '今日视频生成', value: stats.value?.videoTask?.today ?? 0, icon: 'film', color: '#f0a020' },
  { label: '今日 TTS 合成', value: stats.value?.tts?.today ?? 0, icon: 'volume', color: '#d03050' },
])

function renderStatIcon(name: string, color: string) {
  const Icon = iconMap[name]
  if (!Icon) return null
  return h(NIcon, { size: 24, color }, { default: () => h(Icon) })
}

// ===== 近 7 日趋势 =====
const maxTrendTokens = computed(() => {
  if (!stats.value?.aiUsage?.dailyTrend?.length) return 1
  return Math.max(1, ...stats.value.aiUsage.dailyTrend.map((d) => d.tokens))
})
const maxTrendCalls = computed(() => {
  if (!stats.value?.aiUsage?.dailyTrend?.length) return 1
  return Math.max(1, ...stats.value.aiUsage.dailyTrend.map((d) => d.calls))
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
const maxTypeTokens = computed(() => {
  if (!stats.value?.aiUsage?.byType?.length) return 1
  return Math.max(1, ...stats.value.aiUsage.byType.map((t) => t.tokens))
})
const maxProviderTokens = computed(() => {
  if (!stats.value?.aiUsage?.byProvider?.length) return 1
  return Math.max(1, ...stats.value.aiUsage.byProvider.map((p) => p.tokens))
})

// ===== AI 生成质量（文案/图片/视频）=====
const taskQualityCards = computed(() => [
  { title: '文案生成', icon: 'log', color: '#18a058', stat: stats.value?.textGen, unit: 'ms' as const },
  { title: '图片生成', icon: 'image', color: '#2080f0', stat: stats.value?.imageTask, unit: 's' as const },
  { title: '视频生成', icon: 'film', color: '#f0a020', stat: stats.value?.videoTask, unit: 's' as const },
])

// ===== 文件存储 =====
const maxFileTypeCount = computed(() => {
  if (!stats.value?.file?.byType?.length) return 1
  return Math.max(1, ...stats.value.file.byType.map((t) => t.count))
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
  try {
    stats.value = await getDashboardStats()
  } catch {
    // 错误已由拦截器提示
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

    <!-- AI 调用概览 -->
    <div class="section-title">AI 调用概览</div>
    <NGrid :x-gap="16" :y-gap="16" :cols="4" responsive="screen" item-responsive>
      <NGridItem v-for="item in aiKpiCards" :key="item.label" span="4 m:2 l:1">
        <NCard :bordered="false" class="stat-card">
          <div class="stat">
            <div class="stat-icon" :style="{ background: item.color + '1a' }">
              <component :is="renderStatIcon(item.icon, item.color)" />
            </div>
            <NStatistic :label="item.label" :value="formatNumber(item.value)" />
          </div>
        </NCard>
      </NGridItem>
    </NGrid>

    <!-- 今日生成 -->
    <div class="section-title">今日生成</div>
    <NGrid :x-gap="16" :y-gap="16" :cols="4" responsive="screen" item-responsive>
      <NGridItem v-for="item in todayKpiCards" :key="item.label" span="4 m:2 l:1">
        <NCard :bordered="false" class="stat-card">
          <div class="stat">
            <div class="stat-icon" :style="{ background: item.color + '1a' }">
              <component :is="renderStatIcon(item.icon, item.color)" />
            </div>
            <NStatistic :label="item.label" :value="formatNumber(item.value)" />
          </div>
        </NCard>
      </NGridItem>
    </NGrid>

    <!-- 近 7 日趋势 -->
    <NCard title="近 7 日 AI 调用趋势" :bordered="false">
      <div v-if="stats?.aiUsage?.dailyTrend?.length" class="trend">
        <div v-for="d in stats.aiUsage.dailyTrend" :key="d.date" class="trend-item">
          <div class="trend-bar-wrap">
            <div
              class="trend-bar tokens-bar"
              :style="{ height: (d.tokens / maxTrendTokens * 100) + '%' }"
              :title="`Token: ${d.tokens}`"
            ></div>
            <div
              class="trend-bar calls-bar"
              :style="{ height: (d.calls / maxTrendCalls * 100) + '%' }"
              :title="`调用: ${d.calls}`"
            ></div>
          </div>
          <div class="trend-label">{{ d.date.slice(5) }}</div>
          <div class="trend-tokens">{{ formatNumber(d.tokens) }}</div>
          <div class="trend-calls">{{ d.calls }} 次</div>
        </div>
      </div>
      <NEmpty v-else description="暂无数据" />
    </NCard>

    <!-- AI 生成质量 -->
    <div class="section-title">AI 生成质量</div>
    <NGrid :x-gap="16" :y-gap="16" :cols="3" responsive="screen" item-responsive>
      <NGridItem v-for="item in taskQualityCards" :key="item.title" span="3 m:1">
        <NCard :bordered="false" class="quality-card">
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
        <NCard title="文件存储" :bordered="false">
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
            <div v-if="stats.file.byType?.length" class="group-list">
              <div v-for="t in stats.file.byType" :key="t.type" class="group-row">
                <div class="group-head">
                  <NTag size="small" :bordered="false" :type="t.type === 'image' ? 'success' : t.type === 'audio' ? 'warning' : 'default'">
                    {{ fileTypeLabels[t.type] ?? t.type }}
                  </NTag>
                  <span class="group-tokens">{{ formatNumber(t.count) }} 个</span>
                  <span class="group-calls">{{ formatBytes(t.size) }}</span>
                </div>
                <div class="group-bar-wrap">
                  <div class="group-bar file-bar" :style="{ width: (t.count / maxFileTypeCount * 100) + '%' }"></div>
                </div>
              </div>
            </div>
          </div>
          <NEmpty v-else description="暂无数据" />
        </NCard>
      </NGridItem>
      <NGridItem span="2 m:1">
        <NCard title="TTS 语音合成" :bordered="false">
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
          <NEmpty v-else description="暂无数据" />
        </NCard>
      </NGridItem>
    </NGrid>

    <!-- 按模型类型 + 按供应商 -->
    <NGrid :x-gap="16" :y-gap="16" :cols="2" responsive="screen" item-responsive>
      <NGridItem span="2 m:1">
        <NCard title="按模型类型" :bordered="false">
          <div v-if="stats?.aiUsage?.byType?.length" class="group-list">
            <div v-for="t in stats.aiUsage.byType" :key="t.modelType" class="group-row">
              <div class="group-head">
                <NTag size="small" :bordered="false" :type="t.modelType === 'TEXT' ? 'success' : 'warning'">
                  {{ typeLabel(t.modelType) }}
                </NTag>
                <span class="group-tokens">{{ formatNumber(t.tokens) }} tokens</span>
                <span class="group-calls">{{ formatNumber(t.calls) }} 次</span>
              </div>
              <div class="group-bar-wrap">
                <div class="group-bar" :style="{ width: (t.tokens / maxTypeTokens * 100) + '%' }"></div>
              </div>
            </div>
          </div>
          <NEmpty v-else description="暂无数据" />
        </NCard>
      </NGridItem>
      <NGridItem span="2 m:1">
        <NCard title="按供应商" :bordered="false">
          <div v-if="stats?.aiUsage?.byProvider?.length" class="group-list">
            <div v-for="p in stats.aiUsage.byProvider" :key="p.providerId" class="group-row">
              <div class="group-head">
                <span class="group-name">{{ p.providerName || '未知' }}</span>
                <span class="group-tokens">{{ formatNumber(p.tokens) }} tokens</span>
                <span class="group-calls">{{ formatNumber(p.calls) }} 次</span>
              </div>
              <div class="group-bar-wrap">
                <div class="group-bar provider-bar" :style="{ width: (p.tokens / maxProviderTokens * 100) + '%' }"></div>
              </div>
            </div>
          </div>
          <NEmpty v-else description="暂无数据" />
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
  color: #888;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #666;
  padding-left: 4px;
  margin-top: 4px;
}

.stat {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: rgba(127, 127, 127, 0.1);
  flex-shrink: 0;
}

/* 趋势图 */
.trend {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  height: 220px;
  padding: 0 4px;
}

.trend-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  height: 100%;
}

.trend-bar-wrap {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 4px;
}

.trend-bar {
  width: 40%;
  max-width: 28px;
  border-radius: 6px 6px 0 0;
  min-height: 2px;
  transition: height 0.3s;
}

.tokens-bar {
  background: var(--primary-color, #18a058);
}

.calls-bar {
  background: #2080f0;
  opacity: 0.6;
}

.trend-label {
  font-size: 12px;
  opacity: 0.6;
}

.trend-tokens {
  font-size: 12px;
  font-weight: 600;
}

.trend-calls {
  font-size: 11px;
  opacity: 0.5;
}

/* AI 生成质量卡 */
.quality-card {
  height: 100%;
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
  background: rgba(127, 127, 127, 0.06);
  border-radius: 8px;
}

.quality-label {
  font-size: 12px;
  color: #888;
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
  background: rgba(127, 127, 127, 0.06);
  border-radius: 8px;
}

.file-kpi-label {
  font-size: 12px;
  color: #888;
}

.file-kpi-value {
  font-size: 20px;
  font-weight: 600;
}

/* 分组条形图 */
.group-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.group-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.group-head {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}

.group-name {
  font-weight: 500;
  min-width: 80px;
}

.group-tokens {
  opacity: 0.8;
}

.group-calls {
  margin-left: auto;
  opacity: 0.5;
  font-size: 12px;
}

.group-bar-wrap {
  width: 100%;
  height: 6px;
  border-radius: 3px;
  background: rgba(127, 127, 127, 0.12);
  overflow: hidden;
}

.group-bar {
  height: 100%;
  background: var(--primary-color, #18a058);
  border-radius: 3px;
  transition: width 0.3s;
  min-width: 2px;
}

.file-bar {
  background: #18a058;
}

.provider-bar {
  background: #2080f0;
}

@media (max-width: 768px) {
  .trend {
    height: 160px;
    gap: 6px;
  }
  .trend-label,
  .trend-tokens,
  .trend-calls {
    font-size: 10px;
  }
  .trend-bar {
    max-width: 18px;
  }
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
</style>
