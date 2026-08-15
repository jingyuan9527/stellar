<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts/core'
import type { ECharts, EChartsCoreOption } from 'echarts/core'
import { PieChart, BarChart } from 'echarts/charts'
import { TooltipComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { NButton, NCard, NGrid, NGridItem, NIcon, NStatistic, NTag } from 'naive-ui'
import { DownloadOutline } from '@vicons/ionicons5'
import { exportMonitorReport, getMonitorOverview } from '@/api/monitor'
import type { MonitorOverview } from '@/types/api'

// 按需注册监控页用到的 echarts 组件，缩小该 chunk 体积（pie/bar + tooltip/grid 已覆盖本页全部图表）
echarts.use([PieChart, BarChart, TooltipComponent, GridComponent, CanvasRenderer])

const overview = ref<MonitorOverview | null>(null)
const loading = ref(true)

const heapChartRef = ref<HTMLDivElement>()
const cpuChartRef = ref<HTMLDivElement>()
const diskChartRef = ref<HTMLDivElement>()
const httpChartRef = ref<HTMLDivElement>()
const poolChartRef = ref<HTMLDivElement>()

let heapChart: ECharts | null = null
let cpuChart: ECharts | null = null
let diskChart: ECharts | null = null
let httpChart: ECharts | null = null
let poolChart: ECharts | null = null

let timer: ReturnType<typeof setInterval> | null = null

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

function formatDuration(seconds: number | undefined | null): string {
  const s = seconds ?? 0
  if (s < 60) return s + ' 秒'
  const days = Math.floor(s / 86400)
  const hours = Math.floor((s % 86400) / 3600)
  const mins = Math.floor((s % 3600) / 60)
  if (days > 0) return `${days} 天 ${hours} 时 ${mins} 分`
  if (hours > 0) return `${hours} 时 ${mins} 分`
  return `${mins} 分 ${s % 60} 秒`
}

function formatTime(ms: number | undefined | null): string {
  if (!ms) return '-'
  return new Date(ms).toLocaleString('zh-CN')
}

function formatMs(ms: number | undefined | null): string {
  const v = ms ?? 0
  if (v < 1000) return v + ' ms'
  return (v / 1000).toFixed(2) + ' s'
}

// ===== 派生 =====
const heapPercent = computed(() => {
  const h = overview.value?.jvm
  if (!h || !h.heapMax) return 0
  return Math.min(100, Math.round((h.heapUsed / h.heapMax) * 100))
})

const nonHeapPercent = computed(() => {
  const h = overview.value?.jvm
  if (!h || h.nonHeapMax <= 0) return 0
  return Math.min(100, Math.round((h.nonHeapUsed / h.nonHeapMax) * 100))
})

const diskUsedPercent = computed(() => {
  const s = overview.value?.system
  if (!s || !s.diskTotal) return 0
  const used = s.diskTotal - s.diskFree
  return Math.min(100, Math.round((used / s.diskTotal) * 100))
})

const poolUsedPercent = computed(() => {
  const p = overview.value?.hikariPool
  if (!p || !p.maximumPoolSize) return 0
  return Math.min(100, Math.round(((p.activeConnections + p.pendingConnections) / p.maximumPoolSize) * 100))
})

const healthTagType = computed(() => {
  const st = overview.value?.health?.status
  if (st === 'UP') return 'success'
  if (st === 'DOWN') return 'error'
  return 'warning'
})

const healthTagText = computed(() => overview.value?.health?.status || '未知')

/** JVM 参数值友好化：内存类字节数转可读单位，ThreadStackSize 单位为 KB */
const BYTE_ARGS = new Set([
  'InitialHeapSize',
  'MaxHeapSize',
  'NewSize',
  'MaxNewSize',
  'MetaspaceSize',
  'MaxMetaspaceSize',
  'MaxDirectMemorySize',
])
function formatArgValue(arg: { name: string; value: string }): string {
  if (BYTE_ARGS.has(arg.name)) return formatBytes(Number(arg.value))
  if (arg.name === 'ThreadStackSize') return arg.value + ' KB'
  return arg.value
}

async function downloadReport() {
  try {
    const blob = await exportMonitorReport()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `stellar-monitor-${new Date().toISOString().slice(0, 19).replace(/[-T:]/g, '')}.md`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    // 错误已由拦截器提示
  }
}

// ===== ECharts =====
const RING_TRACK = 'rgba(127, 127, 127, 0.15)'
const COLORS = ['#18a058', '#2080f0', '#f0a020', '#d03050']

function initCharts() {
  if (heapChartRef.value) heapChart = echarts.init(heapChartRef.value)
  if (cpuChartRef.value) cpuChart = echarts.init(cpuChartRef.value)
  if (diskChartRef.value) diskChart = echarts.init(diskChartRef.value)
  if (httpChartRef.value) httpChart = echarts.init(httpChartRef.value)
  if (poolChartRef.value) poolChart = echarts.init(poolChartRef.value)
  window.addEventListener('resize', resizeCharts)
}

function resizeCharts() {
  heapChart?.resize()
  cpuChart?.resize()
  diskChart?.resize()
  httpChart?.resize()
  poolChart?.resize()
}

function ringOption(percent: number, color: string, label: string): EChartsCoreOption {
  return {
    series: [
      {
        type: 'pie',
        radius: ['72%', '90%'],
        avoidLabelOverlap: false,
        label: { show: false },
        silent: true,
        data: [
          { value: percent, itemStyle: { color } },
          { value: Math.max(0, 100 - percent), itemStyle: { color: RING_TRACK } },
        ],
      },
    ],
    graphic: [
      {
        type: 'text',
        left: 'center',
        top: 'center',
        style: { text: `${percent}%`, fontSize: 20, fontWeight: 600, fill: color, align: 'center', verticalAlign: 'middle' },
      },
      {
        type: 'text',
        left: 'center',
        top: '72%',
        style: { text: label, fontSize: 12, fill: '#999', align: 'center' },
      },
    ],
  }
}

function updateCharts() {
  const o = overview.value
  if (!o) return
  heapChart?.setOption(ringOption(heapPercent.value, '#2080f0', '堆内存'), true)
  const cpu = o.system.processCpuUsage
  const cpuPct = cpu < 0 ? 0 : Math.min(100, Math.round(cpu * 100))
  cpuChart?.setOption(ringOption(cpuPct, '#18a058', '进程 CPU'), true)
  diskChart?.setOption(ringOption(diskUsedPercent.value, '#f0a020', '磁盘使用率'), true)

  httpChart?.setOption(
    {
      tooltip: { trigger: 'axis' },
      grid: { left: 48, right: 16, top: 24, bottom: 28 },
      xAxis: { type: 'category', data: ['2xx 成功', '4xx 客户端', '5xx 服务端'], axisLabel: { fontSize: 12 } },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        {
          type: 'bar',
          barWidth: 36,
          data: [
            { value: o.http.status2xx, itemStyle: { color: '#18a058' } },
            { value: o.http.status4xx, itemStyle: { color: '#f0a020' } },
            { value: o.http.status5xx, itemStyle: { color: '#d03050' } },
          ],
        },
      ],
    },
    true,
  )

  poolChart?.setOption(
    {
      tooltip: { trigger: 'axis' },
      grid: { left: 48, right: 16, top: 24, bottom: 28 },
      xAxis: { type: 'category', data: ['空闲', '活跃', '等待'], axisLabel: { fontSize: 12 } },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        {
          type: 'bar',
          barWidth: 36,
          data: [
            { value: o.hikariPool.idleConnections, itemStyle: { color: '#18a058' } },
            { value: o.hikariPool.activeConnections, itemStyle: { color: '#2080f0' } },
            {
              value: o.hikariPool.pendingConnections,
              itemStyle: { color: o.hikariPool.pendingConnections > 0 ? '#d03050' : '#18a058' },
            },
          ],
        },
      ],
    },
    true,
  )
}

async function loadData() {
  try {
    overview.value = await getMonitorOverview()
  } catch {
    // 错误已由拦截器提示，轮询继续
  } finally {
    loading.value = false
  }
  updateCharts()
}

onMounted(async () => {
  await loadData()
  await nextTick()
  initCharts()
  updateCharts()
  timer = setInterval(loadData, 4000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  window.removeEventListener('resize', resizeCharts)
  heapChart?.dispose()
  cpuChart?.dispose()
  diskChart?.dispose()
  httpChart?.dispose()
  poolChart?.dispose()
})
</script>

<template>
  <div class="monitor">
    <!-- 应用信息 -->
    <div class="section-row">
      <div class="section-title">应用信息</div>
      <NButton size="small" secondary type="primary" @click="downloadReport">
        <template #icon><NIcon><DownloadOutline /></NIcon></template>
        导出报告
      </NButton>
    </div>
    <NGrid :x-gap="16" :y-gap="16" :cols="4" responsive="screen" item-responsive>
      <NGridItem span="4 m:2 l:1">
        <NCard :bordered="false" class="stat-card">
          <NStatistic label="运行时长" :value="formatDuration(overview?.app.upTimeSeconds)" />
        </NCard>
      </NGridItem>
      <NGridItem span="4 m:2 l:1">
        <NCard :bordered="false" class="stat-card">
          <NStatistic label="启动耗时" :value="formatMs(overview?.app.startCostMs)" />
        </NCard>
      </NGridItem>
      <NGridItem span="4 m:2 l:1">
        <NCard :bordered="false" class="stat-card">
          <NStatistic label="启动时间" :value="formatTime(overview?.app.startTimeMillis)" />
        </NCard>
      </NGridItem>
      <NGridItem span="4 m:2 l:1">
        <NCard :bordered="false" class="stat-card">
          <div class="health-head">
            <NStatistic label="整体健康" :value="healthTagText" />
            <NTag :type="healthTagType" :bordered="false" round size="small">{{ healthTagText }}</NTag>
          </div>
        </NCard>
      </NGridItem>
    </NGrid>

    <!-- 健康组件 -->
    <div class="section-title">健康组件</div>
    <NCard :bordered="false">
      <div v-if="overview?.health.components && Object.keys(overview.health.components).length" class="health-list">
        <div v-for="(status, name) in overview.health.components" :key="name" class="health-item">
          <span class="health-name">{{ name }}</span>
          <NTag
            size="small"
            :bordered="false"
            round
            :type="status === 'UP' ? 'success' : status === 'DOWN' ? 'error' : 'warning'"
          >
            {{ status }}
          </NTag>
        </div>
      </div>
      <div v-else class="muted">暂无健康组件</div>
    </NCard>

    <!-- JVM -->
    <div class="section-title">JVM 内存</div>
    <NGrid :x-gap="16" :y-gap="16" :cols="2" responsive="screen" item-responsive>
      <NGridItem span="2 m:1">
        <NCard title="堆内存" :bordered="false">
          <div class="chart-wrap"><div ref="heapChartRef" class="chart"></div></div>
          <div class="sub-stats">
            <div class="sub-stat">
              <div class="sub-label">已用</div>
              <div class="sub-value">{{ formatBytes(overview?.jvm.heapUsed) }}</div>
            </div>
            <div class="sub-stat">
              <div class="sub-label">最大</div>
              <div class="sub-value">{{ formatBytes(overview?.jvm.heapMax) }}</div>
            </div>
            <div class="sub-stat">
              <div class="sub-label">非堆已用</div>
              <div class="sub-value">{{ formatBytes(overview?.jvm.nonHeapUsed) }}</div>
            </div>
          </div>
        </NCard>
      </NGridItem>
      <NGridItem span="2 m:1">
        <NCard title="线程与类加载" :bordered="false">
          <div class="kv-grid">
            <div class="kv-item">
              <div class="kv-label">活跃线程</div>
              <div class="kv-value">{{ formatNumber(overview?.jvm.threadActive) }}</div>
            </div>
            <div class="kv-item">
              <div class="kv-label">守护线程</div>
              <div class="kv-value">{{ formatNumber(overview?.jvm.threadDaemon) }}</div>
            </div>
            <div class="kv-item">
              <div class="kv-label">线程峰值</div>
              <div class="kv-value">{{ formatNumber(overview?.jvm.threadPeak) }}</div>
            </div>
            <div class="kv-item">
              <div class="kv-label">已加载类</div>
              <div class="kv-value">{{ formatNumber(overview?.jvm.loadedClasses) }}</div>
            </div>
          </div>
        </NCard>
      </NGridItem>
    </NGrid>

    <!-- GC -->
    <div class="section-title">GC 垃圾回收</div>
    <NGrid :x-gap="16" :y-gap="16" :cols="2" responsive="screen" item-responsive>
      <NGridItem span="2 m:1">
        <NCard title="汇总" :bordered="false">
          <div class="kv-grid">
            <div class="kv-item">
              <div class="kv-label">Young GC 次数</div>
              <div class="kv-value">{{ formatNumber(overview?.jvm.youngGcCount) }}</div>
            </div>
            <div class="kv-item">
              <div class="kv-label">Young GC 耗时</div>
              <div class="kv-value">{{ formatMs(overview?.jvm.youngGcTimeMs) }}</div>
            </div>
            <div class="kv-item">
              <div class="kv-label">Full GC 次数</div>
              <div class="kv-value" :class="{ danger: (overview?.jvm.fullGcCount ?? 0) > 0 }">
                {{ formatNumber(overview?.jvm.fullGcCount) }}
              </div>
            </div>
            <div class="kv-item">
              <div class="kv-label">Full GC 耗时</div>
              <div class="kv-value">{{ formatMs(overview?.jvm.fullGcTimeMs) }}</div>
            </div>
          </div>
        </NCard>
      </NGridItem>
      <NGridItem span="2 m:1">
        <NCard title="收集器明细" :bordered="false">
          <div v-if="overview?.jvm.gcCollectors?.length" class="gc-table">
            <div class="gc-row gc-head">
              <span class="gc-name">收集器</span>
              <span>次数</span>
              <span>累计耗时</span>
              <span>平均单次</span>
            </div>
            <div v-for="c in overview.jvm.gcCollectors" :key="c.name" class="gc-row">
              <span class="gc-name">{{ c.name }}</span>
              <span>{{ formatNumber(c.count) }}</span>
              <span>{{ formatMs(c.timeMs) }}</span>
              <span>{{ formatMs(c.avgTimeMs) }}</span>
            </div>
          </div>
          <div v-else class="muted">无 GC 数据</div>
        </NCard>
      </NGridItem>
    </NGrid>

    <!-- JVM 生效参数 -->
    <div class="section-title">JVM 生效参数</div>
    <NCard :bordered="false">
      <div v-if="overview?.jvm.keyJvmArgs?.length" class="jvm-args">
        <div v-for="a in overview.jvm.keyJvmArgs" :key="a.name" class="jvm-arg">
          <span class="jvm-arg-name">{{ a.name }}</span>
          <span class="jvm-arg-value">{{ formatArgValue(a) }}</span>
        </div>
      </div>
      <div v-else class="muted">当前 JVM 不支持读取参数（需 HotSpot）</div>
    </NCard>

    <!-- 系统 -->
    <div class="section-title">系统资源</div>
    <NGrid :x-gap="16" :y-gap="16" :cols="3" responsive="screen" item-responsive>
      <NGridItem span="3 m:1">
        <NCard title="进程 CPU" :bordered="false">
          <div class="chart-wrap"><div ref="cpuChartRef" class="chart"></div></div>
          <div class="muted center">
            系统 CPU：
            {{ (overview?.system.systemCpuUsage ?? 0) < 0 ? '未知' : ((overview?.system.systemCpuUsage ?? 0) * 100).toFixed(1) + '%' }}
          </div>
        </NCard>
      </NGridItem>
      <NGridItem span="3 m:1">
        <NCard title="磁盘" :bordered="false">
          <div class="chart-wrap"><div ref="diskChartRef" class="chart"></div></div>
          <div class="sub-stats">
            <div class="sub-stat">
              <div class="sub-label">总量</div>
              <div class="sub-value">{{ formatBytes(overview?.system.diskTotal) }}</div>
            </div>
            <div class="sub-stat">
              <div class="sub-label">剩余</div>
              <div class="sub-value">{{ formatBytes(overview?.system.diskFree) }}</div>
            </div>
          </div>
        </NCard>
      </NGridItem>
      <NGridItem span="3 m:1">
        <NCard title="文件句柄" :bordered="false">
          <div class="center pad-top">
            <div v-if="overview?.system.fileOpenDescriptors != null" class="fd-value">
              {{ formatNumber(overview.system.fileOpenDescriptors) }}
              <span class="fd-max">/ {{ formatNumber(overview.system.fileMaxDescriptors) }}</span>
            </div>
            <div v-else class="muted">当前平台不支持（Linux 生产环境可用）</div>
          </div>
        </NCard>
      </NGridItem>
    </NGrid>

    <!-- HTTP -->
    <div class="section-title">HTTP 请求</div>
    <NGrid :x-gap="16" :y-gap="16" :cols="4" responsive="screen" item-responsive>
      <NGridItem span="4 l:3">
        <NCard title="状态码分布（累计）" :bordered="false">
          <div class="chart-wrap tall"><div ref="httpChartRef" class="chart"></div></div>
        </NCard>
      </NGridItem>
      <NGridItem span="4 l:1">
        <NCard title="请求指标" :bordered="false">
          <div class="kv-grid">
            <div class="kv-item">
              <div class="kv-label">请求总数</div>
              <div class="kv-value">{{ formatNumber(overview?.http.totalRequests) }}</div>
            </div>
            <div class="kv-item">
              <div class="kv-label">最大耗时</div>
              <div class="kv-value">{{ formatMs(overview?.http.maxCostMs) }}</div>
            </div>
            <div class="kv-item">
              <div class="kv-label">平均耗时</div>
              <div class="kv-value">{{ formatMs(overview?.http.avgCostMs) }}</div>
            </div>
            <div class="kv-item">
              <div class="kv-label">活跃请求</div>
              <div class="kv-value" :class="{ danger: (overview?.http.activeRequests ?? 0) >= 20 }">
                {{ formatNumber(overview?.http.activeRequests) }}
              </div>
            </div>
          </div>
        </NCard>
      </NGridItem>
    </NGrid>

    <!-- 连接池 -->
    <div class="section-title">数据库连接池（HikariCP）</div>
    <NGrid :x-gap="16" :y-gap="16" :cols="2" responsive="screen" item-responsive>
      <NGridItem span="2 m:1">
        <NCard title="连接分布" :bordered="false">
          <div class="chart-wrap tall"><div ref="poolChartRef" class="chart"></div></div>
        </NCard>
      </NGridItem>
      <NGridItem span="2 m:1">
        <NCard title="池容量" :bordered="false">
          <div class="kv-grid">
            <div class="kv-item">
              <div class="kv-label">空闲连接</div>
              <div class="kv-value">{{ formatNumber(overview?.hikariPool.idleConnections) }}</div>
            </div>
            <div class="kv-item">
              <div class="kv-label">活跃连接</div>
              <div class="kv-value">{{ formatNumber(overview?.hikariPool.activeConnections) }}</div>
            </div>
            <div class="kv-item">
              <div class="kv-label">等待队列</div>
              <div class="kv-value" :class="{ danger: (overview?.hikariPool.pendingConnections ?? 0) > 0 }">
                {{ formatNumber(overview?.hikariPool.pendingConnections) }}
              </div>
            </div>
            <div class="kv-item">
              <div class="kv-label">池上限</div>
              <div class="kv-value">{{ formatNumber(overview?.hikariPool.maximumPoolSize) }}</div>
            </div>
          </div>
          <div v-if="(overview?.hikariPool.pendingConnections ?? 0) > 0" class="alert">
            等待队列大于 0：连接池可能打满，请求在排队，请关注！
          </div>
        </NCard>
      </NGridItem>
    </NGrid>
  </div>
</template>

<style scoped>
.monitor {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #666;
  padding-left: 4px;
  margin-top: 4px;
}

.section-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 4px;
}

.jvm-args {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.jvm-arg {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: rgba(127, 127, 127, 0.06);
  border-radius: 8px;
  font-size: 13px;
}

.jvm-arg-name {
  font-weight: 500;
  color: #888;
  min-width: 170px;
}

.jvm-arg-value {
  font-family: var(--mono-font, Consolas, monospace);
  font-size: 13px;
  word-break: break-all;
}

.stat-card {
  height: 100%;
}

.health-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.health-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.health-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: rgba(127, 127, 127, 0.06);
  border-radius: 8px;
  font-size: 13px;
}

.health-name {
  font-weight: 500;
  min-width: 60px;
}

.chart {
  width: 100%;
  height: 100%;
}

.chart-wrap {
  height: 200px;
}

.chart-wrap.tall {
  height: 240px;
}

.sub-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 8px;
}

.sub-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.sub-label {
  font-size: 12px;
  color: #888;
}

.sub-value {
  font-size: 15px;
  font-weight: 600;
}

.kv-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.kv-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  background: rgba(127, 127, 127, 0.06);
  border-radius: 8px;
}

.kv-label {
  font-size: 12px;
  color: #888;
}

.kv-value {
  font-size: 18px;
  font-weight: 600;
}

.danger {
  color: #d03050;
}

.alert {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(208, 48, 80, 0.1);
  color: #d03050;
  font-size: 13px;
}

.gc-table {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.gc-row {
  display: grid;
  grid-template-columns: 1.6fr 0.8fr 1fr 1fr;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 13px;
}

.gc-row:nth-child(even) {
  background: rgba(127, 127, 127, 0.06);
}

.gc-head {
  font-size: 12px;
  color: #888;
}

.gc-name {
  font-weight: 500;
}

.muted {
  color: #999;
  font-size: 13px;
}

.center {
  text-align: center;
}

.pad-top {
  padding-top: 24px;
}

.fd-value {
  font-size: 28px;
  font-weight: 600;
}

.fd-max {
  font-size: 14px;
  color: #999;
  font-weight: 400;
}

@media (max-width: 768px) {
  .chart-wrap {
    height: 160px;
  }
  .chart-wrap.tall {
    height: 180px;
  }
  .kv-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
  }
  .kv-item {
    padding: 8px 10px;
  }
  .kv-value {
    font-size: 15px;
  }
  .sub-stats {
    gap: 6px;
  }
  .jvm-args {
    grid-template-columns: 1fr;
  }
}
</style>