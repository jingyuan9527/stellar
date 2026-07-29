<script setup lang="ts">
import { h, computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  NSpace, NInput, NSelect, NButton, NEmpty, NAlert, NDataTable, NTag, NPopconfirm,
  useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import AiGeneratorLayout from '../components/AiGeneratorLayout.vue'
import { createAiImage, deleteAiImage, getAiImagePage, getAiModelsByType } from '@/api/ai'
import { useAuthStore } from '@/store/auth'
import { useAiNotifyStore, type AiNotifyMessage } from '@/store/aiNotify'
import type { AiModel, AiImageTask } from '@/types/api'
import { formatTime } from '@/utils/format'
import { useIsMobile } from '@/composables/useBreakpoint'

const message = useMessage()
const authStore = useAuthStore()
const aiNotifyStore = useAiNotifyStore()
const isMobile = useIsMobile()

const models = ref<AiModel[]>([])
const modelId = ref<number | null>(null)
const prompt = ref('')
const size = ref('1K')
const ratio = ref('1:1')
const creating = ref(false)
const generating = ref(false)

// 历史
const history = ref<AiImageTask[]>([])
const historyLoading = ref(false)

// 历史详情抽屉：点历史「查看」/生成完成自动弹
const drawerOpen = ref(false)
const drawerRow = ref<AiImageTask | null>(null)

function openDrawer(row: AiImageTask) {
  drawerRow.value = row
  drawerOpen.value = true
}

const modelOptions = computed(() =>
  models.value.map((m) => ({
    value: m.id,
    label: m.providerName ? `${m.model} (${m.providerName})` : m.model,
  })),
)

const sizeOptions = [
  { value: '1K', label: '1K（约 1024）' },
  { value: '2K', label: '2K（约 2048）' },
  { value: '3K', label: '3K（约 3072）' },
  { value: '4K', label: '4K（约 4096）' },
]

const ratioOptions = [
  { value: '1:1', label: '1:1 方形' },
  { value: '16:9', label: '16:9 横图' },
  { value: '9:16', label: '9:16 竖图' },
  { value: '4:3', label: '4:3 横图' },
  { value: '3:4', label: '3:4 竖图' },
  { value: '2:3', label: '2:3 竖图' },
  { value: '3:2', label: '3:2 横图' },
  { value: '21:9', label: '21:9 超宽' },
]



const allColumns: DataTableColumns<AiImageTask> = [
  { title: '请求时间', key: 'createTime', width: 170, render: (row) => formatTime(row.createTime) },
  { title: '提示词', key: 'prompt', ellipsis: { tooltip: true } },
  {
    title: '尺寸/比例', key: 'size', width: 130,
    render: (row) => `${row.size ?? '-'} / ${row.ratio ?? '-'}`,
  },
  {
    title: '结果', key: 'result', width: 90,
    render: (row) => {
      if (row.status === 'completed' && row.url) {
        return h('img', { src: row.url, style: 'width:60px;height:60px;object-fit:contain;border-radius:4px' })
      }
      if (row.status === 'failed') {
        return h(NTag, { type: 'error', size: 'small' }, { default: () => '失败' })
      }
      if (row.status === 'generating') {
        return h(NTag, { size: 'small' }, { default: () => '生成中' })
      }
      return h(NTag, { size: 'small' }, { default: () => row.status })
    },
  },
  { title: '返回时间', key: 'updateTime', width: 170, render: (row) => formatTime(row.updateTime) },
  {
    title: '耗时', key: 'durationMs', width: 90,
    render: (row) => (row.durationMs != null ? `${row.durationMs} ms` : '-'),
  },
  {
    title: '操作', key: 'actions', width: 140, fixed: 'right',
    render: (row) => h(NSpace, { size: 'small' }, {
      default: () => [
        h(NButton, { size: 'small', onClick: () => openDrawer(row) }, { default: () => '查看' }),
        h(NPopconfirm, { onPositiveClick: () => handleDelete(row.taskId) }, {
          trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
          default: () => '确定删除该记录？',
        }),
      ],
    }),
  },
]

const columns = computed<DataTableColumns<AiImageTask>>(() =>
  isMobile.value
    ? allColumns.filter((c) => {
        const key = (c as { key?: string }).key
        return key !== 'updateTime' && key !== 'durationMs'
      })
    : allColumns,
)

const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
  onChange: (page: number) => {
    pagination.page = page
    loadHistory()
  },
  onUpdatePageSize: (size: number) => {
    pagination.pageSize = size
    pagination.page = 1
    loadHistory()
  },
})

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

async function loadHistory() {
  historyLoading.value = true
  try {
    const res = await getAiImagePage({ pageNum: pagination.page, pageSize: pagination.pageSize })
    history.value = res.records
    pagination.itemCount = res.total
  } catch {
    // 错误已由拦截器提示
  } finally {
    historyLoading.value = false
  }
}

async function refreshHistory() {
  pagination.page = 1
  await loadHistory()
}

async function handleCreate() {
  if (!modelId.value) {
    message.warning('请选择图片模型')
    return
  }
  if (!prompt.value.trim()) {
    message.warning('请输入提示词')
    return
  }
  creating.value = true
  try {
    await createAiImage({
      modelId: modelId.value,
      prompt: prompt.value.trim(),
      size: size.value,
      ratio: ratio.value,
    })
    generating.value = true
    message.success('任务已创建，正在生成...')
    await refreshHistory()
  } catch {
    // 错误已由拦截器提示
  } finally {
    creating.value = false
  }
}

async function handleDelete(taskId: number) {
  try {
    await deleteAiImage(taskId)
    message.success('删除成功')
    await loadHistory()
  } catch {
    // 拦截器提示
  }
}

function onTaskNotify(msg: AiNotifyMessage) {
  if (msg.type !== 'image') return
  generating.value = false
  if (msg.status === 'completed') {
    message.success('图片生成完成')
    refreshHistory().then(() => {
      if (history.value[0]) openDrawer(history.value[0])
    })
  } else {
    message.error('图片生成失败')
    refreshHistory()
  }
}

let offNotify: (() => void) | null = null

onMounted(() => {
  loadModels()
  loadHistory()
  offNotify = aiNotifyStore.onTaskNotify(onTaskNotify)
})

onBeforeUnmount(() => {
  if (offNotify) offNotify()
})
</script>

<template>
  <div class="image-page">
    <AiGeneratorLayout
      aside-title="AI 图片生成"
      drawer-title="图片详情"
      v-model:drawer-open="drawerOpen"
    >
      <template #aside>
        <NSpace vertical :size="16">
          <NAlert v-if="models.length === 0" type="warning" :bordered="false">
            暂无可用的图片模型，请联系管理员在 系统管理 → AI 配置 中添加 IMAGE 类型模型。
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
            <div class="field-label">尺寸档位 / 宽高比</div>
            <NSpace>
            <NSelect v-model:value="size" :options="sizeOptions" class="size-select" />
            <NSelect v-model:value="ratio" :options="ratioOptions" class="size-select" />
            </NSpace>
          </div>

          <NSpace>
            <NButton
              type="primary"
              :loading="creating || generating"
              :disabled="models.length === 0 || !prompt.trim() || generating"
              @click="handleCreate"
            >
              生成图片
            </NButton>
          </NSpace>

          <NAlert v-if="!authStore.isLogin" type="info" :bordered="false">
            游客每日 2 次（受 IP 限流）。历史按 IP 记录，同网络下他人可见。
          </NAlert>
        </NSpace>
      </template>

      <template #history>
        <NDataTable
          :columns="columns"
          :data="history"
          :loading="historyLoading"
          :pagination="pagination"
          :scroll-x="1000"
          remote
          striped
          size="small"
        />
      </template>

      <template #drawer>
        <div v-if="drawerRow" class="drawer-body">
          <div class="result-meta">
            {{ formatTime(drawerRow.createTime) }} · {{ drawerRow.size ?? '-' }} / {{ drawerRow.ratio ?? '-' }}
          </div>
          <img v-if="drawerRow.url" :src="drawerRow.url" alt="历史图片" class="drawer-img" />
          <NEmpty v-else-if="drawerRow.status === 'failed'" :description="drawerRow.errorMsg || '生成失败'" />
          <NEmpty v-else description="该任务暂无结果" />
          <div class="drawer-prompt">{{ drawerRow.prompt }}</div>
          <NButton v-if="drawerRow.url" tag="a" :href="drawerRow.url" target="_blank" download>
            下载图片
          </NButton>
        </div>
      </template>
    </AiGeneratorLayout>
  </div>
</template>

<style scoped>
.image-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field-label {
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 6px;
  opacity: 0.8;
}

.result-meta {
  font-size: 12px;
  opacity: 0.6;
}

.drawer-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.drawer-img {
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
  border-radius: 4px;
  display: block;
  margin: 0 auto;
}

.drawer-prompt {
  font-size: 13px;
  opacity: 0.8;
  line-height: 1.6;
  word-break: break-word;
}

.size-select {
  width: 160px;
}

@media (max-width: 768px) {
  .size-select {
    width: 100%;
  }
}
</style>
