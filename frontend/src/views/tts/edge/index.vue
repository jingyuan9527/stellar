<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NCard, NSpace, NButton, NIcon, NSelect, NInput, NSlider, NTag,
  NDivider, NEmpty, NAlert, NList, NListItem, NThing, useMessage,
} from 'naive-ui'
import type { SelectGroupOption, SelectOption } from 'naive-ui'
import { iconMap } from '@/utils/icons'
import { synthesizeEdgeTts, getTtsRecordPage, getTtsRecordAudio } from '@/api/tts'
import type { TtsRecord } from '@/types/api'
import { ttsVoiceOptions, getVoiceLabel } from '@/constants/tts-voices'

const router = useRouter()
const message = useMessage()

const text = ref('欢迎使用 Edge 语音合成，输入文本后选择发音人即可生成高质量神经网络语音。')
const voiceValue = ref('zh-CN-XiaoxiaoNeural')
const rate = ref(1)
const pitch = ref(1)
const volume = ref(1)
const synthesizing = ref(false)
const audioUrl = ref<string | null>(null)
const audioRef = ref<HTMLAudioElement | null>(null)

const voiceOptions: Array<SelectGroupOption | SelectOption> = ttsVoiceOptions

const charCount = computed(() => text.value.length)
const canSynthesize = computed(
  () => text.value.trim().length > 0 && !synthesizing.value,
)

// ===== 最近合成 =====
const recentRecords = ref<TtsRecord[]>([])
const historyLoadingId = ref<number | null>(null)

function formatTime(s?: string): string {
  if (!s) return ''
  return s.replace('T', ' ').slice(5, 16)
}

function formatFileSize(bytes?: number): string {
  if (!bytes) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

async function loadRecentRecords() {
  try {
    const res = await getTtsRecordPage({ pageNum: 1, pageSize: 5 })
    recentRecords.value = res.records
  } catch {
    // 静默失败，不影响主功能
  }
}

function revokeAudioUrl() {
  if (audioUrl.value) {
    URL.revokeObjectURL(audioUrl.value)
    audioUrl.value = null
  }
}

async function handleSynthesize() {
  if (!canSynthesize.value) return

  revokeAudioUrl()
  synthesizing.value = true
  try {
    const blob = await synthesizeEdgeTts({
      text: text.value,
      voice: voiceValue.value as string,
      rate: rate.value,
      pitch: pitch.value,
      volume: volume.value,
    })
    audioUrl.value = URL.createObjectURL(blob)
    message.success('语音合成成功')
    loadRecentRecords()
  } catch {
    // 错误已由拦截器提示
  } finally {
    synthesizing.value = false
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
    const a = document.createElement('a')
    a.href = url
    a.download = `tts_${row.id}_${row.voice}.mp3`
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
  a.download = `tts_${voiceValue.value}_${Date.now()}.mp3`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

function handleClear() {
  text.value = ''
  revokeAudioUrl()
}

function goToHistory() {
  router.push('/tts/history')
}

onMounted(() => {
  loadRecentRecords()
})

onBeforeUnmount(() => {
  revokeAudioUrl()
})
</script>

<template>
  <div class="tts-page">
    <NCard title="Edge 语音合成" :bordered="false">
      <template #header-extra>
        <NTag type="success" size="small" round>
          <template #icon><NIcon><component :is="iconMap.megaphone" /></NIcon></template>
          Microsoft Edge TTS
        </NTag>
      </template>
      <NSpace vertical :size="16">
        <NAlert type="info" :bordered="false">
          基于 Microsoft Edge 在线神经网络语音合成服务，支持多种中文发音人，合成后可在线试听与下载 MP3。
        </NAlert>

        <div class="form-row">
          <span class="form-label">合成文本</span>
          <NInput
            v-model:value="text"
            type="textarea"
            placeholder="请输入需要合成语音的文本"
            :autosize="{ minRows: 5, maxRows: 12 }"
            maxlength="2000"
            show-count
          />
        </div>

        <div class="form-row">
          <span class="form-label">发音人</span>
          <NSelect
            v-model:value="voiceValue"
            :options="voiceOptions"
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
              <NTag size="small" :bordered="false">{{ rate.toFixed(1) }}x</NTag>
            </div>
            <NSlider v-model:value="rate" :min="0.5" :max="2" :step="0.1" />
          </div>
          <div class="param-item">
            <div class="param-head">
              <span>音调</span>
              <NTag size="small" :bordered="false">{{ pitch.toFixed(1) }}</NTag>
            </div>
            <NSlider v-model:value="pitch" :min="0" :max="2" :step="0.1" />
          </div>
          <div class="param-item">
            <div class="param-head">
              <span>音量</span>
              <NTag size="small" :bordered="false">{{ Math.round(volume * 100) }}%</NTag>
            </div>
            <NSlider v-model:value="volume" :min="0" :max="1" :step="0.1" />
          </div>
        </div>

        <NSpace>
          <NButton
            type="primary"
            :loading="synthesizing"
            :disabled="!canSynthesize"
            @click="handleSynthesize"
          >
            <template #icon><NIcon><component :is="iconMap.sparkles" /></NIcon></template>
            {{ synthesizing ? '合成中...' : '合成语音' }}
          </NButton>
          <NButton
            :disabled="!audioUrl"
            @click="handleDownload"
          >
            <template #icon><NIcon><component :is="iconMap.download" /></NIcon></template>
            下载 MP3
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
        <audio ref="audioRef" :src="audioUrl" controls style="width: 100%" />
      </div>
      <NEmpty v-else description="合成后将在此处播放音频">
        <template #icon>
          <NIcon size="48" color="#999">
            <component :is="iconMap.volume" />
          </NIcon>
        </template>
      </NEmpty>
    </NCard>

    <NCard title="最近合成" :bordered="false">
      <template #header-extra>
        <NButton text type="primary" @click="goToHistory">
          查看全部
          <template #icon><NIcon><component :is="iconMap.expand" /></NIcon></template>
        </NButton>
      </template>
      <NEmpty v-if="!recentRecords.length" description="暂无合成记录" size="small" />
      <NList v-else hoverable clickable size="small">
        <NListItem v-for="row in recentRecords" :key="row.id">
          <NThing>
            <template #header>
              <span class="recent-text">{{ row.text.length > 40 ? row.text.slice(0, 40) + '...' : row.text }}</span>
            </template>
            <template #description>
              <NSpace size="small" align="center">
                <NTag size="tiny" type="info" :bordered="false">{{ getVoiceLabel(row.voice) }}</NTag>
                <span class="recent-meta">{{ formatTime(row.createTime) }}</span>
                <span class="recent-meta">{{ formatFileSize(row.fileSize) }}</span>
              </NSpace>
            </template>
            <template #header-extra>
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
            </template>
          </NThing>
        </NListItem>
      </NList>
    </NCard>

    <NCard title="使用说明" :bordered="false">
      <ul class="tips">
        <li>本功能调用 Microsoft Edge 在线 TTS 服务，由后端代理 WebSocket 合成，返回 MP3 音频。</li>
        <li>发音人涵盖普通话、东北话、陕西话、粤语、台湾国语，均为神经网络语音。</li>
        <li>语速范围 0.5 ~ 2.0，音调范围 0 ~ 2.0，音量范围 0 ~ 100%，默认均为标准值。</li>
        <li>合成后可在线试听，也可下载为 MP3 文件。当前文本长度：<b>{{ charCount }}</b> / 2000 字。</li>
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

.recent-text {
  font-size: 14px;
  line-height: 1.5;
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
  .params {
    grid-template-columns: 1fr;
    gap: 16px;
  }
}
</style>
