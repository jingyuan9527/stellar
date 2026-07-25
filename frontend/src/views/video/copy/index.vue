<script setup lang="ts">
import { h, ref, computed, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import {
  NSpace, NButton, NInput, NSelect, NTag, NDataTable, NPopconfirm, NEmpty, NAlert, useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import AiGeneratorLayout from '../components/AiGeneratorLayout.vue'
import { useCoverStore } from '../store/cover'
import { useApiConfigStore } from '../store/apiConfig'
import { useUIStore } from '../store/ui'
import ApiSettingsModal from '../components/ApiSettingsModal.vue'
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
const raw = ref('')
const abortRef = ref<AbortController | null>(null)

// ===== 历史（后端流式结束自动落库，登录按账号、游客按 IP）=====
const history = ref<AiChatRecord[]>([])
const activeId = ref<number | null>(null)
const historyLoading = ref(false)

const activeResult = computed(() =>
  history.value.find((h) => h.id === activeId.value) ?? null,
)

const display = computed<CopyResultData | null>(() => {
  if (!activeResult.value || streaming.value || !activeResult.value.result) return null
  return parseCopyResult(activeResult.value.result)
})

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

function formatTime(s?: string | null): string {
  if (!s) return ''
  return s.replace('T', ' ').slice(0, 19)
}

const columns: DataTableColumns<AiChatRecord> = [
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
          type: row.id === activeId.value ? 'primary' : 'default',
          onClick: () => { activeId.value = row.id },
        }, { default: () => '查看' }),
        h(NPopconfirm, { onPositiveClick: () => handleDeleteHistory(row.id) }, {
          trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
          default: () => '删除该记录？',
        }),
      ],
    }),
  },
]

const rowProps = (row: AiChatRecord) => ({
  style: 'cursor: pointer',
  onClick: () => { activeId.value = row.id },
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
    if (activeId.value === null && res.records.length > 0) {
      activeId.value = res.records[0].id
    }
  } catch {
    // 错误已由拦截器提示
  } finally {
    historyLoading.value = false
  }
}

async function refreshHistoryAndSelectNewest() {
  pagination.page = 1
  await loadHistory()
  if (history.value[0]) activeId.value = history.value[0].id
}

async function generate() {
  if (!topic.value.trim()) {
    message.warning('请输入视频主题')
    return
  }
  const tpl = templates.value.find((t) => t.id === templateId.value)
  if (!tpl) {
    message.warning('请选择模板')
    return
  }
  if (!configured.value) {
    message.warning('请先在 系统管理 → AI 配置 中完成配置')
    return
  }
  streaming.value = true
  raw.value = ''
  const ac = new AbortController()
  abortRef.value = ac
  try {
    const full = await streamAiChat(
      buildPrompt(tpl.prompt, topic.value.trim()),
      (text) => { raw.value = text },
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
    await refreshHistoryAndSelectNewest()
  } catch (e) {
    if ((e as Error).name !== 'AbortError') {
      message.error('请求失败: ' + (e as Error).message)
      await refreshHistoryAndSelectNewest()
    }
  } finally {
    streaming.value = false
  }
}

function stop() {
  abortRef.value?.abort()
}

function sendToCover(title: string) {
  coverStore.update({ title })
  router.push('/video/cover')
}

function copyText(text: string) {
  navigator.clipboard.writeText(text)
  message.success('已复制')
}

async function handleDeleteHistory(id: number) {
  try {
    await deleteChatRecord(id)
    if (activeId.value === id) activeId.value = null
    await loadHistory()
  } catch {
    // 错误已由拦截器提示
  }
}

async function handleClearHistory() {
  try {
    await clearChatRecords()
    activeId.value = null
    await loadHistory()
    message.success('已清空全部历史')
  } catch {
    // 错误已由拦截器提示
  }
}

function formatTag(t: string) {
  return t.startsWith('#') ? t : `#${t}`
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
    <AiGeneratorLayout aside-title="AI 文案生成">
      <template #aside>
        <NSpace vertical :size="16">
          <NAlert v-if="!configured" type="warning" :bordered="false">
            AI 接口未配置，请前往 系统管理 → AI 配置 完成设置。
          </NAlert>

          <div>
            <div class="field-label">视频主题</div>
            <NInput
              v-model:value="topic"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 4 }"
              placeholder="输入视频主题"
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

      <template #result>
        <pre v-if="streaming" class="stream-preview">{{ raw || '等待响应...' }}</pre>
        <div v-else-if="display && activeResult" class="result-wrap">
          <div class="result-meta">{{ formatTime(activeResult.requestTime) }}</div>
          <div class="result-section">
            <div class="section-header">
              <span class="section-label">标题</span>
            </div>
            <NSpace vertical :size="8" style="margin-top: 8px">
              <div
                v-for="(t, i) in display.titles"
                :key="i"
                class="title-row"
              >
                <span class="title-text">{{ t }}</span>
                <NSpace size="small">
                  <NButton size="small" @click="copyText(t)">复制</NButton>
                  <NButton size="small" type="primary" @click="sendToCover(t)">发送到封面</NButton>
                </NSpace>
              </div>
            </NSpace>
          </div>

          <div class="result-section">
            <div class="section-header">
              <span class="section-label">简介</span>
              <NButton size="tiny" text type="primary" @click="copyText(display.description)">复制</NButton>
            </div>
            <p class="section-text">{{ display.description }}</p>
          </div>

          <div class="result-section">
            <div class="section-header">
              <span class="section-label">标签</span>
              <NButton
                size="tiny"
                text
                type="primary"
                @click="copyText(display.tags.map(formatTag).join(' '))"
              >
                复制全部
              </NButton>
            </div>
            <NSpace :size="8" style="margin-top: 8px">
              <NTag
                v-for="(t, i) in display.tags"
                :key="i"
                :bordered="false"
                style="cursor: pointer"
                @click="copyText(formatTag(t))"
              >
                {{ formatTag(t) }}
              </NTag>
            </NSpace>
          </div>
        </div>
        <NEmpty v-else description="暂无结果，输入主题后点击生成" />
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

.stream-preview {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.6;
}

.result-meta {
  font-size: 12px;
  opacity: 0.6;
  margin-bottom: 12px;
}

.result-section {
  margin-bottom: 16px;
}

.result-section:last-child {
  margin-bottom: 0;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.section-label {
  font-weight: 600;
  font-size: 14px;
}

.section-text {
  margin: 8px 0 0;
  line-height: 1.6;
}

.title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.title-text {
  flex: 1;
}
</style>
