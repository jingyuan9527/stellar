export interface ApiConfig {
  endpoint: string
  apiKey: string
  model: string
}

export interface PromptTemplate {
  id: string
  name: string
  platform: string
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
