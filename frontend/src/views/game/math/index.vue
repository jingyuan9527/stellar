<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { NCard, NButton, NInput, NTag, NEmpty, NIcon, useMessage } from 'naive-ui'
import { useAuthStore } from '@/store/auth'
import { submitGameScore, getGameTopScores } from '@/api/game'
import type { GameScore } from '@/types/api'
import { iconMap } from '@/utils/icons'

const message = useMessage()
const authStore = useAuthStore()

// ===== 常量 =====
const TOTAL_QUESTIONS = 30
const QUESTION_INTERVAL = 3000 // 每 3 秒出一题
const TICK = 100 // 100ms 节拍驱动倒计时

// ===== 类型 =====
interface Question {
  id: number
  num1: number
  num2: number
  operator: '+' | '-'
  correctAnswer: number
  questionText: string
}
interface UserAnswer {
  questionId: number
  userAnswer: number | null
  isCorrect: boolean | null
  answeredAt: Date | null
}
interface GameResult {
  totalQuestions: number
  correctAnswers: number
  score: number
  accuracy: number
  totalTime: number // 毫秒
  questionDetails: { question: Question; userAnswer: number | null; isCorrect: boolean | null }[]
}

// ===== 状态 =====
type Phase = 'idle' | 'playing' | 'result'
const phase = ref<Phase>('idle')
const questions = ref<Question[]>([])
const userAnswers = ref<UserAnswer[]>([])
const questionCounter = ref(0)
const answerCounter = ref(0)
const canAnswerQuestionId = ref<number | null>(null)
const score = ref(0)
const gameStartTime = ref<Date | null>(null)
const gameEndTime = ref<Date | null>(null)
const countdown = ref(QUESTION_INTERVAL)
const feedback = ref<{ message: string; type: '' | 'success' | 'error' }>({ message: '', type: '' })
const result = ref<GameResult | null>(null)
const inputAnswer = ref('')
const inputRef = ref<{ focus: () => void } | null>(null)

// 排行榜
const leaderboard = ref<GameScore[]>([])
const showLeaderboard = ref(false)
const playerName = ref('')
const submitting = ref(false)
const submitted = ref(false)

// 计时器内部状态
let timerId: number | null = null
let lastTickTime = 0
const endFlag = ref(false)

// ===== 出题 =====
function generateQuestion(id: number): Question {
  const a = Math.floor(Math.random() * 10) + 1
  const b = Math.floor(Math.random() * 10) + 1
  const operator: '+' | '-' = Math.random() > 0.5 ? '+' : '-'
  let n1 = a, n2 = b, answer: number
  if (operator === '-') {
    // 减法保证非负：被减数不小于减数
    if (n1 < n2) { [n1, n2] = [n2, n1] }
    answer = n1 - n2
  } else {
    answer = n1 + n2
  }
  return { id, num1: n1, num2: n2, operator, correctAnswer: answer, questionText: `${n1} ${operator} ${n2} = ?` }
}

function generateQuestions(): Question[] {
  const list: Question[] = []
  for (let i = 1; i <= TOTAL_QUESTIONS; i++) {
    let q: Question, tries = 0
    // 与上一题去重（题面或答案相同则重试，最多 20 次）
    do {
      q = generateQuestion(i)
      tries++
    } while (
      tries < 20 &&
      list.length > 0 &&
      (q.questionText === list[list.length - 1].questionText ||
        q.correctAnswer === list[list.length - 1].correctAnswer)
    )
    list.push(q)
  }
  return list
}

// ===== 游戏控制 =====
function startGame() {
  questions.value = generateQuestions()
  userAnswers.value = questions.value.map(q => ({
    questionId: q.id, userAnswer: null, isCorrect: null, answeredAt: null,
  }))
  questionCounter.value = 0
  answerCounter.value = 0
  canAnswerQuestionId.value = null
  score.value = 0
  gameStartTime.value = new Date()
  gameEndTime.value = null
  feedback.value = { message: '', type: '' }
  result.value = null
  inputAnswer.value = ''
  submitted.value = false
  endFlag.value = false
  phase.value = 'playing'
  // 1 秒后出第一题并启动计时（给玩家准备时间）
  setTimeout(() => {
    questionCounter.value = 1
    startTimer()
  }, 1000)
}

function startTimer() {
  countdown.value = QUESTION_INTERVAL
  lastTickTime = 0
  timerId = window.setInterval(() => {
    countdown.value -= TICK
    if (countdown.value <= 100) {
      const now = Date.now()
      // 100ms 节流去抖，避免重复触发
      if (now - lastTickTime < 100) {
        countdown.value = QUESTION_INTERVAL
        return
      }
      lastTickTime = now
      if (endFlag.value) {
        endFlag.value = false
        stopTimer()
        gameEndTime.value = new Date()
        computeResult()
        phase.value = 'result'
      } else {
        advance()
        countdown.value = QUESTION_INTERVAL
      }
    }
  }, TICK)
}

function stopTimer() {
  if (timerId !== null) {
    clearInterval(timerId)
    timerId = null
  }
}

// 每 3 秒推进一步：出新一题 + 开放下一答题窗口
function advance() {
  let newQ = questionCounter.value
  let newA = answerCounter.value
  let newCanAnswer = canAnswerQuestionId.value
  let newAnswers = userAnswers.value

  if (questionCounter.value < TOTAL_QUESTIONS) {
    newQ = questionCounter.value + 1
  }

  // 答题解锁：第 6 题出现时开放第 1 题，之后每出一题开放下一题（滞后 5 题，考察记忆）
  const shouldOpenAnswer =
    (newQ === 6 && answerCounter.value === 0) ||
    (newQ > 6 && answerCounter.value > 0 && answerCounter.value < TOTAL_QUESTIONS)

  if (shouldOpenAnswer) {
    // 关闭上一答题窗口（未答的标记关闭时间）
    if (canAnswerQuestionId.value !== null) {
      newAnswers = userAnswers.value.map(a =>
        a.questionId === canAnswerQuestionId.value && !a.answeredAt
          ? { ...a, answeredAt: new Date() }
          : a
      )
    }
    newA = answerCounter.value + 1
    if (newA >= TOTAL_QUESTIONS) {
      // 答题窗口全开放，下一 tick 结束
      endFlag.value = true
      newA = TOTAL_QUESTIONS
      newCanAnswer = TOTAL_QUESTIONS
    } else {
      newCanAnswer = newA
    }
  }

  questionCounter.value = newQ
  answerCounter.value = newA
  canAnswerQuestionId.value = newCanAnswer
  userAnswers.value = newAnswers
  inputAnswer.value = ''
  feedback.value = { message: '', type: '' }
}

function submitAnswer() {
  const qid = canAnswerQuestionId.value
  if (!qid || !inputAnswer.value.trim()) return
  const existing = userAnswers.value.find(a => a.questionId === qid)
  if (existing && existing.userAnswer !== null) {
    feedback.value = { message: '该题已经回答过了！', type: 'error' }
    return
  }
  const ans = parseInt(inputAnswer.value, 10)
  if (isNaN(ans)) {
    feedback.value = { message: '请输入有效数字', type: 'error' }
    return
  }
  const q = questions.value.find(x => x.id === qid)
  if (!q) return
  const correct = ans === q.correctAnswer
  userAnswers.value = userAnswers.value.map(a =>
    a.questionId === qid
      ? { ...a, userAnswer: ans, isCorrect: correct, answeredAt: new Date() }
      : a
  )
  if (correct) score.value++

  // 答完最后一题且答题窗口已全开放 → 直接结束
  if (qid === TOTAL_QUESTIONS && answerCounter.value >= TOTAL_QUESTIONS) {
    stopTimer()
    gameEndTime.value = new Date()
    computeResult()
    phase.value = 'result'
    return
  }

  feedback.value = correct
    ? { message: `✅ 正确！${q.questionText.replace('?', String(ans))}`, type: 'success' }
    : { message: `❌ 错误！${q.questionText.replace('?', `${ans}，正确答案是 ${q.correctAnswer}`)}`, type: 'error' }
  inputAnswer.value = ''
}

function computeResult() {
  if (!gameEndTime.value || !gameStartTime.value) return
  const totalTime = gameEndTime.value.getTime() - gameStartTime.value.getTime()
  result.value = {
    totalQuestions: TOTAL_QUESTIONS,
    correctAnswers: score.value,
    score: score.value,
    accuracy: (score.value / TOTAL_QUESTIONS) * 100,
    totalTime,
    questionDetails: questions.value.map(q => {
      const ua = userAnswers.value.find(a => a.questionId === q.id)
      return { question: q, userAnswer: ua?.userAnswer ?? null, isCorrect: ua?.isCorrect ?? null }
    }),
  }
}

function backHome() {
  stopTimer()
  phase.value = 'idle'
  questions.value = []
  userAnswers.value = []
  questionCounter.value = 0
  answerCounter.value = 0
  canAnswerQuestionId.value = null
  score.value = 0
  gameStartTime.value = null
  gameEndTime.value = null
  result.value = null
  feedback.value = { message: '', type: '' }
  inputAnswer.value = ''
  submitted.value = false
}

// ===== 排行榜 =====
async function loadLeaderboard() {
  try {
    leaderboard.value = await getGameTopScores()
  } catch {
    // 错误已由拦截器提示
  }
}

async function submitToLeaderboard() {
  if (!playerName.value.trim() || !result.value || submitted.value) return
  submitting.value = true
  try {
    await submitGameScore({
      playerName: playerName.value.trim(),
      score: result.value.score,
      totalTime: Math.round(result.value.totalTime / 1000),
      accuracy: result.value.accuracy,
    })
    submitted.value = true
    message.success('成绩已提交到排行榜')
    await loadLeaderboard()
    showLeaderboard.value = true
  } catch {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

// 可答题窗口变化时自动聚焦输入框
watch(canAnswerQuestionId, async (v) => {
  if (v && phase.value === 'playing') {
    inputAnswer.value = ''
    feedback.value = { message: '', type: '' }
    await nextTick()
    inputRef.value?.focus()
  }
})

// ===== 计算属性 =====
const latestQuestion = computed(() => {
  if (questionCounter.value === 0) return null
  return questions.value.find(q => q.id === questionCounter.value) ?? null
})

// 是否显示最新题面：题全出完且答到第 26+ 题时进入纯答题阶段，不再显示
const showLatestQuestion = computed(() => {
  if (questionCounter.value === 0 || questionCounter.value > TOTAL_QUESTIONS) return false
  if (questionCounter.value === TOTAL_QUESTIONS && canAnswerQuestionId.value !== null && canAnswerQuestionId.value >= 26) {
    return false
  }
  return true
})

const questionProgress = computed(() => (questionCounter.value / TOTAL_QUESTIONS) * 100)
const answerProgress = computed(() => (answerCounter.value / TOTAL_QUESTIONS) * 100)
const countdownProgress = computed(() => (countdown.value / QUESTION_INTERVAL) * 100)

const waitHint = computed(() => {
  if (questionCounter.value === 0) return '准备中...'
  if (questionCounter.value < 6) return '第 6 题开始可以答题...'
  if (canAnswerQuestionId.value === null) return '等待下一题出现...'
  return ''
})

function formatTime(ms: number): string {
  return Math.round(ms / 1000).toString()
}

function formatCreateTime(s?: string): string {
  if (!s) return ''
  return s.replace('T', ' ').slice(0, 19)
}

onMounted(() => {
  // 登录用户默认填昵称
  if (authStore.userInfo?.nickname) {
    playerName.value = authStore.userInfo.nickname
  }
  loadLeaderboard()
})

onBeforeUnmount(() => {
  stopTimer()
})
</script>

<template>
  <div class="math-game-page">
    <!-- 首页 -->
    <template v-if="phase === 'idle'">
      <NCard :bordered="false" class="hero-card">
        <div class="hero">
          <h1 class="title">🧮 十以内加减法</h1>
          <div class="rules">
            <p>每 3 秒出一道题，共 30 题</p>
            <p>第 6 题开始可以回答第 1 题</p>
            <p>之后每出新题可答前 5 题</p>
            <p>30 题出完后进入答题阶段，回答所有未答题目</p>
          </div>
          <p class="tip">⚠️ 作答时不显示题目内容，考察记忆力</p>
          <div class="actions">
            <NButton type="primary" size="large" @click="startGame">
              <template #icon><NIcon><component :is="iconMap.play" /></NIcon></template>
              开始游戏
            </NButton>
            <NButton size="large" @click="showLeaderboard = !showLeaderboard">
              <template #icon><NIcon><component :is="iconMap.trophy" /></NIcon></template>
              {{ showLeaderboard ? '收起排行榜' : '查看排行榜' }}
            </NButton>
          </div>
        </div>
      </NCard>

      <NCard v-if="showLeaderboard" :bordered="false">
        <template #header>
          <div class="card-header">
            <NIcon><component :is="iconMap.trophy" /></NIcon>
            <span>排行榜（前 100）</span>
          </div>
        </template>
        <NEmpty v-if="!leaderboard.length" description="暂无数据，快来成为第一名！" />
        <div v-else class="rank-list">
          <div v-for="(item, idx) in leaderboard" :key="item.id" class="rank-item">
            <span class="rank-no" :class="{ top: idx < 3 }">{{ idx + 1 }}</span>
            <span class="rank-name">{{ item.playerName }}</span>
            <span class="rank-score">{{ item.score }} 分</span>
            <span class="rank-time">{{ item.totalTime }}s</span>
            <span class="rank-acc">{{ item.accuracy.toFixed(1) }}%</span>
            <span class="rank-date">{{ formatCreateTime(item.createTime) }}</span>
          </div>
        </div>
      </NCard>
    </template>

    <!-- 游戏中 -->
    <template v-else-if="phase === 'playing'">
      <NCard :bordered="false">
        <div class="game-header">
          <span class="counter">第 {{ questionCounter }}/{{ TOTAL_QUESTIONS }} 题</span>
          <span class="score">得分: {{ score }}</span>
        </div>

        <div class="progress-block">
          <div class="progress-label">
            <span>题目进度</span>
            <span>{{ questionCounter }}/{{ TOTAL_QUESTIONS }}</span>
          </div>
          <div class="progress-bar"><div class="progress-fill" :style="{ width: questionProgress + '%' }"></div></div>
        </div>
        <div class="progress-block">
          <div class="progress-label">
            <span>回答进度</span>
            <span>{{ answerCounter }}/{{ TOTAL_QUESTIONS }}</span>
          </div>
          <div class="progress-bar"><div class="progress-fill answer" :style="{ width: answerProgress + '%' }"></div></div>
        </div>
        <div class="progress-block">
          <div class="progress-label">
            <span>距下一题</span>
            <span>{{ (countdown / 1000).toFixed(1) }}s</span>
          </div>
          <div class="progress-bar"><div class="progress-fill countdown" :style="{ width: countdownProgress + '%' }"></div></div>
        </div>

        <!-- 最新题面 -->
        <div v-if="showLatestQuestion && latestQuestion" class="latest-question">
          {{ latestQuestion.questionText }}
        </div>
        <div v-else-if="questionCounter === TOTAL_QUESTIONS" class="latest-question placeholder">
          题目已全部出现，请继续作答未答题目
        </div>

        <!-- 作答区 -->
        <div v-if="canAnswerQuestionId" class="answer-area">
          <p class="answer-hint">请回答第 {{ canAnswerQuestionId }} 题</p>
          <p class="answer-sub">（考察记忆能力，不显示题目内容）</p>
          <div class="answer-form">
            <NInput
              ref="inputRef"
              v-model:value="inputAnswer"
              placeholder="输入答案"
              size="large"
              @keyup.enter="submitAnswer"
            />
            <NButton type="primary" size="large" @click="submitAnswer">提交答案</NButton>
          </div>
        </div>
        <div v-else-if="waitHint" class="wait-hint">{{ waitHint }}</div>

        <!-- 反馈 -->
        <div v-if="feedback.message" class="feedback" :class="feedback.type">
          {{ feedback.message }}
        </div>
      </NCard>
    </template>

    <!-- 结果 -->
    <template v-else>
      <NCard :bordered="false" class="result-card">
        <h1 class="result-title">🎉 游戏结束！</h1>

        <!-- 提交排行榜 -->
        <div v-if="!submitted" class="submit-area">
          <span class="submit-label">提交成绩到排行榜：</span>
          <NInput v-model:value="playerName" placeholder="输入姓名" style="max-width: 200px" />
          <NButton type="primary" :loading="submitting" :disabled="!playerName.trim()" @click="submitToLeaderboard">
            提交到排行榜
          </NButton>
        </div>
        <div v-else class="submitted-tag">
          <NTag type="success" size="medium">✅ 成绩已提交</NTag>
        </div>

        <div class="stats-grid">
          <div class="stat-item">
            <span class="stat-label">总得分</span>
            <span class="stat-value score">{{ result?.score }}/{{ TOTAL_QUESTIONS }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">正确率</span>
            <span class="stat-value accuracy">{{ result?.accuracy.toFixed(1) }}%</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">用时</span>
            <span class="stat-value time">{{ result ? formatTime(result.totalTime) : 0 }} 秒</span>
          </div>
        </div>

        <div class="details">
          <h3 class="details-title">答题详情</h3>
          <div v-for="d in result?.questionDetails" :key="d.question.id" class="detail-item">
            <span class="detail-q">第 {{ d.question.id }} 题: {{ d.question.questionText.replace('?', '') }}</span>
            <span class="detail-a">
              <template v-if="d.userAnswer !== null">{{ d.userAnswer }}</template>
              <template v-else>未答</template>
            </span>
            <span v-if="d.isCorrect === false" class="detail-correct">(正确: {{ d.question.correctAnswer }})</span>
            <span class="detail-icon">
              <span v-if="d.isCorrect === true">✅</span>
              <span v-else-if="d.isCorrect === false">❌</span>
              <span v-else>⏸️</span>
            </span>
          </div>
        </div>

        <div class="actions">
          <NButton type="primary" size="large" @click="startGame">
            <template #icon><NIcon><component :is="iconMap.refresh" /></NIcon></template>
            重新开始
          </NButton>
          <NButton size="large" @click="backHome">
            <template #icon><NIcon><component :is="iconMap.home" /></NIcon></template>
            回到首页
          </NButton>
          <NButton size="large" @click="showLeaderboard = !showLeaderboard">
            <template #icon><NIcon><component :is="iconMap.trophy" /></NIcon></template>
            {{ showLeaderboard ? '收起排行榜' : '查看排行榜' }}
          </NButton>
        </div>
      </NCard>

      <NCard v-if="showLeaderboard" :bordered="false">
        <template #header>
          <div class="card-header">
            <NIcon><component :is="iconMap.trophy" /></NIcon>
            <span>排行榜（前 100）</span>
          </div>
        </template>
        <NEmpty v-if="!leaderboard.length" description="暂无数据" />
        <div v-else class="rank-list">
          <div v-for="(item, idx) in leaderboard" :key="item.id" class="rank-item">
            <span class="rank-no" :class="{ top: idx < 3 }">{{ idx + 1 }}</span>
            <span class="rank-name">{{ item.playerName }}</span>
            <span class="rank-score">{{ item.score }} 分</span>
            <span class="rank-time">{{ item.totalTime }}s</span>
            <span class="rank-acc">{{ item.accuracy.toFixed(1) }}%</span>
            <span class="rank-date">{{ formatCreateTime(item.createTime) }}</span>
          </div>
        </div>
      </NCard>
    </template>
  </div>
</template>

<style scoped>
.math-game-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  text-align: center;
}

.title {
  font-size: 32px;
  font-weight: 800;
  margin: 0;
}

.rules {
  opacity: 0.75;
  line-height: 1.9;
  font-size: 14px;
}
.rules p { margin: 0; }

.tip {
  color: #f0a020;
  font-size: 13px;
  margin: 0;
}

.actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 排行榜 */
.rank-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.rank-item {
  display: grid;
  grid-template-columns: 40px 1fr auto auto auto auto;
  gap: 12px;
  align-items: center;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
  background: rgba(127, 127, 127, 0.06);
}
.rank-item:hover { background: rgba(127, 127, 127, 0.12); }
.rank-no {
  font-weight: 700;
  text-align: center;
  opacity: 0.6;
}
.rank-no.top { color: #f0a020; opacity: 1; }
.rank-name { font-weight: 600; }
.rank-score { color: #18a058; font-weight: 600; }
.rank-time { opacity: 0.7; }
.rank-acc { opacity: 0.7; }
.rank-date { opacity: 0.5; font-size: 12px; }

/* 游戏中 */
.game-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  font-size: 16px;
  font-weight: 600;
}
.score { color: #18a058; }

.progress-block { margin-bottom: 12px; }
.progress-label {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  opacity: 0.7;
  margin-bottom: 4px;
}
.progress-bar {
  height: 8px;
  background: rgba(127, 127, 127, 0.14);
  border-radius: 999px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: #18a058;
  border-radius: 999px;
  transition: width 0.3s ease;
}
.progress-fill.answer { background: #2080f0; }
.progress-fill.countdown { background: #f0a020; }

.latest-question {
  text-align: center;
  font-size: 40px;
  font-weight: 800;
  padding: 32px 0;
  letter-spacing: 2px;
}
.latest-question.placeholder {
  font-size: 16px;
  font-weight: 400;
  opacity: 0.6;
}

.answer-area {
  margin-top: 16px;
  padding: 16px;
  border-radius: 12px;
  background: rgba(32, 128, 240, 0.08);
  text-align: center;
}
.answer-hint { font-size: 16px; font-weight: 600; margin: 0 0 4px; }
.answer-sub { font-size: 12px; opacity: 0.6; margin: 0 0 12px; }
.answer-form {
  display: flex;
  gap: 8px;
  justify-content: center;
  max-width: 400px;
  margin: 0 auto;
}

.wait-hint {
  text-align: center;
  padding: 24px;
  opacity: 0.6;
  font-size: 14px;
}

.feedback {
  margin-top: 16px;
  padding: 10px 16px;
  border-radius: 8px;
  font-size: 14px;
  text-align: center;
}
.feedback.success { background: rgba(24, 160, 88, 0.12); color: #18a058; }
.feedback.error {
  background: rgba(208, 48, 80, 0.12);
  color: #d03050;
  animation: shake 0.4s;
}
@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-6px); }
  75% { transform: translateX(6px); }
}

/* 结果 */
.result-title {
  text-align: center;
  font-size: 28px;
  font-weight: 800;
  margin: 0 0 20px;
}
.submit-area {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
  margin-bottom: 20px;
}
.submit-label { font-size: 14px; }
.submitted-tag { text-align: center; margin-bottom: 20px; }

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}
.stat-item {
  text-align: center;
  padding: 16px;
  border-radius: 12px;
  background: rgba(127, 127, 127, 0.06);
}
.stat-label { display: block; font-size: 12px; opacity: 0.6; margin-bottom: 4px; }
.stat-value { font-size: 24px; font-weight: 800; }
.stat-value.score { color: #18a058; }
.stat-value.accuracy { color: #2080f0; }
.stat-value.time { color: #f0a020; }

.details { margin-bottom: 20px; }
.details-title { font-size: 16px; font-weight: 700; margin: 0 0 12px; }
.detail-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 13px;
  border-bottom: 1px solid rgba(127, 127, 127, 0.1);
}
.detail-q { flex: 1; }
.detail-a { font-weight: 600; }
.detail-correct { opacity: 0.6; font-size: 12px; }
.detail-icon { font-size: 16px; }

@media (max-width: 768px) {
  .title { font-size: 24px; }
  .latest-question { font-size: 28px; padding: 20px 0; }
  .stats-grid { grid-template-columns: 1fr; }
  .answer-form { flex-direction: column; }
  .rank-item {
    grid-template-columns: 32px 1fr auto auto;
    font-size: 12px;
  }
  .rank-acc, .rank-date { display: none; }
}
</style>
