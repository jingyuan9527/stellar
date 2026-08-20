<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NCard, NSpace, NButton, NIcon, NSelect, NInput, NSlider, NTag,
  NDivider, NEmpty, NAlert, NTabs, NTabPane, useMessage,
} from 'naive-ui'
import type { SelectGroupOption, SelectOption } from 'naive-ui'
import { iconMap } from '@/utils/icons'
import { useAuthStore } from '@/store/auth'
import { synthesizeEdgeTts, synthesizeAiTts, getTtsRecordPage, getTtsRecordAudio } from '@/api/tts'
import { getAiModelsByType } from '@/api/ai'
import type { AiModel, TtsRecord } from '@/types/api'
import { formatTime } from '@/utils/format'
import { ttsVoiceOptions, mimoVoiceOptions, getVoiceLabel } from '@/constants/tts-voices'

const router = useRouter()
const message = useMessage()
const authStore = useAuthStore()

const activeTab = ref<'edge' | 'ai'>('edge')

// ===== Edge TTS =====
const edgeText = ref('欢迎使用 Edge 语音合成，输入文本后选择发音人即可生成高质量神经网络语音。')
const edgeVoice = ref('zh-CN-XiaoxiaoNeural')
const edgeRate = ref(1)
const edgePitch = ref(1)
const edgeVolume = ref(1)
const edgeSynthesizing = ref(false)

// ===== AI TTS =====
const aiText = ref('欢迎使用 AI 语音合成，输入文本后选择音色与风格指令，即可生成富有表现力的神经网络语音。')
const aiModelId = ref<number | null>(null)
const aiVoice = ref('冰糖')
const aiStyle = ref('')
const aiGenerating = ref(false)
const models = ref<AiModel[]>([])
const modelsLoading = ref(false)

// ===== Shared =====
const audioUrl = ref<string | null>(null)
const recentRecords = ref<TtsRecord[]>([])
const historyLoadingId = ref<number | null>(null)

const edgeVoiceOptions: Array<SelectGroupOption | SelectOption> = ttsVoiceOptions
const aiVoiceOptions: Array<SelectGroupOption | SelectOption> = mimoVoiceOptions

const edgeCanSynthesize = computed(() => edgeText.value.trim().length > 0 && !edgeSynthesizing.value)
const aiCanGenerate = computed(() => aiText.value.trim().length > 0 && aiModelId.value !== null && !aiGenerating.value)
const hasModel = computed(() => models.value.length > 0)

const modelOptions = computed<SelectOption[]>(() =>
  models.value.map((m) => ({
    label: m.providerName ? `${m.model}（${m.providerName}）` : m.model,
    value: m.id,
  })),
)

function formatFileSize(bytes?: number): string {
  if (!bytes) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function revokeAudioUrl() {
  if (audioUrl.value) {
    URL.revokeObjectURL(audioUrl.value)
    audioUrl.value = null
  }
}

async function loadModels() {
  modelsLoading.value = true
  try {
    const list = await getAiModelsByType('AUDIO')
    models.value = list
    const def = list.find((m) => m.isDefault === 1)
    aiModelId.value = def ? def.id : (list[0]?.id ?? null)
  } catch { /* interceptor handles */ } finally {
    modelsLoading.value = false
  }
}

async function loadRecentRecords() {
  try {
    const res = await getTtsRecordPage({ pageNum: 1, pageSize: 8 })
    recentRecords.value = res.records
  } catch { /* silent */ }
}

async function handleEdgeSynthesize() {
  if (!edgeCanSynthesize.value) return
  revokeAudioUrl()
  edgeSynthesizing.value = true
  try {
    const blob = await synthesizeEdgeTts({
      text: edgeText.value,
      voice: edgeVoice.value as string,
      rate: edgeRate.value,
      pitch: edgePitch.value,
      volume: edgeVolume.value,
    })
    audioUrl.value = URL.createObjectURL(blob)
    message.success('语音合成成功')
    if (authStore.isLogin) loadRecentRecords()
  } catch { /* interceptor handles */ } finally {
    edgeSynthesizing.value = false
  }
}

async function handleAiGenerate() {
  if (!aiCanGenerate.value) return
  if (!authStore.isLogin) {
    message.warning('AI 语音合成需登录后使用')
    return
  }
  revokeAudioUrl()
  aiGenerating.value = true
  try {
    const blob = await synthesizeAiTts({
      modelId: aiModelId.value!,
      text: aiText.value,
      voice: aiVoice.value as string,
      style: aiStyle.value.trim() || undefined,
    })
    audioUrl.value = URL.createObjectURL(blob)
    message.success('AI 语音合成成功')
    loadRecentRecords()
  } catch { /* interceptor handles */ } finally {
    aiGenerating.value = false
  }
}

async function handlePlayFromHistory(row: TtsRecord) {
  historyLoadingId.value = row.id
  revokeAudioUrl()
  try {
    const blob = await getTtsRecordAudio(row.id)
    audioUrl.value = URL.createObjectURL(blob)
  } catch { /* interceptor handles */ } finally {
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
  } catch { /* interceptor handles */ } finally {
    historyLoadingId.value = null
  }
}

function handleDownload() {
  if (!audioUrl.value) return
  const ext = activeTab.value === 'ai' ? 'wav' : 'mp3'
  const a = document.createElement('a')
  a.href = audioUrl.value
  a.download = `tts_${Date.now()}.${ext}`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

function goToAiConfig() {
  router.push('/ai/config')
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
    <NCard title="语音合成" :bordered="false">
      <NTabs v-model:value="activeTab" type="segment" animated>
        <NTabPane name="edge" tab="Edge TTS">
          <NSpace vertical :size="16" style="padding-top: 12px">
            <NAlert type="info" :bordered="false">
              基于 Microsoft Edge 在线神经网络语音合成，支持多种中文发音人，合成 MP3 音频。
            </NAlert>

            <div class="form-row">
              <span class="form-label">合成文本</span>
              <NInput
                v-model:value="edgeText"
                type="textarea"
                placeholder="请输入需要合成语音的文本"
                :autosize="{ minRows: 4, maxRows: 10 }"
                maxlength="2000"
                show-count
              />
            </div>

            <div class="form-row">
              <span class="form-label">发音人</span>
              <NSelect
                v-model:value="edgeVoice"
                :options="edgeVoiceOptions"
                placeholder="选择发音人"
                filterable
                style="max-width: 420px"
              />
            </div>

            <NDivider style="margin: 4px 0" />

            <div class="params">
              <div class="param-item">
                <div class="param-head">
                  <span>语速</span>
                  <NTag size="small" :bordered="false">{{ edgeRate.toFixed(1) }}x</NTag>
                </div>
                <NSlider v-model:value="edgeRate" :min="0.5" :max="2" :step="0.1" />
              </div>
              <div class="param-item">
                <div class="param-head">
                  <span>音调</span>
                  <NTag size="small" :bordered="false">{{ edgePitch.toFixed(1) }}</NTag>
                </div>
                <NSlider v-model:value="edgePitch" :min="0" :max="2" :step="0.1" />
              </div>
              <div class="param-item">
                <div class="param-head">
                  <span>音量</span>
                  <NTag size="small" :bordered="false">{{ Math.round(edgeVolume * 100) }}%</NTag>
                </div>
                <NSlider v-model:value="edgeVolume" :min="0" :max="1" :step="0.1" />
              </div>
            </div>

            <NSpace>
              <NButton type="primary" :loading="edgeSynthesizing" :disabled="!edgeCanSynthesize" @click="handleEdgeSynthesize">
                <template #icon><NIcon><component :is="iconMap.sparkles" /></NIcon></template>
                {{ edgeSynthesizing ? '合成中...' : '合成语音' }}
              </NButton>
              <NButton :disabled="!audioUrl" @click="handleDownload">
                <template #icon><NIcon><component :is="iconMap.download" /></NIcon></template>
                下载 MP3
              </NButton>
            </NSpace>
          </NSpace>
        </NTabPane>

        <NTabPane name="ai" tab="AI TTS">
          <NSpace vertical :size="16" style="padding-top: 12px">
            <NAlert type="info" :bordered="false">
              基于 MiMo-V2.5-TTS 神经网络语音合成，支持预置音色与自然语言风格控制，生成 WAV 音频。需登录。
            </NAlert>

            <NAlert v-if="!hasModel && !modelsLoading" type="warning" :bordered="false">
              暂无可用 AUDIO 类型模型，请先在
              <NButton text type="primary" @click="goToAiConfig">AI 配置</NButton>
              中添加。
            </NAlert>

            <div class="form-row">
              <span class="form-label">合成文本</span>
              <NInput
                v-model:value="aiText"
                type="textarea"
                placeholder="请输入需要合成语音的文本"
                :autosize="{ minRows: 4, maxRows: 10 }"
                maxlength="2000"
                show-count
              />
            </div>

            <div class="form-grid">
              <div class="form-row">
                <span class="form-label">AI 模型</span>
                <NSelect
                  v-model:value="aiModelId"
                  :options="modelOptions"
                  :loading="modelsLoading"
                  placeholder="选择 AUDIO 类型模型"
                  filterable
                />
              </div>
              <div class="form-row">
                <span class="form-label">预置音色</span>
                <NSelect
                  v-model:value="aiVoice"
                  :options="aiVoiceOptions"
                  placeholder="选择音色"
                  filterable
                />
              </div>
            </div>

            <div class="form-row">
              <span class="form-label">风格指令（可选）</span>
              <NInput
                v-model:value="aiStyle"
                type="textarea"
                placeholder="用自然语言描述风格，如：用轻快上扬的语调，语速稍快。也可留空。"
                :autosize="{ minRows: 2, maxRows: 4 }"
                maxlength="500"
              />
            </div>

            <NSpace>
              <NButton type="primary" :loading="aiGenerating" :disabled="!aiCanGenerate" @click="handleAiGenerate">
                <template #icon><NIcon><component :is="iconMap.sparkles" /></NIcon></template>
                {{ aiGenerating ? '合成中...' : '生成语音' }}
              </NButton>
              <NButton :disabled="!audioUrl" @click="handleDownload">
                <template #icon><NIcon><component :is="iconMap.download" /></NIcon></template>
                下载 WAV
              </NButton>
            </NSpace>
          </NSpace>
        </NTabPane>
      </NTabs>
    </NCard>

    <NCard title="音频预览" :bordered="false">
      <div v-if="audioUrl" class="audio-player">
        <audio :src="audioUrl" controls style="width: 100%" />
      </div>
      <NEmpty v-else description="合成后将在此处播放音频">
        <template #icon>
          <NIcon size="48" color="var(--c-text-3)"><component :is="iconMap.volume" /></NIcon>
        </template>
      </NEmpty>
    </NCard>

    <NCard v-if="authStore.isLogin" title="最近合成" :bordered="false">
      <NEmpty v-if="!recentRecords.length" description="暂无合成记录" size="small" />
      <NSpace v-else vertical :size="8">
        <div v-for="row in recentRecords" :key="row.id" class="recent-item">
          <div class="recent-info">
            <span class="recent-text">{{ row.text.length > 40 ? row.text.slice(0, 40) + '...' : row.text }}</span>
            <NSpace size="small" align="center" style="margin-top: 4px">
              <NTag size="tiny" :type="row.audioFormat === 'wav' ? 'info' : 'success'" :bordered="false">
                {{ row.audioFormat === 'wav' ? 'AI' : 'Edge' }}
              </NTag>
              <NTag size="tiny" type="info" :bordered="false">{{ getVoiceLabel(row.voice) }}</NTag>
              <span class="recent-meta">{{ formatTime(row.createTime) }}</span>
              <span class="recent-meta">{{ formatFileSize(row.fileSize) }}</span>
            </NSpace>
          </div>
          <NSpace size="small">
            <NButton size="tiny" text type="primary" :loading="historyLoadingId === row.id" @click="handlePlayFromHistory(row)">
              <template #icon><NIcon><component :is="iconMap.play" /></NIcon></template>
              试听
            </NButton>
            <NButton size="tiny" text type="info" :disabled="historyLoadingId === row.id" @click="handleDownloadFromHistory(row)">
              <template #icon><NIcon><component :is="iconMap.download" /></NIcon></template>
              下载
            </NButton>
          </NSpace>
        </div>
      </NSpace>
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
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.params {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.param-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.param-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
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
  color: var(--n-text-color-3, var(--c-text-3));
}

@media (max-width: 768px) {
  .params {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
