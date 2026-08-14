<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { NCard, NSpace, NButton, NInput, NIcon, NEmpty, NAlert, NTag, useMessage } from 'naive-ui'
import { iconMap } from '@/utils/icons'
import { askConch } from '@/api/conch'
import type { ConchAskResult } from '@/types/api'

const message = useMessage()

const question = ref('')
const asking = ref(false)
const result = ref<ConchAskResult | null>(null)
const audioUrl = ref<string | null>(null)
const audioRef = ref<HTMLAudioElement | null>(null)

const canAsk = computed(() => question.value.trim().length > 0 && !asking.value)

function revokeAudioUrl() {
  audioUrl.value = null
}

async function handleAsk() {
  if (!canAsk.value) return
  revokeAudioUrl()
  result.value = null
  asking.value = true
  try {
    const res = await askConch(question.value.trim())
    result.value = res
    // 直接用 src 请求音频（浏览器原生 + 走缓存），省 axios blob 开销
    audioUrl.value = `${import.meta.env.VITE_API_BASE_URL}${res.audioUrl}`
    await nextTick()
    audioRef.value?.play().catch(() => {})
  } catch {
    // 错误已由拦截器提示
  } finally {
    asking.value = false
  }
}

function handleClear() {
  question.value = ''
  result.value = null
  revokeAudioUrl()
}

onBeforeUnmount(() => {
  revokeAudioUrl()
})
</script>

<template>
  <div class="conch-page">
    <NCard title="神奇海螺" :bordered="false">
      <template #header-extra>
        <NTag size="small" type="warning" round :bordered="false">🐚 占卜</NTag>
      </template>
      <NSpace vertical :size="20">
        <NAlert type="info" :bordered="false">
          神奇海螺会倾听你的问题，从预设回答中选出最贴切的一条回答你。拉动绳子，等待海螺的启示。
        </NAlert>

        <div class="conch-stage">
          <div class="conch-shell" :class="{ shaking: asking }">🐚</div>
          <Transition name="answer-fade">
            <div v-if="result && !asking" class="conch-answer">
              <div class="answer-label">海螺说：</div>
              <div class="answer-text">{{ result.answerText }}</div>
            </div>
          </Transition>
          <NEmpty
            v-if="!result && !asking"
            description="拉动海螺的绳子，它将回答你"
            size="small"
          />
          <div v-if="asking" class="asking-hint">
            <NIcon size="20" class="loading-icon">
              <component :is="iconMap.refresh" />
            </NIcon>
            海螺正在思考...
          </div>
        </div>

        <div class="form-row">
          <NInput
            v-model:value="question"
            type="textarea"
            placeholder="向神奇海螺提出你的问题..."
            :autosize="{ minRows: 3, maxRows: 6 }"
            maxlength="500"
            show-count
            @keyup.enter="handleAsk"
          />
        </div>

        <NSpace>
          <NButton
            type="primary"
            size="large"
            :loading="asking"
            :disabled="!canAsk"
            @click="handleAsk"
          >
            <template #icon><NIcon><component :is="iconMap.conch" /></NIcon></template>
            {{ asking ? '拉绳中...' : '拉绳' }}
          </NButton>
          <NButton size="large" @click="handleClear">
            <template #icon><NIcon><component :is="iconMap.trash" /></NIcon></template>
            清空
          </NButton>
        </NSpace>

        <audio v-if="audioUrl" ref="audioRef" :src="audioUrl" controls style="width: 100%" />
      </NSpace>
    </NCard>

    <NCard title="使用说明" :bordered="false">
      <ul class="tips">
        <li>神奇海螺是海绵宝宝里的占卜道具，像神奇八号球：问问题，拉绳，随机得到一句预设回答。</li>
        <li>本站海螺由 AI 理解你的问题语义，从预设回答里挑出最贴切的几条，再随机选一条回答你。</li>
        <li>回答的语音由管理员预先上传，提问后自动播放，同时展示文字。</li>
        <li>对游客开放，按 IP 单日限 10 次。管理员可在"游戏 → 海螺管理"维护回答库。</li>
      </ul>
    </NCard>
  </div>
</template>

<style scoped>
.conch-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.conch-stage {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  min-height: 240px;
  padding: 24px;
  border-radius: 12px;
  background: var(--n-color, transparent);
  border: 1px solid rgba(127, 127, 127, 0.12);
}

.conch-shell {
  font-size: 96px;
  line-height: 1;
  filter: drop-shadow(0 4px 12px rgba(0, 0, 0, 0.15));
  transition: transform 0.2s;
}

.conch-shell.shaking {
  animation: shake 0.4s ease-in-out infinite;
}

@keyframes shake {
  0%, 100% { transform: rotate(0deg) scale(1); }
  25% { transform: rotate(-12deg) scale(1.05); }
  75% { transform: rotate(12deg) scale(1.05); }
}

.conch-answer {
  text-align: center;
}

.answer-label {
  font-size: 13px;
  opacity: 0.5;
  margin-bottom: 8px;
}

.answer-text {
  font-size: 30px;
  font-weight: 700;
  color: var(--primary-color, #18a058);
  line-height: 1.4;
}

.asking-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  opacity: 0.7;
}

.loading-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.answer-fade-enter-active {
  transition: all 0.4s ease;
}

.answer-fade-enter-from {
  opacity: 0;
  transform: translateY(12px);
}

.tips {
  margin: 0;
  padding-left: 20px;
  line-height: 1.9;
  color: var(--n-text-color-3, #999);
}

@media (max-width: 768px) {
  .conch-shell {
    font-size: 72px;
  }
  .answer-text {
    font-size: 22px;
  }
  .conch-stage {
    min-height: 200px;
    padding: 16px;
  }
}
</style>
