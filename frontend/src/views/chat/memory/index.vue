<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NCard, NSpace, NButton, NDataTable, NModal, NFormItem,
  NInput, NSelect, NPopconfirm, useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import {
  pageAllMemories, updateMemory, deleteMemory, summarizeSession,
  createMemory, pageAllChatSessions,
} from '@/api/chat'
import { getUserList } from '@/api/user'
import { formatTime } from '@/utils/format'
import type { AiMemory, AiChatSessionAdmin, SysUser } from '@/types/api'
import { useIsMobile } from '@/composables/useBreakpoint'

const message = useMessage()
const isMobile = useIsMobile()

// ===== 记忆列表 =====
const loading = ref(false)
const tableData = ref<AiMemory[]>([])
const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
  onChange: (page: number) => { pagination.page = page; loadData() },
  onUpdatePageSize: (size: number) => { pagination.pageSize = size; pagination.page = 1; loadData() },
})



const allColumns: DataTableColumns<AiMemory> = [
  { title: 'ID', key: 'id', width: 70 },
  {
    title: '内容', key: 'content', ellipsis: { tooltip: true },
    render: (row) => h('span', { style: 'font-size: 13px' }, row.content),
  },
  { title: '用户', key: 'username', width: 120, render: (row) => row.username || row.userId },
  {
    title: '来源会话', key: 'sourceSessionId', width: 110,
    render: (row) => row.sourceSessionId != null ? String(row.sourceSessionId) : '-',
  },
  { title: '创建时间', key: 'createTime', width: 160, render: (row) => formatTime(row.createTime) },
  {
    title: '操作', key: 'actions', width: 160, fixed: 'right',
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

const mobileHiddenKeys = new Set(['id', 'sourceSessionId'])
const columns = computed<DataTableColumns<AiMemory>>(() =>
  isMobile.value
    ? allColumns.filter((c) => !mobileHiddenKeys.has((c as { key?: string }).key ?? ''))
    : allColumns,
)

async function loadData() {
  loading.value = true
  try {
    const res = await pageAllMemories({ pageNum: pagination.page, pageSize: pagination.pageSize })
    tableData.value = res.records
    pagination.itemCount = res.total
  } catch {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

// ===== 编辑 =====
const editShow = ref(false)
const editing = ref<{ id: number; content: string } | null>(null)

function startEdit(row: AiMemory) {
  editing.value = { id: row.id, content: row.content }
  editShow.value = true
}

async function handleSave() {
  if (!editing.value) return
  if (!editing.value.content.trim()) {
    message.warning('内容不能为空')
    return false
  }
  try {
    await updateMemory(editing.value.id, editing.value.content)
    message.success('已保存')
    editShow.value = false
    loadData()
  } catch {
    // 错误已由拦截器提示
    return false
  }
}

async function handleDelete(id: number) {
  try {
    await deleteMemory(id)
    message.success('已删除')
    loadData()
  } catch {
    // 错误已由拦截器提示
  }
}

// ===== 新增记忆 =====
const addShow = ref(false)
const users = ref<SysUser[]>([])
const addUser = ref<number | null>(null)
const addContent = ref('')
const adding = ref(false)

const userOptions = () => users.value.map((u) => ({
  value: u.id,
  label: u.nickname ? `${u.username}（${u.nickname}）` : u.username,
}))

async function loadUsers() {
  try {
    users.value = await getUserList()
  } catch {
    // 错误已由拦截器提示
  }
}

function startAdd() {
  addUser.value = null
  addContent.value = ''
  addShow.value = true
  if (users.value.length === 0) loadUsers()
}

async function handleAdd() {
  if (!addUser.value) {
    message.warning('请选择用户')
    return false
  }
  if (!addContent.value.trim()) {
    message.warning('内容不能为空')
    return false
  }
  adding.value = true
  try {
    await createMemory({ userId: addUser.value, content: addContent.value })
    message.success('已新增')
    addShow.value = false
    loadData()
  } catch {
    // 错误已由拦截器提示
    return false
  } finally {
    adding.value = false
  }
}

// ===== 手动整理会话（会话列表选择）=====
const summarizeShow = ref(false)
const sessionLoading = ref(false)
const sessionList = ref<AiChatSessionAdmin[]>([])
const summarizingId = ref<number | null>(null)
const sessionPagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  onChange: (page: number) => { sessionPagination.page = page; loadSessions() },
})

const sessionColumns: DataTableColumns<AiChatSessionAdmin> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '标题', key: 'title', ellipsis: { tooltip: true } },
  { title: '用户', key: 'username', width: 120, render: (r) => r.username || r.subjectId },
  { title: '更新时间', key: 'updateTime', width: 160, render: (r) => formatTime(r.updateTime) },
  {
    title: '操作', key: 'actions', width: 100, fixed: 'right',
    render: (row) => h(NButton, {
      size: 'small',
      type: 'primary',
      loading: summarizingId.value === row.id,
      onClick: () => handleSummarize(row.id),
    }, { default: () => '整理' }),
  },
]

async function loadSessions() {
  sessionLoading.value = true
  try {
    const res = await pageAllChatSessions({ pageNum: sessionPagination.page, pageSize: sessionPagination.pageSize })
    sessionList.value = res.records
    sessionPagination.itemCount = res.total
  } catch {
    // 错误已由拦截器提示
  } finally {
    sessionLoading.value = false
  }
}

function openSummarize() {
  summarizeShow.value = true
  sessionPagination.page = 1
  loadSessions()
}

async function handleSummarize(sessionId: number) {
  summarizingId.value = sessionId
  try {
    const count = await summarizeSession(sessionId)
    message.success(`整理完成，新增 ${count} 条记忆`)
    loadData()
  } catch {
    // 错误已由拦截器提示
  } finally {
    summarizingId.value = null
  }
}

onMounted(() => {
  loadData()
  loadUsers()
})
</script>

<template>
  <div class="memory-page">
    <NCard title="长期记忆管理" :bordered="false">
      <template #header-extra>
        <NSpace>
          <NButton type="primary" @click="startAdd">新增记忆</NButton>
          <NButton @click="openSummarize">手动整理会话</NButton>
          <NButton @click="loadData">刷新</NButton>
        </NSpace>
      </template>
      <p class="hint">
        定时任务每日 3 点自动整理近 7 天会话为记忆。此处可手动新增/编辑/删除记忆，或选择会话整理为记忆。
      </p>
      <NDataTable
        :columns="columns"
        :data="tableData"
        :loading="loading"
        :row-key="(row: AiMemory) => row.id"
        :pagination="pagination"
        :scroll-x="900"
        remote
        :bordered="false"
      />
    </NCard>

    <NModal
      v-model:show="editShow"
      preset="card"
      title="编辑记忆"
      :style="{ width: '560px' }"
    >
      <NFormItem label="内容">
        <NInput
          v-if="editing"
          v-model:value="editing.content"
          type="textarea"
          :autosize="{ minRows: 4, maxRows: 10 }"
        />
      </NFormItem>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="editShow = false">取消</NButton>
          <NButton type="primary" @click="handleSave">保存</NButton>
        </NSpace>
      </template>
    </NModal>

    <NModal
      v-model:show="addShow"
      preset="card"
      title="新增长期记忆"
      :style="{ width: '520px' }"
    >
      <NSpace vertical :size="16">
        <NFormItem label="用户">
          <NSelect
            v-model:value="addUser"
            :options="userOptions()"
            placeholder="选择用户"
            filterable
          />
        </NFormItem>
        <NFormItem label="内容">
          <NInput
            v-model:value="addContent"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 10 }"
            placeholder="输入记忆内容（对话时注入该用户的 system prompt）"
          />
        </NFormItem>
      </NSpace>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="addShow = false">取消</NButton>
          <NButton type="primary" :loading="adding" @click="handleAdd">保存</NButton>
        </NSpace>
      </template>
    </NModal>

    <NModal
      v-model:show="summarizeShow"
      preset="card"
      title="选择会话整理为记忆"
      :style="{ width: '720px' }"
    >
      <p class="hint">选择会话点击「整理」，将调用 LLM 提取对话中的持久事实，存为该用户的长期记忆。</p>
      <NDataTable
        :columns="sessionColumns"
        :data="sessionList"
        :loading="sessionLoading"
        :row-key="(row: AiChatSessionAdmin) => row.id"
        :pagination="sessionPagination"
        :scroll-x="700"
        remote
        size="small"
        :bordered="false"
      />
    </NModal>
  </div>
</template>

<style scoped>
.memory-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.hint {
  font-size: 12px;
  opacity: 0.6;
  margin: 0 0 12px;
}
</style>
