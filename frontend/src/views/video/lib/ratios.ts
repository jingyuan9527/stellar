import type { Ratio, RatioConfig } from '../types'

export const ratios: Record<Ratio, RatioConfig> = {
  landscape: { label: '横屏 16:9', width: 640, height: 360 },
  landscape32: { label: '横屏 3:2', width: 630, height: 420 },
  portrait: { label: '竖屏 9:16', width: 360, height: 640 },
}

export const ratioList: Ratio[] = ['landscape', 'landscape32', 'portrait']
