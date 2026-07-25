<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  NCard, NSpace, NInput, NSelect, NButton, NEmpty, NAlert, NProgress, useMessage,
} from 'naive-ui'
import { createAiVideo, getAiVideoStatus, getAiModelsByType } from '@/api/ai'
import type { AiModel, AiVideoStatus } from '@/types/api'

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
  taskStatus.value = ''
  progress.value = 0
  try {
    const sz = sizeMap[ratio.value]
    const du = durationMap[duration.value]
    const res = await createAiVideo({
      modelId: modelId.value,
      prompt: prompt.value.trim(),
      width: sz.width,
      height: sz.height,
      numFrames: du.numFrames,
      frameRate: du.frameRate,
    })
    taskVideoId.value = res.videoId
    taskModelId.value = modelId.value
    taskStatus.value = res.status
    message.success('任务已创建，正在生成...')
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
    } else if (res.status === 'failed') {
      stopPolling()
      message.error('视频生成失败')
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
onMounted(loadModels)
</script>

<template>
  <div class="video-page">
    <NCard title="AI 视频生成" :bordered="false">
      <NSpace vertical :size="16">
        <NAlert v-if="models.length === 0" type="warning" :bordered="false">
          暂无可用的视频生成模型，请联系管理员在 系统管理 → AI 配置 中添加 VIDEO 类型模型。
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
            <NSelect v-model:value="ratio" :options="ratioOptions" style="width: 160px" />
          </div>
          <div>
            <div class="field-label">时长</div>
            <NSelect v-model:value="duration" :options="durationOptions" style="width: 180px" />
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
          视频生成为异步任务，创建后自动轮询（每 5 秒），通常需要 1-5 分钟。每日限 3 次。
        </NAlert>
      </NSpace>
    </NCard>

    <NCard v-if="polling || taskStatus || resultUrl" title="生成结果" :bordered="false">
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
      <NEmpty v-else description="无结果" />
    </NCard>
  </div>
</template>

<style scoped>
.video-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 720px;
  margin: 0 auto;
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
