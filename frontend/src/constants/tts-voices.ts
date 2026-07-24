import type { SelectGroupOption } from 'naive-ui'

export interface TtsVoiceOption {
  label: string
  value: string
}

export interface TtsVoiceGroup {
  type: 'group'
  label: string
  key: string
  children: TtsVoiceOption[]
}

/** 中文发音人分组列表（用于 NSelect options） */
export const ttsVoiceGroups: TtsVoiceGroup[] = [
  {
    type: 'group',
    label: '普通话（大陆）',
    key: 'group-mandarin',
    children: [
      { label: '晓晓（女声）', value: 'zh-CN-XiaoxiaoNeural' },
      { label: '晓伊（女声）', value: 'zh-CN-XiaoyiNeural' },
      { label: '云健（男声）', value: 'zh-CN-YunjianNeural' },
      { label: '云希（男声）', value: 'zh-CN-YunxiNeural' },
      { label: '云夏（男声）', value: 'zh-CN-YunxiaNeural' },
      { label: '云扬（男声）', value: 'zh-CN-YunyangNeural' },
    ],
  },
  {
    type: 'group',
    label: '方言',
    key: 'group-dialect',
    children: [
      { label: '晓北（女声·东北话）', value: 'zh-CN-liaoning-XiaobeiNeural' },
      { label: '晓妮（女声·陕西话）', value: 'zh-CN-shaanxi-XiaoniNeural' },
    ],
  },
  {
    type: 'group',
    label: '粤语（香港）',
    key: 'group-cantonese',
    children: [
      { label: '晓佳（女声）', value: 'zh-HK-HiuGaaiNeural' },
      { label: '晓曼（女声）', value: 'zh-HK-HiuMaanNeural' },
      { label: '云龙（男声）', value: 'zh-HK-WanLungNeural' },
    ],
  },
  {
    type: 'group',
    label: '台湾',
    key: 'group-taiwan',
    children: [
      { label: '晓臻（女声）', value: 'zh-TW-HsiaoChenNeural' },
      { label: '晓雨（女声）', value: 'zh-TW-HsiaoYuNeural' },
      { label: '云哲（男声）', value: 'zh-TW-YunJheNeural' },
    ],
  },
]

/** voice value → 中文标签 映射 */
export const ttsVoiceMap: Record<string, string> = Object.fromEntries(
  ttsVoiceGroups.flatMap((g) => g.children).map((v) => [v.value, v.label]),
)

/** 根据 voice value 获取中文标签 */
export function getVoiceLabel(value: string): string {
  return ttsVoiceMap[value] || value
}

/** 用于 NSelect 的 options（类型兼容） */
export const ttsVoiceOptions = ttsVoiceGroups as unknown as SelectGroupOption[]
