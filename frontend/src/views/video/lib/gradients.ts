import type { Gradient } from '../types'

export const gradients: Gradient[] = [
  { id: 'aurora', name: '蓝紫渐变', value: 'linear-gradient(135deg, #2563eb 0%, #7c3aed 48%, #ec4899 100%)' },
  { id: 'sunset', name: '粉橙渐变', value: 'linear-gradient(135deg, #f97316 0%, #fb7185 52%, #a855f7 100%)' },
  { id: 'mint', name: '青绿渐变', value: 'linear-gradient(135deg, #0f766e 0%, #14b8a6 50%, #99f6e4 100%)' },
  { id: 'night', name: '深蓝夜色', value: 'linear-gradient(135deg, #020617 0%, #1d4ed8 45%, #0f172a 100%)' },
  { id: 'amber', name: '金橙暖调', value: 'linear-gradient(135deg, #f59e0b 0%, #f97316 50%, #7c2d12 100%)' },
  { id: 'nebula', name: '紫黑高对比', value: 'linear-gradient(135deg, #111827 0%, #6d28d9 52%, #db2777 100%)' },
  { id: 'yellow-purple', name: '黄紫对比', value: '#FFF300', titleColor: '#6A0DAD', subtitleColor: '#6A0DAD' },
  { id: 'pink-cyan', name: '粉青亮色', value: '#FF527C', titleColor: '#00FFFF', subtitleColor: '#00FFFF' },
  { id: 'mint-orange', name: '薄荷橙', value: '#A1E6DD', titleColor: '#FF6F2C', subtitleColor: '#FF6F2C' },
  { id: 'red-blue', name: '红蓝经典', value: '#D30121', titleColor: '#BFDEFF', subtitleColor: '#BFDEFF' },
  { id: 'pink-green', name: '粉绿活泼', value: '#FF449E', titleColor: '#ACFE6C', subtitleColor: '#ACFE6C' },
  { id: 'lightgreen-blue', name: '浅绿蓝', value: '#D3FFAF', titleColor: '#05A5FA', subtitleColor: '#05A5FA' },
  { id: 'purple-dark', name: '紫黑深邃', value: '#A855F7', titleColor: '#0B0C10', subtitleColor: '#0B0C10' },
  { id: 'rose-red', name: '粉红热烈', value: '#F1DDDF', titleColor: '#E72D48', subtitleColor: '#E72D48' },
  { id: 'green-cream', name: '绿米自然', value: '#73AE52', titleColor: '#FBF1D7', subtitleColor: '#FBF1D7' },
  { id: 'neon-blue', name: '荧光蓝', value: '#B5F800', titleColor: '#0036FF', subtitleColor: '#0036FF' },
]

export function getGradient(id: string): Gradient {
  return gradients.find((g) => g.id === id) ?? gradients[0]
}
