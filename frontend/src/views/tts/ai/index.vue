<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  NCard, NSpace, NButton, NIcon, NSelect, NInput, NSlider, NTag,
  NEmpty, NAlert, useMessage,
} from 'naive-ui'
import type { SelectOption } from 'naive-ui'
import { iconMap } from '@/utils/icons'

const message = useMessage()

const text = ref('欢迎使用 AI 语音合成，输入文本后选择音色与情感风格即可生成富有表现力的语音。')
const modelValue = ref('standard')
const emotionValue = ref('neutral')
const rate = ref(1)
const pitch = ref(1)
const volume = ref(1)
const generating = ref(false)

const modelOptions: SelectOption[] = [
  { label: '标准女声', value: 'standard' },
  { label: '标准男声', value: 'standard-m' },
  { label: '温柔女声', value: 'gentle' },
  { label: '磁性男声', value: 'magnetic' },
  { label: '童声', value: 'child' },
]

const emotionOptions: SelectOption[] = [
  { label: '平静', value: 'neutral' },
  { label: '欢快', value: 'happy' },
  { label: '悲伤', value: 'sad' },
  { label: '愤怒', value: 'angry' },
  { label: '惊讶', value: 'surprise' },
]

const charCount = computed(() => text.value.length)
const canGenerate = computed(() => text.value.trim().length > 0)

function handleGenerate() {
  if (!canGenerate.value) {
    message.warning('请输入需要合成的文本')
    return
  }
  generating.value = true
  message.info('AI 语音合成服务尚未接入，敬请期待')
  setTimeout(() => {
    generating.value = false
  }, 800)
}

function handleClear() {
  text.value = ''
}
</script>

<template>
  <div class="tts-page">
    <NCard title="AI 语音合成" :bordered="false">
      <template #header-extra>
        <NTag type="info" size="small" round>
          <template #icon><NIcon><component :is="iconMap.sparkles" /></NIcon></template>
          神经网络语音
        </NTag>
      </template>
      <NSpace vertical :size="16">
        <NAlert type="info" :bordered="false">
          AI 语音合成基于深度神经网络模型，可生成富有情感表现力的高质量语音。该功能需接入后端 AI 服务，当前为界面预览。
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

        <div class="form-grid">
          <div class="form-row">
            <span class="form-label">音色模型</span>
            <NSelect
              v-model:value="modelValue"
              :options="modelOptions"
              style="width: 100%"
            />
          </div>
          <div class="form-row">
            <span class="form-label">情感风格</span>
            <NSelect
              v-model:value="emotionValue"
              :options="emotionOptions"
              style="width: 100%"
            />
          </div>
        </div>

        <div class="params">
          <div class="param-item">
            <div class="param-head">
              <span>语速</span>
              <NTag size="small" :bordered="false">{{ rate.toFixed(1) }}</NTag>
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
              <NTag size="small" :bordered="false">{{ volume.toFixed(1) }}</NTag>
            </div>
            <NSlider v-model:value="volume" :min="0" :max="1" :step="0.1" />
          </div>
        </div>

        <NSpace>
          <NButton
            type="primary"
            :loading="generating"
            :disabled="!canGenerate"
            @click="handleGenerate"
          >
            <template #icon><NIcon><component :is="iconMap.sparkles" /></NIcon></template>
            生成语音
          </NButton>
          <NButton @click="handleClear">
            <template #icon><NIcon><component :is="iconMap.trash" /></NIcon></template>
            清空
          </NButton>
        </NSpace>
      </NSpace>
    </NCard>

    <NCard title="音频预览" :bordered="false">
      <NEmpty description="生成后将在此处播放音频">
        <template #icon>
          <NIcon size="48" color="#999">
            <component :is="iconMap.volume" />
          </NIcon>
        </template>
      </NEmpty>
    </NCard>

    <NCard title="使用说明" :bordered="false">
      <ul class="tips">
        <li>AI 语音合成支持多种音色模型与情感风格，可生成富有表现力的语音。</li>
        <li>情感风格会影响语音的语气与情绪，例如欢快、悲伤、愤怒等。</li>
        <li>语速范围 0.5 ~ 2.0，音调范围 0 ~ 2.0，音量范围 0 ~ 1.0，默认均为 1.0。</li>
        <li>当前文本长度：<b>{{ charCount }}</b> / 2000 字。</li>
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

  .params {
    grid-template-columns: 1fr;
    gap: 16px;
  }
}
</style>
