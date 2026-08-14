<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import {
  NCard, NForm, NFormItem, NInput, NButton, NSpace, NDataTable, NTag,
  NDrawer, NDrawerContent, NDescriptions, NDescriptionsItem, NIcon, NSelect,
  NAlert, useMessage,
} from 'naive-ui'
import type { DataTableColumns, SelectOption } from 'naive-ui'
import {
  getMemosConfig, saveMemosConfig, pullMemos, tagMemos, pushMemosTags,
  getMemosPage, getMemosStats, getMemosWebhookConfig, saveMemosWebhookSecret,
  rebuildMemosRag, getMemosRagStatus,
} from '@/api/memos'
import { getAiModelsByType } from '@/api/ai'
import type { MemosConfig, MemosNote, MemosStats, AiModel } from '@/types/api'
import { iconMap } from '@/utils/icons'
import { formatTime } from '@/utils/format'
import { useIsMobile } from '@/composables/useBreakpoint'

const message = useMessage()
const isMobile = useIsMobile()
const drawerWidth = computed(() => (isMobile.value ? '100%' : 660))

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

// ===== 三个动作按钮 =====

const pulling = ref(false)
const tagging = ref(false)
const pushing = ref(false)
const rebuilding = ref(false)

async function handlePull() {
  pulling.value = true
  try {
    const r = await pullMemos()
    message.success(`同步完成：新增 ${r.created}，更新 ${r.updated}，标记删除 ${r.markedDeleted}，失败 ${r.errors}`)
    await Promise.all([loadData(), loadStats()])
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

const allColumns: DataTableColumns<MemosNote> = [
  { type: 'selection', fixed: 'left' },
  { title: 'ID', key: 'id', width: 70 },
  { title: 'UID', key: 'uid', ellipsis: { tooltip: true }, width: 110 },
  {
    title: '内容', key: 'content', width: 340,
    render: (row) => {
      if (!row.content) return h('span', { style: 'color:#999' }, '-')
      const hasImage = hasContentImage(row.content)
      const hasLink = hasContentLink(row.content)
      const icon = (ic: string, color: string) =>
        h(NIcon, { size: 14, style: `color:${color};vertical-align:-2px;margin-left:4px;flex-shrink:0` },
          { default: () => h(iconMap[ic]) })
      return h('span', { class: 'content-cell', title: row.content }, [
        h('span', { class: 'content-text' }, row.content),
        hasImage ? icon('image', '#f0a020') : null,
        hasLink ? icon('link', '#2080f0') : null,
      ])
    },
  },
  {
    title: '标签', key: 'tags', width: 200,
    render: (row) => {
      if (!row.tags.length) return h('span', { style: 'color:#999' }, '-')
      const show = row.tags.slice(0, 2)
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
    title: '远端状态', key: 'remoteDeleted', width: 100,
    render: (row) => row.remoteDeleted === 1
      ? h(NTag, { size: 'small', type: 'error' }, { default: () => '已删除' })
      : h(NTag, { size: 'small', type: 'success' }, { default: () => '存活' }),
  },
  { title: '创建时间', key: 'createTime', width: 170, render: (row) => formatTime(row.createTime) },
  { title: '更新时间', key: 'remoteUpdateTime', width: 170, render: (row) => formatTime(row.remoteUpdateTime) },
  {
    title: '操作', key: 'actions', width: 80, fixed: 'right',
    render: (row) => h(NButton, { size: 'small', text: true, onClick: () => viewDetail(row) },
      { icon: () => h(NIcon, null, { default: () => h(iconMap.eye) }), default: () => '查看' }),
  },
]

/** 已删除行置灰 + 删除线（视觉区分备份与存活笔记） */
const rowProps = (row: MemosNote) => (row.remoteDeleted === 1 ? { class: 'row-deleted' } : {})

const mobileHiddenKeys = new Set(['id', 'uid', 'tagsSynced', 'createTime'])
const columns = computed<DataTableColumns<MemosNote>>(() =>
  isMobile.value
    ? allColumns.filter((c) => !mobileHiddenKeys.has((c as { key?: string }).key ?? ''))
    : allColumns,
)

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
})
</script>

<template>
  <div class="memos-page">
    <NSpace vertical :size="16">
      <NCard title="Memos 配置" size="small">
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
              placeholder="支持 {{content}} 占位符替换笔记内容" :disabled="configLoading" />
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

      <NCard title="同步操作" size="small">
        <template #header-extra>
          <NSpace :size="8">
            <NTag v-for="s in statTags" :key="s.label" size="small" type="info">
              {{ s.label }} {{ s.value }}
            </NTag>
          </NSpace>
        </template>
        <NSpace vertical :size="12">
        <NSpace :size="12" wrap>
            <NButton type="primary" :loading="pulling" @click="handlePull">
              <template #icon><NIcon><component :is="iconMap.sync" /></NIcon></template>
              立即同步
            </NButton>
            <NSpace :size="8" align="center">
              <NSelect v-model:value="selectedProviderId" :options="providerOptions" placeholder="供应商"
                size="small" :loading="modelLoading" style="width: 140px" clearable
                :disabled="providerOptions.length === 0" @update:value="handleProviderChange" />
              <NSelect v-model:value="selectedModelId" :options="modelOptions" placeholder="AI 模型"
                size="small" style="width: 200px" clearable
                :disabled="modelOptions.length === 0" />
            </NSpace>
            <NButton type="info" :loading="tagging" :disabled="checkedKeys.length === 0" @click="handleTag">
              AI 打标签<template v-if="checkedKeys.length">({{ checkedKeys.length }})</template>
            </NButton>
            <NButton type="warning" :loading="pushing" @click="handlePushTags">同步标签到 Memos</NButton>
            <NButton type="tertiary" :loading="rebuilding" @click="handleRebuildRag">
              重建RAG索引
            </NButton>
            <template v-if="ragStatus">
              <NTag size="small" :type="ragStatus.pending === 0 ? 'success' : 'warning'" :bordered="false">
                RAG索引 {{ ragStatus.embedded }}/{{ ragStatus.total }}
              </NTag>
              <span v-if="ragStatus.lastRebuildAt" class="rag-status-time">
                上次重建 {{ formatTime(ragStatus.lastRebuildAt) }}
              </span>
            </template>
          </NSpace>
          <NAlert type="info" :bordered="false">
            立即同步：全量拉取远端笔记备份到本地，远端已删除的笔记在本地标记删除（不删数据）。
            勾选笔记后选供应商/模型（留空用默认 TEXT 模型），点「AI 打标签」：AI 生成标签（与现有标签合并），打标成功后自动写回远端；写回失败的笔记保持待写回状态，可点「同步标签到 Memos」手动重试。
            笔记同步后自动向量化供 AI 聊天 RAG 检索（登录后问自己的笔记）；「重建RAG索引」用于新增向量化失败的笔记或更换向量模型后全量重算。
          </NAlert>
        </NSpace>
      </NCard>

      <NCard title="笔记备份" size="small">
        <template #header-extra>
          <NSpace :size="8">
            <NInput v-model:value="query.keyword" placeholder="搜索内容/UID/标签" clearable
              style="width: 180px" @keyup.enter="handleSearch" />
            <NSelect v-model:value="query.remoteDeleted" :options="deletedOptions" style="width: 120px" />
            <NButton type="primary" size="small" @click="handleSearch">查询</NButton>
          </NSpace>
        </template>
        <NDataTable :columns="columns" :data="tableData" :loading="loading" :pagination="pagination"
          :row-key="(row: MemosNote) => row.id" v-model:checked-row-keys="checkedKeys"
          :row-props="rowProps"
          :scroll-x="isMobile ? 900 : 1180" remote striped size="small" />
      </NCard>
    </NSpace>

    <NDrawer v-model:show="drawerOpen" :width="drawerWidth">
      <NDrawerContent title="笔记详情" closable>
        <template v-if="current">
          <NDescriptions :column="1" label-placement="left" size="small" bordered>
            <NDescriptionsItem label="UID">{{ current.uid }}</NDescriptionsItem>
            <NDescriptionsItem label="标签">
              <NSpace :size="4">
                <NTag v-for="t in current.tags" :key="t" size="small" type="info">#{{ t }}</NTag>
                <span v-if="!current.tags.length" style="color:#999">-</span>
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
            <span v-else style="color:#999">-</span>
          </NCard>
        </template>
      </NDrawerContent>
    </NDrawer>
  </div>
</template>

<style scoped>
.rag-status-time {
  font-size: 12px;
  opacity: 0.6;
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
:deep(.row-deleted) {
  opacity: 0.55;
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
  background: rgba(128, 128, 128, 0.15);
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 12px;
}
.markdown-body :deep(pre) {
  background: rgba(128, 128, 128, 0.1);
  padding: 10px;
  border-radius: 6px;
  overflow-x: auto;
}
.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid rgba(128, 128, 128, 0.4);
  margin: 0.5em 0;
  padding-left: 10px;
  color: #888;
}
.markdown-body :deep(a) {
  color: #2080f0;
}
.markdown-body :deep(img) {
  max-width: 100%;
  border-radius: 6px;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 0.5em 0;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid rgba(128, 128, 128, 0.3);
  padding: 4px 8px;
}
</style>

