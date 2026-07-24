import type { ExportPreset, Ratio } from '../types'

export const presets: Record<Ratio, ExportPreset[]> = {
  landscape: [{ id: 'youtube', name: 'YouTube / B站 横版', desc: '1280 × 720', width: 1280, height: 720 }],
  landscape32: [{ id: 'general-32', name: '通用 3:2', desc: '1500 × 1000', width: 1500, height: 1000 }],
  portrait: [{ id: 'douyin', name: '抖音 / 小红书 竖版', desc: '1080 × 1920', width: 1080, height: 1920 }],
}

export function getPreset(ratio: Ratio, presetId: string): ExportPreset {
  const list = presets[ratio]
  return list.find((p) => p.id === presetId) ?? list[0]
}
