export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

export interface SysUser {
  id: number
  username: string
  nickname: string
  avatar: string
  status: number
  createTime: string
  updateTime: string
}

export interface LoginResult {
  token: string
  userInfo: SysUser
}

export interface LoginRequest {
  username: string
  password: string
}

export interface SysLog {
  id: number
  module: string
  operationType: string
  operator: string
  requestMethod: string
  requestUrl: string
  javaMethod: string
  params: string
  status: number
  errorMsg: string
  ip: string
  duration: number
  createTime: string
}

export interface SysLogQuery {
  module?: string
  operator?: string
  status?: number | null
  startTime?: string | null
  endTime?: string | null
  pageNum?: number
  pageSize?: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface TtsRecord {
  id: number
  text: string
  voice: string
  rate: number
  pitch: number
  volume: number
  fileSize: number
  audioFormat: string
  operator: string
  createTime: string
}

export interface TtsRecordQuery {
  text?: string
  voice?: string
  startTime?: string | null
  endTime?: string | null
  pageNum: number
  pageSize: number
}

export interface AiProvider {
  id: number
  name: string
  endpoint: string
  apiKey: string
  availableModels: string[]
  enabled: number
  sortOrder: number
  createTime: string
  updateTime: string
}

export interface AiModel {
  id: number
  providerId: number
  providerName: string | null
  model: string
  modelType: string
  enabled: number
  isDefault: number
  sortOrder: number
  createTime: string
  updateTime: string
}

export interface AiImageTask {
  taskId: number
  status: string
  prompt: string
  size: string | null
  ratio: string | null
  url: string | null
  errorMsg: string | null
  createTime: string
  updateTime: string | null
  durationMs: number | null
}

export interface AiVideoTask {
  taskId: string
  videoId: string
  status: string
}

export interface AiVideoStatus {
  status: string
  progress: number
  videoUrl: string | null
  seconds: string
  size: string
}

export interface DictData {
  value: string
  label: string
  sortOrder: number
}

export interface AiTemplate {
  id: number
  name: string
  platform: string
  prompt: string
  builtIn: number
  creatorId: number | null
  createTime: string
  updateTime: string
}

export interface AiTemplateQuery {
  name?: string
  platform?: string
  pageNum: number
  pageSize: number
}

export interface CopyResultData {
  titles: string[]
  description: string
  tags: string[]
}

/** AI 文本生成历史记录（流式结束自动落库） */
export interface AiChatRecord {
  id: number
  model: string | null
  prompt: string
  result: string | null
  status: string
  errorMsg: string | null
  requestTime: string
  responseTime: string | null
  durationMs: number | null
  createTime: string
}

/** AI 视频生成历史记录（本地留痕） */
export interface AiVideoHistory {
  id: number
  prompt: string
  ratio: string | null
  duration: number | null
  status: string
  url: string | null
  errorMsg: string | null
  createTime: string
  updateTime: string | null
  durationMs: number | null
}

export interface MenuVisibility {
  id: number
  routeKey: string
  routeName: string
  parentKey: string | null
  publicVisible: number
  sortOrder: number
  createTime: string
  updateTime: string
}

export interface MenuVisibilityItem {
  routeKey: string
  routeName: string
  parentKey: string | null
  publicVisible: number
  sortOrder: number
}

export interface Profile {
  id: number
  nickname: string
  avatar: string | null
  bio: string | null
  skills: string | null
  links: string | null
  title: string | null
  about: string | null
  location: string | null
  updateTime: string
}

export interface ProfileProject {
  id: number
  name: string
  siteUrl: string | null
  sourceUrl: string | null
  description: string | null
  createTime: string
  updateTime: string
}

export interface AiUsageDailyPoint {
  date: string
  tokens: number
  calls: number
}

export interface AiUsageTypeStat {
  modelType: string
  tokens: number
  calls: number
}

export interface AiUsageProviderStat {
  providerId: number
  providerName: string | null
  tokens: number
  calls: number
}

export interface AiUsageStats {
  totalTokens: number
  todayTokens: number
  totalCalls: number
  todayCalls: number
  /** 近 7 日 token 总量 */
  periodTokens: number
  /** 近 7 日调用次数 */
  periodCalls: number
  /** 前 7 日 token 总量 */
  prevPeriodTokens: number
  /** 前 7 日调用次数 */
  prevPeriodCalls: number
  dailyTrend: AiUsageDailyPoint[]
  byType: AiUsageTypeStat[]
  byProvider: AiUsageProviderStat[]
}

/** 通用任务统计：成功率/平均耗时，文案耗时单位 ms，图片/视频为 s */
export interface DashboardTaskStat {
  total: number
  today: number
  successCount: number
  /** 成功率 0-100，保留 1 位小数 */
  successRate: number
  /** 平均耗时（文案 ms / 图片视频 s） */
  avgDuration: number
  /** 本周任务数（含今日，近 7 日） */
  weekTotal?: number
  /** 上周任务数（前 7 日） */
  prevWeekTotal?: number
}

export interface DashboardFileTypeStat {
  /** image / audio / other */
  type: string
  count: number
  size: number
}

export interface DashboardFileStat {
  total: number
  todayUpload: number
  totalSize: number
  byType: DashboardFileTypeStat[]
}

export interface DashboardTtsStat {
  total: number
  today: number
  totalSize: number
}

/** 仪表盘聚合统计 */
export interface DashboardStats {
  aiUsage: AiUsageStats | null
  textGen: DashboardTaskStat | null
  imageTask: DashboardTaskStat | null
  videoTask: DashboardTaskStat | null
  file: DashboardFileStat | null
  tts: DashboardTtsStat | null
}

export interface GameScore {
  id: number
  playerName: string
  score: number
  totalTime: number
  accuracy: number
  createTime: string
}

export interface GameScoreSubmit {
  playerName: string
  score: number
  totalTime: number
  accuracy: number
}

export interface ConchAnswer {
  id: number
  answerText: string
  matchDescription: string | null
  fileId: number
  enabled: number
  sortOrder: number
  createTime: string
}

export interface ConchAnswerQuery {
  answerText?: string
  enabled?: number | null
  pageNum: number
  pageSize: number
}

export interface ConchAskResult {
  answerId: number
  answerText: string
  audioUrl: string
}

export interface ConchRecord {
  id: number
  questionText: string
  answerId: number | null
  answerText: string | null
  userId: number | null
  createTime: string
}

/** 文件记录（列表/详情，不含二进制 data） */
export interface SysFile {
  id: number
  originalName: string | null
  ext: string | null
  contentType: string | null
  size: number | null
  userId: number | null
  uploaderName: string | null
  createTime: string
}

export interface SysFileQuery {
  originalName?: string
  fileType?: string | null
  userId?: number | null
  startTime?: string | null
  endTime?: string | null
  pageNum: number
  pageSize: number
}

// ===== AI 聊天模块 =====

export interface AiPersona {
  id: number
  name: string
  systemPrompt: string
  description: string | null
  enabled: number
  sortOrder: number
  builtIn: number
  createTime: string
  updateTime: string
}

export interface AiChatSession {
  id: number
  title: string
  personaId: number | null
  kbId: number | null
  subjectType: string
  subjectId: string
  createTime: string
  updateTime: string
}

/** 管理后台会话列表项（含用户名） */
export interface AiChatSessionAdmin {
  id: number
  title: string
  personaId: number | null
  kbId: number | null
  subjectType: string
  subjectId: string
  username: string | null
  createTime: string
  updateTime: string
}

/** RAG 引用来源：回答实际召回并注入的资料（气泡下方"参考"链接，溯源用） */
export interface RagSource {
  /** 来源类型: kb=知识库 / memos=备忘笔记 */
  source: 'kb' | 'memos'
  /** 来源标识: chunkId 或 noteId（字符串） */
  sourceKey: string
  /** 来源标题: KB sourceName / memos 首行，可空 */
  title: string | null
  /** 原始链接: memos 为 {base}/m/{uid}，KB 为 null */
  url: string | null
  /** 检索融合分 */
  score: number
}

/** RAG 评估用例（golden set）：问题 + 期望命中的来源 key */
export interface RagEvalCase {
  id: number
  query: string
  /** 关联知识库 (空=仅备忘笔记源) */
  kbId: number | null
  /** 期望命中的来源key(JSON数组文本, 如 ["memos:12","kb:3"]) */
  expectedSources: string
  note: string | null
  createTime: string
  updateTime: string
}

/** RAG 跑分结果明细（某条 case） */
export interface RagEvalDetail {
  caseId: number
  query: string
  pass: boolean
  recall: number
  topHits: { source: string; title: string | null; url: string | null; score: number }[]
}

/** RAG 跑分汇总（一次 run） */
export interface RagEvalRunVO {
  runId: string
  /** retrieval=纯检索路径 / full=完整管线（含改写/重排/loop） */
  mode: string
  total: number
  passCount: number
  failCount: number
  recallAvg: number
  details: RagEvalDetail[]
}

/** RAG 跑分结果行（历史批次落库记录） */
export interface RagEvalResultRow {
  id: number
  runId: string
  caseId: number
  query: string
  topHits: string | null
  pass: number
  recall: number
  /** retrieval=纯检索路径 / full=完整管线 */
  mode: string | null
  createTime: string
}

/** 回复反馈 + 消息快照（bad case 复盘原料） */
export interface RagFeedbackVO {
  id: number
  messageId: number
  value: number
  comment: string | null
  subjectType: string
  subjectId: string
  content: string | null
  refs: RagSource[]
  createTime: string
}

export interface AiChatMessage {
  id: number
  sessionId: number
  role: string
  content: string
  tokens: number | null
  createTime: string
  /** 附件类型: image/audio (聊天 function calling 工具产物)，无附件为 null */
  attachmentType: 'image' | 'audio' | null
  /** 附件文件ID (引用 sys_file.id) */
  attachmentFileId: number | null
  /** 附件访问 URL (/file/{id})，后端实体 getter 计算输出 */
  attachmentUrl: string | null
  /** RAG 引用来源原始 JSON 文本（兼容字段，一般用 refs 解析数组） */
  ragRefs: string | null
  /** RAG 引用来源列表（后端 getter 解析输出，气泡渲染"参考"链接） */
  refs: RagSource[] | null
  /** 当前主体对该消息的评价（1 有用 / -1 没用 / null 未评价），回显"有用/没用"选中态 */
  feedbackValue: number | null
}

export interface AiMemory {
  id: number
  userId: number
  username: string | null
  content: string
  sourceSessionId: number | null
  createTime: string
}

export interface AiKnowledgeBase {
  id: number
  name: string
  description: string | null
  embeddingModelId: number | null
  chunkCount: number
  createTime: string
  updateTime: string
}

export interface AiKnowledgeChunk {
  id: number
  kbId: number
  chunkText: string
  chunkIndex: number
  tokenCount: number | null
  sourceName: string | null
  createTime: string
}

export interface MemosConfig {
  baseUrl: string
  tokenConfigured: boolean
  promptTemplate: string
}

export interface MemosConfigUpdate {
  baseUrl?: string
  token?: string
  promptTemplate?: string
}

export interface MemosSyncResult {
  fetched: number
  created: number
  updated: number
  markedDeleted: number
  errors: number
}

export interface MemosSyncLog {
  id: number
  triggerType: 'scheduled' | 'manual'
  status: 'success' | 'partial' | 'failed' | 'skipped'
  fetched: number
  created: number
  updated: number
  markedDeleted: number
  errors: number
  durationMs: number | null
  errorMessage: string | null
  createTime: string | null
}

export interface MemosJobResult {
  processed: number
  success: number
  skipped: number
  failed: number
  remaining: number
  pushSuccess: number
  pushFailed: number
}

export interface MemosNote {
  id: number
  uid: string
  content: string
  tags: string[]
  tagsSynced: number
  remoteDeleted: number
  remoteCreateTime: string | null
  remoteUpdateTime: string | null
  createTime: string | null
  updateTime: string | null
}

export interface MemosQuery {
  pageNum: number
  pageSize: number
  keyword?: string
  remoteDeleted?: number
}

export interface MemosStats {
  total: number
  active: number
  deleted: number
  untagged: number
  pendingPush: number
}

export interface MonitorOverview {
  jvm: {
    heapUsed: number
    heapMax: number
    heapInit: number
    nonHeapUsed: number
    nonHeapMax: number
    youngGcCount: number
    youngGcTimeMs: number
    fullGcCount: number
    fullGcTimeMs: number
threadActive: number
    threadDaemon: number
    threadPeak: number
    loadedClasses: number
    gcCollectors: {
      name: string
      count: number
      timeMs: number
      avgTimeMs: number
    }[]
    keyJvmArgs: {
      name: string
      value: string
    }[]
  }
  system: {
    processCpuUsage: number
    systemCpuUsage: number
    diskTotal: number
    diskFree: number
    fileOpenDescriptors: number | null
    fileMaxDescriptors: number | null
  }
  http: {
    totalRequests: number
    status2xx: number
    status4xx: number
    status5xx: number
    maxCostMs: number
    avgCostMs: number
    activeRequests: number
  }
  hikariPool: {
    idleConnections: number
    activeConnections: number
    pendingConnections: number
    maximumPoolSize: number
  }
  app: {
    upTimeSeconds: number
    startCostMs: number
    startTimeMillis: number
  }
  health: {
    status: string
    components: Record<string, string>
  }
}
