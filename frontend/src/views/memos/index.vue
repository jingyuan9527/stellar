<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import {
  NCard, NForm, NFormItem, NInput, NButton, NSpace, NDataTable, NTag,
  NDrawer, NDrawerContent, NDescriptions, NDescriptionsItem, NIcon, NSelect,
  NAlert, NDropdown, NModal, NPagination, NCheckbox, useMessage,
} from 'naive-ui'
import type { DataTableColumns, SelectOption, DropdownOption } from 'naive-ui'
import BrandEmpty from '@/components/BrandEmpty.vue'
import {
  getMemosConfig, saveMemosConfig, pullMemos, tagMemos, pushMemosTags,
  getMemosPage, getMemosStats, getMemosWebhookConfig, saveMemosWebhookSecret,
  rebuildMemosRag, getMemosRagStatus, getMemosSyncLogPage, getLatestMemosSyncLog,
} from '@/api/memos'
import { getAiModelsByType } from '@/api/ai'
import type { MemosConfig, MemosNote, MemosStats, MemosSyncLog, AiModel } from '@/types/api'
import { iconMap } from '@/utils/icons'
import { formatTime } from '@/utils/format'
import { useIsMobile } from '@/composables/useBreakpoint'

const message = useMessage()
const isMobile = useIsMobile()
const drawerWidth = computed(() => (isMobile.value ? '100%' : 660))
// 撑满视口：header 56px + 多标签页 40px + 内容区上下 padding（桌面 16×2 / 移动 12×2）
// 不用 height:100% 链（NLayout 滚动容器高度链不可靠），直接按视口计算，表格内部滚动
const pageHeight = computed(() => (isMobile.value ? 'calc(100vh - 120px)' : 'calc(100vh - 128px)'))

// 详情原文 Markdown 渲染（html=false 防 XSS，linkify 自动识别裸链接）
const markdown = new MarkdownIt({ html: false, linkify: true })

function renderMarkdown(content: string) {
  return markdown.render(content || '')
}

/** 内容预览是否含图片/链接（Memos 笔记常见 ![图] 或裸 URL） */
function hasContentImage(content: string) {
  return content.includes('![')
}
function hasContentLink(content: string) {
  return /https?:\/\//.test(content)
}

const config = reactive<MemosConfig & { token: string }>({
  baseUrl: '',
  tokenConfigured: false,
  token: '',
  promptTemplate: '',
})
const configLoading = ref(false)

async function loadConfig() {
  configLoading.value = true
  try {
    const c = await getMemosConfig()
    config.baseUrl = c.baseUrl
    config.tokenConfigured = c.tokenConfigured
    config.promptTemplate = c.promptTemplate
  } finally {
    configLoading.value = false
  }
}

async function handleSaveConfig() {
  await saveMemosConfig({
    baseUrl: config.baseUrl || undefined,
    token: config.token || undefined,
    promptTemplate: config.promptTemplate,
  })
  message.success('配置已保存')
  config.token = ''
  await loadConfig()
}

// ===== Webhook 配置 =====

const webhookUrl = ref('')
const webhookSecretConfigured = ref(false)
const webhookSecret = ref('')
const webhookSaving = ref(false)

function buildWebhookUrl() {
  return `${window.location.origin}/memos/webhook`
}

async function loadWebhookConfig() {
  webhookUrl.value = buildWebhookUrl()
  const c = await getMemosWebhookConfig()
  webhookSecretConfigured.value = c.secretConfigured
}

async function handleSaveWebhookSecret() {
  if (!webhookSecret.value.trim()) return
  webhookSaving.value = true
  try {
    await saveMemosWebhookSecret(webhookSecret.value.trim())
    message.success('Webhook 密钥已保存')
    webhookSecret.value = ''
    await loadWebhookConfig()
  } finally {
    webhookSaving.value = false
  }
}

async function copyWebhookUrl() {
  try {
    await navigator.clipboard.writeText(webhookUrl.value)
    message.success('已复制回调地址')
  } catch {
    message.error('复制失败')
  }
}

// ===== 同步状态（定时/手动「立即同步」记录，保留最近 3 天） =====

const latestSync = ref<MemosSyncLog | null>(null)
const syncLogs = ref<MemosSyncLog[]>([])
const syncLogTotal = ref(0)
const syncLogLoading = ref(false)
// 明细须开抽屉看，避免展开表格把笔记列表顶出视口
const syncLogDrawerShow = ref(false)
const syncLogQuery = reactive({ pageNum: 1, pageSize: 10 })
const syncLogPagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  onChange: (page: number) => {
    syncLogPagination.page = page
    syncLogQuery.pageNum = page
    loadSyncLogs()
  },
  onUpdatePageSize: (size: number) => {
    syncLogPagination.pageSize = size
    syncLogQuery.pageSize = size
    syncLogPagination.page = 1
    syncLogQuery.pageNum = 1
    loadSyncLogs()
  },
})

const syncStatusMeta: Record<string, { type: 'success' | 'warning' | 'error' | 'default'; label: string }> = {
  success: { type: 'success', label: '成功' },
  partial: { type: 'warning', label: '部分失败' },
  failed: { type: 'error', label: '失败' },
  skipped: { type: 'default', label: '未配置跳过' },
}
const syncTriggerLabel: Record<string, string> = { scheduled: '定时', manual: '手动' }

/** 相对时间（x分钟/小时前），超过 24h 回退完整时间 */
function relativeTime(input?: string | null) {
  if (!input) return ''
  const d = new Date(String(input).replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return ''
  const diff = Date.now() - d.getTime()
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min} 分钟前`
  const h = Math.floor(min / 60)
  if (h < 24) return `${h} 小时前`
  return formatTime(input)
}

function formatDuration(ms: number | null) {
  if (ms == null) return '-'
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`
}

async function loadLatestSync() {
  try {
    latestSync.value = await getLatestMemosSyncLog()
  } catch {
    // 错误已由拦截器提示
  }
}

async function loadSyncLogs() {
  syncLogLoading.value = true
  try {
    const r = await getMemosSyncLogPage(syncLogQuery)
    syncLogs.value = r.records
    syncLogTotal.value = r.total
    syncLogPagination.itemCount = r.total
  } finally {
    syncLogLoading.value = false
  }
}

function openSyncLogDrawer() {
  syncLogDrawerShow.value = true
  loadSyncLogs()
}

async function loadSyncStatus() {
  await Promise.all([
    loadLatestSync(),
    loadSyncLogs(),
  ])
}

const syncLogColumns: DataTableColumns<MemosSyncLog> = [
  { title: '时间', key: 'createTime', width: 160, render: (row) => formatTime(row.createTime) },
  {
    title: '触发', key: 'triggerType', width: 70,
    render: (row) => syncTriggerLabel[row.triggerType] ?? row.triggerType,
  },
  {
    title: '状态', key: 'status', width: 100,
    render: (row) => {
      const meta = syncStatusMeta[row.status] ?? { type: 'default' as const, label: row.status }
      return h(NTag, { size: 'small', type: meta.type }, { default: () => meta.label })
    },
  },
  { title: '新增', key: 'created', width: 55 },
  { title: '更新', key: 'updated', width: 55 },
  { title: '标记删除', key: 'markedDeleted', width: 70 },
  { title: '失败', key: 'errors', width: 55 },
  { title: '耗时', key: 'durationMs', width: 80, render: (row) => formatDuration(row.durationMs) },
  {
    title: '详情', key: 'errorMessage', minWidth: 140, ellipsis: { tooltip: true },
    render: (row) => row.errorMessage || '-',
  },
]

// ===== 动作按钮 =====

const pulling = ref(false)
const tagging = ref(false)
const pushing = ref(false)
const rebuilding = ref(false)

// 设置抽屉 / AI 打标签弹窗
const settingShow = ref(false)
const tagShow = ref(false)

function openTagModal() {
  if (!checkedKeys.value.length) return
  tagShow.value = true
}

async function confirmTag() {
  tagShow.value = false
  await handleTag()
}

async function handlePull() {
  pulling.value = true
  try {
    const r = await pullMemos()
    message.success(`同步完成：新增 ${r.created}，更新 ${r.updated}，标记删除 ${r.markedDeleted}，失败 ${r.errors}`)
    await Promise.all([loadData(), loadStats(), loadLatestSync(), loadSyncLogs()])
  } finally {
    pulling.value = false
  }
}

async function handleTag() {
  if (!checkedKeys.value.length) return
  tagging.value = true
  try {
    const r = await tagMemos(checkedKeys.value, selectedModelId.value || undefined)
    const parts = [`打标成功 ${r.success}`]
    if (r.pushSuccess) parts.push(`自动写回 ${r.pushSuccess}`)
    if (r.pushFailed) parts.push(`写回失败 ${r.pushFailed}（可稍后手动重试）`)
    if (r.failed) parts.push(`打标失败 ${r.failed}`)
    if (r.skipped) parts.push(`跳过 ${r.skipped}`)
    message.success(`打标签完成：${parts.join('，')}`)
    checkedKeys.value = []
    await Promise.all([loadData(), loadStats()])
  } finally {
    tagging.value = false
  }
}

// ===== AI 模型选择（供应商 → 模型 联动） =====

const textModels = ref<AiModel[]>([])
const selectedProviderId = ref<number | null>(null)
const selectedModelId = ref<number | null>(null)
const modelLoading = ref(false)

const providerOptions = computed<SelectOption[]>(() => {
  const seen = new Map<number, string>()
  for (const m of textModels.value) {
    if (!seen.has(m.providerId)) seen.set(m.providerId, m.providerName || `供应商#${m.providerId}`)
  }
  return [...seen.entries()].map(([id, label]) => ({ label, value: id }))
})

const modelOptions = computed<SelectOption[]>(() =>
  textModels.value
    .filter((m) => m.providerId === selectedProviderId.value)
    .map((m) => ({ label: m.isDefault === 1 ? `${m.model}（默认）` : m.model, value: m.id })),
)

async function loadTextModels() {
  modelLoading.value = true
  try {
    textModels.value = await getAiModelsByType('TEXT')
    const def = textModels.value.find((m) => m.isDefault === 1) ?? textModels.value[0]
    if (def) {
      selectedProviderId.value = def.providerId
      selectedModelId.value = def.id
    } else {
      selectedProviderId.value = null
      selectedModelId.value = null
    }
  } finally {
    modelLoading.value = false
  }
}

function handleProviderChange() {
  const first = textModels.value.find((m) => m.providerId === selectedProviderId.value)
  selectedModelId.value = first?.id ?? null
}

async function handlePushTags() {
  pushing.value = true
  try {
    const r = await pushMemosTags()
    message.success(`写回完成：成功 ${r.success}，跳过 ${r.skipped}，失败 ${r.failed}`)
    await Promise.all([loadData(), loadStats()])
  } finally {
    pushing.value = false
  }
}

async function handleRebuildRag() {
  rebuilding.value = true
  try {
    const r = await rebuildMemosRag()
    message.success(`重建完成：处理 ${r.processed}，成功 ${r.success}，失败 ${r.failed}`)
    await loadRagStatus()
  } finally {
    rebuilding.value = false
  }
}

// ===== 更多操作（下拉） =====

const moreOptions: DropdownOption[] = [
  { label: '同步标签到 Memos', key: 'push' },
  { label: '重建RAG索引', key: 'rebuild' },
  { label: '设置', key: 'setting' },
]

function handleMoreSelect(key: string) {
  if (key === 'push') void handlePushTags()
  else if (key === 'rebuild') void handleRebuildRag()
  else if (key === 'setting') settingShow.value = true
}

// ===== RAG 索引状态（是否已构建：已向量化数/总数 + 上次全量重建时间）=====

const ragStatus = ref<{ total: number; embedded: number; pending: number; lastRebuildAt: string } | null>(null)

async function loadRagStatus() {
  try {
    ragStatus.value = await getMemosRagStatus()
  } catch {
    // 错误已由拦截器提示
  }
}

// ===== 统计 =====

const stats = ref<MemosStats>({ total: 0, active: 0, deleted: 0, untagged: 0, pendingPush: 0 })

async function loadStats() {
  stats.value = await getMemosStats()
}

const statTags = computed(() => [
  { label: '总数', value: stats.value.total },
  { label: '存活', value: stats.value.active },
  { label: '已删', value: stats.value.deleted },
  { label: '未打标', value: stats.value.untagged },
  { label: '待写回', value: stats.value.pendingPush },
])

// ===== 列表 =====

const query = reactive({
  keyword: '',
  remoteDeleted: -1 as number,
  pageNum: 1,
  pageSize: 10,
})

const loading = ref(false)
const tableData = ref<MemosNote[]>([])
const total = ref(0)
const checkedKeys = ref<number[]>([])

const deletedOptions: SelectOption[] = [
  { label: '全部', value: -1 },
  { label: '存活', value: 0 },
  { label: '远端已删', value: 1 },
]

function escapeRegExp(s: string) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/** 命中关键词切成 {text, hit} 段；无关键词时不命中任何段 */
function splitHighlight(text: string, keyword: string) {
  const words = keyword.trim().split(/[\s,，、;；]+/).filter(Boolean)
  if (!words.length) return [{ text, hit: false }]
  const pattern = new RegExp(`(${words.map(escapeRegExp).join('|')})`, 'gi')
  return text.split(pattern).map((part) => ({
    text: part,
    hit: words.some((w) => part.toLowerCase() === w.toLowerCase()),
  }))
}

/** 命中关键词包 <mark> 高亮（表格内容列用）；无命中关键词时返回原文本 */
function highlightContent(text: string, keyword: string) {
  if (!text) return ''
  return splitHighlight(text, keyword).map((p) =>
    p.hit ? h('mark', { class: 'search-hit' }, p.text) : p.text,
  )
}

/**
 * 桌面表格列（内容优先）：ID/UID/创建/更新时间 入详情抽屉，内容列为唯一弹性列占满剩余宽度。
 * 移动端改卡片列表，不走本列。
 */
const columns: DataTableColumns<MemosNote> = [
  { type: 'selection', fixed: 'left' },
  {
    title: '内容', key: 'content', minWidth: 360,
    render: (row) => {
      if (!row.content) return h('span', { style: 'color:var(--c-text-3)' }, '-')
      const hasImage = hasContentImage(row.content)
      const hasLink = hasContentLink(row.content)
      const icon = (ic: string, color: string) =>
        h(NIcon, { size: 14, style: `color:${color};vertical-align:-2px;margin-left:4px;flex-shrink:0` },
          { default: () => h(iconMap[ic]) })
      return h('span', { class: 'content-cell', title: row.content }, [
        h('span', { class: 'content-text' }, highlightContent(row.content, query.keyword)),
        hasImage ? icon('image', 'var(--c-warning)') : null,
        hasLink ? icon('link', 'var(--c-info)') : null,
      ])
    },
  },
  {
    title: '标签', key: 'tags', width: 180,
    render: (row) => {
      if (!row.tags.length) return h('span', { style: 'color:var(--c-text-3)' }, '-')
      const show = row.tags.slice(0, 3)
      const rest = row.tags.length - show.length
      return h(NSpace, { size: 2, wrap: true }, {
        default: () => [
          ...show.map((t) => h(NTag, { size: 'small', type: 'info' }, { default: () => `#${t}` })),
          rest > 0 ? h(NTag, { size: 'small', type: 'default' }, { default: () => `+${rest}` }) : null,
        ],
      })
    },
  },
  {
    title: '标签同步', key: 'tagsSynced', width: 90,
    render: (row) => row.tagsSynced === 1
      ? h(NTag, { size: 'small', type: 'success' }, { default: () => '已同步' })
      : h(NTag, { size: 'small', type: 'warning' }, { default: () => '待写回' }),
  },
  {
    title: '远端状态', key: 'remoteDeleted', width: 90,
    render: (row) => row.remoteDeleted === 1
      ? h(NTag, { size: 'small', type: 'error' }, { default: () => '已删除' })
      : h(NTag, { size: 'small', type: 'success' }, { default: () => '存活' }),
  },
  {
    title: '操作', key: 'actions', width: 80, fixed: 'right',
    render: (row) => h(NButton, { size: 'small', text: true, onClick: () => viewDetail(row) },
      { icon: () => h(NIcon, null, { default: () => h(iconMap.eye) }), default: () => '查看' }),
  },
]

/** 已删除行置灰 + 删除线（视觉区分备份与存活笔记，桌面表格行用） */
const rowProps = (row: MemosNote) => (row.remoteDeleted === 1 ? { class: 'row-deleted' } : {})

/** 移动端卡片勾选（AI 打标签用） */
function toggleCheck(id: number, checked: boolean) {
  if (checked) {
    if (!checkedKeys.value.includes(id)) checkedKeys.value.push(id)
  } else {
    checkedKeys.value = checkedKeys.value.filter((k) => k !== id)
  }
}

const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
  onChange: (page: number) => {
    pagination.page = page
    query.pageNum = page
    loadData()
  },
  onUpdatePageSize: (size: number) => {
    pagination.pageSize = size
    query.pageSize = size
    pagination.page = 1
    query.pageNum = 1
    loadData()
  },
})

async function loadData() {
  loading.value = true
  try {
    const r = await getMemosPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      remoteDeleted: query.remoteDeleted === -1 ? undefined : query.remoteDeleted,
    })
    tableData.value = r.records
    total.value = r.total
    pagination.itemCount = r.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  pagination.page = 1
  loadData()
}

// ===== 详情抽屉 =====

const drawerOpen = ref(false)
const current = ref<MemosNote | null>(null)

function viewDetail(row: MemosNote) {
  current.value = row
  drawerOpen.value = true
}

onMounted(() => {
  loadConfig()
  loadData()
  loadStats()
  loadTextModels()
  loadWebhookConfig()
  loadRagStatus()
  loadSyncStatus()
})
</script>

<template>
  <div class="memos-page" :style="{ height: pageHeight }">
      <NCard :bordered="false" class="action-bar">
        <div class="bar-row">
          <NSpace :size="8" wrap class="bar-actions">
            <NButton type="primary" :loading="pulling" @click="handlePull">
              <template #icon><NIcon><component :is="iconMap.sync" /></NIcon></template>
              立即同步
            </NButton>
            <NButton type="info" :loading="tagging" :disabled="checkedKeys.length === 0" @click="openTagModal">
              AI 打标签<template v-if="checkedKeys.length">({{ checkedKeys.length }})</template>
            </NButton>

            <template v-if="isMobile">
              <!-- 移动端空间不足：低频动作收进「更多」 -->
              <NDropdown :options="moreOptions" trigger="click" @select="handleMoreSelect">
                <NButton :loading="pushing || rebuilding" :disabled="pushing || rebuilding">
                  … 更多
                </NButton>
              </NDropdown>
            </template>
            <template v-else>
              <!-- 桌面平铺全部动作，设置归入动作组（中性样式），右侧只留数据状态 -->
              <NButton :loading="pushing" @click="handlePushTags">同步标签到 Memos</NButton>
              <NButton :loading="rebuilding" @click="handleRebuildRag">重建RAG索引</NButton>
              <NDivider vertical style="height: 20px; margin: 0 2px" />
              <NButton quaternary @click="settingShow = true">
                <template #icon><NIcon><component :is="iconMap.settings" /></NIcon></template>
                设置
              </NButton>
            </template>
          </NSpace>

          <NSpace :size="8" align="center" wrap class="bar-info">
            <template v-if="isMobile">
              <NTag size="tiny" type="info" :bordered="false">总数 {{ stats.total }}</NTag>
              <NTag size="tiny" type="warning" :bordered="false">待写回 {{ stats.pendingPush }}</NTag>
            </template>
            <template v-else>
              <NTag v-for="s in statTags" :key="s.label" size="tiny" type="info" :bordered="false">
                {{ s.label }} {{ s.value }}
              </NTag>
            </template>
            <template v-if="ragStatus">
              <NTag size="tiny" :type="ragStatus.pending === 0 ? 'success' : 'warning'" :bordered="false">
                RAG索引 {{ ragStatus.embedded }}/{{ ragStatus.total }}
              </NTag>
              <span v-if="ragStatus.lastRebuildAt" class="rag-status-time">
                上次重建 {{ formatTime(ragStatus.lastRebuildAt) }}
              </span>
            </template>
          </NSpace>
        </div>
      </NCard>

      <NCard title="同步状态" size="small" class="sync-card">
        <template #header-extra>
          <NSpace :size="8" align="center">
            <span v-if="syncLogTotal" class="sync-log-count">近 3 天记录 {{ syncLogTotal }} 条</span>
            <NButton size="tiny" quaternary @click="openSyncLogDrawer">查看记录</NButton>
          </NSpace>
        </template>
        <template v-if="latestSync">
          <div class="sync-summary">
            <NTag size="small" :bordered="false" :type="latestSync.triggerType === 'scheduled' ? 'info' : 'default'">
              {{ syncTriggerLabel[latestSync.triggerType] }}
            </NTag>
            <NTag size="small" :bordered="false" :type="syncStatusMeta[latestSync.status]?.type ?? 'default'">
              {{ syncStatusMeta[latestSync.status]?.label ?? latestSync.status }}
            </NTag>
            <span class="sync-summary-time">
              {{ relativeTime(latestSync.createTime) }}
              <span class="muted">({{ formatTime(latestSync.createTime) }})</span>
              · 耗时 {{ formatDuration(latestSync.durationMs) }}
            </span>
            <span class="sync-summary-counts">
              拉取 {{ latestSync.fetched }} · 新增 {{ latestSync.created }} · 更新 {{ latestSync.updated }}
              · 标记删除 {{ latestSync.markedDeleted }}
              <span :class="latestSync.errors > 0 ? 'sync-errors' : ''">· 失败 {{ latestSync.errors }}</span>
            </span>
            <span v-if="latestSync.errorMessage" class="sync-error-msg">原因：{{ latestSync.errorMessage }}</span>
          </div>
        </template>
        <span v-else class="sync-empty">暂无同步记录，点「立即同步」或等每 4 小时定时同步后查看</span>
      </NCard>

      <NCard title="笔记备份" size="small" class="table-card">
        <template #header-extra>
          <NSpace :size="8" wrap class="note-filter">
            <NInput v-model:value="query.keyword" class="note-search" placeholder="搜索内容/UID/标签，空格或逗号分隔多词" clearable
              @keyup.enter="handleSearch" />
            <NSelect v-model:value="query.remoteDeleted" :options="deletedOptions" class="note-status-filter" />
            <NButton type="primary" size="small" @click="handleSearch">查询</NButton>
          </NSpace>
        </template>
        <NDataTable v-if="!isMobile" :columns="columns" :data="tableData" :loading="loading" :pagination="pagination"
          :row-key="(row: MemosNote) => row.id" v-model:checked-row-keys="checkedKeys"
          :row-props="rowProps" flex-height
          :scroll-x="840" remote striped size="small" />

        <!-- 移动端：卡片列表（内容整卡换行展示，杜绝横向滚动挤走正文） -->
        <div v-else class="note-cards">
          <div v-for="(row, idx) in tableData" :key="row.id" class="note-card" :class="{ 'card-deleted': row.remoteDeleted === 1 }" :style="{ animationDelay: idx * 40 + 'ms' }">
            <div class="card-head">
              <NCheckbox :checked="checkedKeys.includes(row.id)"
                @update:checked="(v: boolean) => toggleCheck(row.id, v)" class="card-check" />
              <div class="card-content" role="button" tabindex="0" @click="viewDetail(row)" @keydown.enter="viewDetail(row)">
                <template v-if="row.content">
                  <span class="card-content-text" :title="row.content">
                    <template v-for="(p, i) in splitHighlight(row.content, query.keyword)" :key="i">
                      <mark v-if="p.hit" class="search-hit">{{ p.text }}</mark>
                      <template v-else>{{ p.text }}</template>
                    </template>
                  </span>
                  <NIcon v-if="hasContentImage(row.content)" size="14" class="card-icon" style="color:var(--c-warning)">
                    <component :is="iconMap.image" />
                  </NIcon>
                  <NIcon v-if="hasContentLink(row.content)" size="14" class="card-icon" style="color:var(--c-info)">
                    <component :is="iconMap.link" />
                  </NIcon>
                </template>
                <span v-else class="card-content-empty">-</span>
              </div>
            </div>
            <div class="card-meta">
              <NTag v-for="t in row.tags.slice(0, 3)" :key="t" size="tiny" type="info" :bordered="false">#{{ t }}</NTag>
              <NTag v-if="row.tags.length > 3" size="tiny" type="default" :bordered="false">
                +{{ row.tags.length - 3 }}
              </NTag>
              <NTag size="tiny" :type="row.tagsSynced === 1 ? 'success' : 'warning'" :bordered="false">
                {{ row.tagsSynced === 1 ? '已同步' : '待写回' }}
              </NTag>
              <NTag size="tiny" :type="row.remoteDeleted === 1 ? 'error' : 'success'" :bordered="false">
                {{ row.remoteDeleted === 1 ? '已删' : '存活' }}
              </NTag>
              <span class="card-time">{{ formatTime(row.remoteUpdateTime || row.createTime) }}</span>
            </div>
          </div>
          <BrandEmpty v-if="!loading && !tableData.length" size="small" description="暂无笔记" class="card-empty" />
          <NPagination v-if="total > 0" class="card-pagination" :page="pagination.page"
            :page-size="pagination.pageSize" :item-count="pagination.itemCount"
            :page-sizes="[10, 20, 50]" show-size-picker
            @update:page="(p: number) => { pagination.page = p; query.pageNum = p; loadData() }"
            @update:page-size="(s: number) => { pagination.pageSize = s; query.pageSize = s; pagination.page = 1; query.pageNum = 1; loadData() }" />
        </div>
      </NCard>
  </div>

    <!-- 同步状态记录抽屉：明细独立展示，不挤占笔记列表 -->
    <NDrawer v-model:show="syncLogDrawerShow" :width="drawerWidth">
      <NDrawerContent title="同步状态记录（最近 3 天）" :native-scrollbar="false" closable>
        <NAlert type="info" :bordered="false" class="sync-log-hint">
          定时任务每 4 小时整点触发；手动「立即同步」同样记录。共 <b>{{ syncLogTotal }}</b> 条（3 天前自动清理）。
        </NAlert>
        <NDataTable :columns="syncLogColumns" :data="syncLogs" :loading="syncLogLoading" :pagination="syncLogPagination"
          size="small" :scroll-x="760" striped />
      </NDrawerContent>
    </NDrawer>

    <!-- 设置抽屉：同步配置 + Webhook 配置（低频，收进弹层） -->
    <NDrawer v-model:show="settingShow" :width="drawerWidth">
      <NDrawerContent title="Memos 设置" :native-scrollbar="false" closable>
        <NSpace vertical :size="16">
          <NCard title="同步配置" size="small">
            <NForm label-placement="left" :label-width="90" :show-feedback="false">
              <NFormItem label="域名">
                <NInput v-model:value="config.baseUrl" placeholder="https://memo.booksy.cf" :disabled="configLoading" />
              </NFormItem>
              <NFormItem label="Token">
                <NInput v-model:value="config.token" type="password" show-password-on="click"
                  :placeholder="config.tokenConfigured ? '已配置（留空不修改）' : '请输入 Memos API Token'" :disabled="configLoading" />
              </NFormItem>
              <NFormItem label="打标提示词">
                <NInput v-model:value="config.promptTemplate" type="textarea" :rows="4"
                  placeholder="支持 \{\{content\}\} 占位符替换笔记内容" :disabled="configLoading" />
              </NFormItem>
              <NFormItem label=" ">
                <NButton type="primary" :loading="configLoading" @click="handleSaveConfig">保存配置</NButton>
              </NFormItem>
            </NForm>
          </NCard>

          <NCard title="Webhook 实时同步" size="small">
            <NForm label-placement="left" :label-width="90" :show-feedback="false">
              <NFormItem label="回调地址">
                <NSpace :size="8" style="width: 100%">
                  <NInput :value="webhookUrl" readonly></NInput>
                  <NButton size="small" :disabled="!webhookUrl" @click="copyWebhookUrl">复制</NButton>
                </NSpace>
              </NFormItem>
              <NFormItem label="签名密钥">
                <NInput v-model:value="webhookSecret" type="password" show-password-on="click"
                  :placeholder="webhookSecretConfigured ? '已配置（留空不修改）' : '请输入 webhook 签名密钥'" />
              </NFormItem>
              <NFormItem label=" ">
                <NButton type="primary" :loading="webhookSaving" :disabled="!webhookSecret.trim()"
                  @click="handleSaveWebhookSecret">保存密钥</NButton>
              </NFormItem>
            </NForm>
            <NAlert type="info" :bordered="false">
              在 Memos 设置 → 我的 Webhooks 中新建回调：URL 填上面的回调地址，创建后复制签名密钥（whsec_ 开头）填到这里保存。
              此后 Memos 创建/更新/删除笔记时实时推送到本地，与「立即同步」并行、互不影响。
            </NAlert>
          </NCard>
        </NSpace>
      </NDrawerContent>
    </NDrawer>

    <!-- AI 打标签弹窗：勾选笔记后点工具条按钮弹出 -->
    <NModal v-model:show="tagShow" preset="card" title="AI 打标签" :style="{ width: 'var(--modal-sm)', maxWidth: '90vw' }">
      <NSpace vertical :size="16">
        <NAlert type="info" :bordered="false">
          已勾选 <b>{{ checkedKeys.length }}</b> 条笔记。AI 生成标签并与现有标签合并，成功后自动写回远端；供应商/模型留空则用默认 TEXT 模型。
        </NAlert>
        <div class="tag-model-row">
          <div class="tag-model-label">供应商</div>
          <NSelect v-model:value="selectedProviderId" :options="providerOptions" placeholder="供应商"
            size="small" :loading="modelLoading" clearable
            :disabled="providerOptions.length === 0" @update:value="handleProviderChange" />
        </div>
        <div class="tag-model-row">
          <div class="tag-model-label">AI 模型</div>
          <NSelect v-model:value="selectedModelId" :options="modelOptions" placeholder="AI 模型"
            size="small" clearable :disabled="modelOptions.length === 0" />
        </div>
      </NSpace>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="tagShow = false">取消</NButton>
          <NButton type="primary" :loading="tagging" :disabled="checkedKeys.length === 0" @click="confirmTag">
            开始打标签
          </NButton>
        </NSpace>
      </template>
    </NModal>

    <NDrawer v-model:show="drawerOpen" :width="drawerWidth">
      <NDrawerContent title="笔记详情" closable>
        <template v-if="current">
          <NDescriptions :column="1" label-placement="left" size="small" bordered>
            <NDescriptionsItem label="UID">{{ current.uid }}</NDescriptionsItem>
            <NDescriptionsItem label="标签">
              <NSpace :size="4">
                <NTag v-for="t in current.tags" :key="t" size="small" type="info">#{{ t }}</NTag>
                <span v-if="!current.tags.length" style="color:var(--c-text-3)">-</span>
              </NSpace>
            </NDescriptionsItem>
            <NDescriptionsItem label="标签同步">
              <NTag size="small" :type="current.tagsSynced === 1 ? 'success' : 'warning'">
                {{ current.tagsSynced === 1 ? '已同步' : '待写回' }}
              </NTag>
            </NDescriptionsItem>
            <NDescriptionsItem label="远端状态">
              <NTag size="small" :type="current.remoteDeleted === 1 ? 'error' : 'success'">
                {{ current.remoteDeleted === 1 ? '已删除' : '存活' }}
              </NTag>
            </NDescriptionsItem>
            <NDescriptionsItem label="远端创建">{{ formatTime(current.remoteCreateTime) }}</NDescriptionsItem>
            <NDescriptionsItem label="远端更新">{{ formatTime(current.remoteUpdateTime) }}</NDescriptionsItem>
            <NDescriptionsItem label="本地入库">{{ formatTime(current.createTime) }}</NDescriptionsItem>
          </NDescriptions>
<NCard title="原文" size="small" style="margin-top: 12px">
            <div v-if="current.content" class="markdown-body" v-html="renderMarkdown(current.content)"></div>
            <span v-else style="color:var(--c-text-3)">-</span>
          </NCard>
        </template>
      </NDrawerContent>
    </NDrawer>
</template>

<style scoped>
/* 撑满视口：工具条固定、表格卡占剩余高度，页面本身不滚动（表格内部滚动） */
.memos-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.action-bar {
  flex: none;
}

.sync-card {
  flex: none;
}

.sync-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.sync-summary-time,
.sync-summary-counts {
  color: var(--c-text-2);
}

.sync-errors {
  color: var(--c-error);
  font-weight: 600;
}

.sync-error-msg {
  color: var(--c-error);
}

.sync-empty {
  font-size: 13px;
  color: var(--c-text-3);
}

.sync-log-count {
  font-size: 12px;
  color: var(--c-text-3);
}

.sync-log-hint {
  margin-bottom: 12px;
}

.muted {
  color: var(--c-text-3);
  font-size: 12px;
}

.table-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* Naive NCard 内容区类名是 n-card-content（注意是单下划线，不是 n-card__content） */
.table-card :deep(.n-card-content) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* flex-height 表格：作为 flex item 填充父容器；min-height 兜底，即使高度链断裂也至少显示 200px 数据行 */
.table-card :deep(.n-data-table) {
  flex: 1;
  min-height: 200px;
}

/* ===== 笔记列表（桌面表格 / 移动端卡片） ===== */

.note-filter {
  justify-content: flex-end;
}

.note-search {
  width: 240px;
}

.note-status-filter {
  width: 120px;
}

.note-cards {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 2px;
}

.note-card {
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  padding: 10px 12px;
  background: var(--c-fill-2);
  transition: border-color 0.2s, background-color 0.2s;
  animation: list-in 0.3s ease both;
}

.note-card:hover {
  border-color: var(--c-info);
  background: var(--c-fill-2);
}

.note-card.card-deleted {
  color: var(--c-text-3);
}

.note-card.card-deleted .card-content-text {
  text-decoration: line-through;
}

.card-head {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.card-check {
  flex-shrink: 0;
  margin-top: 2px;
}

.card-content {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  line-height: 1.6;
  cursor: pointer;
  word-break: break-word;
}

.card-content-text {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: normal;
}

.card-icon {
  flex-shrink: 0;
  margin-left: 4px;
  vertical-align: -2px;
}

.card-content-empty {
  color: var(--c-text-3);
}

.card-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding-left: 28px;
}

.card-time {
  font-size: 12px;
  color: var(--c-text-3);
  margin-left: auto;
}

.card-empty {
  padding: 8px;
}

.card-pagination {
  justify-content: center;
  margin-top: 4px;
}

@media (max-width: 768px) {
  .note-search {
    width: 100%;
  }

  .note-status-filter {
    flex: 1;
    min-width: 110px;
  }
}

.bar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.bar-actions {
  flex-shrink: 0;
}

.bar-info {
  margin-left: auto;
}

.tag-model-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tag-model-label {
  font-size: 13px;
  color: var(--c-text-2);
  white-space: nowrap;
  width: 60px;
  text-align: right;
}

.rag-status-time {
  font-size: 12px;
  color: var(--c-text-3);
}
.content-cell {
  display: flex;
  align-items: center;
  min-width: 0;
}
.content-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.search-hit {
  background: var(--c-warning-bg);
  color: inherit;
  border-radius: 2px;
  padding: 0 1px;
}
:deep(.row-deleted) {
  color: var(--c-text-3);
}
:deep(.row-deleted td) {
  text-decoration: line-through;
}
.markdown-body {
  font-size: 13px;
  line-height: 1.8;
  word-break: break-word;
}
.markdown-body :deep(p) {
  margin: 0.5em 0;
}
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 0.8em 0 0.4em;
  font-weight: 600;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 1.5em;
  margin: 0.4em 0;
}
.markdown-body :deep(code) {
  background: var(--c-fill-2);
  padding: 1px 5px;
  border-radius: var(--r-xs);
  font-size: 12px;
}
.markdown-body :deep(pre) {
  background: var(--c-fill-2);
  padding: 10px;
  border-radius: var(--r-sm);
  overflow-x: auto;
}
.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--c-border);
  margin: 0.5em 0;
  padding-left: 10px;
  color: var(--c-text-3);
}
.markdown-body :deep(a) {
  color: var(--c-info);
}
.markdown-body :deep(img) {
  max-width: 100%;
  border-radius: var(--r-sm);
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 0.5em 0;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--c-border);
  padding: 4px 8px;
}
</style>

