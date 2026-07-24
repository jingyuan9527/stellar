<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import {
  NCard, NSpace, NButton, NInput, NSelect, NTag,
  NList, NListItem, NThing, NCollapse, NCollapseItem,
  NPopconfirm, NEmpty, NAlert, useMessage,
} from 'naive-ui'
import { useCoverStore } from '../store/cover'
import { useApiConfigStore } from '../store/apiConfig'
import { useUIStore } from '../store/ui'
import ApiSettingsModal from '../components/ApiSettingsModal.vue'
import { buildPrompt, parseCopyResult } from '../lib/llm'
import {
  getAiConfig, getAiTemplatePage, streamAiChat,
  saveCopyResult, getCopyResultPage, deleteCopyResult, clearCopyResults,
} from '@/api/ai'
import { useAuthStore } from '@/store/auth'
import type { AiTemplate, AiCopyResult, CopyResultData } from '@/types/api'

const router = useRouter()
const message = useMessage()
const coverStore = useCoverStore()
const authStore = useAuthStore()
const apiConfigStore = useApiConfigStore()
const uiStore = useUIStore()

// ===== 模板 =====
const templates = ref<AiTemplate[]>([])
const templateId = ref<number | null>(null)

const templateOptions = computed(() =>
  templates.value.map((t) => ({ value: t.id, label: t.name })),
)

// ===== 配置状态 =====
const configured = ref(false)

// ===== 生成 =====
const topic = ref('')
const streaming = ref(false)
const raw = ref('')
const abortRef = ref<AbortController | null>(null)

// ===== 历史 =====
const history = ref<AiCopyResult[]>([])
const activeId = ref<number | null>(null)

const activeResult = computed(() =>
  history.value.find((h) => h.id === activeId.value) ?? null,
)

const display = computed<CopyResultData | null>(() => {
  if (!activeResult.value || streaming.value) return null
  try {
    return JSON.parse(activeResult.value.result) as CopyResultData
  } catch {
    return null
  }
})

onBeforeUnmount(() => abortRef.value?.abort())

async function loadTemplates() {
  try {
    const res = await getAiTemplatePage({ pageNum: 1, pageSize: 100 })
    templates.value = res.records
    if (templateId.value === null && res.records.length > 0) {
      templateId.value = res.records[0].id
    }
  } catch {
    // 错误已由拦截器提示
  }
}

async function loadConfig() {
  try {
    const config = await getAiConfig()
    configured.value = config.configured
  } catch {
    // 错误已由拦截器提示
  }
}

async function loadHistory() {
  try {
    const res = await getCopyResultPage({ pageNum: 1, pageSize: 50 })
    history.value = res.records
    if (activeId.value === null && res.records.length > 0) {
      activeId.value = res.records[0].id
    }
  } catch {
    // 错误已由拦截器提示
  }
}

async function generate() {
  if (!topic.value.trim()) {
    message.warning('请输入视频主题')
    return
  }
  const tpl = templates.value.find((t) => t.id === templateId.value)
  if (!tpl) {
    message.warning('请选择模板')
    return
  }
  if (!configured.value) {
    message.warning('请先在 系统管理 → AI 配置 中完成配置')
    return
  }
  streaming.value = true
  raw.value = ''
  const ac = new AbortController()
  abortRef.value = ac
  try {
    const full = await streamAiChat(
      buildPrompt(tpl.prompt, topic.value.trim()),
      (text) => { raw.value = text },
      ac.signal,
      apiConfigStore.state,
    )
    const parsed = parseCopyResult(full)
    if (parsed) {
      if (authStore.isLogin) {
        await saveCopyResult({
          topic: topic.value.trim(),
          templateId: tpl.id,
          result: JSON.stringify(parsed),
        })
        await loadHistory()
        const latest = history.value[0]
        if (latest) activeId.value = latest.id
      } else {
        // 游客不持久化，本地虚拟一项用于展示生成结果
        const tempId = Date.now()
        history.value.unshift({
          id: tempId,
          topic: topic.value.trim(),
          templateId: tpl.id,
          result: JSON.stringify(parsed),
          generatedAt: Date.now(),
          creatorId: 0,
          createTime: new Date().toISOString(),
          updateTime: new Date().toISOString(),
        } as AiCopyResult)
        activeId.value = tempId
      }
    } else {
      message.error('未能解析为 JSON，已展示原始文本')
    }
  } catch (e) {
    if ((e as Error).name !== 'AbortError') {
      message.error('请求失败: ' + (e as Error).message)
    }
  } finally {
    streaming.value = false
  }
}

function stop() {
  abortRef.value?.abort()
}

function sendToCover(title: string) {
  coverStore.update({ title })
  router.push('/video/cover')
}

function copyText(text: string) {
  navigator.clipboard.writeText(text)
  message.success('已复制')
}

function formatTime(ts: number | string): string {
  if (typeof ts === 'number') {
    return new Date(ts).toLocaleString()
  }
  return ts.toString().replace('T', ' ').slice(0, 19)
}

async function handleDeleteHistory(id: number) {
  try {
    await deleteCopyResult(id)
    if (activeId.value === id) activeId.value = null
    await loadHistory()
  } catch {
    // 错误已由拦截器提示
  }
}

async function handleClearHistory() {
  try {
    await clearCopyResults()
    activeId.value = null
    await loadHistory()
    message.success('已清空全部历史')
  } catch {
    // 错误已由拦截器提示
  }
}

function formatTag(t: string) {
  return t.startsWith('#') ? t : `#${t}`
}

onMounted(() => {
  if (authStore.isLogin) {
    loadConfig()
    loadHistory()
  } else {
    configured.value = true
  }
  loadTemplates()
})
</script>

<template>
  <div class="copy-page">
    <NCard title="AI 文案生成" :bordered="false">
      <template #header-extra>
        <NPopconfirm @positive-click="handleClearHistory">
          <template #trigger>
            <NButton size="small" type="error" :disabled="history.length === 0">清空历史</NButton>
          </template>
          确认清空全部历史记录？
        </NPopconfirm>
      </template>

      <NSpace vertical :size="16">
        <NAlert v-if="!configured" type="warning" :bordered="false">
          AI 接口未配置，请前往 系统管理 → AI 配置 完成设置。
        </NAlert>

        <div>
          <div class="field-label">视频主题</div>
          <NInput
            v-model:value="topic"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 4 }"
            placeholder="输入视频主题"
          />
        </div>

        <div>
          <div class="field-label">提示词模板</div>
          <NSelect
            v-model:value="templateId"
            :options="templateOptions"
            placeholder="选择模板"
          />
        </div>

        <NSpace>
          <NButton
            type="primary"
            :loading="streaming"
            :disabled="!configured"
            @click="generate"
          >
            {{ configured ? '生成文案' : '未配置 API' }}
          </NButton>
          <NButton v-if="streaming" type="error" @click="stop">停止</NButton>
          <NButton @click="uiStore.openModal('api')">自己的 AI</NButton>
        </NSpace>
      </NSpace>
    </NCard>

    <NCard v-if="streaming" title="生成中..." :bordered="false">
      <pre class="stream-preview">{{ raw || '等待响应...' }}</pre>
    </NCard>

    <NCard
      v-if="display && activeResult"
      :title="`生成结果 · ${formatTime(activeResult.generatedAt)}`"
      :bordered="false"
    >
      <div class="result-section">
        <div class="section-header">
          <span class="section-label">标题</span>
        </div>
        <NSpace vertical :size="8" style="margin-top: 8px">
          <div
            v-for="(t, i) in display.titles"
            :key="i"
            class="title-row"
          >
            <span class="title-text">{{ t }}</span>
            <NSpace size="small">
              <NButton size="small" @click="copyText(t)">复制</NButton>
              <NButton size="small" type="primary" @click="sendToCover(t)">发送到封面</NButton>
            </NSpace>
          </div>
        </NSpace>
      </div>

      <div class="result-section">
        <div class="section-header">
          <span class="section-label">简介</span>
          <NButton size="tiny" text type="primary" @click="copyText(display.description)">复制</NButton>
        </div>
        <p class="section-text">{{ display.description }}</p>
      </div>

      <div class="result-section">
        <div class="section-header">
          <span class="section-label">标签</span>
          <NButton
            size="tiny"
            text
            type="primary"
            @click="copyText(display.tags.map(formatTag).join(' '))"
          >
            复制全部
          </NButton>
        </div>
        <NSpace :size="8" style="margin-top: 8px">
          <NTag
            v-for="(t, i) in display.tags"
            :key="i"
            :bordered="false"
            style="cursor: pointer"
            @click="copyText(formatTag(t))"
          >
            {{ formatTag(t) }}
          </NTag>
        </NSpace>
      </div>
    </NCard>

    <NCollapse v-if="authStore.isLogin && history.length > 0">
      <NCollapseItem :title="`历史记录 (${history.length})`" name="history">
        <NList hoverable>
          <NListItem v-for="h in history" :key="h.id">
            <NThing>
              <template #header>{{ h.topic }}</template>
              <template #description>
                {{ formatTime(h.generatedAt) }}
              </template>
              <template #header-extra>
                <NSpace size="small">
                  <NButton
                    size="small"
                    :type="h.id === activeId ? 'primary' : 'default'"
                    @click="activeId = h.id"
                  >
                    查看
                  </NButton>
                  <NPopconfirm @positive-click="handleDeleteHistory(h.id)">
                    <template #trigger>
                      <NButton size="small" type="error">删除</NButton>
                    </template>
                    删除该记录？
                  </NPopconfirm>
                </NSpace>
              </template>
            </NThing>
          </NListItem>
        </NList>
      </NCollapseItem>
    </NCollapse>

    <ApiSettingsModal />
  </div>
</template>

<style scoped>
.copy-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 720px;
  margin: 0 auto;
}

.field-label {
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 6px;
  opacity: 0.8;
}

.stream-preview {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.6;
}

.result-section {
  margin-bottom: 16px;
}

.result-section:last-child {
  margin-bottom: 0;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.section-label {
  font-weight: 600;
  font-size: 14px;
}

.section-text {
  margin: 8px 0 0;
  line-height: 1.6;
}

.title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.title-text {
  flex: 1;
}
</style>
