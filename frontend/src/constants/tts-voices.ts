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

/** 根据 voice value 获取中文标签（先查 Edge 音色，再查 MiMo 音色） */
export function getVoiceLabel(value: string): string {
  return ttsVoiceMap[value] || mimoVoiceMap[value] || value
}

/** 用于 NSelect 的 options（类型兼容） */
export const ttsVoiceOptions = ttsVoiceGroups as unknown as SelectGroupOption[]

/** MiMo-V2.5-TTS 预置音色分组（用于 AI 语音合成页 NSelect） */
export const mimoVoiceGroups: TtsVoiceGroup[] = [
  {
    type: 'group',
    label: '中文音色',
    key: 'group-mimo-zh',
    children: [
      { label: '冰糖（女声）', value: '冰糖' },
      { label: '茉莉（女声）', value: '茉莉' },
      { label: '苏打（男声）', value: '苏打' },
      { label: '白桦（男声）', value: '白桦' },
    ],
  },
  {
    type: 'group',
    label: '英文音色',
    key: 'group-mimo-en',
    children: [
      { label: 'Mia（女声）', value: 'Mia' },
      { label: 'Chloe（女声）', value: 'Chloe' },
      { label: 'Milo（男声）', value: 'Milo' },
      { label: 'Dean（男声）', value: 'Dean' },
    ],
  },
  {
    type: 'group',
    label: '默认',
    key: 'group-mimo-default',
    children: [
      { label: 'MiMo 默认（随集群）', value: 'mimo_default' },
    ],
  },
]

/** MiMo 音色 value → 标签 映射 */
export const mimoVoiceMap: Record<string, string> = Object.fromEntries(
  mimoVoiceGroups.flatMap((g) => g.children).map((v) => [v.value, v.label]),
)

/** 用于 NSelect 的 MiMo 音色 options */
export const mimoVoiceOptions = mimoVoiceGroups as unknown as SelectGroupOption[]

/** 聊天页 TTS 音色下拉（合并 Edge + MiMo 分组）。
 * 用户选了具体音色则按音色所属引擎走（覆盖系统开关 chat_tts_engine）；
 * 用户未选（clear）则按系统开关 + 对应默认音色兜底。 */
export const chatVoiceOptions = [...ttsVoiceOptions, ...mimoVoiceOptions]
