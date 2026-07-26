<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  NModal, NForm, NFormItem, NInput, NAutoComplete, NButton, NSpace, useMessage,
} from 'naive-ui'
import { useUIStore } from '../store/ui'
import { useApiConfigStore } from '../store/apiConfig'
import { fetchModels, testConnection } from '../lib/llm'

const uiStore = useUIStore()
const apiStore = useApiConfigStore()
const message = useMessage()

const show = computed({
  get: () => uiStore.modal === 'api',
  set: (v) => (v ? uiStore.openModal('api') : uiStore.closeModal()),
})

const formData = ref({ endpoint: '', apiKey: '', model: '' })
const modelOptions = ref<string[]>([])
const fetching = ref(false)
const testing = ref(false)

watch(show, (v) => {
  if (v) {
    formData.value = { ...apiStore.state }
    modelOptions.value = apiStore.state.model ? [apiStore.state.model] : []
  }
})

async function handleFetchModels() {
  if (!formData.value.endpoint || !formData.value.apiKey) {
    message.warning('请先填写接口地址和 API Key')
    return
  }
  fetching.value = true
  try {
    const models = await fetchModels({
      endpoint: formData.value.endpoint,
      apiKey: formData.value.apiKey,
    })
    if (models.length === 0) {
      message.info('未获取到模型列表')
    } else {
      modelOptions.value = models
      message.success(`已拉取 ${models.length} 个模型`)
    }
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    fetching.value = false
  }
}

async function handleTest() {
  if (!formData.value.endpoint || !formData.value.apiKey || !formData.value.model) {
    message.warning('请先填写接口地址、API Key 和模型名称')
    return
  }
  testing.value = true
  try {
    await testConnection(formData.value)
    message.success('连通正常，可正常调用')
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    testing.value = false
  }
}

function handleSave() {
  if (!formData.value.model?.trim()) {
    message.warning('请输入或拉取模型')
    return false
  }
  apiStore.update(formData.value)
  message.success('API 配置已保存')
  return true
}
</script>

<template>
  <NModal
    v-model:show="show"
    preset="card"
    title="API 设置"
    :style="{ width: '560px' }"
    positive-text="保存"
    negative-text="取消"
    @positive-click="handleSave"
  >
    <NForm label-placement="top">
      <NFormItem label="接口地址">
        <NInput
          v-model:value="formData.endpoint"
          placeholder="https://api.openai.com"
        />
      </NFormItem>
      <NFormItem label="API Key">
        <NInput
          v-model:value="formData.apiKey"
          type="password"
          show-password-on="click"
          placeholder="sk-..."
        />
      </NFormItem>
      <NFormItem label="模型名称" required>
        <div style="display: flex; gap: 8px; width: 100%">
          <NAutoComplete
            v-model:value="formData.model"
            :options="modelOptions.map((m) => ({ label: m, value: m }))"
            placeholder="gpt-4o-mini，或点右侧拉取"
            clearable
          />
          <NButton :loading="fetching" @click="handleFetchModels">拉取模型</NButton>
        </div>
      </NFormItem>
    </NForm>
    <NButton block :loading="testing" @click="handleTest">
      测试连通（发送一条测试请求）
    </NButton>
    <p class="hint">API Key 存于浏览器本地，仅适合个人使用；多人共享需自建代理后端。</p>
  </NModal>
</template>

<style scoped>
.hint {
  color: var(--n-text-color-3, #999);
  font-size: 12px;
  margin: 12px 0 0;
}
</style>
