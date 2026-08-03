<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NCard, NForm, NFormItem, NInput, NButton, NSpace, NDataTable, NTag,
  NDrawer, NDrawerContent, NDescriptions, NDescriptionsItem, NIcon, NSelect,
  NAlert, useMessage,
} from 'naive-ui'
import type { DataTableColumns, SelectOption } from 'naive-ui'
import {
  getMemosConfig, saveMemosConfig, pullMemos, tagMemos, pushMemosTags,
  getMemosPage, getMemosStats,
} from '@/api/memos'
import { getAiModelsByType } from '@/api/ai'
import type { MemosConfig, MemosNote, MemosStats, AiModel } from '@/types/api'
import { iconMap } from '@/utils/icons'
import { formatTime } from '@/utils/format'
import { useIsMobile } from '@/composables/useBreakpoint'

const message = useMessage()
const isMobile = useIsMobile()
const drawerWidth = computed(() => (isMobile.value ? '100%' : 640))

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

// ===== 三个动作按钮 =====

const pulling = ref(false)
const tagging = ref(false)
const pushing = ref(false)

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
  remoteDeleted: null as number | null,
  pageNum: 1,
  pageSize: 10,
})

const loading = ref(false)
const tableData = ref<MemosNote[]>([])
const total = ref(0)
const checkedKeys = ref<number[]>([])

const deletedOptions: SelectOption[] = [
  { label: '全部', value: null as unknown as string },
  { label: '存活', value: 0 },
  { label: '远端已删', value: 1 },
]

const allColumns: DataTableColumns<MemosNote> = [
  { type: 'selection', fixed: 'left' },
  { title: 'ID', key: 'id', width: 70 },
  { title: 'UID', key: 'uid', ellipsis: { tooltip: true }, width: 110 },
  {
    title: '内容', key: 'content', ellipsis: { tooltip: true }, width: 260,
    render: (row) => row.content || '-',
  },
  {
    title: '标签', key: 'tags', width: 200,
    render: (row) => {
      if (!row.tags.length) return h('span', { style: 'color:#999' }, '-')
      return h(NSpace, { size: 2, wrap: true }, {
        default: () => row.tags.map((t) => h(NTag, { size: 'small', type: 'info' }, { default: () => `#${t}` })),
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
  { title: '更新时间', key: 'remoteUpdateTime', width: 170, render: (row) => formatTime(row.remoteUpdateTime) },
  {
    title: '操作', key: 'actions', width: 80, fixed: 'right',
    render: (row) => h(NButton, { size: 'small', text: true, onClick: () => viewDetail(row) },
      { icon: () => h(NIcon, null, { default: () => h(iconMap.eye) }), default: () => '查看' }),
  },
]

const mobileHiddenKeys = new Set(['id', 'uid', 'tagsSynced'])
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
      remoteDeleted: query.remoteDeleted ?? undefined,
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
          </NSpace>
          <NAlert type="info" :bordered="false">
            立即同步：全量拉取远端笔记备份到本地，远端已删除的笔记在本地标记删除（不删数据）。
            勾选笔记后选供应商/模型（留空用默认 TEXT 模型），点「AI 打标签」：AI 生成标签（与现有标签合并），打标成功后自动写回远端；写回失败的笔记保持待写回状态，可点「同步标签到 Memos」手动重试。
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
          :scroll-x="isMobile ? 800 : 1080" remote striped size="small" />
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
            <pre class="memo-content">{{ current.content }}</pre>
          </NCard>
        </template>
      </NDrawerContent>
    </NDrawer>
  </div>
</template>

<style scoped>
.memo-content {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
}
</style>

