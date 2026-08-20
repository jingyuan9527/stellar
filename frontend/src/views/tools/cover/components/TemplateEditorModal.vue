<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  NModal, NButton, NList, NListItem, NThing, NTag, NSpace,
  NInput, NSelect, NPopconfirm, useMessage,
} from 'naive-ui'
import { useUIStore } from '../store/ui'
import { useTemplateStore } from '../store/template'
import type { Platform, PromptTemplate } from '../types'

const uiStore = useUIStore()
const templateStore = useTemplateStore()
const message = useMessage()

const show = computed({
  get: () => uiStore.modal === 'template',
  set: (v) => (v ? uiStore.openModal('template') : uiStore.closeModal()),
})

const PLATFORM_LABELS: Record<Platform, string> = {
  bilibili: 'B站',
  douyin: '抖音',
  xiaohongshu: '小红书',
  custom: '自定义',
}

const platformOptions = Object.entries(PLATFORM_LABELS).map(([v, l]) => ({ value: v, label: l }))

const editing = ref<{ id: string | null; name: string; platform: Platform; prompt: string } | null>(null)
const formShow = ref(false)

watch(show, (v) => {
  if (!v) {
    editing.value = null
    formShow.value = false
  }
})

function startAdd() {
  editing.value = { id: null, name: '', platform: 'custom', prompt: '' }
  formShow.value = true
}

function startEdit(t: PromptTemplate) {
  editing.value = { id: t.id, name: t.name, platform: t.platform, prompt: t.prompt }
  formShow.value = true
}

function save() {
  if (!editing.value) return false
  if (!editing.value.name.trim() || !editing.value.prompt.trim()) {
    message.warning('名称和提示词不能为空')
    return false
  }
  if (editing.value.id) {
    templateStore.updateTemplate(editing.value.id, {
      name: editing.value.name,
      platform: editing.value.platform,
      prompt: editing.value.prompt,
    })
  } else {
    templateStore.addTemplate({
      name: editing.value.name,
      platform: editing.value.platform,
      prompt: editing.value.prompt,
    })
  }
  message.success('已保存')
  formShow.value = false
  editing.value = null
  return true
}

function handleDelete(id: string) {
  templateStore.deleteTemplate(id)
  message.success('已删除')
}

function handleReset(id: string) {
  templateStore.resetBuiltin(id)
  message.success('已恢复默认')
}
</script>

<template>
  <NModal
    v-model:show="show"
    preset="card"
    title="模板编辑"
    :style="{ width: 'var(--modal-lg)', maxWidth: '90vw' }"
  >
    <template #footer>
      <NButton @click="show = false">关闭</NButton>
    </template>
    <div style="margin-bottom: 12px">
      <NButton type="primary" @click="startAdd">新增模板</NButton>
    </div>
    <NList hoverable>
      <NListItem v-for="t in templateStore.templates" :key="t.id">
        <NThing>
          <template #header>
            <NSpace size="small" align="center">
              <span>{{ t.name }}</span>
              <NTag size="small" :bordered="false">{{ PLATFORM_LABELS[t.platform] }}</NTag>
              <NTag v-if="t.builtIn" size="small" type="info" :bordered="false">内置</NTag>
            </NSpace>
          </template>
          <template #description>
            <span class="prompt-preview">{{ t.prompt }}</span>
          </template>
          <template #header-extra>
            <NSpace size="small">
              <NButton size="small" @click="startEdit(t)">编辑</NButton>
              <NPopconfirm v-if="t.builtIn" @positive-click="handleReset(t.id)">
                <template #trigger>
                  <NButton size="small">恢复默认</NButton>
                </template>
                恢复为默认？
              </NPopconfirm>
              <NPopconfirm v-else @positive-click="handleDelete(t.id)">
                <template #trigger>
                  <NButton size="small" type="error">删除</NButton>
                </template>
                确认删除？
              </NPopconfirm>
            </NSpace>
          </template>
        </NThing>
      </NListItem>
    </NList>
  </NModal>

  <NModal
    v-model:show="formShow"
    preset="card"
    :title="editing?.id ? '编辑模板' : '新增模板'"
    :style="{ width: 'var(--modal-lg)', maxWidth: '90vw' }"
  >
    <NSpace v-if="editing" vertical :size="16">
      <div>
        <div class="field-label">名称</div>
        <NInput v-model:value="editing.name" placeholder="模板名称" />
      </div>
      <div>
        <div class="field-label">平台</div>
        <NSelect v-model:value="editing.platform" :options="platformOptions" />
      </div>
      <div>
        <div class="field-label" v-pre>提示词（用 {{topic}} 作为主题占位符）</div>
        <NInput
          v-model:value="editing.prompt"
          type="textarea"
          :autosize="{ minRows: 8, maxRows: 16 }"
          placeholder="输入提示词"
        />
      </div>
    </NSpace>
    <template #footer>
      <NSpace justify="end">
        <NButton @click="formShow = false">取消</NButton>
        <NButton type="primary" @click="save">保存</NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped>
.prompt-preview {
  display: block;
  max-height: 60px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  opacity: 0.6;
}

.field-label {
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 500;
}
</style>
