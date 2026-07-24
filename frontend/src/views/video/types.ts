export type Ratio = 'landscape' | 'landscape32' | 'portrait'
export type Platform = 'bilibili' | 'douyin' | 'xiaohongshu' | 'custom'
export type TemplateId = 'center' | 'top-left' | 'bottom-impact'

export interface Gradient {
  id: string
  name: string
  value: string
  titleColor?: string
  subtitleColor?: string
}

export interface RatioConfig {
  label: string
  width: number
  height: number
}

export interface LayoutTemplate {
  id: TemplateId
  name: string
  desc: string
}

export interface ExportPreset {
  id: string
  name: string
  desc: string
  width: number
  height: number
}

export interface ColorPreset {
  id: string
  name: string
  title: string
  subtitle: string
}

export interface FontPreset {
  id: string
  name: string
  desc: string
  titleFont: string
  subtitleFont: string
  titleWeight: number
  subtitleWeight: number
  titleSpacingOffset: string
  subtitleSpacingOffset: string
  titleScale: number
  subtitleScale: number
}

export interface CoverState {
  ratio: Ratio
  gradientId: string
  templateId: TemplateId
  presetId: string
  fontPresetId: string
  title: string
  subtitle: string
  badgeText: string
  badgeVisible: boolean
  titleColor: string
  subtitleColor: string
  strokeColor: string
  strokeWidth: number
  shadowStrength: number
  glowStrength: number
  titleFontsize: number
  subtitleFontsize: number
  badgeColor: string
  backgroundImage: string
  backgroundOverride: string
}

export interface ApiConfig {
  endpoint: string
  apiKey: string
  model: string
}

export interface PromptTemplate {
  id: string
  name: string
  platform: Platform
  prompt: string
  builtIn: boolean
  updatedAt: number
}

export interface CopyResult {
  id: string
  topic: string
  templateId: string
  result: { titles: string[]; description: string; tags: string[] }
  generatedAt: number
}

export interface CoverDraft {
  id: string
  name: string
  state: CoverState
  savedAt: number
}
