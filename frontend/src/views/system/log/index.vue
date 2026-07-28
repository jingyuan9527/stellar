<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import {
  NCard, NForm, NFormItem, NInput, NSelect, NDatePicker, NButton, NSpace,
  NDataTable, NTag, NDrawer, NDrawerContent, NDescriptions, NDescriptionsItem,
  NIcon, useMessage,
} from 'naive-ui'
import type { DataTableColumns, SelectOption } from 'naive-ui'
import { getLogPage, getLogDetail, exportLogs } from '@/api/log'
import type { SysLog, SysLogQuery } from '@/types/api'
import { iconMap } from '@/utils/icons'
import { formatTime } from '@/utils/format'

const message = useMessage()

const query = reactive<SysLogQuery>({
  module: '',
  operator: '',
  status: null,
  startTime: null,
  endTime: null,
  pageNum: 1,
  pageSize: 10,
})

const timeRange = ref<[number, number] | null>(null)

const loading = ref(false)
const tableData = ref<SysLog[]>([])
const total = ref(0)

const statusOptions: SelectOption[] = [
  { label: '全部', value: null as unknown as number },
  { label: '成功', value: 1 },
  { label: '失败', value: 0 },
]

const opTypeText: Record<string, string> = {
  LOGIN: '登录', LOGOUT: '登出', INSERT: '新增', UPDATE: '修改',
  DELETE: '删除', QUERY: '查询', EXPORT: '导出', OTHER: '其他',
}

const opTypeColor: Record<string, string> = {
  LOGIN: 'success', LOGOUT: 'warning', INSERT: 'success', UPDATE: 'info',
  DELETE: 'error', QUERY: 'default', EXPORT: 'info', OTHER: 'default',
}



const columns: DataTableColumns<SysLog> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '模块', key: 'module', width: 120 },
  {
    title: '操作类型', key: 'operationType', width: 90,
    render: (row) => h(NTag, { type: (opTypeColor[row.operationType] || 'default') as never, size: 'small' },
      { default: () => opTypeText[row.operationType] || row.operationType }),
  },
  { title: '操作人', key: 'operator', width: 110 },
  { title: '请求方法', key: 'requestMethod', width: 90 },
  { title: '请求URL', key: 'requestUrl', ellipsis: { tooltip: true }, width: 200 },
  {
    title: '状态', key: 'status', width: 80,
    render: (row) => h(NTag, { type: row.status === 1 ? 'success' : 'error', size: 'small' },
      { default: () => (row.status === 1 ? '成功' : '失败') }),
  },
  { title: 'IP', key: 'ip', width: 130 },
  { title: '耗时(ms)', key: 'duration', width: 90 },
  { title: '操作时间', key: 'createTime', width: 170, render: (row) => formatTime(row.createTime) },
  {
    title: '操作', key: 'actions', width: 80, fixed: 'right',
    render: (row) => h(NButton, { size: 'small', text: true, onClick: () => viewDetail(row.id) },
      { icon: () => h(NIcon, null, { default: () => h(iconMap.eye) }), default: () => '详情' }),
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
    const res = await getLogPage(query)
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
  query.module = ''
  query.operator = ''
  query.status = null
  timeRange.value = null
  handleSearch()
}

const detailShow = ref(false)
const detailLoading = ref(false)
const detail = ref<SysLog | null>(null)

async function viewDetail(id: number) {
  detailShow.value = true
  detailLoading.value = true
  try {
    detail.value = await getLogDetail(id)
  } catch {
    // 错误已由拦截器提示
  } finally {
    detailLoading.value = false
  }
}

const exporting = ref(false)

async function handleExport() {
  exporting.value = true
  try {
    if (timeRange.value) {
      query.startTime = new Date(timeRange.value[0]).toISOString()
      query.endTime = new Date(timeRange.value[1]).toISOString()
    } else {
      query.startTime = null
      query.endTime = null
    }
    const blob = await exportLogs(query)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `操作日志_${new Date().toISOString().slice(0, 10)}.xlsx`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch {
    // 错误已由拦截器提示
  } finally {
    exporting.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <div class="log-page">
    <NCard :bordered="false" class="filter-card">
      <NForm label-placement="left" :show-feedback="false" inline>
        <NFormItem label="模块">
          <NInput v-model:value="query.module" placeholder="模块名称" clearable style="width: 150px" />
        </NFormItem>
        <NFormItem label="操作人">
          <NInput v-model:value="query.operator" placeholder="用户名" clearable style="width: 150px" />
        </NFormItem>
        <NFormItem label="状态">
          <NSelect v-model:value="query.status" :options="statusOptions" style="width: 110px" />
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
          <NButton type="primary" ghost :loading="exporting" @click="handleExport">
            <template #icon><NIcon><component :is="iconMap.download" /></NIcon></template>
            导出
          </NButton>
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
        :scroll-x="1200"
        remote
        striped
        size="small"
      />
    </NCard>

    <NDrawer v-model:show="detailShow" :width="520" placement="right">
      <NDrawerContent title="日志详情" :native-scrollbar="false" closable>
        <NDescriptions v-if="detail" :column="1" label-placement="left" bordered size="small">
          <NDescriptionsItem label="ID">{{ detail.id }}</NDescriptionsItem>
          <NDescriptionsItem label="模块">{{ detail.module }}</NDescriptionsItem>
          <NDescriptionsItem label="操作类型">{{ opTypeText[detail.operationType] || detail.operationType }}</NDescriptionsItem>
          <NDescriptionsItem label="操作人">{{ detail.operator }}</NDescriptionsItem>
          <NDescriptionsItem label="请求方法">{{ detail.requestMethod }}</NDescriptionsItem>
          <NDescriptionsItem label="请求URL">{{ detail.requestUrl }}</NDescriptionsItem>
          <NDescriptionsItem label="Java方法">{{ detail.javaMethod }}</NDescriptionsItem>
          <NDescriptionsItem label="状态">
            <NTag :type="detail.status === 1 ? 'success' : 'error'" size="small">
              {{ detail.status === 1 ? '成功' : '失败' }}
            </NTag>
          </NDescriptionsItem>
          <NDescriptionsItem label="IP">{{ detail.ip }}</NDescriptionsItem>
          <NDescriptionsItem label="耗时">{{ detail.duration }} ms</NDescriptionsItem>
          <NDescriptionsItem label="操作时间">{{ formatTime(detail.createTime) }}</NDescriptionsItem>
          <NDescriptionsItem label="请求参数">
            <pre class="log-pre">{{ detail.params }}</pre>
          </NDescriptionsItem>
          <NDescriptionsItem v-if="detail.errorMsg" label="异常信息">
            <pre class="log-pre">{{ detail.errorMsg }}</pre>
          </NDescriptionsItem>
        </NDescriptions>
        <NSpace v-else-if="detailLoading" justify="center" style="padding: 40px">
          <NButton loading text>加载中...</NButton>
        </NSpace>
      </NDrawerContent>
    </NDrawer>
  </div>
</template>

<style scoped>
.log-page {
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

.log-pre {
  margin: 0;
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  background: rgba(127, 127, 127, 0.08);
  padding: 8px;
  border-radius: 4px;
}
</style>
