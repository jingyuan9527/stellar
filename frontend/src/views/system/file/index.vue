<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NCard, NForm, NFormItem, NInput, NSelect, NDatePicker, NButton, NSpace,
  NDataTable, NTag, NDrawer, NDrawerContent, NDescriptions, NDescriptionsItem,
  NIcon, NPopconfirm, useMessage,
} from 'naive-ui'
import type { DataTableColumns, SelectOption } from 'naive-ui'
import { getFilePage, deleteFile, deleteFileBatch } from '@/api/file'
import type { SysFile, SysFileQuery } from '@/types/api'
import { iconMap } from '@/utils/icons'
import { formatTime } from '@/utils/format'
import { useIsMobile } from '@/composables/useBreakpoint'

const message = useMessage()
const isMobile = useIsMobile()
const drawerWidth = computed(() => (isMobile.value ? '100%' : 560))

const IMAGE_EXTS = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg', 'ico'])
const AUDIO_EXTS = new Set(['mp3', 'wav', 'm4a', 'aac', 'ogg'])

function isImage(ext?: string | null) {
  return !!ext && IMAGE_EXTS.has(ext.toLowerCase())
}
function isAudio(ext?: string | null) {
  return !!ext && AUDIO_EXTS.has(ext.toLowerCase())
}

function formatSize(bytes?: number | null): string {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

function fileUrl(id: number) {
  return `/file/${id}`
}

const query = reactive<SysFileQuery>({
  originalName: '',
  fileType: null,
  userId: null,
  startTime: null,
  endTime: null,
  pageNum: 1,
  pageSize: 10,
})

const timeRange = ref<[number, number] | null>(null)

const loading = ref(false)
const tableData = ref<SysFile[]>([])
const total = ref(0)
const checkedKeys = ref<number[]>([])

const typeOptions: SelectOption[] = [
  { label: '全部', value: null as unknown as string },
  { label: '图片', value: 'image' },
  { label: '音频', value: 'audio' },
]

const allColumns: DataTableColumns<SysFile> = [
  { type: 'selection' },
  { title: 'ID', key: 'id', width: 70 },
  {
    title: '预览', key: 'preview', width: 80,
    render: (row) => {
      if (isImage(row.ext)) {
        return h('img', {
          src: fileUrl(row.id),
          alt: row.originalName || '',
          style: 'width:40px;height:40px;object-fit:cover;border-radius:4px;background:var(--c-fill-2)',
        })
      }
      if (isAudio(row.ext)) {
        return h(NIcon, { size: 28, color: 'var(--c-brand)' },
          { default: () => h(iconMap.volume) })
      }
      return h('span', { style: 'color:var(--c-text-3)' }, '-')
    },
  },
  { title: '文件名', key: 'originalName', ellipsis: { tooltip: true }, width: 200 },
  {
    title: '类型', key: 'ext', width: 80,
    render: (row) => h(NTag, { size: 'small', type: isImage(row.ext) ? 'success' : 'info' },
      { default: () => (row.ext || '-').toUpperCase() }),
  },
  {
    title: '大小', key: 'size', width: 100,
    render: (row) => formatSize(row.size),
  },
  {
    title: '上传者', key: 'uploaderName', width: 120,
    render: (row) => row.uploaderName || h('span', { style: 'color:var(--c-text-3)' }, '-'),
  },
  { title: '上传时间', key: 'createTime', width: 170, render: (row) => formatTime(row.createTime) },
  {
    title: '操作', key: 'actions', width: 130, fixed: 'right',
    render: (row) => h(NSpace, { size: 0 },
      {
        default: () => [
          h(NButton, { size: 'small', text: true, onClick: () => viewDetail(row) },
            { icon: () => h(NIcon, null, { default: () => h(iconMap.eye) }), default: () => '查看' }),
          h(NPopconfirm, { onPositiveClick: () => handleDelete(row.id) },
            {
              trigger: () => h(NButton, { size: 'small', text: true, type: 'error' },
                { icon: () => h(NIcon, null, { default: () => h(iconMap.trash) }), default: () => '删除' }),
              default: () => `确认删除「${row.originalName || row.id}」？引用方（头像/海螺/AI 产物）将悬空。`,
            }),
        ],
      }),
  },
]

const mobileHiddenKeys = new Set(['id', 'uploaderName'])
const columns = computed<DataTableColumns<SysFile>>(() =>
  isMobile.value
    ? allColumns.filter((c) => !mobileHiddenKeys.has((c as { key?: string }).key ?? ''))
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
    query.pageNum = page
    loadData()
  },
  onUpdatePageSize: (size: number) => {
    pagination.pageSize = size
    query.pageSize = size
    pagination.page = 1
    query.pageNum = 1
    loadData()
  },
})

async function loadData() {
  loading.value = true
  try {
    if (timeRange.value) {
      query.startTime = new Date(timeRange.value[0]).toISOString()
      query.endTime = new Date(timeRange.value[1]).toISOString()
    } else {
      query.startTime = null
      query.endTime = null
    }
    const res = await getFilePage(query)
    tableData.value = res.records
    total.value = res.total
    pagination.itemCount = res.total
  } catch {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  query.pageNum = 1
  checkedKeys.value = []
  loadData()
}

function handleReset() {
  query.originalName = ''
  query.fileType = null
  timeRange.value = null
  handleSearch()
}

async function handleDelete(id: number) {
  try {
    await deleteFile(id)
    message.success('删除成功')
    if (detail.value?.id === id) detailShow.value = false
    if (checkedKeys.value.includes(id)) {
      checkedKeys.value = checkedKeys.value.filter((k) => k !== id)
    }
    loadData()
  } catch {
    // 错误已由拦截器提示
  }
}

async function handleBatchDelete() {
  if (checkedKeys.value.length === 0) return
  try {
    await deleteFileBatch(checkedKeys.value)
    message.success(`已删除 ${checkedKeys.value.length} 个文件`)
    checkedKeys.value = []
    loadData()
  } catch {
    // 错误已由拦截器提示
  }
}

const detailShow = ref(false)
const detail = ref<SysFile | null>(null)

function viewDetail(row: SysFile) {
  detail.value = row
  detailShow.value = true
}

onMounted(loadData)
</script>

<template>
  <div class="file-page">
    <NCard :bordered="false" class="filter-card">
      <NForm label-placement="left" :show-feedback="false" inline>
        <NFormItem label="文件名">
          <NInput v-model:value="query.originalName" placeholder="原始文件名" clearable style="width: 170px" />
        </NFormItem>
        <NFormItem label="类型">
          <NSelect v-model:value="query.fileType" :options="typeOptions" style="width: 120px" />
        </NFormItem>
        <NFormItem label="时间范围">
          <NDatePicker v-model:value="timeRange" type="datetimerange" clearable style="width: 100%; max-width: 360px" />
        </NFormItem>
        <NSpace>
          <NButton type="primary" @click="handleSearch">
            <template #icon><NIcon><component :is="iconMap.search" /></NIcon></template>
            搜索
          </NButton>
          <NButton @click="handleReset">重置</NButton>
        </NSpace>
      </NForm>
    </NCard>

    <NCard :bordered="false">
      <div class="table-header">
        <span class="total-text">共 <b>{{ total }}</b> 条</span>
        <NPopconfirm @positive-click="handleBatchDelete">
          <template #trigger>
            <NButton type="error" ghost size="small" :disabled="checkedKeys.length === 0">
              <template #icon><NIcon><component :is="iconMap.trash" /></NIcon></template>
              批量删除<template v-if="checkedKeys.length">({{ checkedKeys.length }})</template>
            </NButton>
          </template>
          确认删除选中的 {{ checkedKeys.length }} 个文件？引用方将悬空。
        </NPopconfirm>
      </div>
      <NDataTable
        :columns="columns"
        :data="tableData"
        :loading="loading"
        :pagination="pagination"
        :row-key="(row: SysFile) => row.id"
        v-model:checked-row-keys="checkedKeys"
        :scroll-x="1100"
        remote
        striped
        size="small"
      />
    </NCard>

    <NDrawer v-model:show="detailShow" :width="drawerWidth" placement="right">
      <NDrawerContent title="文件详情" :native-scrollbar="false" closable>
        <template v-if="detail">
          <div class="preview-box">
            <img v-if="isImage(detail.ext)" :src="fileUrl(detail.id)" :alt="detail.originalName || ''" />
            <audio v-else-if="isAudio(detail.ext)" :src="fileUrl(detail.id)" controls style="width: 100%" />
            <div v-else class="no-preview">无预览</div>
          </div>
          <NButton tag="a" :href="fileUrl(detail.id)" :download="detail.originalName || undefined" size="small" type="primary" ghost style="margin-top: 12px">
            <template #icon><NIcon><component :is="iconMap.download" /></NIcon></template>
            下载
          </NButton>
          <NDescriptions :column="1" label-placement="left" bordered size="small" style="margin-top: 16px">
            <NDescriptionsItem label="ID">{{ detail.id }}</NDescriptionsItem>
            <NDescriptionsItem label="文件名">{{ detail.originalName || '-' }}</NDescriptionsItem>
            <NDescriptionsItem label="扩展名">{{ (detail.ext || '-').toUpperCase() }}</NDescriptionsItem>
            <NDescriptionsItem label="MIME">{{ detail.contentType || '-' }}</NDescriptionsItem>
            <NDescriptionsItem label="大小">{{ formatSize(detail.size) }}</NDescriptionsItem>
            <NDescriptionsItem label="上传者">{{ detail.uploaderName || '-' }}</NDescriptionsItem>
            <NDescriptionsItem label="上传时间">{{ formatTime(detail.createTime) }}</NDescriptionsItem>
          </NDescriptions>
        </template>
      </NDrawerContent>
    </NDrawer>
  </div>
</template>

<style scoped>
.file-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.total-text {
  font-size: 13px;
  color: var(--n-text-color-3, var(--c-text-3));
}

.total-text b {
  color: var(--c-brand);
  font-size: 15px;
}

.filter-card :deep(.n-form) {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.preview-box {
  display: flex;
  justify-content: center;
  align-items: center;
  background: var(--c-fill-2);
  border-radius: 8px;
  padding: 12px;
  min-height: 160px;
}

.preview-box img {
  max-width: 100%;
  max-height: 420px;
  border-radius: 6px;
}

.no-preview {
  color: var(--c-text-3);
}
</style>
