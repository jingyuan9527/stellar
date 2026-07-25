<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  NCard, NSpace, NInput, NSelect, NButton, NEmpty, NAlert, NImage, useMessage,
} from 'naive-ui'
import { createAiImage, getAiImageTask, getAiModelsByType } from '@/api/ai'
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
    } else if (res.status === 'failed') {
      stopPolling()
      errorMsg.value = res.errorMsg
      message.error(res.errorMsg || '图片生成失败')
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
onMounted(loadModels)
</script>

<template>
  <div class="image-page">
    <NCard title="AI 图片生成" :bordered="false">
      <NSpace vertical :size="16">
        <NAlert v-if="models.length === 0" type="warning" :bordered="false">
          暂无可用的图片生成模型，请联系管理员在 系统管理 → AI 配置 中添加 IMAGE 类型模型。
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
          游客每日可生成 2 次，登录后无此限制（受 IP 限流保护）。异步生成，无需等待。
        </NAlert>
      </NSpace>
    </NCard>

    <NCard v-if="polling || taskStatus || resultUrl" title="生成结果" :bordered="false">
      <div v-if="polling || (taskStatus && taskStatus !== 'completed' && taskStatus !== 'failed')" class="loading-box">
        生成中，请稍候（通常 10-60 秒）...
      </div>
      <NImage
        v-else-if="resultUrl"
        :src="resultUrl"
        :width="'100%'"
        object-fit="contain"
        style="max-height: 600px"
      />
      <NEmpty v-else-if="errorMsg" :description="errorMsg" />
      <NEmpty v-else description="无结果" />
    </NCard>
  </div>
</template>

<style scoped>
.image-page {
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

.loading-box {
  padding: 40px 0;
  text-align: center;
  opacity: 0.6;
}
</style>
