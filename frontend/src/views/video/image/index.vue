<script setup lang="ts">
import { h, computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  NSpace, NInput, NSelect, NButton, NEmpty, NAlert, NDataTable, NTag,
  useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import AiGeneratorLayout from '../components/AiGeneratorLayout.vue'
import { createAiImage, getAiImageTask, getAiImagePage, getAiModelsByType } from '@/api/ai'
import { useAuthStore } from '@/store/auth'
import type { AiModel, AiImageTask } from '@/types/api'

const message = useMessage()
const authStore = useAuthStore()

const models = ref<AiModel[]>([])
const modelId = ref<number | null>(null)
const prompt = ref('')
const size = ref('1K')
const ratio = ref('1:1')
const creating = ref(false)

// 异步任务状态
const taskId = ref<number | null>(null)
const taskStatus = ref<string>('')
const polling = ref(false)
const resultUrl = ref<string | null>(null)
const errorMsg = ref<string | null>(null)
let pollTimer: number | null = null
let pollStart = 0

// 历史
const history = ref<AiImageTask[]>([])
const historyLoading = ref(false)

const modelOptions = computed(() =>
  models.value.map((m) => ({
    value: m.id,
    label: m.providerName ? `${m.model} (${m.providerName})` : m.model,
  })),
)

const sizeOptions = [
  { value: '1K', label: '1K（约 1024）' },
  { value: '2K', label: '2K（约 2048）' },
  { value: '3K', label: '3K（约 3072）' },
  { value: '4K', label: '4K（约 4096）' },
]

const ratioOptions = [
  { value: '1:1', label: '1:1 方形' },
  { value: '16:9', label: '16:9 横图' },
  { value: '9:16', label: '9:16 竖图' },
  { value: '4:3', label: '4:3 横图' },
  { value: '3:4', label: '3:4 竖图' },
  { value: '2:3', label: '2:3 竖图' },
  { value: '3:2', label: '3:2 横图' },
  { value: '21:9', label: '21:9 超宽' },
]

function formatTime(s?: string | null): string {
  if (!s) return ''
  return s.replace('T', ' ').slice(0, 19)
}

const columns: DataTableColumns<AiImageTask> = [
  { title: '请求时间', key: 'createTime', width: 170, render: (row) => formatTime(row.createTime) },
  { title: '提示词', key: 'prompt', ellipsis: { tooltip: true } },
  {
    title: '尺寸/比例', key: 'size', width: 130,
    render: (row) => `${row.size ?? '-'} / ${row.ratio ?? '-'}`,
  },
  {
    title: '结果', key: 'result', width: 90,
    render: (row) => {
      if (row.status === 'completed' && row.url) {
        return h('img', { src: row.url, style: 'width:60px;height:60px;object-fit:contain;border-radius:4px' })
      }
      if (row.status === 'failed') {
        return h(NTag, { type: 'error', size: 'small' }, { default: () => '失败' })
      }
      if (row.status === 'generating') {
        return h(NTag, { size: 'small' }, { default: () => '生成中' })
      }
      return h(NTag, { size: 'small' }, { default: () => row.status })
    },
  },
  { title: '返回时间', key: 'updateTime', width: 170, render: (row) => formatTime(row.updateTime) },
  {
    title: '耗时', key: 'durationMs', width: 90,
    render: (row) => (row.durationMs != null ? `${row.durationMs} ms` : '-'),
  },
  {
    title: '操作', key: 'actions', width: 90, fixed: 'right',
    render: (row) => h(NButton, { size: 'small', onClick: () => viewHistory(row) }, { default: () => '查看' }),
  },
]

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

async function loadModels() {
  try {
    models.value = await getAiModelsByType('IMAGE')
    if (modelId.value === null && models.value.length > 0) {
      const def = models.value.find((m) => m.isDefault === 1)
      modelId.value = def?.id ?? models.value[0].id
    }
  } catch {
    // 错误已由拦截器提示
  }
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const res = await getAiImagePage({ pageNum: pagination.page, pageSize: pagination.pageSize })
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

function viewHistory(row: AiImageTask) {
  taskId.value = row.taskId
  taskStatus.value = row.status
  resultUrl.value = row.url
  errorMsg.value = row.errorMsg
}

async function handleCreate() {
  if (!modelId.value) {
    message.warning('请选择图片模型')
    return
  }
  if (!prompt.value.trim()) {
    message.warning('请输入提示词')
    return
  }
  creating.value = true
  resultUrl.value = null
  errorMsg.value = null
  taskStatus.value = ''
  try {
    const id = await createAiImage({
      modelId: modelId.value,
      prompt: prompt.value.trim(),
      size: size.value,
      ratio: ratio.value,
    })
    taskId.value = id
    taskStatus.value = 'generating'
    message.success('任务已创建，正在生成...')
    await refreshHistory()
    startPolling()
  } catch {
    // 错误已由拦截器提示
  } finally {
    creating.value = false
  }
}

function startPolling() {
  if (!taskId.value) return
  polling.value = true
  pollStart = Date.now()
  pollTimer = window.setInterval(pollOnce, 3000)
}

async function pollOnce() {
  if (!taskId.value) return
  try {
    const res: AiImageTask = await getAiImageTask(taskId.value)
    taskStatus.value = res.status
    if (res.status === 'completed') {
      stopPolling()
      resultUrl.value = res.url
      message.success('图片生成完成')
      await refreshHistory()
    } else if (res.status === 'failed') {
      stopPolling()
      errorMsg.value = res.errorMsg
      message.error(res.errorMsg || '图片生成失败')
      await refreshHistory()
    } else if (Date.now() - pollStart > 5 * 60 * 1000) {
      stopPolling()
      message.warning('轮询超时，请稍后重试')
    }
  } catch {
    // 单次查询失败不中断轮询
  }
}

function stopPolling() {
  polling.value = false
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onBeforeUnmount(stopPolling)
onMounted(() => {
  loadModels()
  loadHistory()
})
</script>

<template>
  <div class="image-page">
    <AiGeneratorLayout aside-title="AI 图片生成">
      <template #aside>
        <NSpace vertical :size="16">
          <NAlert v-if="models.length === 0" type="warning" :bordered="false">
            暂无可用的图片模型，请联系管理员在 系统管理 → AI 配置 中添加 IMAGE 类型模型。
          </NAlert>

          <div>
            <div class="field-label">模型</div>
            <NSelect
              v-model:value="modelId"
              :options="modelOptions"
              placeholder="选择图片模型"
              :disabled="models.length === 0"
            />
          </div>

          <div>
            <div class="field-label">提示词</div>
            <NInput
              v-model:value="prompt"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 6 }"
              placeholder="描述要生成的图片，如：一只在月球上弹吉他的猫，赛博朋克风格"
            />
          </div>

          <div>
            <div class="field-label">尺寸档位 / 宽高比</div>
            <NSpace>
              <NSelect v-model:value="size" :options="sizeOptions" style="width: 160px" />
              <NSelect v-model:value="ratio" :options="ratioOptions" style="width: 160px" />
            </NSpace>
          </div>

          <NSpace>
            <NButton
              type="primary"
              :loading="creating"
              :disabled="models.length === 0 || !prompt.trim() || polling"
              @click="handleCreate"
            >
              生成图片
            </NButton>
            <NButton v-if="polling" type="error" @click="stopPolling">停止轮询</NButton>
          </NSpace>

          <NAlert v-if="!authStore.isLogin" type="info" :bordered="false">
            游客每日 2 次（受 IP 限流）。历史按 IP 记录，同网络下他人可见。
          </NAlert>
        </NSpace>
      </template>

      <template #result>
        <div v-if="polling || (taskStatus && taskStatus !== 'completed' && taskStatus !== 'failed')" class="loading-box">
          生成中，请稍候（通常 10-60 秒）...
        </div>
        <img
          v-else-if="resultUrl"
          :src="resultUrl"
          alt="生成结果"
          class="result-img"
        />
        <NEmpty v-else-if="errorMsg" :description="errorMsg" />
        <NEmpty v-else description="暂无结果，输入提示词后点击生成" />
      </template>

      <template #history>
        <NDataTable
          :columns="columns"
          :data="history"
          :loading="historyLoading"
          :pagination="pagination"
          :scroll-x="1000"
          remote
          striped
          size="small"
        />
      </template>
    </AiGeneratorLayout>
  </div>
</template>

<style scoped>
.image-page {
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

.loading-box {
  padding: 40px 0;
  text-align: center;
  opacity: 0.6;
}

.result-img {
  width: 100%;
  max-height: 600px;
  object-fit: contain;
  border-radius: 4px;
}
</style>
