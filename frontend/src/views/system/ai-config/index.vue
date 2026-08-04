<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NCard, NSpace, NInput, NSelect, NButton, NDataTable, NTag, NSwitch,
  NModal, NFormItem, NPopconfirm, NAlert, useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import {
  getAiProviderList, createAiProvider, updateAiProvider, deleteAiProvider,
  toggleAiProviderEnabled, previewAiProviderModels, saveAiProviderModels,
  clearAiProviderModels, testAiProviderConnection,
  getAiModelList, createAiModel, updateAiModel, deleteAiModel,
  toggleAiModelEnabled, setAiModelDefault, deleteAiModelsBatch, toggleAiModelsBatch,
} from '@/api/ai'
import type { AiProvider, AiModel } from '@/types/api'
import { useIsMobile } from '@/composables/useBreakpoint'

const message = useMessage()
const isMobile = useIsMobile()

// 模型类型选项（与后端字典 model_type 数据一致）
const modelTypeOptions = [
  { value: 'TEXT', label: '文本对话' },
  { value: 'IMAGE', label: '图片生成' },
  { value: 'AUDIO', label: '语音合成' },
  { value: 'EMBEDDING', label: '向量嵌入' },
  { value: 'VIDEO', label: '视频生成' },
]

function typeLabel(t: string): string {
  return modelTypeOptions.find((o) => o.value === t)?.label ?? t
}



// ===== 供应商 =====
const providers = ref<AiProvider[]>([])
const providerLoading = ref(false)
const selectedProviderId = ref<number | null>(null)

const selectedProvider = ref<AiProvider | null>(null)

async function loadProviders() {
  providerLoading.value = true
  try {
    providers.value = await getAiProviderList()
    if (selectedProviderId.value === null && providers.value.length > 0) {
      selectedProviderId.value = providers.value[0].id
      selectedProvider.value = providers.value[0]
    } else {
      selectedProvider.value = providers.value.find((p) => p.id === selectedProviderId.value) ?? null
    }
    if (selectedProvider.value) {
      loadModels(selectedProvider.value.id)
    }
  } catch {
    // 错误已由拦截器提示
  } finally {
    providerLoading.value = false
  }
}

function selectProvider(row: AiProvider) {
  selectedProviderId.value = row.id
  selectedProvider.value = row
  loadModels(row.id)
}

async function handleToggleProvider(id: number, enabled: number) {
  try {
    await toggleAiProviderEnabled(id, enabled)
    message.success(enabled === 1 ? '已启用' : '已禁用')
    loadProviders()
  } catch {
    // 错误已由拦截器提示
  }
}

async function handleDeleteProvider(id: number) {
  try {
    await deleteAiProvider(id)
    message.success('已删除供应商及其下模型')
    if (selectedProviderId.value === id) {
      selectedProviderId.value = null
      selectedProvider.value = null
      models.value = []
    }
    loadProviders()
  } catch {
    // 错误已由拦截器提示
  }
}

// ===== 拉取模型弹窗 =====
const fetchShow = ref(false)
const fetchProvider = ref<AiProvider | null>(null)
const fetchLoading = ref(false)
const fetchSaving = ref(false)
const fetchModels = ref<string[]>([])
const fetchKeyword = ref('')
const fetchChecked = ref<string[]>([])

const fetchFiltered = computed(() => {
  const kw = fetchKeyword.value.trim().toLowerCase()
  const list = !kw
    ? fetchModels.value
    : fetchModels.value.filter((m) => m.toLowerCase().includes(kw))
  return list.map((m) => ({ model: m }))
})

async function openFetchModal(provider: AiProvider) {
  fetchProvider.value = provider
  fetchKeyword.value = ''
  fetchModels.value = []
  fetchChecked.value = []
  fetchShow.value = true
  await loadPreviewModels(provider.id)
}

async function loadPreviewModels(id: number) {
  fetchLoading.value = true
  try {
    const list = await previewAiProviderModels(id)
    fetchModels.value = list
    // 默认全勾选，方便全量拉取
    fetchChecked.value = [...list]
  } catch {
    // 错误已由拦截器提示
  } finally {
    fetchLoading.value = false
  }
}

function handleFetchSelectAll() {
  fetchChecked.value = fetchFiltered.value.map((r) => r.model)
}

function handleFetchSelectNone() {
  fetchChecked.value = []
}

async function handleConfirmFetch() {
  if (!fetchProvider.value) return
  fetchSaving.value = true
  try {
    await saveAiProviderModels(fetchProvider.value.id, fetchChecked.value)
    message.success(`已保存 ${fetchChecked.value.length} 个模型（覆盖旧配置）`)
    fetchShow.value = false
    const p = providers.value.find((x) => x.id === fetchProvider.value!.id)
    if (p) selectProvider(p)
    else loadProviders()
  } catch {
    // 错误已由拦截器提示
  } finally {
    fetchSaving.value = false
  }
}

async function handleTestProvider(id: number) {
  try {
    await testAiProviderConnection(id)
    message.success('连通正常')
  } catch {
    // 错误已由拦截器提示
  }
}

const allProviderColumns: DataTableColumns<AiProvider> = [
  { title: 'ID', key: 'id', width: 60 },
  { title: '名称', key: 'name', width: 120 },
  { title: '接口地址', key: 'endpoint', ellipsis: { tooltip: true } },
  { title: 'API Key', key: 'apiKey', width: 140, render: (row) => row.apiKey || '—' },
  {
    title: '启用', key: 'enabled', width: 70,
    render: (row) => h(NSwitch, {
      value: row.enabled === 1,
      size: 'small',
      onUpdateValue: (v: boolean) => handleToggleProvider(row.id, v ? 1 : 0),
    }),
  },
  {
    title: '操作', key: 'actions', width: 240,
    render: (row) => h(NSpace, { size: 'small' }, {
      default: () => [
        h(NButton, { size: 'small', type: row.id === selectedProviderId.value ? 'primary' : 'default', onClick: () => selectProvider(row) }, { default: () => '模型' }),
        h(NButton, { size: 'small', onClick: () => openFetchModal(row) }, { default: () => '拉取' }),
        h(NButton, { size: 'small', onClick: () => handleTestProvider(row.id) }, { default: () => '测试' }),
        h(NButton, { size: 'small', onClick: () => startEditProvider(row) }, { default: () => '编辑' }),
        h(NPopconfirm, { onPositiveClick: () => handleDeleteProvider(row.id) }, {
          trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
          default: () => '删除供应商将同时删除其下所有模型，确认？',
        }),
      ],
    }),
  },
]

const mobileHiddenProviderKeys = new Set(['id', 'apiKey'])
const providerColumns = computed<DataTableColumns<AiProvider>>(() =>
  isMobile.value
    ? allProviderColumns.filter((c) => !mobileHiddenProviderKeys.has((c as { key?: string }).key ?? ''))
    : allProviderColumns,
)

// 供应商编辑
const providerEditShow = ref(false)
const providerEditing = ref<{ id: number | null; name: string; endpoint: string; apiKey: string } | null>(null)

function startAddProvider() {
  providerEditing.value = { id: null, name: '', endpoint: '', apiKey: '' }
  providerEditShow.value = true
}

function startEditProvider(row: AiProvider) {
  providerEditing.value = { id: row.id, name: row.name, endpoint: row.endpoint, apiKey: '' }
  providerEditShow.value = true
}

async function handleSaveProvider() {
  if (!providerEditing.value) return
  if (!providerEditing.value.name.trim() || !providerEditing.value.endpoint.trim()) {
    message.warning('名称和接口地址不能为空')
    return
  }
  try {
    if (providerEditing.value.id) {
      await updateAiProvider({
        id: providerEditing.value.id,
        name: providerEditing.value.name.trim(),
        endpoint: providerEditing.value.endpoint.trim(),
        apiKey: providerEditing.value.apiKey.trim() || undefined,
      })
    } else {
      await createAiProvider({
        name: providerEditing.value.name.trim(),
        endpoint: providerEditing.value.endpoint.trim(),
        apiKey: providerEditing.value.apiKey.trim() || undefined,
      })
    }
    message.success('已保存')
    providerEditShow.value = false
    loadProviders()
  } catch {
    // 错误已由拦截器提示
  }
}

// ===== 模型 =====
const models = ref<AiModel[]>([])
const modelLoading = ref(false)

async function loadModels(providerId: number) {
  modelLoading.value = true
  try {
    models.value = await getAiModelList(providerId)
  } catch {
    // 错误已由拦截器提示
  } finally {
    modelLoading.value = false
  }
}

async function handleToggleModel(id: number, enabled: number) {
  try {
    await toggleAiModelEnabled(id, enabled)
    message.success(enabled === 1 ? '已启用' : '已禁用')
    if (selectedProviderId.value) loadModels(selectedProviderId.value)
  } catch {
    // 错误已由拦截器提示
  }
}

async function handleSetDefault(id: number) {
  try {
    await setAiModelDefault(id)
    message.success('已设为默认')
    if (selectedProviderId.value) loadModels(selectedProviderId.value)
  } catch {
    // 错误已由拦截器提示
  }
}

async function handleDeleteModel(id: number) {
  try {
    await deleteAiModel(id)
    message.success('已删除')
    if (selectedProviderId.value) loadModels(selectedProviderId.value)
  } catch {
    // 错误已由拦截器提示
  }
}

// ===== 模型批量操作 =====
const checkedModelIds = ref<number[]>([])

async function handleBatchToggleModel(enabled: number) {
  if (checkedModelIds.value.length === 0) return
  try {
    await toggleAiModelsBatch(checkedModelIds.value, enabled)
    message.success(`已批量${enabled === 1 ? '启用' : '停用'} ${checkedModelIds.value.length} 个模型`)
    checkedModelIds.value = []
    if (selectedProviderId.value) loadModels(selectedProviderId.value)
  } catch {
    // 错误已由拦截器提示
  }
}

async function handleBatchDeleteModels() {
  if (checkedModelIds.value.length === 0) return
  try {
    await deleteAiModelsBatch(checkedModelIds.value)
    message.success(`已批量删除 ${checkedModelIds.value.length} 个模型`)
    checkedModelIds.value = []
    if (selectedProviderId.value) loadModels(selectedProviderId.value)
  } catch {
    // 错误已由拦截器提示
  }
}

async function handleClearModels() {
  if (!selectedProviderId.value) return
  try {
    await clearAiProviderModels(selectedProviderId.value)
    message.success('已清空该供应商全部模型')
    checkedModelIds.value = []
    if (selectedProviderId.value) loadModels(selectedProviderId.value)
    loadProviders()
  } catch {
    // 错误已由拦截器提示
  }
}

const allModelColumns: DataTableColumns<AiModel> = [
  { type: 'selection' },
  { title: 'ID', key: 'id', width: 60 },
  { title: '模型名', key: 'model', ellipsis: { tooltip: true } },
  {
    title: '类型', key: 'modelType', width: 110,
    render: (row) => h(NTag, { size: 'small', bordered: false, type: row.modelType === 'TEXT' ? 'success' : 'warning' }, { default: () => typeLabel(row.modelType) }),
  },
  {
    title: '启用', key: 'enabled', width: 70,
    render: (row) => h(NSwitch, {
      value: row.enabled === 1,
      size: 'small',
      onUpdateValue: (v: boolean) => handleToggleModel(row.id, v ? 1 : 0),
    }),
  },
  {
    title: '默认', key: 'isDefault', width: 90,
    render: (row) => row.isDefault === 1
      ? h(NTag, { size: 'small', type: 'info', bordered: false }, { default: () => '默认' })
      : h(NButton, { size: 'small', text: true, onClick: () => handleSetDefault(row.id) }, { default: () => '设默认' }),
  },
  { title: '排序', key: 'sortOrder', width: 60 },
  {
    title: '操作', key: 'actions', width: 140,
    render: (row) => h(NSpace, { size: 'small' }, {
      default: () => [
        h(NButton, { size: 'small', onClick: () => startEditModel(row) }, { default: () => '编辑' }),
        h(NPopconfirm, { onPositiveClick: () => handleDeleteModel(row.id) }, {
          trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
          default: () => '确认删除？',
        }),
      ],
    }),
  },
]

const mobileHiddenModelKeys = new Set(['id', 'sortOrder'])
const modelColumns = computed<DataTableColumns<AiModel>>(() =>
  isMobile.value
    ? allModelColumns.filter((c) => !mobileHiddenModelKeys.has((c as { key?: string }).key ?? ''))
    : allModelColumns,
)

const fetchModelColumns: DataTableColumns<{ model: string }> = [
  { type: 'selection' },
  {
    title: '模型名', key: 'model', ellipsis: { tooltip: true },
    render: (row) => h(NTag, { size: 'small', bordered: false, type: 'info' }, { default: () => row.model }),
  },
]

// 模型编辑
const modelEditShow = ref(false)
const modelEditing = ref<{ id: number | null; providerId: number | null; model: string; modelType: string; isDefault: number } | null>(null)

function startAddModel() {
  if (!selectedProviderId.value) {
    message.warning('请先选择供应商')
    return
  }
  modelEditing.value = { id: null, providerId: selectedProviderId.value, model: '', modelType: 'TEXT', isDefault: 0 }
  modelEditShow.value = true
}

function startEditModel(row: AiModel) {
  modelEditing.value = { id: row.id, providerId: row.providerId, model: row.model, modelType: row.modelType, isDefault: row.isDefault }
  modelEditShow.value = true
}

async function handleSaveModel() {
  if (!modelEditing.value) return
  if (!modelEditing.value.model.trim()) {
    message.warning('模型名不能为空')
    return
  }
  try {
    if (modelEditing.value.id) {
      await updateAiModel({
        id: modelEditing.value.id,
        providerId: modelEditing.value.providerId ?? undefined,
        model: modelEditing.value.model.trim(),
        modelType: modelEditing.value.modelType,
        isDefault: modelEditing.value.isDefault,
      })
    } else {
      await createAiModel({
        providerId: selectedProviderId.value!,
        model: modelEditing.value.model.trim(),
        modelType: modelEditing.value.modelType,
        isDefault: modelEditing.value.isDefault,
      })
    }
    message.success('已保存')
    modelEditShow.value = false
    if (selectedProviderId.value) loadModels(selectedProviderId.value)
  } catch {
    // 错误已由拦截器提示
  }
}

onMounted(loadProviders)
</script>

<template>
  <div class="ai-config-page">
    <NCard title="AI 供应商" :bordered="false">
      <template #header-extra>
        <NButton type="primary" @click="startAddProvider">新增供应商</NButton>
      </template>
      <NSpace vertical :size="12">
        <NAlert type="info" :bordered="false">
          配置 OpenAI 兼容的 LLM 供应商（接口地址 + API Key），每个供应商下可挂多个模型并标注类型。
          API Key 存于数据库，返回前端时脱敏。
        </NAlert>
        <NDataTable
          :columns="providerColumns"
          :data="providers"
          :loading="providerLoading"
          :row-key="(row: AiProvider) => row.id"
          :bordered="false"
          :scroll-x="700"
        />
      </NSpace>
    </NCard>

    <NCard v-if="selectedProvider" :title="`模型管理 · ${selectedProvider.name}`" :bordered="false">
      <template #header-extra>
        <NSpace :size="8" wrap>
          <NPopconfirm @positive-click="handleClearModels">
            <template #trigger>
              <NButton size="small" type="warning">清空</NButton>
            </template>
            清空该供应商下全部模型？此操作不可恢复。
          </NPopconfirm>
          <NButton type="primary" @click="startAddModel">新增模型</NButton>
        </NSpace>
      </template>
      <NSpace v-if="checkedModelIds.length > 0" vertical :size="8" class="batch-bar">
        <NSpace align="center" :size="8" wrap>
          <NTag type="info" size="small" bordered>已选 {{ checkedModelIds.length }} 项</NTag>
          <NButton size="small" type="success" @click="handleBatchToggleModel(1)">批量启用</NButton>
          <NButton size="small" type="warning" @click="handleBatchToggleModel(0)">批量停用</NButton>
          <NPopconfirm @positive-click="handleBatchDeleteModels">
            <template #trigger>
              <NButton size="small" type="error">批量删除</NButton>
            </template>
            删除选中的 {{ checkedModelIds.length }} 个模型？此操作不可恢复。
          </NPopconfirm>
        </NSpace>
      </NSpace>
      <NDataTable
        :columns="modelColumns"
        :data="models"
        :loading="modelLoading"
        :row-key="(row: AiModel) => row.id"
        :checked-row-keys="checkedModelIds"
        @update:checked-row-keys="(keys: (string | number)[]) => checkedModelIds = keys.map(Number)"
        :bordered="false"
        :scroll-x="640"
      />
    </NCard>

    <NModal
      v-model:show="providerEditShow"
      preset="card"
      :title="providerEditing?.id ? '编辑供应商' : '新增供应商'"
      :style="{ width: '500px' }"
      :mask-closable="false"
    >
      <NSpace v-if="providerEditing" vertical :size="16">
        <NFormItem label="名称">
          <NInput v-model:value="providerEditing.name" placeholder="如 OpenAI / 通义千问" />
        </NFormItem>
        <NFormItem label="接口地址">
          <NInput v-model:value="providerEditing.endpoint" placeholder="https://api.openai.com" />
        </NFormItem>
        <NFormItem label="API Key">
          <NInput
            v-model:value="providerEditing.apiKey"
            type="password"
            show-password-on="click"
            placeholder="留空则不修改"
          />
        </NFormItem>
      </NSpace>
      <template #action>
        <NSpace justify="end">
          <NButton @click="providerEditShow = false">取消</NButton>
          <NButton type="primary" @click="handleSaveProvider">保存</NButton>
        </NSpace>
      </template>
    </NModal>

    <NModal
      v-model:show="modelEditShow"
      preset="card"
      :title="modelEditing?.id ? '编辑模型' : '新增模型'"
      :style="{ width: '500px' }"
      :mask-closable="false"
    >
      <NSpace v-if="modelEditing" vertical :size="16">
        <NFormItem label="模型名">
          <NInput v-model:value="modelEditing.model" placeholder="如 gpt-4o-mini" />
        </NFormItem>
        <NFormItem label="类型">
          <NSelect v-model:value="modelEditing.modelType" :options="modelTypeOptions" />
        </NFormItem>
        <NFormItem label="设为该类型默认">
          <NSwitch v-model:value="modelEditing.isDefault" :checked-value="1" :unchecked-value="0" />
        </NFormItem>
      </NSpace>
      <template #action>
        <NSpace justify="end">
          <NButton @click="modelEditShow = false">取消</NButton>
          <NButton type="primary" @click="handleSaveModel">保存</NButton>
        </NSpace>
      </template>
    </NModal>

    <NModal
      v-model:show="fetchShow"
      preset="card"
      :title="`拉取模型 · ${fetchProvider?.name ?? ''}`"
      :style="{ width: isMobile ? '100%' : '720px', maxWidth: '95vw' }"
      :mask-closable="false"
    >
      <NSpace vertical :size="12">
        <NAlert v-if="fetchProvider && !fetchProvider.endpoint" type="warning" :bordered="false">
          该供应商未配置接口地址/API Key，无法拉取模型。
        </NAlert>
        <NInput
          v-model:value="fetchKeyword"
          placeholder="输入关键字筛选模型（支持部分匹配）"
          clearable
        >
          <template #prefix>🔍</template>
        </NInput>
        <NDataTable
          :columns="fetchModelColumns"
          :data="fetchFiltered"
          :loading="fetchLoading"
          :row-key="(row: any) => row.model"
          :checked-row-keys="fetchChecked"
          @update:checked-row-keys="(keys: (string | number)[]) => fetchChecked = keys.map(String)"
          :bordered="false"
          :scroll-y="420"
          :scroll-x="380"
        />
        <NSpace align="center" :size="8" justify="space-between" wrap>
          <NSpace :size="8">
            <NTag size="small" bordered>远端共 {{ fetchModels.length }} 个</NTag>
            <NTag v-if="fetchKeyword.trim()" size="small" type="info" bordered>
              筛选后 {{ fetchFiltered.length }} 个
            </NTag>
            <NTag v-if="fetchChecked.length > 0" size="small" type="success" bordered>
              已勾选 {{ fetchChecked.length }} 个
            </NTag>
          </NSpace>
          <NSpace :size="8">
            <NButton size="small" @click="handleFetchSelectAll" :disabled="fetchFiltered.length === 0">全选</NButton>
            <NButton size="small" @click="handleFetchSelectNone" :disabled="fetchChecked.length === 0">清空选择</NButton>
          </NSpace>
        </NSpace>
      </NSpace>
      <template #action>
        <NSpace justify="end">
          <NButton @click="fetchShow = false">取消</NButton>
          <NButton
            type="primary"
            :loading="fetchSaving"
            :disabled="fetchLoading || !fetchProvider?.endpoint"
            @click="handleConfirmFetch"
          >
            确认保存（覆盖旧配置）
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.ai-config-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.batch-bar {
  margin-bottom: 12px;
}
</style>
