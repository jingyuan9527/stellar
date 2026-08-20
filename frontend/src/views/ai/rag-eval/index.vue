<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NCard, NButton, NDataTable, NModal, NForm, NFormItem, NInput, NTag, NSelect,
  NSpace, NPopconfirm, NSwitch, useMessage,
} from 'naive-ui'
import type { DataTableColumns, SelectOption } from 'naive-ui'
import {
  pageRagEvalCases, createRagEvalCase, updateRagEvalCase, deleteRagEvalCase,
  runRagEval, getRagEvalRunResults, listRecentRagEvalRuns, pageRagFeedback,
} from '@/api/rag'
import { listKnowledgeBases } from '@/api/chat'
import type { RagEvalCase, RagEvalResultRow, RagEvalRunVO, RagFeedbackVO, RagSource } from '@/types/api'
import { formatTime } from '@/utils/format'
import { useIsMobile } from '@/composables/useBreakpoint'

const message = useMessage()
const isMobile = useIsMobile()

// ===== 评估集 =====

const loading = ref(false)
const cases = ref<RagEvalCase[]>([])
const caseTotal = ref(0)
const casePagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
  onChange: (p: number) => { casePagination.page = p; loadCases() },
  onUpdatePageSize: (s: number) => { casePagination.pageSize = s; casePagination.page = 1; loadCases() },
})

const kbOptions = ref<SelectOption[]>([])

async function loadKbs() {
  try {
    const list = await listKnowledgeBases()
    kbOptions.value = list.map((k) => ({ label: k.name, value: k.id }))
  } catch {
    // 错误已由拦截器提示
  }
}

async function loadCases() {
  loading.value = true
  try {
    const res = await pageRagEvalCases({ pageNum: casePagination.page, pageSize: casePagination.pageSize })
    cases.value = res.records
    casePagination.itemCount = res.total
  } catch {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

// ===== 用例编辑 =====

const modalShow = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  query: '',
  kbId: null as number | null,
  expectedSources: '', // 多行输入，逗号/换行分隔
  note: '',
})

function rowToForm(row: RagEvalCase) {
  editingId.value = row.id
  form.query = row.query
  form.kbId = row.kbId
  form.note = row.note ?? ''
  let srcs: string[] = []
  try {
    srcs = JSON.parse(row.expectedSources || '[]')
  } catch {
    srcs = (row.expectedSources || '').split(',')
  }
  form.expectedSources = srcs.join('\n')
  modalShow.value = true
}

function openCreate() {
  editingId.value = null
  form.query = ''
  form.kbId = null
  form.expectedSources = ''
  form.note = ''
  modalShow.value = true
}

function parseSources(text: string): string[] {
  return text.split(/[\n,，、]+/).map((s) => s.trim()).filter(Boolean)
}

async function handleSaveCase() {
  const expected = parseSources(form.expectedSources)
  if (!form.query.trim()) {
    message.warning('请输入问题')
    return
  }
  if (expected.length === 0) {
    message.warning('请输入至少一个期望来源（如 memos:12）')
    return
  }
  saving.value = true
  try {
    const data = {
      query: form.query.trim(),
      kbId: form.kbId,
      expectedSources: expected,
      note: form.note || undefined,
    }
    if (editingId.value) {
      await updateRagEvalCase({ id: editingId.value, ...data })
    } else {
      await createRagEvalCase(data)
    }
    message.success('已保存')
    modalShow.value = false
    loadCases()
  } finally {
    saving.value = false
  }
}

async function handleDeleteCase(id: number) {
  try {
    await deleteRagEvalCase(id)
    message.success('已删除')
    loadCases()
  } catch {
    // 错误已由拦截器提示
  }
}

const caseColumns: DataTableColumns<RagEvalCase> = [
  { title: 'ID', key: 'id', width: 60 },
  { title: '问题', key: 'query', ellipsis: { tooltip: true }, width: 200 },
  {
    title: '知识库', key: 'kbId', width: 80,
    render: (row) => h(NTag, { size: 'small', bordered: false, type: row.kbId ? 'info' : 'default' },
      { default: () => row.kbId ? `kb:${row.kbId}` : '仅备忘' }),
  },
  {
    title: '期望来源', key: 'expectedSources', width: 140,
    render: (row) => h(NTag, { size: 'small', bordered: false, type: 'success' },
      { default: () => row.expectedSources.replace(/[\["\]]/g, '').slice(0, 40) }),
  },
  { title: '备注', key: 'note', ellipsis: { tooltip: true }, width: 120 },
  { title: '创建时间', key: 'createTime', width: 150, render: (row) => formatTime(row.createTime) },
  {
    title: '操作', key: 'actions', width: 130, fixed: 'right',
    render: (row) => h(NSpace, { size: 'small' }, {
      default: () => [
        h(NButton, { size: 'tiny', onClick: () => rowToForm(row) }, { default: () => '编辑' }),
        h(NPopconfirm, { onPositiveClick: () => handleDeleteCase(row.id) }, {
          trigger: () => h(NButton, { size: 'tiny', type: 'error' }, { default: () => '删除' }),
          default: () => '删除该评估用例？（历史跑分保留）',
        }),
      ],
    }),
  },
]

// ===== 跑分 =====

const running = ref(false)
const fullMode = ref(false)
const runSummary = ref<RagEvalRunVO | null>(null)
const historyRuns = ref<string[]>([])
const selectedRunId = ref<string | null>(null)
const historyResults = ref<RagEvalResultRow[]>([])
const historyLoading = ref(false)

async function handleRun() {
  running.value = true
  try {
    runSummary.value = await runRagEval(fullMode.value ? 'full' : 'retrieval')
    message.success(`跑分完成（${runSummary.value.mode}）：通过 ${runSummary.value.passCount}/${runSummary.value.total}，平均召回 ${(runSummary.value.recallAvg * 100).toFixed(1)}%`)
    selectedRunId.value = runSummary.value.runId
    loadHistoryRuns()
  } catch {
    // 错误已由拦截器提示
  } finally {
    running.value = false
  }
}

async function loadHistoryRuns() {
  try {
    historyRuns.value = await listRecentRagEvalRuns(20)
    if (historyRuns.value.length > 0 && !selectedRunId.value) {
      selectedRunId.value = historyRuns.value[0]
    }
    if (selectedRunId.value) loadRunResults(selectedRunId.value)
  } catch {
    // 错误已由拦截器提示
  }
}

async function loadRunResults(runId: string) {
  historyLoading.value = true
  try {
    historyResults.value = await getRagEvalRunResults(runId)
  } finally {
    historyLoading.value = false
  }
}

const runColumns: DataTableColumns<RagEvalResultRow> = [
  { title: '用例ID', key: 'caseId', width: 70 },
  { title: '问题', key: 'query', ellipsis: { tooltip: true }, width: 220 },
  {
    title: '模式', key: 'mode', width: 90,
    render: (row) => h(NTag, { size: 'small', bordered: false, type: row.mode === 'full' ? 'warning' : 'default' },
      { default: () => row.mode === 'full' ? '完整管线' : '纯检索' }),
  },
  {
    title: '结果', key: 'pass', width: 80,
    render: (row) => h(NTag, { size: 'small', bordered: false, type: row.pass === 1 ? 'success' : 'error' },
      { default: () => row.pass === 1 ? '命中' : '未命中' }),
  },
  { title: '召回率', key: 'recall', width: 90, render: (row) => `${(row.recall * 100).toFixed(0)}%` },
  {
    title: '实际命中 top-k', key: 'topHits', ellipsis: { tooltip: true }, width: 240,
    render: (row) => {
      const hits = (row.topHits ? JSON.parse(row.topHits) : []) as RagSource[]
      if (!hits.length) return '（无召回）'
      const labels = hits.map((x) => x.title || `${x.source}:${x.sourceKey}`).join(' / ')
      return h('span', { style: 'font-size: 12px' }, labels)
    },
  },
  { title: '跑分时间', key: 'createTime', width: 150, render: (row) => formatTime(row.createTime) },
]

// ===== 不良反馈（bad case）=====

const fbLoading = ref(false)
const badCases = ref<RagFeedbackVO[]>([])
const fbTotal = ref(0)
const fbPagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  onChange: (p: number) => { fbPagination.page = p; loadFeedback() },
})

async function loadFeedback() {
  fbLoading.value = true
  try {
    const res = await pageRagFeedback({ value: -1, pageNum: fbPagination.page, pageSize: fbPagination.pageSize })
    badCases.value = res.records
    fbPagination.itemCount = res.total
  } finally {
    fbLoading.value = false
  }
}

const fbColumns: DataTableColumns<RagFeedbackVO> = [
  { title: '消息ID', key: 'messageId', width: 80 },
  {
    title: '回答摘要', key: 'content', ellipsis: { tooltip: true },
    render: (row) => h('span', { style: 'font-size: 12px' }, (row.content || '(无文本)').slice(0, 120)),
  },
  {
    title: 'RAG 引用', key: 'refs', width: 90,
    render: (row) => h(NTag, { size: 'small', bordered: false, type: row.refs?.length ? 'info' : 'default' },
      { default: () => row.refs?.length ? `${row.refs.length} 条` : '无' }),
  },
  { title: '来源', key: 'subjectId', width: 110, render: (row) => row.subjectType === 'account' ? '账号' : row.subjectId },
  { title: '评价时间', key: 'createTime', width: 150, render: (row) => formatTime(row.createTime) },
]

const mobileHiddenCaseKeys = new Set(['kbId', 'note', 'createTime'])
const caseColumnsFinal = computed(() =>
  isMobile.value ? caseColumns.filter((c) => !mobileHiddenCaseKeys.has((c as { key?: string }).key ?? '')) : caseColumns,
)

onMounted(() => {
  loadCases()
  loadHistoryRuns()
  loadFeedback()
  loadKbs()
})
</script>

<template>
  <div class="rag-eval-page">
    <NCard title="评估集（golden set）" :bordered="false">
      <template #header-extra>
        <NSpace :size="8" align="center">
          <NSwitch v-model:value="fullMode" size="small" />
          <span style="font-size: 13px">完整管线</span>
          <NButton type="primary" :loading="running" @click="handleRun">运行评估</NButton>
          <NButton @click="openCreate">新增用例</NButton>
        </NSpace>
      </template>
      <NAlert type="info" :bordered="false" style="margin-bottom: 12px">
        每条用例 = 问题 + 期望命中的来源 key（<code>memos:{{ '{noteId}' }}</code> / <code>kb:{{ '{chunkId}' }}</code>）。
        默认纯检索路径（不改写/不重排/不调 LLM，秒级）；勾选「完整管线」走改写/混合检索/重排/loop，
        与线上行为一致但每条用例会调 LLM（较慢）。调完分块/阈值/融合后重跑，对比通过率防回归（数据飞轮的核心护栏）。
      </NAlert>
      <NDataTable
        :columns="caseColumnsFinal"
        :data="cases"
        :loading="loading"
        :row-key="(row: RagEvalCase) => row.id"
        :pagination="casePagination"
        :scroll-x="900"
        remote
        :bordered="false"
      />
    </NCard>

    <NCard title="跑分结果" :bordered="false">
      <template #header-extra>
        <NSpace :size="8" align="center">
          最近批次：
          <NSelect
            :value="selectedRunId"
            :options="historyRuns.map((r) => ({ label: r, value: r }))"
            size="small"
            style="width: 200px"
            clearable
            @update:value="(v) => { selectedRunId = v as string | null; if (v) loadRunResults(v as string) }"
          />
        </NSpace>
      </template>
      <div v-if="runSummary" class="run-summary">
        <div class="summary-line">
          最新一次：<NTag size="small" type="info" :bordered="false">{{ runSummary.runId }}</NTag>
          <NTag size="small" :type="runSummary.mode === 'full' ? 'warning' : 'default'" :bordered="false">
            {{ runSummary.mode === 'full' ? '完整管线' : '纯检索' }}
          </NTag>
          <NTag size="small" type="success" :bordered="false">通过 {{ runSummary.passCount }}/{{ runSummary.total }}</NTag>
          <NTag size="small" type="warning" :bordered="false">平均召回 {{ (runSummary.recallAvg * 100).toFixed(1) }}%</NTag>
          <NTag size="small" type="error" :bordered="false">失败 {{ runSummary.failCount }}</NTag>
        </div>
      </div>
      <NDataTable
        :columns="runColumns"
        :data="historyResults"
        :loading="historyLoading"
        :row-key="(row: RagEvalResultRow) => row.id"
        :scroll-x="900"
        :bordered="false"
      />
    </NCard>

    <NCard title="不良反馈（bad case 复盘原料）" :bordered="false">
      <NAlert type="info" :bordered="false" style="margin-bottom: 12px">
        聊天里被点「没用」的回复（含当时的 RAG 引用）。复盘流向：👎 多的问题 → 补知识库/调阈值/加评估用例锁回归。
      </NAlert>
      <NDataTable
        :columns="fbColumns"
        :data="badCases"
        :loading="fbLoading"
        :row-key="(row: RagFeedbackVO) => row.id"
        :pagination="fbPagination"
        :scroll-x="900"
        remote
        :bordered="false"
      />
    </NCard>

    <NModal v-model:show="modalShow" :width="isMobile ? '92%' : 'var(--modal-md)'" preset="card" title="评估用例">
      <NForm label-placement="left" :label-width="90" :show-feedback="false">
        <NFormItem label="问题">
          <NInput v-model:value="form.query" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }"
            placeholder="与线上相同的提问方式，如：上次记的图床方案是什么？" />
        </NFormItem>
        <NFormItem label="知识库">
          <NSelect v-model:value="form.kbId" :options="kbOptions" clearable placeholder="不关联（仅备忘笔记源）" />
        </NFormItem>
        <NFormItem label="期望来源">
          <NInput v-model:value="form.expectedSources" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }"
            placeholder="每行一个来源 key：&#10;memos:12&#10;kb:3&#10;（备忘笔记 id 见备忘同步页，知识库 chunk id 见知识库页）" />
        </NFormItem>
        <NFormItem label="备注">
          <NInput v-model:value="form.note" placeholder="为什么期望这些来源 / 问题背景" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="modalShow = false">取消</NButton>
          <NButton type="primary" :loading="saving" @click="handleSaveCase">保存</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.rag-eval-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.summary-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
</style>