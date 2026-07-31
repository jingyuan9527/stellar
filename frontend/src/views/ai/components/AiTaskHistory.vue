<script setup lang="ts">
import { h, ref, onMounted } from 'vue'
import { NDataTable, NButton, NIcon, NPopconfirm, NTag, NSpace, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { iconMap } from '@/utils/icons'
import { formatTime } from '@/utils/format'
import { getAiTaskPage, deleteAiTask, clearAiTasks, type AiTaskRecord } from '@/api/ai'

const props = defineProps<{
  taskType: string
  pageSize?: number
}>()

const emit = defineEmits<{
  (e: 'view', row: AiTaskRecord): void
}>()

const message = useMessage()
const loading = ref(false)
const records = ref<AiTaskRecord[]>([])
const pagination = ref({ page: 1, pageSize: props.pageSize ?? 10, itemCount: 0 })

const statusMap: Record<string, { label: string; type: 'success' | 'error' | 'warning' | 'default' }> = {
  success: { label: '成功', type: 'success' },
  completed: { label: '完成', type: 'success' },
  failed: { label: '失败', type: 'error' },
  generating: { label: '生成中', type: 'warning' },
}

const columns: DataTableColumns<AiTaskRecord> = [
  {
    title: '请求时间',
    key: 'requestTime',
    width: 160,
    render: (row) => formatTime(row.requestTime),
  },
  {
    title: '提示词',
    key: 'prompt',
    ellipsis: { tooltip: true },
  },
  {
    title: '状态',
    key: 'status',
    width: 80,
    render: (row) => {
      const s = statusMap[row.status] ?? { label: row.status, type: 'default' as const }
      return h(NTag, { size: 'small', type: s.type, bordered: false }, { default: () => s.label })
    },
  },
  {
    title: '耗时',
    key: 'durationMs',
    width: 80,
    render: (row) => (row.durationMs != null ? `${(row.durationMs / 1000).toFixed(1)}s` : '-'),
  },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    render: (row) =>
      h(NSpace, { size: 'small' }, {
        default: () => [
          h(NButton, { size: 'tiny', text: true, type: 'primary', onClick: () => emit('view', row) }, {
            default: () => '查看',
            icon: () => h(NIcon, null, { default: () => h(iconMap.eye) }),
          }),
          h(NPopconfirm, { onPositiveClick: () => handleDelete(row.id) }, {
            trigger: () =>
              h(NButton, { size: 'tiny', text: true, type: 'error' }, {
                default: () => '删除',
                icon: () => h(NIcon, null, { default: () => h(iconMap.trash) }),
              }),
            default: () => '确认删除？',
          }),
        ],
      }),
  },
]

async function load() {
  loading.value = true
  try {
    const res = await getAiTaskPage({
      taskType: props.taskType,
      pageNum: pagination.value.page,
      pageSize: pagination.value.pageSize,
    })
    records.value = res.records
    pagination.value.itemCount = res.total
  } catch { /* interceptor handles */ } finally {
    loading.value = false
  }
}

async function handleDelete(id: number) {
  await deleteAiTask(id)
  message.success('已删除')
  load()
}

async function handleClear() {
  await clearAiTasks(props.taskType)
  message.success('已清空')
  pagination.value.page = 1
  load()
}

function handlePageChange(page: number) {
  pagination.value.page = page
  load()
}

function refresh() {
  load()
}

onMounted(load)

defineExpose({ refresh })
</script>

<template>
  <div class="ai-task-history">
    <div class="history-header">
      <NPopconfirm @positive-click="handleClear">
        <template #trigger>
          <NButton size="small" quaternary type="error">
            <template #icon><NIcon><component :is="iconMap.trash" /></NIcon></template>
            清空
          </NButton>
        </template>
        确认清空全部历史？
      </NPopconfirm>
    </div>
    <NDataTable
      :columns="columns"
      :data="records"
      :loading="loading"
      :pagination="{
        page: pagination.page,
        pageSize: pagination.pageSize,
        itemCount: pagination.itemCount,
        onUpdatePage: handlePageChange,
        showSizePicker: false,
      }"
      :row-key="(row: AiTaskRecord) => row.id"
      size="small"
      striped
    />
  </div>
</template>

<style scoped>
.ai-task-history {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.history-header {
  display: flex;
  justify-content: flex-end;
}
</style>
