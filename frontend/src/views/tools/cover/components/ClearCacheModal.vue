<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NModal, NButton, NCheckboxGroup, NCheckbox, NSpace, NEmpty, useMessage } from 'naive-ui'
import { useUIStore } from '../store/ui'
import { useCoverStore } from '../store/cover'
import { useCoverDraftsStore } from '../store/coverDrafts'

const uiStore = useUIStore()
const coverStore = useCoverStore()
const draftsStore = useCoverDraftsStore()
const message = useMessage()

const show = computed({
  get: () => uiStore.modal === 'clear',
  set: (v) => (v ? uiStore.openModal('clear') : uiStore.closeModal()),
})

const options = [
  { label: '封面编辑状态', value: 'cover' },
  { label: '封面草稿', value: 'drafts' },
]

const ALL = options.map((o) => o.value)
const checked = ref<string[]>([...ALL])

watch(show, (v) => {
  if (v) checked.value = [...ALL]
})

function handleOk() {
  if (checked.value.includes('cover')) coverStore.reset()
  if (checked.value.includes('drafts')) draftsStore.clear()
  message.success('已清空选中数据')
  show.value = false
}
</script>

<template>
  <NModal
    v-model:show="show"
    preset="card"
    title="清空缓存"
    :style="{ width: '440px', maxWidth: '90vw' }"
  >
    <p style="margin-bottom: 12px">选择要清空的数据（默认全选）：</p>
    <NCheckboxGroup v-model:value="checked">
      <NSpace vertical>
        <NCheckbox
          v-for="o in options"
          :key="o.value"
          :value="o.value"
          :label="o.label"
        />
      </NSpace>
    </NCheckboxGroup>
    <NEmpty v-if="ALL.length === 0" description="无可清空数据" />
    <template #footer>
      <NSpace justify="end">
        <NButton @click="show = false">取消</NButton>
        <NButton type="error" @click="handleOk">清空</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
