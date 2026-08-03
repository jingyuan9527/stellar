<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { CopyOutline, DocumentTextOutline, KeyOutline, ChevronDownOutline, ChevronForwardOutline, CheckmarkOutline } from '@vicons/ionicons5'
import { useThemeStore } from '@/store/theme'

const props = defineProps<{
  value: unknown
  name: string
  path: string
  depth: number
  expandSignal: number
  collapseSignal: number
}>()

const themeStore = useThemeStore()

const isContainer = computed(() => {
  const v = props.value
  return v !== null && typeof v === 'object'
})

const isArray = computed(() => Array.isArray(props.value))

const collapsed = ref(props.depth >= 2)

watch(
  () => props.expandSignal,
  () => {
    if (!isContainer.value) return
    collapsed.value = props.depth >= 2
  },
)
watch(
  () => props.collapseSignal,
  () => {
    collapsed.value = true
  },
)

const entries = computed(() => {
  const v = props.value as Record<string | number, unknown>
  if (Array.isArray(v)) return v.map((item, i) => ({ key: String(i), value: item }))
  return Object.entries(v).map(([k, val]) => ({ key: k, value: val }))
})

const sizeLabel = computed(() => {
  const v = props.value as { length?: number } | Record<string, unknown>
  if (Array.isArray(v)) return `[${v.length}]`
  return `{${Object.keys(v).length}}`
})

const typeOf = computed(() => {
  const v = props.value
  if (v === null) return 'null'
  return Array.isArray(v) ? 'array' : typeof v
})

const primitiveText = computed(() => {
  const v = props.value
  if (v === null) return 'null'
  if (typeof v === 'string') return `"${v}"`
  return String(v)
})

const truncated = computed(() => {
  const t = primitiveText.value
  return t.length > 200 ? `${t.slice(0, 200)}…` : t
})

const typeClass = computed(
  () =>
    (
      {
        string: 'v-str',
        number: 'v-num',
        boolean: 'v-bool',
        null: 'v-null',
      } as Record<string, string>
    )[typeOf.value] ?? 'v-other',
)

const copyAction = ref<'value' | 'pair' | 'key' | ''>('')
let copyTimer: ReturnType<typeof setTimeout> | null = null

function flash(kind: 'value' | 'pair' | 'key') {
  copyAction.value = kind
  if (copyTimer) clearTimeout(copyTimer)
  copyTimer = setTimeout(() => (copyAction.value = ''), 1200)
}

async function doCopy(text: string, kind: 'value' | 'pair' | 'key') {
  try {
    await navigator.clipboard.writeText(text)
    flash(kind)
  } catch {
    window.$message?.error('复制失败')
  }
}

function copyValue() {
  const v = props.value
  doCopy(typeof v === 'string' ? v : JSON.stringify(v, null, 2), 'value')
}

function copyPair() {
  const key = JSON.stringify(String(props.name))
  const val = JSON.stringify(props.value)
  doCopy(`${key}:${val}`, 'pair')
}

function copyKey() {
  doCopy(String(props.name), 'key')
}

function toggle() {
  if (isContainer.value) collapsed.value = !collapsed.value
}

onBeforeUnmount(() => {
  if (copyTimer) clearTimeout(copyTimer)
})

const palette = computed(() =>
  themeStore.darkMode
    ? {
        '--json-key': '#79c0ff',
        '--json-array-key': '#8b949e',
        '--json-string': '#7ee787',
        '--json-number': '#ffa657',
        '--json-boolean': '#d2a8ff',
        '--json-null': '#8b949e',
        '--json-other': '#79c0ff',
        '--json-punct': '#8b949e',
        '--json-row-hover': 'rgba(121,192,255,0.12)',
        '--json-value-copy': '#2f81f7',
      }
    : {
        '--json-key': '#8250df',
        '--json-array-key': '#6e7781',
        '--json-string': '#1a7f37',
        '--json-number': '#b35900',
        '--json-boolean': '#cf222e',
        '--json-null': '#6e7781',
        '--json-other': '#0550ae',
        '--json-punct': '#57606a',
        '--json-row-hover': 'rgba(9,105,218,0.12)',
        '--json-value-copy': '#0969da',
      },
)
</script>

<template>
  <div class="json-node" :style="palette">
    <div class="row" :class="{ container: isContainer, 'no-toggle': !isContainer }" :title="path">
      <span v-if="isContainer" class="toggle" @click.stop="toggle">
        <ChevronDownOutline v-if="!collapsed" class="arrow" :size="14" />
        <ChevronForwardOutline v-else class="arrow" :size="14" />
      </span>
      <span v-if="name" class="key" :class="{ 'arr-index': isArray }">{{ name }}<span class="punct">:</span></span>
      <template v-if="!isContainer">
        <span :class="typeClass" class="value">{{ truncated }}</span>
      </template>
      <template v-else-if="collapsed">
        <span class="punct">…</span>
        <span class="size-label">{{ sizeLabel }}</span>
      </template>

      <span class="actions" @click.stop>
        <span
          v-if="copyAction === 'value'"
          class="act-btn copied"
          title="已复制"
          @click.stop="copyValue"
        >
          <CheckmarkOutline :size="14" />
        </span>
        <span v-else class="act-btn" title="复制值" @click.stop="copyValue">
          <CopyOutline :size="14" />
        </span>
        <span
          v-if="name && !isArray"
          class="act-btn"
          :class="{ copied: copyAction === 'pair' }"
          :title="'复制键+值'"
          @click.stop="copyPair"
        >
          <CheckmarkOutline v-if="copyAction === 'pair'" :size="14" />
          <DocumentTextOutline v-else :size="14" />
        </span>
        <span
          v-if="name"
          class="act-btn"
          :class="{ copied: copyAction === 'key' }"
          title="复制键名"
          @click.stop="copyKey"
        >
          <CheckmarkOutline v-if="copyAction === 'key'" :size="14" />
          <KeyOutline v-else :size="14" />
        </span>
      </span>
    </div>
    <div v-if="isContainer && !collapsed" class="children">
      <JsonNode
        v-for="item in entries"
        :key="item.key"
        :value="item.value"
        :name="item.key"
        :path="`${path}.${item.key}`"
        :depth="depth + 1"
        :expand-signal="expandSignal"
        :collapse-signal="collapseSignal"
      />
      <div class="row closer">
        <span class="punct">{{ isArray ? ']' : '}' }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.json-node {
  font-family: 'JetBrains Mono', Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.7;
}

.row {
  display: flex;
  align-items: baseline;
  gap: 4px;
  padding: 1px 8px;
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-all;
  cursor: default;
  user-select: text;
  min-height: 24px;
}
.row:hover {
  background-color: var(--json-row-hover);
}
.row.no-toggle {
  padding-left: 8px;
}

.toggle {
  cursor: pointer;
  user-select: none;
  color: var(--json-punct);
  display: inline-flex;
  align-self: center;
}
.arrow {
  display: inline-block;
  width: 16px;
  color: var(--json-punct);
}

.key {
  color: var(--json-key);
  font-weight: 600;
}
.arr-index {
  color: var(--json-array-key);
  font-weight: 400;
}
.punct {
  color: var(--json-punct);
}
.size-label {
  color: var(--json-array-key);
  font-size: 12px;
  margin-left: 2px;
}
.value {
  user-select: text;
}
.children {
  margin-left: 2px;
  border-left: 1px dashed var(--json-punct);
  padding-left: 12px;
}
.closer {
  padding-left: 8px;
  color: var(--json-punct);
}

.actions {
  display: inline-flex;
  gap: 1px;
  align-items: center;
  align-self: center;
  margin-left: 8px;
  opacity: 0;
  transition: opacity 0.15s;
}
.row:hover .actions {
  opacity: 1;
}
.act-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 4px;
  color: var(--json-punct);
  cursor: pointer;
  user-select: none;
}
.act-btn:hover {
  background-color: var(--json-row-hover);
  color: var(--json-value-copy);
}
.act-btn.copied {
  color: var(--json-value-copy);
}

.v-str {
  color: var(--json-string);
}
.v-num {
  color: var(--json-number);
  font-weight: 600;
}
.v-bool {
  color: var(--json-boolean);
  font-weight: 600;
}
.v-null {
  color: var(--json-null);
  font-style: italic;
}
.v-other {
  color: var(--json-other);
}
</style>
