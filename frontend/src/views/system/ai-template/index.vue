<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import {
  NCard, NSpace, NInput, NSelect, NButton, NDataTable, NTag,
  NModal, NFormItem, NPopconfirm, useMessage,
} from 'naive-ui'
import type { DataTableColumns, SelectOption } from 'naive-ui'
import { getAiTemplatePage, createAiTemplate, updateAiTemplate, deleteAiTemplate, resetAiTemplate } from '@/api/ai'
import type { AiTemplate, AiTemplateQuery } from '@/types/api'
import { formatTime } from '@/utils/format'

const message = useMessage()

const query = reactive<AiTemplateQuery>({
  name: '',
  platform: '',
  pageNum: 1,
  pageSize: 10,
})

const loading = ref(false)
const tableData = ref<AiTemplate[]>([])
const total = ref(0)

const platformLabels: Record<string, string> = {
  bilibili: 'B站',
  douyin: '抖音',
  xiaohongshu: '小红书',
  custom: '自定义',
}

const platformOptions: SelectOption[] = Object.entries(platformLabels).map(([v, l]) => ({
  value: v,
  label: l,
}))



const columns: DataTableColumns<AiTemplate> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '名称', key: 'name', width: 140 },
  {
    title: '平台', key: 'platform', width: 90,
    render: (row) => h(NTag, { size: 'small', bordered: false }, { default: () => platformLabels[row.platform] || row.platform }),
  },
  {
    title: '提示词', key: 'prompt', ellipsis: { tooltip: true },
    render: (row) => h('span', { style: 'opacity: 0.6; font-size: 12px' }, row.prompt.slice(0, 80) + (row.prompt.length > 80 ? '...' : '')),
  },
  {
    title: '类型', key: 'builtIn', width: 70,
    render: (row) => row.builtIn === 1
      ? h(NTag, { size: 'small', type: 'info', bordered: false }, { default: () => '内置' })
      : h(NTag, { size: 'small', bordered: false }, { default: () => '自定义' }),
  },
  { title: '更新时间', key: 'updateTime', width: 160, render: (row) => formatTime(row.updateTime) },
  {
    title: '操作', key: 'actions', width: 200,
    render: (row) => h(NSpace, { size: 'small' }, {
      default: () => [
        h(NButton, { size: 'small', onClick: () => startEdit(row) }, { default: () => '编辑' }),
        row.builtIn === 1
          ? h(NPopconfirm, { onPositiveClick: () => handleReset(row.id) }, {
              trigger: () => h(NButton, { size: 'small' }, { default: () => '恢复默认' }),
              default: () => '恢复为默认？',
            })
          : h(NPopconfirm, { onPositiveClick: () => handleDelete(row.id) }, {
              trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
              default: () => '确认删除？',
            }),
      ],
    }),
  },
]

async function loadData() {
  loading.value = true
  try {
    const res = await getAiTemplatePage(query)
    tableData.value = res.records
    total.value = res.total
  } catch {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadData()
}

function handlePageChange(page: number) {
  query.pageNum = page
  loadData()
}

// ===== 新增/编辑 =====
const editShow = ref(false)
const editing = ref<{ id: number | null; name: string; platform: string; prompt: string } | null>(null)

function startAdd() {
  editing.value = { id: null, name: '', platform: 'custom', prompt: '' }
  editShow.value = true
}

function startEdit(row: AiTemplate) {
  editing.value = { id: row.id, name: row.name, platform: row.platform, prompt: row.prompt }
  editShow.value = true
}

async function handleSave() {
  if (!editing.value) return
  if (!editing.value.name.trim() || !editing.value.prompt.trim()) {
    message.warning('名称和提示词不能为空')
    return
  }
  try {
    if (editing.value.id) {
      await updateAiTemplate(editing.value.id, {
        name: editing.value.name,
        platform: editing.value.platform,
        prompt: editing.value.prompt,
      })
    } else {
      await createAiTemplate({
        name: editing.value.name,
        platform: editing.value.platform,
        prompt: editing.value.prompt,
      })
    }
    message.success('已保存')
    editShow.value = false
    loadData()
  } catch {
    // 错误已由拦截器提示
  }
}

async function handleDelete(id: number) {
  try {
    await deleteAiTemplate(id)
    message.success('已删除')
    loadData()
  } catch {
    // 错误已由拦截器提示
  }
}

async function handleReset(id: number) {
  try {
    await resetAiTemplate(id)
    message.success('已恢复默认')
    loadData()
  } catch {
    // 错误已由拦截器提示
  }
}

onMounted(loadData)
</script>

<template>
  <div class="ai-template-page">
    <NCard title="AI 模板管理" :bordered="false">
      <template #header-extra>
        <NButton type="primary" @click="startAdd">新增模板</NButton>
      </template>

      <NSpace align="center" :size="12" style="margin-bottom: 16px">
        <NInput
          v-model:value="query.name"
          placeholder="模板名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
        <NSelect
          v-model:value="query.platform"
          :options="platformOptions"
          placeholder="平台"
          clearable
          style="width: 140px"
          @update:value="handleSearch"
        />
        <NButton @click="handleSearch">搜索</NButton>
      </NSpace>

      <NDataTable
        :columns="columns"
        :data="tableData"
        :loading="loading"
        :row-key="(row: AiTemplate) => row.id"
        :pagination="{
          page: query.pageNum,
          pageSize: query.pageSize,
          itemCount: total,
          showSizePicker: false,
          onChange: handlePageChange,
        }"
        :bordered="false"
      />
    </NCard>

    <NModal
      v-model:show="editShow"
      preset="card"
      :title="editing?.id ? '编辑模板' : '新增模板'"
      :style="{ width: '680px' }"
      positive-text="保存"
      negative-text="取消"
      @positive-click="handleSave"
    >
      <NSpace v-if="editing" vertical :size="16">
        <NFormItem label="名称">
          <NInput v-model:value="editing.name" placeholder="模板名称" />
        </NFormItem>
        <NFormItem label="平台">
          <NSelect v-model:value="editing.platform" :options="platformOptions" />
        </NFormItem>
        <NFormItem label="提示词">
          <NInput
            v-model:value="editing.prompt"
            type="textarea"
            :autosize="{ minRows: 8, maxRows: 16 }"
            placeholder="输入提示词，用 {{topic}} 作为主题占位符"
          />
        </NFormItem>
      </NSpace>
    </NModal>
  </div>
</template>

<style scoped>
.ai-template-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
