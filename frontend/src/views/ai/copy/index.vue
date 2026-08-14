<script setup lang="ts">
import { h, ref, computed, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import {
  NSpace, NButton, NInput, NSelect, NTag, NDataTable, NPopconfirm, NEmpty, NAlert, useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import AiGeneratorLayout from '../components/AiGeneratorLayout.vue'
import CopyResultDetail from '../components/CopyResultDetail.vue'
import { useCoverStore } from '@/views/tools/cover/store/cover'
import { useApiConfigStore } from '../store/apiConfig'
import { useUIStore } from '../store/ui'
import { formatTime } from '@/utils/format'
import ApiSettingsModal from '../components/ApiSettingsModal.vue'
import { useIsMobile } from '@/composables/useBreakpoint'
import { buildPrompt, parseCopyResult } from '../lib/llm'
import {
  getAiModelsByType, getAiTemplatePage, streamAiChat,
  getChatRecordPage, deleteChatRecord, clearChatRecords,
} from '@/api/ai'
import { useAuthStore } from '@/store/auth'
import type { AiModel, AiTemplate, AiChatRecord, CopyResultData } from '@/types/api'

const router = useRouter()
const message = useMessage()
const coverStore = useCoverStore()
const authStore = useAuthStore()
const apiConfigStore = useApiConfigStore()
const uiStore = useUIStore()
const isMobile = useIsMobile()

// ===== 模板 =====
const templates = ref<AiTemplate[]>([])
const templateId = ref<number | null>(null)

const templateOptions = computed(() =>
  templates.value.map((t) => ({ value: t.id, label: t.name })),
)

// ===== 模型选择 =====
const textModels = ref<AiModel[]>([])
const selectedModelId = ref<number | null>(null)
const modelOptions = computed(() =>
  textModels.value.map((m) => ({
    value: m.id,
    label: m.providerName ? `${m.model} (${m.providerName})` : m.model,
  })),
)

// ===== 配置状态 =====
const configured = ref(false)

// ===== 生成 =====
const topic = ref('')
const streaming = ref(false)
const abortRef = ref<AbortController | null>(null)

// ===== 历史（后端流式结束自动落库，登录按账号、游客按 IP）=====
const history = ref<AiChatRecord[]>([])
const historyLoading = ref(false)

// 历史详情抽屉：点历史「查看」或生成完成后自动弹
const drawerOpen = ref(false)
const drawerRow = ref<AiChatRecord | null>(null)
const drawerDisplay = computed<CopyResultData | null>(() => {
  const row = drawerRow.value
  if (!row?.result) return null
  return parseCopyResult(row.result)
})

function openDrawer(row: AiChatRecord) {
  drawerRow.value = row
  drawerOpen.value = true
}

const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
  onChange: (page: number) => {
    pagination.page = page
    loadHistory()
  },
  onUpdatePageSize: (size: number) => {
    pagination.pageSize = size
    pagination.page = 1
    loadHistory()
  },
})



const allColumns: DataTableColumns<AiChatRecord> = [
  { title: '请求时间', key: 'requestTime', width: 170, render: (row) => formatTime(row.requestTime) },
  { title: '提示词', key: 'prompt', ellipsis: { tooltip: true } },
  {
    title: '结果', key: 'result', width: 150,
    render: (row) => row.status === 'failed'
      ? h(NTag, { type: 'error', size: 'small' }, { default: () => '失败' })
      : h('span', { style: 'opacity: 0.7' },
          row.result ? (row.result.length > 30 ? row.result.slice(0, 30) + '...' : row.result) : '-'),
  },
  { title: '返回时间', key: 'responseTime', width: 170, render: (row) => formatTime(row.responseTime) },
  {
    title: '耗时', key: 'durationMs', width: 90,
    render: (row) => (row.durationMs != null ? `${row.durationMs} ms` : '-'),
  },
  {
    title: '操作', key: 'actions', width: 140, fixed: 'right',
    render: (row) => h(NSpace, { size: 'small' }, {
      default: () => [
        h(NButton, {
          size: 'small',
          onClick: () => openDrawer(row),
        }, { default: () => '查看' }),
        h(NPopconfirm, { onPositiveClick: () => handleDeleteHistory(row.id) }, {
          trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
          default: () => '删除该记录？',
        }),
      ],
    }),
  },
]

const columns = computed<DataTableColumns<AiChatRecord>>(() =>
  isMobile.value
    ? allColumns.filter((c) => {
        const key = (c as { key?: string }).key
        return key !== 'responseTime' && key !== 'durationMs'
      })
    : allColumns,
)

const rowProps = (row: AiChatRecord) => ({
  style: 'cursor: pointer',
  onClick: () => openDrawer(row),
})

onBeforeUnmount(() => abortRef.value?.abort())

async function loadTemplates() {
  try {
    const res = await getAiTemplatePage({ pageNum: 1, pageSize: 100 })
    templates.value = res.records
    if (templateId.value === null && res.records.length > 0) {
      templateId.value = res.records[0].id
    }
  } catch {
    // 错误已由拦截器提示
  }
}

async function loadConfig() {
  try {
    textModels.value = await getAiModelsByType('TEXT')
    if (selectedModelId.value === null && textModels.value.length > 0) {
      const def = textModels.value.find((m) => m.isDefault === 1)
      selectedModelId.value = def?.id ?? textModels.value[0].id
    }
    configured.value = textModels.value.length > 0 || !!apiConfigStore.state.endpoint
  } catch {
    // 错误已由拦截器提示
  }
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const res = await getChatRecordPage({ pageNum: pagination.page, pageSize: pagination.pageSize })
    history.value = res.records
    pagination.itemCount = res.total
  } catch {
    // 错误已由拦截器提示
  } finally {
    historyLoading.value = false
  }
}

async function refreshHistory() {
  pagination.page = 1
  await loadHistory()
}

async function generate() {
  if (!topic.value.trim()) {
    message.warning('请输入文案主题')
    return
  }
  const tpl = templates.value.find((t) => t.id === templateId.value)
  if (!tpl) {
    message.warning('请选择模板')
    return
  }
  if (!configured.value) {
    message.warning('请先在 AI创作 → 管理 → AI 配置 中完成配置')
    return
  }
  streaming.value = true
  const ac = new AbortController()
  abortRef.value = ac
  try {
    const full = await streamAiChat(
      buildPrompt(tpl.prompt, topic.value.trim()),
      () => {},
      ac.signal,
      selectedModelId.value
        ? { modelId: selectedModelId.value }
        : apiConfigStore.state.endpoint
          ? apiConfigStore.state
          : {},
    )
    const parsed = parseCopyResult(full)
    if (!parsed) {
      message.error('未能解析为 JSON，已展示原始文本')
    }
    await refreshHistory()
    if (history.value[0]) openDrawer(history.value[0])
  } catch (e) {
    if ((e as Error).name !== 'AbortError') {
      message.error('请求失败: ' + (e as Error).message)
      await refreshHistory()
    }
  } finally {
    streaming.value = false
  }
}

function stop() {
  abortRef.value?.abort()
}

function sendToCover(title: string) {
  drawerOpen.value = false
  coverStore.update({ title })
  router.push('/tools/cover')
}

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    message.success('已复制')
  } catch {
    message.error('复制失败')
  }
}

async function handleDeleteHistory(id: number) {
  try {
    await deleteChatRecord(id)
    if (drawerRow.value?.id === id) {
      drawerOpen.value = false
      drawerRow.value = null
    }
    await loadHistory()
  } catch {
    // 错误已由拦截器提示
  }
}

async function handleClearHistory() {
  try {
    await clearChatRecords()
    drawerOpen.value = false
    drawerRow.value = null
    await loadHistory()
    message.success('已清空全部历史')
  } catch {
    // 错误已由拦截器提示
  }
}

onMounted(() => {
  loadTemplates()
  if (authStore.isLogin) {
    loadConfig()
  } else {
    configured.value = true
  }
  loadHistory()
})
</script>

<template>
  <div class="copy-page">
    <AiGeneratorLayout
      aside-title="AI 文案生成"
      drawer-title="历史详情"
      v-model:drawer-open="drawerOpen"
    >
      <template #aside>
        <NSpace vertical :size="16">
          <NAlert v-if="!configured" type="warning" :bordered="false">
            AI 接口未配置，请前往 AI创作 → 管理 → AI 配置 完成设置。
          </NAlert>

          <div>
            <div class="field-label">文案主题</div>
            <NInput
              v-model:value="topic"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 4 }"
              placeholder="输入要创作的主题，如：周末露营穿搭分享"
            />
          </div>

          <div>
            <div class="field-label">提示词模板</div>
            <NSelect
              v-model:value="templateId"
              :options="templateOptions"
              placeholder="选择模板"
            />
          </div>

          <div v-if="authStore.isLogin && modelOptions.length > 0">
            <div class="field-label">模型</div>
            <NSelect
              v-model:value="selectedModelId"
              :options="modelOptions"
              placeholder="留空则用自带 AI 或默认模型"
              clearable
            />
          </div>

          <NSpace>
            <NButton
              type="primary"
              :loading="streaming"
              :disabled="!configured"
              @click="generate"
            >
              {{ configured ? '生成文案' : '未配置 API' }}
            </NButton>
            <NButton v-if="streaming" type="error" @click="stop">停止</NButton>
            <NButton @click="uiStore.openModal('api')">自己的 AI</NButton>
          </NSpace>

          <NAlert v-if="!authStore.isLogin" type="info" :bordered="false">
            游客每日 5 次（受 IP 限流）。历史按 IP 记录，同网络下他人可见。
          </NAlert>
        </NSpace>
      </template>

      <template #history-extra>
        <NPopconfirm @positive-click="handleClearHistory">
          <template #trigger>
            <NButton size="small" type="error" :disabled="history.length === 0">清空历史</NButton>
          </template>
          确认清空全部历史记录？
        </NPopconfirm>
      </template>

      <template #history>
        <NDataTable
          :columns="columns"
          :data="history"
          :loading="historyLoading"
          :pagination="pagination"
          :row-props="rowProps"
          :scroll-x="1000"
          remote
          striped
          size="small"
        />
      </template>

      <template #drawer>
        <CopyResultDetail
          v-if="drawerDisplay"
          :data="drawerDisplay"
          :meta-time="formatTime(drawerRow?.requestTime)"
          @copy="copyText"
          @send-cover="sendToCover"
        />
        <NEmpty v-else-if="drawerRow?.status === 'failed'" description="该条生成失败" />
        <NEmpty v-else description="无结果内容" />
      </template>
    </AiGeneratorLayout>

    <ApiSettingsModal />
  </div>
</template>

<style scoped>
.copy-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field-label {
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 6px;
  opacity: 0.8;
}
</style>
