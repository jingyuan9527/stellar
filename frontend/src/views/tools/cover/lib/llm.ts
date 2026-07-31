import type { ApiConfig } from '../types'

export function buildPrompt(template: string, topic: string): string {
  return template.replaceAll('{{topic}}', topic)
}

export interface ParsedCopy {
  titles: string[]
  description: string
  tags: string[]
}

export function parseCopyResult(text: string): ParsedCopy | null {
  const match = text.match(/\{[\s\S]*\}/)
  const candidate = match ? match[0] : text
  try {
    const parsed = JSON.parse(candidate)
    if (
      parsed &&
      Array.isArray(parsed.titles) &&
      typeof parsed.description === 'string' &&
      Array.isArray(parsed.tags)
    ) {
      return {
        titles: parsed.titles.map((t: unknown) => String(t)),
        description: parsed.description,
        tags: parsed.tags.map((t: unknown) => String(t)),
      }
    }
    return null
  } catch {
    return null
  }
}

export async function streamChat(
  config: ApiConfig,
  prompt: string,
  onDelta: (full: string) => void,
  signal: AbortSignal,
): Promise<string> {
  const endpoint = config.endpoint.replace(/\/+$/, '')
  const res = await fetch(`${endpoint}/v1/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${config.apiKey}`,
    },
    body: JSON.stringify({
      model: config.model,
      messages: [{ role: 'user', content: prompt }],
      stream: true,
    }),
    signal,
  })

  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }

  const reader = res.body!.getReader()
  const decoder = new TextDecoder()
  let full = ''
  let buf = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buf += decoder.decode(value, { stream: true })
    const lines = buf.split('\n')
    buf = lines.pop()!
    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed.startsWith('data:')) continue
      const data = trimmed.slice(5).trim()
      if (data === '[DONE]') return full
      try {
        const json = JSON.parse(data)
        const delta: string = json.choices?.[0]?.delta?.content ?? ''
        if (delta) {
          full += delta
          onDelta(full)
        }
      } catch {
        // 忽略格式异常的分片
      }
    }
  }

  return full
}

export async function fetchModels(
  config: Pick<ApiConfig, 'endpoint' | 'apiKey'>,
): Promise<string[]> {
  const endpoint = config.endpoint.replace(/\/+$/, '')
  const res = await fetch(`${endpoint}/v1/models`, {
    headers: { Authorization: `Bearer ${config.apiKey}` },
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const json = await res.json()
  const data: unknown = json?.data
  if (!Array.isArray(data)) return []
  return data
    .map((m: { id?: string }) => (typeof m === 'object' && m ? m.id : undefined))
    .filter((id: unknown): id is string => typeof id === 'string')
    .sort()
}

export async function testConnection(config: ApiConfig): Promise<void> {
  const endpoint = config.endpoint.replace(/\/+$/, '')
  const res = await fetch(`${endpoint}/v1/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${config.apiKey}`,
    },
    body: JSON.stringify({
      model: config.model,
      messages: [{ role: 'user', content: 'hi' }],
      max_tokens: 1,
      stream: false,
    }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}
