/**
 * echarts canvas 不读 CSS 变量，需运行时从 token 读取色值注入。
 * 调用时机：组件渲染后（computed 依赖 darkMode 以在主题切换时重算）。
 */
export function getChartColors() {
  const s = getComputedStyle(document.documentElement)
  const v = (name: string, fallback: string) => s.getPropertyValue(name).trim() || fallback
  return {
    brand: v('--c-brand', '#18a058'),
    info: v('--c-info', '#2080f0'),
    success: v('--c-success', '#18a058'),
    warning: v('--c-warning', '#f0a020'),
    error: v('--c-error', '#d03050'),
    text3: v('--c-text-3', '#8a9099'),
    border: v('--c-border', 'rgba(0,0,0,0.08)'),
  }
}