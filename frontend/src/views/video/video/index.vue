<script setup lang="ts">
import { h, computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  NSpace, NInput, NSelect, NButton, NEmpty, NAlert, NProgress, NDataTable, NTag,
  useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import AiGeneratorLayout from '../components/AiGeneratorLayout.vue'
import { createAiVideo, getAiVideoStatus, getAiVideoPage, getAiModelsByType } from '@/api/ai'
import type { AiModel, AiVideoStatus, AiVideoHistory } from '@/types/api'

const message = useMessage()

const models = ref<AiModel[]>([])
const modelId = ref<number | null>(null)
const prompt = ref('')
const ratio = ref('16:9')
const duration = ref('5')
const creating = ref(false)

// 轮询状态
const taskVideoId = ref<string | null>(null)
const taskModelId = ref<number | null>(null)
const taskStatus = ref<string>('')
const progress = ref(0)
const polling = ref(false)
let pollTimer: number | null = null
let pollStart = 0

// 结果
const resultUrl = ref<string | null>(null)
const resultSize = ref('')
const resultSeconds = ref('')
const errorMsg = ref<string | null>(null)

// 历史
const history = ref<AiVideoHistory[]>([])
const historyLoading = ref(false)

const modelOptions = computed(() =>
  models.value.map((m) => ({
    value: m.id,
    label: m.providerName ? `${m.model} (${m.providerName})` : m.model,
  })),
)

const ratioOptions = [
  { value: '16:9', label: '16:9 横版' },
  { value: '9:16', label: '9:16 竖版' },
  { value: '1:1', label: '1:1 方形' },
]

const durationOptions = [
  { value: '3', label: '约 3 秒（81 帧）' },
  { value: '5', label: '约 5 秒（121 帧）' },
  { value: '10', label: '约 10 秒（241 帧）' },
]

// 比例 → 宽高
const sizeMap: Record<string, { width: number; height: number }> = {
  '16:9': { width: 1152, height: 768 },
  '9:16': { width: 768, height: 1152 },
  '1:1': { width: 768, height: 768 },
}

// 时长 → 帧数/帧率
const durationMap: Record<string, { numFrames: number; frameRate: number }> = {
  '3': { numFrames: 81, frameRate: 24 },
  '5': { numFrames: 121, frameRate: 24 },
  '10': { numFrames: 241, frameRate: 24 },
}

function formatTime(s?: string | null): string {
  if (!s) return ''
  return s.replace('T', ' ').slice(0, 19)
}

const columns: DataTableColumns<AiVideoHistory> = [
  { title: '请求时间', key: 'createTime', width: 170, render: (row) => formatTime(row.createTime) },
  { title: '提示词', key: 'prompt', ellipsis: { tooltip: true } },
  {
    title: '比例/时长', key: 'ratio', width: 130,
    render: (row) => `${row.ratio ?? '-'} / ${row.duration != null ? row.duration + 's' : '-'}`,
  },
  {
    title: '结果', key: 'status', width: 90,
    render: (row) => {
      if (row.status === 'completed') return h(NTag, { type: 'success', size: 'small' }, { default: () => '成功' })
      if (row.status === 'failed') return h(NTag, { type: 'error', size: 'small' }, { default: () => '失败' })
      return h(NTag, { size: 'small' }, { default: () => '生成中' })
    },
  },
  { title: '返回时间', key: 'updateTime', width: 170, render: (row) => formatTime(row.updateTime) },
  {
    title: '耗时', key: 'durationMs', width: 110,
    render: (row) => (row.durationMs != null ? `${(row.durationMs / 1000).toFixed(1)} s` : '-'),
  },
  {
    title: '操作', key: 'actions', width: 90, fixed: 'right',
    render: (row) => h(NButton, { size: 'small', disabled: !row.url, onClick: () => viewHistory(row) }, { default: () => '查看' }),
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
    models.value = await getAiModelsByType('VIDEO')
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
    const res = await getAiVideoPage({ pageNum: pagination.page, pageSize: pagination.pageSize })
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

function viewHistory(row: AiVideoHistory) {
  resultUrl.value = row.url
  taskStatus.value = row.status
  errorMsg.value = row.status === 'failed' ? (row.errorMsg || '生成失败') : null
  resultSize.value = ''
  resultSeconds.value = ''
}

async function handleCreate() {
  if (!modelId.value) {
    message.warning('请选择视频模型')
    return
  }
  if (!prompt.value.trim()) {
    message.warning('请输入提示词')
    return
  }
  creating.value = true
  resultUrl.value = null
  resultSize.value = ''
  resultSeconds.value = ''
  errorMsg.value = null
  taskStatus.value = ''
  progress.value = 0
  try {
    const sz = sizeMap[ratio.value]
    const du = durationMap[duration.value]
    const res = await createAiVideo({
      modelId: modelId.value,
      prompt: prompt.value.trim(),
      ratio: ratio.value,
      duration: Number(duration.value),
      width: sz.width,
      height: sz.height,
      numFrames: du.numFrames,
      frameRate: du.frameRate,
    })
    taskVideoId.value = res.videoId
    taskModelId.value = modelId.value
    taskStatus.value = res.status
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
  if (!taskVideoId.value || !taskModelId.value) return
  polling.value = true
  pollStart = Date.now()
  pollTimer = window.setInterval(pollOnce, 5000)
}

async function pollOnce() {
  if (!taskVideoId.value || !taskModelId.value) return
  try {
    const res: AiVideoStatus = await getAiVideoStatus(taskModelId.value, taskVideoId.value)
    taskStatus.value = res.status
    progress.value = res.progress
    if (res.status === 'completed') {
      stopPolling()
      resultUrl.value = res.videoUrl
      resultSize.value = res.size
      resultSeconds.value = res.seconds
      message.success('视频生成完成')
      await refreshHistory()
    } else if (res.status === 'failed') {
      stopPolling()
      errorMsg.value = '视频生成失败'
      message.error('视频生成失败')
      await refreshHistory()
    } else if (Date.now() - pollStart > 5 * 60 * 1000) {
      stopPolling()
      message.warning('轮询超时，请稍后重试或联系管理员')
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

const statusText = computed(() => {
  const map: Record<string, string> = {
    queued: '排队中',
    in_progress: '生成中',
    completed: '已完成',
    failed: '失败',
    '': '',
  }
  return map[taskStatus.value] ?? taskStatus.value
})

onBeforeUnmount(stopPolling)
onMounted(() => {
  loadModels()
  loadHistory()
})
</script>

<template>
  <div class="video-page">
    <AiGeneratorLayout aside-title="AI 视频生成">
      <template #aside>
        <NSpace vertical :size="16">
          <NAlert v-if="models.length === 0" type="warning" :bordered="false">
            暂无可用的视频模型，请联系管理员在 系统管理 → AI 配置 中添加 VIDEO 类型模型。
          </NAlert>

          <div>
            <div class="field-label">模型</div>
            <NSelect
              v-model:value="modelId"
              :options="modelOptions"
              placeholder="选择视频模型"
              :disabled="models.length === 0"
            />
          </div>

          <div>
            <div class="field-label">提示词</div>
            <NInput
              v-model:value="prompt"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 6 }"
              placeholder="描述要生成的视频，如：一只猫在海滩上散步，夕阳金色光线，电影级写实"
            />
          </div>

          <NSpace>
            <div>
              <div class="field-label">画面比例</div>
              <NSelect v-model:value="ratio" :options="ratioOptions" style="width: 150px" />
            </div>
            <div>
              <div class="field-label">时长</div>
              <NSelect v-model:value="duration" :options="durationOptions" style="width: 170px" />
            </div>
          </NSpace>

          <NSpace>
            <NButton
              type="primary"
              :loading="creating"
              :disabled="models.length === 0 || !prompt.trim() || polling"
              @click="handleCreate"
            >
              生成视频
            </NButton>
            <NButton v-if="polling" type="error" @click="stopPolling">停止轮询</NButton>
          </NSpace>

          <NAlert type="info" :bordered="false">
            视频生成为异步任务，创建后自动轮询（每 5 秒），通常 1-5 分钟。每日限 3 次。
          </NAlert>
        </NSpace>
      </template>

      <template #result>
        <div v-if="polling || (taskStatus && taskStatus !== 'completed' && taskStatus !== 'failed')" class="progress-box">
          <div class="progress-status">{{ statusText }} · {{ progress }}%</div>
          <NProgress
            type="line"
            :percentage="progress"
            :show-indicator="false"
            status="success"
          />
        </div>
        <video
          v-else-if="resultUrl"
          :src="resultUrl"
          controls
          autoplay
          style="width: 100%; max-height: 500px"
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
.video-page {
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

.progress-box {
  padding: 20px 0;
}

.progress-status {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 500;
}
</style>
