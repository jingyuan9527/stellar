<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  NCard, NSpace, NInput, NAutoComplete, NButton, NAlert, useMessage,
} from 'naive-ui'
import { getAiConfig, updateAiConfig, fetchAiModels, testAiConnection } from '@/api/ai'

const message = useMessage()

const formData = ref({ endpoint: '', apiKey: '', model: '' })
const modelOptions = ref<string[]>([])
const fetching = ref(false)
const testing = ref(false)
const saving = ref(false)
const maskedKey = ref('')

async function loadConfig() {
  const config = await getAiConfig()
  formData.value.endpoint = config.endpoint
  formData.value.model = config.model
  formData.value.apiKey = ''
  maskedKey.value = config.apiKey
  modelOptions.value = config.model ? [config.model] : []
}

async function handleFetchModels() {
  if (!formData.value.endpoint || (!formData.value.apiKey && !maskedKey.value)) {
    message.warning('请先填写接口地址和 API Key')
    return
  }
  fetching.value = true
  try {
    const models = await fetchAiModels()
    if (models.length === 0) {
      message.info('未获取到模型列表')
    } else {
      modelOptions.value = models
      message.success(`已拉取 ${models.length} 个模型`)
    }
  } catch {
    // 错误已由拦截器提示
  } finally {
    fetching.value = false
  }
}

async function handleTest() {
  testing.value = true
  try {
    await testAiConnection()
    message.success('连通正常，可正常调用')
  } catch {
    // 错误已由拦截器提示
  } finally {
    testing.value = false
  }
}

async function handleSave() {
  if (!formData.value.model?.trim()) {
    message.warning('请输入或拉取模型')
    return
  }
  saving.value = true
  try {
    await updateAiConfig(formData.value)
    message.success('API 配置已保存')
    await loadConfig()
  } catch {
    // 错误已由拦截器提示
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<template>
  <div class="ai-config-page">
    <NCard title="AI 配置" :bordered="false">
      <template #header-extra>
        <NButton type="primary" :loading="saving" @click="handleSave">保存</NButton>
      </template>

      <NSpace vertical :size="16">
        <NAlert type="info" :bordered="false">
          配置 OpenAI 兼容的 LLM 接口地址、API Key 和模型名称。所有 AI 功能（如文案生成）共用此配置。
          API Key 存于数据库，不暴露给浏览器。
        </NAlert>

        <div class="form-item">
          <span class="label">接口地址</span>
          <NInput
            v-model:value="formData.endpoint"
            placeholder="https://api.openai.com"
          />
        </div>

        <div class="form-item">
          <span class="label">API Key</span>
          <NInput
            v-model:value="formData.apiKey"
            type="password"
            show-password-on="click"
            :placeholder="maskedKey ? `当前: ${maskedKey}（留空则不修改）` : 'sk-...'"
          />
        </div>

        <div class="form-item">
          <span class="label">模型名称</span>
          <div class="model-row">
            <NAutoComplete
              v-model:value="formData.model"
              :options="modelOptions.map((m) => ({ label: m, value: m }))"
              placeholder="gpt-4o-mini，或点右侧拉取"
              clearable
            />
            <NButton :loading="fetching" @click="handleFetchModels">拉取模型</NButton>
          </div>
        </div>

        <NButton :loading="testing" @click="handleTest">测试连通（发送一条测试请求）</NButton>
      </NSpace>
    </NCard>
  </div>
</template>

<style scoped>
.ai-config-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.label {
  font-size: 13px;
  font-weight: 500;
  opacity: 0.8;
}

.model-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.model-row > :first-child {
  flex: 1;
}
</style>
