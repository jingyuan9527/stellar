<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NCard, NSpace, NButton, NDataTable, NTag, NDrawer, NDrawerContent,
  NPopconfirm, NEmpty, useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { pageAllChatSessions, getChatMessagesAdmin, deleteChatSessionAdmin } from '@/api/chat'
import type { AiChatSessionAdmin, AiChatMessage } from '@/types/api'
import { formatTime } from '@/utils/format'
import { useIsMobile } from '@/composables/useBreakpoint'
import SkeletonList from '@/components/SkeletonList.vue'

const message = useMessage()
const isMobile = useIsMobile()
const drawerWidth = computed(() => (isMobile.value ? '100%' : 520))

const loading = ref(false)
const tableData = ref<AiChatSessionAdmin[]>([])
const total = ref(0)
const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
  onChange: (page: number) => { pagination.page = page; loadData() },
  onUpdatePageSize: (size: number) => { pagination.pageSize = size; pagination.page = 1; loadData() },
})



const allColumns: DataTableColumns<AiChatSessionAdmin> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '标题', key: 'title', ellipsis: { tooltip: true } },
  {
    title: '主体', key: 'subjectType', width: 140,
    render: (row) => h(NSpace, { size: 4, align: 'center' }, {
      default: () => [
        h(NTag, { size: 'small', bordered: false, type: row.subjectType === 'account' ? 'info' : 'warning' },
          { default: () => row.subjectType === 'account' ? '账号' : 'IP' }),
        h('span', { style: 'font-size: 12px; color: var(--c-text-3)' },
          row.username || row.subjectId),
      ],
    }),
  },
  { title: '创建时间', key: 'createTime', width: 160, render: (row) => formatTime(row.createTime) },
  { title: '更新时间', key: 'updateTime', width: 160, render: (row) => formatTime(row.updateTime) },
  {
    title: '操作', key: 'actions', width: 160, fixed: 'right',
    render: (row) => h(NSpace, { size: 'small' }, {
      default: () => [
        h(NButton, { size: 'small', onClick: () => viewMessages(row.id) }, { default: () => '查看消息' }),
        h(NPopconfirm, { onPositiveClick: () => handleDelete(row.id) }, {
          trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
          default: () => '删除该会话及其消息？',
        }),
      ],
    }),
  },
]

const mobileHiddenKeys = new Set(['id', 'updateTime'])
const columns = computed<DataTableColumns<AiChatSessionAdmin>>(() =>
  isMobile.value
    ? allColumns.filter((c) => !mobileHiddenKeys.has((c as { key?: string }).key ?? ''))
    : allColumns,
)

async function loadData() {
  loading.value = true
  try {
    const res = await pageAllChatSessions({ pageNum: pagination.page, pageSize: pagination.pageSize })
    tableData.value = res.records
    pagination.itemCount = res.total
  } catch {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

// ===== 消息详情抽屉 =====
const drawerShow = ref(false)
const drawerLoading = ref(false)
const drawerMessages = ref<AiChatMessage[]>([])

async function viewMessages(sessionId: number) {
  drawerShow.value = true
  drawerMessages.value = []
  drawerLoading.value = true
  try {
    drawerMessages.value = await getChatMessagesAdmin(sessionId)
  } catch {
    // 错误已由拦截器提示
  } finally {
    drawerLoading.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await deleteChatSessionAdmin(id)
    message.success('已删除')
    loadData()
  } catch {
    // 错误已由拦截器提示
  }
}

onMounted(loadData)
</script>

<template>
  <div class="history-page">
    <NCard title="历史聊天管理" :bordered="false">
      <NDataTable
        :columns="columns"
        :data="tableData"
        :loading="loading"
        :row-key="(row: AiChatSessionAdmin) => row.id"
        :pagination="pagination"
        :scroll-x="900"
        remote
        :bordered="false"
      />
    </NCard>

    <NDrawer v-model:show="drawerShow" :width="drawerWidth" placement="right">
      <NDrawerContent title="会话消息" closable>
        <div v-if="drawerLoading" style="padding: 24px"><SkeletonList :rows="5" height="60px" /></div>
        <div v-else class="msg-stream">
          <template v-for="m in drawerMessages" :key="m.id">
            <div v-if="m.role !== 'system'" class="msg-row" :class="m.role">
              <div class="msg-role">{{ m.role === 'user' ? '用户' : 'AI' }}</div>
              <div class="bubble">
                <img
                  v-if="m.attachmentType === 'image' && m.attachmentUrl"
                  :src="m.attachmentUrl"
                  class="msg-image"
                  loading="lazy"
                />
                <audio
                  v-else-if="m.attachmentType === 'audio' && m.attachmentUrl"
                  :src="m.attachmentUrl"
                  controls
                  class="msg-audio"
                />
                <span v-if="m.content" class="msg-text">{{ m.content }}</span>
                <div v-if="m.role === 'assistant' && m.refs && m.refs.length" class="msg-refs">
                  <div class="refs-label">参考</div>
                  <div class="refs-list">
                    <template v-for="(r, i) in m.refs" :key="r.source + ':' + r.sourceKey">
                      <a
                        v-if="r.url"
                        :href="r.url"
                        target="_blank"
                        rel="noopener"
                        class="ref-link"
                        :title="r.title ?? ''"
                      >{{ r.title || '来源' + (i + 1) }}</a>
                      <span v-else class="ref-text">{{ r.title || '来源' + (i + 1) }}</span>
                    </template>
                  </div>
                </div>
              </div>
            </div>
          </template>
          <NEmpty v-if="drawerMessages.length === 0" description="无消息" />
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
.msg-stream {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 0;
}
.msg-row {
  display: flex;
  flex-direction: column;
  max-width: 90%;
}
.msg-row.user {
  align-self: flex-end;
  align-items: flex-end;
}
.msg-row.assistant {
  align-self: flex-start;
}
.msg-role {
  font-size: 11px;
  color: var(--c-text-3);
  margin-bottom: 3px;
}
.bubble {
  padding: 8px 12px;
  border-radius: var(--r-md);
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
  font-size: 13px;
}
.msg-image {
  max-width: 100%;
  max-height: 300px;
  object-fit: contain;
  border-radius: var(--r-md);
  display: block;
  margin-bottom: 8px;
}
.msg-audio {
  width: 100%;
  max-width: 260px;
  display: block;
  margin-bottom: 8px;
}
.msg-row.user .bubble {
  background: var(--c-brand-bg);
}
.msg-row.assistant .bubble {
  background: var(--c-fill-2);
}
.msg-text {
  display: block;
}
.msg-refs {
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px dashed var(--c-border);
  font-size: 12px;
}
.refs-label {
  color: var(--c-text-3);
  margin-bottom: 4px;
}
.refs-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.ref-link,
.ref-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--c-info-bg);
  font-size: 12px;
}
.ref-link {
  color: var(--c-info);
}
.ref-link:hover {
  text-decoration: underline;
}
.ref-text {
  color: var(--c-text-3);
}
</style>
