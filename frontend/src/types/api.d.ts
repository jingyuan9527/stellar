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

export interface AiImageResult {
  fileId: number
  url: string
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

export interface AiCopyResult {
  id: number
  topic: string
  templateId: number | null
  result: string
  generatedAt: number
  creatorId: number
  createTime: string
  updateTime: string
}

export interface CopyResultData {
  titles: string[]
  description: string
  tags: string[]
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

export interface AiUsageDailyPoint {
  date: string
  tokens: number
  calls: number
}

export interface AiUsageStats {
  totalTokens: number
  todayTokens: number
  totalCalls: number
  todayCalls: number
  dailyTrend: AiUsageDailyPoint[]
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
