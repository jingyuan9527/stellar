<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  NCard, NSpace, NInput, NSelect, NButton, NDataTable, NTag, NSwitch,
  NModal, NFormItem, NUpload, NPopconfirm, NTabs, NTabPane, useMessage,
} from 'naive-ui'
import type { DataTableColumns, SelectOption, UploadCustomRequestOptions } from 'naive-ui'
import {
  getConchAnswerPage, createConchAnswer, updateConchAnswer,
  deleteConchAnswer, toggleConchAnswerEnabled, getConchRecordPage,
  getConchAnswerAudio,
} from '@/api/conch'
import { formatTime } from '@/utils/format'
import { uploadFile } from '@/api/file'
import { getSetting, setSetting } from '@/api/ai'
import type { ConchAnswer, ConchAnswerQuery, ConchRecord } from '@/types/api'
import { useIsMobile } from '@/composables/useBreakpoint'

const message = useMessage()
const isMobile = useIsMobile()

// ===== AI 匹配开关 =====
const conchAiEnabled = ref(1)
const aiSwitchLoading = ref(false)

async function loadAiConfig() {
  try {
    const v = await getSetting('conch_ai_enabled')
    conchAiEnabled.value = v === '0' ? 0 : 1
  } catch {
    // 静默
  }
}

async function handleToggleAi(v: number) {
  aiSwitchLoading.value = true
  try {
    await setSetting('conch_ai_enabled', String(v))
    message.success(v === 1 ? '已开启 AI 匹配' : '已关闭 AI 匹配（纯随机）')
  } catch {
    conchAiEnabled.value = v === 1 ? 0 : 1
  } finally {
    aiSwitchLoading.value = false
  }
}

// ===== 预设管理 =====
const query = reactive<ConchAnswerQuery>({ answerText: '', enabled: null, pageNum: 1, pageSize: 10 })
const loading = ref(false)
const tableData = ref<ConchAnswer[]>([])
const total = ref(0)

const enabledOptions: SelectOption[] = [
  { value: 1, label: '启用' },
  { value: 0, label: '禁用' },
]



const currentAudioId = ref<number | null>(null)
const currentAudioUrl = ref<string | null>(null)
const audioLoadingId = ref<number | null>(null)

function revokeAudioUrl() {
  if (currentAudioUrl.value) {
    URL.revokeObjectURL(currentAudioUrl.value)
    currentAudioUrl.value = null
  }
}

async function togglePlay(id: number) {
  if (currentAudioId.value === id) {
    currentAudioId.value = null
    revokeAudioUrl()
    return
  }
  revokeAudioUrl()
  audioLoadingId.value = id
  try {
    const blob = await getConchAnswerAudio(id)
    currentAudioUrl.value = URL.createObjectURL(blob)
    currentAudioId.value = id
  } catch {
    // 错误已由拦截器提示
  } finally {
    audioLoadingId.value = null
  }
}

const allColumns: DataTableColumns<ConchAnswer> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '回答文本', key: 'answerText', width: 160, ellipsis: { tooltip: true } },
  {
    title: '匹配描述', key: 'matchDescription', ellipsis: { tooltip: true },
    render: (row) => row.matchDescription
      ? h('span', { style: 'color: var(--c-text-3); font-size: 12px' }, row.matchDescription)
      : h('span', { style: 'color: var(--c-text-3)' }, '—'),
  },
  {
    title: '启用', key: 'enabled', width: 80,
    render: (row) => h(NSwitch, {
      value: row.enabled === 1,
      size: 'small',
      onUpdateValue: (v: boolean) => handleToggle(row.id, v ? 1 : 0),
    }),
  },
  {
    title: '试听', key: 'audio', width: 90,
    render: (row) => h(NButton, {
      size: 'small', text: true, type: 'primary',
      loading: audioLoadingId.value === row.id,
      onClick: () => togglePlay(row.id),
    }, { default: () => currentAudioId.value === row.id ? '停止' : '试听' }),
  },
  { title: '创建时间', key: 'createTime', width: 160, render: (row) => formatTime(row.createTime) },
  {
    title: '操作', key: 'actions', width: 160,
    render: (row) => h(NSpace, { size: 'small' }, {
      default: () => [
        h(NButton, { size: 'small', onClick: () => startEdit(row) }, { default: () => '编辑' }),
        h(NPopconfirm, { onPositiveClick: () => handleDelete(row.id) }, {
          trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
          default: () => '确认删除？',
        }),
      ],
    }),
  },
]

const mobileHiddenKeys = new Set(['id', 'createTime'])
const columns = computed<DataTableColumns<ConchAnswer>>(() =>
  isMobile.value
    ? allColumns.filter((c) => !mobileHiddenKeys.has((c as { key?: string }).key ?? ''))
    : allColumns,
)

async function loadData() {
  loading.value = true
  try {
    const res = await getConchAnswerPage(query)
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

async function handleToggle(id: number, enabled: number) {
  try {
    await toggleConchAnswerEnabled(id, enabled)
    message.success(enabled === 1 ? '已启用' : '已禁用')
    loadData()
  } catch {
    // 错误已由拦截器提示
  }
}

async function handleDelete(id: number) {
  try {
    await deleteConchAnswer(id)
    message.success('已删除')
    loadData()
  } catch {
    // 错误已由拦截器提示
  }
}

// ===== 新增/编辑 =====
const editShow = ref(false)
const editing = ref<{ id: number | null; answerText: string; matchDescription: string; fileId: number | null } | null>(null)

function startAdd() {
  editing.value = { id: null, answerText: '', matchDescription: '', fileId: null }
  editShow.value = true
}

function startEdit(row: ConchAnswer) {
  editing.value = {
    id: row.id,
    answerText: row.answerText,
    matchDescription: row.matchDescription || '',
    fileId: row.fileId,
  }
  editShow.value = true
}

function parseFileId(url: string): number {
  return Number(url.split('/').pop())
}

async function customUpload({ file, onFinish, onError }: UploadCustomRequestOptions) {
  const f = file.file as File
  if (!f) {
    onError()
    return
  }
  try {
    // 海螺预设音频游客可播放，需标记公开
    const url = await uploadFile(f, true)
    editing.value!.fileId = parseFileId(url)
    message.success('音频上传成功')
    onFinish()
  } catch {
    onError()
  }
}

async function handleSave() {
  if (!editing.value) return
  if (!editing.value.answerText.trim()) {
    message.warning('回答文本不能为空')
    return
  }
  if (!editing.value.fileId) {
    message.warning('请上传音频')
    return
  }
  try {
    if (editing.value.id) {
      await updateConchAnswer({
        id: editing.value.id,
        answerText: editing.value.answerText,
        matchDescription: editing.value.matchDescription || undefined,
        fileId: editing.value.fileId,
      })
    } else {
      await createConchAnswer({
        answerText: editing.value.answerText,
        matchDescription: editing.value.matchDescription || undefined,
        fileId: editing.value.fileId,
      })
    }
    message.success('已保存')
    editShow.value = false
    loadData()
  } catch {
    // 错误已由拦截器提示
  }
}

// ===== 提问历史 =====
const recordLoading = ref(false)
const recordData = ref<ConchRecord[]>([])
const recordTotal = ref(0)
const recordPage = reactive({ pageNum: 1, pageSize: 10 })

const allRecordColumns: DataTableColumns<ConchRecord> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '问题', key: 'questionText', ellipsis: { tooltip: true } },
  {
    title: '命中回答', key: 'answerText', width: 160,
    render: (row) => row.answerText
      ? h(NTag, { size: 'small', type: 'success', bordered: false }, { default: () => row.answerText })
      : h('span', { style: 'color: var(--c-text-3)' }, '—'),
  },
  {
    title: '用户', key: 'userId', width: 100,
    render: (row) => row.userId ? h('span', null, String(row.userId)) : h(NTag, { size: 'small', bordered: false }, { default: () => '游客' }),
  },
  { title: '时间', key: 'createTime', width: 160, render: (row) => formatTime(row.createTime) },
]

const mobileHiddenRecordKeys = new Set(['id', 'userId'])
const recordColumns = computed<DataTableColumns<ConchRecord>>(() =>
  isMobile.value
    ? allRecordColumns.filter((c) => !mobileHiddenRecordKeys.has((c as { key?: string }).key ?? ''))
    : allRecordColumns,
)

async function loadRecords() {
  recordLoading.value = true
  try {
    const res = await getConchRecordPage(recordPage.pageNum, recordPage.pageSize)
    recordData.value = res.records
    recordTotal.value = res.total
  } catch {
    // 错误已由拦截器提示
  } finally {
    recordLoading.value = false
  }
}

function handleRecordPageChange(page: number) {
  recordPage.pageNum = page
  loadRecords()
}

function handleTabChange(name: string | number) {
  if (name === 'records' && recordData.value.length === 0) {
    loadRecords()
  }
}

onBeforeUnmount(() => {
  revokeAudioUrl()
})

onMounted(() => {
  loadData()
  loadAiConfig()
})
</script>

<template>
  <div class="conch-admin-page">
    <NCard title="神奇海螺" :bordered="false">
      <div class="ai-switch-row">
        <NSpace align="center" :size="8">
          <NSwitch
            v-model:value="conchAiEnabled"
            :checked-value="1"
            :unchecked-value="0"
            :loading="aiSwitchLoading"
            @update:value="handleToggleAi"
          />
          <span class="switch-label">AI 语义匹配</span>
          <span class="switch-hint">{{ conchAiEnabled === 1 ? '开启：AI 选 top-3 随机选 1（调 LLM，较慢）' : '关闭：纯随机不调 LLM（快）' }}</span>
        </NSpace>
      </div>
      <NTabs type="line" @update:value="handleTabChange">
        <NTabPane name="answers" tab="预设管理">
          <NSpace align="center" :size="12" style="margin-bottom: 16px">
            <NInput
              v-model:value="query.answerText"
              placeholder="回答文本"
              clearable
              style="width: 200px"
              @keyup.enter="handleSearch"
            />
            <NSelect
              v-model:value="query.enabled"
              :options="enabledOptions"
              placeholder="状态"
              clearable
              style="width: 120px"
              @update:value="handleSearch"
            />
            <NButton @click="handleSearch">搜索</NButton>
            <NButton type="primary" @click="startAdd">新增预设</NButton>
          </NSpace>

          <NDataTable
            :columns="columns"
            :data="tableData"
            :loading="loading"
            :row-key="(row: ConchAnswer) => row.id"
            :pagination="{
              page: query.pageNum,
              pageSize: query.pageSize,
              itemCount: total,
              showSizePicker: false,
              onChange: handlePageChange,
            }"
            :scroll-x="800"
            :bordered="false"
            remote
          />

          <div v-if="currentAudioUrl" class="inline-audio">
            <audio :src="currentAudioUrl" controls autoplay style="width: 100%" />
          </div>
        </NTabPane>
        <NTabPane name="records" tab="提问历史">
          <NDataTable
            :columns="recordColumns"
            :data="recordData"
            :loading="recordLoading"
            :row-key="(row: ConchRecord) => row.id"
            :pagination="{
              page: recordPage.pageNum,
              pageSize: recordPage.pageSize,
              itemCount: recordTotal,
              showSizePicker: false,
              onChange: handleRecordPageChange,
            }"
            :scroll-x="600"
            :bordered="false"
            remote
          />
        </NTabPane>
      </NTabs>
    </NCard>

    <NModal
      v-model:show="editShow"
      preset="card"
      :title="editing?.id ? '编辑预设' : '新增预设'"
      :style="{ width: 'var(--modal-md)', maxWidth: '90vw' }"
    >
      <NSpace v-if="editing" vertical :size="16">
        <NFormItem label="回答文本">
          <NInput v-model:value="editing.answerText" placeholder="如：确实如此" maxlength="200" />
        </NFormItem>
        <NFormItem label="匹配描述">
          <NInput
            v-model:value="editing.matchDescription"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 5 }"
            placeholder="辅助 AI 语义匹配，如：适合肯定类问题"
            maxlength="500"
          />
        </NFormItem>
        <NFormItem label="音频">
          <NSpace align="center" :size="12">
            <NUpload :custom-request="customUpload" :show-file-list="false" accept="audio/*">
              <NButton>{{ editing.fileId ? '重新上传' : '上传音频' }}</NButton>
            </NUpload>
            <audio
              v-if="editing.fileId"
              :src="`/file/${editing.fileId}`"
              controls
              style="height: 36px"
            />
          </NSpace>
        </NFormItem>
      </NSpace>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="editShow = false">取消</NButton>
          <NButton type="primary" @click="handleSave">保存</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.conch-admin-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ai-switch-row {
  margin-bottom: 16px;
  padding: 12px 16px;
  border-radius: 8px;
  background: var(--c-fill-2);
}

.switch-label {
  font-size: 14px;
  font-weight: 500;
}

.switch-hint {
  font-size: 12px;
  color: var(--c-text-3);
}

.inline-audio {
  margin-top: 12px;
}
</style>
