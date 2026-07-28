<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import {
  NCard, NSpace, NInput, NButton, NDataTable, NTag, NSwitch,
  NModal, NFormItem, NPopconfirm, NInputNumber, useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import {
  listAllPersonas, createPersona, updatePersona, togglePersonaEnabled,
  deletePersona, resetPersona,
} from '@/api/chat'
import type { AiPersona } from '@/types/api'
import { formatTime } from '@/utils/format'

const message = useMessage()

const loading = ref(false)
const tableData = ref<AiPersona[]>([])



const columns: DataTableColumns<AiPersona> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '名称', key: 'name', width: 140 },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  {
    title: '系统提示词', key: 'systemPrompt', ellipsis: { tooltip: true },
    render: (row) => h('span', { style: 'opacity: 0.6; font-size: 12px' },
      row.systemPrompt.slice(0, 80) + (row.systemPrompt.length > 80 ? '...' : '')),
  },
  {
    title: '类型', key: 'builtIn', width: 80,
    render: (row) => row.builtIn === 1
      ? h(NTag, { size: 'small', type: 'info', bordered: false }, { default: () => '内置' })
      : h(NTag, { size: 'small', bordered: false }, { default: () => '自定义' }),
  },
  {
    title: '启用', key: 'enabled', width: 80,
    render: (row) => h(NSwitch, {
      value: row.enabled === 1,
      size: 'small',
      onUpdateValue: (v: boolean) => handleToggle(row, v ? 1 : 0),
    }),
  },
  { title: '排序', key: 'sortOrder', width: 70 },
  { title: '更新时间', key: 'updateTime', width: 160, render: (row) => formatTime(row.updateTime) },
  {
    title: '操作', key: 'actions', width: 220,
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
    tableData.value = await listAllPersonas()
  } catch {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

async function handleToggle(row: AiPersona, enabled: number) {
  try {
    await togglePersonaEnabled(row.id, enabled)
    row.enabled = enabled
  } catch {
    // 错误已由拦截器提示
  }
}

// ===== 新增/编辑 =====
const editShow = ref(false)
const editing = ref<{
  id: number | null; name: string; systemPrompt: string
  description: string; sortOrder: number; enabled: number
} | null>(null)

function startAdd() {
  editing.value = {
    id: null, name: '', systemPrompt: '',
    description: '', sortOrder: 0, enabled: 1,
  }
  editShow.value = true
}

function startEdit(row: AiPersona) {
  editing.value = {
    id: row.id, name: row.name, systemPrompt: row.systemPrompt,
    description: row.description || '', sortOrder: row.sortOrder, enabled: row.enabled,
  }
  editShow.value = true
}

async function handleSave() {
  if (!editing.value) return
  if (!editing.value.name.trim() || !editing.value.systemPrompt.trim()) {
    message.warning('名称和系统提示词不能为空')
    return false
  }
  try {
    if (editing.value.id) {
      await updatePersona({
        id: editing.value.id,
        name: editing.value.name,
        systemPrompt: editing.value.systemPrompt,
        description: editing.value.description,
        sortOrder: editing.value.sortOrder,
        enabled: editing.value.enabled,
      })
    } else {
      await createPersona({
        name: editing.value.name,
        systemPrompt: editing.value.systemPrompt,
        description: editing.value.description,
        sortOrder: editing.value.sortOrder,
        enabled: editing.value.enabled,
      })
    }
    message.success('已保存')
    loadData()
  } catch {
    // 错误已由拦截器提示
    return false
  }
}

async function handleDelete(id: number) {
  try {
    await deletePersona(id)
    message.success('已删除')
    loadData()
  } catch {
    // 错误已由拦截器提示
  }
}

async function handleReset(id: number) {
  try {
    await resetPersona(id)
    message.success('已恢复默认')
    loadData()
  } catch {
    // 错误已由拦截器提示
  }
}

onMounted(loadData)
</script>

<template>
  <div class="persona-page">
    <NCard title="人设管理" :bordered="false">
      <template #header-extra>
        <NButton type="primary" @click="startAdd">新增人设</NButton>
      </template>
      <NDataTable
        :columns="columns"
        :data="tableData"
        :loading="loading"
        :row-key="(row: AiPersona) => row.id"
        :pagination="false"
        :scroll-x="1200"
        :bordered="false"
      />
    </NCard>

    <NModal
      v-model:show="editShow"
      preset="card"
      :title="editing?.id ? '编辑人设' : '新增人设'"
      :style="{ width: '680px' }"
      positive-text="保存"
      negative-text="取消"
      @positive-click="handleSave"
    >
      <NSpace v-if="editing" vertical :size="16">
        <NFormItem label="名称">
          <NInput v-model:value="editing.name" placeholder="人设名称（如 程序员）" />
        </NFormItem>
        <NFormItem label="系统提示词">
          <NInput
            v-model:value="editing.systemPrompt"
            type="textarea"
            :autosize="{ minRows: 6, maxRows: 16 }"
            placeholder="注入 LLM 的 system prompt，定义人设风格与角色"
          />
        </NFormItem>
        <NFormItem label="描述">
          <NInput v-model:value="editing.description" placeholder="人设说明（可选）" />
        </NFormItem>
        <NFormItem label="排序">
          <NInputNumber v-model:value="editing.sortOrder" :min="0" />
        </NFormItem>
        <NFormItem label="启用">
          <NSwitch :value="editing.enabled === 1" @update:value="(v: boolean) => editing!.enabled = v ? 1 : 0" />
        </NFormItem>
      </NSpace>
    </NModal>
  </div>
</template>

<style scoped>
.persona-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
