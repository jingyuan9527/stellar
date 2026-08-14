<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NCard, NSpace, NButton, NDataTable, NTag, NModal, NFormItem,
  NInput, NSelect, NUpload, NPopconfirm, NAlert, useMessage,
} from 'naive-ui'
import type { DataTableColumns, UploadCustomRequestOptions } from 'naive-ui'
import {
  listKnowledgeBases, createKnowledgeBase, updateKnowledgeBase, deleteKnowledgeBase,
  pageKnowledgeChunks, addKnowledgeDocument, uploadKnowledgeDocument,
  deleteKnowledgeChunk, rebuildKnowledgeBase,
} from '@/api/chat'
import { formatTime } from '@/utils/format'
import { getAiModelsByType } from '@/api/ai'
import type { AiKnowledgeBase, AiKnowledgeChunk, AiModel } from '@/types/api'
import { useIsMobile } from '@/composables/useBreakpoint'

const message = useMessage()
const isMobile = useIsMobile()

// ===== 知识库列表 =====
const kbLoading = ref(false)
const knowledgeBases = ref<AiKnowledgeBase[]>([])
const selectedKbId = ref<number | null>(null)
const selectedKb = ref<AiKnowledgeBase | null>(null)

// 向量化模型
const embeddingModels = ref<AiModel[]>([])
const embeddingOptions = () => embeddingModels.value.map((m) => ({
  value: m.id,
  label: m.providerName ? `${m.model} (${m.providerName})` : m.model,
}))



const allKbColumns: DataTableColumns<AiKnowledgeBase> = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '名称', key: 'name', width: 160 },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  {
    title: '分块数', key: 'chunkCount', width: 90,
    render: (row) => h(NTag, { size: 'small', bordered: false }, { default: () => row.chunkCount }),
  },
  { title: '更新时间', key: 'updateTime', width: 160, render: (row) => formatTime(row.updateTime) },
  {
    title: '操作', key: 'actions', width: 280, fixed: 'right',
    render: (row) => h(NSpace, { size: 'small' }, {
      default: () => [
        h(NButton, {
          size: 'small',
          type: selectedKbId.value === row.id ? 'primary' : 'default',
          onClick: () => selectKb(row),
        }, { default: () => '查看分块' }),
        h(NButton, { size: 'small', onClick: () => startEditKb(row) }, { default: () => '编辑' }),
        h(NPopconfirm, { onPositiveClick: () => handleRebuild(row.id) }, {
          trigger: () => h(NButton, { size: 'small' }, { default: () => '重建索引' }),
          default: () => '重新向量化全部分块？',
        }),
        h(NPopconfirm, { onPositiveClick: () => handleDeleteKb(row.id) }, {
          trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
          default: () => '删除知识库及全部分块？',
        }),
      ],
    }),
  },
]

const mobileHiddenKbKeys = new Set(['id', 'updateTime'])
const kbColumns = computed<DataTableColumns<AiKnowledgeBase>>(() =>
  isMobile.value
    ? allKbColumns.filter((c) => !mobileHiddenKbKeys.has((c as { key?: string }).key ?? ''))
    : allKbColumns,
)

async function loadKbs() {
  kbLoading.value = true
  try {
    knowledgeBases.value = await listKnowledgeBases()
    if (selectedKbId.value) {
      selectedKb.value = knowledgeBases.value.find((k) => k.id === selectedKbId.value) || null
    }
  } catch {
    // 错误已由拦截器提示
  } finally {
    kbLoading.value = false
  }
}

async function loadEmbeddingModels() {
  try {
    embeddingModels.value = await getAiModelsByType('EMBEDDING')
  } catch {
    // 错误已由拦截器提示
  }
}

function selectKb(row: AiKnowledgeBase) {
  selectedKbId.value = row.id
  selectedKb.value = row
  chunkPagination.page = 1
  loadChunks()
}

async function handleRebuild(id: number) {
  try {
    await rebuildKnowledgeBase(id)
    message.success('索引重建完成')
    await loadKbs()
  } catch {
    // 错误已由拦截器提示
  }
}

async function handleDeleteKb(id: number) {
  try {
    await deleteKnowledgeBase(id)
    message.success('已删除')
    if (selectedKbId.value === id) {
      selectedKbId.value = null
      selectedKb.value = null
      chunks.value = []
    }
    loadKbs()
  } catch {
    // 错误已由拦截器提示
  }
}

// ===== KB 编辑 =====
const kbEditShow = ref(false)
const kbEditing = ref<{
  id: number | null; name: string; description: string; embeddingModelId: number | null
} | null>(null)

function startAddKb() {
  kbEditing.value = {
    id: null, name: '', description: '',
    embeddingModelId: embeddingModels.value.find((m) => m.isDefault === 1)?.id || null,
  }
  kbEditShow.value = true
}

function startEditKb(row: AiKnowledgeBase) {
  kbEditing.value = {
    id: row.id, name: row.name,
    description: row.description || '',
    embeddingModelId: row.embeddingModelId,
  }
  kbEditShow.value = true
}

async function handleSaveKb() {
  if (!kbEditing.value) return
  if (!kbEditing.value.name.trim()) {
    message.warning('名称不能为空')
    return false
  }
  try {
    if (kbEditing.value.id) {
      await updateKnowledgeBase({
        id: kbEditing.value.id,
        name: kbEditing.value.name,
        description: kbEditing.value.description,
        embeddingModelId: kbEditing.value.embeddingModelId,
      })
    } else {
      await createKnowledgeBase({
        name: kbEditing.value.name,
        description: kbEditing.value.description,
        embeddingModelId: kbEditing.value.embeddingModelId,
      })
    }
    message.success('已保存')
    kbEditShow.value = false
    loadKbs()
  } catch {
    // 错误已由拦截器提示
    return false
  }
}

// ===== 分块 =====
const chunkLoading = ref(false)
const chunks = ref<AiKnowledgeChunk[]>([])
const chunkTotal = ref(0)
const chunkPagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  onChange: (page: number) => { chunkPagination.page = page; loadChunks() },
})

const allChunkColumns: DataTableColumns<AiKnowledgeChunk> = [
  { title: '序号', key: 'chunkIndex', width: 70 },
  { title: '内容', key: 'chunkText', ellipsis: { tooltip: true } },
  { title: '来源', key: 'sourceName', width: 140, render: (row) => row.sourceName || '-' },
  { title: 'Token', key: 'tokenCount', width: 80 },
  {
    title: '操作', key: 'actions', width: 90, fixed: 'right',
    render: (row) => h(NPopconfirm, { onPositiveClick: () => handleDeleteChunk(row.id) }, {
      trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
      default: () => '删除该分块？',
    }),
  },
]

const mobileHiddenChunkKeys = new Set(['tokenCount'])
const chunkColumns = computed<DataTableColumns<AiKnowledgeChunk>>(() =>
  isMobile.value
    ? allChunkColumns.filter((c) => !mobileHiddenChunkKeys.has((c as { key?: string }).key ?? ''))
    : allChunkColumns,
)

async function loadChunks() {
  if (!selectedKbId.value) return
  chunkLoading.value = true
  try {
    const res = await pageKnowledgeChunks(selectedKbId.value, {
      pageNum: chunkPagination.page,
      pageSize: chunkPagination.pageSize,
    })
    chunks.value = res.records
    chunkPagination.itemCount = res.total
  } catch {
    // 错误已由拦截器提示
  } finally {
    chunkLoading.value = false
  }
}

async function handleDeleteChunk(id: number) {
  try {
    await deleteKnowledgeChunk(id)
    message.success('已删除')
    loadChunks()
    loadKbs()
  } catch {
    // 错误已由拦截器提示
  }
}

// ===== 添加文档 =====
const docShow = ref(false)
const docText = ref('')
const docSource = ref('')
const docAdding = ref(false)

async function handleAddDoc() {
  if (!selectedKbId.value) return
  if (!docText.value.trim()) {
    message.warning('文档内容不能为空')
    return false
  }
  docAdding.value = true
  try {
    const count = await addKnowledgeDocument(selectedKbId.value, {
      text: docText.value,
      sourceName: docSource.value || undefined,
    })
    message.success(`已分块 ${count} 条`)
    docShow.value = false
    docText.value = ''
    docSource.value = ''
    loadChunks()
    loadKbs()
  } catch {
    // 错误已由拦截器提示
    return false
  } finally {
    docAdding.value = false
  }
}

async function handleUpload({ file }: UploadCustomRequestOptions) {
  if (!selectedKbId.value || !file.file) return
  try {
    const count = await uploadKnowledgeDocument(selectedKbId.value, file.file as File)
    message.success(`已分块 ${count} 条`)
    loadChunks()
    loadKbs()
  } catch {
    // 错误已由拦截器提示
  }
}

onMounted(() => {
  loadEmbeddingModels()
  loadKbs()
})
</script>

<template>
  <div class="knowledge-page">
    <NCard title="知识库管理" :bordered="false">
      <template #header-extra>
        <NButton type="primary" @click="startAddKb">新增知识库</NButton>
      </template>
      <NAlert v-if="embeddingModels.length === 0" type="warning" :bordered="false" style="margin-bottom: 12px">
        未配置 EMBEDDING 类型模型，请在 AI创作 → 管理 → AI 配置 中添加并向量化。无配置时文档可分块入库但无法语义检索。
      </NAlert>
      <NDataTable
        :columns="kbColumns"
        :data="knowledgeBases"
        :loading="kbLoading"
        :row-key="(row: AiKnowledgeBase) => row.id"
        :pagination="false"
        :scroll-x="1100"
        :bordered="false"
      />
    </NCard>

    <NCard v-if="selectedKb" :title="`分块管理：${selectedKb.name}`" :bordered="false">
      <template #header-extra>
        <NSpace>
          <NButton @click="docShow = true">添加文档</NButton>
          <NUpload
            :show-file-list="false"
            :custom-request="handleUpload"
            accept=".txt,.md,.markdown"
          >
            <NButton>上传 txt/md</NButton>
          </NUpload>
        </NSpace>
      </template>
      <NDataTable
        :columns="chunkColumns"
        :data="chunks"
        :loading="chunkLoading"
        :row-key="(row: AiKnowledgeChunk) => row.id"
        :pagination="chunkPagination"
        :scroll-x="700"
        remote
        :bordered="false"
      />
    </NCard>

    <NModal
      v-model:show="kbEditShow"
      preset="card"
      :title="kbEditing?.id ? '编辑知识库' : '新增知识库'"
      :style="{ width: '560px', maxWidth: '90vw' }"
    >
      <NSpace v-if="kbEditing" vertical :size="16">
        <NFormItem label="名称">
          <NInput v-model:value="kbEditing.name" placeholder="知识库名称" />
        </NFormItem>
        <NFormItem label="描述">
          <NInput v-model:value="kbEditing.description" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" />
        </NFormItem>
        <NFormItem label="向量化模型">
          <NSelect
            v-model:value="kbEditing.embeddingModelId"
            :options="embeddingOptions()"
            placeholder="默认用 EMBEDDING 类型默认模型"
            clearable
          />
        </NFormItem>
      </NSpace>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="kbEditShow = false">取消</NButton>
          <NButton type="primary" @click="handleSaveKb">保存</NButton>
        </NSpace>
      </template>
    </NModal>

    <NModal
      v-model:show="docShow"
      preset="card"
      title="添加文档"
      :style="{ width: '640px', maxWidth: '90vw' }"
    >
      <NSpace vertical :size="16">
        <NFormItem label="来源名称">
          <NInput v-model:value="docSource" placeholder="可选，如 文档名/章节" />
        </NFormItem>
        <NFormItem label="文档内容">
          <NInput
            v-model:value="docText"
            type="textarea"
            :autosize="{ minRows: 10, maxRows: 20 }"
            placeholder="粘贴纯文本，将按 500 字 + 50 重叠分块并向量化"
          />
        </NFormItem>
      </NSpace>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="docShow = false">取消</NButton>
          <NButton type="primary" :loading="docAdding" @click="handleAddDoc">分块入库</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.knowledge-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
