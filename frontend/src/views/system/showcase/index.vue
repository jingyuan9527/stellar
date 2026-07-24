<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import {
  NCard, NSpace, NInput, NSelect, NInputNumber, NButton, NDataTable, NTag,
  NModal, NFormItem, NSwitch, NPopconfirm, NUpload, NImage, useMessage,
} from 'naive-ui'
import type { DataTableColumns, SelectOption, UploadCustomRequestOptions } from 'naive-ui'
import { getShowcasePage, createShowcase, updateShowcase, deleteShowcase } from '@/api/showcase'
import { uploadFile } from '@/api/file'
import type { Showcase, ShowcaseQuery } from '@/types/api'

const message = useMessage()

const query = reactive<ShowcaseQuery>({ type: '', title: '', pageNum: 1, pageSize: 10 })
const loading = ref(false)
const tableData = ref<Showcase[]>([])
const total = ref(0)

const typeOptions: SelectOption[] = [
  { value: 'cover', label: '封面' },
  { value: 'text', label: '文案' },
  { value: 'audio', label: '音频' },
  { value: 'demo', label: 'Demo' },
  { value: 'project', label: '项目' },
  { value: 'link', label: '链接' },
]
const typeLabel: Record<string, string> = Object.fromEntries(
  typeOptions.map((o) => [o.value as string, o.label as string]),
)

function formatTime(s?: string): string {
  return s ? s.replace('T', ' ').slice(0, 19) : ''
}

async function loadData() {
  loading.value = true
  try {
    const res = await getShowcasePage(query)
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

const columns: DataTableColumns<Showcase> = [
  { title: 'ID', key: 'id', width: 70 },
  {
    title: '类型', key: 'type', width: 80,
    render: (r) => h(NTag, { size: 'small', bordered: false }, { default: () => typeLabel[r.type] || r.type }),
  },
  { title: '标题', key: 'title', width: 160 },
  {
    title: '封面', key: 'coverUrl', width: 90,
    render: (r) =>
      r.coverUrl
        ? h(NImage, { src: r.coverUrl, width: 60, height: 40, objectFit: 'cover', previewDisabled: true })
        : '-',
  },
  { title: '排序', key: 'sortOrder', width: 70 },
  { title: '公开', key: 'visible', width: 70, render: (r) => (r.visible === 1 ? '是' : '否') },
  { title: '更新时间', key: 'updateTime', width: 160, render: (r) => formatTime(r.updateTime) },
  {
    title: '操作', key: 'actions', width: 140,
    render: (r) =>
      h(NSpace, { size: 'small' }, {
        default: () => [
          h(NButton, { size: 'small', onClick: () => startEdit(r) }, { default: () => '编辑' }),
          h(NPopconfirm, { onPositiveClick: () => handleDelete(r.id) }, {
            trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => '删除' }),
            default: () => '确认删除？',
          }),
        ],
      }),
  },
]

const editShow = ref(false)
const editing = ref<Partial<Showcase> | null>(null)

function startAdd() {
  editing.value = {
    type: 'cover', title: '', summary: '', coverUrl: '',
    content: '', mediaUrl: '', link: '', tags: '', sortOrder: 0, visible: 1,
  }
  editShow.value = true
}

function startEdit(r: Showcase) {
  editing.value = { ...r }
  editShow.value = true
}

async function customUpload({ file, onFinish, onError }: UploadCustomRequestOptions) {
  const f = file.file as File
  if (!f) {
    onError()
    return
  }
  try {
    const url = await uploadFile(f)
    editing.value!.coverUrl = url
    message.success('封面上传成功')
    onFinish()
  } catch {
    onError()
  }
}

async function handleSave() {
  if (!editing.value) return
  if (!editing.value.title?.trim()) {
    message.warning('标题不能为空')
    return
  }
  if (!editing.value.type) {
    message.warning('请选择类型')
    return
  }
  try {
    if (editing.value.id) {
      await updateShowcase(editing.value.id, editing.value)
    } else {
      await createShowcase(editing.value)
    }
    message.success('已保存')
    editShow.value = false
    loadData()
  } catch {
    // 错误已由拦截器提示
  }
}

async function handleDelete(id: number) {
  try {
    await deleteShowcase(id)
    message.success('已删除')
    loadData()
  } catch {
    // 错误已由拦截器提示
  }
}

onMounted(loadData)
</script>

<template>
  <div class="showcase-admin-page">
    <NCard title="作品橱窗管理" :bordered="false">
      <template #header-extra>
        <NButton type="primary" @click="startAdd">新增作品</NButton>
      </template>
      <NSpace align="center" :size="12" style="margin-bottom: 16px">
        <NSelect
          v-model:value="query.type"
          :options="typeOptions"
          placeholder="类型"
          clearable
          style="width: 140px"
          @update:value="handleSearch"
        />
        <NInput
          v-model:value="query.title"
          placeholder="标题"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
        <NButton @click="handleSearch">搜索</NButton>
      </NSpace>
      <NDataTable
        :columns="columns"
        :data="tableData"
        :loading="loading"
        :row-key="(row: Showcase) => row.id"
        :pagination="{
          page: query.pageNum,
          pageSize: query.pageSize,
          itemCount: total,
          showSizePicker: false,
          onChange: handlePageChange,
        }"
        :bordered="false"
      />
    </NCard>

    <NModal
      v-model:show="editShow"
      preset="card"
      :title="editing?.id ? '编辑作品' : '新增作品'"
      :style="{ width: '680px', maxWidth: '92vw' }"
      positive-text="保存"
      negative-text="取消"
      @positive-click="handleSave"
    >
      <NSpace v-if="editing" vertical :size="16">
        <NFormItem label="类型">
          <NSelect v-model:value="editing.type" :options="typeOptions" />
        </NFormItem>
        <NFormItem label="标题">
          <NInput v-model:value="editing.title" placeholder="作品标题" />
        </NFormItem>
        <NFormItem label="摘要">
          <NInput v-model:value="editing.summary" type="textarea" :autosize="{ minRows: 2 }" placeholder="一句话简介" />
        </NFormItem>
        <NFormItem label="封面图">
          <NSpace align="center" :size="12">
            <NUpload :custom-request="customUpload" :show-file-list="false" accept="image/*">
              <NButton>上传图片</NButton>
            </NUpload>
            <NImage
              v-if="editing.coverUrl"
              :src="editing.coverUrl"
              width="120"
              height="80"
              object-fit="cover"
            />
          </NSpace>
        </NFormItem>
        <NFormItem label="正文（支持HTML）">
          <NInput v-model:value="editing.content" type="textarea" :autosize="{ minRows: 4, maxRows: 12 }" />
        </NFormItem>
        <NFormItem label="媒体URL（音频/视频）">
          <NInput v-model:value="editing.mediaUrl" placeholder="/uploads/xxx.mp3 或外链" />
        </NFormItem>
        <NFormItem label="跳转链接">
          <NInput v-model:value="editing.link" placeholder="外链（link类型用）" />
        </NFormItem>
        <NFormItem label="标签（逗号分隔）">
          <NInput v-model:value="editing.tags" placeholder="vue,工具,实验" />
        </NFormItem>
        <NSpace :size="24">
          <NFormItem label="排序">
            <NInputNumber v-model:value="editing.sortOrder" :min="0" style="width: 120px" />
          </NFormItem>
          <NFormItem label="公开">
            <NSwitch :value="editing.visible === 1" @update:value="(v: boolean) => (editing!.visible = v ? 1 : 0)" />
          </NFormItem>
        </NSpace>
      </NSpace>
    </NModal>
  </div>
</template>

<style scoped>
.showcase-admin-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
