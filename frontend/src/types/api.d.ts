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
  dailyTrend: AiUsageDailyPoint[]
  byType: AiUsageTypeStat[]
  byProvider: AiUsageProviderStat[]
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

export interface AiChatMessage {
  id: number
  sessionId: number
  role: string
  content: string
  tokens: number | null
  createTime: string
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
