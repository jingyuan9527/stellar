<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  NCard, NSpace, NInput, NSelect, NButton, NEmpty, NAlert, NImage, useMessage,
} from 'naive-ui'
import { getAiModelsByType, generateAiImage } from '@/api/ai'
import { useAuthStore } from '@/store/auth'
import type { AiModel } from '@/types/api'

const message = useMessage()
const authStore = useAuthStore()

const models = ref<AiModel[]>([])
const modelId = ref<number | null>(null)
const prompt = ref('')
const size = ref('1024x1024')
const generating = ref(false)
const resultUrl = ref<string | null>(null)

const modelOptions = computed(() =>
  models.value.map((m) => ({
    value: m.id,
    label: m.providerName ? `${m.model} (${m.providerName})` : m.model,
  })),
)

const sizeOptions = [
  { value: '1024x1024', label: '1024×1024（方形）' },
  { value: '1024x1792', label: '1024×1792（竖图）' },
  { value: '1792x1024', label: '1792×1024（横图）' },
  { value: '512x512', label: '512×512（小图）' },
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

async function handleGenerate() {
  if (!modelId.value) {
    message.warning('请选择图片模型')
    return
  }
  if (!prompt.value.trim()) {
    message.warning('请输入提示词')
    return
  }
  generating.value = true
  resultUrl.value = null
  try {
    const res = await generateAiImage({
      modelId: modelId.value,
      prompt: prompt.value.trim(),
      size: size.value,
    })
    resultUrl.value = res.url
    message.success('生成成功')
  } catch {
    // 错误已由拦截器提示
  } finally {
    generating.value = false
  }
}

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
          <div class="field-label">尺寸</div>
          <NSelect v-model:value="size" :options="sizeOptions" />
        </div>

        <NSpace>
          <NButton
            type="primary"
            :loading="generating"
            :disabled="models.length === 0 || !prompt.trim()"
            @click="handleGenerate"
          >
            生成图片
          </NButton>
        </NSpace>

        <NAlert v-if="!authStore.isLogin" type="info" :bordered="false">
          游客每日可生成 2 次，登录后无此限制（受 IP 限流保护）。
        </NAlert>
      </NSpace>
    </NCard>

    <NCard v-if="generating || resultUrl" title="生成结果" :bordered="false">
      <div v-if="generating" class="loading-box">生成中，请稍候（图片生成通常需要 10-30 秒）...</div>
      <NImage
        v-else-if="resultUrl"
        :src="resultUrl"
        :width="'100%'"
        object-fit="contain"
        style="max-height: 600px"
      />
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
