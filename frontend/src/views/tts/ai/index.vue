<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NCard, NSpace, NButton, NIcon, NSelect, NInput, NTag,
  NEmpty, NAlert, useMessage,
} from 'naive-ui'
import type { SelectGroupOption, SelectOption } from 'naive-ui'
import { iconMap } from '@/utils/icons'
import { useAuthStore } from '@/store/auth'
import { synthesizeAiTts, getTtsRecordPage, getTtsRecordAudio } from '@/api/tts'
import { getAiModelsByType } from '@/api/ai'
import type { AiModel, TtsRecord } from '@/types/api'
import { mimoVoiceOptions } from '@/constants/tts-voices'
import { formatTime } from '@/utils/format'

const router = useRouter()
const message = useMessage()
const authStore = useAuthStore()

const text = ref('欢迎使用 AI 语音合成，输入文本后选择音色与风格指令，即可生成富有表现力的神经网络语音。')
const modelId = ref<number | null>(null)
const voiceValue = ref('冰糖')
const style = ref('')
const generating = ref(false)
const audioUrl = ref<string | null>(null)

const models = ref<AiModel[]>([])
const modelsLoading = ref(false)

const voiceOptions: Array<SelectGroupOption | SelectOption> = mimoVoiceOptions

const charCount = computed(() => text.value.length)
const canGenerate = computed(
  () => text.value.trim().length > 0 && modelId.value !== null && !generating.value,
)
const hasModel = computed(() => models.value.length > 0)

function formatFileSize(bytes?: number): string {
  if (!bytes) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

async function loadModels() {
  modelsLoading.value = true
  try {
    const list = await getAiModelsByType('AUDIO')
    models.value = list
    const def = list.find((m) => m.isDefault === 1)
    modelId.value = def ? def.id : (list[0]?.id ?? null)
  } catch {
    // 错误已由拦截器提示
  } finally {
    modelsLoading.value = false
  }
}

const modelOptions = computed<SelectOption[]>(() =>
  models.value.map((m) => ({
    label: m.providerName ? `${m.model}（${m.providerName}）` : m.model,
    value: m.id,
  })),
)

// ===== 最近合成 =====
const recentRecords = ref<TtsRecord[]>([])
const historyLoadingId = ref<number | null>(null)

async function loadRecentRecords() {
  try {
    const res = await getTtsRecordPage({ pageNum: 1, pageSize: 5 })
    recentRecords.value = res.records
  } catch {
    // 静默失败
  }
}

function revokeAudioUrl() {
  if (audioUrl.value) {
    URL.revokeObjectURL(audioUrl.value)
    audioUrl.value = null
  }
}

async function handleGenerate() {
  if (!canGenerate.value) return
  if (!authStore.isLogin) {
    message.warning('AI 语音合成需登录后使用')
    return
  }
  revokeAudioUrl()
  generating.value = true
  try {
    const blob = await synthesizeAiTts({
      modelId: modelId.value!,
      text: text.value,
      voice: voiceValue.value as string,
      style: style.value.trim() || undefined,
    })
    audioUrl.value = URL.createObjectURL(blob)
    message.success('AI 语音合成成功')
    loadRecentRecords()
  } catch {
    // 错误已由拦截器提示
  } finally {
    generating.value = false
  }
}

async function handlePlayFromHistory(row: TtsRecord) {
  historyLoadingId.value = row.id
  revokeAudioUrl()
  try {
    const blob = await getTtsRecordAudio(row.id)
    audioUrl.value = URL.createObjectURL(blob)
    message.success('已加载历史音频')
  } catch {
    // 错误已由拦截器提示
  } finally {
    historyLoadingId.value = null
  }
}

async function handleDownloadFromHistory(row: TtsRecord) {
  historyLoadingId.value = row.id
  try {
    const blob = await getTtsRecordAudio(row.id)
    const url = URL.createObjectURL(blob)
    const ext = row.audioFormat === 'wav' ? 'wav' : 'mp3'
    const a = document.createElement('a')
    a.href = url
    a.download = `tts_${row.id}_${row.voice}.${ext}`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    message.success('下载成功')
  } catch {
    // 错误已由拦截器提示
  } finally {
    historyLoadingId.value = null
  }
}

function handleDownload() {
  if (!audioUrl.value) return
  const a = document.createElement('a')
  a.href = audioUrl.value
  a.download = `ai_tts_${voiceValue.value}_${Date.now()}.wav`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

function handleClear() {
  text.value = ''
  style.value = ''
  revokeAudioUrl()
}

function goToHistory() {
  router.push('/tts/history')
}

function goToAiConfig() {
  router.push('/system/ai-config')
}

onMounted(() => {
  loadModels()
  if (authStore.isLogin) loadRecentRecords()
})

onBeforeUnmount(() => {
  revokeAudioUrl()
})
</script>

<template>
  <div class="tts-page">
    <NCard title="AI 语音合成" :bordered="false">
      <template #header-extra>
        <NTag type="info" size="small" round>
          <template #icon><NIcon><component :is="iconMap.sparkles" /></NIcon></template>
          MiMo-V2.5-TTS
        </NTag>
      </template>
      <NSpace vertical :size="16">
        <NAlert type="info" :bordered="false">
          基于 MiMo-V2.5-TTS 神经网络语音合成，支持多种预置音色与自然语言风格控制，可生成富有情感表现力的高质量语音。
        </NAlert>

        <NAlert v-if="!hasModel && !modelsLoading" type="warning" :bordered="false">
          暂无可用 AUDIO 类型模型，请先在
          <NButton text type="primary" @click="goToAiConfig">AI 配置</NButton>
          中添加供应商与 AUDIO 模型（如 mimo-v2.5-tts）。
        </NAlert>

        <div class="form-row">
          <span class="form-label">合成文本</span>
          <NInput
            v-model:value="text"
            type="textarea"
            placeholder="请输入需要合成语音的文本（放在 assistant 消息，即实际播报内容）"
            :autosize="{ minRows: 5, maxRows: 12 }"
            maxlength="2000"
            show-count
          />
        </div>

        <div class="form-grid">
          <div class="form-row">
            <span class="form-label">AI 模型</span>
            <NSelect
              v-model:value="modelId"
              :options="modelOptions"
              :loading="modelsLoading"
              placeholder="选择 AUDIO 类型模型"
              filterable
              style="width: 100%"
            />
          </div>
          <div class="form-row">
            <span class="form-label">预置音色</span>
            <NSelect
              v-model:value="voiceValue"
              :options="voiceOptions"
              placeholder="选择音色"
              filterable
              style="width: 100%"
            />
          </div>
        </div>

        <div class="form-row">
          <span class="form-label">
            风格指令（可选）
            <NTag size="tiny" :bordered="false" type="info">放 user 消息</NTag>
          </span>
          <NInput
            v-model:value="style"
            type="textarea"
            placeholder="用自然语言描述风格，如：用轻快上扬的语调，语速稍快，带着激动与小骄傲。也可留空。"
            :autosize="{ minRows: 2, maxRows: 5 }"
            maxlength="500"
          />
        </div>

        <NSpace>
          <NButton
            type="primary"
            :loading="generating"
            :disabled="!canGenerate"
            @click="handleGenerate"
          >
            <template #icon><NIcon><component :is="iconMap.sparkles" /></NIcon></template>
            {{ generating ? '合成中...' : '生成语音' }}
          </NButton>
          <NButton :disabled="!audioUrl" @click="handleDownload">
            <template #icon><NIcon><component :is="iconMap.download" /></NIcon></template>
            下载 WAV
          </NButton>
          <NButton @click="handleClear">
            <template #icon><NIcon><component :is="iconMap.trash" /></NIcon></template>
            清空
          </NButton>
        </NSpace>
      </NSpace>
    </NCard>

    <NCard title="音频预览" :bordered="false">
      <div v-if="audioUrl" class="audio-player">
        <audio :src="audioUrl" controls style="width: 100%" />
      </div>
      <NEmpty v-else description="合成后将在此处播放音频">
        <template #icon>
          <NIcon size="48" color="#999">
            <component :is="iconMap.volume" />
          </NIcon>
        </template>
      </NEmpty>
    </NCard>

    <NCard v-if="authStore.isLogin" title="最近合成" :bordered="false">
      <template #header-extra>
        <NButton text type="primary" @click="goToHistory">
          查看全部
          <template #icon><NIcon><component :is="iconMap.expand" /></NIcon></template>
        </NButton>
      </template>
      <NEmpty v-if="!recentRecords.length" description="暂无合成记录" size="small" />
      <NSpace v-else vertical :size="8">
        <div v-for="row in recentRecords" :key="row.id" class="recent-item">
          <div class="recent-info">
            <span class="recent-text">{{ row.text.length > 40 ? row.text.slice(0, 40) + '...' : row.text }}</span>
            <NSpace size="small" align="center" style="margin-top: 4px">
              <NTag size="tiny" :type="row.audioFormat === 'wav' ? 'info' : 'success'" :bordered="false">
                {{ row.audioFormat === 'wav' ? 'AI' : 'Edge' }}
              </NTag>
              <NTag size="tiny" type="info" :bordered="false">{{ row.voice }}</NTag>
              <span class="recent-meta">{{ formatTime(row.createTime) }}</span>
              <span class="recent-meta">{{ formatFileSize(row.fileSize) }}</span>
            </NSpace>
          </div>
          <NSpace size="small">
            <NButton
              size="tiny"
              text
              type="primary"
              :loading="historyLoadingId === row.id"
              @click="handlePlayFromHistory(row)"
            >
              <template #icon><NIcon><component :is="iconMap.play" /></NIcon></template>
              试听
            </NButton>
            <NButton
              size="tiny"
              text
              type="info"
              :disabled="historyLoadingId === row.id"
              @click="handleDownloadFromHistory(row)"
            >
              <template #icon><NIcon><component :is="iconMap.download" /></NIcon></template>
              下载
            </NButton>
          </NSpace>
        </div>
      </NSpace>
    </NCard>

    <NCard title="使用说明" :bordered="false">
      <ul class="tips">
        <li>本功能调用 MiMo-V2.5-TTS 神经网络语音合成，需在「系统管理 → AI 配置」配置 AUDIO 类型模型。</li>
        <li>合成文本放在 assistant 消息（实际播报内容）；风格指令放在 user 消息，用自然语言描述语气、语速、情绪等。</li>
        <li>预置音色涵盖中文（冰糖/茉莉/苏打/白桦）与英文（Mia/Chloe/Milo/Dean），也可选 mimo_default 随集群默认。</li>
        <li>也可在合成文本开头加标签控制风格，如 (慵懒)再让我睡五分钟。或用自然语言指令更细腻地刻画风格。</li>
        <li>AI 语音合成需登录，合成音频为 WAV 格式，自动记入合成历史。当前文本长度：<b>{{ charCount }}</b> / 2000 字。</li>
      </ul>
    </NCard>
  </div>
</template>

<style scoped>
.tts-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  font-size: 14px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 8px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.audio-player {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.recent-info {
  flex: 1;
  min-width: 0;
}

.recent-text {
  font-size: 14px;
  line-height: 1.5;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-meta {
  font-size: 12px;
  color: var(--n-text-color-3, #999);
}

.tips {
  margin: 0;
  padding-left: 20px;
  line-height: 1.9;
  color: var(--n-text-color-3, #999);
}

.tips b {
  color: var(--primary-color, #18a058);
}

@media (max-width: 768px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
