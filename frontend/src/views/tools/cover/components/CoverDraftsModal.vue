<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  NModal, NButton, NList, NListItem, NThing, NInput, NSpace, NEmpty,
  NPopconfirm, useMessage,
} from 'naive-ui'
import { useUIStore } from '../store/ui'
import { useCoverDraftsStore } from '../store/coverDrafts'
import { useCoverStore } from '../store/cover'
import { formatTime } from '@/utils/format'

const uiStore = useUIStore()
const draftsStore = useCoverDraftsStore()
const coverStore = useCoverStore()
const message = useMessage()

const show = computed({
  get: () => uiStore.modal === 'drafts',
  set: (v) => (v ? uiStore.openModal('drafts') : uiStore.closeModal()),
})

const name = ref('')

function handleSave() {
  const state = { ...coverStore.state, backgroundImage: '' }
  const finalName = name.value.trim() || `草稿 ${new Date().toLocaleString()}`
  draftsStore.saveDraft(finalName, state)
  name.value = ''
  message.success('已保存为草稿')
}

function handleLoad(id: string) {
  const d = draftsStore.drafts.find((x) => x.id === id)
  if (!d) return
  coverStore.update({ ...d.state, backgroundImage: '' })
  message.success('已加载草稿')
  uiStore.closeModal()
}

function handleRename(id: string, oldName: string) {
  const newName = window.prompt('重命名草稿', oldName)
  if (newName && newName.trim()) {
    draftsStore.renameDraft(id, newName.trim())
    message.success('已重命名')
  }
}


</script>

<template>
  <NModal
    v-model:show="show"
    preset="card"
    title="封面草稿"
    :style="{ width: 'var(--modal-md)', maxWidth: '90vw' }"
  >
    <template #footer>
      <NButton @click="show = false">关闭</NButton>
    </template>
    <div style="display: flex; gap: 8px; margin-bottom: 12px">
      <NInput
        v-model:value="name"
        placeholder="草稿名称"
        style="flex: 1"
        @keyup.enter="handleSave"
      />
      <NButton type="primary" @click="handleSave">保存当前为草稿</NButton>
    </div>
    <NList v-if="draftsStore.drafts.length" hoverable>
      <NListItem v-for="d in draftsStore.drafts" :key="d.id">
        <NThing>
          <template #header>{{ d.name }}</template>
          <template #description>{{ formatTime(d.savedAt) }}</template>
          <template #header-extra>
            <NSpace size="small">
              <NButton size="small" type="primary" @click="handleLoad(d.id)">加载</NButton>
              <NButton size="small" @click="handleRename(d.id, d.name)">重命名</NButton>
              <NPopconfirm @positive-click="draftsStore.deleteDraft(d.id)">
                <template #trigger>
                  <NButton size="small" type="error">删除</NButton>
                </template>
                删除该草稿？
              </NPopconfirm>
            </NSpace>
          </template>
        </NThing>
      </NListItem>
    </NList>
    <NEmpty v-else description="暂无草稿" />
  </NModal>
</template>
