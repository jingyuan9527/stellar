<script setup lang="ts">
import { computed, ref } from 'vue'
import { NButton, NSpace, NSelect, NTag, NAlert, useMessage } from 'naive-ui'
import JsonNode from './JsonNode.vue'

const message = useMessage()
const fileInputRef = ref<HTMLInputElement | null>(null)

const source = ref('')
const parsed = ref<{ tree: unknown; raw: string } | null>(null)
const error = ref('')
const expandSignal = ref(0)
const collapseSignal = ref(0)
const viewMode = ref<'tree' | 'pretty'>('tree')
const indent = ref(2)

const EXAMPLE = `{
  "app": "Stellar",
  "version": "1.0.0",
  "features": [
    { "name": "json-format", "enabled": true, "rating": 9.5 },
    { "name": "diff", "enabled": false, "rating": null }
  ],
  "meta": {
    "author": { "name": "admin", "tags": ["dev", "ops"] },
    "keywords": ["json", "tool", 42, true, null]
  }
}`

function parse(text: string) {
  const trimmed = text.trim()
  if (!trimmed) {
    parsed.value = null
    error.value = ''
    return
  }
  try {
    parsed.value = { tree: JSON.parse(trimmed), raw: JSON.stringify(JSON.parse(trimmed), null, indent.value) }
    error.value = ''
  } catch (e) {
    parsed.value = null
    error.value = e instanceof Error ? e.message : String(e)
  }
}

function onInput() {
  parse(source.value)
}

function format() {
  if (!parsed.value) {
    parse(source.value)
    if (error.value) {
      message.error('JSON 无效，无法格式化')
      return
    }
  }
  source.value = JSON.stringify(JSON.parse(source.value), null, indent.value)
  parse(source.value)
}

function minify() {
  if (!source.value.trim()) return
  try {
    source.value = JSON.stringify(JSON.parse(source.value))
    parse(source.value)
  } catch (e) {
    message.error('JSON 无效，无法压缩')
  }
}

function expandAll() {
  expandSignal.value++
}
function collapseAll() {
  collapseSignal.value++
}

async function copyFormatted() {
  if (!parsed.value) {
    message.warning('请先输入有效 JSON')
    return
  }
  const text = viewMode.value === 'tree' ? parsed.value.raw : JSON.stringify(JSON.parse(source.value), null, indent.value)
  try {
    await navigator.clipboard.writeText(text)
    message.success('已复制格式化结果')
  } catch {
    message.error('复制失败')
  }
}

function download() {
  if (!parsed.value && !source.value.trim()) {
    message.warning('无内容可下载')
    return
  }
  const text = parsed.value ? parsed.value.raw : source.value
  const blob = new Blob([text], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'data.json'
  a.click()
  URL.revokeObjectURL(url)
}

function clearAll() {
  source.value = ''
  parsed.value = null
  error.value = ''
}

function loadExample() {
  source.value = EXAMPLE
  parse(source.value)
}

function onUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    source.value = String(reader.result ?? '')
    parse(source.value)
    if (error.value) message.error(`解析失败：${error.value}`)
    else message.success(`已加载 ${file.name}`)
  }
  reader.readAsText(file)
  input.value = ''
}

const fileSize = computed(() => new Blob([source.value]).size)
const prettyText = computed(() => {
  if (!parsed.value) return ''
  return JSON.stringify(JSON.parse(source.value), null, indent.value)
})
</script>

<template>
  <div class="json-page">
    <div class="toolbar">
      <NSpace size="small" wrap>
        <NButton size="small" type="primary" @click="format">格式化</NButton>
        <NButton size="small" @click="minify">压缩</NButton>
        <NButton size="small" @click="expandAll">展开全部</NButton>
        <NButton size="small" @click="collapseAll">折叠全部</NButton>
        <NButton size="small" @click="loadExample">示例</NButton>
        <input
          ref="fileInputRef"
          id="json-file-input"
          type="file"
          accept=".json,application/json,text/plain"
          style="display: none"
          @change="onUpload"
        />
        <NButton size="small" @click="fileInputRef?.click()">上传文件</NButton>
        <NButton size="small" @click="copyFormatted">复制结果</NButton>
        <NButton size="small" @click="download">下载</NButton>
        <NButton size="small" type="error" quaternary @click="clearAll">清空</NButton>
      </NSpace>
      <NSpace size="small" align="center">
        <NSelect
          v-model:value="viewMode"
          size="small"
          style="width: 132px"
          :options="[
            { label: '树形视图', value: 'tree' },
            { label: '格式化文本', value: 'pretty' },
          ]"
        />
        <NSelect
          v-model:value="indent"
          size="small"
          style="width: 102px"
          :options="[
            { label: '2 空格', value: 2 },
            { label: '4 空格', value: 4 },
          ]"
          @update:value="format"
        />
        <NTag v-if="parsed" size="small" :bordered="false" type="success">{{ fileSize }} B</NTag>
        <NTag v-if="parsed" size="small" :bordered="false" type="info">
          {{ Array.isArray(parsed.tree) ? 'Array' : 'Object' }}
        </NTag>
      </NSpace>
    </div>

    <NAlert v-if="error" type="error" :show-icon="false" closable class="err-bar" @close="error = ''">
      <span class="mono">{{ error }}</span>
    </NAlert>

    <div class="split">
      <div class="pane input-pane">
        <div class="pane-title">原文编辑</div>
        <textarea
          v-model="source"
          class="editor"
          spellcheck="false"
          placeholder="在此粘贴或输入 JSON，左侧栏实时解析展示树形结构…"
          @input="onInput"
        />
      </div>

      <div class="pane output-pane">
        <div class="pane-title">解析结果</div>
        <div v-if="error" class="empty">
          <span class="mono err">JSON 解析失败，请检查语法</span>
        </div>
        <div v-else-if="!parsed" class="empty">
          输入合法 JSON 后，此处展示可折叠的树形结构
        </div>
        <div v-else-if="viewMode === 'tree'" class="tree-wrap">
          <JsonNode
            :value="parsed.tree"
            name=""
            :path="'$'"
            :depth="0"
            :expand-signal="expandSignal"
            :collapse-signal="collapseSignal"
          />
        </div>
        <pre v-else class="pretty">{{ prettyText }}</pre>
      </div>
    </div>
  </div>
</template>

<style scoped>
.json-page {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: calc(100vh - 128px);
  min-height: 400px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.err-bar {
  flex-shrink: 0;
}

.mono {
  font-family: 'JetBrains Mono', Consolas, 'Courier New', monospace;
}
.mono.err {
  color: #e88080;
}

.split {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  min-height: 0;
}

.pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  overflow: hidden;
  background-color: var(--c-fill-2);
}

.pane-title {
  flex-shrink: 0;
  padding: 6px 12px;
  font-size: 12px;
  color: var(--c-text-3);
  background-color: var(--c-fill-2);
  border-bottom: 1px solid var(--c-border);
}

.editor {
  flex: 1;
  min-height: 0;
  resize: none;
  border: none;
  outline: none;
  padding: 12px;
  font-family: 'JetBrains Mono', Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.7;
  background: transparent;
  color: var(--n-text-color, inherit);
  white-space: pre;
  overflow: auto;
  tab-size: 2;
}

.output-pane {
  position: relative;
}

.tree-wrap {
  flex: 1;
  overflow: auto;
  padding: 8px 4px;
  min-height: 0;
}

.pretty {
  flex: 1;
  margin: 0;
  padding: 12px;
  overflow: auto;
  font-family: 'JetBrains Mono', Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre;
  color: var(--n-text-color, inherit);
}

.empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-3);
  font-size: 13px;
  padding: 24px;
  text-align: center;
}

@media (max-width: 768px) {
  .json-page {
    height: auto;
    min-height: 0;
  }
  .split {
    grid-template-columns: 1fr;
    grid-template-rows: 320px 420px;
  }
}
</style>
