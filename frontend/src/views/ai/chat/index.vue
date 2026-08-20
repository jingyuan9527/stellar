<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import {
  NSpace, NButton, NInput, NSelect, NEmpty, NAlert, NPopconfirm, NPopover, useMessage,
} from 'naive-ui'
import { useAuthStore } from '@/store/auth'
import { useIsMobile } from '@/composables/useBreakpoint'
import {
  listMyChatSessions, createChatSession, getChatMessages, deleteChatSession,
  clearMyChatSessions, streamChat, listEnabledPersonas, listKnowledgeBases,
  submitFeedback,
} from '@/api/chat'
import { getAiModelsByType as _getModels } from '@/api/ai'
import { chatVoiceOptions } from '@/constants/tts-voices'
import type { AiChatSession, AiChatMessage, AiPersona, AiKnowledgeBase, AiModel } from '@/types/api'

const authStore = useAuthStore()
const message = useMessage()
const isMobile = useIsMobile()
const sessionCollapsed = ref(true)
// 乐观消息临时 id（负数，避免与后端真实 id 冲突导致 v-for 重复 key）
let tmpMsgId = 0
let disposed = false

// ===== 会话 =====
const sessions = ref<AiChatSession[]>([])
const currentSessionId = ref<number | null>(null)
const messages = ref<AiChatMessage[]>([])
const loadingMessages = ref(false)

// ===== 顶部设置（用于新建对话）=====
const personas = ref<AiPersona[]>([])
const personaId = ref<number | null>(null)
const knowledgeBases = ref<AiKnowledgeBase[]>([])
const kbId = ref<number | null>(null)
const textModels = ref<AiModel[]>([])
const modelId = ref<number | null>(null)

const personaOptions = computed(() =>
  personas.value.map((p) => ({ value: p.id, label: p.name })))
const kbOptions = computed(() =>
  knowledgeBases.value.map((k) => ({ value: k.id, label: k.name })))
const modelOptions = computed(() =>
  textModels.value.map((m) => ({
    value: m.id,
    label: m.providerName ? `${m.model} (${m.providerName})` : m.model,
  })))

// ===== 输入与流式 =====
const input = ref('')
const streaming = ref(false)
const abortRef = ref<AbortController | null>(null)
// 流式 assistant 消息（本地乐观渲染）
const streamingContent = ref('')
// 流式进度状态（generating_image/generating_audio），工具执行期间显示进度提示
const streamingStatus = ref('')
const streamError = ref('')
// TTS 音色（仅登录用户工具调用生效；用户选了具体音色则按音色所属引擎走覆盖系统开关）
const ttsVoice = ref<string | null>(null)

// 回复反馈（👍有用/👎没用）：记录当前已选评价值，切换/取消本地即时反馈，异步落库
const feedbackMap = ref<Record<number, number>>({})

async function handleFeedback(messageId: number, value: number) {
  if (!messageId || !authStore.isLogin) return
  const next = feedbackMap.value[messageId] === value ? 0 : value
  // 乐观更新，失败回滚
  const prev = feedbackMap.value[messageId] ?? 0
  feedbackMap.value[messageId] = next
  try {
    await submitFeedback(messageId, next)
  } catch {
    feedbackMap.value[messageId] = prev
    // 错误已由拦截器提示
  }
}

// 页面高度：视口 - 顶栏(56) - 多标签页(40,仅登录) - 内容区 padding(16×2 / 移动端 12×2)
// 不依赖父容器 height:100%（NLayoutContent 自定义滚动容器不约束子高度），保证输入区固定在最下方
const chatPageStyle = computed(() => {
  const padding = isMobile.value ? 12 : 16
  const chrome = 56 + (authStore.isLogin ? 40 : 0) + padding * 2
  return { height: `calc(100vh - ${chrome}px)` }
})

const messageListRef = ref<HTMLElement | null>(null)

function scrollToBottom() {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

watch(() => messages.value.length, () => scrollToBottom())
watch(() => streamingContent.value, () => scrollToBottom())

// ===== 加载 =====
async function loadSessions() {
  try {
    const result = await listMyChatSessions()
    if (disposed) return
    sessions.value = result
    if (sessions.value.length > 0 && currentSessionId.value === null) {
      await selectSession(sessions.value[0].id)
    } else if (sessions.value.length === 0) {
      await newSession()
    }
  } catch {
    // 错误已由拦截器提示
  }
}

async function loadPersonas() {
  try {
    const result = await listEnabledPersonas()
    if (disposed) return
    personas.value = result
    if (personaId.value === null && personas.value.length > 0) {
      personaId.value = personas.value[0].id
    }
  } catch {
    // 错误已由拦截器提示
  }
}

async function loadKnowledgeBases() {
  if (!authStore.isLogin) return
  try {
    const result = await listKnowledgeBases()
    if (disposed) return
    knowledgeBases.value = result
  } catch {
    // 错误已由拦截器提示
  }
}

async function loadModels() {
  if (!authStore.isLogin) return
  try {
    const result = await _getModels('TEXT')
    if (disposed) return
    textModels.value = result
    if (modelId.value === null && textModels.value.length > 0) {
      const def = textModels.value.find((m) => m.isDefault === 1)
      modelId.value = def?.id ?? textModels.value[0].id
    }
  } catch {
    // 错误已由拦截器提示
  }
}

async function selectSession(id: number) {
  if (disposed) return
  currentSessionId.value = id
  loadingMessages.value = true
  streamError.value = ''
  try {
    const result = await getChatMessages(id)
    if (disposed) return
    messages.value = result
    // 回填历史反馈（"有用/没用"选中态），保证切会话/刷新后状态不丢
    const map: Record<number, number> = {}
    for (const m of result) {
      if (m.role === 'assistant' && m.feedbackValue) map[m.id] = m.feedbackValue
    }
    feedbackMap.value = map
    scrollToBottom()
  } catch {
    // 错误已由拦截器提示
  } finally {
    if (!disposed) loadingMessages.value = false
  }
}

async function newSession() {
  try {
    const s = await createChatSession({
      personaId: personaId.value,
      kbId: authStore.isLogin ? kbId.value : null,
      title: '新对话',
    })
    if (disposed) return
    sessions.value.unshift(s)
    await selectSession(s.id)
  } catch {
    // 错误已由拦截器提示
  }
}

async function handleDeleteSession(id: number) {
  try {
    await deleteChatSession(id)
    sessions.value = sessions.value.filter((s) => s.id !== id)
    if (currentSessionId.value === id) {
      currentSessionId.value = null
      messages.value = []
      if (sessions.value.length > 0) {
        await selectSession(sessions.value[0].id)
      } else {
        await newSession()
      }
    }
  } catch {
    // 错误已由拦截器提示
  }
}

async function handleClearSessions() {
  try {
    await clearMyChatSessions()
    sessions.value = []
    currentSessionId.value = null
    messages.value = []
    await newSession()
    message.success('已清空全部会话')
  } catch {
    // 错误已由拦截器提示
  }
}

// ===== 发送 =====
async function send() {
  const text = input.value.trim()
  if (!text) return
  if (!currentSessionId.value) {
    await newSession()
    if (!currentSessionId.value) return
  }
  if (streaming.value) return

  // 乐观：本地先追加 user + 空 assistant
  const now = new Date().toISOString()
  messages.value.push({
    id: --tmpMsgId, sessionId: currentSessionId.value, role: 'user', content: text,
    tokens: null, createTime: now, attachmentType: null, attachmentFileId: null,
    attachmentUrl: null, ragRefs: null, refs: null, feedbackValue: null,
  })
  streamingContent.value = ''
  streamingStatus.value = ''
  streamError.value = ''
  input.value = ''
  streaming.value = true
  const ac = new AbortController()
  abortRef.value = ac

  try {
    await streamChat(
      currentSessionId.value,
      text,
      (delta) => {
        if (!disposed) streamingContent.value = delta
      },
      ac.signal,
      authStore.isLogin ? modelId.value : null,
      authStore.isLogin ? ttsVoice.value : null,
      (status) => {
        if (!disposed) streamingStatus.value = status
      },
    )
    if (disposed) return
    // 流式完成：先把 assistant 乐观加入消息列表，再关 streaming 行——
    // 让回答从 streaming 行无缝衔接到消息流，避免“消失再重现”的闪烁
    messages.value.push({
      id: --tmpMsgId,
      sessionId: currentSessionId.value!,
      role: 'assistant',
      content: streamingContent.value,
      tokens: null,
      createTime: new Date().toISOString(),
      attachmentType: null,
      attachmentFileId: null,
      attachmentUrl: null,
      ragRefs: null,
      refs: null,
      feedbackValue: null,
    })
    streaming.value = false
    streamingContent.value = ''
    streamingStatus.value = ''
    if (currentSessionId.value) {
      await selectSession(currentSessionId.value)
    }
    if (disposed) return
    const result = await listMyChatSessions()
    if (disposed) return
    sessions.value = result
  } catch (e) {
    if (disposed) return
    streaming.value = false
    streamingStatus.value = ''
    const aborted = (e as Error).name === 'AbortError'
    if (currentSessionId.value) {
      await selectSession(currentSessionId.value)
    }
    if (disposed) return
    if (!aborted) {
      streamError.value = (e as Error).message || '请求失败'
      message.error('请求失败: ' + streamError.value)
    } else {
      streamingContent.value = ''
    }
  } finally {
    if (!disposed && abortRef.value === ac) abortRef.value = null
  }
}

function stop() {
  abortRef.value?.abort()
}

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    message.success('已复制')
  } catch {
    message.error('复制失败')
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    send()
  }
}

onBeforeUnmount(() => {
  disposed = true
  abortRef.value?.abort()
})

onMounted(() => {
  disposed = false
  loadPersonas()
  loadKnowledgeBases()
  loadModels()
  loadSessions()
})
</script>

<template>
  <div class="chat-page" :style="chatPageStyle">
    <aside class="session-panel" :class="{ collapsed: isMobile && sessionCollapsed }">
      <div class="session-panel-header">
        <NButton size="small" type="primary" block @click="newSession">+ 新建对话</NButton>
        <NButton v-if="isMobile" size="small" quaternary block @click="sessionCollapsed = !sessionCollapsed">
          {{ sessionCollapsed ? '展开会话列表' : '收起会话列表' }}
        </NButton>
      </div>
      <div v-show="!isMobile || !sessionCollapsed" class="session-list">
        <div
          v-for="s in sessions"
          :key="s.id"
          class="session-item"
          :class="{ active: s.id === currentSessionId }"
          role="button"
          tabindex="0"
          @click="selectSession(s.id)"
          @keydown.enter="selectSession(s.id)"
        >
          <span class="session-title">{{ s.title }}</span>
          <NPopconfirm @positive-click="handleDeleteSession(s.id)">
            <template #trigger>
              <span class="session-del" @click.stop>×</span>
            </template>
            删除该会话？
          </NPopconfirm>
        </div>
        <NEmpty v-if="sessions.length === 0" description="暂无会话" size="small" />
      </div>
      <div v-show="!isMobile || !sessionCollapsed" class="session-panel-footer">
        <NPopconfirm @positive-click="handleClearSessions">
          <template #trigger>
            <NButton size="small" block quaternary type="error" :disabled="sessions.length === 0">清空全部</NButton>
          </template>
          确认清空全部会话？
        </NPopconfirm>
      </div>
    </aside>

    <section class="chat-panel">
      <header class="chat-header">
        <div class="header-field">
          <span class="field-label">人设</span>
          <NSelect
            v-model:value="personaId"
            :options="personaOptions"
            size="small"
            placeholder="通用助手"
            style="width: 160px"
          />
        </div>
        <!-- 桌面端：设置项平铺 -->
        <template v-if="!isMobile">
          <div v-if="authStore.isLogin" class="header-field">
            <span class="field-label">知识库</span>
            <NSelect
              v-model:value="kbId"
              :options="kbOptions"
              size="small"
              style="width: 180px"
              clearable
              placeholder="不关联"
            />
          </div>
          <div v-if="authStore.isLogin && modelOptions.length > 0" class="header-field">
            <span class="field-label">模型</span>
            <NSelect
              v-model:value="modelId"
              :options="modelOptions"
              size="small"
              style="width: 200px"
              clearable
            />
          </div>
          <div v-if="authStore.isLogin" class="header-field">
            <span class="field-label">TTS音色</span>
            <NSelect
              v-model:value="ttsVoice"
              :options="chatVoiceOptions"
              size="small"
              style="width: 160px"
              clearable
              placeholder="按系统开关"
            />
          </div>
          <span class="header-hint">人设/知识库将用于新对话</span>
        </template>
        <!-- 移动端：登录用户的设置项收进弹层，避免头部堆叠 4 行挤占消息区 -->
        <template v-else>
          <NPopover v-if="authStore.isLogin" trigger="click" placement="bottom-end" style="width: 260px">
            <template #trigger>
              <NButton size="small" secondary>设置</NButton>
            </template>
            <div class="mobile-settings">
              <div class="header-field">
                <span class="field-label">知识库</span>
                <NSelect
                  v-model:value="kbId"
                  :options="kbOptions"
                  size="small"
                  clearable
                  placeholder="不关联"
                />
              </div>
              <div v-if="modelOptions.length > 0" class="header-field">
                <span class="field-label">模型</span>
                <NSelect
                  v-model:value="modelId"
                  :options="modelOptions"
                  size="small"
                  clearable
                />
              </div>
              <div class="header-field">
                <span class="field-label">TTS音色</span>
                <NSelect
                  v-model:value="ttsVoice"
                  :options="chatVoiceOptions"
                  size="small"
                  clearable
                  placeholder="按系统开关"
                />
              </div>
              <p class="mobile-hint">人设/知识库将用于新对话</p>
            </div>
          </NPopover>
        </template>
      </header>

      <div ref="messageListRef" class="message-list">
        <NEmpty v-if="loadingMessages && messages.length === 0" class="msg-empty" />
        <template v-for="m in messages" :key="m.id">
          <div v-if="m.role !== 'system'" class="msg-row" :class="m.role">
            <div class="msg-role">{{ m.role === 'user' ? '我' : 'AI' }}</div>
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
            <div class="msg-tools" :class="{ visible: m.content }">
              <NButton
                v-if="m.content"
                class="msg-copy"
                size="tiny"
                text
                type="primary"
                @click="copyText(m.content)"
              >
                复制
              </NButton>
              <template v-if="m.role === 'assistant' && m.id && authStore.isLogin">
                <NButton
                  class="msg-fb"
                  size="tiny"
                  text
                  :type="feedbackMap[m.id] === 1 ? 'success' : 'default'"
                  :disabled="feedbackMap[m.id] === -1"
                  @click="handleFeedback(m.id, 1)"
                >
                  {{ feedbackMap[m.id] === 1 ? '✓ 有用' : '有用' }}
                </NButton>
                <NButton
                  class="msg-fb"
                  size="tiny"
                  text
                  :type="feedbackMap[m.id] === -1 ? 'error' : 'default'"
                  :disabled="feedbackMap[m.id] === 1"
                  @click="handleFeedback(m.id, -1)"
                >
                  {{ feedbackMap[m.id] === -1 ? '✗ 没用' : '没用' }}
                </NButton>
              </template>
            </div>
          </div>
        </template>
        <div v-if="streaming" class="msg-row assistant">
          <div class="msg-role">AI</div>
          <div class="bubble">
            <span v-if="streamingContent">{{ streamingContent }}</span>
            <span v-else class="typing">{{
              streamingStatus === 'retrieving'
                ? '正在检索资料…'
                : streamingStatus === 'generating_image'
                  ? '正在生成图片…'
                  : streamingStatus === 'generating_audio'
                    ? '正在合成语音…'
                    : '正在思考…'
            }}</span>
          </div>
        </div>
        <div v-if="streamError" class="msg-row assistant">
          <div class="msg-role">AI</div>
          <div class="bubble error">
            <span class="error-title">回复失败</span>
            <span class="error-detail">{{ streamError }}</span>
          </div>
        </div>
        <NEmpty v-if="!loadingMessages && messages.length === 0 && !streaming" description="开始一段新对话" style="margin: auto" />
      </div>

      <footer class="input-area">
        <NAlert v-if="!authStore.isLogin" type="info" :bordered="false" style="margin-bottom: 8px">
          游客每日 20 次（受 IP 限流）。会话按 IP 记录，同网络下他人可见。登录后可用知识库 + 备忘笔记 RAG（带参考溯源）与长期记忆。
        </NAlert>
        <div class="input-row">
          <NInput
            v-model:value="input"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 5 }"
            placeholder="输入消息，Enter 发送，Shift+Enter 换行"
            @keydown="onKeydown"
          />
          <NButton v-if="!streaming" type="primary" :disabled="!input.trim()" @click="send">发送</NButton>
          <NButton v-else type="error" @click="stop">停止</NButton>
        </div>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  min-height: 0;
  gap: 1px;
  background: var(--n-border-color, rgba(128, 128, 128, 0.15));
}
.session-panel {
  width: 240px;
  display: flex;
  flex-direction: column;
  background: var(--n-color, #fff);
  min-height: 0;
}
.session-panel-header,
.session-panel-footer {
  padding: 10px;
}
.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px;
}
.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: var(--r-sm);
  cursor: pointer;
  font-size: 13px;
  gap: 8px;
}
.session-item:hover {
  background: var(--n-color-hover, rgba(128, 128, 128, 0.08));
}
.session-item.active {
  background: var(--c-brand-bg);
  font-weight: 600;
}
.session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-del {
  color: var(--n-text-color-3, var(--c-text-3));
  font-size: 16px;
  line-height: 1;
  padding: 2px 6px;
  border-radius: 4px;
  flex-shrink: 0;
}
.session-del:hover {
  background: var(--c-error-bg);
  color: var(--c-error);
}
.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: var(--n-color, #fff);
}
.chat-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--n-border-color, rgba(128, 128, 128, 0.15));
  flex-wrap: wrap;
}
.header-field {
  display: flex;
  align-items: center;
  gap: 6px;
}
.field-label {
  font-size: 12px;
  color: var(--c-text-3);
  white-space: nowrap;
}
.header-hint {
  margin-left: auto;
  font-size: 12px;
  color: var(--c-text-3);
}

/* 移动端设置弹层内字段纵向排列 */
.mobile-settings {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.mobile-settings .header-field {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 4px;
}
.mobile-settings :deep(.n-base-selection) {
  width: 100% !important;
}
.mobile-hint {
  margin: 0;
  font-size: 12px;
  color: var(--c-text-3);
}
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}
.msg-empty {
  margin: auto;
}
.msg-row {
  display: flex;
  flex-direction: column;
  max-width: 80%;
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
  margin-bottom: 4px;
}
.bubble {
  padding: 10px 14px;
  border-radius: var(--r-lg);
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
  font-size: 14px;
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
  max-width: 280px;
  display: block;
  margin-bottom: 8px;
}
.msg-row.user .bubble {
  background: var(--c-brand-bg);
  border-bottom-right-radius: 4px;
}
.msg-row.assistant .bubble {
  background: var(--n-color-hover, rgba(128, 128, 128, 0.08));
  border-bottom-left-radius: 4px;
}
.typing {
  color: var(--c-text-3);
  font-style: italic;
}
.bubble.error {
  background: var(--c-error-bg);
  color: var(--c-error);
}
.error-title {
  display: block;
  font-weight: 600;
  margin-bottom: 4px;
}
.error-detail {
  display: block;
  font-size: 13px;
  color: var(--c-text-3);
}
.msg-text {
  display: block;
}
.msg-refs {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--n-border-color, rgba(128, 128, 128, 0.25));
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
  color: var(--n-text-color, inherit);
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
.msg-tools {
  display: flex;
  gap: 4px;
  align-items: center;
  margin-top: 4px;
  opacity: 0;
  transition: opacity 0.15s ease;
}
.msg-tools.visible,
.msg-tools:has(.msg-fb:hover),
.msg-row:hover .msg-tools,
.msg-tools:focus-within {
  opacity: 1;
}
.msg-copy {
  opacity: 1 !important;
}
.msg-fb {
  margin-left: 2px;
}
.input-area {
  padding: 12px 16px 16px;
  border-top: 1px solid var(--n-border-color, rgba(128, 128, 128, 0.15));
}
.input-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

@media (max-width: 768px) {
  .chat-page {
    flex-direction: column;
  }
  .session-panel {
    width: 100%;
    max-height: 200px;
  }
  .chat-header {
    gap: 8px;
    padding: 8px 10px;
  }
  .header-hint {
    display: none;
  }
  .header-field :deep(.n-base-selection) {
    width: 120px !important;
  }
  .msg-row {
    max-width: 95%;
  }
  .message-list {
    padding: 12px;
  }
  .input-area {
    padding: 8px 12px 12px;
  }
  .msg-tools {
    opacity: 1;
  }
}
</style>
