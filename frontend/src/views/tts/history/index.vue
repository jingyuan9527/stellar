<script setup lang="ts">
import { h, onBeforeUnmount, reactive, ref } from 'vue'
import {
  NCard, NForm, NFormItem, NInput, NSelect, NDatePicker, NButton, NSpace,
  NDataTable, NTag, NDrawer, NDrawerContent, NDescriptions, NDescriptionsItem,
  NIcon, NEmpty, useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { getTtsRecordPage, getTtsRecordAudio, deleteTtsRecord } from '@/api/tts'
import type { TtsRecord, TtsRecordQuery } from '@/types/api'
import { iconMap } from '@/utils/icons'
import { getVoiceLabel, ttsVoiceOptions } from '@/constants/tts-voices'
import { useAuthStore } from '@/store/auth'

const message = useMessage()
const authStore = useAuthStore()

const query = reactive<TtsRecordQuery>({
  text: '',
  voice: '',
  startTime: null,
  endTime: null,
  pageNum: 1,
  pageSize: 10,
})

const timeRange = ref<[number, number] | null>(null)
const loading = ref(false)
const tableData = ref<TtsRecord[]>([])
const total = ref(0)

function formatTime(s?: string): string {
  if (!s) return ''
  return s.replace('T', ' ').slice(0, 19)
}

function formatFileSize(bytes?: number): string {
  if (!bytes) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function formatParams(rate?: number, pitch?: number, volume?: number): string {
  return [
    `语速 ${(rate ?? 1).toFixed(1)}`,
    `音调 ${(pitch ?? 1).toFixed(1)}`,
    `音量 ${Math.round((volume ?? 1) * 100)}%`,
  ].join(' / ')
}

const columns: DataTableColumns<TtsRecord> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '合成时间', key: 'createTime', width: 170, render: (row) => formatTime(row.createTime) },
  {
    title: '文本内容', key: 'text', ellipsis: { tooltip: true },
    render: (row) => row.text.length > 50 ? row.text.slice(0, 50) + '...' : row.text,
  },
  {
    title: '发音人', key: 'voice', width: 150,
    render: (row) => h(NTag, { size: 'small', type: 'info' }, { default: () => getVoiceLabel(row.voice) }),
  },
  {
    title: '参数', key: 'params', width: 220,
    render: (row) => formatParams(row.rate, row.pitch, row.volume),
  },
  {
    title: '文件大小', key: 'fileSize', width: 100,
    render: (row) => formatFileSize(row.fileSize),
  },
  {
    title: '操作', key: 'actions', width: 200, fixed: 'right',
    render: (row) => {
      const actions = [
        h(NButton, { size: 'small', text: true, type: 'primary', onClick: () => openAudio(row) },
          { icon: () => h(NIcon, null, { default: () => h(iconMap.play) }), default: () => '试听' }),
        h(NButton, { size: 'small', text: true, type: 'info', onClick: () => handleDownload(row) },
          { icon: () => h(NIcon, null, { default: () => h(iconMap.download) }), default: () => '下载' }),
      ]
      // 删除仅登录用户可见（公共墙游客只读）
      if (authStore.isLogin) {
        actions.push(h(NButton, { size: 'small', text: true, type: 'error', onClick: () => handleDelete(row) },
          { icon: () => h(NIcon, null, { default: () => h(iconMap.trash) }), default: () => '删除' }))
      }
      return h(NSpace, { size: 4 }, { default: () => actions })
    },
  },
]

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
    const res = await getTtsRecordPage(query)
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
  loadData()
}

function handleReset() {
  query.text = ''
  query.voice = ''
  timeRange.value = null
  handleSearch()
}

// ===== 试听 Drawer =====
const audioDrawerShow = ref(false)
const audioLoading = ref(false)
const audioUrl = ref<string | null>(null)
const currentRecord = ref<TtsRecord | null>(null)

function revokeAudioUrl() {
  if (audioUrl.value) {
    URL.revokeObjectURL(audioUrl.value)
    audioUrl.value = null
  }
}

async function openAudio(row: TtsRecord) {
  currentRecord.value = row
  audioDrawerShow.value = true
  revokeAudioUrl()
  audioLoading.value = true
  try {
    const blob = await getTtsRecordAudio(row.id)
    audioUrl.value = URL.createObjectURL(blob)
  } catch {
    // 错误已由拦截器提示
  } finally {
    audioLoading.value = false
  }
}

function closeAudioDrawer() {
  audioDrawerShow.value = false
  revokeAudioUrl()
  currentRecord.value = null
}

async function handleDownload(row: TtsRecord) {
  try {
    const blob = await getTtsRecordAudio(row.id)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `tts_${row.id}_${row.voice}.mp3`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    message.success('下载成功')
  } catch {
    // 错误已由拦截器提示
  }
}

function handleDelete(row: TtsRecord) {
  window.$dialog?.warning({
    title: '确认删除',
    content: `确定要删除这条合成记录吗？（ID: ${row.id}）`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await deleteTtsRecord(row.id)
        message.success('删除成功')
        loadData()
      } catch {
        // 错误已由拦截器提示
      }
    },
  })
}

onBeforeUnmount(() => {
  revokeAudioUrl()
})

loadData()
</script>

<template>
  <div class="history-page">
    <NCard :bordered="false" class="filter-card">
      <NForm label-placement="left" :show-feedback="false" inline>
        <NFormItem label="文本">
          <NInput v-model:value="query.text" placeholder="搜索文本内容" clearable style="width: 180px" />
        </NFormItem>
        <NFormItem label="发音人">
          <NSelect
            v-model:value="query.voice"
            :options="ttsVoiceOptions"
            placeholder="全部"
            clearable
            filterable
            style="width: 200px"
          />
        </NFormItem>
        <NFormItem label="时间范围">
          <NDatePicker v-model:value="timeRange" type="datetimerange" clearable style="width: 360px" />
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
      </div>
      <NDataTable
        :columns="columns"
        :data="tableData"
        :loading="loading"
        :pagination="pagination"
        :scroll-x="1000"
        remote
        striped
        size="small"
      />
    </NCard>

    <NDrawer :show="audioDrawerShow" :width="560" placement="right" @update:show="(v: boolean) => !v && closeAudioDrawer()">
      <NDrawerContent title="试听音频" :native-scrollbar="false" closable>
        <NDescriptions v-if="currentRecord" :column="1" label-placement="left" bordered size="small">
          <NDescriptionsItem label="ID">{{ currentRecord.id }}</NDescriptionsItem>
          <NDescriptionsItem label="合成时间">{{ formatTime(currentRecord.createTime) }}</NDescriptionsItem>
          <NDescriptionsItem label="发音人">{{ getVoiceLabel(currentRecord.voice) }}</NDescriptionsItem>
          <NDescriptionsItem label="参数">{{ formatParams(currentRecord.rate, currentRecord.pitch, currentRecord.volume) }}</NDescriptionsItem>
          <NDescriptionsItem label="文件大小">{{ formatFileSize(currentRecord.fileSize) }}</NDescriptionsItem>
          <NDescriptionsItem label="文本内容">
            <div class="record-text">{{ currentRecord.text }}</div>
          </NDescriptionsItem>
        </NDescriptions>

        <div class="audio-section">
          <div v-if="audioLoading" class="audio-loading">
            <NButton loading text>加载音频中...</NButton>
          </div>
          <div v-else-if="audioUrl" class="audio-player">
            <audio :src="audioUrl" controls style="width: 100%" />
          </div>
          <NEmpty v-else description="音频加载失败" />
        </div>
      </NDrawerContent>
    </NDrawer>
  </div>
</template>

<style scoped>
.history-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.total-text {
  font-size: 13px;
  color: var(--n-text-color-3, #999);
}

.total-text b {
  color: var(--primary-color, #18a058);
  font-size: 15px;
}

.filter-card :deep(.n-form) {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.record-text {
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.6;
}

.audio-section {
  margin-top: 20px;
}

.audio-loading,
.audio-player {
  display: flex;
  justify-content: center;
  padding: 20px 0;
}
</style>
